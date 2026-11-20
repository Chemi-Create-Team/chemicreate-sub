package net.createplant.transport;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import net.createplant.registry.CEGasPipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The exposed sleeve beyond a pipe's center, shared by the tool and its highlight. */
public record PipeArm(BlockPos pipe, Direction side, boolean cut, AABB bounds) {
    private static final Direction[] SIDES = Direction.values();

    public static PipeArm target(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        return trace(level, eye, end, new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    public static PipeArm trace(Level level, Vec3 eye, Vec3 end, ClipContext occlusion) {
        var obstruction = level.clip(occlusion);
        double limit = obstruction.getType() == HitResult.Type.MISS ? eye.distanceToSqr(end) : eye.distanceToSqr(obstruction.getLocation());
        return BlockGetter.traverseBlocks(eye, end, level, (world, pos) -> {
            if (!world.hasChunkAt(pos)) return null;
            var state = world.getBlockState(pos);
            PipeArm closest = null;
            double closestDistance = limit + 1e-5;
            for (Direction side : SIDES) {
                var shape = bounds(world, pos, state, side);
                if (shape == null) continue;
                var hit = shape.clip(eye, end);
                if (hit.isEmpty()) continue;
                double distance = eye.distanceToSqr(hit.get());
                if (distance >= closestDistance || !PipeConnections.canToggle(world, pos, side)) continue;
                closestDistance = distance;
                closest = new PipeArm(pos.immutable(), side, PipeConnections.isClosed(world, pos, side), shape);
            }
            return closest;
        }, world -> null);
    }

    public static AABB bounds(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
        boolean glass = state.getBlock() instanceof GlassFluidPipeBlock;
        if (glass ? state.getValue(AxisPipeBlock.AXIS) != side.getAxis()
                : !FluidPipeBlock.isPipe(state) || state.is(CEGasPipes.LARGE_GAS_PIPE.get())) return null;
        double core = !glass && FluidPipeBlock.shouldDrawCasing(world, pos, state) ? 3 / 16d : 4 / 16d;
        double min = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - core : 0;
        double max = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : core;
        return switch (side.getAxis()) {
            case X -> new AABB(min, .25, .25, max, .75, .75).move(pos);
            case Y -> new AABB(.25, min, .25, .75, max, .75).move(pos);
            case Z -> new AABB(.25, .25, min, .75, .75, max).move(pos);
        };
    }
}
