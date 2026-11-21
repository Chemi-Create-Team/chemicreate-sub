package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.GasDetectorBlock;
import net.createplant.block.GasRecoveryDuctBlock;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.entity.ElectricPumpBlockEntity;
import net.createplant.block.entity.FlareTipBlockEntity;
import net.createplant.registry.CEElectricUtilities;
import net.createplant.registry.CEFlare;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CESafety;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEWaterTreatment;
import net.createplant.cloud.CloudVolume;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasClouds;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class ProcessGasRecoveryGameTests {
    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void detectorFeedsAComparatorAndStopsTheSupplyPump(GameTestHelper h) {
        var detector = new BlockPos(2, 2, 5);
        var comparator = detector.east();
        var supply = comparator.east();
        var lamp = detector.north();
        var remote = detector.east(6);
        var site = h.absolutePos(detector.west());
        h.setBlock(detector, CESafety.GAS_DETECTOR.get());
        h.setBlock(remote, CESafety.GAS_DETECTOR.get());
        h.setBlock(comparator.below(), Blocks.STONE);
        h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        h.setBlock(supply, CEElectricUtilities.PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var pump = (ElectricPumpBlockEntity) h.getBlockEntity(supply);
        var diode = (ComparatorBlockEntity) h.getBlockEntity(comparator);
        GasClouds.release(h.getLevel(), site, steam(1100));
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(diode.getOutputSignal() > 1 && pump.status().equals("disabled")
                && h.getBlockState(lamp).getValue(BlockStateProperties.LIT), "The detector lights the lamp and stops the pump through the comparator");
            h.assertTrue(h.getBlockState(remote).getValue(GasDetectorBlock.POWER) == 0, "The incident stays outside the range of a remote detector");
        }).thenExecute(() -> {
            var cloud = onlyCloud(h, site);
            cloud.extract(cloud.amount() - 30);
        }).thenWaitUntil(() ->
            h.assertTrue(diode.getOutputSignal() == 1, "A small remaining charge gives a weak signal")
        ).thenExecute(() -> onlyCloud(h, site).extract(1000)).thenWaitUntil(() ->
            h.assertTrue(diode.getOutputSignal() == 0 && pump.status().equals("power_low") && !h.getBlockState(lamp).getValue(BlockStateProperties.LIT),
                "Removing the steam clears the alarm and releases the interlock")
        ).thenSucceed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 420)
    public static void fanRecoversFiniteSteamBehindAnOpenIntake(GameTestHelper h) {
        var duct = new BlockPos(4, 2, 5);
        var barrier = duct.west(2);
        var site = h.absolutePos(duct.west(3));
        h.setBlock(duct, CESafety.GAS_RECOVERY_DUCT.get().defaultBlockState().setValue(GasRecoveryDuctBlock.OUTLET, Direction.EAST));
        h.setBlock(duct.above(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        var fanMotor = motor(h, duct.above(2), Direction.DOWN);
        var destination = receiver(h, duct.east(2));
        var pumpMotor = pump(h, duct.east());
        int[] captured = {0};
        var ductPort = port(h, h.absolutePos(duct), Direction.EAST);
        h.assertTrue(ductPort != null && port(h, h.absolutePos(duct), Direction.WEST) == null
            && port(h, h.absolutePos(duct), Direction.UP) == null && ductPort.fill(steam(1000), SIMULATE) == 0, "Only the nozzle exposes a drain-only port");
        // Enclose the exhaust on all sides except the duct intake.
        var room = duct.west(3);
        for (var side : Direction.values()) if (side != Direction.EAST) h.setBlock(room.relative(side), Blocks.STONE);
        h.setBlock(barrier, Blocks.STONE);
        GasClouds.release(h.getLevel(), site, steam(1000));

        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(ductPort.getFluidInTank(0).isEmpty(), "An unpowered duct cannot recover gas");
            fanMotor.generatedSpeed.setValue(64);
        }).thenIdle(20).thenExecute(() -> {
            h.assertTrue(ductPort.getFluidInTank(0).isEmpty(), "Recovery does not pull through a wall");
            h.setBlock(barrier, Blocks.AIR);
        }).thenExecute(() -> {
            var cloud = onlyCloud(h, site);
            var differentGrade = new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 150);
            int before = cloud.amount();
            h.assertTrue(GasClouds.collect(h.getLevel(), site, differentGrade, 250).isEmpty() && cloud.amount() == before,
                "A nonempty buffer cannot recover a different steam grade");
            var recovered = GasClouds.collect(h.getLevel(), site, steam(1), 10);
            h.assertTrue(recovered.getAmount() == 10 && cloud.amount() == before - 10, "Recovery debits water steam once");
        }).thenWaitUntil(() -> {
            var stored = ductPort.getFluidInTank(0);
            h.assertTrue(stored.is(CEFluids.STEAM.get()) && stored.getAmount() > 250 && stored.getAmount() <= 990
                && GasClouds.in(h.getLevel(), new AABB(site)).isEmpty(), "The powered duct collects the remaining finite charge");
        }).thenExecute(() -> {
            var blockEntity = h.getBlockEntity(duct);
            var registry = h.getLevel().registryAccess();
            int stored = ductPort.getFluidInTank(0).getAmount();
            h.assertTrue(ductPort.drain(250, SIMULATE).getAmount() == 250 && ductPort.getFluidInTank(0).getAmount() == stored,
                "Polling the duct does not consume the contents");
            var drops = Block.getDrops(h.getBlockState(duct), h.getLevel(), h.absolutePos(duct), blockEntity);
            var storedContents = drops.size() == 1 ? drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA) : null;
            h.assertTrue(storedContents != null && !storedContents.isEmpty(), "A mined duct keeps its recovered gas in the dropped item");
            blockEntity.loadWithComponents(blockEntity.saveWithoutMetadata(registry), registry);
            h.assertTrue(ductPort.getFluidInTank(0).getAmount() == stored, "The installed duct retains its contents through NBT reload");
            captured[0] = stored;
            pumpMotor.generatedSpeed.setValue(64);
        }).thenWaitUntil(() -> {
            var recovered = destination.getTankInventory().getFluid();
            h.assertTrue(recovered.is(CEFluids.STEAM.get()) && recovered.getAmount() == captured[0] && ductPort.getFluidInTank(0).isEmpty(),
                "The native pump delivers the entire charge (" + captured[0] + " mB) to the gas receiver. Received " + recovered.getAmount());
        }).thenSucceed();
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 170)
    public static void removingAPipeWithHighPressureSteamTriggersAnIncident(GameTestHelper h) {
        var sourceAt = new BlockPos(1, 2, 3);
        var pipe = sourceAt.east(2);
        var source = receiver(h, sourceAt);
        var destination = receiver(h, sourceAt.east(3));
        h.setBlock(pipe, AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true));
        h.assertTrue(source.getTankInventory().fill(new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 10000), EXECUTE) == 10000,
            "The pipe uses a finite charge of steam");
        pump(h, sourceAt.east()).generatedSpeed.setValue(64);
        var level = h.getLevel();
        var incident = h.absolutePos(pipe);
        h.startSequence().thenWaitUntil(() ->
            h.assertTrue(!destination.getTankInventory().isEmpty(), "High-pressure steam passed through a Create pipe")
        ).thenExecute(() -> {
            h.assertTrue(GasClouds.in(level, new AABB(incident)).isEmpty(), "An intact pipe produces no residue");
            h.setBlock(pipe, Blocks.AIR);
        }).thenWaitUntil(() ->
            h.assertTrue(onlyCloud(h, incident).content().is(CEFluids.STEAM.get()), "The removed pipe leaves a water steam cloud")
        ).thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 80)
    public static void steamVentedFromARealFlareCanBeRecovered(GameTestHelper h) {
        var tip = h.absolutePos(new BlockPos(3, 10, 5));
        var level = h.getLevel();
        for (int y = 1; y <= 70; y++) level.removeBlock(tip.above(y), false);
        var pipe = AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.UP, true).setValue(FluidPipeBlock.DOWN, true);
        for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(tip.below(y), pipe);
        level.setBlockAndUpdate(tip, CEFlare.TIP.get().defaultBlockState());
        var flare = (FlareTipBlockEntity) level.getBlockEntity(tip);
        h.assertTrue(port(h, tip, Direction.DOWN).fill(new FluidStack(CEFluids.STEAM.get(), 600), EXECUTE) == 600, "A clear chimney accepts steam");
        h.succeedWhen(() -> {
            h.assertTrue(flare.vented() == 600 && flare.gas().isEmpty(), "The existing flare has vented the charge");
            int collected = GasClouds.collect(level, tip.above(), FluidStack.EMPTY, 1000).getAmount();
            h.assertTrue(collected > 0 && collected <= 600, "Recoverable exhaust is bounded by the amount actually vented");
        });
    }

    @GameTest(template = "test_reactor")
    public static void voxelVolumeFillsByLayerAndStopsAtADoor(GameTestHelper h) {
        var level = h.getLevel();
        var start = new BlockPos(4, 3, 5);
        var origin = h.absolutePos(start);
        for (int x = 2; x <= 8; x++) for (int y = 1; y <= 5; y++) for (int z = 3; z <= 7; z++) {
            if (x == 2 || x == 8 || y == 1 || y == 5 || z == 3 || z == 7 || x == 5)
                h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        var door = new BlockPos(5, 2, 5);
        var lower = Blocks.IRON_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        h.setBlock(door, lower);
        h.setBlock(door.above(), lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var ceiling = CloudVolume.build(level, origin, 1, true);
        var floor = CloudVolume.build(level, origin, 1, false);
        h.assertTrue(ceiling.size() == 8 && floor.size() == 8, "Small clouds fill eight cells");
        h.assertTrue(ceiling.bounds().maxY > floor.bounds().maxY && floor.bounds().minY < ceiling.bounds().minY,
            "Ceiling and floor priorities produce different occupied layers");
        var beyond = h.absolutePos(door.east());
        var closed = CloudVolume.build(level, origin, 5000, true);
        h.assertTrue(!closed.contains(beyond) && !CloudVolume.reachable(level, origin, 4).contains(beyond),
            "A closed door and the adjacent wall stop volume and sampling");
        level.setBlock(h.absolutePos(door), lower.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_CLIENTS);
        level.setBlock(h.absolutePos(door.above()), lower.setValue(BlockStateProperties.OPEN, true)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        var open = CloudVolume.build(level, origin, 5000, true);
        h.assertTrue(open.contains(beyond) && CloudVolume.reachable(level, origin, 4).contains(beyond),
            "Opening the door connects both rooms");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 145)
    public static void cloudMergesPersistsAndDissipatesOutside(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(7, 12, 7));
        for (int y = 0; y <= 70; y++) level.removeBlock(origin.above(y), false);
        GasClouds.release(level, origin, new FluidStack(Fluids.WATER, 1000));
        h.assertTrue(GasClouds.in(level, new AABB(origin)).isEmpty(), "Unregistered water creates no cloud");
        var stack = steam(1000);
        stack.set(net.createplant.registry.CEThermal.HEAT_GRADE.get(), 3);
        GasClouds.release(level, origin, stack);
        var cloud = onlyCloud(h, origin);
        h.assertTrue(cloud.lifetime() == 100 && cloud.volume().size() == 58, "Open sky shortens 600 ticks to 100 and 1000 mB has 58 cells");
        GasClouds.release(level, origin, stack.copyWithAmount(2000));
        h.assertTrue(onlyCloud(h, origin) == cloud && cloud.amount() == 3000 && cloud.lifetime() == 166, "Matching steam merges into a single cloud");
        GasClouds.release(level, origin, new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(), 100));
        h.assertTrue(GasClouds.in(level, new AABB(origin)).size() == 2, "Steam grades keep distinct clouds");
        h.runAfterDelay(105, () -> {
            var saved = cloud.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var loaded = new GasCloud(CESafety.GAS_CLOUD.get(), level);
            loaded.load(saved);
            h.assertTrue(loaded.volume().sameCells(cloud.volume()) && loaded.amount() == cloud.amount() && loaded.age() == cloud.age()
                && FluidStack.isSameFluidSameComponents(loaded.content(), stack), "Entity NBT preserves cells, amount, age and fluid components");
            h.assertTrue(cloud.volume().size() > 58, "A combined charge expands on its scheduled rebuild");
            var max = CloudVolume.build(level, origin, 20000, true);
            h.assertTrue(max.size() == 512 && max.bounds().getXsize() <= 17 && max.bounds().getYsize() <= 9 && max.bounds().getZsize() <= 17,
                "Even the largest cloud stays inside the fixed box");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 140)
    public static void legacyDuctChargeStillWashesInTheAbsorptionTower(GameTestHelper h) {
        var duct = new BlockPos(3, 2, 5);
        var level = h.getLevel();
        h.setBlock(duct, CESafety.GAS_RECOVERY_DUCT.get());
        var entity = h.getBlockEntity(duct);
        var stored = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000);
        stored.fill(new FluidStack(CESafety.RAW_PROCESS_GAS.get(), 250), EXECUTE);
        var tag = entity.saveWithoutMetadata(level.registryAccess());
        tag.put("Buffer", stored.writeToNBT(level.registryAccess(), new net.minecraft.nbt.CompoundTag()));
        entity.loadWithComponents(tag, level.registryAccess());
        var outlet = port(h, h.absolutePos(duct), Direction.NORTH);
        var recovered = outlet.drain(250, EXECUTE);
        h.assertTrue(recovered.is(CESafety.RAW_PROCESS_GAS.get()) && recovered.getAmount() == 250,
            "An existing duct can still deliver its saved legacy contents");
        var base = h.absolutePos(duct.east(4));
        AbsorptionTowerGameTests.tower(level, base, 4, true);
        h.assertTrue(port(h, base.east(), Direction.EAST).fill(recovered, EXECUTE) == 250
            && port(h, base.above(3), Direction.UP).fill(new FluidStack(Fluids.WATER, 250), EXECUTE) == 250,
            "The absorption tower still takes the legacy stream and water");
        h.succeedWhen(() -> {
            var wash = port(h, base.east(), Direction.EAST);
            h.assertTrue(wash != null && wash.drain(250, SIMULATE).is(CEWaterTreatment.NEUTRALIZED_WASTEWATER.get()),
                "Legacy gas is converted into neutralized wastewater");
        });
    }

    private static FluidStack steam(int amount) { return new FluidStack(CEFluids.STEAM.get(), amount); }

    private static GasCloud onlyCloud(GameTestHelper h, BlockPos pos) {
        var clouds = GasClouds.in(h.getLevel(), new AABB(pos));
        h.assertTrue(clouds.size() == 1, "Expected exactly one cloud at the source, got " + clouds.size());
        return clouds.getFirst();
    }

    private static IFluidHandler port(GameTestHelper h, BlockPos position, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, position, side);
    }

    private static FluidTankBlockEntity receiver(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X));
        return (FluidTankBlockEntity) h.getBlockEntity(pos);
    }

    private static CreativeMotorBlockEntity pump(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(pos.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        return motor(h, pos.south().west(), Direction.EAST);
    }

    private static CreativeMotorBlockEntity motor(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        var motor = (CreativeMotorBlockEntity) h.getBlockEntity(pos);
        motor.generatedSpeed.setValue(0);
        return motor;
    }
}
