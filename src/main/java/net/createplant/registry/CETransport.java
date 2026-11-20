package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import java.util.function.Predicate;
import net.createplant.block.StainlessEncasedPipeBlock;
import net.createplant.block.StainlessPipeBlock;
import net.createplant.block.StainlessGlassPipeBlock;
import net.createplant.block.MaterialPipeBlock;
import net.createplant.block.PlantFluidTankBlock;
import net.createplant.block.entity.GasOnlyFluidHandler;
import net.createplant.block.entity.MaterialPipeBlockEntity;
import net.createplant.block.entity.PlantFluidTankBlockEntity;
import net.createplant.transport.PipeMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CETransport {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<MaterialPipeBlock> REINFORCED_PIPE=pipe("reinforced_pipe",PipeMaterial.REINFORCED);
    public static final DeferredBlock<MaterialPipeBlock> NICKEL_ALLOY_PIPE=pipe("nickel_alloy_pipe",PipeMaterial.NICKEL_ALLOY);
    public static final DeferredBlock<MaterialPipeBlock> ZIRCONIUM_PIPE=pipe("zirconium_pipe",PipeMaterial.ZIRCONIUM);
    public static final DeferredBlock<MaterialPipeBlock> CRYOGENIC_PIPE=pipe("cryogenic_pipe",PipeMaterial.CRYOGENIC);
    public static final DeferredBlock<MaterialPipeBlock> PVC_PIPE=pipe("pvc_pipe",PipeMaterial.PVC);
    public static final DeferredBlock<MaterialPipeBlock> CERAMIC_PIPE=pipe("ceramic_pipe",PipeMaterial.CERAMIC,BlockBehaviour.Properties.ofFullCopy(Blocks.TERRACOTTA).noOcclusion());
    static { ITEMS.registerSimpleItem("unfired_ceramic_pipe"); }
    public static final java.util.List<DeferredBlock<net.createplant.block.MaterialPumpBlock>> MATERIAL_PUMPS=
        java.util.List.of(pump("reinforced", .92f), pump("nickel_alloy", 1.11f), pump("zirconium", 1.18f), pump("stainless_steel", 1.04f),
            pump("cryogenic", .82f), pump("pvc", 1.27f), pump("resin", 1.01f), pump("gas", 1.14f), pump("large_gas", .73f));
    public static final Supplier<BlockEntityType<com.simibubi.create.content.fluids.pump.PumpBlockEntity>> MATERIAL_PUMP_ENTITY=TYPES.register("material_pump",()->
        BlockEntityType.Builder.of((pos,state)->new com.simibubi.create.content.fluids.pump.PumpBlockEntity(CETransport.MATERIAL_PUMP_ENTITY.get(),pos,state),
            MATERIAL_PUMPS.stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static final Supplier<BlockEntityType<MaterialPipeBlockEntity>> PIPE=TYPES.register("material_pipe",()->
        BlockEntityType.Builder.of(MaterialPipeBlockEntity::new,REINFORCED_PIPE.get(),NICKEL_ALLOY_PIPE.get(),ZIRCONIUM_PIPE.get(),CRYOGENIC_PIPE.get(),PVC_PIPE.get(),CERAMIC_PIPE.get()).build(null));
    // Create's fluid pipe, encased pipe and glass pipe in stainless steel: Create's shapes and behaviour with their own material.
    public static final DeferredBlock<StainlessPipeBlock> STAINLESS_PIPE=BLOCKS.registerBlock("stainless_steel_pipe",StainlessPipeBlock::new,stainlessPipe().forceSolidOff());
    public static final DeferredBlock<StainlessEncasedPipeBlock> STAINLESS_ENCASED_PIPE=BLOCKS.registerBlock("stainless_steel_encased_pipe",StainlessEncasedPipeBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY));
    public static final DeferredBlock<StainlessGlassPipeBlock> STAINLESS_GLASS_PIPE=BLOCKS.registerBlock("stainless_steel_glass_pipe",StainlessGlassPipeBlock::new,stainlessPipe().noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STAINLESS_PIPE_ITEM=ITEMS.registerSimpleBlockItem(STAINLESS_PIPE);
    public static final Supplier<BlockEntityType<com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity>> STAINLESS_PIPE_ENTITY=TYPES.register("stainless_steel_pipe",()->
        BlockEntityType.Builder.of((pos,state)->new com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity(CETransport.STAINLESS_PIPE_ENTITY.get(),pos,state),STAINLESS_PIPE.get()).build(null));
    public static final Supplier<BlockEntityType<com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity>> STAINLESS_ENCASED_PIPE_ENTITY=TYPES.register("stainless_steel_encased_pipe",()->
        BlockEntityType.Builder.of((pos,state)->new com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity(CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get(),pos,state),STAINLESS_ENCASED_PIPE.get()).build(null));
    public static final Supplier<BlockEntityType<com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity>> STAINLESS_GLASS_PIPE_ENTITY=TYPES.register("stainless_steel_glass_pipe",()->
        BlockEntityType.Builder.of((pos,state)->new com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity(CETransport.STAINLESS_GLASS_PIPE_ENTITY.get(),pos,state),STAINLESS_GLASS_PIPE.get()).build(null));
    // Create's speedometer and stressometer set into a pipe, reading temperature, pressure and flow instead of rotation.
    public static final DeferredBlock<net.createplant.block.PipeGaugeBlock> PIPE_THERMOMETER=BLOCKS.registerBlock("pipe_thermometer",p->new net.createplant.block.PipeGaugeBlock(p,net.createplant.block.PipeGaugeBlock.Kind.TEMPERATURE),gauge());
    public static final DeferredBlock<net.createplant.block.PipeGaugeBlock> PIPE_PRESSURE_GAUGE=BLOCKS.registerBlock("pipe_pressure_gauge",p->new net.createplant.block.PipeGaugeBlock(p,net.createplant.block.PipeGaugeBlock.Kind.PRESSURE),gauge());
    public static final DeferredBlock<net.createplant.block.PipeGaugeBlock> PIPE_FLOW_METER=BLOCKS.registerBlock("pipe_flow_meter",p->new net.createplant.block.PipeGaugeBlock(p,net.createplant.block.PipeGaugeBlock.Kind.FLOW),gauge());
    static { ITEMS.registerSimpleBlockItem(PIPE_THERMOMETER);ITEMS.registerSimpleBlockItem(PIPE_PRESSURE_GAUGE);ITEMS.registerSimpleBlockItem(PIPE_FLOW_METER); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.PipeGaugeBlockEntity>> PIPE_GAUGE_ENTITY=TYPES.register("pipe_gauge",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.PipeGaugeBlockEntity::new,PIPE_THERMOMETER.get(),PIPE_PRESSURE_GAUGE.get(),PIPE_FLOW_METER.get()).build(null));
    // Create's own fluid tank laid on its side, like its item vault.
    public static final DeferredBlock<net.createplant.block.HorizontalFluidTankBlock> HORIZONTAL_FLUID_TANK=BLOCKS.registerBlock("horizontal_fluid_tank",net.createplant.block.HorizontalFluidTankBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.COPPER));
    static { ITEMS.registerSimpleBlockItem(HORIZONTAL_FLUID_TANK); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.HorizontalTankBlockEntity>> HORIZONTAL_FLUID_TANK_ENTITY=TYPES.register("horizontal_fluid_tank",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.HorizontalTankBlockEntity::new,HORIZONTAL_FLUID_TANK.get()).build(null));
    public static final DeferredBlock<net.createplant.block.HorizontalFluidTankBlock> STAINLESS_BOILER=BLOCKS.registerBlock("stainless_boiler",
        p->new net.createplant.block.HorizontalFluidTankBlock(p,()->CETransport.STAINLESS_BOILER_ENTITY.get()),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.METAL));
    static { ITEMS.registerSimpleBlockItem(STAINLESS_BOILER); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.HorizontalTankBlockEntity>> STAINLESS_BOILER_ENTITY=TYPES.register("stainless_boiler",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.HorizontalTankBlockEntity::stainless,STAINLESS_BOILER.get()).build(null));
    // A gas tank in a container frame, formed only at the container sizes.
    public static final DeferredBlock<net.createplant.block.GasTankContainerBlock> GAS_TANK_CONTAINER=BLOCKS.registerBlock("gas_tank_container",net.createplant.block.GasTankContainerBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.NETHERITE_BLOCK));
    static { ITEMS.registerSimpleBlockItem(GAS_TANK_CONTAINER); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.GasTankContainerBlockEntity>> GAS_TANK_CONTAINER_ENTITY=TYPES.register("gas_tank_container",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.GasTankContainerBlockEntity::new,GAS_TANK_CONTAINER.get()).build(null));
    // A riveted iron-grey Create-style tank body for the separator.
    public static final DeferredBlock<net.createplant.block.AmmoniaSeparatorBlock> AMMONIA_SEPARATOR=BLOCKS.registerBlock("ammonia_separator",p->new net.createplant.block.AmmoniaSeparatorBlock(p,()->CETransport.AMMONIA_SEPARATOR_ENTITY.get()),
        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion());
    static { ITEMS.register("ammonia_separator",()->new net.createplant.item.PlantTankItem(AMMONIA_SEPARATOR.get(),new net.minecraft.world.item.Item.Properties())); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.AmmoniaSeparatorBlockEntity>> AMMONIA_SEPARATOR_ENTITY=TYPES.register("ammonia_separator",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.AmmoniaSeparatorBlockEntity::new,AMMONIA_SEPARATOR.get()).build(null));
    // The Haber-Bosch tower: a 3x3 shell round a core of triiron tetroxide piles.
    public static final DeferredBlock<net.createplant.block.ReactionTowerBlock> REACTION_TOWER=BLOCKS.registerBlock("reaction_tower",p->new net.createplant.block.ReactionTowerBlock(p,()->CETransport.REACTION_TOWER_ENTITY.get()),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.NETHERITE_BLOCK));
    static { ITEMS.register("reaction_tower",()->new net.createplant.item.PlantTankItem(REACTION_TOWER.get(),new net.minecraft.world.item.Item.Properties())); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.ReactionTowerBlockEntity>> REACTION_TOWER_ENTITY=TYPES.register("reaction_tower",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.ReactionTowerBlockEntity::new,REACTION_TOWER.get()).build(null));
    public static final DeferredBlock<net.createplant.block.PulsationTubeBlock> GAS_RECEIVER=BLOCKS.registerBlock("gas_receiver",net.createplant.block.PulsationTubeBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.METAL));
    static { ITEMS.registerSimpleBlockItem(GAS_RECEIVER); }
    // A thick carbon-steel process vessel for flash drums and other mixed liquid/vapour service.
    public static final DeferredBlock<PlantFluidTankBlock> PRESSURE_TANK=tank("pressure_tank",()->CETransport.PRESSURE_TANK_ENTITY.get(),stack->true,SoundType.NETHERITE_BLOCK);
    public static final Supplier<BlockEntityType<net.createplant.block.entity.PulsationTubeBlockEntity>> GAS_RECEIVER_ENTITY=TYPES.register("gas_receiver",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.PulsationTubeBlockEntity::new,GAS_RECEIVER.get()).build(null));
    public static final Supplier<BlockEntityType<PlantFluidTankBlockEntity>> PRESSURE_TANK_ENTITY=tankEntity("pressure_tank",PRESSURE_TANK);
    // Any liquid or gas: the stainless tank stands up to corrosive ones, the vacuum-jacketed cryogenic tank keeps its contents' temperature.
    public static final DeferredBlock<PlantFluidTankBlock> STAINLESS_STEEL_TANK=tank("stainless_steel_tank",()->CETransport.STAINLESS_STEEL_TANK_ENTITY.get(),stack->true);
    public static final Supplier<BlockEntityType<PlantFluidTankBlockEntity>> STAINLESS_STEEL_TANK_ENTITY=tankEntity("stainless_steel_tank",STAINLESS_STEEL_TANK);
    public static final DeferredBlock<PlantFluidTankBlock> CRYOGENIC_TANK=tank("cryogenic_tank",()->CETransport.CRYOGENIC_TANK_ENTITY.get(),stack->true);
    public static final Supplier<BlockEntityType<PlantFluidTankBlockEntity>> CRYOGENIC_TANK_ENTITY=tankEntity("cryogenic_tank",CRYOGENIC_TANK);
    // Create's tank, up to four blocks high, with a hinged lid that opens it into a basin.
    public static final DeferredBlock<net.createplant.block.LiddedVesselBlock> LIDDED_VESSEL=BLOCKS.registerBlock("lidded_vessel",net.createplant.block.LiddedVesselBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.NETHERITE_BLOCK));
    static { ITEMS.register("lidded_vessel",()->new net.createplant.item.LiddedVesselItem(LIDDED_VESSEL.get(),new net.minecraft.world.item.Item.Properties())); }
    public static final Supplier<BlockEntityType<net.createplant.block.entity.LiddedVesselBlockEntity>> LIDDED_VESSEL_ENTITY=TYPES.register("lidded_vessel",()->
        BlockEntityType.Builder.of(net.createplant.block.entity.LiddedVesselBlockEntity::new,LIDDED_VESSEL.get()).build(null));
    /** Legacy storable gases and every process gas flagged in the transport table. */
    public static boolean gas(FluidStack stack) { return GasOnlyFluidHandler.isGas(stack) || net.createplant.transport.FluidHazards.of(stack).gas() || !stack.isEmpty() && stack.getFluidType().isLighterThanAir(); }
    private static DeferredBlock<MaterialPipeBlock> pipe(String id,PipeMaterial material) {
        return pipe(id,material,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    }
    private static DeferredBlock<MaterialPipeBlock> pipe(String id,PipeMaterial material,BlockBehaviour.Properties properties) {
        var block=BLOCKS.registerBlock(id,p->new MaterialPipeBlock(p,material),properties);
        ITEMS.registerSimpleBlockItem(block);return block;
    }
    private static DeferredBlock<net.createplant.block.MaterialPumpBlock> pump(String material,float pitch) {
        var block=BLOCKS.registerBlock(material+"_pump",properties->new net.createplant.block.MaterialPumpBlock(properties,pitch),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
        ITEMS.registerSimpleBlockItem(block);return block;
    }
    private static BlockBehaviour.Properties gauge() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion().mapColor(MapColor.PODZOL);
    }
    private static BlockBehaviour.Properties stainlessPipe() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).mapColor(MapColor.METAL).sound(SoundType.METAL);
    }
    private static DeferredBlock<PlantFluidTankBlock> tank(String id,Supplier<BlockEntityType<PlantFluidTankBlockEntity>> type,Predicate<FluidStack> accepts) {
        return tank(id,type,accepts,SoundType.METAL);
    }
    // Same heavy steel sound as Create's basin and casings.
    private static DeferredBlock<PlantFluidTankBlock> tank(String id,Supplier<BlockEntityType<PlantFluidTankBlockEntity>> type,Predicate<FluidStack> accepts,SoundType sound) {
        var block=BLOCKS.registerBlock(id,p->new PlantFluidTankBlock(p,type,accepts),BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(sound));
        ITEMS.register(id,()->new net.createplant.item.PlantTankItem(block.get(),new net.minecraft.world.item.Item.Properties()));return block;
    }
    private static Supplier<BlockEntityType<PlantFluidTankBlockEntity>> tankEntity(String id,DeferredBlock<PlantFluidTankBlock> block) {
        return TYPES.register(id,()->BlockEntityType.Builder.of(PlantFluidTankBlockEntity::new,block.get()).build(null));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(()-> {
            com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(STAINLESS_PIPE.get(),STAINLESS_ENCASED_PIPE.get());
            for (var pump:MATERIAL_PUMPS)
                com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(pump.get(),()->
                    com.simibubi.create.api.stress.BlockStressValues.getImpact(com.simibubi.create.AllBlocks.MECHANICAL_PUMP.get()));
        }));
    }
}
