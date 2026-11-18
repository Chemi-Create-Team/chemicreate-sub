package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createplant.registry.CEHeating;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Passive ownership/port proxy; no inventory and no per-part ticker. */
public class ThermalPartBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private BlockPos controller;
    public ThermalPartBlockEntity(BlockPos pos,BlockState state) { super(CEHeating.PART_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {  }
    public void assign(BlockPos pos) {
        if (pos.equals(controller)) return;controller=pos;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);level.invalidateCapabilities(worldPosition);
    }
    public ReactorMachineBlockEntity owner() {
        return controller!=null && level!=null && level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof ReactorMachineBlockEntity be ? be : null;
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) { var be=owner();return be!=null && be.owns(worldPosition) && be.addToGoggleTooltip(text,sneaking); }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) { super.write(tag,lookup,packet);if (controller!=null) tag.putLong("Controller",controller.asLong()); }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) { controller=tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;super.read(tag,lookup,packet); }
}
