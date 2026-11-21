package net.createplant.chemistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.createplant.thermal.FluidIdentity;
import net.createplant.thermal.FluidRequirement;
import net.createplant.thermal.FluidState;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Bundled recipes for the existing fixed-batch machines. Both sides read the same table. */
final class SynthesisProcesses {
    private SynthesisProcesses() { }

    static List<ProcessRecipe> load(String file) {
        String resource = "/data/" + CreatePlant.MOD_ID + "/process/" + file + ".json";
        var source = SynthesisProcesses.class.getResourceAsStream(resource);
        if (source == null) throw new IllegalStateException("Missing process table " + resource);
        try (var reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
            var recipes = new ArrayList<ProcessRecipe>();
            var names = new HashSet<String>();
            for (var row : JsonParser.parseReader(reader).getAsJsonArray()) {
                var object = row.getAsJsonObject();
                String id = GsonHelper.getAsString(object, "id");
                if (!names.add(id)) throw new IllegalArgumentException("Duplicate process " + id);
                recipes.add(read(id, object));
            }
            return List.copyOf(recipes);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Invalid process table " + resource, ex);
        }
    }

    private static ProcessRecipe read(String id, JsonObject object) {
        var module = ProcessModule.valueOf(GsonHelper.getAsString(object, "machine"));
        int duration = GsonHelper.getAsInt(object, "ticks");
        var rows=GsonHelper.getAsJsonArray(object,"inputs");
        var conditions=rows.asList().stream().map(JsonElement::getAsJsonObject).map(SynthesisProcesses::requirement).toList();
        var inputs=fluids(rows,false);
        var outputs = fluids(GsonHelper.getAsJsonArray(object, "outputs"),true);
        var heat = HeatCondition.valueOf(GsonHelper.getAsString(object, "heat", "NONE"));
        var catalyst = Items.AIR;
        if (object.has("catalyst")) {
            catalyst = BuiltInRegistries.ITEM.get(ResourceLocation.parse(GsonHelper.getAsString(object, "catalyst")));
            if (catalyst == Items.AIR || module != ProcessModule.REACTION_VESSEL)
                throw new IllegalArgumentException("Invalid catalyst for " + id);
        }
        boolean valid = switch (module) {
            case ABSORPTION_TRAYS -> inputs.size() == 2 && outputs.size() <= 2;
            case REACTION_VESSEL -> inputs.size() <= 2 && outputs.size() <= 2;
            case FRACTION_SEPARATOR -> inputs.size() == 1 && outputs.size() < DistillationProfile.SLOTS && heat == HeatCondition.NONE;
            default -> false;
        };
        if (!valid || duration < 1 || inputs.isEmpty() || outputs.isEmpty())
            throw new IllegalArgumentException("Invalid machine, duration or ports for " + id);
        return new ProcessRecipe(id, module, duration, inputs, outputs, catalyst, heat,conditions);
    }

    private static FluidRequirement requirement(JsonObject entry) {
        if(entry.has("condition"))return FluidRequirement.CODEC.codec().parse(JsonOps.INSTANCE,entry.get("condition")).getOrThrow();
        return FluidIdentity.requirement(BuiltInRegistries.FLUID.get(ResourceLocation.parse(GsonHelper.getAsString(entry,"fluid"))));
    }

    private static List<FluidStack> fluids(JsonArray entries,boolean output) {
        return entries.asList().stream().map(JsonElement::getAsJsonObject).map(entry -> {
            String id = GsonHelper.getAsString(entry, "fluid");
            var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
            int amount = GsonHelper.getAsInt(entry, "amount");
            if (fluid == Fluids.EMPTY || amount < 1) throw new IllegalArgumentException("Invalid charge for " + id);
            var stack=new FluidStack(fluid,amount);
            if(!output) {
                var example=requirement(entry).example(stack);
                if(example.isEmpty())throw new IllegalArgumentException("Impossible fluid condition for "+id);
                return example;
            }
            int temperature=GsonHelper.getAsInt(entry,"temperature",FluidState.temperature(stack));
            int pressure=GsonHelper.getAsInt(entry,"pressure",FluidState.pressure(stack));
            if(temperature!=FluidState.clamp(temperature) || pressure!=FluidState.clamp(pressure))
                throw new IllegalArgumentException("Fluid state is outside 0–16 for "+id);
            return temperature==FluidState.temperature(stack) && pressure==FluidState.pressure(stack) ? stack : FluidState.with(stack,temperature,pressure);
        }).toList();
    }
}
