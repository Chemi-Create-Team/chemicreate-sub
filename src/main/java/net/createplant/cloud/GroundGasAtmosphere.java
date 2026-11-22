package net.createplant.cloud;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class GroundGasAtmosphere {
    private static final int WORK_PER_TICK = 128, WORK_PER_FIELD = 16;
    private static final Map<ServerLevel, Atmosphere> LEVELS = new WeakHashMap<>();

    private static final class Atmosphere {
        final Map<UUID, GroundGasField> fields = new HashMap<>();
        final ArrayDeque<UUID> pending = new ArrayDeque<>();
        final Map<Long, Set<UUID>> sections = new HashMap<>();

        GroundGasField add(UUID id, BlockPos origin, long tick) {
            pending.add(id);
            return new GroundGasField(origin, tick, key -> sections.computeIfAbsent(key, ignored -> new HashSet<>()).add(id),
                key -> {
                    var entries = sections.get(key);
                    entries.remove(id);
                    if (entries.isEmpty()) sections.remove(key);
                });
        }
    }

    private GroundGasAtmosphere() { }

    static void release(GasCloud source) {
        if (!(source.level() instanceof ServerLevel level) || source.volume().size() == 0 || source.charge() <= 0) return;
        var style = source.style();
        if (style == null || style.ceiling()) return;
        var atmosphere = LEVELS.computeIfAbsent(level, ignored -> new Atmosphere());
        var field = atmosphere.fields.computeIfAbsent(source.getUUID(),
            id -> atmosphere.add(id, source.volume().origin(), level.getGameTime()));
        field.observeRelease(level, source);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var atmosphere = LEVELS.get(level);
        if (atmosphere == null) return;
        int budget = WORK_PER_TICK;
        for (int fields = atmosphere.pending.size(); fields > 0 && budget > 0; fields--) {
            var id = atmosphere.pending.remove();
            var field = atmosphere.fields.get(id);
            int work = field.spread(level, field.time(level.getGameTime()), Math.min(budget, WORK_PER_FIELD));
            budget -= Math.max(1, work);
            if (field.isEmpty()) atmosphere.fields.remove(id);
            else atmosphere.pending.add(id);
        }
        if (atmosphere.fields.isEmpty()) LEVELS.remove(level);
    }

    public static float concentration(ServerLevel level, Vec3 eye) {
        var atmosphere = LEVELS.get(level);
        if (atmosphere == null) return 0;
        var pos = BlockPos.containing(eye);
        var sources = atmosphere.sections.get(SectionPos.asLong(pos));
        if (sources == null) return 0;
        float remainingAir = 1;
        for (var source : sources)
            remainingAir *= 1 - atmosphere.fields.get(source).concentration(pos, eye.y - pos.getY(), level.getGameTime());
        return Mth.clamp(1 - remainingAir, 0, 1);
    }
}
