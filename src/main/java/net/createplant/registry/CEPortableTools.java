package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.item.PipeConfiguratorItem;
import net.createplant.item.PortableToolFluidHandler;
import net.createplant.item.SprayToolItem;
import net.createplant.item.SteamGunAction;
import net.createplant.item.SteamGunItem;
import net.createplant.item.SteamHammerAction;
import net.createplant.item.SteamHammerItem;
import net.createplant.safety.DazedEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEPortableTools {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,CreatePlant.MOD_ID);
    public static final Supplier<DataComponentType<SimpleFluidContent>> CONTENTS=COMPONENTS.register("spray_tool_contents",()->DataComponentType.<SimpleFluidContent>builder()
        .persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());
    public static final DeferredItem<SprayToolItem> FIRE_EXTINGUISHER=tool("fire_extinguisher",SprayToolItem.Kind.EXTINGUISHER);
    public static final DeferredItem<SprayToolItem> FLAMETHROWER=tool("flamethrower",SprayToolItem.Kind.FLAME);
    public static final DeferredItem<SprayToolItem> CHEMICAL_SPRAYER=tool("chemical_sprayer",SprayToolItem.Kind.LIQUID);
    public static final DeferredItem<SteamGunItem> STEAM_GUN=ITEMS.register("steam_gun",()->new SteamGunItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SteamHammerItem> STEAM_HAMMER=ITEMS.register("steam_hammer",()->new SteamHammerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<PipeConfiguratorItem> PIPE_CONFIGURATOR=ITEMS.register("pipe_configurator",()->new PipeConfiguratorItem(new Item.Properties().stacksTo(1)));
    public static final Holder<MobEffect> DAZED=EFFECTS.register("dazed",DazedEffect::new);

    private CEPortableTools() { }
    private static DeferredItem<SprayToolItem> tool(String id,SprayToolItem.Kind kind) {
        return ITEMS.register(id,()->new SprayToolItem(kind,new Item.Properties().stacksTo(1)));
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus);EFFECTS.register(bus);COMPONENTS.register(bus);
        bus.addListener(CEPortableTools::capabilities);bus.addListener(SteamGunAction::register);bus.addListener(SteamHammerAction::register);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new PortableToolFluidHandler(stack),
            FIRE_EXTINGUISHER.get(),FLAMETHROWER.get(),CHEMICAL_SPRAYER.get(),STEAM_GUN.get(),STEAM_HAMMER.get());
    }
}
