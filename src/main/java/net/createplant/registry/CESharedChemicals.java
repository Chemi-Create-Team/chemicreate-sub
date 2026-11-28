package net.createplant.registry;

import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Chemistry shared by the base plant and the addons: sulfur, fluorine and phosphorus streams with the minerals beside them. */
public final class CESharedChemicals {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);

    public static final DeferredItem<Item> FLUORITE = ITEMS.registerSimpleItem("fluorite");
    public static final DeferredItem<Item> BORAX = ITEMS.registerSimpleItem("borax");
    public static final DeferredItem<Item> GYPSUM = ITEMS.registerSimpleItem("gypsum");

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFUR_DIOXIDE = stream("sulfur_dioxide", 0x99D8D2B0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFUR_TRIOXIDE = stream("sulfur_trioxide", 0xAAE2DCC6);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFURIC_ACID = stream("sulfuric_acid", 0xDDD9D0A0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SULFUROUS_ACID = stream("sulfurous_acid", 0xCCDCE0C4);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN_FLUORIDE = stream("hydrogen_fluoride", 0x99DCE6D8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FLUORINE = stream("fluorine", 0xAAD8D88A);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PHOSPHORIC_ACID = liquid("phosphoric_acid", 0xDDD1CFB8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BUTYRALDEHYDE = liquid("butyraldehyde", 0xCCD4CCAE);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BUTANOL = liquid("butanol", 0xCCD3D4C2);

    private CESharedChemicals() { }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int color) {
        var hazard = FluidHazards.named(name);
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).supportsBoating(false)
            .temperature((int) Math.round(hazard.kelvin())).density(hazard.gas() ? -1 : 1000), UnaryOperator.identity()).source;
    }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> liquid(String name, int color) {
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + "." + name)
            .temperature(293).density(1100).supportsBoating(false), UnaryOperator.identity()).source;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
