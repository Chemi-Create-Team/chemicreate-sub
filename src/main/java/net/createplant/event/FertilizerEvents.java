package net.createplant.event;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.agriculture.FertilizedCrops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class FertilizerEvents {
    private static final ThreadLocal<BlockDropsEvent> CREATE_DROPS = new ThreadLocal<>();

    private FertilizerEvents() { }

    @SubscribeEvent
    public static void grow(CropGrowEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) FertilizedCrops.grew(level, event.getPos(), event.getOriginalState(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof CropBlock) || CREATE_DROPS.get() == event || event.getDrops().isEmpty()) return;
        var bonus = FertilizedCrops.harvest(event.getLevel(), event.getPos(), event.getState(),
            event.getDrops().stream().map(ItemEntity::getItem).toList());
        if (bonus.isEmpty()) return;
        var normal = event.getDrops().getFirst();
        var added = new ItemEntity(event.getLevel(), normal.getX(), normal.getY(), normal.getZ(), bonus);
        added.setDefaultPickUpDelay();
        event.getDrops().add(added);
    }

    public static Event postCreateDrops(BlockDropsEvent event, Supplier<Event> post) {
        var previous = CREATE_DROPS.get();
        CREATE_DROPS.set(event);
        try {
            return post.get();
        } finally {
            if (previous == null) CREATE_DROPS.remove();else CREATE_DROPS.set(previous);
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) FertilizedCrops.expire(level);
    }
}
