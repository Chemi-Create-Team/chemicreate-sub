package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.createplant.nuclear.ControlRodLinks;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/** Sends the highest adjacent 100-step controller setting to top caps on the same two-item frequency. */
public class ControlRodLinkBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private LinkBehaviour link;
    private Couple<Frequency> key;
    private int setting;
    private boolean sendingControl;

    public ControlRodLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.ROD_LINK_ENTITY.get(), pos, state);
        setLazyTickRate(10);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        link = LinkBehaviour.transmitter(this, TopFrequencySlot.pair(), () -> 0);
        behaviours.add(link);
    }

    public int setting() { return setting; }

    public boolean sendingControl() { return sendingControl; }

    @Override public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide()) return;
        var next = link.getNetworkKey();
        if (!next.equals(key)) {
            if (key != null) ControlRodLinks.remove(level, key, worldPosition);
            key = next;
            ControlRodLinks.add(level, key, worldPosition);
        }
        int highest = 0;
        boolean foundController = false;
        for (Direction side : Direction.values()) {
            BlockPos adjacent = worldPosition.relative(side);
            if (level.hasChunkAt(adjacent)
                && level.getBlockEntity(adjacent) instanceof ControlRodControllerBlockEntity controller) {
                foundController = true;
                highest = Math.max(highest, controller.setting());
            }
        }
        if (highest != setting || foundController != sendingControl) {
            setting = highest;
            sendingControl = foundController;
            setChanged();
            sendData();
        }
    }

    @Override public void remove() {
        super.remove();
        if (level != null && key != null) ControlRodLinks.remove(level, key, worldPosition);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(sendingControl
            ? Component.translatable("control_rod_link.create_refinery_n_reactors.setting", setting)
            : Component.translatable("control_rod_link.create_refinery_n_reactors.idle"));
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Setting", setting);
        tag.putBoolean("SendingControl", sendingControl);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        setting = tag.getInt("Setting");
        sendingControl = tag.getBoolean("SendingControl");
    }
}
