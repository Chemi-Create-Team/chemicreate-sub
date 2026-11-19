package net.createplant.safety;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Local, recoverable residue from fictional process incidents. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class ProcessGasIncidents extends SavedData {
    private static final int CAPACITY = 1000;
    private static final int DILUTION_PER_SECOND = 5;
    private static final int MAX_SITES_PER_CHUNK = 16;
    private static final int REACH = 4;
    private static final Factory<ProcessGasIncidents> FACTORY = new Factory<>(ProcessGasIncidents::new, ProcessGasIncidents::load);
    private static final String ID = CreatePlant.MOD_ID + "_process_gas_incidents";

    private record Site(int amount, long second) {
        int remaining(long now) {
            long elapsed = Math.clamp(now - second, 0, CAPACITY / DILUTION_PER_SECOND);
            return Math.max(0, amount - (int) elapsed * DILUTION_PER_SECOND);
        }
    }

    private final Map<Long, Map<BlockPos, Site>> chunks = new HashMap<>();

    public static void record(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0 || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return;
        level.getDataStorage().computeIfAbsent(FACTORY, ID).add(pos, amount, second(level));
    }

    public static int concentration(ServerLevel level, BlockPos pos) {
        var data = level.getDataStorage().get(FACTORY, ID);
        return data == null ? 0 : data.reading(level, pos);
    }

    public static int collect(ServerLevel level, BlockPos intake, int limit) {
        var data = level.getDataStorage().get(FACTORY, ID);
        if (limit <= 0 || data == null || !level.hasChunkAt(intake) || !level.getBlockState(intake).isAir()) return 0;
        long now = second(level);
        var nearest = data.nearby(level, intake)
            .filter(entry -> entry.getValue().remaining(now) > 0 && airPath(level, intake, entry.getKey()))
            .min(Comparator.<Map.Entry<BlockPos, Site>>comparingDouble(entry -> entry.getKey().distSqr(intake))
                .thenComparing(Map.Entry::getKey));
        if (nearest.isEmpty()) return 0;
        var entry = nearest.get();
        int present = entry.getValue().remaining(now), taken = Math.min(limit, present);
        var sites = data.chunks.get(ChunkPos.asLong(entry.getKey()));
        if (present == taken) sites.remove(entry.getKey());
        else sites.put(entry.getKey(), new Site(present - taken, now));
        if (sites.isEmpty()) data.chunks.remove(ChunkPos.asLong(entry.getKey()));
        data.setDirty();
        return taken;
    }

    private void add(BlockPos pos, int amount, long now) {
        var sites = chunks.computeIfAbsent(ChunkPos.asLong(pos), chunk -> new HashMap<>());
        var previous = sites.get(pos);
        if (previous == null && sites.size() >= MAX_SITES_PER_CHUNK) return;
        int present = previous == null ? 0 : previous.remaining(now);
        sites.put(pos.immutable(), new Site(present + Math.min(amount, CAPACITY - present), now));
        setDirty();
    }

    int reading(ServerLevel level, BlockPos pos) {
        long now = second(level);
        int amount = nearby(level, pos).mapToInt(entry -> entry.getValue().remaining(now)).max().orElse(0);
        return (amount + 9) / 10;
    }

    private Stream<Map.Entry<BlockPos, Site>> nearby(ServerLevel level, BlockPos pos) {
        if (chunks.isEmpty()) return Stream.empty();
        var lower = new ChunkPos(pos.offset(-REACH, 0, -REACH));
        var upper = new ChunkPos(pos.offset(REACH, 0, REACH));
        return ChunkPos.rangeClosed(lower, upper)
            .filter(chunk -> level.getChunkSource().hasChunk(chunk.x, chunk.z))
            .flatMap(chunk -> chunks.getOrDefault(chunk.toLong(), Map.of()).entrySet().stream())
            .filter(entry -> entry.getKey().distSqr(pos) <= REACH * REACH && level.getBlockState(entry.getKey()).isAir());
    }

    private static boolean airPath(ServerLevel level, BlockPos from, BlockPos to) {
        return BlockGetter.traverseBlocks(Vec3.atCenterOf(from), Vec3.atCenterOf(to), level,
            (world, at) -> !world.hasChunkAt(at) || !world.getBlockState(at).isAir() ? Boolean.FALSE : null,
            world -> Boolean.TRUE);
    }

    private static long second(ServerLevel level) { return level.getGameTime() / 20; }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        var data = level.getDataStorage().get(FACTORY, ID);
        if (data != null) data.update(level);
    }

    private void update(ServerLevel level) {
        long now = second(level);
        var iterator = chunks.values().iterator();
        while (iterator.hasNext()) {
            var sites = iterator.next();
            if (sites.values().removeIf(site -> site.remaining(now) == 0)) setDirty();
            if (sites.isEmpty()) {
                iterator.remove();
                continue;
            }
            for (var pos : sites.keySet()) {
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) continue;
                level.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    2, .22, .12, .22, .005);
            }
        }
    }

    static ProcessGasIncidents load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data = new ProcessGasIncidents();
        for (var raw : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            int amount = Math.clamp(row.getInt("Amount"), 0, CAPACITY);
            if (amount > 0) data.add(BlockPos.of(row.getLong("Pos")), amount, Math.max(0, row.getLong("Second")));
        }
        data.setDirty(false);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        var sites = new ListTag();
        for (var chunk : chunks.values()) for (var entry : chunk.entrySet()) {
            var row = new CompoundTag();
            row.putLong("Pos", entry.getKey().asLong());
            row.putInt("Amount", entry.getValue().amount());
            row.putLong("Second", entry.getValue().second());
            sites.add(row);
        }
        tag.put("Sites", sites);
        return tag;
    }
}
