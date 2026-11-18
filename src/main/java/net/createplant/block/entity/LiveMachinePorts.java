package net.createplant.block.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Stable external ports stop serving cached references when their owner is removed. */
public final class LiveMachinePorts {
    private LiveMachinePorts() { }

    public static IFluidHandler fluid(BlockEntity owner, IFluidHandler storage) {
        return new IFluidHandler() {
            @Override public int getTanks() { return storage.getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) {
                return owner.isRemoved() ? FluidStack.EMPTY : storage.getFluidInTank(tank).copy();
            }
            @Override public int getTankCapacity(int tank) { return storage.getTankCapacity(tank); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return !owner.isRemoved() && storage.isFluidValid(tank, stack);
            }
            @Override public int fill(FluidStack stack, FluidAction action) {
                return owner.isRemoved() ? 0 : storage.fill(stack, action);
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                return owner.isRemoved() ? FluidStack.EMPTY : storage.drain(stack, action);
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                return owner.isRemoved() || amount <= 0 ? FluidStack.EMPTY : storage.drain(amount, action);
            }
        };
    }

    public static IItemHandler items(BlockEntity owner, IItemHandler storage) {
        return new IItemHandler() {
            @Override public int getSlots() { return storage.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) {
                return owner.isRemoved() ? ItemStack.EMPTY : storage.getStackInSlot(slot).copy();
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return owner.isRemoved() ? stack : storage.insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return owner.isRemoved() || amount <= 0 ? ItemStack.EMPTY : storage.extractItem(slot, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return storage.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return !owner.isRemoved() && storage.isItemValid(slot, stack);
            }
        };
    }
}
