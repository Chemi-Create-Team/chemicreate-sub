package net.createplant.chemistry;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.mixture.MixtureHelper;
import net.createplant.safety.FlammableGas;
import net.createplant.safety.ToxicGas;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidChemicalRegistry {
    public enum ChemicalHazard { FLAMMABLE, CORROSIVE, TOXIC, HIGH_VISCOSITY, REFRIGERANT, OXIDIZER }

    public record ChemicalFluidInfo(String formula, List<ChemicalHazard> hazards, boolean aqueous, String compositionKey) {
        public ChemicalFluidInfo {
            if (formula.isBlank()) throw new IllegalArgumentException("Empty fluid formula");
            hazards = List.copyOf(hazards);
        }

        public String solutes() {
            return String.join(" + ", java.util.Arrays.stream(formula.split(" \\+ ")).filter(part -> !part.equals("H₂O")).toList());
        }

        public boolean mixture() {
            return aqueous || !compositionKey.isEmpty() || formula.contains(" + ") || formula.contains("…")
                || formula.contains("–") || formula.contains("ₙ") || formula.contains("ₘ");
        }
    }

    private static final Map<ResourceLocation, ChemicalFluidInfo> DATA = load();

    private FluidChemicalRegistry() { }

    private static Map<ResourceLocation, ChemicalFluidInfo> load() {
        String file = "/data/" + CreatePlant.MOD_ID + "/chemistry/fluids.json";
        var entries = new HashMap<ResourceLocation, ChemicalFluidInfo>();
        try (var stream = FluidChemicalRegistry.class.getResourceAsStream(file)) {
            if (stream == null) throw new IllegalStateException("Missing " + file);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var row : json.entrySet()) {
                var id = row.getKey().contains(":") ? ResourceLocation.parse(row.getKey()) : CreatePlant.location(row.getKey());
                var data = row.getValue().getAsJsonObject();
                var hazards = data.getAsJsonArray("hazards").asList().stream()
                    .map(value -> ChemicalHazard.valueOf(value.getAsString())).toList();
                entries.put(id, new ChemicalFluidInfo(data.get("formula").getAsString(), hazards,
                    data.has("aqueous") && data.get("aqueous").getAsBoolean(),
                    data.has("composition") ? data.get("composition").getAsString() : ""));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
        return Map.copyOf(entries);
    }

    public static ChemicalFluidInfo base(Fluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        return DATA.get(id.withPath(id.getPath().replaceFirst("^flowing_", "")));
    }

    public static ChemicalFluidInfo of(FluidStack stack) {
        if (stack.isEmpty()) return null;
        var info = base(stack.getFluid());
        if (info == null) return null;
        var hazards = EnumSet.noneOf(ChemicalHazard.class);
        hazards.addAll(info.hazards());
        if (FluidHazards.of(stack).corrosive()) hazards.add(ChemicalHazard.CORROSIVE);
        if (FlammableGas.fuel(stack)) hazards.add(ChemicalHazard.FLAMMABLE);
        if (ToxicGas.strength(stack) >= 0) hazards.add(ChemicalHazard.TOXIC);
        if (stack.getFluidType().getViscosity() >= 3000) hazards.add(ChemicalHazard.HIGH_VISCOSITY);
        if (WasteGas.contaminated(stack) && (WasteGas.of(stack).hazards() & WasteGas.OXIDIZER) != 0) hazards.add(ChemicalHazard.OXIDIZER);
        var formula = info.formula();
        if (MixtureHelper.isMixture(stack)) {
            var components = new LinkedHashSet<String>();
            for (var part : MixtureHelper.components(stack)) {
                var partInfo = of(part);
                if (partInfo != null) {
                    hazards.addAll(partInfo.hazards());
                    components.add(partInfo.formula());
                } else components.add("…");
            }
            if (!components.isEmpty()) {
                formula = String.join(" + ", components.stream().limit(3).toList());
                if (components.size() > 3) formula += " + …";
            }
        }
        return new ChemicalFluidInfo(formula, List.copyOf(hazards), info.aqueous(), info.compositionKey());
    }
}
