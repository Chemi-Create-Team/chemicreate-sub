package net.createplant.gametest;

import com.google.gson.*;
import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.gametest.*;

/** Every registered block, item and fluid of the mod ships its blockstate, models, textures, translations and, if it has an item, loot (the turbine casing drops its casing item in code), and every current item is in the creative tab. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class AuditGameTests {
    private static JsonObject json(String path) {
        try { return net.createplant.ResourceStack.object(AuditGameTests.class,path); }
        catch (Exception e) { return null; }
    }
    private static boolean exists(String path) { return net.createplant.ResourceStack.exists(AuditGameTests.class,path); }
    private static void model(String id,List<String> problems,Set<String> seen,String from) {
        if (!seen.add(id)) return;
        var loc=net.minecraft.resources.ResourceLocation.parse(id);
        if (!loc.getNamespace().equals(CreatePlant.MOD_ID)) return;
        var m=json("/assets/"+loc.getNamespace()+"/models/"+loc.getPath()+".json");
        if (m==null) { problems.add("model "+id+" ("+from+")");return; }
        if (m.has("parent")) model(m.get("parent").getAsString().contains(":") ? m.get("parent").getAsString() : "minecraft:"+m.get("parent").getAsString(),problems,seen,id);
        if (m.has("textures")) for (var t:m.getAsJsonObject("textures").entrySet()) {
            String v=t.getValue().getAsString();if (v.startsWith("#")) continue;
            var tl=net.minecraft.resources.ResourceLocation.parse(v.contains(":") ? v : "minecraft:"+v);
            if (tl.getNamespace().equals(CreatePlant.MOD_ID) && !exists("/assets/"+tl.getNamespace()+"/textures/"+tl.getPath()+".png")) problems.add("texture "+tl+" ("+id+")");
        }
    }
    private static void collect(JsonElement e,Set<String> models) {
        if (e.isJsonObject()) { var o=e.getAsJsonObject();if (o.has("model") && o.get("model").isJsonPrimitive()) models.add(o.get("model").getAsString());for (var c:o.entrySet()) collect(c.getValue(),models); }
        else if (e.isJsonArray()) for (var c:e.getAsJsonArray()) collect(c,models);
    }
    @GameTest(template="test_empty")
    public static void everyRegisteredEntryHasAssetsTranslationsAndLoot(GameTestHelper h) {
        var problems=new ArrayList<String>();var seen=new HashSet<String>();
        var en=json("/assets/"+CreatePlant.MOD_ID+"/lang/en_us.json");var ja=json("/assets/"+CreatePlant.MOD_ID+"/lang/ja_jp.json");
        for (var entry:BuiltInRegistries.BLOCK.entrySet()) {
            var id=entry.getKey().location();if (!id.getNamespace().equals(CreatePlant.MOD_ID)) continue;var block=entry.getValue();
            var state=json("/assets/"+CreatePlant.MOD_ID+"/blockstates/"+id.getPath()+".json");
            if (state==null) problems.add("blockstate "+id);
            else { var models=new TreeSet<String>();collect(state,models);for (var m:models) model(m.contains(":") ? m : "minecraft:"+m,problems,seen,"blockstate "+id); }
            if (!en.has(block.getDescriptionId())) problems.add("en "+block.getDescriptionId());
            if (!ja.has(block.getDescriptionId())) problems.add("ja "+block.getDescriptionId());
            if (block.asItem()!=net.minecraft.world.item.Items.AIR && block!=CEBlocks.HIGH_PRESSURE_TURBINE_PART.get() && h.getLevel().getServer().reloadableRegistries().getLootTable(block.getLootTable())==net.minecraft.world.level.storage.loot.LootTable.EMPTY) problems.add("loot "+id);
        }
        for (var entry:BuiltInRegistries.ITEM.entrySet()) {
            var id=entry.getKey().location();if (!id.getNamespace().equals(CreatePlant.MOD_ID)) continue;var item=entry.getValue();
            var m=json("/assets/"+CreatePlant.MOD_ID+"/models/item/"+id.getPath()+".json");
            if (m==null) problems.add("item model "+id);else model(id.getNamespace()+":item/"+id.getPath(),problems,seen,"item "+id);
            if (!(item instanceof BlockItem)) { if (!en.has(item.getDescriptionId())) problems.add("en "+item.getDescriptionId());if (!ja.has(item.getDescriptionId())) problems.add("ja "+item.getDescriptionId()); }
        }
        for (var entry:net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.entrySet()) {
            var id=entry.getKey().location();if (!id.getNamespace().equals(CreatePlant.MOD_ID)) continue;
            String key=entry.getValue().getDescriptionId();
            if (!en.has(key)) problems.add("en "+key);if (!ja.has(key)) problems.add("ja "+key);
        }
        for (var key:en.keySet()) if (!ja.has(key)) problems.add("ja missing "+key);
        for (var key:ja.keySet()) if (!en.has(key)) problems.add("en missing "+key);
        var shown=new HashSet<net.minecraft.world.item.Item>();
        for (var tab:BuiltInRegistries.CREATIVE_MODE_TAB) { tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess()));for (var s:tab.getDisplayItems()) shown.add(s.getItem()); }
        var hidden=new TreeSet<String>();for (var e:BuiltInRegistries.ITEM.entrySet()) if (e.getKey().location().getNamespace().equals(CreatePlant.MOD_ID) && !shown.contains(e.getValue())) hidden.add(e.getKey().location().getPath());
        // Hidden legacy gas and steam buckets keep their registered assets.
        hidden.removeAll(Set.of("steam_bucket", "raw_process_gas_bucket"));hidden.removeIf(path->path.endsWith("_large_flywheel"));
        h.assertTrue(hidden.isEmpty(),"Items missing from the creative tab: "+hidden);
        h.assertTrue(problems.isEmpty(),"Missing assets, translations or loot: "+problems);
        h.succeed();
    }
}
