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
    /** Share of the best-case heat that opposing flow moves, and the smallest temperature difference worth exchanging. */
    public static final double COUNTERFLOW_EFFICIENCY=.9, MINIMUM_DELTA=.5;
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

    /** The outlets of two streams that pass each other; {@code first} is the hotter when {@code firstHot}. */
    public record Exchange(HeatContent.Shift first,HeatContent.Shift second,int firstLevel,int secondLevel,boolean firstHot) { }

    /**
     * Heat moved per mB of each stream (both take the same amount), counting the latent heat of a stream that boils or condenses on
     * the way: parallel flow ends both at one temperature, counterflow moves 0.9 of the most either stream could give or take
     * between the two inlet temperatures. For streams that keep their phase these are {@code C1C2/(C1+C2)·ΔT} and {@code 0.9·Cmin·ΔT}.
     */
    public static Exchange exchange(FluidStack first,double kelvinA,FluidStack second,double kelvinB,boolean counterflow) {
        boolean firstHot=kelvinA>kelvinB;
        var hot=(firstHot ? first : second).copyWithAmount(1000);var cold=(firstHot ? second : first).copyWithAmount(1000);
        double hotKelvin=Math.max(kelvinA,kelvinB), coldKelvin=Math.min(kelvinA,kelvinB), moved;
        if(counterflow)moved=COUNTERFLOW_EFFICIENCY*Math.min(HeatContent.heatBetween(hot,hotKelvin,coldKelvin),HeatContent.heatBetween(cold,coldKelvin,hotKelvin));
        else {
            // The common outlet temperature: what the hot stream gives down to it equals what the cold one takes up to it.
            double low=coldKelvin, high=hotKelvin;
            for(int step=0;step<32;step++) {
                double middle=(low+high)/2;
                if(HeatContent.heatBetween(hot,hotKelvin,middle)>HeatContent.heatBetween(cold,coldKelvin,middle))low=middle;
                else high=middle;
            }
            moved=HeatContent.heatBetween(cold,coldKelvin,(low+high)/2);
        }
        var shiftA=HeatContent.shift(first.copyWithAmount(1000),kelvinA,firstHot ? -moved : moved);
        var shiftB=HeatContent.shift(second.copyWithAmount(1000),kelvinB,firstHot ? moved : -moved);
        return new Exchange(shiftA,shiftB,FluidState.levelFor(first,shiftA.kelvin(),FluidState.pressure(first),shiftA.gas()),
            FluidState.levelFor(second,shiftB.kelvin(),FluidState.pressure(second),shiftB.gas()),firstHot);
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
        double kelvinA=tanks[0].kelvin(), kelvinB=tanks[2].kelvin();
        if(Math.abs(kelvinA-kelvinB)<MINIMUM_DELTA) {
            credit=0;
            owner.thermalStatus("temperature_delta",false);
            return;
        }
        var exchange=exchange(first,kelvinA,second,kelvinB,counterflow());
        boolean firstHot=exchange.firstHot();
        var shiftA=exchange.first();var shiftB=exchange.second();
        int outA=exchange.firstLevel(), outB=exchange.secondLevel();
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
        // Steam in a hot gas that cools to or below boiling leaves as water through the hot row's condensate tank.
        int hotRow=firstHot ? 0 : 1;
        var hotProduct=hotRow==0 ? productA : productB;
        double hotKelvin=FluidState.kelvin(hotRow==0 ? outA : outB);
        var split=SteamCondensate.separate(hotProduct,hotKelvin,condensate[hotRow].getSpace());
        hotProduct=split.gas().isEmpty() ? split.gas() : FluidState.with(split.gas(),hotRow==0 ? outA : outB,FluidState.pressure(split.gas()));
        if(hotProduct.is(CEProcessFluids.WATER_GAS.get()) && hotKelvin<=SteamCondensate.BOILING_KELVIN)hotProduct.set(CEThermal.CONDENSATE.get(),true);
        if(hotRow==0)productA=hotProduct;else productB=hotProduct;
        int amount=batches*quantum;
        tanks[0].drain(amount,EXECUTE);
        tanks[2].drain(amount,EXECUTE);
        // The outlet buffers take the streams at their real outlet temperature, so rounding to a level loses no heat.
        tanks[1].fillAt(productA,shiftA.kelvin(),shiftA.quality(),EXECUTE);
        tanks[3].fillAt(productB,shiftB.kelvin(),shiftB.quality(),EXECUTE);
        if(!split.water().isEmpty())condensate[hotRow].fill(split.water(),EXECUTE);
        credit=credit-amount;
        if(batches<feed)credit=0;
        coolantRow=firstHot ? 1 : 0;
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
