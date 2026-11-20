package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HorizontalTankBoilerFlowGameTests {
    private HorizontalTankBoilerFlowGameTests() { }

    private static void pipe(GameTestHelper h, BlockPos pos, Direction first, Direction second) {
        var state = net.createplant.registry.CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (Direction side : Direction.values()) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side == first || side == second);
        h.setBlock(pos, state);
    }

    private static CreativeMotorBlockEntity motor(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        var motor = (CreativeMotorBlockEntity) h.getBlockEntity(pos);motor.generatedSpeed.setValue(0);return motor;
    }

    private static void firedBurner(GameTestHelper h, BlockPos under) {
        h.setBlock(under, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var burner=(com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity)h.getBlockEntity(under);
        var tag=burner.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("fuelLevel",1);tag.putInt("burnTimeRemaining",4000);burner.loadWithComponents(tag,h.getLevel().registryAccess());burner.updateBlockState();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 280)
    public static void nativeFeedAndSteamPumpsConserveWaterThroughAStopAndRestart(GameTestHelper h) {
        verifyNativePumps(h, false);
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 280)
    public static void nativeSteamPumpDrawsFromTheMiddleOfTheRoof(GameTestHelper h) {
        verifyNativePumps(h, true);
    }

    private static void verifyNativePumps(GameTestHelper h, boolean roof) {
        int charge = 3_000;
        BlockPos base = new BlockPos(6, 3, 8), hot = base.east(2), supply = base.north(4);
        BlockPos outletPipe = roof ? base.east().above() : hot.east();
        BlockPos receiver = outletPipe.east(3);
        var boilerState = CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        for (int section = 0; section < 3; section++) { h.setBlock(base.east(section), boilerState);firedBurner(h, base.east(section).below()); }
        var root = (HorizontalTankBlockEntity) ((HorizontalTankBlockEntity) h.getBlockEntity(base)).getControllerBE();
        var outlet = (HorizontalTankBlockEntity) h.getBlockEntity(hot);
        h.assertTrue(root.getHeight() == 3, "Three horizontal tank cells over burners form one boiler");

        h.setBlock(supply, AllBlocks.FLUID_TANK.get());var source = ((FluidTankBlockEntity) h.getBlockEntity(supply)).getTankInventory();
        source.fill(FluidThermal.at(new FluidStack(Fluids.WATER, charge), 293.15), EXECUTE);
        pipe(h, supply.south(), Direction.NORTH, Direction.SOUTH);
        BlockPos feedPump = supply.south(2);
        h.setBlock(feedPump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        pipe(h, supply.south(3), Direction.NORTH, Direction.SOUTH);
        h.setBlock(feedPump.east(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        var feedMotor = motor(h, feedPump.east().north(), Direction.SOUTH);

        for (int x = 0; x < 3; x++) h.setBlock(receiver.east(x), CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS, Direction.Axis.X));
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti((FluidTankBlockEntity) h.getBlockEntity(receiver));
        var receiverOwner = ((FluidTankBlockEntity) h.getBlockEntity(receiver)).getControllerBE();var stored = receiverOwner.getTankInventory();
        h.assertTrue(receiverOwner.getWidth() == 1 && receiverOwner.getHeight() == 3 && stored.getCapacity() == 24000,
            "The steam receiver is a genuine axial gas tank, large enough for the finite steam charge");
        pipe(h, outletPipe, roof ? Direction.DOWN : Direction.WEST, Direction.EAST);
        BlockPos steamPump = outletPipe.east();
        h.setBlock(steamPump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        pipe(h, steamPump.east(), Direction.WEST, Direction.EAST);
        h.setBlock(steamPump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        var steamMotor = motor(h, steamPump.south().west(), Direction.EAST);

        int[] pausedAmount = {-1};
        h.onEachTick(() -> {
            int total = source.getFluidAmount() + root.getTankInventory().getFluidAmount() + stored.getFluidAmount();
            for (int section = 0; section < 3; section++) total += ((HorizontalTankBlockEntity) h.getBlockEntity(base.east(section))).steam().getFluidAmount();
            h.assertTrue(total == charge, "Every native transport tick conserves the finite charge of water and steam: " + total);
        });
        h.runAfterDelay(5, () -> feedMotor.generatedSpeed.setValue(128));
        h.runAfterDelay(175, () -> {
            h.assertTrue(source.getFluidAmount() < charge && outlet.steam().getFluidAmount() > 0 && stored.isEmpty(),
                "The stock feed pump supplies finite water while the stopped steam pump exports nothing");
            h.assertTrue(outlet.steam().getFluid().is(CEProcessFluids.HIGH_PRESSURE_STEAM.get()), "A normally fired burner creates high-pressure steam");
        });
        h.runAfterDelay(180, () -> steamMotor.generatedSpeed.setValue(128));
        h.runAfterDelay(192, () -> steamMotor.generatedSpeed.setValue(0));
        h.runAfterDelay(202, () -> {
            var current = MachineReadings.read(outlet, "production_fluid");
            var rated = MachineReadings.read(outlet, "production_fluid_max");
            double theoretical = 3 * 65.536;
            h.assertTrue(current != null && current.value() > 0 && rated != null && Math.abs(rated.value() - theoretical) < .001,
                "A noncontroller boiler section shows the observed output and the full structure's heat-limited rating");
            h.assertTrue(MachineReadings.productionBottleneck(outlet).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents reason
                && reason.getKey().endsWith(".rated"),
                "Three normally heated burners provide the rated mass flow for high-pressure steam");
            h.assertTrue(stored.getFluidAmount() > 0, "A stock pipe and real Create pump deliver finite high-pressure steam to the receiver");
            pausedAmount[0] = stored.getFluidAmount();
        });
        h.runAfterDelay(209, () -> h.assertTrue(stored.getFluidAmount() == pausedAmount[0], "Stopping the native pump stops export after its pressure update"));
        h.runAfterDelay(210, () -> steamMotor.generatedSpeed.setValue(128));
        h.runAfterDelay(222, () -> steamMotor.generatedSpeed.setValue(0));
        h.runAfterDelay(240, () -> {
            h.assertTrue(stored.getFluidAmount() > pausedAmount[0] && stored.getFluid().is(CEProcessFluids.HIGH_PRESSURE_STEAM.get()),
                "Restarting the same native pump resumes steam export without replacing the pipe or boiler");
            int converted = charge - source.getFluidAmount() - root.getTankInventory().getFluidAmount();
            h.assertTrue(converted == outlet.steam().getFluidAmount() + stored.getFluidAmount(),
                "Feedwater reduction equals the boiler's steam plus the complete receiver water/steam mass");
            h.assertTrue((outlet.steam().isEmpty() || outlet.steam().getFluid().is(CEProcessFluids.HIGH_PRESSURE_STEAM.get())) && !outlet.isRemoved() && !receiverOwner.isRemoved(),
                "The fired boiler maintains its 350 C outlet and the heat-resistant receiver remains intact");
            h.succeed();
        });
    }
}
