package net.createplant.safety;

import net.createplant.CreatePlant;
import net.createplant.registry.CEPortableTools;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class DazedEffect extends MobEffect {
    public DazedEffect() {
        super(MobEffectCategory.HARMFUL, 0xe5bc53);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, CreatePlant.location("dazed_movement"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, CreatePlant.location("dazed_jump"), -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

    @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        if (entity.isUsingItem()) entity.stopUsingItem();
        entity.setSprinting(false);
        return true;
    }

    @SubscribeEvent public static void useItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().hasEffect(CEPortableTools.DAZED)) event.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(CEPortableTools.DAZED)) event.setCanceled(true);
    }

    @SubscribeEvent public static void mobAttack(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (source.getDirectEntity() instanceof Mob mob && mob.hasEffect(CEPortableTools.DAZED)
            && (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) event.setCanceled(true);
    }
}
