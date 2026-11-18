package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.block.PipeGaugeBlock;
import net.createplant.block.PipeGaugeBlock.Kind;
import net.createplant.registry.CETransport;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Inline fluid grades and flow, with native Create value settings and redstone alarms. */
public class PipeGaugeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int STALE=40,SYNC=10;
    private static final String KEY="pipe_gauge.create_refinery_n_reactors.";
    private double reading=Double.NaN,sent=Double.NaN;
    private static final int TEMPERATURE_STEPS=4,PRESSURE_STEPS=2,FLOW_STEPS=46;
    private long observed,lastSync=-SYNC;
    /** Ticks a flow observation holds before the meter falls back to zero. */
    private int span;
    private boolean alarm, vesselMounted;
    private int analogSignal;
    public float dialState,prevDialState;
    /** The row chooses above or below; the column the threshold step. Stored as row × (steps + 1) + step. */
    public ScrollValueBehaviour threshold;
    public PipeGaugeBlockEntity(BlockPos pos,BlockState state) { super(CETransport.PIPE_GAUGE_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new FluidTransportBehaviour(this) {
            @Override public boolean canHaveFlowToward(BlockState state,Direction side) { return side.getAxis()==state.getValue(BlockStateProperties.AXIS); }
        });
        int steps=steps();
        threshold=new ScrollValueBehaviour(Component.translatable(KEY+"threshold"),this,new ValueBoxTransform.Sided() {
            @Override protected Vec3 getSouthLocation() { return new Vec3(.5,.5,15/16d); }
            @Override protected boolean isSideActive(BlockState state,Direction side) {
                return level!=null && state.getBlock() instanceof PipeGaugeBlock gauge ? gauge.shouldRenderHeadOnFace(level,worldPosition,state,side)
                    : side.getAxis().isHorizontal() && side.getAxis()!=state.getValue(BlockStateProperties.AXIS);
            }
        }) {
            @Override public ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player player,BlockHitResult hit) {
                return new ValueSettingsBoard(label,steps,kind()==Kind.FLOW ? 10 : 1,List.of(Component.translatable(KEY+"above"),Component.translatable(KEY+"below")),
                    new ValueSettingsFormatter(v->Component.literal(format(v.value()))));
            }
            @Override public void setValueSettings(net.minecraft.world.entity.player.Player player,ValueSettings settings,boolean ctrlDown) {
                int next=Math.clamp(settings.row(),0,1)*(steps+1)+Math.clamp(settings.value(),0,steps);
                if (next!=value) { setValue(next);playFeedbackSound(this); }
            }
            @Override public ValueSettings getValueSettings() { return new ValueSettings(value/(steps+1),value%(steps+1)); }
            @Override public void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
                super.read(tag,lookup,packet);value=tag.contains("ScrollValue") ? Math.clamp(value,0,2*steps+1) : defaultThreshold();
            }
        };
        threshold.between(0,2*steps+1).requiresWrench().withFormatter(v->format(v%(steps+1)));
        threshold.setValue(defaultThreshold());
        behaviours.add(threshold);
    }
    private int defaultThreshold() { return switch (kind()) { case TEMPERATURE -> 2;case PRESSURE -> 0;case FLOW -> 0; }; }
    private int steps() { return switch (kind()) { case TEMPERATURE -> TEMPERATURE_STEPS;case PRESSURE -> PRESSURE_STEPS;case FLOW -> FLOW_STEPS; }; }
    private double limit(int step) {
        return switch (kind()) {
            case TEMPERATURE -> step;
            case PRESSURE -> step;
            case FLOW -> step<=10 ? step : step<=28 ? 10+(step-10)*5 : 100+(step-28)*50;
        };
    }
    private String format(int step) {
        return switch (kind()) {
            case TEMPERATURE -> net.createplant.thermal.Grade.Heat.values()[Math.clamp(step,0,4)].label().getString();
            case PRESSURE -> net.createplant.thermal.Grade.Pressure.values()[Math.clamp(step,0,2)].label().getString();
            case FLOW -> String.format(Locale.ROOT,"%.0f mB/t",limit(step));
        };
    }
    private boolean below() { return threshold.getValue()>steps(); }
    /** Whether the alarm contact gives its redstone signal. */
    public boolean alarm() { return alarm; }
    public boolean vesselMounted() { return vesselMounted; }
    public double analogRange() { return limit(threshold.getValue()%(steps()+1)); }
    public int analogSignal() { return measured() && kind()!=Kind.FLOW ? (int)Math.round(15*reading/steps()) : 0; }
    private Component alarmLine() {
        return Component.translatable(KEY+(below() ? "alarm_below" : "alarm_above"),format(threshold.getValue()%(steps()+1)),
            Component.translatable(KEY+(alarm ? "alarm_on" : "alarm_off")));
    }
    public Kind kind() { return getBlockState().getBlock() instanceof PipeGaugeBlock gauge ? gauge.kind : Kind.TEMPERATURE; }
    public boolean measured() { return !Double.isNaN(reading); }
    public double reading() { return reading; }
    public void observe(double value) {
        if (level==null || level.isClientSide || !Double.isFinite(value)) return;
        reading=grade(value);observed=level.getGameTime();
    }
    /** Adds mB/t passing a flow meter; transfers in the same tick add up, and the sum holds for the given ticks. */
    public void observeFlow(double perTick,int ticks) {
        if (level==null || level.isClientSide || !Double.isFinite(perTick)) return;
        long now=level.getGameTime();
        reading=observed==now && measured() ? reading+perTick : perTick;observed=now;span=ticks;
    }
    /** Sets the shown reading without a flow, for Ponder scenes; NaN shows nothing. */
    public void preview(double value) { reading=grade(value); }
    private double grade(double value) {return switch(kind()){case TEMPERATURE -> net.createplant.thermal.Grade.Heat.of(value).ordinal();case PRESSURE -> net.createplant.thermal.Grade.Pressure.of(value/1e5).ordinal();case FLOW -> value;};}
    public void observe(net.neoforged.neoforge.fluids.FluidStack fluid) {if(fluid.isEmpty() || kind()==Kind.FLOW)return;var properties=net.createplant.transport.FluidHazards.of(fluid);reading=kind()==Kind.TEMPERATURE ? properties.heat().ordinal() : properties.pressure().ordinal();observed=level.getGameTime();}
    public float dialTarget() {
        if (!measured()) return 0;
        double fraction=kind()==Kind.FLOW ? Math.log10(1+reading)/Math.log10(1001) : reading/steps();
        return (float)Math.clamp(fraction,0,1.1);
    }
    @Override public void tick() {
        super.tick();
        if (level==null) return;
        if (level.isClientSide) {
            prevDialState=dialState;
            dialState+=(dialTarget()-dialState)*.125f;
            return;
        }
        long now=level.getGameTime();
        var vessel=getBlockState().getBlock() instanceof PipeGaugeBlock gauge ? gauge.attachedVessel(level,worldPosition,getBlockState()) : null;
        boolean attachmentChanged=(vessel!=null)!=vesselMounted;
        vesselMounted=vessel!=null;
        boolean sampled=false;
        if(kind()!=Kind.FLOW) {
            if(vessel!=null && !vessel.getTankInventory().isEmpty()) {observe(vessel.getTankInventory().getFluid());sampled=true;}
            var transport=getBehaviour(FluidTransportBehaviour.TYPE);
            if(!sampled && transport!=null) for(Direction side:Direction.values()) {
                var connection=transport.getConnection(side);
                if(connection!=null && !connection.getProvidedFluid().isEmpty()) {observe(connection.getProvidedFluid());sampled=true;break;}
            }
            if(!sampled) for(Direction side:Direction.values()) {
                if(side.getAxis()!=getBlockState().getValue(BlockStateProperties.AXIS))continue;
                BlockPos at=worldPosition.relative(side);if(!level.hasChunkAt(at))continue;
                var port=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,at,side.getOpposite());
                if(port!=null) for(int i=0;i<port.getTanks();i++) if(!port.getFluidInTank(i).isEmpty()){observe(port.getFluidInTank(i));sampled=true;break;}
                if(sampled)break;
            }
            if(!sampled && measured() && now-observed>STALE) reading=Double.NaN;
        } else if(now-observed>span) reading=0;
        int nextAnalog=analogSignal();
        if (nextAnalog!=analogSignal) { analogSignal=nextAnalog;level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock()); }
        if (attachmentChanged) lastSync=-SYNC;
        boolean changed=attachmentChanged || measured()!=!Double.isNaN(sent) || measured() && Math.abs(reading-sent)>switch (kind()) { case TEMPERATURE, PRESSURE -> 0;case FLOW -> .5; };
        double limit=limit(threshold.getValue()%(steps()+1));
        boolean next=measured() && (below() ? reading<limit : reading>limit);
        if (next!=alarm) { alarm=next;level.updateNeighborsAt(worldPosition,getBlockState().getBlock());changed=true;lastSync=-SYNC; }
        if (changed && now-lastSync>=SYNC) { sent=reading;lastSync=now;sendData(); }
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);
        if (packet && measured()) tag.putDouble("Reading",reading);
        if (packet) { tag.putBoolean("Alarm",alarm);tag.putBoolean("VesselMounted",vesselMounted); }
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);
        if (packet) {
            reading=tag.contains("Reading") ? tag.getDouble("Reading") : Double.NaN;alarm=tag.getBoolean("Alarm");vesselMounted=tag.getBoolean("VesselMounted");
        }
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking) {
        tooltip.add(getBlockState().getBlock().getName());
        if (!measured()) tooltip.add(Component.translatable(KEY+"unmeasured"));
        else tooltip.add(switch (kind()) {
            case TEMPERATURE -> net.createplant.thermal.Grade.Heat.values()[Math.clamp((int)reading,0,4)].label();
            case PRESSURE -> net.createplant.thermal.Grade.Pressure.values()[Math.clamp((int)reading,0,2)].label();
            case FLOW -> Component.translatable(KEY+"flow",String.format(Locale.ROOT,"%.1f",reading));
        });
        tooltip.add(alarmLine());
        return true;
    }
}
