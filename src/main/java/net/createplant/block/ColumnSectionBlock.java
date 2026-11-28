package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/** One storey of a ringed column (extraction column, absorption tower): placed as a whole 3 × 3 octagon, with structure blocks filling the ring. */
public abstract class ColumnSectionBlock extends Block {
    public ColumnSectionBlock(Properties properties) { super(properties.pushReaction(PushReaction.BLOCK)); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos centre = context.getClickedPos();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if ((x != 0 || z != 0) && !context.getLevel().getBlockState(centre.offset(x, 0, z)).canBeReplaced(context)) return null;
        return super.getStateForPlacement(context);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) level.setBlock(pos.offset(x, 0, z), ringCell(x, z), 3);
    }

    protected abstract BlockState ringCell(int x, int z);

    /** Edge cells face the centre; corner cells face the edge cell beside them, which leads on to the centre. */
    public static Direction ringFacing(int x, int z) {
        return x != 0 && z != 0 ? Direction.fromDelta(-x, 0, 0) : Direction.fromDelta(-x, 0, -z);
    }
}
