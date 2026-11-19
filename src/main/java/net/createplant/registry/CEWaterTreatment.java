package net.createplant.registry;

import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Process materials for treating existing vessel wash water in ordinary stirred vessels. */
public final class CEWaterTreatment {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);

    public static final DeferredItem<Item> FLOCCULANT = ITEMS.registerSimpleItem("water_treatment_flocculant");
    public static final DeferredItem<Item> FILTER_MEDIUM = ITEMS.registerSimpleItem("water_filter_medium");
    public static final DeferredItem<Item> SLUDGE = ITEMS.registerSimpleItem("treatment_sludge");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NEUTRALIZED_WASTEWATER = stream("neutralized_wastewater", 0xFF929981);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CLARIFIED_WATER = stream("clarified_water", 0xBB9FB9B6);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFUR_SCRUBBER_LIQUOR = stream("sulfur_scrubber_liquor", 0xFFAA9761);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> RESIN_PROCESS_VAPOR = stream("resin_process_vapor", 0xFFB4CED1);

    private CEWaterTreatment() { }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int colour) {
        return new PlantFluid(TYPES, FLUIDS, name, colour, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).supportsBoating(false)
            .temperature(293).density(1000).viscosity(1000), UnaryOperator.identity()).source;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TYPES.register(bus);
        FLUIDS.register(bus);
        bus.addListener(CEWaterTreatment::thermalProperties);
    }

    private static void thermalProperties(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var properties = new FluidThermal.Properties(293.15, 1, 4180);
            for (String name : new String[] {"neutralized_wastewater", "clarified_water", "sulfur_scrubber_liquor"}) {
                FluidThermal.register(BuiltInRegistries.FLUID.get(CreatePlant.location(name)), properties);
                FluidThermal.register(BuiltInRegistries.FLUID.get(CreatePlant.location("flowing_" + name)), properties);
            }
        });
    }
}
