package net.createplant.registry;

import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.item.ProtectiveClothingItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEProtectiveClothing {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CreatePlant.MOD_ID);

    /** The hall workers' cap, white coat, trousers and shoe covers: together they intercept 90% of beta exposure and surface contamination; not gamma radiation. */
    private static final Holder<ArmorMaterial> PROTECTIVE_CLOTHING_MATERIAL = ARMOR_MATERIALS.register("protective_clothing", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.BOOTS, 1, ArmorItem.Type.LEGGINGS, 1, ArmorItem.Type.CHESTPLATE, 2, ArmorItem.Type.HELMET, 1, ArmorItem.Type.BODY, 2),
        15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.WHITE_WOOL),
        List.of(new ArmorMaterial.Layer(CreatePlant.location("protective_clothing"))), 0, 0));
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_CAP = clothing("protective_cap", ArmorItem.Type.HELMET, 0.15);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_COAT = clothing("protective_coat", ArmorItem.Type.CHESTPLATE, 0.4);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_TROUSERS = clothing("protective_trousers", ArmorItem.Type.LEGGINGS, 0.25);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_SHOE_COVERS = clothing("protective_shoe_covers", ArmorItem.Type.BOOTS, 0.1);

    private CEProtectiveClothing() { }

    private static DeferredItem<ProtectiveClothingItem> clothing(String name, ArmorItem.Type type, double protection) {
        return ITEMS.register(name, () -> new ProtectiveClothingItem(PROTECTIVE_CLOTHING_MATERIAL, type, protection, new Item.Properties().durability(type.getDurability(5))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ARMOR_MATERIALS.register(bus);
    }
}
