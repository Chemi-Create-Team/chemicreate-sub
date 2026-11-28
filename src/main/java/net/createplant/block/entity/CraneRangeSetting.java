package net.createplant.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.function.BiPredicate;
import net.createplant.block.CraneStructure;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** One of a gantry drive's two range settings, set with a wrench on its own face and saved under its own key. */
public final class CraneRangeSetting extends ScrollValueBehaviour {
    private final BehaviourType<ScrollValueBehaviour> type;
    private final String key;
    private final int minimum, netId;

    public CraneRangeSetting(Component label, SmartBlockEntity owner, BiPredicate<BlockState, Direction> face, BehaviourType<ScrollValueBehaviour> type,
        String key, int minimum, int maximum, int netId) {
        super(label, owner, new CenteredSideValueBoxTransform(face) {
            @Override protected Vec3 getSouthLocation() { return new Vec3(.5, 14.5 / 16d, 15.5 / 16d); }
        });
        this.type = type;this.key = key;this.minimum = minimum;this.netId = netId;
        between(minimum, maximum).requiresWrench();
        value = 16;
    }

    @Override public BehaviourType<?> getType() { return type; }
    @Override public int netId() { return netId; }
    @Override public String getClipboardKey() { return key; }
    @Override public boolean testHit(Vec3 hit) { return true; }
    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) { tag.putInt(key, value); }
    @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        if (tag.contains(key)) value = Math.clamp(tag.getInt(key), minimum, CraneStructure.MAX_LENGTH);
    }
}
