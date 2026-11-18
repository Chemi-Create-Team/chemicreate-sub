package net.createplant.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-only, bounded discovery; block entities and server chunks never acquire audio state. */
public final class MachineSounds {
    static final int RANGE = 32, MAX_LOOPS = 24;
    private static final int CHUNK_RADIUS = 6, SCAN_INTERVAL = 5, ENTITY_BUDGET = 128, CHUNK_BUDGET = 4;
    private static final Map<BlockPos, Loop> LOOPS = new LinkedHashMap<>();
    private static final List<ChunkPos> OFFSETS = offsets();
    private static ClientLevel level;
    private static ChunkPos origin;
    private static LevelChunk scanningChunk;
    private static Iterator<BlockEntity> scanning;
    private static int chunkIndex, ticks;

    private MachineSounds() { }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level) { clear(mc);level = mc.level; }
        if (level == null || mc.player == null || mc.isPaused() || muted(mc)) { clear(mc);return; }
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        ticks++;
        var iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            var loop = iterator.next().getValue();
            if (!loop.valid(listener) || ticks % 20 == 0 && (!loop.source.intact()
                || ticks - loop.started > 20 && !mc.getSoundManager().isActive(loop))) {
                loop.finish();mc.getSoundManager().stop(loop);iterator.remove();
            }
        }
        if (ticks % SCAN_INTERVAL == 0) discover(mc, listener);
    }

    private static boolean muted(Minecraft mc) {
        return mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0 || mc.options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0;
    }

    private static void discover(Minecraft mc, Vec3 listener) {
        var nextOrigin = new ChunkPos(BlockPos.containing(listener));
        if (!nextOrigin.equals(origin)) { origin = nextOrigin;scanning = null;scanningChunk = null;chunkIndex = 0; }
        int entities = 0, chunks = 0;
        while (entities < ENTITY_BUDGET && chunks < CHUNK_BUDGET) {
            if (scanning == null) {
                var offset = OFFSETS.get(chunkIndex);chunkIndex=(chunkIndex+1)%OFFSETS.size();
                scanningChunk = level.getChunkSource().getChunk(origin.x + offset.x, origin.z + offset.z, ChunkStatus.FULL, false);
                chunks++;
                if (scanningChunk == null) continue;
                // The scan resumes on later ticks, so it walks a copy: the chunk's own map may change in between.
                scanning = List.copyOf(scanningChunk.getBlockEntities().values()).iterator();
            }
            if (level.getChunkSource().getChunk(scanningChunk.getPos().x, scanningChunk.getPos().z, ChunkStatus.FULL, false) != scanningChunk) {
                scanning = null;scanningChunk = null;continue;
            }
            if (!scanning.hasNext()) { scanning = null;scanningChunk = null;continue; }
            var be = scanning.next();entities++;
            if (be == null || be.isRemoved() || LOOPS.containsKey(be.getBlockPos()) || Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(listener) > 96 * 96) continue;
            var source = MachineSoundSource.find(be);
            if (source == null || LOOPS.containsKey(source.owner().getBlockPos()) || !source.active()
                || source.position().distanceToSqr(listener) > source.range() * source.range()) continue;
            if (LOOPS.size() == MAX_LOOPS) {
                var farthest = LOOPS.entrySet().stream().max(Comparator.comparingDouble(entry -> entry.getValue().source.position().distanceToSqr(listener))).orElseThrow();
                if (source.position().distanceToSqr(listener) + 4 >= farthest.getValue().source.position().distanceToSqr(listener)) continue;
                farthest.getValue().finish();mc.getSoundManager().stop(farthest.getValue());LOOPS.remove(farthest.getKey());
            }
            var loop = new Loop(source, ticks);LOOPS.put(source.owner().getBlockPos(), loop);mc.getSoundManager().play(loop);
        }
    }

    static int activeCount() { return LOOPS.size(); }
    static boolean contains(BlockPos owner) { return LOOPS.containsKey(owner); }
    static SoundInstance instance(BlockPos owner) { return LOOPS.get(owner); }

    static void clear(Minecraft mc) {
        for (var loop : LOOPS.values()) { loop.finish();mc.getSoundManager().stop(loop); }
        LOOPS.clear();scanning = null;scanningChunk = null;origin = null;chunkIndex = 0;ticks = 0;
    }

    private static List<ChunkPos> offsets() {
        var result = new ArrayList<ChunkPos>();
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) result.add(new ChunkPos(x, z));
        result.sort(Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
        return List.copyOf(result);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final MachineSoundSource source;
        private final int started;

        private Loop(MachineSoundSource source, int started) {
            super(source.kind().event.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.source = source;this.started = started;
            looping = true;delay = 0;relative = false;volume = source.kind().volume;
            x = source.position().x;y = source.position().y;z = source.position().z;
        }

        private boolean valid(Vec3 listener) {
            return !isStopped() && source.owner().getLevel() == level
                && source.position().distanceToSqr(listener) <= source.range() * source.range() && source.active();
        }

        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (mc.level != level || mc.player == null || muted(mc) || !valid(mc.gameRenderer.getMainCamera().getPosition())) finish();
        }

        private void finish() { stop(); }
    }
}
