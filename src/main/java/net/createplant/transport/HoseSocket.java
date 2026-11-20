package net.createplant.transport;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.createplant.CreatePlant;
import net.createplant.registry.CEPressureInfrastructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class HoseSocket {
    public static final int MAX_DISTANCE = 32;
    public static final String KEY = "hose." + CreatePlant.MOD_ID + ".";

    public interface Endpoint {
        HoseSocket hose();
        Direction hoseFace();
        IFluidHandler hoseFluid();
        default boolean hoseReady() { return true; }
        default int hoseRole() { return 0; }
        default void hoseChanged() { }
    }

    private final BlockEntity owner;
    private BlockPos remote;
    private Direction remoteFace;
    private UUID id;

    public HoseSocket(BlockEntity owner) { this.owner = owner; }
    public BlockPos position() { return owner.getBlockPos(); }
    public BlockPos remote() { return remote; }
    public Direction remoteFace() { return remoteFace; }
    public boolean connected() { return remote != null; }

    public static Endpoint endpoint(net.minecraft.world.level.Level level, BlockPos pos) {
        return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Endpoint endpoint ? endpoint : null;
    }

    public static Vec3 mouth(BlockPos pos, Direction direction) {
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(.505));
    }

    public Endpoint peer() {
        if (remote == null || owner.getLevel() instanceof ServerLevel server && !Links.get(server).active.contains(id)) return null;
        var other = endpoint(owner.getLevel(), remote);
        return other != null && position().equals(other.hose().remote()) && id.equals(other.hose().id) ? other : null;
    }

    public boolean connect(Endpoint second) {
        var first = (Endpoint) owner;
        var other = second.hose();
        var level = owner.getLevel();
        if (level == null || level.isClientSide || level != other.owner.getLevel() || owner == other.owner
                || owner.isRemoved() || other.owner.isRemoved() || connected() || other.connected()
                || !first.hoseReady() || !second.hoseReady()
                || first.hoseRole() != 0 && first.hoseRole() == second.hoseRole()
                || !position().closerThan(other.position(), MAX_DISTANCE + .00001)
                || !level.getWorldBorder().isWithinBounds(other.position())) return false;
        if (!clearMouth() || !other.clearMouth()) return false;
        UUID link = UUID.randomUUID();
        Links.get((ServerLevel) level).add(link);
        set(other.position(), second.hoseFace(), link);
        other.set(position(), first.hoseFace(), link);
        return true;
    }

    private boolean clearMouth() {
        var level = owner.getLevel();
        var adjacent = position().relative(((Endpoint) owner).hoseFace());
        return level.hasChunkAt(adjacent) && level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty()
            && com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, adjacent) == null
            && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, adjacent,
                ((Endpoint) owner).hoseFace().getOpposite()) == null;
    }

    public boolean disconnect(boolean drop) {
        var level = owner.getLevel();
        if (remote == null || level == null || level.isClientSide) return false;
        var other = peer();
        boolean live = Links.get((ServerLevel) level).remove(id);
        set(null, null, null);
        if (other != null) other.hose().set(null, null, null);
        if (live && drop && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) {
            var at = position();
            Containers.dropItemStack(level, at.getX()+.5, at.getY()+.5, at.getZ()+.5, CEPressureInfrastructure.HOSE.toStack());
        }
        return true;
    }

    /** Detached ends may have been unloaded while their other end was removed. */
    public void validate() {
        var level = owner.getLevel();
        if (remote == null || !(level instanceof ServerLevel server)) return;
        if (!Links.get(server).active.contains(id)) {
            set(null, null, null);
            return;
        }
        var mouth = position().relative(((Endpoint) owner).hoseFace());
        if (level.hasChunkAt(mouth) && !clearMouth()) disconnect(true);
    }

    private void set(BlockPos pos, Direction face, UUID link) {
        remote = pos;
        remoteFace = face;
        id = link;
        owner.setChanged();
        if (owner instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) smart.sendData();
        owner.getLevel().invalidateCapabilities(position());
        ((Endpoint) owner).hoseChanged();
    }

    public void write(CompoundTag tag) {
        if (remote == null) return;
        tag.putUUID("HoseId", id);
        tag.putLong("HoseEnd", remote.asLong());
        tag.putString("HoseFace", remoteFace.getName());
    }

    public void read(CompoundTag tag) {
        remoteFace = Direction.byName(tag.getString("HoseFace"));
        id = tag.hasUUID("HoseId") ? tag.getUUID("HoseId") : null;
        remote = id != null && remoteFace != null && tag.contains("HoseEnd") ? BlockPos.of(tag.getLong("HoseEnd")) : null;
        if (remote != null && (remote.equals(position()) || !remote.closerThan(position(), MAX_DISTANCE + .00001))) remote = null;
    }
    private static final class Links extends SavedData {
        private static final Factory<Links> FACTORY = new Factory<>(Links::new, Links::load);
        private final Set<UUID> active = new HashSet<>();

        static Links get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, "createplant_hose_links");
        }

        void add(UUID id) {
            if (active.add(id)) setDirty();
        }

        boolean remove(UUID id) {
            if (!active.remove(id)) return false;
            setDirty();
            return true;
        }

        private static Links load(CompoundTag tag, HolderLookup.Provider lookup) {
            var links = new Links();
            for (Tag row : tag.getList("Links", Tag.TAG_STRING)) {
                try { links.active.add(UUID.fromString(row.getAsString())); }
                catch (IllegalArgumentException ignored) { }
            }
            return links;
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
            var rows = new ListTag();
            active.forEach(id -> rows.add(StringTag.valueOf(id.toString())));
            tag.put("Links", rows);
            return tag;
        }
    }

}
