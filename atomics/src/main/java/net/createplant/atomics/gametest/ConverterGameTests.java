package net.createplant.atomics.gametest;
import net.createplant.atomics.registry.CEAtomicsRecipeTypes;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;

import net.createplant.CreatePlant;
import net.createplant.ponder.ManufacturingRecipe;
import net.createplant.atomics.block.ConverterShellBlock;
import net.createplant.atomics.block.ConverterShellBlock.Part;
import net.createplant.atomics.block.ConverterStructure;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.createplant.atomics.recipe.ConverterRecipe;
import net.createplant.atomics.registry.CEConverter;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CERecipeTypes;
import net.createplant.registry.CETransport;
import net.createplant.thermal.ThermalEquipment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Real Create-contraption assembly, charge accounting, persistence and teardown of the converter. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConverterGameTests {
    private static final BlockPos ORIGIN = new BlockPos(15, 2, 15);
    private static final Direction FACING = Direction.NORTH;

    private static ConverterTrunnionBearingBlockEntity build(GameTestHelper helper, boolean connectGas, boolean glue) {
        for (ConverterStructure.Cell cell : ConverterStructure.cells())
            helper.setBlock(ConverterStructure.at(ORIGIN, FACING, cell.x(), cell.y(), cell.z()),
                ConverterStructure.stateFor(cell, FACING));
        if (connectGas) helper.setBlock(ConverterStructure.gasPort(ORIGIN, FACING).relative(FACING.getOpposite()),
            CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,FACING.getAxis()));
        if (glue) glue(helper, List.of());
        ConverterTrunnionBearingBlockEntity controller = controller(helper);
        controller.validateStructure();
        return controller;
    }

    private static ConverterTrunnionBearingBlockEntity controller(GameTestHelper helper) {
        return (ConverterTrunnionBearingBlockEntity) helper.getBlockEntity(ConverterStructure.bearing(ORIGIN, FACING));
    }

    private static void glue(GameTestHelper helper, List<BlockPos> extra) {
        List<BlockPos> positions = new ArrayList<>();
        for (ConverterStructure.Cell cell : ConverterStructure.movingCells())
            positions.add(helper.absolutePos(ConverterStructure.at(ORIGIN, FACING, cell.x(), cell.y(), cell.z())));
        for (BlockPos pos : extra) positions.add(helper.absolutePos(pos));
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        AABB box = SuperGlueEntity.span(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), box));
    }

    private static void drive(ConverterTrunnionBearingBlockEntity controller, float speed, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            controller.setSpeedForTest(speed);
            controller.tick();
        }
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), side);
    }

    private static void insert(IItemHandler port, Item item, int count) {
        ItemStack left = new ItemStack(item, count);
        for (int slot = 0; slot < port.getSlots() && !left.isEmpty(); slot++) left = port.insertItem(slot, left, false);
        if (!left.isEmpty()) throw new IllegalStateException("Converter input could not hold " + count + " " + item);
    }

    private static int products(ConverterTrunnionBearingBlockEntity converter) {
        int count = 0;
        for (int slot = ConverterTrunnionBearingBlockEntity.INPUT_SLOTS;
             slot < ConverterTrunnionBearingBlockEntity.INPUT_SLOTS + ConverterTrunnionBearingBlockEntity.OUTPUT_SLOTS; slot++)
            count += converter.inventory().getStackInSlot(slot).getCount();
        return count;
    }

    private static int inputs(ConverterTrunnionBearingBlockEntity converter) {
        int count = 0;
        for (int slot = 0; slot < ConverterTrunnionBearingBlockEntity.INPUT_SLOTS; slot++)
            count += converter.inventory().getStackInSlot(slot).getCount();
        return count;
    }

    private static IFluidHandler metalFeed(GameTestHelper helper) {
        return fluids(helper, ConverterStructure.rawPort(ORIGIN, FACING), FACING.getClockWise());
    }

    private static IFluidHandler metalTap(GameTestHelper helper) {
        return fluids(helper, ConverterStructure.tap(ORIGIN, FACING), FACING);
    }

    private static FluidStack desulfurized() {
        return new FluidStack(CEProcessFluids.DESULFURIZED_HOT_METAL.get(), 6_400);
    }

    private static void oxygenCharge(GameTestHelper helper, boolean liquid) {
        IItemHandler raw = items(helper, ConverterStructure.rawPort(ORIGIN, FACING), Direction.UP);
        insert(raw, Items.IRON_INGOT, 8);
        insert(raw, CEItems.QUICKLIME.get(), 4);
        if (metalFeed(helper).fill(desulfurized(), EXECUTE) != 6_400) throw new IllegalStateException("The liquid inlet rejected hot metal");
        IFluidHandler lance = fluids(helper, ConverterStructure.lance(ORIGIN, FACING), Direction.UP);
        FluidStack oxygen = liquid ? net.createplant.thermal.FluidForm.LIQUID_OXYGEN.stack(92) : new FluidStack(CEFluids.OXYGEN_GAS.get(), 64_000);
        if (lance.fill(oxygen, EXECUTE) != oxygen.getAmount()) throw new IllegalStateException("Converter rejected oxygen charge");
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void exactGluedVesselTiltsAndReturnsAsBlocks(GameTestHelper helper) {
        // Converter checks that used to sit in the core recipe and ponder tests.
        for (var holder : helper.getLevel().getRecipeManager().getRecipes())
            if (holder.id().getNamespace().equals(CreatePlant.MOD_ID) && holder.value() instanceof ConverterRecipe converter && converter.gasIngredient().ingredient().hasNoFluids())
                helper.assertTrue(false, "Converter gas input resolves to nothing: " + holder.id());
        {
            var level = helper.getLevel();
        var converter = ManufacturingRecipe.resolve(level, CreatePlant.MOD_ID + ":converter/refine_hot_metal");
        var actual = (net.createplant.atomics.recipe.ConverterRecipe) level.getRecipeManager().byKey(
            CreatePlant.location("converter/refine_hot_metal")).orElseThrow().value();
        helper.assertTrue(converter.items().getFirst().getCount() == actual.itemIngredients().getFirst().count(),
            "Bulk converter charges retain sized ingredient quantities");
        }
        ConverterTrunnionBearingBlockEntity converter = build(helper, false, true);
        helper.assertTrue(converter.formed(), "The fixed frame and upright vessel form");
        helper.assertTrue(ConverterStructure.movingCells().stream().filter(cell -> cell.material() == ConverterStructure.Material.STEEL).count() == 75
            && ConverterStructure.movingCells().stream().filter(cell -> cell.material() == ConverterStructure.Material.REFRACTORY).count() == 32,
            "The real vessel uses 75 steel blocks and 32 refractory bricks");
        drive(converter, 32, 10);
        helper.assertTrue(converter.isRunning() && converter.angle() == 60 && converter.getMovedContraption() != null,
            "The glued blocks become a real BearingContraption and stop at 60 degrees");
        helper.assertTrue(helper.getBlockState(ConverterStructure.anchor(ORIGIN, FACING)).isAir(),
            "The assembled vessel is removed from the stationary world");
        drive(converter, -32, 10);
        helper.assertTrue(!converter.isRunning() && converter.angle() == 0,
            "Reverse rotation returns to zero and disassembles");
        for (ConverterStructure.Cell cell : ConverterStructure.movingCells())
            helper.assertTrue(helper.getBlockState(ConverterStructure.at(ORIGIN, FACING, cell.x(), cell.y(), cell.z()))
                .is(ConverterStructure.stateFor(cell, FACING).getBlock()), "Every vessel block returns to its original position");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void assemblyRejectsUngluedMissingWrongAndExtraBlocks(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, false, false);
        converter.setSpeedForTest(32);
        converter.assemble();
        helper.assertTrue(!converter.isRunning() && converter.getLastAssemblyException() != null,
            "An unglued vessel is rejected");

        BlockPos changed = ConverterStructure.at(ORIGIN, FACING, 0, 6, 1);
        helper.setBlock(changed, Blocks.AIR);
        glue(helper, List.of());
        converter.assemble();
        helper.assertTrue(!converter.isRunning(), "A missing refractory brick is rejected");

        helper.setBlock(changed, Blocks.COPPER_BLOCK);
        converter.assemble();
        helper.assertTrue(!converter.isRunning(), "A copper block in place of refractory is rejected");

        helper.setBlock(changed, CEConverter.REFRACTORY_BRICKS.get().defaultBlockState());
        BlockPos extra = ConverterStructure.at(ORIGIN, FACING, 0, 5, 3);
        helper.setBlock(extra, CEMetals.STEEL_BLOCK.get().getBlock().defaultBlockState());
        glue(helper, List.of(extra));
        converter.assemble();
        helper.assertTrue(!converter.isRunning(), "An extra glued steel block is rejected");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 120)
    public static void oxygenAndLiquidOxygenMakeMoltenSteelSlagAndOffgas(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, true, true);
        helper.assertTrue(helper.getBlockState(ConverterStructure.lance(ORIGIN, FACING)).getValue(ConverterShellBlock.PART) == Part.LANCE
            && helper.getBlockState(ConverterStructure.tap(ORIGIN, FACING)).getValue(ConverterShellBlock.PART) == Part.TAP,
            "Formation assigns the stationary lance and tapping port");
        oxygenCharge(helper, false);
        converter.runProcess(800);
        helper.assertTrue(products(converter) == 8 && converter.inventory().getStackInSlot(ConverterTrunnionBearingBlockEntity.INPUT_SLOTS)
            .is(CEItems.STEEL_SLAG.get()) && metalTap(helper).getFluidInTank(0).getAmount() == 6_400,
            "One oxygen charge makes 6,400 mB of molten steel and 8 slag");
        helper.assertTrue(converter.gasOutput().getFluidAmount() == 128_000
            && converter.gasOutput().getFluid().is(CEFluids.CONVERTER_GAS.get()),
            "One oxygen charge retains exactly 128,000 mB converter gas");
        IItemHandler tap = items(helper, ConverterStructure.tap(ORIGIN, FACING), Direction.DOWN);
        helper.assertTrue(tap.extractItem(0, 64, true).isEmpty() && metalTap(helper).drain(6_400, EXECUTE).isEmpty(),
            "The upright converter keeps both products in the furnace");

        drive(converter, 32, 10);
        int tapped = 0;
        for (int slot = 0; slot < tap.getSlots(); slot++) tapped += tap.extractItem(slot, 64, false).getCount();
        var molten = metalTap(helper).drain(6_400, EXECUTE);
        helper.assertTrue(tapped == 8 && molten.is(CEProcessFluids.MOLTEN_STEEL.get()) && molten.getAmount() == 6_400,
            "The tilted outlet supplies all steel through the fluid port and slag through the chute");
        helper.assertTrue(items(helper, ConverterStructure.rawPort(ORIGIN, FACING), Direction.UP)
            .insertItem(0, new ItemStack(Items.IRON_INGOT), true).getCount() == 1
            && fluids(helper, ConverterStructure.lance(ORIGIN, FACING), Direction.UP)
                .fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 1), IFluidHandler.FluidAction.SIMULATE) == 0,
            "A tilted converter accepts neither raw material nor blowing gas");
        fluids(helper, ConverterStructure.gasPort(ORIGIN, FACING), FACING.getOpposite()).drain(128_000, EXECUTE);
        drive(converter, -32, 10);
        oxygenCharge(helper, true);
        converter.runProcess(800);
        helper.assertTrue(products(converter) == 8 && metalTap(helper).getFluidInTank(0).getAmount() == 6_400
            && converter.gasOutput().getFluidAmount() == 128_000
            && converter.gasInput().isEmpty(), "92 mB liquid oxygen supplies the same charge without duplication");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void argonChargeMakesOneHundredNinetyTwoStainlessIngots(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, false, true);
        IItemHandler raw = items(helper, ConverterStructure.rawPort(ORIGIN, FACING), Direction.UP);
        insert(raw, Items.IRON_INGOT, 42);
        insert(raw, CEItems.CHROMIUM_INGOT.get(), 12);
        insert(raw, CEItems.NICKEL_INGOT.get(), 6);
        IFluidHandler lance = fluids(helper, ConverterStructure.lance(ORIGIN, FACING), Direction.UP);
        helper.assertTrue(lance.fill(new FluidStack(CEFluids.ARGON_GAS.get(), 600), EXECUTE) == 600,
            "The lance accepts one AOD argon charge");
        converter.runProcess(800);
        var tapped = metalTap(helper).getFluidInTank(0);
        helper.assertTrue(products(converter) == 2 && tapped.is(net.createplant.registry.CEProcessFluids.MOLTEN_STAINLESS_STEEL.get())
                && tapped.getAmount() == 4_800 && converter.gasOutput().isEmpty(),
            "AOD taps 4,800 mB of molten stainless steel and two slag without converter gas: " + tapped.getAmount());
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 120)
    public static void tiltPausesChargeAndReloadReattachesContraption(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, true, true);
        oxygenCharge(helper, false);
        converter.runProcess(100);
        helper.assertTrue(converter.progress() == 100 && converter.gasInput().isEmpty(),
            "Starting consumes the complete charge and records progress");
        drive(converter, 32, 10);
        converter.runProcess(100);
        helper.assertTrue(converter.progress() == 100 && converter.angle() == 60,
            "Tilting pauses the active charge without losing progress: progress=" + converter.progress()
                + ", angle=" + converter.angle() + ", running=" + converter.isRunning()
                + ", formed=" + converter.formed() + ", status=" + converter.status());
        ControlledReload.reload(helper, converter);
        helper.assertTrue(converter.progress() == 100 && converter.angle() == 60 && converter.isRunning()
            && converter.getMovedContraption() != null, "Reload keeps contents, angle and the attached contraption");
        drive(converter, -32, 10);
        converter.runProcess(700);
        helper.assertTrue(products(converter) == 8 && metalTap(helper).getFluidInTank(0).getAmount() == 6_400
            && converter.gasOutput().getFluidAmount() == 128_000,
            "The paused charge completes once after the vessel returns upright");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void breakingBearingReturnsVesselAndReleasesContentsOnce(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, true, true);
        converter.inventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 10));
        converter.inventory().setStackInSlot(ConverterTrunnionBearingBlockEntity.INPUT_SLOTS,
            new ItemStack(CEItems.STAINLESS_STEEL_INGOT.get(), 5));
        converter.gasInput().fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 1_000), EXECUTE);
        converter.gasOutput().fill(new FluidStack(CEFluids.CONVERTER_GAS.get(), 2_000), EXECUTE);
        drive(converter, 32, 10);
        helper.setBlock(ConverterStructure.bearing(ORIGIN, FACING), Blocks.AIR);
        for (ConverterStructure.Cell cell : ConverterStructure.movingCells())
            helper.assertTrue(helper.getBlockState(ConverterStructure.at(ORIGIN, FACING, cell.x(), cell.y(), cell.z()))
                .is(ConverterStructure.stateFor(cell, FACING).getBlock()), "Breaking the bearing returns the upright vessel");
        int itemCount = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).stream()
            .mapToInt(entity -> entity.getItem().getCount()).sum();
        var clouds = net.createplant.cloud.GasClouds.in(helper.getLevel(), helper.getBounds());
        helper.assertTrue(itemCount == 15, "The owner drops every retained item exactly once: " + itemCount);
        helper.assertTrue(clouds.size() == 1, "Converter gas becomes exactly one toxic cloud");
        helper.setBlock(ConverterStructure.passiveBearing(ORIGIN, FACING), Blocks.AIR);
        int after = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).stream()
            .mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(after == itemCount
            && net.createplant.cloud.GasClouds.in(helper.getLevel(), helper.getBounds()).size() == 1,
            "Breaking a surviving stationary part duplicates neither items nor gas");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void breakingAnActiveConverterReturnsTheReservedCharge(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, true, true);
        oxygenCharge(helper, false);
        converter.runProcess(100);
        helper.assertTrue(converter.progress() == 100 && inputs(converter) == 0 && converter.gasInput().isEmpty(),
            "The active batch has moved its complete charge out of the input ports");
        var saved = converter.saveWithoutMetadata(helper.getLevel().registryAccess());
        converter.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.setBlock(ConverterStructure.bearing(ORIGIN, FACING), Blocks.AIR);
        int iron = 0, lime = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
            if (entity.getItem().is(Items.IRON_INGOT)) iron += entity.getItem().getCount();
            if (entity.getItem().is(CEItems.QUICKLIME.get())) lime += entity.getItem().getCount();
        }
        helper.assertTrue(iron == 8 && lime == 4,
            "Breaking a saved active converter returns all reserved iron and lime once: " + iron + "/" + lime);
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void blockedOrDisconnectedChargesConsumeNothing(GameTestHelper helper) {
        ConverterTrunnionBearingBlockEntity converter = build(helper, true, true);
        IItemHandler raw = items(helper, ConverterStructure.rawPort(ORIGIN, FACING), Direction.UP);
        insert(raw, Items.IRON_INGOT, 7);
        insert(raw, CEItems.QUICKLIME.get(), 4);
        helper.assertTrue(metalFeed(helper).fill(new FluidStack(CEProcessFluids.MOLTEN_PIG_IRON.get(), 6_400), EXECUTE) == 0,
            "Unpretreated pig iron cannot start a converter charge");
        helper.assertTrue(metalFeed(helper).fill(desulfurized(), EXECUTE) == 6_400, "The correct liquid feed fills the inlet");
        fluids(helper, ConverterStructure.lance(ORIGIN, FACING), Direction.UP)
            .fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 64_000), EXECUTE);
        converter.runProcess(800);
        helper.assertTrue(inputs(converter) == 11 && converter.gasInput().getFluidAmount() == 64_000,
            "An incomplete charge consumes neither items nor oxygen");
        insert(raw, Items.IRON_INGOT, 1);
        converter.gasOutput().fill(new FluidStack(CEFluids.CONVERTER_GAS.get(), 128_000), EXECUTE);
        converter.runProcess(800);
        helper.assertTrue(inputs(converter) == 12 && converter.gasInput().getFluidAmount() == 64_000,
            "A full offgas buffer consumes nothing");
        converter.gasOutput().setFluid(FluidStack.EMPTY);
        helper.setBlock(ConverterStructure.gasPort(ORIGIN, FACING).relative(FACING.getOpposite()), Blocks.AIR);
        converter.runProcess(800);
        helper.assertTrue(converter.gasInput().getFluidAmount() == 64_000 && converter.status().equals("gas_disconnected"),
            "A disconnected outlet stops without venting or consuming the charge");
        helper.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void converterRecipesLoadAndGasIsARealHeaterFuel(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(
            CEAtomicsRecipeTypes.CONVERTER.<RecipeInput, ConverterRecipe>getType());
        helper.assertTrue(recipes.stream().filter(recipe -> !recipe.value().legacy()).count() == 2,
            "The current steel and AOD charges both load");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CreatePlant.location("pressure_reaction/stainless_steel_ingot_aod")).isEmpty(),
            "The retired stirred-reactor AOD recipe remains absent");
        double value = ThermalEquipment.fuelValue(new FluidStack(CEFluids.CONVERTER_GAS.get(), 1));
        helper.assertTrue(value > 11 && value < 12,
            "CO fuel value follows 283 kJ/mol at the gas reference density: " + value + " J/mB");
        double batch = value * 128_000;
        helper.assertTrue(batch > 1_500_000 && batch < 1_510_000,
            "One offgas batch contains about 1.5 MJ of chemical energy");
        helper.succeed();
    }

    private static final class ControlledReload {
        private ControlledReload() { }

        private static void reload(GameTestHelper helper, ConverterTrunnionBearingBlockEntity converter) {
            var entity = converter.getMovedContraption();
            var saved = converter.saveWithoutMetadata(helper.getLevel().registryAccess());
            converter.loadWithComponents(saved, helper.getLevel().registryAccess());
            converter.attach(entity);
        }
    }
}
