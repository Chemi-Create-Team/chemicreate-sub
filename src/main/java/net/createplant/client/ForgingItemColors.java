package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(
        modid = CreatePlant.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ForgingItemColors {
    private ForgingItemColors() {}

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFFFF7A32, CEItems.HEATED_UNFINISHED_STEEL.get());
        event.register((stack, tint) -> 0xFF9A7160, CEItems.PIG_IRON.get());
        event.register((stack, tint) -> 0xFF766B60, CEItems.STEEL_SLAG.get());
    }
}
