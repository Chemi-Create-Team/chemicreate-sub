package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.item.GasBucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Each process gas has a container, including intermediate streams without world placement. */
public final class CEGasBuckets {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private CEGasBuckets() { }
    static Supplier<? extends Item> define(String name, Supplier<? extends Fluid> fluid) {
        return switch (name) {
            case "hydrogen_gas" -> () -> CEItems.HYDROGEN_GAS_BUCKET.get();
            case "oxygen_gas" -> () -> CEItems.OXYGEN_GAS_BUCKET.get();
            case "nitrogen_gas" -> () -> CEItems.NITROGEN_GAS_BUCKET.get();
            case "argon_gas" -> () -> CEItems.ARGON_GAS_BUCKET.get();
            case "converter_gas" -> () -> CEItems.CONVERTER_GAS_BUCKET.get();
            case "air_gas" -> () -> CEItems.AIR_GAS_BUCKET.get();
            case "ammonia_gas" -> () -> CEItems.AMMONIA_GAS_BUCKET.get();
            case "ethylene" -> () -> CEItems.ETHYLENE_BUCKET.get();
            case "propylene" -> () -> CEItems.PROPYLENE_BUCKET.get();
            case "nitric_oxide" -> () -> CEItems.NITRIC_OXIDE_BUCKET.get();
            case "nitrogen_dioxide" -> () -> CEItems.NITROGEN_DIOXIDE_BUCKET.get();
            case "deuterium_gas" -> () -> CEFusion.DEUTERIUM_BUCKET.get();
            case "tritium_gas" -> () -> CEFusion.TRITIUM_BUCKET.get();
            case "helium_gas" -> () -> CEFusion.HELIUM_BUCKET.get();
            case "helium_recovery_feed" -> () -> CEFusionFuel.HELIUM_FEED_BUCKET.get();
            default -> ITEMS.register(name + "_bucket", () -> new GasBucketItem(fluid.get(), CEWorldFluids.bucketProperties()));
        };
    }
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
            event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                (stack, context) -> new net.createplant.item.GasBucketWrapper(stack),
                ITEMS.getEntries().stream().map(Supplier::get).toArray(Item[]::new)));
    }
}
