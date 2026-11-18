package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.createplant.block.CoolingTowerPortBlock;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CoolingTowerPortBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private BlockPos controller;
    public CoolingTowerPortBlockEntity(BlockPos pos, BlockState state) { super(CEPlantSystems.TOWER_PORT_ENTITY.get(), pos, state); }
    public void assign(BlockPos pos) {
        if (pos.equals(controller)) return;
        controller = pos;setChanged();level.invalidateCapabilities(worldPosition);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public CoolingTowerBlockEntity owner() {
        return !isRemoved() && controller != null && level != null && level.hasChunkAt(controller)
            && level.getBlockEntity(controller) instanceof CoolingTowerBlockEntity tower ? tower : null;
    }
    public int tank() { return ((CoolingTowerPortBlock) getBlockState().getBlock()).tank; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.saveAdditional(tag, lookup);if (controller != null) tag.putLong("Tower", controller.asLong()); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.loadAdditional(tag, lookup);controller = tag.contains("Tower") ? BlockPos.of(tag.getLong("Tower")) : null; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) { return owner() != null && owner().addToGoggleTooltip(text, sneaking); }
}
