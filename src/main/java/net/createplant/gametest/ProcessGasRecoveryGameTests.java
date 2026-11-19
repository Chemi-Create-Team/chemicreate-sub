package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
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
import net.createplant.safety.ProcessGasIncidents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
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
        ProcessGasIncidents.record(h.getLevel(), site, 1100);
        h.assertTrue(ProcessGasIncidents.concentration(h.getLevel(), site) == 100, "A saturated site is capped at 100");
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(diode.getOutputSignal() == 15 && pump.status().equals("disabled")
                && h.getBlockState(lamp).getValue(BlockStateProperties.LIT), "The detector lights the lamp and stops the pump through the comparator");
            h.assertTrue(h.getBlockState(remote).getValue(GasDetectorBlock.POWER) == 0, "The incident stays outside the range of a remote detector");
        }).thenExecute(() -> ProcessGasIncidents.collect(h.getLevel(), site, 975)).thenWaitUntil(() ->
            h.assertTrue(diode.getOutputSignal() == 1, "A small remaining concentration gives a weak signal")
        ).thenWaitUntil(() ->
            h.assertTrue(diode.getOutputSignal() == 0 && pump.status().equals("power_low") && !h.getBlockState(lamp).getValue(BlockStateProperties.LIT),
                "Natural dilution clears the alarm and releases the interlock")
        ).thenSucceed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 420)
    public static void fanRecoversFiniteGasAndAPumpCarriesItToTheAbsorber(GameTestHelper h) {
        var duct = new BlockPos(4, 2, 5);
        var barrier = duct.west(2);
        var site = h.absolutePos(duct.west(3));
        var towerAt = h.absolutePos(duct.east(4));
        var tower = AbsorptionTowerGameTests.tower(h.getLevel(), towerAt, 4, true);
        h.assertTrue(tower.formed(), "The recovery line uses a working large absorption tower");
        h.setBlock(duct, CESafety.GAS_RECOVERY_DUCT.get().defaultBlockState().setValue(GasRecoveryDuctBlock.OUTLET, Direction.EAST));
        h.setBlock(duct.above(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        var fanMotor = motor(h, duct.above(2), Direction.DOWN);
        var pumpMotor = pump(h, duct.east());
        h.setBlock(duct.east(2), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true));
        var ductPort = port(h, h.absolutePos(duct), Direction.EAST);
        var waterPort = port(h, towerAt.above(3).west(), Direction.UP);
        var gasPort = port(h, towerAt.west(), Direction.WEST);
        var raw = new FluidStack(CESafety.RAW_PROCESS_GAS.get(), 1000);
        h.assertTrue(ductPort != null && port(h, h.absolutePos(duct), Direction.WEST) == null
            && port(h, h.absolutePos(duct), Direction.UP) == null && ductPort.fill(raw, SIMULATE) == 0, "Only the nozzle exposes a drain-only port");
        var emptyBucket = new ItemStack(Items.BUCKET);
        int filling = FillingBySpout.getRequiredAmountForItem(h.getLevel(), emptyBucket, raw);
        h.assertTrue(filling == 1000, "A Create spout can fill the bucket with 1000 mB of recovered gas");
        var filledBucket = FillingBySpout.fillItem(h.getLevel(), filling, emptyBucket, raw.copy());
        h.assertTrue(FluidUtil.getFluidContained(filledBucket).orElseThrow().is(CESafety.RAW_PROCESS_GAS.get()),
            "The spout's gas bucket contains the recovery stream");
        h.assertTrue(gasPort.fill(raw, SIMULATE) == 1000, "The bottom port can receive recovered gas");
        h.assertTrue(waterPort.fill(new FluidStack(Fluids.WATER, 250), EXECUTE) == 250, "Wash water goes to the top storey");
        ProcessGasIncidents.record(h.getLevel(), site, 475);

        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(ductPort.getFluidInTank(0).isEmpty(), "An unpowered duct cannot recover gas");
            h.setBlock(barrier, Blocks.STONE);
            fanMotor.generatedSpeed.setValue(64);
        }).thenIdle(20).thenExecute(() -> {
            h.assertTrue(ductPort.getFluidInTank(0).isEmpty(), "Recovery does not pull through a wall");
            h.setBlock(barrier, Blocks.AIR);
        }).thenWaitUntil(() -> {
            var stored = ductPort.getFluidInTank(0);
            h.assertTrue(stored.is(CESafety.RAW_PROCESS_GAS.get()) && stored.getAmount() > 250 && stored.getAmount() <= 475
                && ProcessGasIncidents.concentration(h.getLevel(), site) == 0, "The powered duct collects the remaining finite charge");
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
            pumpMotor.generatedSpeed.setValue(64);
        }).thenWaitUntil(() -> {
            var wastePort = port(h, towerAt.south(), Direction.SOUTH);
            var waste = wastePort == null ? FluidStack.EMPTY : wastePort.drain(1000, SIMULATE);
            h.assertTrue(waste.is(CEWaterTreatment.NEUTRALIZED_WASTEWATER.get()) && waste.getAmount() == 250,
                "The actual Create pump feeds the bottom of the tower and one batch of wastewater leaves through a different face. "
                + "Duct " + ductPort.getFluidInTank(0).getAmount() + ", inlet " + tower.processGasContent(0).getAmount()
                + ", wastewater " + waste.getAmount() + ", motor " + pumpMotor.getSpeed() + ", tower " + tower.status());
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
            h.assertTrue(ProcessGasIncidents.concentration(level, incident) == 0, "An intact pipe produces no residue");
            h.setBlock(pipe, Blocks.AIR);
        }).thenWaitUntil(() ->
            h.assertTrue(ProcessGasIncidents.concentration(level, incident) == 25, "The removed pipe leaves a recoverable local incident")
        ).thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 80)
    public static void steamVentedFromARealFlareEntersTheIncidentData(GameTestHelper h) {
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
            int collected = ProcessGasIncidents.collect(level, tip.above(), 1000);
            h.assertTrue(collected > 500 && collected <= 600, "Recoverable exhaust is bounded by the amount actually vented");
        });
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
