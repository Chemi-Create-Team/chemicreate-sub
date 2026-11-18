package net.createplant.block;

import net.createplant.registry.CEFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VitrificationCanisterBlock extends Block {
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 2);
    public final boolean filled;
    public VitrificationCanisterBlock(Properties properties, boolean filled) { super(properties);this.filled = filled;registerDefaultState(defaultBlockState().setValue(SECTION, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SECTION); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context)
            && context.getLevel().getBlockState(context.getClickedPos().above(2)).canBeReplaced(context) ? defaultBlockState() : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        for (int i = 1; i <= 2; i++) level.setBlock(pos.above(i), state.setValue(SECTION, i), 3);
        if (filled && level instanceof net.minecraft.server.level.ServerLevel server) {
            var data = net.createplant.nuclear.RadiationWorldData.get(server);
            var profile = net.createplant.nuclear.RadiationSources.profile(stack);
            data.blockActivity.put(pos.immutable(), stack.getOrDefault(net.createplant.registry.CERadiation.MATERIAL.get(), profile.fresh(net.createplant.nuclear.RadiationSources.now(level))));
            data.blocks.add(pos.immutable());data.setDirty();
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock()) && !level.isClientSide()) {
            BlockPos bottom = pos.below(state.getValue(SECTION));
            for (int i = 0; i <= 2; i++) if (!bottom.above(i).equals(pos) && level.getBlockState(bottom.above(i)).is(this)) level.destroyBlock(bottom.above(i), i == 0);
        }
        super.onRemove(state, level, pos, next, moved);
    }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        var origin = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (filled && origin != null) {
            var pos = BlockPos.containing(origin).below(state.getValue(SECTION));
            var material = net.createplant.nuclear.RadiationWorldData.get(builder.getLevel()).blockActivity.get(pos);
            if (material != null) for (var stack : drops) if (stack.is(asItem())) stack.set(net.createplant.registry.CERadiation.MATERIAL.get(), material);
        }
        return drops;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(3, 0, 3, 13, 16, 13); }
}
