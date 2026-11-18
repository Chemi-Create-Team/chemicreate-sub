package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.createmod.catnip.data.Iterate;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Create's fluid pipe in stainless steel; only the pipe kinds it turns into are its own stainless variants. */
public class StainlessPipeBlock extends FluidPipeBlock {
    private static final MapCodec<StainlessPipeBlock> CODEC = simpleCodec(StainlessPipeBlock::new);

    public StainlessPipeBlock(Properties properties) {
        super(properties);
    }

    /** Create's wrench toggle, ending in the stainless glass pipe instead of Create's glass pipe. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (tryRemoveBracket(context))
            return InteractionResult.SUCCESS;
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Direction.Axis axis = FluidPropagator.getStraightPipeAxis(state);
        if (axis == null) {
            Vec3 clickLocation = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            double closest = Float.MAX_VALUE;
            Direction argClosest = Direction.UP;
            for (Direction direction : Iterate.directions) {
                if (clickedFace.getAxis() == direction.getAxis())
                    continue;
                double distance = Vec3.atCenterOf(direction.getNormal()).distanceToSqr(clickLocation);
                if (distance < closest) {
                    closest = distance;
                    argClosest = direction;
                }
            }
            axis = argClosest.getAxis();
        }
        if (clickedFace.getAxis() == axis)
            return InteractionResult.PASS;
        if (!world.isClientSide) {
            withBlockEntityDo(world, pos, pipe -> pipe.getBehaviour(FluidTransportBehaviour.TYPE).interfaces.values().stream()
                .filter(connection -> connection != null && connection.hasFlow())
                .findAny()
                .ifPresent($ -> AllAdvancements.GLASS_PIPE.awardTo(context.getPlayer())));
            FluidTransportBehaviour.cacheFlows(world, pos);
            world.setBlockAndUpdate(pos, CETransport.STAINLESS_GLASS_PIPE.get().defaultBlockState()
                .setValue(GlassFluidPipeBlock.AXIS, axis)
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)));
            FluidTransportBehaviour.loadFlows(world, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CETransport.STAINLESS_PIPE_ENTITY.get();
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }
}
