package net.createplant.atomics.gametest;

import net.createplant.gametest.PowerPlantGameTests;
import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.createplant.block.*;
import net.createplant.atomics.block.*;
import net.createplant.block.entity.*;
import net.createplant.atomics.block.entity.*;
import net.createplant.registry.*;
import net.createplant.atomics.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.gametest.PowerPlantGameTests.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.atomics.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.atomics.block.entity.CraneDriveBlockEntity;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.atomics.fusion.FusionStructure;
import net.createplant.atomics.registry.CECrane;
import net.createplant.atomics.registry.CEFuelCycle;
import net.createplant.atomics.registry.CEFusion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerPlantAtomicsGameTests {
    @GameTest(template = "test_rbmk")
    public static void fuelCycleRecipesLoadAndMoxIdentitySurvivesStorage(GameTestHelper h) {
        for (String recipe : new String[]{"cutting/spent_fuel_shearing", "pressure_reaction/spent_fuel_reprocessing", "solvent_extraction/purex_partition",
            "pressure_reaction/plutonium_oxide_recovery", "mixing/mox_blending", "pressing/green_mox_pellet", "pressure_reaction/mox_sintering", "mox_fuel_rod", "vitrification/high_level_waste_glass"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location(recipe)).isPresent(), "The complete survival fuel-cycle recipe loads: " + recipe);
        BlockPos base = new BlockPos(5, 4, 5);FuelPoolGameTests.build(h, base, 2, 2);
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(base), Direction.DOWN);
        fluid.fill(water(4 * FuelPoolBlockEntity.WATER_PER_CELL, 293.15), EXECUTE);
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(base), Direction.UP);
        h.assertTrue(items.insertItem(0, new ItemStack(CEFuelCycle.MOX_FUEL_ROD.get()), false).isEmpty(), "The pool accepts MOX assemblies");
        var pool = (FuelPoolBlockEntity)h.getBlockEntity(base);var saved = pool.saveWithoutMetadata(h.getLevel().registryAccess());pool.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(items.getStackInSlot(0).is(CEFuelCycle.MOX_FUEL_ROD.get()), "Pool reload retains MOX identity instead of converting it into uranium fuel");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void actualDimensionsAndQuantizedPidLoop(GameTestHelper h) {
        h.assertTrue(CoolingTowerStructure.HEIGHT == 36 && CoolingTowerStructure.RADIUS * 2 + 1 == 25, "The cooling tower uses the compact 25 x 25 x 36 layout");
        h.assertTrue(FuelCycleStructure.cells(BlockPos.ZERO, Direction.NORTH, false).stream().mapToInt(c -> c.pos().getY()).max().orElse(0) == 11, "The pulse column is twelve metres high");
        PidLoop loop = new PidLoop();double measurement = 20;
        for (int i = 0; i < 4000; i++) {
            double output = Math.round(loop.step(50, measurement, 2, 10, .5, false, .25) * .15) / .15;
            measurement = Math.clamp(measurement + (output - 50) * .0125, 0, 100);
        }
        h.assertTrue(Math.abs(measurement-50) < 2, "A physical 0-15 output converges to drum level: " + measurement);
        for (int i = 0; i < 1000; i++) loop.step(100, 0, 2, 10, 0, false, .25);
        h.assertTrue(loop.step(0, 100, 2, 10, 0, false, .25) == 0, "A saturated controller recovers immediately without integral windup");h.succeed();
    }

    private static FuelCycleMachineBlockEntity machine(GameTestHelper h, BlockPos pos, boolean melter) {
        BlockPos absolute = h.absolutePos(pos);
        for (var cell : FuelCycleStructure.cells(absolute, Direction.NORTH, melter)) h.getLevel().setBlock(cell.pos(), cell.state(), 2);
        var be = (FuelCycleMachineBlockEntity)h.getLevel().getBlockEntity(absolute);
        h.assertTrue(be.validate(), "The real-size process machine forms");return be;
    }

    @GameTest(template = "test_fractionation")
    public static void extractionSeparatesFourStreamsWithoutSpendingBlockedFeed(GameTestHelper h) {
        var column = machine(h, new BlockPos(5, 2, 5), false);column.setSpeed(64);
        column.tank(0).fill(new FluidStack(CEFuelCycle.DISSOLVED_FUEL.get(), 8000), EXECUTE);
        column.tank(1).fill(new FluidStack(CEFuelCycle.EXTRACTION_SOLVENT.get(), 2000), EXECUTE);
        column.tank(4).fill(new FluidStack(CEFuelCycle.HIGH_LEVEL_WASTE.get(), FuelCycleMachineBlockEntity.CAPACITY), EXECUTE);
        for (int i = 0; i < 500; i++) column.process();
        h.assertTrue(column.tank(0).getFluidAmount() == 8000 && column.progress() == 0, "A full waste outlet does not consume or reserve feed");
        column.tank(4).drain(Integer.MAX_VALUE, EXECUTE);
        for (int i = 0; i < 200; i++) column.process();
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());column.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(column.validate(), "Reloaded pulse column validates");column.setSpeed(64);
        for (int i = 0; i < 200; i++) column.process();
        h.assertTrue(column.tank(0).getFluidAmount() == 4000 && column.tank(1).getFluidAmount() == 1000, "Exactly one saved batch is consumed");
        h.assertTrue(column.tank(2).getFluidAmount() == 3500 && column.tank(3).getFluidAmount() == 100 && column.tank(4).getFluidAmount() == 400 && column.tank(5).getFluidAmount() == 1000, "U/Pu/waste/solvent outputs balance the five-litre process batch");
        var outlet = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, column.getBlockPos().south(2).east().above(9), Direction.EAST);
        h.assertTrue(outlet != null && outlet.drain(1000, SIMULATE).is(CEFuelCycle.EXTRACTION_SOLVENT.get()), "The upper nozzle exposes recycled solvent through a standard Create-compatible handler");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void electricMelterCastsOneSealedCanisterAndPreservesHeat(GameTestHelper h) {
        var melter = machine(h, new BlockPos(6, 2, 5), true);
        var saved = melter.saveWithoutMetadata(h.getLevel().registryAccess());
        melter.loadWithComponents(saved, h.getLevel().registryAccess());melter.validate();
        melter.tank(0).fill(new FluidStack(CEFuelCycle.HIGH_LEVEL_WASTE.get(), 1600), EXECUTE);
        melter.inventory().insertItem(0, new ItemStack(CEFuelCycle.GLASS_FRIT.get(), 4), false);
        melter.inventory().insertItem(1, new ItemStack(CEFuelCycle.EMPTY_CANISTER.get()), false);
        for (int i = 0; i < 1250; i++) { melter.setVoltageSquared(144_000_000);melter.tick(); }
        h.assertTrue(melter.inventory().getStackInSlot(2).is(CEFuelCycle.WASTE_CANISTER.get().asItem()) && melter.inventory().getStackInSlot(2).getCount() == 1, "A powered refractory melter seals exactly one canister");
        h.assertTrue(melter.tank(0).isEmpty() && melter.inventory().getStackInSlot(0).isEmpty() && melter.inventory().getStackInSlot(1).isEmpty(), "Waste, glass frit and empty canister are consumed atomically");
        saved = melter.saveWithoutMetadata(h.getLevel().registryAccess());melter.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(!melter.saveWithoutMetadata(h.getLevel().registryAccess()).contains("Kelvin") && melter.inventory().getStackInSlot(2).getCount()==1,"Reload preserves the sealed canister without a temperature state");h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 700)
    public static void craneExchangesWornFusionComponentsThroughTheServiceHatch(GameTestHelper h) {
        BlockPos control = h.absolutePos(new BlockPos(40, 7, 25));
        for (var cell : FusionStructure.cells(control, Direction.SOUTH)) if (cell.role() != FusionStructure.Role.CLEAR) h.getLevel().setBlock(cell.pos(), cell.expected(), 2);
        var reactor = (FusionReactorControllerBlockEntity)h.getLevel().getBlockEntity(control);reactor.tick();
        ItemStack blanket = new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), divertor = new ItemStack(CEFusion.DIVERTOR_CASSETTE.get());
        blanket.setDamageValue(12000);divertor.setDamageValue(8000);reactor.blanketPort().insertItem(0, blanket, false);reactor.blanketPort().insertItem(1, divertor, false);
        // The crane builds its own frame: legs of sixteen girders down to the floor and two rails of fifteen.
        BlockPos drive = new BlockPos(32, 18, 22);
        var crane = fusionCrane(h, drive);
        ItemHandlerHelper.insertItem(crane.store(), AllBlocks.METAL_GIRDER.asStack(4 * 16 + 2 * 15), false);
        ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        h.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(drive.west())).generatedSpeed.setValue(256));
        h.succeedWhen(() -> h.assertTrue(reactor.componentRemaining(0) == 24000 && reactor.componentRemaining(1) == 16000,
            "The travelling crane exchanges both worn components: " + crane.status() + ", " + reactor.componentRemaining(0) + ", " + reactor.componentRemaining(1)));
    }

    @GameTest(template = "test_fusion", timeoutTicks = 700)
    public static void craneSwapsAUsedUpIrradiationCartridgeForItsYield(GameTestHelper h) {
        BlockPos control = h.absolutePos(new BlockPos(40, 7, 25));
        for (var cell : FusionStructure.cells(control, Direction.SOUTH)) if (cell.role() != FusionStructure.Role.CLEAR) h.getLevel().setBlock(cell.pos(), cell.expected(), 2);
        var reactor = (FusionReactorControllerBlockEntity)h.getLevel().getBlockEntity(control);reactor.tick();
        ItemStack used = new ItemStack(CEFusion.IRRADIATION_CARTRIDGE.get());used.setDamageValue(used.getMaxDamage());
        reactor.blanketPort().insertItem(0, used, false);reactor.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        h.assertTrue(!reactor.breedsTritium(), "An irradiation cartridge in the blanket breeds no tritium");
        // A frame built by hand is accepted as well: girder legs standing on the floor under girder rails.
        int railY = 18;
        for (int x : new int[]{33, 47}) for (int z : new int[]{22, 36}) for (int y = 2; y < railY; y++) h.setBlock(new BlockPos(x, y, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), Direction.Axis.Y));
        for (int z : new int[]{22, 36}) for (int x = 33; x <= 47; x++) h.setBlock(new BlockPos(x, railY, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, railY, z)), Direction.Axis.X));
        var crane = fusionCrane(h, new BlockPos(32, railY, 22));
        BlockPos drive = new BlockPos(32, railY, 22);
        ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        h.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(drive.west())).generatedSpeed.setValue(256));
        h.succeedWhen(() -> {
            int irradiated = 0;
            for (int slot = 0; slot < crane.store().getSlots(); slot++) if (crane.store().getStackInSlot(slot).is(CEFusion.IRRADIATED_CARTRIDGE.get())) irradiated++;
            h.assertTrue(reactor.componentRemaining(0) == 24000 && reactor.breedsTritium() && irradiated == 1,
                "The used-up cartridge comes out irradiated and lithium-lead breeds again: " + crane.status() + ", " + reactor.componentRemaining(0) + ", " + irradiated);
        });
    }

    /** A fusion crane east of a 15 × 13 range, turned from the west and running. */
    static CraneDriveBlockEntity fusionCrane(GameTestHelper h, BlockPos drive) {
        h.setBlock(drive, CECrane.FUSION_DRIVE.get().defaultBlockState().setValue(CraneDriveBlock.FACING, Direction.EAST).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
        var crane = (CraneDriveBlockEntity)h.getBlockEntity(drive);
        crane.size(15, 13);
        return crane;
    }
}
