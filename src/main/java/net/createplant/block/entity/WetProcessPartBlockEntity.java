package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createplant.block.WetProcessPartBlock;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class WetProcessPartBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private BlockPos controller;
    public WetProcessPartBlockEntity(BlockPos pos, BlockState state) { super(CEWetProcessing.PART_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public WetProcessBlockEntity owner() {
        return level != null && controller != null && level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof WetProcessBlockEntity be
            && !be.isRemoved() && (level.isClientSide || be.owns(worldPosition)) ? be : null;
    }
    public void bind(BlockPos pos) { if (!pos.equals(controller)) { controller = pos.immutable();setChanged();sendData();level.invalidateCapabilities(worldPosition); } }
    public void structureChanged(BlockPos changed) { var be = owner();if (be != null) be.cellChanged(changed); }
    public void invalidateOwner() { var be = owner();if (be != null) be.structureChanged();if (level != null) level.invalidateCapabilities(worldPosition); }
    @Override public void onChunkUnloaded() { invalidateOwner();super.onChunkUnloaded(); }
    public IFluidHandler fluidPort(Direction side) {
        var role = ((WetProcessPartBlock) getBlockState().getBlock()).role;
        var be = owner();return be != null && face(side, role) ? be.fluidPort(role) : null;
    }
    public IItemHandler itemPort(Direction side) {
        var role = ((WetProcessPartBlock) getBlockState().getBlock()).role;
        var be = owner();return be != null && role == WetProcessPartBlock.Role.UNDERFLOW && face(side, role) ? be.itemPort(role) : null;
    }
    private boolean face(Direction side, WetProcessPartBlock.Role role) {
        if (role == WetProcessPartBlock.Role.FEED) return side == Direction.DOWN;
        return side != null && role.port() && (side == getBlockState().getValue(WetProcessPartBlock.FACING) || role == WetProcessPartBlock.Role.UNDERFLOW && side == Direction.DOWN);
    }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        var be = owner();
        if (be != null) return be.addToGoggleTooltip(lines, sneaking);
        lines.add(Component.translatable("wet_process.create_refinery_n_reactors.status.structure"));return true;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) { super.write(tag, lookup, packet);if (controller != null) tag.putLong("Controller", controller.asLong()); }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) { super.read(tag, lookup, packet);controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null; }
}
