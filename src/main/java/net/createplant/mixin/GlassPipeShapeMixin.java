package net.createplant.mixin;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import net.createplant.transport.PipeConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AxisPipeBlock.class)
abstract class GlassPipeShapeMixin {
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void createPlant$shortenGlass(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> result) {
        if (!(state.getBlock() instanceof GlassFluidPipeBlock)) return;
        var axis = state.getValue(AxisPipeBlock.AXIS);
        double min = PipeConnections.isClosed(world, pos, Direction.get(Direction.AxisDirection.NEGATIVE, axis)) ? .25 : 0;
        double max = PipeConnections.isClosed(world, pos, Direction.get(Direction.AxisDirection.POSITIVE, axis)) ? .75 : 1;
        if (min == 0 && max == 1) return;
        var span = switch (axis) {
            case X -> Shapes.box(min, 0, 0, max, 1, 1);
            case Y -> Shapes.box(0, min, 0, 1, max, 1);
            case Z -> Shapes.box(0, 0, min, 1, 1, max);
        };
        result.setReturnValue(Shapes.join(result.getReturnValue(), span, BooleanOp.AND));
    }
}
