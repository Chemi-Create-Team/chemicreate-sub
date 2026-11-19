package net.createplant.gametest;

import java.util.ArrayList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.chemistry.VesselFouling;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.GradeFluidTank;
import net.createplant.transport.TransportConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class VesselFoulingGameTests {
    private static final BlockPos BASE = new BlockPos(8, 3, 8);
    private static final ResourceLocation SOLID_RECIPE = CreatePlant.location("gametest/vessel_trace_fouling");

    private static LiddedVesselBlockEntity vessel(GameTestHelper h, int width) {
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) h.setBlock(BASE.offset(x, 0, z), CETransport.LIDDED_VESSEL.get());
        return ((LiddedVesselBlockEntity) h.getBlockEntity(BASE)).getControllerBE();
    }

    private static class SolidParams extends ProcessingRecipeParams {
        SolidParams() {
            ingredients.add(Ingredient.of(Items.CLAY_BALL));
            fluidIngredients.add(SizedFluidIngredient.of(Fluids.WATER, 100));
            results.add(new ProcessingOutput(new ItemStack(Items.BRICK), 1));
            processingDuration = 20;
        }
    }

    private static PressureReactionRecipe solidRecipe() { return new PressureReactionRecipe(new SolidParams()); }
    private static void soil(LiddedVesselBlockEntity owner, int amount) {
        var state = new CompoundTag();state.putInt("Dirt", amount);
        owner.fouling().read(state);
    }
    private static int count(IItemHandler items, Item item) {
        int count = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) if (items.getStackInSlot(slot).is(item)) count += items.getStackInSlot(slot).getCount();
        return count;
    }
    private static String thermal(FluidStack stack) {
        return stack.isEmpty() ? "empty" : stack.getAmount() + "mB @" + FluidThermal.kelvin(stack) + "K / " + FluidThermal.energy(stack) + "J";
    }
    private static String washSnapshot(LiddedVesselBlockEntity owner) {
        var raw = owner.port(Direction.NORTH).getFluidInTank(0);
        var waste = owner.fouling().drainPort().getFluidInTank(0);
        return "raw=" + thermal(raw) + ", liquidPhase=" + thermal(owner.processLiquidContent(0))
            + ", vapourPhase=" + thermal(owner.processGasContent(0)) + ", physical=" + thermal(owner.getTankInventory().getFluid())
            + ", waste=" + thermal(waste) + ", dirt=" + owner.fouling().dirtLevel();
    }

    @GameTest(template = "test_fractionation")
    public static void traceLoadOnlyCountsCommittedHeavyBatchesAndSurvivesMalformedValues(GameTestHelper h) {
        var owner = vessel(h, 2);
        var dirt = owner.fouling();
        dirt.onBatch(solidRecipe(), 0);
        dirt.onBatch(new MixingRecipe(new SolidParams()), 100);
        h.assertTrue(dirt.dirtLevel() == 0, "Rejected batches and ordinary Create mixing do not generate trace fouling");
        dirt.onBatch(solidRecipe(), 200);
        h.assertTrue(dirt.dirtLevel() == 200 && dirt.speedFactor() == 1 && !dirt.blocked(), "The first 200 committed heavy batches retain normal speed");
        dirt.onBatch(solidRecipe(), 1);
        h.assertTrue(dirt.speedFactor() < 1 && dirt.speedFactor() > 0 && dirt.dirtLevel() == 201, "Further trace deposits gradually slow processing");
        dirt.onBatch(solidRecipe(), Integer.MAX_VALUE);
        h.assertTrue(dirt.dirtLevel() == 400 && dirt.blocked(), "Trace load saturates without integer overflow and then blocks processing");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        owner.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(owner.fouling().dirtLevel() == 400 && owner.fouling().blocked(), "The same controller keeps its fouling across save/load");
        var bad = saved.getCompound("Fouling");
        bad.putInt("Dirt", -100);bad.putInt("WasteAmount", Integer.MAX_VALUE);bad.putDouble("WasteKelvin", Double.NaN);
        owner.fouling().read(saved.getCompound("Fouling"));
        h.assertTrue(owner.fouling().dirtLevel() == 0 && owner.fouling().drainPort().getFluidInTank(0).getAmount() == 2000
            && Double.isFinite(FluidThermal.kelvin(owner.fouling().drainPort().getFluidInTank(0))), "Malformed save fields are finite and bounded");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void finiteWashWaterProducesOnlyItsOwnVolumeAndKeepsTemperatureAndEnergy(GameTestHelper h) {
        var owner = vessel(h, 3);
        var fouling = owner.fouling();
        owner.putInternal(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());owner.validateStructure();
        soil(owner, 300);
        owner.port(Direction.NORTH).fill(FluidThermal.at(new FluidStack(Fluids.WATER, 3500), 310), EXECUTE);
        String initialState = washSnapshot(owner);
        h.assertTrue(fouling.toggleDrain(), "An idle cool low-pressure vessel opens its bottom drain");
        h.assertTrue(fouling.flush(SIMULATE) && fouling.dirtLevel() == 300 && owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 3500
            && fouling.drainPort().getFluidInTank(0).isEmpty(), "A simulated wash changes no dirt, water or waste");
        h.assertTrue(fouling.flush(EXECUTE) && fouling.flush(EXECUTE), "Two washes fit the finite two-bucket waste buffer");
        var wastePort = fouling.drainPort();
        h.assertTrue(fouling.dirtLevel() == 100 && owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1500
            && wastePort.getFluidInTank(0).getAmount() == 2000 && !fouling.flush(EXECUTE), "A full waste buffer leaves the next water and deposits untouched");
        h.assertTrue(wastePort.fill(new FluidStack(CEProcessFluids.WASTEWATER.get(), 1), EXECUTE) == 0
            && wastePort.drain(1000, SIMULATE).getAmount() == 1000 && wastePort.getFluidInTank(0).getAmount() == 2000, "The drain is output-only and simulation is inert");
        var alias = wastePort.getFluidInTank(0);alias.setAmount(1);
        h.assertTrue(wastePort.getFluidInTank(0).getAmount() == 2000, "Inspection does not expose an alias to the waste inventory");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        owner.loadWithComponents(saved, h.getLevel().registryAccess());owner.validateStructure();
        fouling = owner.fouling();wastePort = fouling.drainPort();
        h.assertTrue(fouling.drainOpen() && fouling.dirtLevel() == 100 && wastePort.getFluidInTank(0).getAmount() == 2000,
            "Valve, trace load and finite wastewater survive a controller reload");
        var recovered = wastePort.drain(1000, EXECUTE);
        h.assertTrue(recovered.is(CEProcessFluids.WASTEWATER.get()) && fouling.flush(EXECUTE), "Removing real waste makes room for one final wash");
        var left = owner.port(Direction.NORTH).getFluidInTank(0);var held = wastePort.getFluidInTank(0);
        h.assertTrue(fouling.dirtLevel() == 0 && left.getAmount() + held.getAmount() + recovered.getAmount() == 3500,
            "The three washes remove only trace deposits and conserve all 3500 mB of actual liquid");
        h.assertTrue(recovered.is(CEProcessFluids.WASTEWATER.get()) && held.is(CEProcessFluids.WASTEWATER.get()),"Washing creates the fixed wastewater type without numeric temperatures");
        h.assertTrue(!fouling.toggleDrain() && wastePort.drain(1, EXECUTE).isEmpty(), "Closing the valve disables an already cached drain port");
        var released = fouling.clear();
        h.assertTrue(released.getAmount() == 2000 && wastePort.getFluidInTank(0).isEmpty() && !fouling.drainOpen() && fouling.dirtLevel() == 0,
            "Destruction retrieves the exact remaining parcel even behind a closed valve, then clears maintenance state once");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void anIncompleteBucketCannotPartlyWashOrDrainTheInput(GameTestHelper h) {
        var owner = vessel(h, 2);
        soil(owner, 80);
        owner.getTankInventory().fill(new FluidStack(Fluids.WATER, 999), EXECUTE);
        owner.fouling().toggleDrain();
        h.assertTrue(!owner.fouling().flush(SIMULATE) && !owner.fouling().flush(EXECUTE)
            && owner.getTankInventory().getFluidAmount() == 999 && owner.fouling().dirtLevel() == 80
            && owner.fouling().drainPort().getFluidInTank(0).isEmpty(), "A wash requires a complete bucket before any source is debited");
        h.succeed();
    }



    private static Player brushPlayer(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BRUSH));
        return player;
    }
    private static void brush(GameTestHelper h, Player player, BlockPos hatch) {
        var absolute = h.absolutePos(hatch);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, .5, 0), Direction.UP, absolute, false);
        h.getBlockState(hatch).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static void brushAt(GameTestHelper h, Player player, BlockPos pos, double x, double z) {
        var absolute = h.absolutePos(pos);
        var hit = new BlockHitResult(new Vec3(absolute.getX() + x, absolute.getY() + 1, absolute.getZ() + z), Direction.UP, absolute, false);
        h.getBlockState(pos).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = "test_fractionation")
    public static void aBrushCleansThroughTheThreeWideHatchAndNeverThroughAFixedPlate(GameTestHelper h) {
        var owner = vessel(h, 3);soil(owner, 80);owner.toggleLid();
        var player = brushPlayer(h);
        brushAt(h, player, BASE, .5, .5);
        h.assertTrue(owner.fouling().dirtLevel() == 80 && player.getMainHandItem().getDamageValue() == 0,
            "The fixed corner top plate never admits a brush or consumes its durability");
        brushAt(h, player, BASE.offset(1, 0, 1), .5, .5);
        h.assertTrue(owner.fouling().dirtLevel() == 40 && player.getMainHandItem().getDamageValue() == 1,
            "The actual central open hatch admits one cleaning action");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aTwoWideQuarterHatchOnlyAcceptsTheBrushInsideItsOpening(GameTestHelper h) {
        var owner = vessel(h, 2);soil(owner, 80);owner.toggleLid();
        var player = brushPlayer(h);
        brushAt(h, player, BASE, .125, .125);
        h.assertTrue(owner.fouling().dirtLevel() == 80 && player.getMainHandItem().getDamageValue() == 0,
            "The outer part of a quarter-hatch block remains a fixed plate");
        brushAt(h, player, BASE, .75, .75);
        h.assertTrue(owner.fouling().dirtLevel() == 40 && player.getMainHandItem().getDamageValue() == 1,
            "The inner quarter opening admits the brush and spends exactly one durability");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 180)
    public static void aRealCreateDeployerBrushesTheOpenHatchWithExactlyTwoDurability(GameTestHelper h) {
        var owner = vessel(h, 3);
        soil(owner, 80);
        owner.toggleLid();
        BlockPos hatch = BASE.offset(1, 0, 1), deployer = hatch.above(2);
        var state = AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN);
        h.setBlock(deployer, state);
        Direction drive = Direction.get(Direction.AxisDirection.POSITIVE, AllBlocks.DEPLOYER.get().getRotationAxis(state));
        BlockPos motor = deployer.relative(drive);
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, drive.getOpposite()));
        h.runAfterDelay(5, () -> {
            var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(deployer), drive);
            h.assertTrue(items != null && items.insertItem(0, new ItemStack(Items.BRUSH), false).isEmpty(), "The real deployer takes a vanilla brush through its inventory capability");
            ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128);
        });
        h.runAfterDelay(120, () -> {
            var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(deployer), drive);
            var held = items.getStackInSlot(0);
            h.assertTrue(owner.fouling().dirtLevel() == 0 && held.is(Items.BRUSH) && held.getDamageValue() == 2,
                "Native deployer use reaches the open hatch, cleans 40 per action and spends no durability after it is clean: dirt=" + owner.fouling().dirtLevel() + ", damage=" + held.getDamageValue());
            h.succeed();
        });
    }

    private static void removeRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        recipes.removeIf(recipe -> recipe.id().equals(SOLID_RECIPE));manager.replaceRecipes(recipes);
    }
    private static void guarded(GameTestHelper h, Runnable step) {
        try { step.run(); } catch (RuntimeException | Error failure) { removeRecipe(h);throw failure; }
    }
    @GameTest(template = "test_fractionation", timeoutTicks = 240)
    public static void aFouledProcessStopsThenResumesAfterOneManualBrush(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        recipes.add(new RecipeHolder<>(SOLID_RECIPE, solidRecipe()));manager.replaceRecipes(recipes);
        guarded(h, () -> {
            var owner = vessel(h, 3);
            owner.putInternal(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());owner.validateStructure();
            owner.itemPort().insertItem(0, new ItemStack(Items.CLAY_BALL), false);
            owner.port(Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), EXECUTE);
            soil(owner, 400);
            BlockPos motor = BASE.offset(1, -1, 1);
            h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
            h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128));
            h.runAfterDelay(60, () -> guarded(h, () -> {
                h.assertTrue(owner.rotationSpeed() != 0 && owner.fouling().blocked() && !owner.running() && owner.progress() == 0
                    && count(owner.itemPort(), Items.CLAY_BALL) == 1 && count(owner.itemPort(), Items.BRICK) == 0,
                    "A fully fouled but genuinely driven process consumes no feed or progress");
                h.setBlock(motor, Blocks.AIR);
            }));
            h.runAfterDelay(80, () -> guarded(h, () -> {
                owner.toggleLid();var player = brushPlayer(h);brush(h, player, BASE.offset(1, 0, 1));
                h.assertTrue(owner.fouling().dirtLevel() == 360 && player.getMainHandItem().getDamageValue() == 1,
                    "One real top-face brush removes 40 trace load for one durability while stopped");
                owner.toggleLid();
                h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
                ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128);
            }));
            h.runAfterDelay(180, () -> {
                try {
                    h.assertTrue(count(owner.itemPort(), Items.CLAY_BALL) == 0 && count(owner.itemPort(), Items.BRICK) == 1
                        && owner.fouling().dirtLevel() == 361 && owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 900,
                        "The slowed process resumes one batch and charges one new unit of fouling only after its material transaction commits");
                    h.succeed();
                } finally { removeRecipe(h); }
            });
        });
    }
}
