package net.createplant.gametest;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createplant.CreatePlant;
import net.createplant.registry.CECreativeModeTabs;
import net.createplant.registry.CEItems;
import net.createplant.thermal.FluidState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreativeCatalogGameTests {
    @GameTest(template="test_reactor")
    public static void everyCurrentItemHasExactlyOneAppropriateTab(GameTestHelper h) throws java.io.IOException {
        var parameters=new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess());
        var base=CECreativeModeTabs.BASE.get();
        base.buildContents(parameters);
        Set<Item> catalogue=new HashSet<>();
        base.getDisplayItems().forEach(stack->catalogue.add(stack.getItem()));
        var own=BuiltInRegistries.ITEM.stream().filter(item->BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID)).toList();
        var dyed=CEItems.LARGE_FLYWHEELS.stream().map(holder->(Item)holder.get()).filter(item->item!=CEItems.LARGE_FLYWHEEL.get()).toList();
        var retiredBucket = net.createplant.registry.CESafety.RAW_PROCESS_GAS.get().getBucket();
        for(var item:own) h.assertTrue(catalogue.contains(item) == (!dyed.contains(item) && item != retiredBucket && !net.createplant.item.StatefulBuckets.retired(item)),
            "Current items appear once, with dyed variants and the legacy gas bucket hidden: "+BuiltInRegistries.ITEM.getKey(item));
        var order=base.getDisplayItems().stream().map(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).toList();
        h.assertTrue(order.indexOf("corrosion_resistant_bucket")>order.indexOf("industrial_dispatch_board")&&order.indexOf("sulfuric_acid_bucket")<order.indexOf("hydrogen_gas_bucket")
            &&order.indexOf("hydrogen_gas_bucket")<order.indexOf("uranium_ore")&&order.indexOf("salt")<order.indexOf("aluminum_ingot")
            &&order.indexOf("aluminum_ingot")<order.indexOf("gas_mask"),"Materials run from liquid and gas buckets through ores, powders and metals to wearables: "+order);
        var sections=((net.createplant.registry.SectionedCreativeTab)base).sections().stream().map(section->section.key()).toList();
        h.assertTrue(sections.equals(List.of("equipment","materials","construction")),"Only equipment/tools, materials/fluids and construction headings are displayed");
        h.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream().filter(id->id.getNamespace().equals(CreatePlant.MOD_ID)).count()==1,"The mod has a single creative tab");
        check(h,catalogue,List.of("lidded_vessel","electrolysis_cell","electrolysis_cell_controller","fuel_cell","reactor_impeller","reciprocating_compressor","gas_holder","diorite_casing","large_flywheel"));
        check(h,catalogue,List.of("uranium_ore","iron_catalyst","fuel_rod","asbestos_wrapped_electrode","hydrogen_gas_bucket","empty_waste_canister","vitrified_waste_canister","gas_mask","protective_coat","serpentinite"));
        for(String id:List.of("ammonia_oxidizer","catalytic_cracker","distillation_tower","asbestos_mesh","electrolyzer","gas_holder_anchor","gas_holder_guide_rail"))
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(CreatePlant.location(id)),"Retired equipment stays unregistered: "+id);
        for(String id:List.of("uranium_hexafluoride_bucket","enriched_uranium_hexafluoride_bucket","depleted_uranium_hexafluoride_bucket","compact_enriched_uranium_hexafluoride_bucket"))
            h.assertTrue(BuiltInRegistries.ITEM.get(CreatePlant.location(id)) instanceof net.createplant.item.GasBucketItem,"UF6 streams use the shared gas bucket like every process gas: "+id);
        for(String id:List.of("gas_holder_shell","gas_holder_anchor","gas_holder_guide_rail"))
            h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(CreatePlant.location(id)) && !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(CreatePlant.location(id)),"Retired gas holder blocks and entities stay unregistered: "+id);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(CreatePlant.location("gas_holder"))
            && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(CreatePlant.location("gas_holder"))
            && BuiltInRegistries.ENTITY_TYPE.containsKey(CreatePlant.location("gas_holder_sleeve"))
            && BuiltInRegistries.FLUID.containsKey(CreatePlant.location("contaminated_gas")),"The telescopic holder, its moving sleeves and waste stream remain registered");
        var items=own.stream().sorted(java.util.Comparator.comparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).map(item->Map.of(
            "id",BuiltInRegistries.ITEM.getKey(item).toString(),"translation",item.getDescriptionId(),"tab",catalogue.contains(item)?"base":"hidden")).toList();
        var blocks=BuiltInRegistries.BLOCK.keySet().stream().filter(id->id.getNamespace().equals(CreatePlant.MOD_ID)).map(Object::toString).sorted().toList();
        var fluids=BuiltInRegistries.FLUID.keySet().stream().filter(id->id.getNamespace().equals(CreatePlant.MOD_ID)&&!id.getPath().startsWith("flowing_")).map(Object::toString).sorted().toList();
        Files.writeString(Path.of(System.getProperty("createplant.inventoryReport","maintenance-inventory.json")),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("items",items,"blocks",blocks,"fluids",fluids))+"\n");
        h.succeed();
    }
    private static void check(GameTestHelper h,Set<Item> tab,List<String> ids) {
        for(String id:ids) h.assertTrue(tab.contains(BuiltInRegistries.ITEM.get(CreatePlant.location(id))),"Correct creative tab for "+id);
    }

    @GameTest(template="test_reactor")
    public static void creativeCatalogListsOnlyTheDefaultBucketForEachFluid(GameTestHelper h) {
        var tab = CECreativeModeTabs.BASE.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        for (var catalogue : List.of(tab.getDisplayItems(), tab.getSearchTabDisplayItems())) {
            var fluids = new HashSet<net.minecraft.world.level.material.Fluid>();
            for (var stack : catalogue) {
                if (!(stack.getItem() instanceof net.minecraft.world.item.BucketItem)) continue;
                FluidUtil.getFluidContained(stack).ifPresent(fluid -> {
                    h.assertTrue(fluids.add(fluid.getFluid()), "Duplicate bucket for " + BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
                    h.assertTrue(net.createplant.item.StatefulBuckets.defaultState(fluid), "A catalogue bucket starts in its default state: " + fluid);
                });
            }
            h.assertTrue(catalogue.stream().anyMatch(stack -> stack.is(Items.WATER_BUCKET)), "Water and steam use the water bucket");
            h.assertTrue(catalogue.stream().anyMatch(stack -> stack.is(CEItems.NITROGEN_GAS_BUCKET.get())), "Liquid and gaseous nitrogen use the nitrogen bucket");
        }
        h.succeed();
    }

    @GameTest(template="test_reactor")
    public static void pickedFluidStateSurvivesTheBucket(GameTestHelper h) {
        var bucket=new net.minecraft.world.item.ItemStack(net.createplant.registry.CEItems.NITROGEN_GAS_BUCKET.get());
        var nominal=net.createplant.item.StatefulBuckets.contents(bucket,((net.minecraft.world.item.BucketItem)bucket.getItem()).content);
        var picked=FluidState.with(nominal,1,12);
        net.createplant.item.StatefulBuckets.write(picked,bucket);
        var read=net.createplant.item.StatefulBuckets.contents(bucket,((net.minecraft.world.item.BucketItem)bucket.getItem()).content);
        h.assertTrue(FluidState.temperature(read)==1 && FluidState.pressure(read)==12 && !FluidState.gas(read),"The same nitrogen bucket holds liquid and keeps both chosen values");
        h.succeed();
    }
}
