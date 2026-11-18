package net.createplant.registry;

import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.safety.ToxicGasEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Toxic gas and the gas mask that keeps it out. */
public final class CESafety {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CreatePlant.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CreatePlant.MOD_ID);
    public static final Holder<MobEffect> TOXIC_GAS = EFFECTS.register("toxic_gas", ToxicGasEffect::new);
    public static final Holder<MobEffect> ACID_BURN = EFFECTS.register("acid_burn", net.createplant.safety.AcidBurnEffect::new);
    /** Seconds of toxic gas the filter canister takes before it is spent; charcoal renews it. */
    public static final int FILTER_SECONDS = 1200;
    private static final Holder<ArmorMaterial> GAS_MASK_MATERIAL = ARMOR_MATERIALS.register("gas_mask", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.BOOTS, 0, ArmorItem.Type.LEGGINGS, 0, ArmorItem.Type.CHESTPLATE, 0, ArmorItem.Type.HELMET, 1, ArmorItem.Type.BODY, 0),
        5, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(CreatePlant.location("gas_mask"))), 0, 0));
    public static final DeferredItem<ArmorItem> GAS_MASK = ITEMS.register("gas_mask",
        () -> new ArmorItem(GAS_MASK_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().durability(FILTER_SECONDS).setNoRepair()));
    private CESafety() { }
    public static void register(IEventBus bus) { ITEMS.register(bus);EFFECTS.register(bus);ARMOR_MATERIALS.register(bus); }
}
