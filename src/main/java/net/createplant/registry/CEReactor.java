package net.createplant.registry;

import java.util.*;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.ExtractionColumnStructureBlock;
import net.createplant.block.LargeAbsorptionTowerBlock;
import net.createplant.block.LargeAbsorptionTowerRingBlock;
import net.createplant.block.ReactorMachineBlock;
import net.createplant.block.ThermalMachineBlock;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.ColumnRingBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.item.ColumnStoreyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class CEReactor {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    /** Fitted in each layer of the reaction tower to hold its catalyst. */
    public static final DeferredItem<Item> CATALYST_BASKET=ITEMS.registerSimpleItem("reactor_catalyst_basket");
    /** Each layer's internal selects the vessel process: impeller or reaction trays. */
    public static final Map<ReactorModule,DeferredItem<Item>> INTERNALS=new EnumMap<>(ReactorModule.class);
    static {
        INTERNALS.put(ReactorModule.STIRRED,ITEMS.registerSimpleItem("reactor_impeller"));
        INTERNALS.put(ReactorModule.ABSORBER,ITEMS.registerSimpleItem("reactor_separator_core"));
    }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }
    private static BlockBehaviour.Properties steel() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5,1200).sound(SoundType.NETHERITE_BLOCK).noOcclusion(); }
    public static final DeferredBlock<LargeAbsorptionTowerBlock> LARGE_ABSORPTION_TOWER=BLOCKS.registerBlock("large_absorption_tower",LargeAbsorptionTowerBlock::new,steel());
    public static final DeferredBlock<LargeAbsorptionTowerRingBlock> LARGE_ABSORPTION_RING=BLOCKS.registerBlock("large_absorption_tower_ring",LargeAbsorptionTowerRingBlock::new,steel());
    static {
        ITEMS.register("large_absorption_tower",()->new ColumnStoreyItem(LARGE_ABSORPTION_TOWER.get(),new Item.Properties()));
    }
    public static final Supplier<BlockEntityType<ReactorMachineBlockEntity>> ENTITY=TYPES.register("reactor_machine",()->BlockEntityType.Builder.of(ReactorMachineBlockEntity::new,CEHeating.EXCHANGER_CONTROLLER.get(),CEHeating.LARGE_EXCHANGER_CONTROLLER.get(),CEHeating.JACKET_CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<AbsorptionTowerBlockEntity>> ABSORPTION_ENTITY=TYPES.register("absorption_tower",()->BlockEntityType.Builder.of(AbsorptionTowerBlockEntity::new,LARGE_ABSORPTION_TOWER.get()).build(null));
    public static final Supplier<BlockEntityType<ColumnRingBlockEntity>> RING_ENTITY=TYPES.register("large_absorption_tower_ring",()->BlockEntityType.Builder.of(ColumnRingBlockEntity::new,LARGE_ABSORPTION_RING.get()).build(null));
    /** The module an internal makes, or null for any other item. */
    public static ReactorModule module(ItemStack stack) {
        for (var entry:INTERNALS.entrySet()) if (stack.is(entry.getValue().get())) return entry.getKey();
        return null;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);bus.addListener(CEReactor::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,ABSORPTION_ENTITY.get(),(be,side)->be.storeyPort(be.getBlockPos(),side));
        // The ring of a large storey hands pipes on to the storey at its centre.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,RING_ENTITY.get(),(be,side)->{
            BlockPos centre=ExtractionColumnStructureBlock.centre(be.getLevel(),be.getBlockPos(),be.getBlockState());
            return centre!=null && be.getLevel().getBlockEntity(centre) instanceof AbsorptionTowerBlockEntity storey ? storey.storeyPort(be.getBlockPos(),side) : null;
        });
    }
}
