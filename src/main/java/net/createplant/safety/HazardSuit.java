package net.createplant.safety;

import net.createplant.CreatePlant;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.registry.CESafety;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class HazardSuit {
    public static final String KEY = "hazard." + CreatePlant.MOD_ID + ".";
    public static final ResourceKey<DamageType> HOT_LIQUID = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePlant.location("hot_process_liquid"));

    private HazardSuit() { }

    public static boolean boots(LivingEntity living) {
        return living.getItemBySlot(EquipmentSlot.FEET).is(CEPressureInfrastructure.BOOTS.get());
    }

    public static boolean full(LivingEntity living) {
        return living.getItemBySlot(EquipmentSlot.HEAD).is(CEPressureInfrastructure.HELMET.get())
            && living.getItemBySlot(EquipmentSlot.CHEST).is(CEPressureInfrastructure.CHEST.get())
            && living.getItemBySlot(EquipmentSlot.LEGS).is(CEPressureInfrastructure.LEGS.get()) && boots(living);
    }

    private static boolean blocked(Holder<MobEffect> effect) {
        return effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.CONFUSION)
            || effect.is(MobEffects.BLINDNESS) || effect.is(CESafety.ACID_BURN);
    }

    @SubscribeEvent
    public static void protect(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        var living = event.getEntity();
        if (!full(living)) return;
        if (blocked(effect)) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (!effect.is(CESafety.TOXIC_GAS)) return;
        var helmet = living.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getDamageValue() >= helmet.getMaxDamage()) return;
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (!living.level().isClientSide) {
            var data = living.getPersistentData();
            var key = CreatePlant.MOD_ID + ".hazard_filter_tick";
            long now = living.level().getGameTime();
            if (!data.contains(key) || now - data.getLong(key) >= 20 || now < data.getLong(key)) {
                helmet.setDamageValue(helmet.getDamageValue() + 1);
                data.putLong(key, now);
            }
        }
    }

    @SubscribeEvent
    public static void equipped(LivingEquipmentChangeEvent event) {
        var living = event.getEntity();
        if (living.level().isClientSide || !full(living)) return;
        for (var effect : java.util.List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.CONFUSION, MobEffects.BLINDNESS, CESafety.ACID_BURN))
            living.removeEffect(effect);
        var helmet = living.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getDamageValue() < helmet.getMaxDamage()) living.removeEffect(CESafety.TOXIC_GAS);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if ((source.is(CorrosiveContact.DAMAGE) || source.is(HOT_LIQUID) || source.is(DamageTypes.LAVA)) && full(event.getEntity()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (boots(event.getEntity())) event.setDistance(Math.max(0, event.getDistance() - 12));
    }
}
