package net.createplant.thermal;

import net.createplant.block.entity.ProductionMonitor;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Two finite streams converted together; inlet positions determine the counterflow speed bonus. */
public final class ThermalEquipment {
    private final ReactorMachineBlockEntity owner;
    private final ThermalFluidTank[] tanks=new ThermalFluidTank[4];
    private final ThermalFluidTank[] condensate=new ThermalFluidTank[2];
    private final int[] rowInlet={-1,-1};
    private final int[] outletKelvin={0,0};
    private int coolantRow=-1;
    private int clock,lastCold,lastHot;
    public ThermalEquipment(ReactorMachineBlockEntity owner) {
        this.owner=owner;
        for(int i=0;i<4;i++)tanks[i]=new ThermalFluidTank(8000,f->owner.thermalChanged()) {
            @Override protected boolean mixesGases() { return true; }
        };
        for(int i=0;i<2;i++)condensate[i]=new ThermalFluidTank(8000,f->owner.thermalChanged());
    }
    public int coldFlow(){return lastCold;}
    public int hotFlow(){return lastHot;}
    public int flowPerStep(){return (int)(owner.sections()*100*(counterflow() ? 1.5 : 1));}
    public void stop(){lastCold=lastHot=0;}
    public Component outletReading(int row) {
        double kelvin=outletKelvin[row]/100d;
        return Component.translatable("grade.create_refinery_n_reactors.exchange.outlet",
            Component.translatable("grade.create_refinery_n_reactors.exchange."+(coolantRow<0 ? "circuit" : coolantRow==row ? "coolant" : "process")),
            kelvin==0 ? Component.literal("--") : Component.translatable("grade.create_refinery_n_reactors.exchange.degrees",
                String.format(java.util.Locale.ROOT,"%.1f",kelvin-273.15),Grade.Heat.of(kelvin).label()),(row==0 ? lastCold : lastHot)/5d);
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
    private static boolean sensibleGas(FluidStack stack) {
        return SteamCondensate.vapour(stack) || MixtureHelper.isMixture(stack) && MixtureHelper.gas(stack)
            && Grade.Heat.of(FluidThermal.kelvin(stack)).compareTo(Grade.Heat.AMBIENT)>0;
    }
    private static boolean supported(FluidStack stack){return ThermalStreams.cooling(stack)!=null || ThermalStreams.coolant(stack)!=null || sensibleGas(stack);}
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
                return fluid.is(Fluids.WATER) && !condensate[row].isEmpty() ? condensate[row].drain(fluid.getAmount(),action) : tanks[index+1].drain(fluid,action);
            }
        };
    }

    private record Exchange(FluidStack process, FluidStack coolant, FluidStack water, double processKelvin, double coolantKelvin) { }

    static double effectiveness(int sections, boolean counterflow, double coolantPerTick) {
        return 1-Math.exp(-sections*(counterflow ? .72 : .52)*(.60+.40*coolantPerTick/(coolantPerTick+20)));
    }

    private Exchange exchange(FluidStack hot, FluidStack cold, ThermalStreams.Conversion process, ThermalStreams.Conversion coolant, int batches) {
        double hotKelvin=FluidThermal.kelvin(hot), coldKelvin=FluidThermal.kelvin(cold);
        double cooling=Math.max(0,hotKelvin-coldKelvin)*effectiveness(owner.sections(),counterflow(),coolant.inputAmount()*batches/5d);
        double processKelvin=hotKelvin-cooling, coolantKelvin=coldKelvin+cooling*.35;
        FluidStack product, water=FluidStack.EMPTY;
        if (sensibleGas(hot)) {
            var portion=MixtureHelper.portion(hot,batches);
            var split=SteamCondensate.separate(portion,processKelvin,Integer.MAX_VALUE);
            product=split.gas();water=split.water();
        } else {
            product=process.result(hot,batches);
            if (product.is(CEProcessFluids.WATER_GAS.get()) && processKelvin<=SteamCondensate.BOILING_KELVIN) product.set(CEThermal.CONDENSATE.get(),true);
        }
        return new Exchange(SteamCondensate.withTemperature(product,processKelvin),
            SteamCondensate.withTemperature(coolant.result(cold,batches),coolantKelvin),water,processKelvin,coolantKelvin);
    }

    private boolean fits(int hotRow, Exchange result) {
        return fits(tanks[hotRow*2+1],result.process()) && fits(tanks[(1-hotRow)*2+1],result.coolant()) && fits(condensate[hotRow],result.water());
    }

    private static boolean fits(ThermalFluidTank tank, FluidStack result) {
        return result.isEmpty() || tank.fill(result,IFluidHandler.FluidAction.SIMULATE)==result.getAmount();
    }

    public void tick(){
        if(++clock%5!=0)return;lastCold=lastHot=0;
        for(int hot=0;hot<2;hot++){
            int cold=1-hot;
            var hotInput=tanks[hot*2].getFluid();var coldInput=tanks[cold*2].getFluid();
            var process=ThermalStreams.cooling(hotInput);var coolant=ThermalStreams.coolant(coldInput);
            boolean gas=sensibleGas(hotInput);
            if((process==null && !gas) || coolant==null)continue;
            int processInput=gas ? 1 : process.inputAmount();
            int products=(gas ? 1 : process.outputAmount())+coolant.outputAmount();
            int maximum=owner.sections()*150;
            ProductionMonitor.plan(owner,maximum*products*4d,0,0);
            int capacity=flowPerStep();
            int feed=Math.min(capacity,Math.min(hotInput.getAmount()/processInput,coldInput.getAmount()/coolant.inputAmount()));
            if(feed<=0){owner.thermalStatus("materials",false);return;}
            int batches=feed;
            var result=exchange(hotInput,coldInput,process,coolant,batches);
            boolean space=fits(hot,result);
            while(!space && batches>1) {
                batches/=2;
                result=exchange(hotInput,coldInput,process,coolant,batches);
                space=fits(hot,result);
            }
            if(!space){owner.thermalStatus("output",false);return;}
            ProductionMonitor.limitedBy(owner,batches<feed ? "output" : feed<capacity ? "feed" : capacity<maximum ? "counterflow" : "rated");
            int hotAmount=processInput*batches,coldAmount=coolant.inputAmount()*batches;
            tanks[hot*2].drain(hotAmount,IFluidHandler.FluidAction.EXECUTE);
            tanks[cold*2].drain(coldAmount,IFluidHandler.FluidAction.EXECUTE);
            if(!result.process().isEmpty())tanks[hot*2+1].fill(result.process(),IFluidHandler.FluidAction.EXECUTE);
            tanks[cold*2+1].fill(result.coolant(),IFluidHandler.FluidAction.EXECUTE);
            if(!result.water().isEmpty())condensate[hot].fill(result.water(),IFluidHandler.FluidAction.EXECUTE);
            coolantRow=cold;
            outletKelvin[hot]=(int)Math.round(result.processKelvin()*100);outletKelvin[cold]=(int)Math.round(result.coolantKelvin()*100);
            lastCold=hot==0 ? hotAmount : coldAmount;lastHot=hot==0 ? coldAmount : hotAmount;
            ProductionMonitor.produced(owner,products*batches,0,0);
            owner.thermalStatus("exchange",true);owner.thermalChanged();return;
        }
        owner.thermalStatus(tanks[0].isEmpty() || tanks[2].isEmpty() ? "two_streams" : "materials",false);
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
