package net.createplant.item;

import net.createplant.registry.CEPortableTools;
import net.createplant.thermal.FluidThermal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Copy on read and commit, so capability simulations cannot mutate an item's component. */
public final class SprayToolFluidHandler implements IFluidHandlerItem {
    private final ItemStack stack;
    public SprayToolFluidHandler(ItemStack stack) { this.stack=stack; }
    private SprayToolItem tool() { return (SprayToolItem)stack.getItem(); }
    public static FluidStack contents(ItemStack stack) {
        var fluid=stack.get(CEPortableTools.CONTENTS.get());return fluid==null ? FluidStack.EMPTY : fluid.copy();
    }
    private void store(FluidStack fluid) {
        if(fluid.isEmpty())stack.remove(CEPortableTools.CONTENTS.get());
        else stack.set(CEPortableTools.CONTENTS.get(),SimpleFluidContent.copyOf(fluid));
    }
    @Override public ItemStack getContainer() { return stack; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) { return tank==0 ? contents(stack) : FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) { return tank==0 ? tool().capacity() : 0; }
    @Override public boolean isFluidValid(int tank,FluidStack fluid) { return tank==0 && stack.getCount()==1 && tool().accepts(fluid); }
    @Override public int fill(FluidStack fluid,FluidAction action) {
        if(!isFluidValid(0,fluid))return 0;
        var stored=contents(stack);
        if(!stored.isEmpty() && !FluidThermal.compatible(stored,fluid))return 0;
        int amount=Math.min(fluid.getAmount(),Math.max(0,tool().capacity()-stored.getAmount()));
        if(amount>0 && action.execute())store(FluidThermal.add(stored,fluid,amount));
        return amount;
    }
    @Override public FluidStack drain(int amount,FluidAction action) {
        if(amount<=0 || stack.getCount()!=1)return FluidStack.EMPTY;
        var stored=contents(stack);int taken=Math.min(amount,stored.getAmount());
        if(taken<=0)return FluidStack.EMPTY;
        var result=stored.copyWithAmount(taken);
        if(action.execute()){stored.shrink(taken);store(stored);}
        return result;
    }
    @Override public FluidStack drain(FluidStack fluid,FluidAction action) {
        return !fluid.isEmpty() && FluidThermal.compatible(contents(stack),fluid) ? drain(fluid.getAmount(),action) : FluidStack.EMPTY;
    }
}
