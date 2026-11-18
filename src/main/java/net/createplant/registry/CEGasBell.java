package net.createplant.registry;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.block.GasHolderBlock;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.chemistry.WasteGas;
import net.createplant.contraptions.GasHolderSleeveContraption;
import net.createplant.contraptions.GasHolderSleeveEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CEGasBell {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    private static final DeferredRegister<ContraptionType> CONTRAPTIONS=DeferredRegister.create(CreateBuiltInRegistries.CONTRAPTION_TYPE.key(),CreatePlant.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES,CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,CreatePlant.MOD_ID);
    public static final DeferredBlock<GasHolderBlock> TANK=BLOCKS.registerBlock("gas_holder",GasHolderBlock::new,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<GasHolderBlockEntity>> TANK_ENTITY=TYPES.register("gas_holder",()->BlockEntityType.Builder.of(GasHolderBlockEntity::new,TANK.get()).build(null));
    public static final Supplier<ContraptionType> SLEEVE=CONTRAPTIONS.register("gas_holder_sleeve",()->new ContraptionType(GasHolderSleeveContraption::new));
    public static final Supplier<net.minecraft.world.entity.EntityType<GasHolderSleeveEntity>> MOVING_SLEEVE=ENTITIES.register("gas_holder_sleeve",()->net.minecraft.world.entity.EntityType.Builder.<GasHolderSleeveEntity>of(GasHolderSleeveEntity::new,net.minecraft.world.entity.MobCategory.MISC)
        .sized(1,1).clientTrackingRange(8).updateInterval(3).build(CreatePlant.MOD_ID+":gas_holder_sleeve"));
    public static final Supplier<DataComponentType<WasteGas>> GAS_CONTENTS=COMPONENTS.register("waste_gas_contents",()->DataComponentType.<WasteGas>builder().persistent(WasteGas.CODEC).networkSynchronized(WasteGas.STREAM).build());
    private static final PlantFluid WASTE=new PlantFluid(FLUID_TYPES,FLUIDS,"contaminated_gas",0xAA90856F,
        ()->FluidType.Properties.create().descriptionId("fluid."+CreatePlant.MOD_ID+".contaminated_gas").density(-1).temperature(293).supportsBoating(false),UnaryOperator.identity());
    public static final DeferredHolder<Fluid,BaseFlowingFluid.Source> CONTAMINATED_GAS=WASTE.source;
    private CEGasBell() { }
    public static void register(IEventBus bus) {
        ITEMS.register("gas_holder",()->new net.createplant.item.PlantTankItem(TANK.get(),new net.minecraft.world.item.Item.Properties()));
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);CONTRAPTIONS.register(bus);ENTITIES.register(bus);COMPONENTS.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);
        bus.addListener(GasHolderBlockEntity::registerCapabilities);
    }
}
