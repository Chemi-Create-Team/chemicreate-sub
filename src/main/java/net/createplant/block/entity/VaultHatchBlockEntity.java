package net.createplant.block.entity;

import net.createplant.block.VaultHatchBlock;
import net.createplant.inventory.VaultHatchMenu;
import net.createplant.registry.CEVault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

public class VaultHatchBlockEntity extends BlockEntity implements MenuProvider {
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override protected void onOpen(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) { setOpen(true); }
        @Override protected void onClose(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) { setOpen(false); }
        @Override protected void openerCountChanged(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, int oldCount, int newCount) { }
        @Override protected boolean isOwnContainer(Player player) {
            return !player.isSpectator() && player.containerMenu instanceof VaultHatchMenu menu && menu.hatch() == VaultHatchBlockEntity.this;
        }
    };

    public VaultHatchBlockEntity(BlockPos pos, BlockState state) { super(CEVault.HATCH_ENTITY.get(), pos, state); }

    @Override public void onLoad() {
        super.onLoad();
        if (!level.isClientSide && getBlockState().getValue(VaultHatchBlock.OPEN)) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    public void open(ServerPlayer player) {
        if (VaultHatchMenu.inventoryAt(this) == null) {
            player.displayClientMessage(Component.translatable("message.create_refinery_n_reactors.vault_hatch.unavailable"), true);
            return;
        }
        player.openMenu(this, data -> data.writeBlockPos(worldPosition));
    }

    public void startOpen(Player player) {
        if (!isRemoved() && !player.isSpectator()) openers.incrementOpeners(player, level, worldPosition, getBlockState());
    }

    public void stopOpen(Player player) {
        if (!isRemoved() && !player.isSpectator()) openers.decrementOpeners(player, level, worldPosition, getBlockState());
    }

    public void recheckOpen() {
        if (!isRemoved()) {
            openers.recheckOpeners(level, worldPosition, getBlockState());
            if (openers.getOpenerCount() == 0 && getBlockState().getValue(VaultHatchBlock.OPEN)) setOpen(false);
        }
    }

    private void setOpen(boolean open) {
        BlockState state = getBlockState();
        if (state.getValue(VaultHatchBlock.OPEN) == open) return;
        level.setBlock(worldPosition, state.setValue(VaultHatchBlock.OPEN, open), 3);
        level.playSound(null, worldPosition, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS, .5f, .9f + level.random.nextFloat() * .1f);
    }

    @Override public Component getDisplayName() { return CEVault.HATCH.get().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new VaultHatchMenu(id, inventory, this); }
}
