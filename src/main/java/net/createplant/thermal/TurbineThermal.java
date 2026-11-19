package net.createplant.thermal;

import net.createplant.block.entity.*;
import net.createplant.registry.CEBlocks;
import net.createplant.block.entity.ProductionMonitor;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** One rotor's finite inventories, grade-dependent work, wear and condenser budget. */
public final class TurbineThermal {
    public static final int CAPACITY=16000,MAX_FLOW=4096;
    public static final double MAX_J=12000000,CONDENSER_HEAT_CAPACITY=4000000000d,WEAR_LIMIT=10000000000d;
    private final SteamTurbineBlockEntity owner;
    private final ThermalFluidTank input,exhaust;
    private double work,totalWork,condenserJoules,totalCondenserJoules,pendingCondenserJoules,wear;
    private long totalCondensate;
    private String status="idle";
    private boolean previewDirty=true;
    private double previewWork;
    public TurbineThermal(SteamTurbineBlockEntity owner){this.owner=owner;input=tank(true);exhaust=tank(false);}
    private ThermalFluidTank tank(boolean inlet){return new ThermalFluidTank(CAPACITY,s->{previewDirty=true;owner.thermalChanged();}){
        @Override protected int storageRatio(){return 1;}
        @Override public boolean isFluidValid(FluidStack stack){return SteamGrades.of(stack)!=null || !inlet && stack.is(net.minecraft.world.level.material.Fluids.WATER);}
    };}
    public ThermalFluidTank input(){return input;}
    public ThermalFluidTank exhaust(){return exhaust;}
    public double work(){return work;}
    public double totalWork(){return totalWork;}
    public double condenserJoules(){return condenserJoules;}
    public double totalCondenserJoules(){return totalCondenserJoules;}
    public double pendingCondenserJoules(){return pendingCondenserJoules;}
    public long totalCondensate(){return totalCondensate;}
    public double wear(){return wear;}
    public String status(){return status;}
    public boolean service(){if(wear<=0 || work>0 || !input.isEmpty() || !exhaust.isEmpty())return false;wear=0;previewDirty=true;owner.thermalChanged();return true;}
    public double removeCondenserHeat(double joules){double moved=Double.isFinite(joules) ? Math.clamp(joules,0,pendingCondenserJoules) : 0;pendingCondenserJoules-=moved;if(moved>0){previewDirty=true;owner.thermalChanged();}return moved;}
    private boolean lowPressure(){return CEBlocks.isLowPressureSteamTurbine(owner.getBlockState());}
    public static SteamTurbineBlockEntity source(Level world,BlockPos pos){
        if(!world.hasChunkAt(pos))return null;var be=world.getBlockEntity(pos);
        return be instanceof SteamTurbineBlockEntity rotor ? rotor : be instanceof HighPressureTurbineEndBlockEntity end ? end.exhaustSource() : be instanceof HighPressureTurbinePartBlockEntity part ? part.rotor() : null;
    }
    public static boolean serviceAt(Level world,BlockPos pos){
        var rotor=source(world,pos);if(rotor==null)return false;
        var rotors=new java.util.ArrayList<SteamTurbineBlockEntity>();
        if(rotor.unitLayout()==null)rotors.add(rotor);else for(var section:rotor.unitAssembly()){
            if(!(world.getBlockEntity(section.origin()) instanceof SteamTurbineBlockEntity part))return false;rotors.add(part);
        }
        if(rotors.isEmpty() || rotors.stream().noneMatch(part->part.thermodynamics().wear>0) || rotors.stream().anyMatch(part->{var state=part.thermodynamics();return state.work>0 || !state.input.isEmpty() || !state.exhaust.isEmpty();}))return false;
        if(!world.isClientSide)for(var part:rotors)part.thermodynamics().service();return true;
    }
    private int available(int limit){
        var grade=SteamGrades.of(input.getFluid());if(grade==null || limit<=0 || wear>=WEAR_LIMIT)return 0;
        int n=Math.min(limit,input.getFluidAmount());double perMb=grade.workPerMb(lowPressure());
        if(perMb>0)n=Math.min(n,(int)Math.floor(MAX_J/perMb));
        n=Math.min(n,(int)Math.min(Integer.MAX_VALUE,Math.floor((WEAR_LIMIT-wear)/grade.wearPerMb())));
        if(lowPressure())n=Math.min(n,(int)Math.floor((CONDENSER_HEAT_CAPACITY-pendingCondenserJoules)/SteamGrades.CONDENSER_JOULES_PER_MB));
        return Math.min(n,exhaust.fill(SteamGrades.outlet(input.getFluid(),n,lowPressure()),SIMULATE));
    }
    /** A read-only preview lets loaded generators start before any steam is spent. */
    public double capacityWork(){
        if(!owner.canPublishStressCapacity())return 0;if(work>0)return work;
        if(previewDirty){var grade=SteamGrades.of(input.getFluid());previewWork=grade==null ? 0 : available(MAX_FLOW)*grade.workPerMb(lowPressure());previewDirty=false;}
        return previewWork;
    }
    public int step(boolean enabled,int limit){
        work=condenserJoules=0;previewDirty=true;status=enabled ? "feed" : "structure";
        var grade = SteamGrades.of(input.getFluid());
        var ratedGrade = grade == null ? SteamGrades.SUPERHEATED : grade;
        double perMb = ratedGrade.workPerMb(lowPressure());
        int maximum = perMb <= 0 ? MAX_FLOW : Math.min(MAX_FLOW, (int) Math.floor(MAX_J / perMb));
        if (enabled) ProductionMonitor.plan(owner, maximum * 20d, 0, maximum * perMb / 50);
        if(!enabled || input.isEmpty()){ProductionMonitor.limitedBy(owner, status);return 0;}
        if(wear>=WEAR_LIMIT){status="wear";ProductionMonitor.limitedBy(owner, status);return 0;}
        if(grade==null){status="fluid";ProductionMonitor.limitedBy(owner, "steam");return 0;}
        int feed = Math.min(maximum, input.getFluidAmount());
        int n=available(limit);
        boolean limitedCooling = lowPressure() && CONDENSER_HEAT_CAPACITY - pendingCondenserJoules < feed * SteamGrades.CONDENSER_JOULES_PER_MB;
        ProductionMonitor.limitedBy(owner, limitedCooling ? "condenser" : n < feed ? "output" : feed < maximum ? "steam" : perMb == 0 ? "steam_grade" : "rated");
        if(n<=0){status=lowPressure() && CONDENSER_HEAT_CAPACITY-pendingCondenserJoules<SteamGrades.CONDENSER_JOULES_PER_MB ? "condenser" : "output";return 0;}
        var result=SteamGrades.outlet(input.getFluid(),n,lowPressure());input.drain(n,EXECUTE);exhaust.fill(result,EXECUTE);
        work=n*grade.workPerMb(lowPressure());totalWork+=work;wear+=n*grade.wearPerMb();
        if(lowPressure()){condenserJoules=n*SteamGrades.CONDENSER_JOULES_PER_MB;pendingCondenserJoules+=condenserJoules;totalCondenserJoules+=condenserJoules;totalCondensate+=n;}
        ProductionMonitor.produced(owner, result.getAmount(), 0, work);
        status=work>0 ? "running" : "pass";owner.thermalChanged();return n;
    }
    public void stop(String why){work=condenserJoules=0;status=why;previewDirty=true;ProductionMonitor.limitedBy(owner, why);}
    public record Vent(int mass,double joules){ }
    public Vent release(){var grade=SteamGrades.of(input.getFluid());var outGrade=SteamGrades.of(exhaust.getFluid());var result=new Vent(input.getFluidAmount()+exhaust.getFluidAmount(),pendingCondenserJoules+(grade==null ? 0 : input.getFluidAmount()*grade.joulesPerMb())+(outGrade==null ? 0 : exhaust.getFluidAmount()*outGrade.joulesPerMb()));clear();return result;}
    public void clear(){input.setFluid(FluidStack.EMPTY);exhaust.setFluid(FluidStack.EMPTY);work=condenserJoules=pendingCondenserJoules=0;previewDirty=true;}
    public void write(CompoundTag tag,HolderLookup.Provider lookup){tag.put("RotorInput",input.writeToNBT(lookup,new CompoundTag()));tag.put("RotorExhaust",exhaust.writeToNBT(lookup,new CompoundTag()));tag.putDouble("RotorWork",work);tag.putDouble("RotorTotalWork",totalWork);tag.putDouble("RotorCondenserJoules",condenserJoules);tag.putDouble("RotorTotalCondenserJoules",totalCondenserJoules);tag.putDouble("RotorPendingCondenserJoules",pendingCondenserJoules);tag.putDouble("RotorWear",wear);tag.putLong("RotorTotalCondensate",totalCondensate);tag.putString("RotorStatus",status);}
    public void read(CompoundTag tag,HolderLookup.Provider lookup){input.readFromNBT(lookup,tag.getCompound("RotorInput"));exhaust.readFromNBT(lookup,tag.getCompound("RotorExhaust"));totalWork=finite(tag.getDouble("RotorTotalWork"),Double.MAX_VALUE);totalCondenserJoules=finite(tag.getDouble("RotorTotalCondenserJoules"),Double.MAX_VALUE);pendingCondenserJoules=finite(tag.getDouble("RotorPendingCondenserJoules"),CONDENSER_HEAT_CAPACITY);wear=finite(tag.getDouble("RotorWear"),WEAR_LIMIT);totalCondensate=Math.max(0,tag.getLong("RotorTotalCondensate"));work=condenserJoules=0;status=tag.getString("RotorStatus");previewDirty=true;}
    public void readClient(CompoundTag tag){work=finite(tag.getDouble("RotorWork"),MAX_J);condenserJoules=finite(tag.getDouble("RotorCondenserJoules"),CONDENSER_HEAT_CAPACITY);}
    private static double finite(double value,double max){return Double.isFinite(value) ? Math.clamp(value,0,max) : 0;}
}
