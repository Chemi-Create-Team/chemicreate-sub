package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An opaque resin pipe using Create's pressure, topology and fluid-network
 * implementation. No parallel transfer tick or global pipe patch is needed.
 */
public final class ResinPipeBlock extends FluidPipeBlock {
    public static final MapCodec<ResinPipeBlock> CODEC = simpleCodec(ResinPipeBlock::new);

    public ResinPipeBlock(Properties properties) {
        super(properties);
        // PipeBlock's raw default enables all six faces. Placement recomputes
        // them, but structures/commands may use the raw state: start closed so
        // a two-ended line cannot silently acquire four open drain branches.
        BlockState closed=defaultBlockState();
        for (var property:PROPERTY_BY_DIRECTION.values()) closed=closed.setValue(property,false);
        registerDefaultState(closed);
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CEBlockEntityTypes.RESIN_PIPE.get();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // Create's standard pipe wrench action replaces the block with a copper
        // glass pipe. A resin fitting must retain its material identity instead.
        // Topology follows adjacent pipes automatically; sneak-wrench removal
        // continues to use Create's normal protected, loot-aware implementation.
        return InteractionResult.PASS;
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }
}
