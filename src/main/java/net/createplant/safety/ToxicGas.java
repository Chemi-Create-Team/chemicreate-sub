package net.createplant.safety;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.createplant.CreatePlant;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Toxic gases let out to the air by a flare, a gas holder, a vented reactor or a mishandled batch. They hang as a cloud that poisons
 * the living things inside it; the cloud thins away with time, three times faster under the open sky. A gas mask with filter left keeps
 * the gas out, and each second it does so uses up a little of its filter.
 */
public final class ToxicGas {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePlant.location("toxic_gas"));
    /** Fluid name, the strength of its poisoning (0 irritant, 1 toxic, 2 the worst) and the colour of its cloud. */
    private record Gas(int strength, int colour) { }
    private static final Map<String, Gas> GASES = Map.ofEntries(
        Map.entry("ammonia_gas", new Gas(0, 0xD8E0E0)), Map.entry("liquid_ammonia", new Gas(0, 0xD8E0E0)),
        Map.entry("hot_synthesis_mixture", new Gas(0, 0xD8E0E0)), Map.entry("cold_synthesis_mixture", new Gas(0, 0xD8E0E0)),
        Map.entry("sulfur_dioxide", new Gas(0, 0xD8D4B8)), Map.entry("liquid_sulfur_dioxide", new Gas(0, 0xD8D4B8)), Map.entry("hydrogen_chloride", new Gas(0, 0xE0E8E0)),
        Map.entry("vinyl_chloride", new Gas(0, 0xE8E8E0)), Map.entry("liquid_vinyl_chloride", new Gas(0, 0xE8E8E0)), Map.entry("ethylene_oxide", new Gas(0, 0xE0E0E8)),
        Map.entry("nitric_oxide", new Gas(0, 0xC8D4DC)), Map.entry("hot_nitric_oxide_mixture", new Gas(0, 0xC8D4DC)),
        Map.entry("cold_nitric_oxide_mixture", new Gas(0, 0xC8D4DC)),
        // Tritium's external beta dose is small; this weakest cloud represents inhaled/internal contamination after a breach.
        Map.entry("tritium_gas", new Gas(0, 0xA8D898)),
        Map.entry("hydrogen_sulfide", new Gas(1, 0xC8C890)), Map.entry("chlorine", new Gas(1, 0xC8D85A)), Map.entry("liquid_chlorine", new Gas(1, 0xC8D85A)),
        Map.entry("converter_gas", new Gas(1, 0xAEB5AA)), Map.entry("carbon_monoxide", new Gas(1, 0xAEB5AA)),
        Map.entry("water_gas", new Gas(1, 0xAEB5AA)),
        Map.entry("nitrogen_dioxide", new Gas(1, 0xA66E48)), Map.entry("sulfur_trioxide", new Gas(1, 0xE0DCC8)),
        Map.entry("hydrogen_fluoride", new Gas(1, 0xE0E8E8)), Map.entry("uranium_hexafluoride", new Gas(1, 0xE8E8D0)),
        Map.entry("enriched_uranium_hexafluoride", new Gas(1, 0xE8E8D0)), Map.entry("depleted_uranium_hexafluoride", new Gas(1, 0xE8E8D0)),
        Map.entry("fluorine", new Gas(2, 0xE0E080)));
    private static final Set<String> CAUSTIC_SCRUBBABLE = Set.of("chlorine", "liquid_chlorine", "hydrogen_chloride", "hydrogen_sulfide", "sulfur_dioxide", "liquid_sulfur_dioxide",
        "sulfur_trioxide", "nitrogen_dioxide", "hydrogen_fluoride", "fluorine");
    public static final int EFFECT_TICKS = 60, MIN_TICKS = 400, MAX_TICKS = 2400, OUTDOOR_DIVISOR = 3;
    public static final float MIN_RADIUS = 1.5f, MAX_RADIUS = 6;
    private static final double MERGE_DISTANCE = 1.5;
    private static final String TAG = CreatePlant.MOD_ID + ".toxic_gas";

    private ToxicGas() { }

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

    /**
     * Lets a fluid out into the air at a place. A combustible fluid makes a flammable cloud; a toxic one also makes or feeds an inhalation cloud.
     * The cloud reaches 1.5 blocks, wider with the square root of the amount up to 6, and lasts 20 seconds and a tick for every 5 mB, up to two
     * minutes; under the open sky a third of that.
     */
    public static void release(Level level, BlockPos pos, FluidStack fluid) {
        net.createplant.nuclear.RadiationContamination.spill(level, pos, fluid);
        FlammableGas.release(level,pos,fluid);
        releasePoison(level,pos,fluid);
    }

    /** Burning a toxic stream does not turn the flare into a chemical scrubber. */
    public static void releasePoison(Level level,BlockPos pos,FluidStack fluid) {
        int strength = strength(fluid);
        if (strength < 0 || !(level instanceof ServerLevel server)) return;
        Gas gas = GASES.get(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath().replace("flowing_", ""));
        float radius = (float) Math.clamp(MIN_RADIUS + 1.5 * Math.sqrt(fluid.getAmount() / 1000d), MIN_RADIUS, MAX_RADIUS);
        int ticks = Math.clamp(MIN_TICKS + fluid.getAmount() / 5, MIN_TICKS, MAX_TICKS) / (server.canSeeSky(pos.above()) ? OUTDOOR_DIVISOR : 1);
        double x = pos.getX() + .5, y = pos.getY() + .2, z = pos.getZ() + .5;
        for (var cloud : server.getEntitiesOfClass(AreaEffectCloud.class, new net.minecraft.world.phys.AABB(pos).inflate(MERGE_DISTANCE), ToxicGas::toxic)) {
            cloud.setRadius(Math.max(cloud.getRadius(), radius));
            cloud.setDuration(Math.max(cloud.getDuration(), cloud.tickCount + ticks));
            cloud.setRadiusPerTick(-cloud.getRadius() / 2 / Math.max(1, cloud.getDuration() - cloud.tickCount));
            return;
        }
        var cloud = new AreaEffectCloud(server, x, y, z);
        cloud.setRadius(radius);
        cloud.setDuration(ticks);
        // The cloud thins to half its reach by the time it is gone.
        cloud.setRadiusPerTick(-radius / 2 / ticks);
        cloud.setWaitTime(0);
        cloud.setPotionContents(new PotionContents(Optional.empty(), Optional.of(gas==null ? 0x90856F : gas.colour()), List.of(new MobEffectInstance(CESafety.TOXIC_GAS, EFFECT_TICKS, strength))));
        cloud.addTag(TAG);
        server.addFreshEntity(cloud);
    }

    /** The clouds of this gas carry an entity tag, saved with them, so later leaks nearby feed them. */
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
