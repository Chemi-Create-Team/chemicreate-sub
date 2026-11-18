package net.createplant.inventory;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.createplant.block.VaultHatchBlock;
import net.createplant.block.entity.VaultHatchBlockEntity;
import net.createplant.registry.CEVault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class VaultHatchMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 54;
    private final VaultHatchBlockEntity hatch;
    private final IItemHandlerModifiable inventory;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot pages = DataSlot.standalone();

    public VaultHatchMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(id, playerInventory, null, new ItemStackHandler(PAGE_SIZE));
        data.readBlockPos();
    }

    public VaultHatchMenu(int id, Inventory playerInventory, VaultHatchBlockEntity hatch) {
        this(id, playerInventory, hatch, inventoryAt(hatch));
        hatch.startOpen(playerInventory.player);
    }

    private VaultHatchMenu(int id, Inventory playerInventory, VaultHatchBlockEntity hatch, IItemHandlerModifiable inventory) {
        super(CEVault.MENU.get(), id);
        this.hatch = hatch;
        this.inventory = inventory;
        pages.set(inventory == null ? 1 : Math.max(1, (inventory.getSlots() + PAGE_SIZE - 1) / PAGE_SIZE));
        addDataSlot(page);
        addDataSlot(pages);
        var visible = new PageInventory();
        for (int row = 0; row < 6; row++) for (int column = 0; column < 9; column++)
            addSlot(new SlotItemHandler(visible, row * 9 + column, 8 + column * 18, 38 + row * 18) {
                @Override public void setChanged() { visible.setStackInSlot(getSlotIndex(), getItem()); }
            });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 159 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 217));
    }

    public VaultHatchBlockEntity hatch() { return hatch; }
    public int page() { return page.get(); }
    public int pages() { return pages.get(); }

    public static IItemHandlerModifiable inventoryAt(VaultHatchBlockEntity hatch) {
        if (hatch == null || hatch.isRemoved() || hatch.getLevel() == null) return null;
        var level = hatch.getLevel();
        Direction facing = hatch.getBlockState().getValue(VaultHatchBlock.FACING);
        BlockPos at = hatch.getBlockPos().relative(facing.getOpposite());
        if (!level.hasChunkAt(at) || !(level.getBlockEntity(at) instanceof ItemVaultBlockEntity vault)
            || !level.hasChunkAt(vault.getController())) return null;
        ItemVaultBlockEntity controller = vault.getControllerBE();
        if (controller == null || controller.isRemoved()) return null;
        int width = controller.getWidth();
        int length = controller.getHeight();
        BlockPos corner = controller.getBlockPos().offset(
            controller.getMainConnectionAxis() == Direction.Axis.X ? length - 1 : width - 1,
            width - 1, controller.getMainConnectionAxis() == Direction.Axis.Z ? length - 1 : width - 1);
        if (!level.hasChunksAt(controller.getBlockPos(), corner)) return null;
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, at, facing);
        return handler instanceof IItemHandlerModifiable modifiable ? modifiable : null;
    }

    @Override public boolean stillValid(Player player) {
        return hatch == null || !hatch.isRemoved() && player.level() == hatch.getLevel()
            && player.distanceToSqr(hatch.getBlockPos().getCenter()) <= 64
            && inventory != null && inventoryAt(hatch) == inventory;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || !getCarried().isEmpty() || (button != 0 && button != 1)) return false;
        int next = page() + (button == 0 ? -1 : 1);
        if (next < 0 || next >= pages()) return false;
        resetQuickCraft();
        page.set(next);
        broadcastFullState();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, index < PAGE_SIZE ? PAGE_SIZE : 0, index < PAGE_SIZE ? slots.size() : PAGE_SIZE, index < PAGE_SIZE))
            return ItemStack.EMPTY;
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (hatch != null) hatch.stopOpen(player);
    }

    private class PageInventory implements IItemHandlerModifiable {
        private int index(int slot) { return hatch == null ? slot : page() * PAGE_SIZE + slot; }
        private boolean valid(int slot) { return inventory != null && index(slot) >= 0 && index(slot) < inventory.getSlots(); }
        @Override public int getSlots() { return PAGE_SIZE; }
        @Override public ItemStack getStackInSlot(int slot) { return valid(slot) ? inventory.getStackInSlot(index(slot)) : ItemStack.EMPTY; }
        @Override public void setStackInSlot(int slot, ItemStack stack) { if (valid(slot)) inventory.setStackInSlot(index(slot), stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return valid(slot) ? inventory.insertItem(index(slot), stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return valid(slot) ? inventory.extractItem(index(slot), amount, simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return valid(slot) ? inventory.getSlotLimit(index(slot)) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return valid(slot) && inventory.isItemValid(index(slot), stack); }
    }
}
