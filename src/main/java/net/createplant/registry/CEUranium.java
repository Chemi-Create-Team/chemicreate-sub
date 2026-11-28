package net.createplant.registry;

import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Uranium fuel from ore to sintered pellet.
 * Liquid streams also use the shared world-liquid and bucket registrations.
 */
public final class CEUranium {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);

    public static final DeferredBlock<DropExperienceBlock> URANIUM_ORE = BLOCKS.registerBlock("uranium_ore",
        properties -> new DropExperienceBlock(UniformInt.of(1, 3), properties), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_URANIUM_ORE = BLOCKS.registerBlock("deepslate_uranium_ore",
        properties -> new DropExperienceBlock(UniformInt.of(1, 3), properties), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE));
    public static final DeferredItem<BlockItem> URANIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(URANIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEPSLATE_URANIUM_ORE);
    public static final DeferredItem<Item> URANINITE = ITEMS.registerSimpleItem("uraninite");
    public static final DeferredItem<Item> YELLOWCAKE = ITEMS.registerSimpleItem("yellowcake");
    public static final DeferredItem<Item> URANIUM_TRIOXIDE = ITEMS.registerSimpleItem("uranium_trioxide");
    public static final DeferredItem<Item> URANIUM_DIOXIDE = ITEMS.registerSimpleItem("uranium_dioxide");
    public static final DeferredItem<Item> URANIUM_TETRAFLUORIDE = ITEMS.registerSimpleItem("uranium_tetrafluoride");
    public static final DeferredItem<Item> ENRICHED_URANIUM_DIOXIDE = ITEMS.registerSimpleItem("enriched_uranium_dioxide");
    public static final DeferredItem<Item> GREEN_URANIUM_PELLET = ITEMS.registerSimpleItem("green_uranium_pellet");
    public static final DeferredItem<Item> URANIUM_PELLET = ITEMS.registerSimpleItem("uranium_pellet");
    public static final DeferredItem<Item> COMPACT_URANIUM_DIOXIDE = ITEMS.registerSimpleItem("compact_uranium_dioxide");
    public static final DeferredItem<Item> GREEN_COMPACT_URANIUM_PELLET = ITEMS.registerSimpleItem("green_compact_uranium_pellet");
    public static final DeferredItem<Item> COMPACT_URANIUM_PELLET = ITEMS.registerSimpleItem("compact_uranium_pellet");

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> URANYL_NITRATE_SOLUTION = stream("uranyl_nitrate_solution", 0xEEC8C860);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> URANIUM_HEXAFLUORIDE = stream("uranium_hexafluoride", 0xAADAD6C8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ENRICHED_URANIUM_HEXAFLUORIDE = stream("enriched_uranium_hexafluoride", 0xAAD6DCB4);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE = stream("compact_enriched_uranium_hexafluoride", 0xAAD7C48F);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEPLETED_URANIUM_HEXAFLUORIDE = stream("depleted_uranium_hexafluoride", 0xAAB8B8B4);

    private CEUranium() { }

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
    }
}
