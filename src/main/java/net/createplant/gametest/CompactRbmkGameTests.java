package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.block.entity.ShieldDriveBlockEntity;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CompactRbmkGameTests {
    private static final BlockPos BASE = new BlockPos(3, 6, 3);

    private static void blocks(GameTestHelper h, BlockPos base, int height, boolean circular) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            if (circular && !ReactorCoreStructure.inCircle(x, z, 5)) continue;
            for (int y = 0; y < height; y++) h.setBlock(base.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        }
    }

    private static ReactorCoreStructure.Result form(GameTestHelper h, BlockPos base) {
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(base));
        h.assertTrue(result.formed(), "The supported reactor forms: " + result.reason() + " at " + result.fault());
        ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).adopt(result, null);
        return result;
    }

    private static FuelChannelBlockEntity lead(GameTestHelper h, ReactorCoreStructure.Result result) {
        return (FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller());
    }

    private static void feed(GameTestHelper h, BlockPos base, int height) {
        int lower = ReactorPhysics.layout(height).lowerStructureHeight();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 1; y <= lower; y++)
            h.setBlock(base.offset(x, -y, z), CENuclear.FEED_BUNDLE.get());
    }

    private static BlockPos cap(GameTestHelper h, BlockPos bottom, int height) {
        var layout = ReactorPhysics.layout(height);
        for (int y = height; y < layout.fuelAssemblyLength(); y++) h.setBlock(bottom.above(y), CENuclear.FUEL_CHANNEL_TUBE.get());
        BlockPos cap = bottom.above(layout.fuelAssemblyLength());h.setBlock(cap, CENuclear.TOP_CAP.get());
        return cap;
    }

    private static int loadOne(GameTestHelper h, FuelChannelBlockEntity column) {
        for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) if (!column.control(slot)) {
            h.assertTrue(column.topItems().insertItem(slot, new ItemStack(CENuclear.FUEL_ROD.get()), false).isEmpty(), "A real fuel item fills one supported channel");
            return slot;
        }
        throw new IllegalStateException("No fuel channel in fixture");
    }

    @GameTest(template = "test_rbmk")
    public static void compactAndStandardCoresCoexistWithSeparateVolumesAndRatings(GameTestHelper h) {
        BlockPos large = new BlockPos(15, 10, 3);
        blocks(h, BASE, 3, true);blocks(h, large, 7, true);
        var smallResult = form(h, BASE.east(2));var largeResult = form(h, large.east(2));
        var small = lead(h, smallResult).core();var standard = lead(h, largeResult).core();
        h.assertTrue(small.height() == 3 && standard.height() == 7 && small.fuelChannels() == 44 && standard.fuelChannels() == 44,
            "The same five-metre footprint forms either supported axial height without merging the two reactors");
        h.assertTrue(small.layout().totalHeight() == 11 && standard.layout().totalHeight() == 29
            && small.layout().fuelAssemblyLength() == 7 && standard.layout().fuelAssemblyLength() == 21,
            "The compact reactor includes its shorter upper/lower structures and cap instead of merely hiding tall parts");
        h.assertTrue(Math.abs(small.nominalWatts() / standard.nominalWatts() - 3d / 7) < 1e-12
            && small.outlet().getTankCapacity(0) * 7 == standard.outlet().getTankCapacity(0) * 3,
            "Actual installed fuel count and active length determine nominal output and coolant volume");
        h.assertTrue(Math.abs(ReactorPhysics.nominalChannelWatts(7) * 397 - 765e6) < 1e-6
            && Math.abs(ReactorPhysics.nominalChannelWatts(3) * 397 - 765e6 * 3 / 7) < 1e-6,
            "The full-size maximum remains 765 MW; the same compact channel layout is about 328 MW");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void compactCoresRejectUnsupportedOrMixedColumnHeights(GameTestHelper h) {
        blocks(h, BASE, 2, false);
        h.assertTrue(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE)).reason().equals("height"), "Two active layers remain too short");
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(BASE.offset(x, 2, z), CENuclear.FUEL_CHANNEL.get());
        h.setBlock(BASE.offset(4, 1, 4), Blocks.AIR);
        var mixed = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE));
        h.assertTrue(!mixed.formed() && mixed.reason().equals("column"), "Every bundle and graphite column must have the same intact three-layer height");
        h.setBlock(BASE.offset(4, 1, 4), CENuclear.FUEL_CHANNEL.get());
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(BASE.offset(x, 3, z), CENuclear.FUEL_CHANNEL.get());
        h.assertTrue(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE)).reason().equals("height"), "Four active layers do not silently select either geometry profile");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void compactCapsAndRealFeedPortsUseTheReducedServiceHeight(GameTestHelper h) {
        blocks(h, BASE, 3, false);feed(h, BASE, 3);
        var result = form(h, BASE);
        BlockPos channel = BASE.offset(2, 0, 2), top = cap(h, channel, 3);
        var cap = (RodCapBlockEntity) h.getBlockEntity(top);
        h.assertTrue(top.getY() == channel.getY() + 7 && cap.column() == h.getBlockEntity(channel), "Four upper tube layers connect the operating cap to its three-metre core");
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(top), Direction.UP);
        int fuelSlot = 0;while (cap.column().control(fuelSlot)) fuelSlot++;
        h.assertTrue(port != null && port.insertItem(fuelSlot, new ItemStack(CENuclear.FUEL_ROD.get()), false).isEmpty(), "A native item capability loads fuel through the shortened assembly");
        var water = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.below(3)), Direction.DOWN);
        int capacity = 25 * 3 * FuelChannelBlockEntity.COOLANT_PER_CELL;
        h.assertTrue(water != null && water.getTankCapacity(0) == capacity && water.fill(new FluidStack(Fluids.WATER, capacity), SIMULATE) == capacity
            && water.fill(new FluidStack(Fluids.WATER, capacity), EXECUTE) == capacity, "Three actual feed-bundle layers supply all finite channel volume through the real bottom capability");
        h.assertTrue(lead(h, result).core().outlet().drain(1000, EXECUTE).getAmount() == 1000, "The small reactor still gives out its actual wet channel inventory");
        h.setBlock(top.below(), Blocks.AIR);
        h.assertTrue(cap.column() == null && h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(top), Direction.UP) == null,
            "Breaking the shortened tube path disconnects the operating cap instead of reaching a remote column");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void compactFuelEnergyHeatCapacityAndCoolingScaleTogetherAcrossReload(GameTestHelper h) {
        BlockPos large = new BlockPos(15, 10, 3);
        blocks(h, BASE, 3, false);blocks(h, large, 7, false);feed(h, BASE, 3);feed(h, large, 7);
        var compactResult = form(h, BASE);var fullResult = form(h, large);
        var compact = lead(h, compactResult);var full = lead(h, fullResult);
        loadOne(h, compact);loadOne(h, full);
        compact.burn(compact.rodJoules() / 4);full.burn(full.rodJoules() / 4);
        h.assertTrue(Math.abs(compact.cellLoad() - full.cellLoad()) < 1e-12 && Math.abs(compact.cellLoad() - .75) < 1e-12
            && compact.rodJoules() == 12e9 && full.rodJoules() == 28e9, "Three-metre rods contain 3/7 energy and retain the same fractional burn-up at proportional power");
        h.assertTrue(compact.inlet().fill(new FluidStack(Fluids.WATER, 12000), EXECUTE) == 12000
            && full.inlet().fill(new FluidStack(Fluids.WATER, 28000), EXECUTE) == 28000, "Each channel bundle accepts its actual metre-scaled water inventory");
        for (int tick = 0; tick < 200; tick++) {
            compact.heat(ReactorPhysics.NOMINAL_COLUMN_WATTS * 3 / 7, 3);full.heat(ReactorPhysics.NOMINAL_COLUMN_WATTS, 7);
        }
        h.assertTrue(Math.abs(compact.fuelKelvin() - full.fuelKelvin()) < 1e-6 && Math.abs(compact.coolantKelvin() - full.coolantKelvin()) < 1e-6
            && Math.abs(compact.fuelSurfaceKelvin() - full.fuelSurfaceKelvin()) < 1e-6, "Fuel heat capacity, wetted conductance and water mass scale together rather than overheating the small core at its rating");
        var saved = compact.saveWithoutMetadata(h.getLevel().registryAccess());
        compact.loadWithComponents(saved, h.getLevel().registryAccess());compact.adopt(compactResult, null);
        h.assertTrue(compact.layout().coreHeight() == 3 && compact.rodJoules() == 12e9 && compact.coolantAmount() == 12000
            && Math.abs(compact.cellLoad() - .75) < 1e-12, "Reloading keeps the short assembly's fuel energy, burn-up and coolant without reverting to seven metres");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void breakingAndReformingACompactCoreCannotDuplicateOrDrainDetachedCoolant(GameTestHelper h) {
        blocks(h, BASE, 3, false);feed(h, BASE, 3);
        var result = form(h, BASE);var owner = lead(h, result);var core = owner.core();
        var water = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.below(3)), Direction.DOWN);
        h.assertTrue(water.fill(new FluidStack(Fluids.WATER, 6000), EXECUTE) == 6000, "The core starts with one finite water charge");
        var outlet = core.outlet();BlockPos broken = BASE.offset(4, 1, 4);h.setBlock(broken, Blocks.AIR);core.tick();
        h.assertTrue(!core.formed() && outlet.drain(1000, SIMULATE).isEmpty() && outlet.drain(1000, EXECUTE).isEmpty()
            && water.fill(new FluidStack(Fluids.WATER, 1), EXECUTE) == 0, "A broken three-layer column stops the formed reactor and previously obtained coolant ports");
        h.setBlock(broken, CENuclear.FUEL_CHANNEL.get());var restored = form(h, BASE);
        int total = restored.fuel().stream().mapToInt(pos -> ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(pos)).coolantAmount()).sum();
        h.assertTrue(total == 6000 && owner.core().outlet().drain(6000, EXECUTE).getAmount() == 6000 && owner.core().outlet().drain(1, EXECUTE).isEmpty(),
            "Repairing the small core restores access to exactly the retained water once");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void loadedAssembliesCannotGainFuelEnergyByChangingHeightOrReloading(GameTestHelper h) {
        blocks(h, BASE, 3, false);feed(h, BASE, 3);
        var original = form(h, BASE);var column = lead(h, original);int slot = loadOne(h, column);
        column.burn(column.rodJoules() / 4);
        h.assertTrue(column.inlet().fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 1000, "The short assembly starts with real finite coolant");
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++)
            h.setBlock(BASE.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        column.core().tick();var refused = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE));
        h.assertTrue(!refused.formed() && refused.reason().equals("fuel_height") && !column.core().formed(), "Lengthening an installed three-metre rod stops formation instead of creating another four metres of fuel");
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());column.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE)).reason().equals("fuel_height")
            && column.rodJoules() == 12e9 && Math.abs(column.cellLoad() - .75) < 1e-12, "Save/load preserves the original assembly length and cannot bypass its remaining fuel energy");
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++) h.setBlock(BASE.offset(x, y, z), Blocks.AIR);
        form(h, BASE);
        h.assertTrue(column.coolantAmount() == 1000 && column.rodJoules() == 12e9 && Math.abs(column.cellLoad() - .75) < 1e-12,
            "Restoring the original geometry resumes the same partly used fuel and coolant");
        column.burn(column.rodJoules());
        h.assertTrue(column.topItems().extractItem(slot, 1, false).is(CENuclear.SPENT_FUEL_ROD.get()), "The existing spent-only removal path empties the used assembly");
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++)
            h.setBlock(BASE.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        form(h, BASE);
        h.assertTrue(column.layout().coreHeight() == 7 && column.fresh() == 0 && column.spent() == 0 && column.coolantAmount() == 1000,
            "After the spent rod is removed, an empty assembly can be rebuilt longer without gaining fuel or water");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void resizingEmptyCoresConservesExcessHeatAndReleasesOldVaultEquipment(GameTestHelper h) {
        blocks(h, BASE, 7, false);feed(h, BASE, 7);
        BlockPos port = BASE.offset(-2, 13, 2), drive = BASE.offset(-2, 13, 3);
        h.setBlock(port, CENuclear.SHIELD_OUTLET.get());h.setBlock(drive, CENuclear.SHIELD_DRIVE.get());
        var full = form(h, BASE);var column = lead(h, full);
        var oldPort = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(port), Direction.WEST);
        h.assertTrue(oldPort != null && ((ShieldDriveBlockEntity) h.getBlockEntity(drive)).calculateStressApplied() > 0,
            "The upper seven-metre vault initially owns its coolant port and loaded rotation input");
        for (int tick = 0; tick < 100; tick++) column.heat(1e6, 7);
        double originalKelvin = column.fuelKelvin();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++) h.setBlock(BASE.offset(x, y, z), Blocks.AIR);
        var compact = form(h, BASE);
        h.assertTrue(Math.abs(column.fuelKelvin() - originalKelvin) < 1e-9, "Removing hot material does not concentrate its heat into the shorter remaining assembly");
        h.assertTrue(column.inlet().fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 1000, "The new short reactor holds actual water while stale equipment is checked");
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(port), Direction.WEST) == null
            && oldPort.drain(1000, SIMULATE).isEmpty() && oldPort.drain(new FluidStack(Fluids.WATER, 1000), EXECUTE).isEmpty()
            && ((ShieldDriveBlockEntity) h.getBlockEntity(drive)).calculateStressApplied() == 0 && column.coolantAmount() == 1000,
            "Old high ports, including cached handlers, cannot drain the short reactor; removed drives have no residual stress");
        double beforeExpansion = column.fuelKelvin();
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());column.loadWithComponents(saved, h.getLevel().registryAccess());column.adopt(compact, null);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++)
            h.setBlock(BASE.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        form(h, BASE);
        h.assertTrue(Math.abs((column.fuelKelvin() - 293.15) * 7 - (beforeExpansion - 293.15) * 3) < 1e-8
            && column.coolantAmount() == 1000, "Adding ambient material after reload preserves existing excess solid heat and the exact coolant inventory");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 320)
    public static void compactControlRodsMoveThreeMetresAndRetainTheirDepthAcrossSaving(GameTestHelper h) {
        blocks(h, BASE, 3, false);
        BlockPos bottom = BASE.offset(2, 0, 2), capPos = cap(h, bottom, 3);
        var cap = (RodCapBlockEntity) h.getBlockEntity(capPos);h.assertTrue(cap.installControl(0), "The physical compact cap installs a three-metre control rod even before the core first forms");
        var result = form(h, BASE);
        BlockPos drive = BASE.offset(-2, 1, 2), motor = drive.west();
        h.setBlock(drive, CENuclear.SHIELD_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        lead(h, result).adopt(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE)), null);
        h.setBlock(capPos.above(), Blocks.REDSTONE_BLOCK);cap.neighbourChanged();
        h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128));
        h.runAfterDelay(80, () -> {
            var core = lead(h, result).core();
            h.assertTrue(core.installedControlRods() == 1 && Math.abs(core.controlDepth(h.absolutePos(bottom), 0) - 3) < 1d / 512
                && Math.abs(core.controlInsertion(0) - 1) < 1e-6, "Real rotation and the cap command fully insert the short rod at exactly three metres");
            var column = (FuelChannelBlockEntity) h.getBlockEntity(bottom);var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());
            column.loadWithComponents(saved, h.getLevel().registryAccess());lead(h, result).adopt(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE)), null);
            h.assertTrue(column.layout().coreHeight() == 3 && Math.abs(column.controlDepth(0) - 3) < 1d / 512, "Short control depth survives the real block entity save/load path");
            h.setBlock(capPos.above(), Blocks.AIR);cap.neighbourChanged();
        });
        h.runAfterDelay(160, () -> {
            ReactorCore core = lead(h, result).core();
            h.assertTrue(core.controlDepth(h.absolutePos(bottom), 0) < 1d / 512 && core.rodWithdrawn(0), "The same physical cap drive withdraws the compact absorber completely");
            ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(0);core.setConsoleScram(true);
        });
        h.runAfterDelay(280, () -> {
            ReactorCore core = lead(h, result).core();
            h.assertTrue(core.scram() && Math.abs(core.controlDepth(h.absolutePos(bottom), 0) - 3) < 1d / 512,
                "Without motor power AZ-5 still drops through the complete three-metre travel within five seconds");
            h.succeed();
        });
    }
}
