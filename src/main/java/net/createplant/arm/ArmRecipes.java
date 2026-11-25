package net.createplant.arm;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

/** Loads data/&lt;namespace&gt;/assembly/*.json. Not under recipe/, where vanilla would try to parse the files as crafting recipes. */
public class ArmRecipes extends SimpleJsonResourceReloadListener {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, ArmRecipe> all = Map.of();
    private static volatile List<ArmBeltRecipe> belt = List.of();

    public ArmRecipes() {
        super(new Gson(), "assembly");
    }

    public static ArmRecipe get(ResourceLocation id) {
        return all.get(id);
    }

    /** All recipes in a fixed order, so cycling with a wrench is the same for everybody. */
    public static List<ArmRecipe> list() {
        return new ArrayList<>(all.values());
    }

    /** The belt work {@code tool} does on {@code stack}, or null. */
    public static ArmBeltRecipe belt(ArmTool tool, ItemStack stack) {
        for (ArmBeltRecipe r : belt) if (r.tool() == tool && stack.is(r.input())) return r;
        return null;
    }

    public static List<ArmBeltRecipe> beltList() {
        return belt;
    }

    public static Collection<ResourceLocation> ids() {
        return all.keySet();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ArmRecipe> loaded = new TreeMap<>();
        Map<ResourceLocation, ArmBeltRecipe> belts = new TreeMap<>();
        files.forEach((id, json) -> {
            try {
                // A file with an "input" is belt work; the others are staged jig assemblies.
                if (json.getAsJsonObject().has("input")) belts.put(id, ArmBeltRecipe.fromJson(id, json.getAsJsonObject()));
                else loaded.put(id, ArmRecipe.fromJson(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                LOGGER.error("Skipping assembly recipe {}: {}", id, e.getMessage());
            }
        });
        all = loaded;
        belt = List.copyOf(belts.values());
    }
}
