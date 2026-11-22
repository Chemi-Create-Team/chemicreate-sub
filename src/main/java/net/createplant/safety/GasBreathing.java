package net.createplant.safety;

import net.createplant.CreatePlant;
import net.createplant.cloud.GroundGasAtmosphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class GasBreathing {
    private static final float LOW_AIR = .28f;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePlant.location("gas_suffocation"));

    private GasBreathing() { }

    @SubscribeEvent
    public static void breathe(LivingBreatheEvent event) {
        var entity = event.getEntity();
        if (!event.canBreathe() || !entity.getEyeInFluidType().isAir()) return;
        float concentration = concentration(entity);
        if (concentration < LOW_AIR) return;
        event.setCanBreathe(false);
        event.setConsumeAirAmount(1 + Mth.floor(concentration * 3));
    }

    @SubscribeEvent
    public static void suffocate(LivingDrownEvent event) {
        var entity = event.getEntity();
        if (!event.isDrowning() || event.getDamageAmount() <= 0 || !entity.getEyeInFluidType().isAir() || concentration(entity) < LOW_AIR) return;
        var type = entity.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        entity.hurt(new DamageSource(type), event.getDamageAmount());
        event.setDamageAmount(0);
        event.setBubbleCount(0);
    }

    private static float concentration(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity instanceof Player player && player.getAbilities().invulnerable)
            return 0;
        return GroundGasAtmosphere.concentration(level, entity.getEyePosition());
    }
}
