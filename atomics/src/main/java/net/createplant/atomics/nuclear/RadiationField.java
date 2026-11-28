package net.createplant.atomics.nuclear;

import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-owned chunk index. No block-volume search is performed when a player is sampled. */
public final class RadiationField {
    private static final Map<ServerLevel, RadiationField> FIELDS = new WeakHashMap<>();
    private final Map<Long, Map<BlockPos, Source>> sources = new HashMap<>();
    private final Map<BlockPos, BlockEntity> containers = new HashMap<>();
    private final Map<Long, Map<Integer, ItemEntity>> items = new HashMap<>();
    private final Map<Integer, Long> itemChunks = new HashMap<>();
    private final LinkedHashSet<BlockPos> dirty = new LinkedHashSet<>();
    private final Map<BlockPos, AABB> water = new HashMap<>();
    public final RadiationShielding shielding = new RadiationShielding();
    public long evaluatedSources;

    public record Source(
            Vec3 position, RadiationPhysics.Spectrum spectrum, AABB volume, long expires) {}

    private record Candidate(Source source, double bound) {}

    private RadiationField() {}

    public boolean inWater(Vec3 position) {
        return water.values().stream().anyMatch(box -> box.contains(position));
    }

    public static RadiationField get(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, l -> new RadiationField());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RadiationField::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(RadiationField::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(RadiationField::joined);
        NeoForge.EVENT_BUS.addListener(RadiationField::left);
        NeoForge.EVENT_BUS.addListener(RadiationField::entityTick);
        NeoForge.EVENT_BUS.addListener(RadiationField::tick);
        NeoForge.EVENT_BUS.addListener(RadiationField::unloaded);
        NeoForge.EVENT_BUS.addListener(RadiationField::tagsUpdated);
        NeoForge.EVENT_BUS.addListener(Radiation::clonePlayer);
    }

    public static void dirty(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
            var field = get(server);
            field.dirty.add(pos.immutable());
        }
    }

    public static void blockChanging(
            Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState next) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        var old = level.getBlockState(pos);
        if (old == next) return;
        if (old.getBlock() != next.getBlock()) {
            var be = level.getBlockEntity(pos);
            if (be != null) RadiationContents.spill(server, be);
            var profile = RadiationSources.profile(old);
            if (profile != null && old.getFluidState().isEmpty())
                RadiationContamination.deposit(
                        server, pos, profile.fresh(RadiationSources.now(level)).scale(.01));
            var incoming = RadiationSources.profile(next);
            if (incoming != null
                    && !next.getFluidState().isEmpty()
                    && next.getFluidState().isSource())
                RadiationContamination.deposit(
                        server, pos, incoming.fresh(RadiationSources.now(level)).scale(.01));
        }
        get(server).shielding.changed(pos);
        if (old.hasBlockEntity()
                || next.hasBlockEntity()
                || RadiationSources.profile(old) != null
                || RadiationSources.profile(next) != null) get(server).dirty.add(pos.immutable());
    }

    private static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk))
            return;
        level.getServer()
                .execute(
                        () -> {
                            var field = get(level);
                            field.dirty.addAll(chunk.getBlockEntities().keySet());
                            for (var p : RadiationWorldData.get(level).blocks)
                                if (new ChunkPos(p).equals(chunk.getPos())) field.dirty.add(p);
                        });
    }

    private static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var f = get(level);
        long chunk = event.getChunk().getPos().toLong();
        f.sources.remove(chunk);
        f.containers.keySet().removeIf(p -> ChunkPos.asLong(p) == chunk);
        f.water.keySet().removeIf(p -> ChunkPos.asLong(p) == chunk);
        f.dirty.removeIf(p -> ChunkPos.asLong(p) == chunk);
        f.shielding.clear();
    }

    private static void tagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null)
            server.execute(() -> FIELDS.values().forEach(field -> field.shielding.clear()));
    }

    private static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) FIELDS.remove(level);
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ItemEntity item) get(level).index(item);
    }

    private static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ItemEntity item) get(level).remove(item.getId());
    }

    private static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity item
                && item.level() instanceof ServerLevel level) get(level).index(item);
    }

    private void remove(int id) {
        var old = itemChunks.remove(id);
        if (old != null && items.containsKey(old)) {
            items.get(old).remove(id);
            if (items.get(old).isEmpty()) items.remove(old);
        }
    }

    private void index(ItemEntity item) {
        if (RadiationSources.profile(item.getItem()) == null
                && !item.getItem().has(net.createplant.atomics.registry.CERadiation.SURFACE.get())
                && !item.getItem()
                        .has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                && !item.getItem().has(net.minecraft.core.component.DataComponents.CONTAINER)
                && !(item.getItem().getItem() instanceof net.minecraft.world.item.BucketItem)) {
            remove(item.getId());
            return;
        }
        long chunk = item.chunkPosition().toLong();
        var old = itemChunks.get(item.getId());
        if (old != null && old == chunk) return;
        remove(item.getId());
        itemChunks.put(item.getId(), chunk);
        items.computeIfAbsent(chunk, k -> new HashMap<>()).put(item.getId(), item);
        RadiationSources.stamp(item.getItem(), item.level());
    }

    private void put(BlockPos key, Source source) {
        sources.computeIfAbsent(ChunkPos.asLong(key), k -> new HashMap<>())
                .put(key.immutable(), source);
    }

    private void remove(BlockPos key) {
        var chunk = sources.get(ChunkPos.asLong(key));
        if (chunk != null) {
            chunk.remove(key);
            if (chunk.isEmpty()) sources.remove(ChunkPos.asLong(key));
        }
    }

    public void refresh(ServerLevel level) {
        int count = 0;
        var it = dirty.iterator();
        var work = new java.util.ArrayList<BlockPos>();
        while (it.hasNext() && count++ < 2048) {
            work.add(it.next());
            it.remove();
        }
        var data = RadiationWorldData.get(level);
        for (var chunk : items.values())
            for (var item : chunk.values())
                if (item.isAlive())
                    RadiationSources.shed(item.getItem(), level, item.blockPosition());
        for (var p : work) {
            if (!level.hasChunkAt(p)) continue;
            var be = level.getBlockEntity(p);
            if (be instanceof net.createplant.atomics.block.entity.FuelChannelBlockEntity) continue;
            var owner = be == null ? null : RadiationContents.owner(be);
            if (owner != null && !owner.isRemoved()) containers.put(owner.getBlockPos(), owner);
            if (owner != be) {
                containers.remove(p);
                remove(p);
            }
            if (be == null) {
                containers.remove(p);
                water.remove(p);
            }
            var profile = RadiationSources.profile(level.getBlockState(p));
            if (profile != null) {
                if (data.blocks.add(p)) data.setDirty();
                if (!data.blockActivity.containsKey(p)) {
                    data.blockActivity.put(p, profile.fresh(RadiationSources.now(level)));
                    data.setDirty();
                }
                put(
                        p,
                        new Source(
                                Vec3.atCenterOf(p),
                                profile.spectrum(
                                        data.blockActivity.get(p), RadiationSources.now(level)),
                                null,
                                Long.MAX_VALUE));
            } else {
                if (data.blocks.remove(p)) {
                    data.blockActivity.remove(p);
                    data.setDirty();
                }
                if (be == null) remove(p);
            }
        }
        var iterator = containers.entrySet().iterator();
        while (iterator.hasNext()) {
            var e = iterator.next();
            var be = e.getValue();
            if (be.isRemoved() || !level.hasChunkAt(e.getKey())) {
                iterator.remove();
                remove(e.getKey());
                water.remove(e.getKey());
                continue;
            }
            var s = RadiationContents.sample(level, be);
            if (be instanceof FuelPoolBlockEntity pool) {
                var box = pool.radiationWaterBounds();
                if (box != null) water.put(e.getKey(), box);
                else water.remove(e.getKey());
            }
            if (s.external() > 0 || s.activity() > 0)
                put(
                        e.getKey(),
                        new Source(
                                Vec3.atCenterOf(e.getKey()).add(0, .1, 0),
                                s,
                                null,
                                Long.MAX_VALUE));
            else {
                remove(e.getKey());
                iterator.remove();
            }
        }
        for (var chunk : sources.values())
            chunk.values().removeIf(s -> s.expires < level.getGameTime());
    }

    public static Source core(
            Level level, BlockPos key, BoundingBox box, double watts, boolean scram) {
        if (!(level instanceof ServerLevel server)) return null;
        var data = RadiationWorldData.get(server);
        long now = RadiationSources.now(level);
        var old = data.cores.getOrDefault(key, new RadiationWorldData.CoreHistory(0, now));
        if (watts > 1) {
            old = new RadiationWorldData.CoreHistory(Math.max(watts, old.watts()), now);
            data.cores.put(key, old);
            data.setDirty();
        }
        double activity =
                old.watts() * ReactorPhysics.DECAY_SHARE * RadiationPhysics.CORE_GAMMA_PER_WATT;
        double elapsed = Math.max(0, now - old.tick()) / 20d * RadiationPhysics.TIME_MULTIPLIER;
        activity *=
                (.85 * RadiationPhysics.decay(elapsed, RadiationPhysics.SHORT_HALF_LIFE)
                                + .149
                                        * RadiationPhysics.decay(
                                                elapsed, RadiationPhysics.CESIUM_HALF_LIFE))
                        / .999;
        var spectrum =
                new RadiationPhysics.Spectrum(
                        watts * RadiationPhysics.CORE_GAMMA_PER_WATT + activity,
                        0,
                        scram ? 0 : watts * RadiationPhysics.CORE_NEUTRON_PER_WATT,
                        0);
        var source =
                new Source(
                        Vec3.atCenterOf(box.getCenter()),
                        spectrum,
                        AABB.of(box),
                        level.getGameTime() + 40);
        get(server).put(key, source);
        return source;
    }

    private static Vec3 origin(Source source, Vec3 target) {
        if (source.volume == null) return source.position;
        if (source.volume.contains(target)) return target;
        var delta = target.subtract(source.position);
        double t = 1;
        if (delta.x > 0) t = Math.min(t, (source.volume.maxX - source.position.x) / delta.x);
        if (delta.x < 0) t = Math.min(t, (source.volume.minX - source.position.x) / delta.x);
        if (delta.y > 0) t = Math.min(t, (source.volume.maxY - source.position.y) / delta.y);
        if (delta.y < 0) t = Math.min(t, (source.volume.minY - source.position.y) / delta.y);
        if (delta.z > 0) t = Math.min(t, (source.volume.maxZ - source.position.z) / delta.z);
        if (delta.z < 0) t = Math.min(t, (source.volume.minZ - source.position.z) / delta.z);
        return source.position.add(delta.scale(t + 1e-7));
    }

    public RadiationPhysics.Spectrum measure(ServerLevel level, Vec3 target, Entity excluded) {
        var candidates = new java.util.ArrayList<Candidate>();
        int cx = net.minecraft.util.Mth.floor(target.x) >> 4,
                cz = net.minecraft.util.Mth.floor(target.z) >> 4;
        long now = RadiationSources.now(level);
        var data = RadiationWorldData.get(level);
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                long key = ChunkPos.asLong(x, z);
                var chunk = sources.get(key);
                if (chunk != null)
                    for (var e : chunk.entrySet()) {
                        var source = e.getValue();
                        var activity = data.blockActivity.get(e.getKey());
                        if (activity != null) {
                            var profile = RadiationSources.profile(level.getBlockState(e.getKey()));
                            if (profile != null)
                                source =
                                        new Source(
                                                source.position,
                                                profile.spectrum(activity, now),
                                                source.volume,
                                                source.expires);
                        }
                        add(candidates, source, target);
                    }
                var drops = items.get(key);
                if (drops != null)
                    for (var item : drops.values())
                        if (item.isAlive() && item != excluded)
                            add(
                                    candidates,
                                    new Source(
                                            item.position(),
                                            RadiationSources.item(item.getItem(), now),
                                            null,
                                            Long.MAX_VALUE),
                                    target);
                var soil = data.soil.get(key);
                if (soil != null && level.hasChunk(x, z))
                    for (var e : soil.entrySet())
                        add(
                                candidates,
                                new Source(
                                        Vec3.atLowerCornerOf(e.getKey()).add(2, .05, 2),
                                        RadiationSources.contamination(e.getValue().at(now)),
                                        null,
                                        Long.MAX_VALUE),
                                target);
            }
        for (var player : level.players())
            if (player != excluded) {
                var s = RadiationPhysics.Spectrum.ZERO;
                for (var stack : player.getInventory().items)
                    s = s.plus(RadiationSources.item(stack, now));
                for (var stack : player.getArmorSlots())
                    s = s.plus(RadiationSources.item(stack, now));
                for (var stack : player.getInventory().offhand)
                    s = s.plus(RadiationSources.item(stack, now));
                var skin =
                        RadiationWorldData.read(
                                        player.getData(
                                                        net.createplant.atomics.registry.CERadiation
                                                                .EXPOSURE)
                                                .getCompound("Skin"))
                                .at(now);
                s = s.plus(RadiationSources.contamination(skin));
                add(
                        candidates,
                        new Source(player.position().add(0, 1, 0), s, null, Long.MAX_VALUE),
                        target);
            }
        candidates.sort(java.util.Comparator.comparingDouble(Candidate::bound).reversed());
        var total = RadiationPhysics.Spectrum.ZERO;
        int evaluated = 0;
        var volumes = new java.util.HashSet<>(water.values());
        for (var c : candidates) {
            if (evaluated++ >= RadiationPhysics.MAX_SOURCES || c.bound < RadiationPhysics.CUTOFF)
                break;
            evaluatedSources++;
            total = total.plus(contribution(level, c.source, target, volumes));
        }
        return total;
    }

    public RadiationPhysics.Spectrum contribution(ServerLevel level, Source source, Vec3 target) {
        return contribution(level, source, target, new java.util.HashSet<>(water.values()));
    }

    private RadiationPhysics.Spectrum contribution(
            ServerLevel level, Source source, Vec3 target, java.util.Set<AABB> volumes) {
        var start = origin(source, target);
        var shield = shielding.trace(level, start, target);
        double depth = 0;
        for (var volume : volumes) depth += RadiationShielding.lengthInside(volume, start, target);
        double d = source.position.distanceTo(target);
        return new RadiationPhysics.Spectrum(
                RadiationPhysics.inverseSquare(source.spectrum.gamma(), d)
                        * shield.gamma()
                        * RadiationPhysics.transmission(depth, .09),
                RadiationPhysics.inverseSquare(source.spectrum.beta(), d)
                        * shield.beta()
                        * RadiationPhysics.betaAir(d)
                        * RadiationPhysics.transmission(depth, .003),
                RadiationPhysics.inverseSquare(source.spectrum.neutron(), d)
                        * shield.neutron()
                        * RadiationPhysics.transmission(depth, .1),
                0);
    }

    private static void add(java.util.List<Candidate> list, Source source, Vec3 target) {
        double d = source.position.distanceTo(target);
        if (d <= RadiationPhysics.REACH && source.spectrum.external() > 0)
            list.add(
                    new Candidate(
                            source, RadiationPhysics.inverseSquare(source.spectrum.external(), d)));
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.getGameTime() % 20 != 0
                || level.players().isEmpty()) return;
        var field = get(level);
        field.refresh(level);
        for (var player : level.players())
            Radiation.sample(player, field.measure(level, player.getEyePosition(), player));
    }
}
