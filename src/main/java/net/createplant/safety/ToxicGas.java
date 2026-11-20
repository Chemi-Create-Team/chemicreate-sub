package net.createplant.safety;

import java.util.Map;
import java.util.Set;
import net.createplant.CreatePlant;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasCloudEvent;
import net.createplant.cloud.GasCloudStyles;
import net.createplant.cloud.GasClouds;
import net.createplant.registry.CESafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Toxic gases let out to the air by a flare, a gas holder, a vented reactor or a mishandled batch. They fill a {@link GasCloud} that
 * gives the living things breathing inside it the toxic gas effect; the cloud thins away with time, faster under the open sky. A gas
 * mask with filter left keeps the gas out, and each second it does so uses up a little of its filter.
 */
@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class ToxicGas {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePlant.location("toxic_gas"));
    /** The strength of a fluid's poisoning (0 irritant, 1 toxic, 2 the worst), whether its cloud fills from the floor, and its colour, or null for an unseen gas. */
    private record Gas(int strength, boolean floor, Integer colour) { }
    private static final Map<String, Gas> GASES = Map.ofEntries(
        Map.entry("ammonia_gas", new Gas(0, false, null)), Map.entry("liquid_ammonia", new Gas(0, false, null)),
        Map.entry("hot_synthesis_mixture", new Gas(0, false, null)), Map.entry("cold_synthesis_mixture", new Gas(0, false, null)),
        Map.entry("sulfur_dioxide", new Gas(0, true, null)), Map.entry("liquid_sulfur_dioxide", new Gas(0, true, null)), Map.entry("hydrogen_chloride", new Gas(0, true, null)),
        Map.entry("vinyl_chloride", new Gas(0, true, null)), Map.entry("liquid_vinyl_chloride", new Gas(0, true, null)), Map.entry("ethylene_oxide", new Gas(0, true, null)),
        Map.entry("nitric_oxide", new Gas(0, true, null)), Map.entry("hot_nitric_oxide_mixture", new Gas(0, true, null)),
        Map.entry("cold_nitric_oxide_mixture", new Gas(0, true, null)),
        // Tritium's external beta dose is small; this weakest cloud represents inhaled/internal contamination after a breach.
        Map.entry("tritium_gas", new Gas(0, false, null)),
        Map.entry("hydrogen_sulfide", new Gas(1, true, null)), Map.entry("chlorine", new Gas(1, true, 0xC8D85A)), Map.entry("liquid_chlorine", new Gas(1, true, 0xC8D85A)),
        Map.entry("converter_gas", new Gas(1, false, null)), Map.entry("carbon_monoxide", new Gas(1, false, null)),
        Map.entry("water_gas", new Gas(1, false, null)), Map.entry("purified_syngas", new Gas(1, false, null)),
        Map.entry("pressurised_carbon_monoxide", new Gas(1, false, null)),
        Map.entry("nitrogen_dioxide", new Gas(1, true, 0xA66E48)), Map.entry("sulfur_trioxide", new Gas(1, true, 0xE0DCC8)),
        Map.entry("hydrogen_fluoride", new Gas(1, false, null)), Map.entry("uranium_hexafluoride", new Gas(1, true, 0xE8E8D0)),
        Map.entry("enriched_uranium_hexafluoride", new Gas(1, true, 0xE8E8D0)), Map.entry("depleted_uranium_hexafluoride", new Gas(1, true, 0xE8E8D0)),
        Map.entry("compact_enriched_uranium_hexafluoride", new Gas(1, true, 0xE8E8D0)),
        // Carbon dioxide is heavier than air and harmful only in a high concentration, so it is the weakest, unseen cloud on the floor.
        Map.entry("carbon_dioxide", new Gas(0, true, null)),
        Map.entry("fluorine", new Gas(2, true, 0xE0E080)));
    /** Gases that only burn: where their unseen clouds settle. */
    private static final Map<String, Boolean> FUEL_FLOOR = Map.ofEntries(
        Map.entry("hydrogen_gas", false), Map.entry("pressurised_hydrogen", false), Map.entry("compressed_hydrogen", false),
        Map.entry("deuterium_gas", false), Map.entry("synthesis_gas", false), Map.entry("compressed_synthesis_feed", false),
        Map.entry("ethylene", true), Map.entry("propylene", true), Map.entry("hot_olefins", true),
        Map.entry("cooled_olefins", true), Map.entry("ft_offgas", true));
    private static final Set<String> CAUSTIC_SCRUBBABLE = Set.of("chlorine", "liquid_chlorine", "hydrogen_chloride", "hydrogen_sulfide", "sulfur_dioxide", "liquid_sulfur_dioxide",
        "sulfur_trioxide", "nitrogen_dioxide", "hydrogen_fluoride", "fluorine");
    public static final int EFFECT_TICKS = 60, OUTDOOR_DIVISOR = 3;
    private static final String TAG = CreatePlant.MOD_ID + ".toxic_gas";

    private ToxicGas() { }

    /** Gives every toxic and flammable gas, and the contaminated waste gas, its cloud: run while the mod is constructed, on both sides. */
    public static void registerCloudStyles() {
        GASES.forEach((path, gas) -> style(path, gas.floor(), gas.colour()));
        FUEL_FLOOR.forEach((path, floor) -> style(path, floor, null));
        style("contaminated_gas", true, null);
    }

    private static void style(String path, boolean floor, Integer colour) {
        GasCloudStyles.register(CreatePlant.MOD_ID + ":" + path, new GasCloudStyles.Style(!floor, colour, OUTDOOR_DIVISOR));
    }

    /** The poisoning strength of a fluid, or -1 for one that is harmless to breathe. */
    public static int strength(FluidStack fluid) {
        if (fluid.isEmpty()) return -1;
        if (net.createplant.chemistry.WasteGas.contaminated(fluid)) return (net.createplant.chemistry.WasteGas.of(fluid).hazards()>>2)-1;
        Gas gas = GASES.get(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath().replace("flowing_", ""));
        return gas == null ? -1 : gas.strength();
    }

    /** Acid gases that a caustic-soda scrubber can neutralise. Ammonia is deliberately absent because it is alkaline. */
    public static boolean causticScrubbable(FluidStack fluid) {
        return !fluid.isEmpty() && CAUSTIC_SCRUBBABLE.contains(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath().replace("flowing_", ""));
    }

    /** Lets a fluid out into the air at a place. A toxic or combustible gas makes one cloud that carries both hazards. */
    public static void release(Level level, BlockPos pos, FluidStack fluid) {
        net.createplant.nuclear.RadiationContamination.spill(level, pos, fluid);
        if (level instanceof ServerLevel server && (strength(fluid) >= 0 || FlammableGas.fuel(fluid))) GasClouds.release(server, pos, fluid);
    }

    /** Burning a toxic stream does not turn the flare into a chemical scrubber: what is left still poisons, but no longer burns. */
    public static void releasePoison(Level level,BlockPos pos,FluidStack fluid) {
        if (level instanceof ServerLevel server && strength(fluid) >= 0) GasClouds.release(server, pos, fluid, true);
    }

    /** Each second, the living things whose eyes are inside a toxic cloud breathe it. */
    @SubscribeEvent
    public static void breathe(GasCloudEvent.Sample event) {
        var cloud = event.cloud;
        int strength = strength(cloud.content());
        if (strength < 0) return;
        for (var living : cloud.level().getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox(),
                living -> living.isAlive() && cloud.contains(living.getEyePosition())))
            living.addEffect(new MobEffectInstance(CESafety.TOXIC_GAS, EFFECT_TICKS, strength));
    }

    /** Clouds from before the gas cloud entity: vanilla effect clouds carrying this tag, left to fade. */
    public static boolean toxic(AreaEffectCloud cloud) { return cloud.getTags().contains(TAG); }

    /** A gas mask with filter left keeps the gas off its wearer and uses a second of filter each time. */
    public static void maskKeepsGasOut(MobEffectEvent.Applicable event) {
        if (!event.getEffectInstance().getEffect().is(CESafety.TOXIC_GAS)) return;
        LivingEntity living = event.getEntity();
        ItemStack mask = living.getItemBySlot(EquipmentSlot.HEAD);
        if (!mask.is(CESafety.GAS_MASK.get()) || mask.getDamageValue() >= mask.getMaxDamage()) return;
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (!living.level().isClientSide()) mask.setDamageValue(mask.getDamageValue() + 1);
    }
}
