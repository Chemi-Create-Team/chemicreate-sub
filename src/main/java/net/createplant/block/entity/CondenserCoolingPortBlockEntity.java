package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CondenserCoolingPortBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private BlockPos controller;
    public CondenserCoolingPortBlockEntity(BlockPos pos, BlockState state) { super(CEPlantSystems.CONDENSER_PORT_ENTITY.get(), pos, state); }
    public void assign(BlockPos pos) {
        if (pos.equals(controller)) return;controller = pos;setChanged();level.invalidateCapabilities(worldPosition);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public CondenserWaterboxBlockEntity owner() { return !isRemoved() && controller != null && level != null && level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof CondenserWaterboxBlockEntity be ? be : null; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.saveAdditional(tag, lookup);if (controller != null) tag.putLong("CondenserOwner", controller.asLong()); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.loadAdditional(tag, lookup);controller = tag.contains("CondenserOwner") ? BlockPos.of(tag.getLong("CondenserOwner")) : null; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) { return owner() != null && owner().addToGoggleTooltip(text, sneaking); }
}
