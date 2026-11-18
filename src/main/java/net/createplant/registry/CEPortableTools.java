package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.item.SprayToolItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEPortableTools {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,CreatePlant.MOD_ID);
    public static final Supplier<DataComponentType<SimpleFluidContent>> CONTENTS=COMPONENTS.register("spray_tool_contents",()->DataComponentType.<SimpleFluidContent>builder()
        .persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());
    public static final DeferredItem<SprayToolItem> FIRE_EXTINGUISHER=tool("fire_extinguisher",SprayToolItem.Kind.EXTINGUISHER);
    public static final DeferredItem<SprayToolItem> FLAMETHROWER=tool("flamethrower",SprayToolItem.Kind.FLAME);
    public static final DeferredItem<SprayToolItem> CHEMICAL_SPRAYER=tool("chemical_sprayer",SprayToolItem.Kind.LIQUID);

    private CEPortableTools() { }
    private static DeferredItem<SprayToolItem> tool(String id,SprayToolItem.Kind kind) {
        return ITEMS.register(id,()->new SprayToolItem(kind,new Item.Properties().stacksTo(1)));
    }
    public static void register(IEventBus bus) { ITEMS.register(bus);COMPONENTS.register(bus);bus.addListener(CEPortableTools::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new net.createplant.item.SprayToolFluidHandler(stack),
            FIRE_EXTINGUISHER.get(),FLAMETHROWER.get(),CHEMICAL_SPRAYER.get());
    }
}
