package net.createplant.item;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A nitrogen fertiliser used like bone meal: the clicked plant and the crops in the 3 x 3 around it each grow as far as
 * {@code strength} bone meals would, for one item. Deployers use it the same way.
 */
public class FertilizerItem extends Item {
    private final int strength;

    public FertilizerItem(int strength, Properties properties) { super(properties);this.strength = strength; }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();BlockPos centre = context.getClickedPos();ItemStack stack = context.getItemInHand();Player player = context.getPlayer();
        if (!BoneMealItem.applyBonemeal(stack.copy(), level, centre, player)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            grow(level, centre, stack, player, strength - 1);
            level.levelEvent(1505, centre, 15);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1)))
                if (!pos.equals(centre) && level.getBlockState(pos).is(BlockTags.CROPS) && grow(level, pos, stack, player, strength)) level.levelEvent(1505, pos, 15);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean grow(Level level, BlockPos pos, ItemStack stack, Player player, int times) {
        boolean grown = false;
        for (int i = 0; i < times; i++) grown |= BoneMealItem.applyBonemeal(stack.copy(), level, pos, player);
        return grown;
    }
}
