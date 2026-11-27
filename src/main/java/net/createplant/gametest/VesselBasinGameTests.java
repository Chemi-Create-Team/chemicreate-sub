package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import java.util.ArrayList;
import java.util.HashSet;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.mixture.MixtureContents;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Actual Create transport and fuel ticks, alongside inventory/window lifecycle contracts. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class VesselBasinGameTests {
    private static final BlockPos BASE = new BlockPos(8, 3, 8);
    private static final ResourceLocation CONTAINER_RECIPE = CreatePlant.location("gametest/vessel_container_mixing");

    @GameTest(template = "test_rbmk")
    public static void fullLiquidChargesReachTheTopForStorageAndEveryInternal(GameTestHelper h) {
        for (int width=1;width<=3;width++) {
            var owner=square(h,new BlockPos(width*5,3,2),width,2);
            int capacity=owner.getTankInventory().getCapacity();
            owner.getTankInventory().fill(new FluidStack(Fluids.WATER,capacity/2),EXECUTE);
            h.assertTrue(Math.abs(liquidFraction(owner)-.5)<.002,"Half-full "+width+"x"+width+" storage reaches half of the whole two-layer interior");
            owner.getTankInventory().fill(new FluidStack(Fluids.WATER,capacity),EXECUTE);
            h.assertTrue(liquidFraction(owner)>.999,"Full storage reaches the underside of the lid at every supported width");
        }
        ReactorModule[] modules={ReactorModule.STIRRED};
        for (int i=0;i<modules.length;i++) {
            var pos=h.absolutePos(new BlockPos(2+i*5,3,10));
            var owner=(LiddedVesselBlockEntity)ReactorGameTests.assemble(h.getLevel(),pos,modules[i],2);
            var port=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,Direction.NORTH);
            var fluid=Fluids.WATER;
            int capacity=owner.getTankInventory().getCapacity()+owner.liquidBufferCapacity();
            h.assertTrue(port!=null && port.fill(new FluidStack(fluid,capacity/2),EXECUTE)==capacity/2,"Actual pipes fill half the active liquid capacity of "+modules[i]);
            h.assertTrue(Math.abs(liquidFraction(owner)-.5)<.002,"A half charge occupies half the height with "+modules[i]);
            port.fill(new FluidStack(fluid,capacity),EXECUTE);
            h.assertTrue(liquidFraction(owner)>.999,"A full process charge reaches the lid instead of a shallow pool with "+modules[i]);
            var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());owner.loadWithComponents(saved.copy(),h.getLevel().registryAccess());
            h.assertTrue(liquidFraction(owner)>.999,"Saved full process contents keep the same liquid level");
        }
        h.succeed();
    }

    private static double liquidFraction(LiddedVesselBlockEntity owner) {
        long liquid=0;
        for (int slot=0;slot<owner.displayedFluidSlots();slot++) liquid+=owner.displayedLiquid(slot).getAmount();
        return (double)liquid/owner.getDisplayedCapacity();
    }

    private static LiddedVesselBlockEntity square(GameTestHelper h, BlockPos base, int width, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
            h.setBlock(base.offset(x, y, z), CETransport.STIRRED_TANK.get());
        return ((LiddedVesselBlockEntity) h.getBlockEntity(base)).getControllerBE();
    }

    /** Mount the stirrer on the tank roof and validate the process. */
    private static void stir(GameTestHelper h, LiddedVesselBlockEntity owner) {
        h.getLevel().setBlock(owner.stirrerPos(), CEReactor.STIRRER.get().defaultBlockState(), 3);owner.validateStructure();
    }

    private static IItemHandler items(GameTestHelper h, BlockPos member, Direction face) {
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(member), face);
        h.assertTrue(port != null, "A vessel member exposes its shared item inventory");
        return port;
    }

    private static int count(IItemHandler port, Item item) {
        int count = 0;
        for (int slot = 0; slot < port.getSlots(); slot++) if (port.getStackInSlot(slot).is(item)) count += port.getStackInSlot(slot).getCount();
        return count;
    }

    private static String snapshot(LiddedVesselBlockEntity owner) {
        var parts = new ArrayList<String>();
        var inventory = owner.itemPort();
        if (inventory != null) for (int i = 0; i < inventory.getSlots(); i++)
            if (!inventory.getStackInSlot(i).isEmpty()) parts.add("item" + i + "=" + inventory.getStackInSlot(i));
        for (int i = 0; i < owner.displayedFluidSlots(); i++) {
            var fluid = owner.displayedFluid(i);
            if (!fluid.isEmpty()) parts.add("fluid" + i + "=" + fluid.getAmount() + "mB " + fluid.getFluid() + " @" + net.createplant.thermal.FluidThermal.kelvin(fluid) + "K");
        }
        return "status=" + owner.status() + ", progress=" + owner.progress() + ", heat=" + owner.currentBurnerHeat() + ", pool=" + owner.getTankInventory().getFluidAmount() + "mB, " + parts;
    }

    /** All the water in the vessel, whatever its state: the heated vessel goes on to preheat its water as feedwater once mixing is done. */
    private static int water(LiddedVesselBlockEntity owner) {
        int total = 0;
        for (int i = 0; i < owner.displayedFluidSlots(); i++) if (owner.displayedFluid(i).is(Fluids.WATER)) total += owner.displayedFluid(i).getAmount();
        return total;
    }

    private static Player player(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player;
    }

    private static UseOnContext wrench(GameTestHelper h, Player player, BlockPos at, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        var absolute = h.absolutePos(at);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, absolute, false);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static BlockHitResult hit(GameTestHelper h, BlockPos at, Direction face) {
        BlockPos absolute = h.absolutePos(at);
        return new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, absolute, false);
    }

    private static void motor(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(128));
    }

    private static void roofMotor(GameTestHelper h) { ReactorGameTests.driveStirrer(h, BASE); }

    @GameTest(template = "test_fractionation")
    public static void sideWrenchWindowsPersistAtEveryWidthWithoutTakingAnInternal(GameTestHelper h) {
        var block = CETransport.STIRRED_TANK.get();
        var player = player(h);
        for (int width = 1; width <= 3; width++) {
            BlockPos base = new BlockPos(3 + width * 6, 3, 3);
            var owner = square(h, base, width, 2);
            owner.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), EXECUTE);
            if (width == 3) ReactorGameTests.stir(h.getLevel(), h.absolutePos(base));
            BlockPos member = base.offset(0, 1, width / 2);
            boolean before = owner.windowsEnabled();
            var axis = h.getBlockState(member).getValue(LiddedVesselBlock.AXIS);
            h.assertTrue(block.onWrenched(h.getBlockState(member), wrench(h, player, member, Direction.WEST)).consumesAction(), "A side wrench changes the windows");
            for (int y = 0; y < 2; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                var part = (LiddedVesselBlockEntity) h.getBlockEntity(base.offset(x, y, z));
                h.assertTrue(part.windowsEnabled() != before, "Every member shares the selected window mode at width " + width);
                var saved = part.saveWithoutMetadata(h.getLevel().registryAccess());
                part.loadWithComponents(saved, h.getLevel().registryAccess());
                h.assertTrue(part.windowsEnabled() != before, "Window mode survives save/load on each member");
            }
            h.assertTrue(h.getBlockState(member).getValue(LiddedVesselBlock.AXIS) == axis && owner.getTankInventory().getFluidAmount() == 1000,
                "Window changes keep the shaft axis and stored water");
            if (width == 3) h.assertTrue(owner.stirred(), "Side wrenching never takes the tank off its stirrer");
            block.onWrenched(h.getBlockState(member), wrench(h, player, member, Direction.WEST));
            h.assertTrue(owner.windowsEnabled() == before, "A second side click returns to the original window mode");
        }
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 120)
    public static void aRealAndesiteFunnelFeedsAnUnconfiguredVesselAndItemsSurviveSaving(GameTestHelper h) {
        var owner = square(h, BASE, 2, 1);
        BlockPos funnel = BASE.offset(-1, 0, 1);
        h.setBlock(funnel.below(), Blocks.IRON_BLOCK);
        h.setBlock(funnel, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST)
            .setValue(FunnelBlock.EXTRACTING, false).setValue(BlockStateProperties.POWERED, false));
        var absolute = h.absolutePos(funnel);
        var entity = new ItemEntity(h.getLevel(), absolute.getX() + .3, absolute.getY() + .35, absolute.getZ() + .5, new ItemStack(Items.IRON_INGOT, 3));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(entity);
        h.succeedWhen(() -> {
            var port = items(h, BASE.east(), Direction.EAST);
            h.assertTrue(count(port, Items.IRON_INGOT) == 3 && !entity.isAlive(), "The actual funnel collects the item entity into the plain vessel exactly once");
            var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
            owner.loadWithComponents(saved, h.getLevel().registryAccess());
            var reloaded = items(h, BASE, Direction.WEST);
            h.assertTrue(count(reloaded, Items.IRON_INGOT) == 3 && !owner.stirred(), "Saving plain contents keeps all three items without a stirrer");
            h.assertTrue(reloaded.extractItem(0, 1, true).getCount() == 1 && count(reloaded, Items.IRON_INGOT) == 3,
                "Simulated extraction never removes visible storage items");
            h.assertTrue(reloaded.extractItem(0, 1, false).is(Items.IRON_INGOT) && count(reloaded, Items.IRON_INGOT) == 2,
                "Plain storage can return its ordinary items");
        });
    }

    @GameTest(template = "test_fractionation")
    public static void survivalHandsStoreAndRecoverItemsButHeldVesselsStillPlaceAWholeLayer(GameTestHelper h) {
        var owner = square(h, BASE, 2, 1);
        var player = player(h);
        var iron = new ItemStack(Items.IRON_INGOT, 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, iron);
        h.assertTrue(h.getBlockState(BASE).useItemOn(iron, h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h, BASE, Direction.WEST))
            == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && iron.getCount() == 8 && count(items(h, BASE, Direction.WEST), Items.IRON_INGOT) == 0,
            "A closed hatch refuses generic manual storage without consuming the held stack");
        owner.toggleLid();
        h.assertTrue(h.getBlockState(BASE).useItemOn(iron, h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h, BASE, Direction.WEST)).consumesAction()
            && iron.isEmpty() && count(items(h, BASE.east(), Direction.EAST), Items.IRON_INGOT) == 8 && owner.open(),
            "An open plain vessel accepts the actual survival stack through its member wall");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);player.setShiftKeyDown(true);
        h.assertTrue(h.getBlockState(BASE).useWithoutItem(h.getLevel(), player, hit(h, BASE, Direction.WEST)).consumesAction()
            && player.getInventory().countItem(Items.IRON_INGOT) == 8 && count(items(h, BASE, Direction.WEST), Items.IRON_INGOT) == 0 && owner.open(),
            "Sneaking with empty hands returns the stored stack without changing the hatch");
        player.setShiftKeyDown(false);
        var vessels = new ItemStack(CETransport.STIRRED_TANK.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, vessels);
        h.assertTrue(h.getBlockState(BASE).useItemOn(vessels, h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h, BASE, Direction.UP))
            == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && vessels.getCount() == 4,
            "The generic storage interaction leaves vessel placement to the held block item");
        h.assertTrue(vessels.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(h, BASE, Direction.UP))).consumesAction()
            && vessels.isEmpty() && owner.getHeight() == 2 && count(items(h, BASE, Direction.WEST), CETransport.STIRRED_TANK.get().asItem()) == 0,
            "The four held vessels become a physical 2x2 upper layer and are never swallowed as stored items");
        h.succeed();
    }

    private static int accessible(GameTestHelper h, Item item) {
        var seen = new HashSet<BlockPos>();
        int total = 0;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos at = BASE.offset(x, 0, z);
            if (!(h.getLevel().getBlockEntity(h.absolutePos(at)) instanceof LiddedVesselBlockEntity part)) continue;
            var owner = part.getControllerBE();
            h.assertTrue(owner != null, "Every surviving member still resolves its inventory controller");
            if (seen.add(owner.getBlockPos())) total += count(items(h, at, Direction.NORTH), item);
        }
        return total;
    }

    @GameTest(template = "test_fractionation")
    public static void joiningAndSplittingCannotHideOrDeletePlainInventories(GameTestHelper h) {
        h.setBlock(BASE, CETransport.STIRRED_TANK.get());
        h.setBlock(BASE.offset(1, 0, 1), CETransport.STIRRED_TANK.get());
        h.assertTrue(items(h, BASE, Direction.NORTH).insertItem(0, new ItemStack(Items.IRON_INGOT, 7), false).isEmpty()
            && items(h, BASE.offset(1, 0, 1), Direction.NORTH).insertItem(0, new ItemStack(Items.GOLD_INGOT, 5), false).isEmpty(),
            "Two separate diagonal single vessels own distinct ordinary inventories");
        h.setBlock(BASE.east(), CETransport.STIRRED_TANK.get());
        h.setBlock(BASE.south(), CETransport.STIRRED_TANK.get());
        var joined = ((LiddedVesselBlockEntity) h.getBlockEntity(BASE)).getControllerBE();
        h.assertTrue(joined.getWidth() == 2 && count(items(h, BASE, Direction.NORTH), Items.IRON_INGOT) == 7
            && count(items(h, BASE, Direction.NORTH), Items.GOLD_INGOT) == 5
            && accessible(h, Items.IRON_INGOT) == 7 && accessible(h, Items.GOLD_INGOT) == 5,
            "Completing the square joins both inventories into the 2x2 controller and keeps every original item accessible");
        h.setBlock(BASE.east(), Blocks.AIR);
        h.assertTrue(accessible(h, Items.IRON_INGOT) == 7 && accessible(h, Items.GOLD_INGOT) == 5,
            "Splitting at an originally empty cell preserves all seven iron and five gold across accessible controllers");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 260)
    public static void aFiniteCreatePumpFillsThePlainVesselWithoutLosingWater(GameTestHelper h) {
        var owner = square(h, BASE, 2, 1);
        BlockPos source = BASE.west(3), pump = BASE.west(2), pipe = BASE.west();
        h.setBlock(source, AllBlocks.FLUID_TANK.get());
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        var pipeState = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction d : Direction.values()) pipeState = pipeState.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(d), d.getAxis() == Direction.Axis.X);
        h.setBlock(pipe, pipeState);
        var from = ((FluidTankBlockEntity) h.getBlockEntity(source)).getTankInventory();
        h.assertTrue(from.fill(new FluidStack(Fluids.WATER, 4000), EXECUTE) == 4000, "The source contains only a finite four buckets of water");
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        motor(h, pump.south().west(), Direction.EAST);
        h.runAfterDelay(200, () -> {
            int received = owner.getTankInventory().getFluidAmount();
            h.assertTrue(received > 0 && received + from.getFluidAmount() == 4000,
                "The native driven pump fills storage through its member wall and conserves all water: " + received + " + " + from.getFluidAmount());
            h.assertTrue(!owner.stirred(), "A storage vessel needs no stirrer for pipe input");
            h.succeed();
        });
    }

    private static class ContainerMixingParams extends ProcessingRecipeParams {
        ContainerMixingParams() {
            ingredients.add(Ingredient.of(Items.MILK_BUCKET));
            ingredients.add(Ingredient.of(Items.DRAGON_BREATH));
            fluidIngredients.add(SizedFluidIngredient.of(Fluids.WATER, 100));
            results.add(new ProcessingOutput(new ItemStack(Items.SPONGE), 1));
            requiredHeat = HeatCondition.HEATED;
            processingDuration = 80;
        }
    }

    /** Test-only recipe; retain every currently installed recipe, including other tests' temporary entries. */
    private static void installContainerRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        recipes.removeIf(recipe -> recipe.id().equals(CONTAINER_RECIPE));
        recipes.add(new RecipeHolder<>(CONTAINER_RECIPE, new MixingRecipe(new ContainerMixingParams())));
        manager.replaceRecipes(recipes);
    }

    private static void removeContainerRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        recipes.removeIf(recipe -> recipe.id().equals(CONTAINER_RECIPE));
        manager.replaceRecipes(recipes);
    }

    private static void guarded(GameTestHelper h, Runnable step) {
        try { step.run(); } catch (RuntimeException | Error failure) { removeContainerRecipe(h);throw failure; }
    }

    private static void blockProducts(GameTestHelper h, LiddedVesselBlockEntity owner) {
        var tag = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        var inventory = new ItemStackHandler(13);
        inventory.deserializeNBT(h.getLevel().registryAccess(), tag.getCompound("Charge"));
        for (int slot = 9; slot < 12; slot++) inventory.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setStackInSlot(12, ItemStack.EMPTY);
        tag.put("Charge", inventory.serializeNBT(h.getLevel().registryAccess()));
        owner.loadWithComponents(tag, h.getLevel().registryAccess());
        owner.validateStructure();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 360)
    public static void nativeMixingUsesPreloadedWaterRealFuelAndAtomicContainerRemainders(GameTestHelper h) {
        installContainerRecipe(h);
        guarded(h, () -> {
            var owner = square(h, BASE, 3, 2);
            var raw = items(h, BASE, Direction.WEST);
            h.assertTrue(raw.insertItem(0, new ItemStack(Items.MILK_BUCKET), true).isEmpty() && raw.getStackInSlot(0).isEmpty(),
                "Plain inventory simulation accepts without consuming or creating an item");
            h.assertTrue(raw.insertItem(0, new ItemStack(Items.MILK_BUCKET), false).isEmpty()
                && raw.insertItem(1, new ItemStack(Items.DRAGON_BREATH), false).isEmpty(), "Ordinary ingredients can be put in before installing the impeller");
            h.assertTrue(owner.getTankInventory().fill(new FluidStack(Fluids.WATER, 10000), SIMULATE) == 10000 && owner.getTankInventory().isEmpty(),
                "Simulated prefill changes no fluid");
            owner.getTankInventory().fill(new FluidStack(Fluids.WATER, 10000), EXECUTE);
            stir(h, owner);
            h.assertTrue(owner.formed() && owner.getTankInventory().getFluidAmount() == 10000
                && count(items(h, BASE, Direction.NORTH), Items.MILK_BUCKET) == 1, "Installing the impeller preserves and uses the existing storage rather than moving or rejecting it");
            roofMotor(h);
            BlockPos burnerPos = BASE.below();
            h.setBlock(burnerPos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            h.runAfterDelay(60, () -> guarded(h, () -> {
                var port = items(h, BASE, Direction.NORTH);
                h.assertTrue(Math.abs(owner.rotationSpeed()) == 128 && !owner.running() && owner.progress() == 0
                    && count(port, Items.MILK_BUCKET) == 1 && count(port, Items.DRAGON_BREATH) == 1
                    && owner.getTankInventory().getFluidAmount() == 10000, "Real rotation without burner fuel cannot consume the heated mixing ingredients");
                blockProducts(h, owner);
                var coal = new ItemStack(Items.COAL);
                var result = BlazeBurnerBlock.tryInsert(h.getBlockState(burnerPos), h.getLevel(), h.absolutePos(burnerPos), coal, false, false, false);
                h.assertTrue(result.getResult().consumesAction() && coal.isEmpty(), "The real burner consumes a real coal item");
            }));
            h.runAfterDelay(140, () -> guarded(h, () -> {
                var port = items(h, BASE, Direction.NORTH);
                var burner = (BlazeBurnerBlockEntity) h.getBlockEntity(burnerPos);
                h.assertTrue(burner.getRemainingBurnTime() > 0 && burner.getHeatLevelFromBlock().isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED),
                    "Native fuel ticks keep the burner hot");
                h.assertTrue(count(port, Items.MILK_BUCKET) == 1 && count(port, Items.DRAGON_BREATH) == 1 && owner.getTankInventory().getFluidAmount() == 10000,
                    "One free output slot fits the main result but not both empty containers, so every ingredient stays untouched");
                h.assertTrue(port.getStackInSlot(12).isEmpty(), "A failed transaction creates no partial main product");
                for (int slot = 9; slot < 12; slot++) h.assertTrue(port.extractItem(slot, 64, false).getCount() == 64, "The three blocking stacks can be extracted through the real product port");
            }));
            h.runAfterDelay(240, () -> {
                try {
                    var port = items(h, BASE, Direction.NORTH);
                    h.assertTrue(count(port, Items.MILK_BUCKET) == 0 && count(port, Items.DRAGON_BREATH) == 0
                        && count(port, Items.SPONGE) == 1 && count(port, Items.BUCKET) == 1 && count(port, Items.GLASS_BOTTLE) == 1
                        && water(owner) == 9900, "Exactly one native mixing batch consumes 100 mB of the preloaded water and preserves both empty containers: " + snapshot(owner));
                    h.succeed();
                } finally { removeContainerRecipe(h); }
            });
        });
    }

    private static double gasMass(IFluidHandler port, Fluid gas) {
        double mass = 0;
        for (int tank = 0; tank < port.getTanks(); tank++) if (port.getFluidInTank(tank).is(gas)) mass += FluidMaterials.mass(port.getFluidInTank(tank));
        return mass;
    }

    private static double connectedHydrogen(GameTestHelper h, LiddedVesselBlockEntity owner, FluidTankBlockEntity source) {
        double mass = gasMass(source.getTankInventory(), CEFluids.HYDROGEN_GAS.get())
            + gasMass(owner.getTankInventory(), CEFluids.HYDROGEN_GAS.get()) + gasMass(owner.port(Direction.WEST), CEFluids.HYDROGEN_GAS.get());
        return mass;
    }

    @GameTest(template = "test_fractionation")
    public static void stirredGasProductLeavesBelowTheLighterGas(GameTestHelper h) {
        var vessel = ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 1);
        ReactorGameTests.heat(vessel, HeatCondition.HEATED);
        var inlet = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, vessel.getBlockPos(), vessel.inlet());
        h.assertTrue(inlet.fill(new FluidStack(CEFluids.AIR_GAS.get(), 3000), EXECUTE) == 3000
            && vessel.itemPort().insertItem(0, net.createplant.registry.CEItems.SULFUR_DUST.toStack(), false).isEmpty(),
            "A stirred vessel accepts a finite charge with some gas left over");
        ReactorGameTests.speed(vessel, 32);
        for (int i = 0; i < 130; i++) vessel.tickProcess();
        var center = h.absolutePos(BASE.offset(1, 0, 1));
        var top = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, center, Direction.UP);
        var bottom = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, center, Direction.DOWN);
        var light = top.drain(600, EXECUTE);
        var heavy = bottom.drain(600, EXECUTE);
        var residual = new java.util.TreeMap<>(MixtureHelper.airContents(3000).amounts());
        residual.compute(BuiltInRegistries.FLUID.getKey(CEFluids.OXYGEN_GAS.get()), (id, amount) -> amount - 500);
        h.assertTrue(light.getAmount() == 600 && MixtureHelper.extractContents(light).equals(new MixtureContents(residual).split(600).extracted())
            && heavy.is(net.createplant.registry.CEUranium.SULFUR_DIOXIDE.get()) && heavy.getAmount() == 500,
            "The upper outlet exports the residual gas as a mixture while the lower outlet exports the separate sulfur dioxide product");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void cachedMemberPhasePortsCannotExtractAfterTheProcessReforms(GameTestHelper h) {
        var owner = square(h, BASE, 3, 2);stir(h, owner);
        // Seed one finite wet product hold-up; no motor or heat source can change it during the lifecycle assertions.
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        var wet = new FluidStack(Fluids.WATER,1000);
        saved.put("Tank3",FluidForm.STEAM.stack(100).save(h.getLevel().registryAccess()));
        saved.put("Tank2", wet.save(h.getLevel().registryAccess()));
        owner.loadWithComponents(saved, h.getLevel().registryAccess());owner.validateStructure();
        BlockPos member = BASE.offset(1, 0, 1);
        var oldTop = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(member), Direction.UP);
        var oldSide = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(member), Direction.EAST);
        h.assertTrue(oldTop != null && oldSide != null && FluidForm.STEAM.matches(oldTop.drain(1, SIMULATE))
            && oldSide.drain(1, SIMULATE).is(Fluids.WATER), "A live member exposes separate real steam and liquid product phases");
        int generation = owner.portGeneration();
        h.getLevel().removeBlock(owner.stirrerPos(), false);owner.validateStructure();
        h.assertTrue(!owner.formed(), "Taking the stirrer away ends the old process lifetime");
        stir(h, owner);
        h.assertTrue(owner.formed() && owner.portGeneration() != generation, "Putting the stirrer back forms a new process lifetime on the same owner");
        var top = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(member), Direction.UP);
        var side = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(member), Direction.EAST);
        h.assertTrue(top != null && side != null && FluidForm.STEAM.matches(top.drain(1, SIMULATE))
            && side.drain(1, SIMULATE).is(Fluids.WATER), "Fresh member capabilities reach both retained phases after reformation");
        var before = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        for (var action : IFluidHandler.FluidAction.values()) {
            h.assertTrue(oldTop.drain(1, action).isEmpty() && oldTop.drain(FluidForm.STEAM.stack(1), action).isEmpty()
                && oldSide.drain(1, action).isEmpty() && oldSide.drain(new FluidStack(Fluids.WATER, 1), action).isEmpty(),
                "Cached phase-aware member ports cannot simulate or execute either typed or untyped extraction after reformation: " + action);
        }
        for (var stale : new IFluidHandler[]{oldTop, oldSide}) for (int tank = 0; tank < stale.getTanks(); tank++)
            h.assertTrue(stale.getFluidInTank(tank).isEmpty(), "Stale member capabilities expose no current process inventory");
        h.assertTrue(before.equals(owner.saveWithoutMetadata(h.getLevel().registryAccess()))
            && owner.port(Direction.NORTH).getFluidInTank(2).getAmount() == 1000
            && Math.abs(FluidThermal.energy(owner.port(Direction.NORTH).getFluidInTank(2)) - FluidThermal.energy(wet)) < .001,
            "Rejected stale capabilities leave the new owner's full material, energy and saved state untouched");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 440)
    public static void aNativePumpReachesTheSecondFeedAndConservesGas(GameTestHelper h) {
        var owner=square(h,BASE,3,2);stir(h,owner);
        owner.port(Direction.WEST).fill(new FluidStack(Fluids.WATER,500),EXECUTE);
        var sourcePos=BASE.west(4);h.setBlock(sourcePos,CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.X));var source=((FluidTankBlockEntity)h.getBlockEntity(sourcePos)).getTankInventory();source.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),40000),EXECUTE);
        for(int offset:new int[]{1,3}){var state=CETransport.STAINLESS_PIPE.get().defaultBlockState();for(Direction side:Direction.values())state=state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side),side.getAxis()==Direction.Axis.X);h.setBlock(BASE.west(offset),state);}
        var pump=BASE.west(2);var cog=pump.above();var motor=cog.west();
        h.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.EAST));
        h.setBlock(cog,AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.runAfterDelay(180,()->{var current=owner.port(Direction.WEST);int received=current.getFluidInTank(1).getAmount();
            h.assertTrue(current.getFluidInTank(1).is(CEFluids.HYDROGEN_GAS.get()) && received>0 && current.getFluidInTank(0).getAmount()==500,"The native pump reaches gas feed one while feed zero contains water");
            h.assertTrue(received+source.getFluidAmount()==40000,"Native gas pipes contain no hidden material; source and feed close their ledger");h.succeed();});
    }
}
