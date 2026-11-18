package net.createplant.block;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The operating-floor cap over one two-by-two channel bundle. A click on its top addresses that exact subdivision: a wrench
 * changes fuel/control service, and the corresponding rod item is loaded from above. The cap also carries the frequency and
 * local analogue control for every control rod in its bundle; there is no separate winch layer.
 */
public class ReactorCapBlock extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation,
    IBE<RodCapBlockEntity>, IWrenchable {
    public ReactorCapBlock(Properties properties) { super(properties); }

    /** Goggles on any part of the vault read the whole reactor. */
    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof RodCapBlockEntity cap) {
            var column = cap.column();
            if (column != null && column.lithiumTargets() + column.irradiatedTargets() > 0) return column.getBlockPos();
        }
        return net.createplant.nuclear.ReactorCores.controllerUnder(level, pos);
    }

    @Override public Class<RodCapBlockEntity> getBlockEntityClass() { return RodCapBlockEntity.class; }
    @Override public BlockEntityType<? extends RodCapBlockEntity> getBlockEntityType() { return CENuclear.TOP_CAP_ENTITY.get(); }

    public static int slot(BlockPos pos, BlockHitResult hit) {
        double x = Math.clamp(hit.getLocation().x - pos.getX(), 0, Math.nextDown(1d));
        double z = Math.clamp(hit.getLocation().z - pos.getZ(), 0, Math.nextDown(1d));
        return (int) (z * ReactorPhysics.CHANNEL_GRID) * ReactorPhysics.CHANNEL_GRID + (int) (x * ReactorPhysics.CHANNEL_GRID);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP || stack.is(AllItems.WRENCH.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        boolean control = stack.is(CENuclear.CONTROL_ROD.get()), fuel = CENuclear.isFuel(stack) || stack.is(net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.get());
        if (!control && !fuel) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        return onBlockEntityUseItemOn(level, pos, cap -> {
            boolean accepted = control ? cap.installControl(slot(pos, hit)) : cap.installFuel(slot(pos, hit), stack);
            if (!accepted) return ItemInteractionResult.FAIL;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, false);
        return onBlockEntityUse(level, pos, cap -> cap.toggle(slot(pos, hit)) ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || hit.getDirection() != Direction.UP) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, cap -> {
            ItemStack removed = cap.removeControl(slot(pos, hit));
            if (removed.isEmpty()) {
                var items = cap.itemPort(Direction.UP);
                int selected = slot(pos, hit);
                if (items != null && items.getStackInSlot(selected).is(net.createplant.registry.CEFusionFuel.IRRADIATED_TARGET.get())) removed = items.extractItem(selected, 1, false);
            }
            if (removed.isEmpty()) return InteractionResult.PASS;
            player.getInventory().placeItemBackInInventory(removed);
            return InteractionResult.SUCCESS;
        });
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide()) withBlockEntityDo(level, pos, RodCapBlockEntity::neighbourChanged);
    }
}
