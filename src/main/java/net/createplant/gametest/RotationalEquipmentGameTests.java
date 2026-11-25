package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.entity.LargeFlywheelBlockEntity;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for Create-facing rotational machinery interfaces. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RotationalEquipmentGameTests {
    private static final BlockPos FLYWHEEL_START = new BlockPos(8, 4, 8);

    private RotationalEquipmentGameTests() { }

    @GameTest(template = "test_fractionation", timeoutTicks = 160)
    public static void turbineHubReservesItsAxisForMechanicalOutput(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        var turbines = TurbineThermalGameTests.build(level, base);
        BlockPos highPressureRotor = base.offset(13, 4, 10);
        BlockPos endCenter = base.offset(16, 4, 10);
        BlockPos endRing = endCenter.above();

        helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK,
                highPressureRotor, Direction.EAST) == null,
            "The rotor block itself must not expose a fluid handler on its shaft axis");
        helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, endCenter, Direction.EAST) == null,
            "The turbine hub's exposed axial face must be reserved for a Create shaft, not a steam pipe");
        helper.assertTrue(((HighPressureTurbineEndBlock) level.getBlockState(endCenter).getBlock())
                .hasShaftTowards(level, endCenter, level.getBlockState(endCenter), Direction.EAST),
            "The center of the turbine end plate must expose a mechanical shaft");
        helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, endRing, Direction.UP) != null,
            "Steam and exhaust remain accessible through the non-hub end-ring ports");

        BlockPos outputShaft = endCenter.east();
        level.setBlock(outputShaft, AllBlocks.SHAFT.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        FluidStack steam = FluidThermal.at(FluidForm.HIGH_PRESSURE_STEAM.stack(4_000), 750);
        IFluidHandler casingInlet = level.getCapability(Capabilities.FluidHandler.BLOCK,
            highPressureRotor.above(), Direction.UP);
        helper.assertTrue(casingInlet != null
                && casingInlet.fill(steam, IFluidHandler.FluidAction.EXECUTE) == 4_000,
            "The completed turbine accepts its startup steam through the outer casing instead of its shaft");

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(turbines[0].thermodynamics().totalWork() > 0,
                "Steam entering through the casing must perform turbine work");
            helper.assertTrue(level.getBlockEntity(outputShaft) instanceof KineticBlockEntity shaft
                    && Math.abs(shaft.getSpeed()) > 0,
                "Steam operation must deliver Create rotation through the unobstructed axial hub");
            helper.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 260)
    public static void coupledFlywheelsChargeAndTakeOverAfterTheMotorStops(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(FLYWHEEL_START);
        BlockPos motorPos = start.west();
        BlockPos outputShaft = start.east(3);

        level.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        for (int i = 0; i < 3; i++) {
            level.setBlock(start.east(i), CEBlocks.LARGE_FLYWHEEL.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        }
        level.setBlock(outputShaft, AllBlocks.SHAFT.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        ((CreativeMotorBlockEntity) level.getBlockEntity(motorPos)).generatedSpeed.setValue(64);

        helper.runAfterDelay(100, () -> {
            LargeFlywheelBlockEntity flywheel = (LargeFlywheelBlockEntity) level.getBlockEntity(start);
            helper.assertTrue(flywheel.storedEnergyJ() > 0 && flywheel.rotorRpm() > 1
                    && flywheel.calculateAddedStressCapacity() > 0,
                "A driven charged flywheel publishes spinning-reserve capacity before the source transfer");
            level.destroyBlock(motorPos, false);
        });
        helper.runAfterDelay(180, () -> {
            assertFlywheelsRemain(helper, start, 3);
            helper.assertTrue(level.getBlockEntity(outputShaft) instanceof KineticBlockEntity shaft
                    && Math.abs(shaft.getSpeed()) > 1,
                "Charged flywheels must become the source and keep the output shaft turning");
            helper.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 60)
    public static void staleOpposingNetworkStateCannotDeleteAHealthyFlywheelAssembly(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(FLYWHEEL_START);
        for (int i = 0; i < 3; i++) {
            BlockPos pos = start.east(i);
            level.setBlock(pos, CEBlocks.LARGE_FLYWHEEL.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
            LargeFlywheelBlockEntity flywheel = (LargeFlywheelBlockEntity) level.getBlockEntity(pos);
            var saved = flywheel.saveWithoutMetadata(level.registryAccess());
            saved.putDouble("StoredEnergyJ", 10_000_000);
            saved.putDouble("RotorRpm", 64);
            saved.putInt("RotorDirection", 1);
            flywheel.loadWithComponents(saved, level.registryAccess());
            // Reproduces stale negative network metadata with no real external connector.
            flywheel.setSpeed(-64);
        }

        helper.runAfterDelay(20, () -> {
            assertFlywheelsRemain(helper, start, 3);
            LargeFlywheelBlockEntity flywheel = (LargeFlywheelBlockEntity) level.getBlockEntity(start);
            helper.assertTrue(flywheel.storedEnergyJ() > 0 && flywheel.rotorRpm() > 1
                    && flywheel.getSpeed() > 1,
                "The assembly must preserve its stored energy and republish the rotor's real direction");
            helper.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void dyeRecoloursAPlacedFlywheelAndYellowRestoresTheDefault(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(FLYWHEEL_START);
        level.setBlock(pos, CEBlocks.LARGE_FLYWHEEL.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var red = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE, 2);
        level.getBlockState(pos).useItemOn(red, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        var dyed = level.getBlockState(pos);
        helper.assertTrue(dyed.is(CEBlocks.RED_LARGE_FLYWHEEL.get()) && dyed.getValue(BlockStateProperties.AXIS) == Direction.Axis.X && red.getCount() == 1,
            "Red dye recolours the placed flywheel, keeps its axis and is consumed");
        dyed.useItemOn(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.YELLOW_DYE), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getBlockState(pos).is(CEBlocks.LARGE_FLYWHEEL.get()), "Yellow dye restores the default yellow flywheel");
        helper.succeed();
    }

    private static void assertFlywheelsRemain(GameTestHelper helper, BlockPos start, int count) {
        for (int i = 0; i < count; i++) {
            helper.assertTrue(helper.getLevel().getBlockState(start.east(i)).getBlock() instanceof net.createplant.block.LargeFlywheelBlock,
                "The coupled flywheel assembly must not destroy member " + i);
        }
    }
}
