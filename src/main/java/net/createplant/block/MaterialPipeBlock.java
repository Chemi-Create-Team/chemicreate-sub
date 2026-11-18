package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.transport.PipeMaterial;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** New material fittings are still actual Create pipes, not a second network. */
public final class MaterialPipeBlock extends FluidPipeBlock {
    public final PipeMaterial material;
    public MaterialPipeBlock(Properties properties, PipeMaterial material) {
        super(properties);this.material=material;
        var state=defaultBlockState();
        for (var p:PROPERTY_BY_DIRECTION.values()) state=state.setValue(p,false);
        registerDefaultState(state);
    }
    @Override public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() { return CETransport.PIPE.get(); }
    @Override public InteractionResult onWrenched(net.minecraft.world.level.block.state.BlockState s,UseOnContext c) { return InteractionResult.PASS; }
    @Override protected MapCodec<? extends net.minecraft.world.level.block.PipeBlock> codec() {
        return simpleCodec(p->new MaterialPipeBlock(p,material));
    }
}
