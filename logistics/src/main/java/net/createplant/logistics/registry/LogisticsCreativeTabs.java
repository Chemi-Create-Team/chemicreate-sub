package net.createplant.logistics.registry;

import net.createplant.logistics.CreateRnrLogistics;
import net.createplant.registry.CECreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Gives the logistics addon its own creative tab and places its items within the core groups. */
public final class LogisticsCreativeTabs {
    private LogisticsCreativeTabs() { }

    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateRnrLogistics.MOD_ID);

    public static void register(IEventBus modEventBus) {
        CECreativeModeTabs.addonTab(TABS, CreateRnrLogistics.MOD_ID, "itemGroup.%s.base".formatted(CreateRnrLogistics.MOD_ID),
            () -> new ItemStack(BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("industrial_arm"))));
        TABS.register(modEventBus);
        CECreativeModeTabs.addGroupAfter("low_pressure_turbine_end", "anfo", "blasting_charge");
        CECreativeModeTabs.insertBefore("facility_constructor", "quarry_drive", "industrial_dispatch_board");
        CECreativeModeTabs.insertAfter("vault_hatch", "industrial_arm", "welding_torch");
    }
}
