package net.createplant.thermal;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/** Connectivity checks shared by the pressure-driven paths that inspect Create fluid pipes directly. */
public final class FluidPipeTopology {
    private FluidPipeTopology() { }

    /** Whether the pipe at {@code pos} actually exposes its transport face in {@code direction}. */
    public static boolean opens(BlockGetter level, BlockPos pos, Direction direction) {
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
        return pipe != null && pipe.canHaveFlowToward(level.getBlockState(pos), direction);
    }

    /** Adjacent pipe blocks are joined only when both transport faces are open. */
    public static boolean joins(BlockGetter level, BlockPos pos, Direction direction) {
        return opens(level, pos, direction) && opens(level, pos.relative(direction), direction.getOpposite());
    }
}
