package net.createplant.thermal;

import net.createplant.block.entity.ProductionMonitor;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Two finite streams converted together; their inlet positions determine the direction of heat exchange. */
public final class ThermalEquipment {
    private final ReactorMachineBlockEntity owner;
    private final ThermalFluidTank[] tanks=new ThermalFluidTank[4];
    private final ThermalFluidTank[] condensate=new ThermalFluidTank[2];
    private final int[] rowInlet={-1,-1};
    private final int[] outletKelvin={0,0};
    private int coolantRow=-1;
    private int clock,lastCold,lastHot,credit;
    public ThermalEquipment(ReactorMachineBlockEntity owner) {
        this.owner=owner;
        for(int i=0;i<4;i++)tanks[i]=new ThermalFluidTank(8000,f->owner.thermalChanged()) {
            @Override protected boolean mixesGases() { return true; }
        };
        for(int i=0;i<2;i++)condensate[i]=new ThermalFluidTank(8000,f->owner.thermalChanged());
    }
    public int coldFlow(){return lastCold;}
    public int hotFlow(){return lastHot;}
    public int flowPerStep(){return owner.sections()*100;}
    public void stop(){lastCold=lastHot=0;}
    public Component outletReading(int row) {
        double kelvin=outletKelvin[row]/100d;
        return Component.translatable("grade.create_refinery_n_reactors.exchange.outlet",
            Component.translatable("grade.create_refinery_n_reactors.exchange."+(coolantRow<0 ? "circuit" : coolantRow==row ? "coolant" : "process")),
            kelvin==0 ? Component.literal("--") : FluidState.temperatureName(FluidState.temperatureLevel(kelvin)),
            (row==0 ? lastCold : lastHot)/5d);
    }
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
    private static boolean supported(FluidStack stack){return !stack.isEmpty();}

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
            private boolean empty() { return tanks[index].isEmpty() && tanks[index+1].isEmpty() && condensate[row].isEmpty(); }
            private boolean input() { return rowInlet[row]<0 || rowInlet[row]==end || empty(); }
            private boolean output() { return valid() && rowInlet[row]>=0 && rowInlet[row]!=end; }
            private boolean drainingWater() { return output() && !condensate[row].isEmpty(); }
            @Override public int getTanks() { return 2; }
            @Override public int getTankCapacity(int slot) { return slot==0 ? tanks[index].getCapacity() : slot==1 ? condensate[row].getCapacity() : 0; }
            @Override public FluidStack getFluidInTank(int slot) {
                if (!valid()) return FluidStack.EMPTY;
                if (drainingWater()) return slot==0 ? condensate[row].getFluid().copy() : slot==1 ? tanks[index+1].getFluid().copy() : FluidStack.EMPTY;
                return slot==0 ? tanks[index+(rowInlet[row]==end ? 0 : 1)].getFluid().copy() : FluidStack.EMPTY;
            }
            @Override public boolean isFluidValid(int slot,FluidStack fluid) { return slot==0 && valid() && input() && supported(fluid) && tanks[index].isFluidValid(fluid); }
            @Override public int fill(FluidStack fluid,FluidAction action) {
                if (!isFluidValid(0,fluid)) return 0;
                int accepted=tanks[index].fill(fluid,action);
                if (action.execute() && accepted>0 && rowInlet[row]!=end) { rowInlet[row]=end;owner.thermalChanged(); }
                return accepted;
            }
            @Override public FluidStack drain(int amount,FluidAction action) {
                return !output() ? FluidStack.EMPTY : (drainingWater() ? condensate[row] : tanks[index+1]).drain(amount,action);
            }
            @Override public FluidStack drain(FluidStack fluid,FluidAction action) {
                if (!output()) return FluidStack.EMPTY;
                return fluid.is(Fluids.WATER) && !condensate[row].isEmpty() ? condensate[row].drain(fluid,action) : tanks[index+1].drain(fluid,action);
            }
        };
    }

    public static int outletLevel(int own,int other,boolean opposing) {
        if(opposing) return Math.abs(own-other)<=1 ? own : other+Integer.signum(own-other);
        return (own+other+(own>other ? 1 : 0))/2;
    }

    private static int gcd(int a,int b) {
        while(b!=0) { int next=a%b; a=b; b=next; }
        return a;
    }

    public void tick() {
        if(++clock%5!=0)return;
        lastCold=lastHot=0;
        FluidStack first=tanks[0].getFluid(), second=tanks[2].getFluid();
        if(first.isEmpty() || second.isEmpty()) {
            credit=0;
            owner.thermalStatus("two_streams",false);
            return;
        }
        int a=FluidState.temperature(first), b=FluidState.temperature(second);
        if(a==b) {
            credit=0;
            owner.thermalStatus("temperature_delta",false);
            return;
        }
        int outA=outletLevel(a,b,counterflow()), outB=outletLevel(b,a,counterflow());
        var changeA=PhaseChange.transfer(first,outA,FluidState.pressure(first));
        var changeB=PhaseChange.transfer(second,outB,FluidState.pressure(second));
        long common=(long)changeA.consumed()/gcd(changeA.consumed(),changeB.consumed())*changeB.consumed();
        int rate=flowPerStep();
        ProductionMonitor.plan(owner,rate*8d,0,0);
        if(common>Math.min(first.getAmount(),second.getAmount())) {
            credit=0;
            owner.thermalStatus("materials",false);
            return;
        }
        int quantum=(int)common;
        credit=(int)Math.min(Integer.MAX_VALUE,Math.min(quantum+(long)rate-1,credit+(long)rate));
        int feed=Math.min(credit,Math.min(first.getAmount(),second.getAmount()))/quantum;
        if(feed==0) {
            owner.thermalStatus("materials",false);
            return;
        }
        int unitA=quantum/changeA.consumed(), unitB=quantum/changeB.consumed();
        int limitA=changeA.fitting(Math.min(changeA.batches(first.getAmount()),feed*unitA),tanks[1])/unitA;
        int limitB=changeB.fitting(Math.min(changeB.batches(second.getAmount()),feed*unitB),tanks[3])/unitB;
        int batches=Math.min(limitA,limitB);
        if(batches==0) {
            owner.thermalStatus("output",false);
            return;
        }
        FluidStack productA=changeA.output(batches*unitA), productB=changeB.output(batches*unitB);
        int amount=batches*quantum;
        tanks[0].drain(amount,EXECUTE);
        tanks[2].drain(amount,EXECUTE);
        tanks[1].fill(productA,EXECUTE);
        tanks[3].fill(productB,EXECUTE);
        credit=credit-amount;
        if(batches<feed)credit=0;
        coolantRow=a<b ? 0 : 1;
        outletKelvin[0]=(int)Math.round(FluidState.kelvin(outA)*100);
        outletKelvin[1]=(int)Math.round(FluidState.kelvin(outB)*100);
        lastCold=lastHot=amount;
        ProductionMonitor.limitedBy(owner,batches<feed ? "output" : amount<rate ? "feed" : "rated");
        ProductionMonitor.produced(owner,productA.getAmount()+(double)productB.getAmount(),0,0);
        owner.thermalStatus("exchange",true);
        owner.thermalChanged();
    }
    public void write(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putIntArray("RowInlets",rowInlet);
        for(int i=0;i<tanks.length;i++)tag.put("Buffer"+i,tanks[i].writeToNBT(lookup,new CompoundTag()));
        tag.putInt("LastCold",lastCold);
        tag.putInt("LastHot",lastHot);
        tag.putIntArray("OutletCentikelvin",outletKelvin);
        tag.putInt("CoolantRow",coolantRow);
        for(int row=0;row<condensate.length;row++) {
            if(!condensate[row].isEmpty())tag.put("Condensate"+row,condensate[row].writeToNBT(lookup,new CompoundTag()));
        }
    }

    public void read(CompoundTag tag, HolderLookup.Provider lookup) {
        var inlets=tag.getIntArray("RowInlets");
        var temperatures=tag.getIntArray("OutletCentikelvin");
        for(int row=0;row<rowInlet.length;row++) {
            rowInlet[row]=inlets.length==2 ? Math.clamp(inlets[row],-1,1) : -1;
            outletKelvin[row]=temperatures.length==2 ? Math.clamp(temperatures[row],0,1000000) : 0;
            condensate[row].readFromNBT(lookup,tag.getCompound("Condensate"+row));
        }
        for(int i=0;i<tanks.length;i++)tanks[i].readFromNBT(lookup,tag.getCompound("Buffer"+i));
        lastCold=Math.max(0,tag.getInt("LastCold"));
        lastHot=Math.max(0,tag.getInt("LastHot"));
        coolantRow=tag.contains("CoolantRow") ? Math.clamp(tag.getInt("CoolantRow"),-1,1) : -1;
    }

    public boolean hasFluid() {
        return java.util.Arrays.stream(tanks).anyMatch(t->!t.isEmpty()) || java.util.Arrays.stream(condensate).anyMatch(t->!t.isEmpty());
    }

    public void clear() {
        for(var tank:tanks)tank.setFluid(FluidStack.EMPTY);
        for(var tank:condensate)tank.setFluid(FluidStack.EMPTY);
        java.util.Arrays.fill(outletKelvin,0);
        coolantRow=-1;
        stop();
    }
}
