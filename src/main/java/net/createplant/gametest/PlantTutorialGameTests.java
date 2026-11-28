package net.createplant.gametest;

import java.util.HashSet;
import net.createplant.CreatePlant;
import net.createplant.ponder.PlantTutorialCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the actual registered blocks: static JSON validation alone cannot catch unknown properties. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantTutorialGameTests {
    @GameTest(template = "test_petrochemical")
    public static void everyTutorialUsesRegisteredComponentsAndValidBlockStates(GameTestHelper helper) throws Exception {
        var ids = new HashSet<String>();
        var issues = new java.util.ArrayList<String>();
        helper.assertTrue(PlantTutorialCatalog.all().size() >= 29, "All legacy and modular storyboards must ship");
        for (var tutorial : PlantTutorialCatalog.all()) {
            helper.assertTrue(ids.add(tutorial.id()), "Tutorial identifiers must be unique");
            for (String name : tutorial.components()) {
                helper.assertTrue(BuiltInRegistries.ITEM.get(name.contains(":") ? net.minecraft.resources.ResourceLocation.parse(name) : CreatePlant.location(name)) != Items.AIR,
                    "Ponder component must be a real registered item: " + name);
            }
            String path = "/assets/" + CreatePlant.MOD_ID + "/ponder/" + tutorial.storyboard() + ".nbt";
            try (var stream = net.createplant.ResourceStack.open(PlantTutorialCatalog.class, path)) {
                helper.assertTrue(stream != null, "Missing structure: " + path);
                var structure = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                issues.addAll(structureIssues(structure, tutorial.id()));
                if (tutorial.id().equals("open_tank") || tutorial.id().startsWith("open_tank_size_")) {
                    var palette = structure.getList("palette", Tag.TAG_COMPOUND);
                    boolean hasCasing = false;
                    boolean hasIron = false;
                    for (int i = 0; i < palette.size(); i++) {
                        String name = palette.getCompound(i).getString("Name");
                        hasCasing |= name.equals(CreatePlant.MOD_ID + ":tank_casing");
                        hasIron |= name.equals("minecraft:iron_block");
                    }
                    helper.assertTrue(hasCasing && !hasIron, "Tank tutorial structures use tank casing blocks");
                }
            }
            for (var step : tutorial.steps()) {
                if (step.item() != null) helper.assertTrue(BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item())) != Items.AIR,
                    "Illustrative material must exist: " + step.item());
            }
        }
        helper.assertTrue(issues.isEmpty(), String.join("; ", issues));
        helper.succeed();
    }
    /** Storyboards the addons ship; they are absent while the addon is not installed. */
    private static final java.util.Set<String> ADDON_STORYBOARDS = java.util.Set.of("crane/crane_refuelling", "crane/gantry_crane", "crane/quarry_frame",
        "nuclear/fuel_pool", "nuclear/fusion_console", "nuclear/fusion_motor_generator", "nuclear/fusion_tokamak", "nuclear/rbmk_compact_core",
        "nuclear/rbmk_control_rods", "nuclear/rbmk_control_room", "nuclear/rbmk_core", "plant/facility_constructor", "plant/gas_centrifuge",
        "plant/cubic_press", "plant/fuel_cycle", "plant/fusion_maintenance", "plant/helium_cold_box");
    @GameTest(template = "test_petrochemical")
    public static void originalStoryboardsStillUseValidRegisteredBlockStates(GameTestHelper helper) throws Exception {
        var issues = new java.util.ArrayList<String>();
        for (String path : java.util.List.of(
            "chemistry/ammonia_separator",
            "chemistry/reciprocating_compressor",
            "crane/crane_refuelling",
            "crane/gantry_crane",
            "crane/quarry_frame",
            "nuclear/fuel_pool",
            "nuclear/fusion_console",
            "nuclear/fusion_motor_generator",
            "nuclear/fusion_tokamak",
            "nuclear/rbmk_compact_core",
            "nuclear/rbmk_control_rods",
            "nuclear/rbmk_control_room",
            "nuclear/rbmk_core",
            "plant/facility_constructor",
            "plant/reaction_tower",
            "plant/reaction_tower_separator",
                        "plant/large_absorption_tower",
            "plant/gas_centrifuge",
            "plant/cubic_press",
            "plant/condenser_loop",
            "plant/diorite_casing",
            "plant/electrolysis_cell",
            "plant/ore_recovery",
            "plant/electrowinning",
            "plant/electric_pump",
            "plant/flare_stack",
            "plant/fractionating_column",
            "plant/fractionating_column_air",
            "plant/fractionating_column_binary",
            "plant/fuel_cycle",
            "plant/fusion_maintenance",
            "plant/gas_bell",
            "plant/helium_cold_box",
            "plant/horizontal_tank",
            "plant/lidded_vessel",
            "plant/lidded_vessel_2x2",
            "plant/natural_draft_tower",
            "plant/pipe_gauges",
            "plant/process_pid",
            "plant/vessel_basin",
            "plant/vessel_internals",
            "plant/vessel_maintenance",
            "plant/world_liquids",
            "turbine/multiblock")) {
            try (var stream = net.createplant.ResourceStack.open(PlantTutorialCatalog.class, 
                "/assets/" + CreatePlant.MOD_ID + "/ponder/" + path + ".nbt")) {
                if (stream == null && ADDON_STORYBOARDS.contains(path)) continue;
                helper.assertTrue(stream != null, "Missing original storyboard " + path);
                issues.addAll(structureIssues(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()), path));
            }
        }
        helper.assertTrue(issues.isEmpty(), String.join("; ", issues));
        helper.succeed();
    }

    static CompoundTag validateStructure(GameTestHelper helper, java.io.InputStream stream) throws Exception {
        var structure = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        var issues = structureIssues(structure, "Ponder");
        helper.assertTrue(issues.isEmpty(), String.join("; ", issues));
        return structure;
    }

    private static java.util.List<String> structureIssues(CompoundTag structure, String context) {
        var issues = new java.util.ArrayList<String>();
        var palette = structure.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            var entry = palette.getCompound(i);
            var blockId = ResourceLocation.parse(entry.getString("Name"));
            if (!BuiltInRegistries.BLOCK.containsKey(blockId)) issues.add(context + ": unknown block " + blockId);
            var block = BuiltInRegistries.BLOCK.get(blockId);
            var properties = entry.getCompound("Properties");
            for (String name : properties.getAllKeys()) {
                var property = block.getStateDefinition().getProperty(name);
                if (property == null || property.getValue(properties.getString(name)).isEmpty())
                    issues.add(context + ": invalid state " + blockId + "[" + name + "=" + properties.getString(name) + "]");
            }
        }
        var columns=new java.util.HashMap<net.minecraft.core.BlockPos,Integer>();
        var heights=new java.util.HashMap<net.minecraft.core.BlockPos,Integer>();
        var blocks=structure.getList("blocks",Tag.TAG_COMPOUND);
        for(int i=0;i<blocks.size();i++) {
            var cell=blocks.getCompound(i);var state=palette.getCompound(cell.getInt("state"));
            if(!state.getString("Name").equals(CreatePlant.MOD_ID+":fractionating_column"))continue;
            var pos=cell.getList("pos",Tag.TAG_INT);var at=new net.minecraft.core.BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2));
            var data=cell.getCompound("nbt");var owner=data.contains("Controller") ? net.minecraft.core.BlockPos.of(data.getLong("Controller")) : at;
            if(!state.getCompound("Properties").getString("width").equals("2") || data.getInt("Width")!=2)
                issues.add(context+": a fractionator lesson must use the operating 2×2 cross-section");
            int height=data.getInt("Height");heights.putIfAbsent(owner,height);columns.merge(owner,1,Integer::sum);
            if(height<2 || height>14 || at.getX()<owner.getX() || at.getX()>owner.getX()+1 || at.getZ()<owner.getZ() || at.getZ()>owner.getZ()+1 || at.getY()<owner.getY() || at.getY()>=owner.getY()+height)
                issues.add(context+": fractionator cell/controller coordinates are inconsistent");
        }
        for(var column:columns.entrySet())if(column.getValue()!=4*heights.get(column.getKey()))
            issues.add(context+": a fractionator lesson has an incomplete 2×2 layer");
        return issues;
    }
}
