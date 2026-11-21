package net.createplant.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;

final class MoleculeStructures {
    record Atom(String element, float x, float y, float depth) { }
    record Bond(Atom start, Atom end, int order) { }
    record Drawing(List<Atom> atoms, List<Bond> bonds) { }
    record Model(String formula, Drawing flat, Drawing spatial) { }
    private record Catalog(Map<ResourceLocation, Model> fluids, Map<String, Model> namedComponents) { }

    private static final Catalog CATALOG = load();

    private MoleculeStructures() { }

    static Model forFluid(FluidStack stack) {
        var fluid = stack.getFluid() instanceof FlowingFluid flowing ? flowing.getSource() : stack.getFluid();
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        var model = CATALOG.fluids.get(id);
        if (model != null) return model;
        var info = FluidChemicalRegistry.base(fluid);
        if (info == null || !info.formula().contains(" + ")) return null;
        for (String component : info.formula().split(" \\+ ")) {
            model = CATALOG.namedComponents.get(component);
            if (model != null) return model;
        }
        return null;
    }

    private static Catalog load() {
        var file = "/data/" + CreatePlant.MOD_ID + "/chemistry/molecule_structures.json";
        var fluids = new HashMap<ResourceLocation, Model>();
        var components = new HashMap<String, Model>();
        try (var source = MoleculeStructures.class.getResourceAsStream(file)) {
            if (source == null) throw new IllegalStateException("Missing " + file);
            var rows = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var value : rows) {
                var row = value.getAsJsonArray();
                var formula = row.get(0).getAsString();
                var model = new Model(formula, drawing(row.get(3).getAsJsonArray(), row.get(4).getAsJsonArray(), false),
                    drawing(row.get(5).getAsJsonArray(), row.get(6).getAsJsonArray(), true));
                if (row.get(2).getAsBoolean()) components.put(formula, model);
                for (var id : row.get(1).getAsJsonArray()) fluids.put(ResourceLocation.parse(id.getAsString()), model);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
        return new Catalog(Map.copyOf(fluids), Map.copyOf(components));
    }

    private static Drawing drawing(JsonArray points, JsonArray joins, boolean spatial) {
        var atoms = new ArrayList<Atom>();
        for (var value : points) {
            var point = value.getAsJsonArray();
            atoms.add(new Atom(point.get(0).getAsString(), point.get(1).getAsFloat(), point.get(2).getAsFloat(),
                spatial ? point.get(3).getAsFloat() : 0));
        }
        var bonds = new ArrayList<Bond>();
        for (var value : joins) {
            var join = value.getAsJsonArray();
            bonds.add(new Bond(atoms.get(join.get(0).getAsInt()), atoms.get(join.get(1).getAsInt()), join.get(2).getAsInt()));
        }
        if (spatial) {
            atoms.sort(Comparator.comparingDouble(Atom::depth));
            bonds.sort(Comparator.comparingDouble(bond -> (bond.start().depth() + bond.end().depth()) / 2));
        }
        return new Drawing(List.copyOf(atoms), List.copyOf(bonds));
    }
}
