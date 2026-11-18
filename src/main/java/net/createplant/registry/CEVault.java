package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.VaultHatchBlock;
import net.createplant.block.entity.VaultHatchBlockEntity;
import net.createplant.inventory.VaultHatchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEVault {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CreatePlant.MOD_ID);
    public static final DeferredBlock<VaultHatchBlock> HATCH = BLOCKS.registerBlock("vault_hatch", VaultHatchBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_TRAPDOOR).noOcclusion());
    public static final Supplier<BlockEntityType<VaultHatchBlockEntity>> HATCH_ENTITY = TYPES.register("vault_hatch",
        () -> BlockEntityType.Builder.of(VaultHatchBlockEntity::new, HATCH.get()).build(null));
    public static final Supplier<MenuType<VaultHatchMenu>> MENU = MENUS.register("vault_hatch",
        () -> IMenuTypeExtension.create(VaultHatchMenu::new));

    static { ITEMS.registerSimpleBlockItem(HATCH); }

    private CEVault() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        MENUS.register(bus);
    }
}
