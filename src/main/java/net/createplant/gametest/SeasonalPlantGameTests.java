package net.createplant.gametest;

import java.util.List;
import com.google.gson.JsonObject;
import net.createplant.CreatePlant;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class SeasonalPlantGameTests {
    @GameTest(template = "test_empty")
    public static void allPlantAndDieselFluidNamesHaveJapaneseTranslations(GameTestHelper h) throws Exception {
        JsonObject lang;
        lang = net.createplant.ResourceStack.object(SeasonalPlantGameTests.class, "/assets/" + CreatePlant.MOD_ID + "/lang/ja_jp.json");
        for (var fluid : BuiltInRegistries.FLUID) {
            var id = BuiltInRegistries.FLUID.getKey(fluid);
            if ((!id.getNamespace().equals(CreatePlant.MOD_ID) && !id.getNamespace().equals("createdieselgenerators")) || id.getPath().startsWith("flowing_")) continue;
            String key = fluid.getFluidType().getDescriptionId();
            h.assertTrue(lang.has(key) && lang.get(key).getAsString().codePoints().anyMatch(c -> c > 255), "Actual fluid-type name resolves in Japanese: " + id + " / " + key);
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void dieselFuelBucketsStayInTheirOwnModAndNotOurTab(GameTestHelper h) {
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            if (!entry.getKey().location().getNamespace().equals(CreatePlant.MOD_ID)) continue;
            var tab = entry.getValue();tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
            for (var stack : tab.getDisplayItems()) h.assertTrue(!stack.is(com.jesz.createdieselgenerators.CDGFluids.GASOLINE.getBucket().get()) && !stack.is(com.jesz.createdieselgenerators.CDGFluids.DIESEL.getBucket().get()), "The plant tab does not duplicate native CDG fuel buckets");
        }
        h.assertTrue(com.jesz.createdieselgenerators.CDGFluids.GASOLINE.getBucket().get() != net.minecraft.world.item.Items.AIR && com.jesz.createdieselgenerators.CDGFluids.DIESEL.getBucket().get() != net.minecraft.world.item.Items.AIR, "Native CDG buckets and fuel fluids remain registered");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void warmingRatePreservesVolumesAndProgressAcrossReload(GameTestHelper h) {
        var water = new FluidStack(Fluids.WATER, 1000);
        var fluids = List.of(water);
        var heater = new TankHeating();
        for (int n = 0; n < 4; n++) heater.tick(fluids, TankHeating.LIT_WATTS);
        h.assertTrue(FluidState.temperature(water) > 8 && !FluidState.gas(water), "A kilogram of water on one burner warms but has not boiled away after two seconds");
        var saved = new net.minecraft.nbt.CompoundTag();
        heater.write(saved, h.getLevel().registryAccess());
        var restored = new TankHeating();
        restored.read(saved, h.getLevel().registryAccess());
        for (int n = 0; n < 4; n++) restored.tick(fluids, TankHeating.LIT_WATTS);
        h.assertTrue(FluidState.gas(water) && water.getAmount() == 1000, "Heating resumes after reload and the water boils with no volume change");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void actualSereneSeasonsChangesRatesButAbsenceIsNeutral(GameTestHelper h) {
        if (!SeasonalThermal.available()) {
            h.assertTrue(SeasonalThermal.heating(h.getLevel()) == 1 && SeasonalThermal.cooling(h.getLevel()) == 1, "No optional mod means exactly the original rates");h.succeed();return;
        }
        h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()), "season set mid_winter");
        h.runAfterDelay(24, () -> {
            h.assertTrue(SeasonalThermal.cooling(h.getLevel()) == 1.2 && SeasonalThermal.heating(h.getLevel()) == .8, "Native mid-winter state makes cooling stronger and warming slower");
            var centre = h.absolutePos(new BlockPos(20, 2, 20));int index = 0;var control = centre.offset(0, 1, -CoolingTowerStructure.SIZES.get(index).radius());
            for (var cell : CoolingTowerStructure.cells(index)) h.getLevel().setBlock(centre.offset(cell.offset()), cell.block().defaultBlockState(), 2);
            var tower = (CoolingTowerBlockEntity) h.getLevel().getBlockEntity(control);for (int n = 0; n < 200 && !tower.formed(); n++) tower.validateSlice();
            h.assertTrue(tower.formed(), "Actual seasonal cooling tower forms");tower.tank(0).fill(FluidForm.WARM_WATER.stack(2_000_000), EXECUTE);tower.process();
            h.assertTrue(tower.tank(1).getFluidAmount() == Math.round(CoolingTowerBlockEntity.MAX_FLOW * 1.2) && tower.tank(0).getFluidAmount() + tower.tank(1).getFluidAmount() == 2_000_000 && tower.steaming(), "Winter changes real throughput while conserving both tanks and enables the plume only for cooling");
            tower.tank(0).setFluid(FluidStack.EMPTY);tower.process();h.assertTrue(!tower.steaming(), "No cooling work means no new steam");
            h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()), "season set mid_summer");
        });
        h.runAfterDelay(48, () -> { h.assertTrue(SeasonalThermal.cooling(h.getLevel()) == .8 && SeasonalThermal.heating(h.getLevel()) == 1.2, "Changing the real season refreshes the bounded cache");h.succeed(); });
    }
}
