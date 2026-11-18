package net.createplant.nuclear;

import net.createplant.CreatePlant;
import net.createplant.item.ProtectiveClothingItem;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CERadiation;
import net.createplant.registry.CESafety;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

/**
 * Personal dosimetry and injury; every external contribution comes from the common radiation field.
 */
public final class Radiation {
    public static final ResourceKey<DamageType> SICKNESS =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE, CreatePlant.location("acute_radiation_syndrome"));

    private Radiation() {}

    public static double protection(ServerPlayer player) {
        double p = 0;
        for (var worn : player.getArmorSlots())
            if (worn.getItem() instanceof ProtectiveClothingItem clothing)
                p += clothing.protection();
        return Math.clamp(p, 0, 1);
    }

    public static boolean mask(ServerPlayer player) {
        var worn = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        return worn.is(CESafety.GAS_MASK.get()) && worn.getDamageValue() < worn.getMaxDamage();
    }

    /** Explicit gamma exposure for sources outside the normal once-per-second field sampling. */
    public static void expose(ServerPlayer player, double sieverts) {
        apply(player, new RadiationPhysics.Spectrum(Math.max(0, sieverts), 0, 0, 0), 0);
    }

    public static void sample(ServerPlayer player, RadiationPhysics.Spectrum external) {
        long now = RadiationSources.now(player.level());
        var carried = RadiationPhysics.Spectrum.ZERO;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            RadiationSources.stamp(stack, player.level());
            RadiationSources.shed(stack, player.level(), player.blockPosition());
            carried = carried.plus(RadiationSources.item(stack, now).scale(4));
        }
        var soil = RadiationContamination.at(player.serverLevel(), player.blockPosition());
        var old = player.getData(CERadiation.EXPOSURE);
        var next = old.copy();
        double coverage = protection(player);
        var pickup =
                soil.total() > 0
                        ? RadiationContamination.take(
                                player.serverLevel(), player.blockPosition(), .001)
                        : Radionuclides.ZERO;
        var skinMaterial =
                RadiationWorldData.read(old.getCompound("Skin"))
                        .at(now)
                        .plus(pickup.scale((1 - coverage) * .2), now);
        var shoeMaterial =
                RadiationWorldData.read(old.getCompound("Shoes"))
                        .at(now)
                        .plus(pickup.scale((1 - coverage) * .8), now);
        if (coverage > 0 && pickup.total() > 0)
            for (var worn : player.getArmorSlots())
                if (worn.getItem() instanceof ProtectiveClothingItem clothing)
                    RadiationContamination.stain(worn, pickup.scale(clothing.protection()), now);
        var cell = RadiationContamination.cell(player.blockPosition());
        if (old.contains("LastCell") && old.getLong("LastCell") != cell.asLong()) {
            RadiationContamination.deposit(
                    player.serverLevel(), player.blockPosition(), shoeMaterial.scale(.02));
            shoeMaterial = shoeMaterial.scale(.98);
        }
        next.put("Skin", RadiationWorldData.write(skinMaterial));
        next.put("Shoes", RadiationWorldData.write(shoeMaterial));
        next.putDouble("SkinBq", skinMaterial.total());
        next.putDouble("ShoeBq", shoeMaterial.total());
        next.putLong("LastCell", cell.asLong());
        double inhaledBq = soil.total() * 1e-8;
        double committed =
                soil.longLived() * 1e-8 * RadiationPhysics.ALPHA_INHALED_SV_PER_BQ
                        + (soil.shortLived() + soil.cesium())
                                * 1e-8
                                * RadiationPhysics.INHALED_SV_PER_BQ;
        if (mask(player)) {
            committed *= .02;
            if (inhaledBq > 0) {
                var mask = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                mask.setDamageValue(Math.min(mask.getMaxDamage(), mask.getDamageValue() + 1));
            }
        }
        next.putDouble("CommittedSv", safe(next, "CommittedSv") + committed);
        next.putDouble("InternalRemainingSv", safe(next, "InternalRemainingSv") + committed);
        var contact = RadiationSources.contamination(skinMaterial).scale(4);
        apply(player, external.plus(carried).plus(contact), coverage, next);
        if (player.isInWater()
                || RadiationField.get(player.serverLevel()).inWater(player.position()))
            RadiationContamination.wash(player, .2);
    }

    private static double safe(net.minecraft.nbt.CompoundTag tag, String key) {
        return Finite.of(tag.getDouble(key), 0, 0, 1e30);
    }

    public static void apply(ServerPlayer player, RadiationPhysics.Spectrum rate, double coverage) {
        apply(player, rate, coverage, player.getData(CERadiation.EXPOSURE).copy());
    }

    private static void apply(
            ServerPlayer player,
            RadiationPhysics.Spectrum rate,
            double coverage,
            net.minecraft.nbt.CompoundTag next) {
        var old = player.getData(CERadiation.EXPOSURE);
        double gamma = rate.gamma(),
                beta = rate.beta() * (1 - Math.clamp(coverage, 0, 1)),
                neutron = rate.neutron();
        double remaining = safe(next, "InternalRemainingSv"),
                delivered = remaining * (1 - RadiationPhysics.decay(1, 70 * 86400d));
        next.putDouble("InternalRemainingSv", remaining - delivered);
        double dose = gamma + beta + neutron + delivered;
        double lifetime = safe(old, "LifetimeSv");
        if (!old.getBoolean("Migrated")) {
            lifetime += Finite.of(player.getData(CENuclear.DOSE), 0, 0, 1e30);
            next.putBoolean("Migrated", true);
        }
        next.putDouble("AmbientBetaSvS", rate.beta());
        next.putDouble("LifetimeSv", lifetime + dose);
        next.putDouble("GammaSvS", gamma);
        next.putDouble("BetaSvS", beta);
        next.putDouble("NeutronSvS", neutron);
        next.putDouble("InternalSvS", delivered);
        // Slow protracted exposure contributes less to the acute injury state, while lifetime dose
        // always accumulates.
        double acute =
                Finite.of(player.getData(CENuclear.DOSE), 0, 0, 1e30)
                        * RadiationPhysics.decay(1, 7 * 86400d);
        acute += dose * Math.min(1, dose / (.1 / 3600));
        if (player.getData(CENuclear.DOSE) != acute) player.setData(CENuclear.DOSE, acute);
        if (!old.equals(next)) {
            player.setData(CERadiation.EXPOSURE, next);
            net.createplant.item.RadiationMeterItem.update(player);
        }
        int stage =
                acute >= 8
                        ? 5
                        : acute >= 6 ? 4 : acute >= 4 ? 3 : acute >= 2 ? 2 : acute >= 1 ? 1 : 0;
        if (player.isCreative() || player.isSpectator()) return;
        if (stage > player.getData(CENuclear.SICKNESS_STAGE))
            player.displayClientMessage(
                    Component.translatable("radiation.create_refinery_n_reactors.stage." + stage),
                    true);
        if (stage != player.getData(CENuclear.SICKNESS_STAGE))
            player.setData(CENuclear.SICKNESS_STAGE, stage);
        if (stage == 0) return;
        if (player.getRandom().nextInt(stage >= 2 ? 8 : 20) == 0)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
        if (stage >= 2) {
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, stage >= 3 ? 1 : 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0));
        }
        if (stage >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 1));
        }
        if (stage >= 4 && (stage >= 5 || player.tickCount % 100 == 0))
            player.hurt(sickness(player.level()), stage >= 5 ? 2 : 1);
    }

    private static DamageSource sickness(Level level) {
        return new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SICKNESS));
    }

    public static void clonePlayer(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            event.getEntity()
                    .setData(
                            CERadiation.EXPOSURE,
                            event.getOriginal().getData(CERadiation.EXPOSURE).copy());
            return;
        }
        var old = event.getOriginal().getData(CERadiation.EXPOSURE);
        var next = new net.minecraft.nbt.CompoundTag();
        next.putDouble("LifetimeSv", safe(old, "LifetimeSv"));
        next.putDouble("CommittedSv", safe(old, "CommittedSv"));
        next.putBoolean("Migrated", true);
        event.getEntity().setData(CERadiation.EXPOSURE, next);
    }
}
