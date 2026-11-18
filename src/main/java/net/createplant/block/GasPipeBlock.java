package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.createplant.registry.CEGasPipes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Reinforced native Create transport; the large bore fills its block. */
public final class GasPipeBlock extends FluidPipeBlock {
    public final boolean large;
    public GasPipeBlock(Properties properties,boolean large) {
        super(properties);this.large=large;
        var state=defaultBlockState();
        for (var p:PROPERTY_BY_DIRECTION.values()) state=state.setValue(p,false);
        registerDefaultState(state);
    }
    @Override public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() { return CEGasPipes.GAS_PIPE_ENTITY.get(); }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) { return InteractionResult.PASS; }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) {
        return large ? net.minecraft.world.phys.shapes.Shapes.block() : super.getShape(state,level,pos,context);
    }
    @Override protected MapCodec<? extends PipeBlock> codec() { return simpleCodec(p->new GasPipeBlock(p,large)); }
}
