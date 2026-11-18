package net.createplant.registry;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.simulation.WireType;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.TransmissionTowerBlock;
import net.createplant.block.entity.TransmissionTowerBlockEntity;
import net.createplant.item.HighVoltageCableItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CETransmission {
    public static final int MAX_SPAN = 128;
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<WireType> WIRES = DeferredRegister.create(CEERegistries.WIRE_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<TransmissionTowerBlock> TOWER = BLOCKS.registerBlock("transmission_tower", TransmissionTowerBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<TransmissionTowerBlockEntity>> TOWER_ENTITY = ENTITIES.register("transmission_tower",
        () -> BlockEntityType.Builder.of(TransmissionTowerBlockEntity::new, TOWER.get()).build(null));

    public static final DeferredItem<Item> CONDUCTOR = ITEMS.registerSimpleItem("high_voltage_conductor");
    public static final DeferredItem<HighVoltageCableItem> SPOOL = ITEMS.register("high_voltage_cable",
        () -> new HighVoltageCableItem(new Item.Properties(), CETransmission::wire));
    private static final Supplier<WireType> WIRE = WIRES.register("high_voltage_cable", () -> new WireType.Builder(
        PartialModel.of(ResourceLocation.fromNamespaceAndPath("electroenergetics", "block/heavily_insulated_wire_segment")))
        .sag(0).maxLength(() -> MAX_SPAN).thickness(.095f)
        .resistance(() -> .005).insulationResistance(100_000_000).maxInsulationVoltage(() -> 35_000)
        .maxTemperature(() -> 700).spoolItem(SPOOL::get).droppedItem(CONDUCTOR).build());

    static { ITEMS.registerSimpleBlockItem(TOWER); }

    private CETransmission() { }
    public static WireType wire() { return WIRE.get(); }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        WIRES.register(bus);
    }
}
