package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createplant.block.ProcessControlBlock;
import net.createplant.registry.CEHeating;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

public class FlowRegulatorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Each step covers one sixteenth of the manual dial scale. */
    public static final int STEPS=16,STEP_FLOW=16,MAX_FLOW=STEPS*STEP_FLOW;
    public ScrollValueBehaviour maximum;
    private long usedTick=-1;
    private int used;
    public FlowRegulatorBlockEntity(BlockPos pos,BlockState state) { super(CEHeating.REGULATOR_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new FluidTransportBehaviour(this) {
            @Override public boolean canHaveFlowToward(BlockState state,Direction side) { return side.getAxis()==state.getValue(BlockStateProperties.AXIS); }
        });
        maximum=new ScrollValueBehaviour(Component.translatable("thermal_control.create_refinery_n_reactors.flow"),this,new ValueBoxTransform.Sided() {
            @Override protected Vec3 getSouthLocation() { return new Vec3(.5,.5,1); }
            @Override protected boolean isSideActive(BlockState state,Direction side) { return side==plate(state); }
        }) {
            @Override public ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player player,BlockHitResult hit) {
                return new ValueSettingsBoard(label,STEPS,4,List.of(Component.literal("mB/t")),new ValueSettingsFormatter(v->Component.literal(v.value()*STEP_FLOW+" mB/t")));
            }
            @Override public void setValueSettings(net.minecraft.world.entity.player.Player player,ValueSettings settings,boolean ctrlDown) { setValue(Math.clamp(settings.value(),0,STEPS)*STEP_FLOW); }
            @Override public ValueSettings getValueSettings() { return new ValueSettings(0,value/STEP_FLOW); }
            @Override public void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) { super.read(tag,lookup,packet);value=tag.contains("ScrollValue") ? Math.clamp(value,0,MAX_FLOW) : 32; }
        };
        maximum.between(0,MAX_FLOW).requiresWrench().withFormatter(v->v+" mB/t");maximum.setValue(32);behaviours.add(maximum);
    }
    /** The side of the valve that carries the pointer plate. */
    public static Direction plate(BlockState state) { return state.getValue(BlockStateProperties.AXIS)==Direction.Axis.Y ? Direction.NORTH : Direction.UP; }
    private ProcessControlBlockEntity actuator() {
        if (level!=null && level.getBlockEntity(worldPosition.relative(plate(getBlockState()))) instanceof ProcessControlBlockEntity actuator
            && actuator.kind()==ProcessControlBlock.Kind.ACTUATOR && actuator.attachment().equals(worldPosition))
            return actuator;
        return null;
    }
    public int effectiveLimit() {
        var actuator=actuator();
        return actuator==null ? maximum.getValue() : (int)Math.round(actuator.getBlockState().getValue(ProcessControlBlock.INDICATOR)
            * ProcessControlBlockEntity.ACTUATED_FLOW / 15d);
    }
    public float gaugeProgress() {
        var actuator=actuator();
        return actuator==null ? Mth.clamp(maximum.getValue()/(float)MAX_FLOW,0,1)
            : actuator.getBlockState().getValue(ProcessControlBlock.INDICATOR)/15f;
    }
    public int remaining() { return Math.max(0,effectiveLimit()-(level!=null && usedTick==level.getGameTime() ? used : 0)); }
    public void consume(int amount) { long now=level.getGameTime();if (usedTick!=now) {usedTick=now;used=0;}used+=amount;setChanged(); }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) { super.write(tag,lookup,packet);tag.putLong("UsedTick",usedTick);tag.putInt("Used",used); }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) { super.read(tag,lookup,packet);usedTick=tag.getLong("UsedTick");used=Math.clamp(tag.getInt("Used"),0,ProcessControlBlockEntity.ACTUATED_FLOW); }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) { text.add(Component.translatable("thermal_control.create_refinery_n_reactors.flow_readout",effectiveLimit()));return true; }
}
