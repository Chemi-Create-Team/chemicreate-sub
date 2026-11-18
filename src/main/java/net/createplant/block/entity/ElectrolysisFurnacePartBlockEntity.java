package net.createplant.block.entity;

import net.createplant.registry.CEFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A shell or anode block of the electrolysis furnace. It holds nothing: its faces feed oxide and anode carbon into the furnace
 * and its underside gives the metal, through the controller that formed it.
 */
public class ElectrolysisFurnacePartBlockEntity extends BlockEntity {
    private BlockPos owner;
    public ElectrolysisFurnacePartBlockEntity(BlockPos pos, BlockState state) { super(CEFurnace.PART_ENTITY.get(), pos, state); }
    public BlockPos owner() { return owner; }
    public void setOwner(BlockPos pos) { if (!pos.equals(owner)) { owner = pos;setChanged(); } }
    public void structureChanged() {
        if (owner != null && level != null && level.isLoaded(owner)
            && level.getBlockEntity(owner) instanceof ElectrolysisFurnaceBlockEntity furnace) furnace.structureChanged();
    }
    public ElectrolysisFurnaceBlockEntity controller() {
        if (owner == null || level == null || !level.isLoaded(owner) || !(level.getBlockEntity(owner) instanceof ElectrolysisFurnaceBlockEntity furnace) || !furnace.formed()) return null;
        return furnace;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries);if (owner != null) tag.put("Owner", NbtUtils.writeBlockPos(owner)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries);owner = NbtUtils.readBlockPos(tag, "Owner").orElse(null); }

    /** Feed on every face but the bottom; metal leaves only from the bottom. */
    private IItemHandler port(Direction side) {
        boolean bottom = side == Direction.DOWN;
        return new IItemHandler() {
            private IItemHandler inventory() { var furnace = controller();return furnace == null ? null : furnace.inventory(); }
            @Override public int getSlots() { return 3; }
            @Override public ItemStack getStackInSlot(int slot) { var inventory = inventory();return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                var inventory = inventory();
                return inventory == null || bottom || slot == ElectrolysisFurnaceBlockEntity.PRODUCT ? stack : inventory.insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                var inventory = inventory();
                return inventory == null || !bottom || slot != ElectrolysisFurnaceBlockEntity.PRODUCT ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { var inventory = inventory();return inventory != null && !bottom && slot != ElectrolysisFurnaceBlockEntity.PRODUCT && inventory.isItemValid(slot, stack); }
        };
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEFurnace.PART_ENTITY.get(), (be, side) -> be.port(side == null ? Direction.UP : side));
    }
}
