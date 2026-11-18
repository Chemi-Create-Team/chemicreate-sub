package net.createplant.registry;

import java.util.function.Supplier;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import net.createplant.CreatePlant;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.inventory.FacilityConstructorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEAssembly {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreatePlant.MOD_ID);
    public static final DeferredBlock<FacilityConstructorBlock> CONSTRUCTOR = BLOCKS.registerBlock("facility_constructor", FacilityConstructorBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3).noOcclusion());
    public static final Supplier<BlockEntityType<SchematicannonBlockEntity>> CONSTRUCTOR_ENTITY = TYPES.register("facility_constructor",
        () -> BlockEntityType.Builder.<SchematicannonBlockEntity>of(FacilityConstructorBlockEntity::new, CONSTRUCTOR.get()).build(null));
    public static final Supplier<MenuType<SchematicannonMenu>> MENU = MENUS.register("facility_constructor",
        () -> IMenuTypeExtension.create(FacilityConstructorMenu::new));

    static { ITEMS.registerSimpleBlockItem(CONSTRUCTOR); }

    private CEAssembly() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        MENUS.register(bus);
        bus.addListener(CEAssembly::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONSTRUCTOR_ENTITY.get(),
            (be, side) -> ((FacilityConstructorBlockEntity) be).supplies());
    }
}
