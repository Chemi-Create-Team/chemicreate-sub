package net.createplant.registry;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.*;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.CoolerRadiatorBlock;
import net.createplant.block.CoolingFanBlock;
import net.createplant.block.CoolingFanShroudBlock;
import net.createplant.block.entity.CoolingFanBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CEProcessBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<CoolerRadiatorBlock> COOLER_RADIATOR = BLOCKS.registerBlock("cooler_radiator", CoolerRadiatorBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final DeferredBlock<CoolingFanBlock> COOLING_FAN = BLOCKS.registerBlock("cooling_fan", CoolingFanBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<CoolingFanShroudBlock> COOLING_FAN_SHROUD = BLOCKS.registerBlock("cooling_fan_shroud", CoolingFanShroudBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().forceSolidOn());
    static {
        ITEMS.registerSimpleBlockItem(COOLER_RADIATOR);
        ITEMS.registerSimpleBlockItem(COOLING_FAN); ITEMS.registerSimpleBlockItem(COOLING_FAN_SHROUD);
    }
    public static final Supplier<BlockEntityType<CoolingFanBlockEntity>> COOLING_FAN_ENTITY = TYPES.register("cooling_fan", () ->
        BlockEntityType.Builder.of(CoolingFanBlockEntity::new, COOLING_FAN.get()).build(null));
    public static final Supplier<BlockEntityType<net.createplant.block.entity.RadiatorBlockEntity>> COOLER_RADIATOR_DRIVE = TYPES.register("cooler_radiator", () ->
        BlockEntityType.Builder.of(net.createplant.block.entity.RadiatorBlockEntity::new,COOLER_RADIATOR.get()).build(null));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); }
}
