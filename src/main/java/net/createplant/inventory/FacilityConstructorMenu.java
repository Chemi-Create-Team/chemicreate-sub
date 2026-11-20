package net.createplant.inventory;

import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.registry.CEAssembly;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class FacilityConstructorMenu extends SchematicannonMenu {
    public FacilityConstructorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) { super(CEAssembly.MENU.get(), id, inventory, data); }
    public FacilityConstructorMenu(int id, Inventory inventory, FacilityConstructorBlockEntity be) { super(CEAssembly.MENU.get(), id, inventory, be); }
    public FacilityConstructorBlockEntity facility() { return (FacilityConstructorBlockEntity) contentHolder; }

    @Override protected void addSlots() {
        for (int index = 0; index < 2; index++) addSlot(new SlotItemHandler(contentHolder.inventory, index, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        addSlot(new SlotItemHandler(contentHolder.inventory, 2, 134, 19));
        addSlot(new SlotItemHandler(contentHolder.inventory, 3, 174, 19));
        addSlot(new SlotItemHandler(contentHolder.inventory, 4, 15, 19));
        addPlayerSlots(37, 161);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player)) return false;
        if (button >= 0 && button < AssemblyPlan.Kind.values().length) return facility().configure(button, facility().sizeIndex(AssemblyPlan.Kind.values()[button]));
        if ((button == 30 || button == 31) && facility().kind().isCircularTank())
            return facility().configure(facility().kind().ordinal(), facility().sizeIndex() + (button == 30 ? -1 : 1));
        if (button >= 20 && button < 22) return facility().configureHeight(button - 20);
        if (button >= 10 && button < 15) return facility().configure(facility().kind().ordinal(), button - 10);
        return false;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot != 0 && slot != 1 && stillValid(player)) super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 2 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        boolean moved = index < 5 ? moveItemStackTo(stack, 5, slots.size(), true)
            : moveItemStackTo(stack, 2, 3, false) || moveItemStackTo(stack, 4, 5, false);
        if (!moved) return ItemStack.EMPTY;
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return contentHolder != null && !contentHolder.isRemoved() && player.level() == contentHolder.getLevel()
            && player.distanceToSqr(contentHolder.getBlockPos().getCenter()) <= 64;
    }
}
