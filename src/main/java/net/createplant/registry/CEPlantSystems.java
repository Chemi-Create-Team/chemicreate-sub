package net.createplant.registry;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.CoolingTowerBlock;
import net.createplant.block.CoolingTowerPortBlock;
import net.createplant.block.ProcessControlBlock;
import net.createplant.block.PidRackBlock;
import net.createplant.block.CondenserWaterboxBlock;
import net.createplant.block.CondenserCoolingPortBlock;
import net.createplant.block.entity.CondenserCoolingPortBlockEntity;
import net.createplant.block.entity.CondenserWaterboxBlockEntity;
import net.createplant.block.entity.ProcessControlBlockEntity;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.block.entity.CoolingTowerPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEPlantSystems {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static BlockBehaviour.Properties concrete() { return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(8, 1200); }
    private static BlockBehaviour.Properties steel() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6).sound(SoundType.NETHERITE_BLOCK).noOcclusion().forceSolidOn(); }
    public static final DeferredBlock<CoolingTowerBlock> COOLING_TOWER = BLOCKS.registerBlock("natural_draft_cooling_tower", CoolingTowerBlock::new, steel());
    public static final DeferredBlock<Block> TOWER_SHELL = BLOCKS.registerSimpleBlock("cooling_tower_shell", concrete());
    public static final DeferredBlock<Block> TOWER_BASIN = BLOCKS.registerSimpleBlock("cooling_tower_basin", concrete());
    public static final DeferredBlock<Block> TOWER_FILL = BLOCKS.registerSimpleBlock("cooling_tower_fill", steel());
    public static final DeferredBlock<Block> TOWER_DISTRIBUTOR = BLOCKS.registerSimpleBlock("cooling_tower_distributor", steel());
    public static final DeferredBlock<CoolingTowerPortBlock> TOWER_HOT_PORT = BLOCKS.registerBlock("cooling_tower_hot_inlet", p -> new CoolingTowerPortBlock(p, 0), steel());
    public static final DeferredBlock<CoolingTowerPortBlock> TOWER_COLD_PORT = BLOCKS.registerBlock("cooling_tower_cold_outlet", p -> new CoolingTowerPortBlock(p, 1), steel());
    public static final DeferredBlock<CoolingTowerPortBlock> TOWER_MAKEUP_PORT = BLOCKS.registerBlock("cooling_tower_makeup_inlet", p -> new CoolingTowerPortBlock(p, 2), steel());
    public static final DeferredBlock<ProcessControlBlock> PID_CONTROLLER = BLOCKS.registerBlock("pid_controller", p -> new ProcessControlBlock(p, ProcessControlBlock.Kind.PID), steel());
    public static final DeferredBlock<PidRackBlock> PID_RACK = BLOCKS.registerBlock("pid_instrument_rack", PidRackBlock::new, steel());
    public static final DeferredBlock<ProcessControlBlock> LEVEL_TRANSMITTER = BLOCKS.registerBlock("process_transmitter", p -> new ProcessControlBlock(p, ProcessControlBlock.Kind.INSTRUMENT), steel());
    public static final DeferredBlock<ProcessControlBlock> VALVE_ACTUATOR = BLOCKS.registerBlock("control_valve_actuator", p -> new ProcessControlBlock(p, ProcessControlBlock.Kind.ACTUATOR), steel());
    public static final DeferredBlock<CondenserWaterboxBlock> CONDENSER = BLOCKS.registerBlock("condenser_waterbox", CondenserWaterboxBlock::new, steel());
    public static final DeferredBlock<Block> CONDENSER_SHELL = BLOCKS.registerSimpleBlock("condenser_shell", steel());
    public static final DeferredBlock<Block> CONDENSER_TUBES = BLOCKS.registerSimpleBlock("condenser_tube_bundle", steel());
    public static final DeferredBlock<CondenserCoolingPortBlock> CONDENSER_INLET = BLOCKS.registerBlock("condenser_cooling_inlet", p -> new CondenserCoolingPortBlock(p, true), steel());
    public static final DeferredBlock<CondenserCoolingPortBlock> CONDENSER_OUTLET = BLOCKS.registerBlock("condenser_cooling_outlet", p -> new CondenserCoolingPortBlock(p, false), steel());
    public static final List<DeferredBlock<?>> PARTS = List.of(COOLING_TOWER, TOWER_SHELL, TOWER_BASIN, TOWER_FILL, TOWER_DISTRIBUTOR,
        TOWER_HOT_PORT, TOWER_COLD_PORT, TOWER_MAKEUP_PORT, PID_CONTROLLER, PID_RACK, LEVEL_TRANSMITTER, VALVE_ACTUATOR,
        CONDENSER, CONDENSER_SHELL, CONDENSER_TUBES, CONDENSER_INLET, CONDENSER_OUTLET);
    static { PARTS.forEach(ITEMS::registerSimpleBlockItem); }
    public static final Supplier<BlockEntityType<CoolingTowerBlockEntity>> TOWER_ENTITY = TYPES.register("natural_draft_cooling_tower",
        () -> BlockEntityType.Builder.of(CoolingTowerBlockEntity::new, COOLING_TOWER.get()).build(null));
    public static final Supplier<BlockEntityType<CoolingTowerPortBlockEntity>> TOWER_PORT_ENTITY = TYPES.register("cooling_tower_port",
        () -> BlockEntityType.Builder.of(CoolingTowerPortBlockEntity::new, TOWER_HOT_PORT.get(), TOWER_COLD_PORT.get(), TOWER_MAKEUP_PORT.get()).build(null));
    public static final Supplier<BlockEntityType<ProcessControlBlockEntity>> CONTROL_ENTITY = TYPES.register("process_control",
        () -> BlockEntityType.Builder.of(ProcessControlBlockEntity::new, PID_CONTROLLER.get(), LEVEL_TRANSMITTER.get(), VALVE_ACTUATOR.get()).build(null));
    public static final Supplier<BlockEntityType<CondenserWaterboxBlockEntity>> CONDENSER_ENTITY = TYPES.register("condenser_waterbox",
        () -> BlockEntityType.Builder.of(CondenserWaterboxBlockEntity::new, CONDENSER.get()).build(null));
    public static final Supplier<BlockEntityType<CondenserCoolingPortBlockEntity>> CONDENSER_PORT_ENTITY = TYPES.register("condenser_cooling_port",
        () -> BlockEntityType.Builder.of(CondenserCoolingPortBlockEntity::new, CONDENSER_INLET.get(), CONDENSER_OUTLET.get()).build(null));
    private CEPlantSystems() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);bus.addListener(CEPlantSystems::capabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.event.PlantStructureEvents::changed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.event.PlantStructureEvents::unloaded);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.event.FacilityAchievements::tick);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONDENSER_PORT_ENTITY.get(), (be, side) -> {
            var owner = be.owner();return side == null || owner == null ? null : owner.coolingPort(((CondenserCoolingPortBlock) be.getBlockState().getBlock()).input);
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TOWER_PORT_ENTITY.get(), (part, side) -> {
            var owner = part.owner();
            return side == null || owner == null ? null : owner.port(part.tank());
        });
    }
}
