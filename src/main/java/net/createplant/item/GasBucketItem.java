package net.createplant.item;

import com.simibubi.create.AllBlocks;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class GasBucketItem extends BucketItem {
    public GasBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (content.isSame(CEProcessFluids.WATER_GAS.get())) {
            var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(AllBlocks.BLAZE_BURNER.get()))
                return InteractionResultHolder.fail(stack);
        }
        var fluid=StatefulBuckets.contents(stack,content);
        if(!net.createplant.thermal.FluidState.gas(fluid))return InteractionResultHolder.fail(stack);
        var surface=getPlayerPOVHitResult(level,player,ClipContext.Fluid.NONE);
        var position=surface.getType()==HitResult.Type.BLOCK ? surface.getBlockPos().relative(surface.getDirection()) : player.blockPosition().above();
        if(!level.mayInteract(player,position) || !StatefulBuckets.release(level,position,fluid))return InteractionResultHolder.fail(stack);
        if (player.getAbilities().instabuild) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        ItemStack result = ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET));
        return InteractionResultHolder.sidedSuccess(result, level.isClientSide());
    }

    @Override public boolean emptyContents(Player player,Level level,BlockPos pos,BlockHitResult hitResult) {
        return emptyContents(player,level,pos,hitResult,new ItemStack(this));
    }

    @Override public boolean emptyContents(Player player,Level level,BlockPos pos,BlockHitResult hitResult,ItemStack stack) {
        var fluid=StatefulBuckets.contents(stack,content);
        return net.createplant.thermal.FluidState.gas(fluid) && StatefulBuckets.release(level,pos,fluid);
    }
}
