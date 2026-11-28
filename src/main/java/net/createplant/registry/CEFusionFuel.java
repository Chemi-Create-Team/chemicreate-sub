package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEFusionFuel {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    public static final DeferredItem<Item> LITHIUM_TARGET = ITEMS.registerSimpleItem("lithium_target_rod", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> IRRADIATED_TARGET = ITEMS.registerSimpleItem("irradiated_lithium_target_rod", new Item.Properties().stacksTo(1));

    private CEFusionFuel() { }
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
