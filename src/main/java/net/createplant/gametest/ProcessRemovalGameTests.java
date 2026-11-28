package net.createplant.gametest;

import java.util.ArrayList;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.registry.CECreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ProcessRemovalGameTests {
    @GameTest(template = "test_empty")
    public static void everyPlantSourceFluidHasAWorkingBucketInCreative(GameTestHelper h) throws Exception {
        var tab = CECreativeModeTabs.BASE.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        var tabs = new ArrayList<CreativeModeTab>(java.util.List.of(tab));
        for (var mod : java.util.List.of("create_rnr_atomics", "create_rnr_logistics")) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(mod, "base");
            if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id)) continue;
            var addon = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
            addon.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
            tabs.add(addon);
        }
        var checked = new ArrayList<String>();var missing = new ArrayList<String>();
        for (var fluid : BuiltInRegistries.FLUID) {
            var id = BuiltInRegistries.FLUID.getKey(fluid);
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || !(fluid instanceof FlowingFluid flowing) || flowing.getSource() != fluid) continue;
            // Carriers and air's trace gases exist only as parts of a mixture, with no container of their own.
            if (fluid instanceof net.createplant.mixture.MixtureFluid || fluid == net.createplant.registry.CEMixtures.OTHER_GASES.get()) continue;
            // Retired state entries fill their substance's bucket with the matching state.
            var stack = FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
            boolean listed = tabs.stream().anyMatch(t -> t.getDisplayItems().stream().anyMatch(entry -> entry.is(stack.getItem())));
            if (id.equals(CreatePlant.location("raw_process_gas"))) {
                h.assertTrue(!stack.isEmpty() && !listed, "The legacy gas still has a bucket and is hidden from Creative");
            } else if (stack.isEmpty() || !listed) { missing.add(id.toString());continue; }
            var contained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            h.assertTrue(contained.is(fluid) && contained.getAmount() == 1000, "Bucket contains its named source fluid: " + id);
            h.assertTrue(fluid.getBucket() == stack.getItem(), "Fluid and FluidType resolve to the same container: " + id);
            checked.add(id.toString());
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("creative-fluid-buckets.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("checked", checked, "missing", missing)));
        h.assertTrue(missing.isEmpty(), "All plant fluids have an actual container in the creative tab: " + missing);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void gasBucketRoundTripPreservesGradesAndMixtureData(GameTestHelper h) {
        var gas = new FluidStack(net.createplant.registry.CEGasBell.CONTAMINATED_GAS.get(), 1000);
        gas.set(net.createplant.registry.CEGasBell.GAS_CONTENTS.get(), new net.createplant.chemistry.WasteGas(.002, 1400, .04, 5));
        net.createplant.thermal.FluidState.setTemperature(gas, 12);
        var bucket = FluidUtil.getFilledBucket(gas);
        var loaded = ItemStack.parse(h.getLevel().registryAccess(), bucket.save(h.getLevel().registryAccess())).orElseThrow();
        var handler = FluidUtil.getFluidHandler(loaded).orElseThrow();
        var preview = handler.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        h.assertTrue(FluidStack.isSameFluidSameComponents(preview, gas) && FluidUtil.getFluidContained(loaded).orElseThrow().getAmount() == 1000, "Saving and simulating the container retain its mixture and heat grade without consuming it");
        var actual = handler.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(FluidStack.isSameFluidSameComponents(actual, gas) && actual.getAmount() == 1000 && handler.getContainer().is(Items.BUCKET), "Native draining returns the same finite gas and one empty bucket");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void retiredBlocksAndIntermediatesAreUnregistered(GameTestHelper h) {
        var names = new ArrayList<>(List.of("electric_heater"));
        for (String kind : List.of("settling_tank", "crystallizer")) for (String part : List.of("controller", "wall", "weir", "outlet", "coil")) names.add(kind + "_" + part);
        for (String name : names) h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(CreatePlant.location(name)) && !BuiltInRegistries.ITEM.containsKey(CreatePlant.location(name)), "Retired machine has no live registration: " + name);
        for (String metal : List.of("iron", "copper", "zinc", "tin")) {
            h.assertTrue(!BuiltInRegistries.FLUID.containsKey(CreatePlant.location("impure_" + metal + "_sulfate_solution")), "Cloudy intermediate is retired");
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(CreatePlant.location(metal + "_salt_crystals")), "Unused crystals are retired");
        }
        h.succeed();
    }
}
