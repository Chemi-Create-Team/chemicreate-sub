package net.createplant.logistics.block.entity;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.createplant.CreatePlant;
import net.createplant.logistics.block.QuarryBeltLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

/** Ticking tickets for one quarry and its configured excavation and output. */
final class QuarryChunkLoader {
    private static final TicketController CONTROLLER = new TicketController(CreatePlant.location("quarry"), QuarryChunkLoader::restoreSavedTickets);

    private final BlockPos owner;
    private final LongOpenHashSet held = new LongOpenHashSet();
    private QuarryBeltLayout layout;
    private int width, next;
    private List<ChunkPos> footprint = List.of();
    private boolean ready;

    QuarryChunkLoader(BlockPos owner) { this.owner = owner.immutable(); }

    static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }

    private static void restoreSavedTickets(ServerLevel level, TicketHelper helper) {
        helper.getBlockTickets().forEach((owner, tickets) -> {
            if (tickets.ticking().contains(ChunkPos.asLong(owner)) && level.getBlockEntity(owner) instanceof QuarryDriveBlockEntity drive)
                drive.restoreChunkTickets(helper, tickets);
            else helper.removeAllTickets(owner);
        });
    }

    void restore(TicketHelper helper, TicketSet tickets, QuarryBeltLayout layout, int width) {
        configure(layout, width);
        for (long chunk : tickets.ticking()) {
            if (footprint.contains(new ChunkPos(chunk))) held.add(chunk);
            else helper.removeTicket(owner, chunk, true);
        }
        for (long chunk : tickets.nonTicking()) helper.removeTicket(owner, chunk, false);
    }

    private void configure(QuarryBeltLayout layout, int width) {
        this.layout = layout;
        this.width = width;
        ready = false;
        next = 0;
        var home = new ChunkPos(owner);
        var start = new ChunkPos(layout.start().relative(layout.along(), -2).relative(layout.across(), -2));
        var end = new ChunkPos(layout.start().relative(layout.along(), layout.length()).relative(layout.across(), width + 1));
        var chunks = new ArrayList<ChunkPos>(36);
        chunks.add(home);
        ChunkPos.rangeClosed(start, end).filter(chunk -> !chunk.equals(home)).forEach(chunks::add);
        footprint = chunks;
    }

    boolean load(ServerLevel level, QuarryBeltLayout layout, int width) {
        if (!layout.equals(this.layout) || width != this.width) {
            release(level);
            configure(layout, width);
        }
        var source = level.getChunkSource();
        while (next < footprint.size()) {
            ChunkPos chunk = footprint.get(next++);
            if (!held.add(chunk.toLong())) continue;
            boolean alreadyLoaded = source.getChunkNow(chunk.x, chunk.z) != null;
            CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, true, true);
            // NeoForge can load a missing chunk synchronously when the ticket is first added.
            if (!alreadyLoaded && next < footprint.size()) return false;
        }
        if (!ready) {
            for (ChunkPos chunk : footprint) if (!source.isPositionTicking(chunk.toLong())) return false;
            ready = true;
        }
        return true;
    }

    void release(ServerLevel level) {
        if (held.isEmpty()) return;
        for (long chunk : held) CONTROLLER.forceChunk(level, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
        held.clear();
        next = 0;
        ready = false;
    }
}
