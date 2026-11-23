package net.createplant.registry;

import java.util.function.UnaryOperator;
import com.mojang.serialization.Codec;
import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The chlor-alkali line: salt from rock salt ore or sun-dried sea water, brine electrolysed into chlorine, hydrogen and caustic soda, and what
 * they go on to make: alumina by the Bayer process, hydrochloric acid, and PVC from ethylene and chlorine.
 */
public final class CEChlorAlkali {
    public static final int DIAPHRAGM_LIFE = 10000;
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePlant.MOD_ID);
    /** Brine in mB that has passed an asbestos diaphragm in an electrolysis basin; it wears out at DIAPHRAGM_LIFE. Kept on the item, so moving it keeps its wear. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DIAPHRAGM_WEAR = COMPONENTS.register("diaphragm_wear",
        () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, DIAPHRAGM_LIFE)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredItem<Item> SALT = ITEMS.register("salt", () -> new net.createplant.item.DeIcingSaltItem(new Item.Properties()));
    public static final DeferredItem<Item> SOAP = ITEMS.register("soap", () -> new net.createplant.item.SoapItem(new Item.Properties()));
    public static final DeferredItem<Item> PVC = ITEMS.registerSimpleItem("pvc_resin");
    public static final DeferredItem<BlockItem> HALITE_ORE = ore("halite_ore", Blocks.STONE);
    public static final DeferredItem<BlockItem> DEEPSLATE_HALITE_ORE = ore("deepslate_halite_ore", Blocks.DEEPSLATE);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BRINE = stream("brine", 0xCCD8E8EC);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CHLORINE = stream("chlorine", 0xCC9EC41C);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_CHLORINE = stream("liquid_chlorine", 0xDDD8BE1C);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CAUSTIC_SODA = stream("caustic_soda_solution", 0xDDE8EEF0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SODIUM_ALUMINATE = stream("sodium_aluminate_solution", 0xDDD8C8A8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN_CHLORIDE = stream("hydrogen_chloride", 0x88E0E8E0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROCHLORIC_ACID = stream("hydrochloric_acid", 0xCCE6EED0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHYLENE_DICHLORIDE = stream("ethylene_dichloride", 0xCCE8E4C0);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> VINYL_CHLORIDE = stream("vinyl_chloride", 0x88ECECE4);
    /** Bleach: chlorine taken up by caustic soda. */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SODIUM_HYPOCHLORITE = stream("sodium_hypochlorite_solution", 0xCCE4EEC8);

    private CEChlorAlkali() { }

    private static DeferredItem<BlockItem> ore(String name, net.minecraft.world.level.block.Block like) {
        var block = BLOCKS.registerBlock(name, properties -> new DropExperienceBlock(UniformInt.of(0, 2), properties), BlockBehaviour.Properties.ofFullCopy(like));
        return ITEMS.registerSimpleBlockItem(block);
    }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int color) {
        var hazard = FluidHazards.named(name);
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).supportsBoating(false)
            .temperature((int) Math.round(hazard.kelvin())).density(hazard.gas() ? -1 : 1000), UnaryOperator.identity()).source;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        FLUIDS.register(bus);
        COMPONENTS.register(bus);
    }
}
