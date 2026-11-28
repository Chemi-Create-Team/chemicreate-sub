package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.createplant.registry.CESharedChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ForgingGameTests {
    private static MechanicalPressBlockEntity press(GameTestHelper h) {
        var pos = new BlockPos(3, 3, 3);
        h.setBlock(pos, AllBlocks.MECHANICAL_PRESS.get());
        return (MechanicalPressBlockEntity) h.getBlockEntity(pos);
    }

    @GameTest(template = "test_reactor")
    public static void nineNativeOperationsFinishSteelAndKeepProgressAcrossSaving(
            GameTestHelper h) {
        var press = press(h);
        var current = CEItems.PIG_IRON.toStack();
        for (int step = 0; step < 9; step++) {
            var snapshot = current.copy();
            var transported = new TransportedItemStack(current);
            var outputs = new ArrayList<ItemStack>();
            if (step % 3 == 1) {
                h.assertTrue(
                        !press.tryProcessOnBelt(transported, null, true),
                        "Pressing cannot skip required heating");
                h.assertTrue(
                        AllFanProcessingTypes.BLASTING.canProcess(current, h.getLevel()),
                        "The native lava fan recognizes the assembly heating step");
                outputs.addAll(AllFanProcessingTypes.BLASTING.process(current, h.getLevel()));
            } else {
                if (current.is(CEItems.HEATED_UNFINISHED_STEEL.get()))
                    h.assertTrue(
                            !AllFanProcessingTypes.BLASTING.canProcess(current, h.getLevel()),
                            "Repeated heating cannot skip a press");
                h.assertTrue(
                        press.tryProcessOnBelt(transported, null, true),
                        "Native belt pressing simulates the next folding/rolling step");
                h.assertTrue(
                        ItemStack.matches(current, snapshot),
                        "Pressing simulation leaves progress and count unchanged");
                h.assertTrue(
                        press.tryProcessOnBelt(transported, outputs, false),
                        "The native mechanical press executes the step");
            }
            h.assertTrue(!outputs.isEmpty(), "Every required operation has a physical output");
            current = outputs.getFirst();
            if (step < 8) {
                h.assertTrue(
                        current.is(CEItems.HEATED_UNFINISHED_STEEL.get()) && outputs.size() == 1,
                        "Intermediate passes cannot emit finished steel or slag");
                h.assertTrue(
                        current.get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == step + 1,
                        "Assembly advances exactly one operation");
                current =
                        ItemStack.parseOptional(
                                h.getLevel().registryAccess(),
                                (CompoundTag) current.save(h.getLevel().registryAccess()));
            } else {
                h.assertTrue(
                        current.is(CEItems.STEEL_INGOT.get())
                                && current.getCount() == 1
                                && !current.has(AllDataComponents.SEQUENCED_ASSEMBLY),
                        "Nine operations always produce one finished steel ingot");
                h.assertTrue(
                        outputs.size() <= 2
                                && outputs.stream()
                                        .skip(1)
                                        .allMatch(
                                                stack ->
                                                        stack.is(CEItems.STEEL_SLAG.get())
                                                                && stack.getCount() == 1),
                        "Slag is a secondary product, never a replacement for steel");
            }
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void completionAlwaysKeepsSteelAlongsideTheIndependentSlagRoll(GameTestHelper h) {
        var current = CEItems.HEATED_UNFINISHED_STEEL.toStack();
        current.set(
                AllDataComponents.SEQUENCED_ASSEMBLY,
                new SequencedAssemblyRecipe.SequencedAssembly(
                        CreatePlant.location("sequenced_assembly/forged_steel"), 8, 8f / 9));
        var recipe = press(h).getRecipe(current).orElseThrow().value();
        var random = net.minecraft.util.RandomSource.create(12345);
        int steel = 0, slag = 0;
        for (int batch = 0; batch < 256; batch++) {
            var results = recipe.rollResults(recipe.getRollableResults(), random);
            for (var item : results) {
                if (item.is(CEItems.STEEL_INGOT.get())) steel += item.getCount();
                else if (item.is(CEItems.STEEL_SLAG.get())) slag += item.getCount();
                else
                    h.fail("Finished forging cannot return an unfinished item or unrelated output");
            }
        }
        org.slf4j.LoggerFactory.getLogger(ForgingGameTests.class)
                .info("FORGING OUTPUT CHECK steel={} slag={} batches=256", steel, slag);
        h.assertTrue(
                steel == 256 && slag > 32 && slag < 96,
                "Every batch keeps its steel while the separate 25 percent slag roll varies: steel="
                        + steel
                        + ", slag="
                        + slag);
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void soulFanProducesCalciteFromDiorite(GameTestHelper h) {
        var pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE.defaultBlockState());
        h.assertTrue(
                AllFanProcessingTypes.HAUNTING.isValidAt(h.getLevel(), h.absolutePos(pos)),
                "A lit soul campfire selects native haunting");
        var outputs =
                AllFanProcessingTypes.HAUNTING.process(new ItemStack(Items.DIORITE), h.getLevel());
        h.assertTrue(
                outputs != null
                        && outputs.size() == 1
                        && outputs.getFirst().is(Items.CALCITE)
                        && outputs.getFirst().getCount() == 1,
                "One diorite produces one calcite through the native fan processor");
        h.setBlock(pos, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
        h.assertTrue(
                !AllFanProcessingTypes.HAUNTING.isValidAt(h.getLevel(), h.absolutePos(pos)),
                "Ordinary fire cannot substitute for soul fire");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void basinMixesBoneMealGravelAndExactlyOneWaterCharge(GameTestHelper h) {
        var pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, AllBlocks.BASIN.get());
        h.runAfterDelay(
                5,
                () -> {
                    var basin =
                            (com.simibubi.create.content.processing.basin.BasinBlockEntity)
                                    h.getBlockEntity(pos);
                    basin.getInputInventory().insertItem(0, new ItemStack(Items.BONE_MEAL), false);
                    basin.getInputInventory().insertItem(1, new ItemStack(Items.GRAVEL), false);
                    var tank = basin.getTanks().getFirst().getCapability();
                    tank.fill(
                            new net.neoforged.neoforge.fluids.FluidStack(
                                    net.minecraft.world.level.material.Fluids.WATER, 250),
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                                    .EXECUTE);
                    var recipe =
                            h.getLevel()
                                    .getRecipeManager()
                                    .byKey(CreatePlant.location("mixing/fluorite_from_bone_meal"))
                                    .orElseThrow()
                                    .value();
                    h.assertTrue(
                            com.simibubi.create.content.processing.basin.BasinRecipe.match(
                                    basin, recipe),
                            "The unheated basin accepts the ordinary water recipe");
                    h.assertTrue(
                            com.simibubi.create.content.processing.basin.BasinRecipe.apply(
                                    basin, recipe),
                            "The native mixer transaction succeeds");
                    int output = 0;
                    for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++)
                        if (basin.getOutputInventory()
                                .getStackInSlot(slot)
                                .is(CESharedChemicals.FLUORITE.get()))
                            output += basin.getOutputInventory().getStackInSlot(slot).getCount();
                    h.assertTrue(
                            output == 1
                                    && tank.getFluidInTank(0).isEmpty()
                                    && !com.simibubi.create.content.processing.basin.BasinRecipe
                                            .apply(basin, recipe),
                            "One bone meal, gravel and 250 mB charge produces one fluorite without"
                                + " duplication");
                    h.succeed();
                });
    }

    @GameTest(template = "test_reactor")
    public static void ordinaryIronSheetsAndFanSmeltingRemainAvailable(GameTestHelper h) {
        var recipe = press(h).getRecipe(new ItemStack(Items.IRON_INGOT)).orElseThrow();
        h.assertTrue(
                recipe.value()
                        .getResultItem(h.getLevel().registryAccess())
                        .is(com.simibubi.create.AllItems.IRON_SHEET.get()),
                "Forging does not capture the ordinary iron-sheet input");
        var outputs =
                AllFanProcessingTypes.BLASTING.process(new ItemStack(Items.RAW_IRON), h.getLevel());
        h.assertTrue(
                outputs != null && outputs.stream().anyMatch(stack -> stack.is(Items.IRON_INGOT)),
                "Other fan-blasting recipes retain native behavior");
        var assembly =
                (SequencedAssemblyRecipe)
                        h.getLevel()
                                .getRecipeManager()
                                .byKey(CreatePlant.location("sequenced_assembly/forged_steel"))
                                .orElseThrow()
                                .value();
        h.assertTrue(
                assembly.getLoops() == 3
                        && assembly.getSequence().size() == 3
                        && assembly.getOutputChance() == 1,
                "The route has three loops, three operations and guaranteed steel");
        var fold = assembly.getSequence().get(2).getRecipe();
        h.assertTrue(
                fold.getType() == AllRecipeTypes.PRESSING.getType()
                        && fold.getRollableResults().get(1).getChance() == .25f,
                "Folding uses the standard press and data-defined 25 percent slag");
        h.succeed();
    }
}
