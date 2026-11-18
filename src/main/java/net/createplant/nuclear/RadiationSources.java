package net.createplant.nuclear;

import net.createplant.registry.CERadiation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

/** Radioactivity is a material property; storage and transport use this same table. */
public final class RadiationSources {
    public record Profile(
            double gamma, double beta, double bq, double shortShare, double cesiumShare) {
        public Profile {
            gamma = Finite.of(gamma, 0, 0, 1e30);
            beta = Finite.of(beta, 0, 0, 1e30);
            bq = Finite.of(bq, 1, 1e-30, 1e30);
            shortShare = Finite.of(shortShare, 0, 0, 1);
            cesiumShare = Finite.of(cesiumShare, 0, 0, 1 - shortShare);
        }

        public Radionuclides fresh(long tick) {
            return new Radionuclides(
                    bq * shortShare, bq * cesiumShare, bq * (1 - shortShare - cesiumShare), tick);
        }

        public RadiationPhysics.Spectrum spectrum(Radionuclides material, long tick) {
            var decayed = material.at(tick);
            double emitting = decayed.shortLived() + decayed.cesium();
            double ratio =
                    shortShare + cesiumShare > 0 ? emitting / (bq * (shortShare + cesiumShare)) : 0;
            return new RadiationPhysics.Spectrum(
                    gamma * ratio, beta * ratio, 0, material.at(tick).total());
        }
    }

    private static final Map<ResourceLocation, Profile> ITEMS = new HashMap<>(),
            FLUIDS = new HashMap<>(),
            BLOCKS = new HashMap<>();

    static {
        var fuel = new Profile(5000d / 3600, 500d / 3600, 1e15, .85, .149);
        items(fuel, "spent_fuel_rod");
        items(
                new Profile(50d / 3600, 5d / 3600, 1e13, .8, .199),
                "spent_control_rod",
                "spent_blanket_cartridge",
                "irradiated_cartridge",
                "spent_divertor_cassette");
        items(new Profile(5d / 3600, .5 / 3600, 1e12, .95, .049), "irradiated_lithium_target_rod");
        items(new Profile(500d / 3600, 50d / 3600, 1e14, .85, .149), "chopped_spent_fuel");
        items(new Profile(5d / 3600, .5 / 3600, 1e12, .8, .199), "spent_cladding_hulls");
        var glass = new Profile(100d / 3600, 0, 1e14, .2, .799);
        items(glass, "vitrified_waste_canister");
        registerBlock(id("vitrified_waste_canister"), glass);
        var uranium = new Profile(0, 0, 5e8, 0, 0);
        items(
                uranium,
                "fuel_rod",
                "compact_enriched_fuel_rod",
                "mox_fuel_rod",
                "yellowcake",
                "uraninite",
                "uranium_ore",
                "deepslate_uranium_ore",
                "uranium_dioxide",
                "enriched_uranium_dioxide",
                "uranium_trioxide",
                "uranium_tetrafluoride",
                "compact_uranium_dioxide",
                "green_uranium_pellet",
                "uranium_pellet",
                "green_compact_uranium_pellet",
                "compact_uranium_pellet");
        for (var name :
                java.util.List.of(
                        "uranyl_nitrate_solution",
                        "uranium_hexafluoride",
                        "enriched_uranium_hexafluoride",
                        "compact_enriched_uranium_hexafluoride",
                        "depleted_uranium_hexafluoride")) registerFluid(id(name), uranium);
        BLOCKS.put(id("uranium_ore"), uranium);
        BLOCKS.put(id("deepslate_uranium_ore"), uranium);
        registerFluid(
                id("high_level_raffinate"), new Profile(500d / 3600, 50d / 3600, 1e14, .8, .199));
        registerFluid(id("dissolved_spent_fuel"), fuel);
    }

    private RadiationSources() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_refinery_n_reactors", path);
    }

    private static void items(Profile p, String... names) {
        for (String name : names) registerItem(id(name), p);
    }

    public static void registerItem(ResourceLocation id, Profile profile) {
        ITEMS.put(id, profile);
    }

    public static void registerFluid(ResourceLocation id, Profile profile) {
        FLUIDS.put(id, profile);
        BLOCKS.put(id, profile);
    }

    public static void registerBlock(ResourceLocation id, Profile profile) {
        BLOCKS.put(id, profile);
    }

    public static Profile profile(ItemStack stack) {
        return stack.isEmpty() ? null : ITEMS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static Profile profile(FluidStack stack) {
        if (stack.isEmpty()) return null;
        var key = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return FLUIDS.get(
                ResourceLocation.fromNamespaceAndPath(
                        key.getNamespace(), key.getPath().replace("flowing_", "")));
    }

    public static Profile profile(BlockState state) {
        if (state.hasProperty(net.createplant.block.VitrificationCanisterBlock.SECTION)
                && state.getValue(net.createplant.block.VitrificationCanisterBlock.SECTION) != 0)
            return null;
        return BLOCKS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static long now(Level level) {
        return level.getServer() == null
                ? level.getGameTime()
                : level.getServer().overworld().getGameTime();
    }

    public static ItemStack stamp(ItemStack stack, Level level) {
        var p = profile(stack);
        if (p != null && (p.gamma() > 0 || p.beta() > 0) && !stack.has(CERadiation.MATERIAL.get()))
            stack.set(CERadiation.MATERIAL.get(), p.fresh(now(level)));
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem) {
            var contained = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack);
            if (contained.isPresent()
                    && profile(contained.get()) != null
                    && !contained.get().has(CERadiation.MATERIAL.get())) {
                var fluid = contained.get();
                stamp(fluid);
                stack.set(
                        net.createplant.registry.CEWorldFluids.BUCKET_CONTENTS.get(),
                        net.createplant.item.BucketFluidContents.of(fluid));
            }
        }
        return stack;
    }

    public static void stamp(FluidStack fluid) {
        var profile = profile(fluid);
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (profile != null
                && server != null
                && server.isSameThread()
                && !fluid.has(CERadiation.MATERIAL.get()))
            fluid.set(CERadiation.MATERIAL.get(), profile.fresh(server.overworld().getGameTime()));
    }

    public static void shed(ItemStack stack, Level level, net.minecraft.core.BlockPos pos) {
        var profile = profile(stack);
        if (profile == null
                || profile.gamma() != 0
                || !(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!(name.equals("yellowcake")
                || name.equals("uraninite")
                || name.equals("uranium_trioxide")
                || name.endsWith("uranium_dioxide")
                || name.equals("uranium_tetrafluoride")
                || name.endsWith("dust")
                || name.endsWith("powder"))) return;
        // The shed fraction is far below what a stack could show, so the stack stays as it was and still stacks with fresh ore.
        var material =
                stack.getOrDefault(CERadiation.MATERIAL.get(), profile.fresh(now(level)))
                        .at(now(level));
        RadiationContamination.deposit(server, pos, material.scale(1e-9 * stack.getCount()));
    }

    public static Radionuclides material(FluidStack stack, long now) {
        var p = profile(stack);
        return p == null
                ? Radionuclides.ZERO
                : stack.getOrDefault(CERadiation.MATERIAL.get(), p.fresh(now));
    }

    public static RadiationPhysics.Spectrum item(ItemStack stack, long now) {
        if (stack.isEmpty()) return RadiationPhysics.Spectrum.ZERO;
        var p = profile(stack);
        var base =
                p == null
                        ? RadiationPhysics.Spectrum.ZERO
                        : p.spectrum(
                                        stack.getOrDefault(
                                                CERadiation.MATERIAL.get(), p.fresh(now)),
                                        now)
                                .scale(stack.getCount());
        var soil = stack.getOrDefault(CERadiation.SURFACE.get(), Radionuclides.ZERO).at(now);
        var result = base.plus(contamination(soil).scale(stack.getCount()));
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem) {
            var bucket = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack);
            if (bucket.isPresent()) result = result.plus(fluid(bucket.get(), now));
        }
        var container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null)
            for (var item : container.nonEmptyItems()) result = result.plus(item(item, now));
        var block = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        return block == null
                ? result
                : result.plus(nested(block.copyTag(), now, 0, new int[] {4096}));
    }

    private static RadiationPhysics.Spectrum nested(
            net.minecraft.nbt.Tag raw, long now, int depth, int[] budget) {
        if (depth > 16 || budget[0]-- <= 0) return RadiationPhysics.Spectrum.ZERO;
        var result = RadiationPhysics.Spectrum.ZERO;
        if (raw instanceof net.minecraft.nbt.CompoundTag tag) {
            var id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null && id.equals(net.createplant.CreatePlant.location("chemical_mixture")) && tag.contains("amount")) {
                var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if(server!=null)return FluidStack.parse(server.registryAccess(),tag).map(stack->fluid(stack,now)).orElse(RadiationPhysics.Spectrum.ZERO);
            }
            if (id != null && tag.contains("amount") && FLUIDS.containsKey(id)) {
                var p = FLUIDS.get(id);
                // Parse the material directly; component codecs can require a live registry
                // provider.
                var component =
                        tag.getCompound("components")
                                .getCompound("create_refinery_n_reactors:radionuclides");
                var data =
                        Radionuclides.CODEC
                                .parse(net.minecraft.nbt.NbtOps.INSTANCE, component)
                                .result()
                                .orElse(p.fresh(now));
                return p.spectrum(data, now).scale(tag.getInt("amount") / 1000d);
            }
            if (id != null && tag.contains("count") && ITEMS.containsKey(id)) {
                var p = ITEMS.get(id);
                var component =
                        tag.getCompound("components")
                                .getCompound("create_refinery_n_reactors:radionuclides");
                return p.spectrum(
                                Radionuclides.CODEC
                                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, component)
                                        .result()
                                        .orElse(p.fresh(now)),
                                now)
                        .scale(tag.getInt("count"));
            }
            for (var key : tag.getAllKeys())
                result = result.plus(nested(tag.get(key), now, depth + 1, budget));
        } else if (raw instanceof net.minecraft.nbt.ListTag list)
            for (var child : list) result = result.plus(nested(child, now, depth + 1, budget));
        return result;
    }

    public static RadiationPhysics.Spectrum fluid(FluidStack stack, long now) {
        if (net.createplant.mixture.MixtureHelper.isMixture(stack)) {
            var total = RadiationPhysics.Spectrum.ZERO;
            for (var component : net.createplant.mixture.MixtureHelper.components(stack)) total = total.plus(fluid(component, now));
            return total;
        }
        var p = profile(stack);
        return p == null
                ? RadiationPhysics.Spectrum.ZERO
                : p.spectrum(material(stack, now), now).scale(stack.getAmount() / 1000d);
    }

    public static RadiationPhysics.Spectrum contamination(Radionuclides soil) {
        return new RadiationPhysics.Spectrum(
                soil.cesium() * RadiationPhysics.GAMMA_PER_SURFACE_BQ
                        + soil.shortLived() * RadiationPhysics.GAMMA_PER_SURFACE_BQ,
                (soil.shortLived() + soil.cesium()) * RadiationPhysics.BETA_PER_SURFACE_BQ,
                0,
                soil.total());
    }

    public static void mix(
            FluidStack result, FluidStack first, FluidStack second, int added, long now) {
        if (profile(result) == null) return;
        double amount = first.getAmount() + (double) added;
        if (amount <= 0) return;
        result.set(
                CERadiation.MATERIAL.get(),
                material(first, now)
                        .scale(first.getAmount() / amount)
                        .plus(material(second, now).scale(added / amount), now));
    }
}
