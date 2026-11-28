package net.createplant.atomics.nuclear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.createplant.atomics.block.entity.ControlRodLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The loaded 0-to-100 control-rod transmitters of each level, grouped by their two-item Create frequency. */
public final class ControlRodLinks {
    private static final Map<Level, Map<Couple<Frequency>, Set<BlockPos>>> LINKS = new WeakHashMap<>();

    private ControlRodLinks() { }

    public static void add(Level level, Couple<Frequency> key, BlockPos pos) {
        LINKS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(key, k -> new HashSet<>()).add(pos);
    }

    public static void remove(Level level, Couple<Frequency> key, BlockPos pos) {
        var keys = LINKS.get(level);
        if (keys == null || !keys.containsKey(key)) return;
        keys.get(key).remove(pos);
        if (keys.get(key).isEmpty()) keys.remove(key);
    }

    /** The highest setting of the links on the frequency, or -1 if none is sending. */
    public static int setting(Level level, Couple<Frequency> key) {
        var keys = LINKS.get(level);
        if (keys == null || !keys.containsKey(key)) return -1;
        int best = -1;
        for (BlockPos pos : keys.get(key))
            if (level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof ControlRodLinkBlockEntity link
                && link.sendingControl()) best = Math.max(best, link.setting());
        return best;
    }
}
