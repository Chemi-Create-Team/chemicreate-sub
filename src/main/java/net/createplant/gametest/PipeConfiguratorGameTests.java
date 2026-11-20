package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CETransport;
import net.createplant.transport.PipeArm;
import net.createplant.transport.PipeConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeConfiguratorGameTests {
    private PipeConfiguratorGameTests() { }

    private static BlockState pipe(Direction... sides) {
        var state = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side : sides) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), true);
        return state;
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 100)
    public static void armHitAndCutAreIndependentOfBlockFaceAndNeighborUpdates(GameTestHelper h) {
        var center = new BlockPos(4, 2, 4);
        h.setBlock(center.west(), pipe(Direction.EAST, Direction.WEST));
        h.setBlock(center.north(), pipe(Direction.NORTH, Direction.SOUTH));
        h.setBlock(center.east(), pipe(Direction.EAST, Direction.WEST));
        h.setBlock(center, pipe(Direction.EAST, Direction.NORTH, Direction.WEST));
        var world = h.getLevel();
        var pos = h.absolutePos(center);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var tool = CEPortableTools.PIPE_CONFIGURATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, CEPortableTools.PIPE_CONFIGURATOR.toStack());
        player.setYRot(180);
        player.setXRot(0);
        var origin = Vec3.atLowerCornerOf(pos).add(0, .5 - player.getEyeHeight(), 2);
        h.runAfterDelay(3, () -> {
            player.setPos(origin.add(.5, 0, 0));
            h.assertTrue(PipeArm.target(world, player) == null && !tool.use(world, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "A ray on the middle of the pipe cannot toggle its connections");
            h.setBlock(center.south(), Blocks.STONE);
            player.setPos(origin.add(.875, 0, 0));
            h.assertTrue(PipeArm.target(world, player) == null, "A solid block occludes a joint");
            h.setBlock(center.south(), Blocks.AIR);
            var arm = PipeArm.target(world, player);
            h.assertTrue(arm != null && arm.pipe().equals(pos) && arm.side() == Direction.EAST && !arm.cut(),
                "A ray from the south selects the east arm without clicking an east face");
            h.assertTrue(tool.use(world, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "The tool cuts the selected arm");
            assertClosed(h, center, Direction.EAST);
            h.assertTrue(FluidPipeBlock.isOpenAt(h.getBlockState(center), Direction.NORTH)
                && FluidPipeBlock.isOpenAt(h.getBlockState(center), Direction.WEST), "The north and west outlets stay open");
            h.setBlock(center.above(), Blocks.STONE);
            h.setBlock(center.above(), Blocks.AIR);
            h.setBlock(center.east(), Blocks.AIR);
            h.setBlock(center.east(), pipe(Direction.WEST, Direction.EAST));
        });
        h.runAfterDelay(10, () -> {
            assertClosed(h, center, Direction.EAST);
            var arm = PipeArm.target(world, player);
            h.assertTrue(arm != null && arm.cut() && arm.side() == Direction.EAST,
                "The cut sleeve can be selected again after native shape updates");
            h.assertTrue(tool.use(world, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "The tool reconnects the sleeve");
            h.assertTrue(!PipeConnections.isClosed(world, pos, Direction.EAST)
                && FluidPipeBlock.isOpenAt(h.getBlockState(center), Direction.EAST)
                && FluidPipeBlock.isOpenAt(h.getBlockState(center.east()), Direction.WEST), "Both ends return to native flow");
            h.succeed();
        });
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 320)
    public static void nativePumpKeepsOtherBranchWhileAFlowingJointIsCut(GameTestHelper h) {
        var start = new BlockPos(1, 2, 3);
        var tee = start.east(3);
        var across = start.east(6);
        var north = tee.north(2);
        h.setBlock(start, AllBlocks.FLUID_TANK.get());
        h.setBlock(across, AllBlocks.FLUID_TANK.get());
        h.setBlock(north, AllBlocks.FLUID_TANK.get());
        h.setBlock(start.east(2), pipe(Direction.EAST, Direction.WEST));
        h.setBlock(tee.east(), AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(tee.east(2), CETransport.STAINLESS_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true));
        h.setBlock(tee.north(), pipe(Direction.NORTH, Direction.SOUTH));
        h.setBlock(tee, pipe(Direction.EAST, Direction.NORTH, Direction.WEST));
        var pump = start.east();
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(pump.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((CreativeMotorBlockEntity) h.getBlockEntity(pump.south().west())).generatedSpeed.setValue(32);
        var input = ((FluidTankBlockEntity) h.getBlockEntity(start)).getTankInventory();
        var output = ((FluidTankBlockEntity) h.getBlockEntity(across)).getTankInventory();
        var branch = ((FluidTankBlockEntity) h.getBlockEntity(north)).getTankInventory();
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 8000), EXECUTE) == 8000, "The source has a finite charge");
        var world = h.getLevel();
        var p = h.absolutePos(tee);
        int[] previous = new int[3];
        h.startSequence().thenWaitUntil(() -> h.assertTrue(output.getFluidAmount() > 16 && branch.getFluidAmount() > 16,
            "The native pump flows through both arms before closing either one")).thenExecute(() -> {
            previous[0] = output.getFluidAmount();
            previous[1] = branch.getFluidAmount();
            previous[2] = input.getFluidAmount() + previous[0] + previous[1];
            h.assertTrue(PipeConnections.toggle(world, p, Direction.EAST), "The flowing joint closes");
            assertClosed(h, tee, Direction.EAST);
            h.assertTrue(!FluidPropagator.isOpenEnd(world, p.east(), Direction.WEST), "A glass end facing the cut is sealed, not a vent");
        }).thenIdle(35).thenExecute(() -> {
            h.assertTrue(output.getFluidAmount() == previous[0], "The already-running route stops delivering immediately");
            h.assertTrue(branch.getFluidAmount() > previous[1], "The other arm continues receiving");
            h.assertTrue(input.getFluidAmount() + output.getFluidAmount() + branch.getFluidAmount() == previous[2],
                "The closed network preserves the remaining charge");
            h.assertTrue(PipeConnections.toggle(world, p.east(), Direction.WEST), "The same edge opens when approached from the glass side");
            h.assertTrue(input.getFluidAmount() + output.getFluidAmount() + branch.getFluidAmount() == previous[2],
                "Reopening a joint does not change the stored amount");
        }).thenWaitUntil(() -> h.assertTrue(output.getFluidAmount() > previous[0], "The native pump resumes the restored route"))
            .thenSucceed();
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 100)
    public static void changingAPipeToCasingPreservesOnlyTheSpecifiedLock(GameTestHelper h) {
        var left = new BlockPos(3, 2, 3);
        var right = left.east();
        var world = h.getLevel();
        h.setBlock(left, pipe(Direction.WEST, Direction.EAST));
        h.setBlock(right, AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.runAfterDelay(3, () -> {
            h.assertTrue(PipeConnections.toggle(world, h.absolutePos(left), Direction.EAST), "The copper-to-glass joint closes");
            h.setBlock(right, AllBlocks.ENCASED_FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.EAST, true));
        });
        h.runAfterDelay(10, () -> {
            assertClosed(h, left, Direction.EAST);
            h.assertTrue(!h.getBlockState(right).getValue(FluidPipeBlock.WEST) && h.getBlockState(right).getValue(FluidPipeBlock.EAST),
                "Casing has a closed face on the cut joint and an open face on the other side");
            h.assertTrue(PipeConnections.toggle(world, h.absolutePos(left), Direction.EAST)
                && h.getBlockState(right).getValue(FluidPipeBlock.WEST), "The exposed matching arm can reopen the encased face");
            h.succeed();
        });
    }

    private static void assertClosed(GameTestHelper h, BlockPos start, Direction side) {
        var world = h.getLevel();
        var pos = h.absolutePos(start);
        var other = pos.relative(side);
        h.assertTrue(PipeConnections.isClosed(world, pos, side) && PipeConnections.isClosed(world, other, side.getOpposite()),
            "Both positions resolve the same stored joint");
        h.assertTrue(!FluidPipeBlock.isOpenAt(h.getBlockState(start), side)
            && !FluidPropagator.getPipe(world, pos).canHaveFlowToward(world.getBlockState(pos), side)
            && !FluidPropagator.getPipe(world, other).canHaveFlowToward(world.getBlockState(other), side.getOpposite()),
            "The selected flags and both native transport interfaces are closed");
    }
}
