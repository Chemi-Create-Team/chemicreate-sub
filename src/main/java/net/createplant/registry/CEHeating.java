package net.createplant.registry;

import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CEHeating {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    /** A tank with a tube bundle through it, the casing of both shell-and-tube exchangers, and the 1 x 1 jacketed pipe; a complete run turns one into its controller. */
    public static final DeferredBlock<ExchangerCasingBlock> TUBE_TANK=BLOCKS.registerBlock("tube_piped_tank",ExchangerCasingBlock::new,tank());
    public static final DeferredBlock<ExchangerCasingBlock> JACKET=BLOCKS.registerBlock("jacketed_pipe",ExchangerCasingBlock::new,properties());
    public static final DeferredBlock<ExchangerControllerBlock> EXCHANGER_CONTROLLER=BLOCKS.registerBlock("heat_exchanger_controller",p->new ExchangerControllerBlock(p,ExchangerStructure.Kind.SHELL),tank());
    public static final DeferredBlock<ExchangerControllerBlock> LARGE_EXCHANGER_CONTROLLER=BLOCKS.registerBlock("large_heat_exchanger_controller",p->new ExchangerControllerBlock(p,ExchangerStructure.Kind.LARGE),tank());
    public static final DeferredBlock<ExchangerControllerBlock> JACKET_CONTROLLER=BLOCKS.registerBlock("jacketed_pipe_controller",p->new ExchangerControllerBlock(p,ExchangerStructure.Kind.JACKET),properties());
    public static final DeferredBlock<FlowRegulatorBlock> REGULATOR=BLOCKS.registerBlock("flow_regulator",FlowRegulatorBlock::new,properties());
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> TYPES=DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<net.createplant.block.entity.ThermalPartBlockEntity>> PART_ENTITY=TYPES.register("thermal_part",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(net.createplant.block.entity.ThermalPartBlockEntity::new,TUBE_TANK.get(),JACKET.get()).build(null));
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<net.createplant.block.entity.FlowRegulatorBlockEntity>> REGULATOR_ENTITY=TYPES.register("flow_regulator",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(net.createplant.block.entity.FlowRegulatorBlockEntity::new,REGULATOR.get()).build(null));
    public static final DeferredItem<net.createplant.item.JacketedPipeItem> JACKET_ITEM=ITEMS.register("jacketed_pipe",()->new net.createplant.item.JacketedPipeItem(JACKET.get(),new net.minecraft.world.item.Item.Properties()));
    static { ITEMS.registerSimpleBlockItem(TUBE_TANK);ITEMS.registerSimpleItem("iron_pipe");ITEMS.registerSimpleBlockItem(REGULATOR); }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }
    /** A whole tank cube, like Create's fluid tank, so the faces between the run's blocks are not drawn. */
    private static BlockBehaviour.Properties tank() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK); }
    private CEHeating() { }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus); }
}
