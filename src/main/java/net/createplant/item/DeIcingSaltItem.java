package net.createplant.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Road salt: thrown on ice it melts it to water, and on snow it clears it away. Packed and blue ice are too cold for it. */
public class DeIcingSaltItem extends Item {
    public DeIcingSaltItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();BlockPos pos = context.getClickedPos();BlockState state = level.getBlockState(pos);
        BlockState melted = state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) ? (level.dimensionType().ultraWarm() ? Blocks.AIR : Blocks.WATER).defaultBlockState()
            : state.is(BlockTags.SNOW) ? Blocks.AIR.defaultBlockState() : null;
        if (melted == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, melted);
            level.playSound(null, pos, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 1, 1.2f);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
