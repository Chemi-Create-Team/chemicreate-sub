package net.createplant.thermal;

import net.createplant.block.entity.ProductionMonitor;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Two finite streams converted together; inlet positions determine the counterflow speed bonus. */
public final class ThermalEquipment {
    private final ReactorMachineBlockEntity owner;
    private final ThermalFluidTank[] tanks=new ThermalFluidTank[4];
    private final int[] rowInlet={-1,-1};
    private int clock,lastCold,lastHot;
    public ThermalEquipment(ReactorMachineBlockEntity owner){this.owner=owner;for(int i=0;i<4;i++)tanks[i]=new ThermalFluidTank(8000,f->owner.thermalChanged());}
    public int coldFlow(){return lastCold;}
    public int hotFlow(){return lastHot;}
    public int flowPerStep(){return (int)(owner.sections()*100*(counterflow() ? 1.5 : 1));}
    public void stop(){lastCold=lastHot=0;}
    public boolean counterflow(){return rowInlet[0]>=0 && rowInlet[1]>=0 && rowInlet[0]!=rowInlet[1];}
    public Component exchangeMode(){return Component.translatable("counterflow.create_refinery_n_reactors."+(counterflow() ? "counter" : "parallel"));}
    public static double fuelValue(FluidStack stack) {
        if (stack.is(CEPetroFluids.DIESEL.get()) || stack.is(CEProcessFluids.LOW_SULFUR_DIESEL.get())) return 34000;
        if (stack.is(CEPetroFluids.GASOLINE.get())) return 32000;
        if (stack.is(CEPetroFluids.HEAVY_OIL.get())) return 38000;
        if (stack.is(CEFluids.CONVERTER_GAS.get())) {
            // CO + 1/2 O2 -> CO2: 283 kJ/mol. Gas mB use this mod's one-bar reference density.
            double kgPerMb = FluidThermal.properties(stack).kilogramsPerLitre() / 1000d;
            return kgPerMb / FluidMaterials.molarMass(stack) * 283_000;
        }
        return 0;
    }
    public ThermalFluidTank tank(int index) { return tanks[index]; }
    private static boolean supported(FluidStack stack){return ThermalStreams.cooling(stack)!=null || ThermalStreams.coolant(stack)!=null;}
    public IFluidHandler port(int index,boolean input,boolean output,boolean live) {
        long generation=owner.portGeneration();
        return new IFluidHandler() {
            private boolean valid() { return live && owner.thermalReady() && generation==owner.portGeneration(); }
            private ThermalFluidTank storage() { return tanks[index]; }
            @Override public int getTanks() { return 1; }
            @Override public int getTankCapacity(int slot) { return slot==0 ? 8000 : 0; }
            @Override public FluidStack getFluidInTank(int slot) { return slot==0 && generation==owner.portGeneration() && !owner.isRemoved() ? storage().getFluid().copy() : FluidStack.EMPTY; }
            @Override public boolean isFluidValid(int slot,FluidStack stack) { return valid() && input && slot==0 && !stack.isEmpty() && supported(stack) && storage().isFluidValid(stack); }
            @Override public int fill(FluidStack stack,FluidAction action) { return valid() && input && supported(stack) ? storage().fill(stack,action) : 0; }
            @Override public FluidStack drain(int amount,FluidAction action) { return valid() && output ? storage().drain(amount,action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack request,FluidAction action) { return valid() && output ? storage().drain(request,action) : FluidStack.EMPTY; }
        };
    }
    /** Each row chooses its direction from an accepted fill; simulations never reserve it. */
    public IFluidHandler rowPort(int row,int end) {
        int index=row*2;long generation=owner.portGeneration();
        return new IFluidHandler() {
            private boolean valid() { return owner.thermalReady() && generation==owner.portGeneration(); }
            private boolean empty() { return tanks[index].isEmpty() && tanks[index+1].isEmpty(); }
            private boolean input() { return rowInlet[row]<0 || rowInlet[row]==end || empty(); }
            @Override public int getTanks() { return 1; }
            @Override public int getTankCapacity(int slot) { return slot==0 ? tanks[index].getCapacity() : 0; }
            @Override public FluidStack getFluidInTank(int slot) { return slot!=0 || !valid() ? FluidStack.EMPTY : tanks[index+(rowInlet[row]==end ? 0 : 1)].getFluid().copy(); }
            @Override public boolean isFluidValid(int slot,FluidStack fluid) { return slot==0 && valid() && input() && supported(fluid) && tanks[index].isFluidValid(fluid); }
            @Override public int fill(FluidStack fluid,FluidAction action) {
                if (!valid() || !input()) return 0;
                int accepted=tanks[index].fill(fluid,action);
                if (action.execute() && accepted>0 && rowInlet[row]!=end) { rowInlet[row]=end;owner.thermalChanged(); }
                return accepted;
            }
            @Override public FluidStack drain(int amount,FluidAction action) { return valid() && rowInlet[row]>=0 && rowInlet[row]!=end ? tanks[index+1].drain(amount,action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack fluid,FluidAction action) { return valid() && rowInlet[row]>=0 && rowInlet[row]!=end ? tanks[index+1].drain(fluid,action) : FluidStack.EMPTY; }
        };
    }
    public void tick(){
        if(++clock%5!=0)return;lastCold=lastHot=0;
        for(int hot=0;hot<2;hot++){
            var process=ThermalStreams.cooling(tanks[hot*2].getFluid());var coolant=ThermalStreams.coolant(tanks[(1-hot)*2].getFluid());
            if(process==null || coolant==null)continue;
            var first=hot==0 ? process : coolant;var second=hot==0 ? coolant : process;
            int maximum = owner.sections() * 150;
            int products = first.outputAmount() + second.outputAmount();
            ProductionMonitor.plan(owner, maximum * products * 4d, 0, 0);
            int capacity = flowPerStep();
            int feed = Math.min(capacity, Math.min(tanks[0].getFluidAmount()/first.inputAmount(), tanks[2].getFluidAmount()/second.inputAmount()));
            int batches = feed;
            for(int row=0;row<2;row++){
                var conversion=row==0 ? first : second;
                batches=Math.min(batches,tanks[row*2+1].fill(conversion.result(tanks[row*2].getFluid(),batches),IFluidHandler.FluidAction.SIMULATE)/conversion.outputAmount());
            }
            if(batches<=0){owner.thermalStatus(tanks[0].isEmpty() || tanks[2].isEmpty() ? "two_streams" : "output",false);return;}
            ProductionMonitor.limitedBy(owner, batches < feed ? "output" : feed < capacity ? "feed" : capacity < maximum ? "counterflow" : "rated");
            for(int row=0;row<2;row++){
                var conversion=row==0 ? first : second;var result=conversion.result(tanks[row*2].getFluid(),batches);tanks[row*2].drain(conversion.inputAmount()*batches,IFluidHandler.FluidAction.EXECUTE);tanks[row*2+1].fill(result,IFluidHandler.FluidAction.EXECUTE);
            }
            ProductionMonitor.produced(owner, products * batches, 0, 0);
            lastCold=first.inputAmount()*batches;lastHot=second.inputAmount()*batches;owner.thermalStatus("exchange",true);owner.thermalChanged();return;
        }
        owner.thermalStatus(tanks[0].isEmpty() || tanks[2].isEmpty() ? "two_streams" : "materials",false);
    }
    public void write(CompoundTag tag,HolderLookup.Provider lookup){tag.putIntArray("RowInlets",rowInlet);for(int i=0;i<4;i++)tag.put("Buffer"+i,tanks[i].writeToNBT(lookup,new CompoundTag()));tag.putInt("LastCold",lastCold);tag.putInt("LastHot",lastHot);}
    public void read(CompoundTag tag,HolderLookup.Provider lookup){var inlets=tag.getIntArray("RowInlets");for(int row=0;row<2;row++)rowInlet[row]=inlets.length==2 ? Math.clamp(inlets[row],-1,1) : -1;for(int i=0;i<4;i++)tanks[i].readFromNBT(lookup,tag.getCompound("Buffer"+i));lastCold=Math.max(0,tag.getInt("LastCold"));lastHot=Math.max(0,tag.getInt("LastHot"));}
    public boolean hasFluid(){return java.util.Arrays.stream(tanks).anyMatch(t->!t.isEmpty());}
    public void clear(){for(var tank:tanks)tank.setFluid(FluidStack.EMPTY);stop();}
}
