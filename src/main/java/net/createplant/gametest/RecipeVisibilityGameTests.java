package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.compat.jei.PlantProcess;
import net.createplant.ponder.*;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.*;
import net.createplant.thermal.ThermalStreams;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@ExplicitGameTest
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeVisibilityGameTests {
    @GameTest(template = "test_petrochemical")
    public static void gunpowderHasAnUnbrokenHydrogenNitrogenRecipePath(GameTestHelper h) {
        var edges = new IdentityHashMap<Fluid, Set<Fluid>>();
        java.util.function.BiConsumer<List<FluidStack>, List<FluidStack>> add =
                (in, out) ->
                        out.forEach(
                                product ->
                                        in.forEach(
                                                feed ->
                                                        edges.computeIfAbsent(
                                                                        product.getFluid(),
                                                                        k ->
                                                                                Collections
                                                                                        .newSetFromMap(
                                                                                                new IdentityHashMap<>()))
                                                                .add(feed.getFluid())));
        for (var process : ProcessRecipe.all()) add.accept(process.inputs(), process.outputs());
        for (var holder :
                h.getLevel()
                        .getRecipeManager()
                        .getAllRecipesFor(
                                CERecipeTypes.PRESSURE_REACTION
                                        .<net.minecraft.world.item.crafting.RecipeInput,
                                                PressureReactionRecipe>
                                                getType()))
            add.accept(
                    holder.value().getFluidIngredients().stream()
                            .map(f -> f.getFluids()[0])
                            .toList(),
                    holder.value().getFluidResults());
        for (var display : PlantProcess.ammoniaSynthesis()) {
            add.accept(display.fluids(), display.fluidResults());
            h.assertTrue(
                    net.createplant.block.entity.ReactionTowerBlockEntity.recipes().stream()
                                    .anyMatch(
                                            recipe ->
                                                    recipe.catalyst() == CEItems.IRON_CATALYST.get()
                                                            && recipe.inputs().getFirst().is(display.fluids().getFirst().getFluid()))
                            && display.kept().stream()
                                    .anyMatch(item -> item.is(CEItems.IRON_CATALYST.get())),
                    "JEI feed is accepted by the reaction tower and shows iron catalyst");
        }
        net.createplant.thermal.ThermalStreams.cooledStreams()
                .forEach(
                        (hot, cold) ->
                                add.accept(
                                        List.of(hot.stack(1)),
                                        List.of(new FluidStack(cold, 1))));
        var visited = Collections.newSetFromMap(new IdentityHashMap<Fluid, Boolean>());
        var pending = new ArrayDeque<Fluid>();
        pending.add(CEFluids.NITRIC_ACID.get());
        while (!pending.isEmpty()) {
            var fluid = pending.remove();
            if (visited.add(fluid)) pending.addAll(edges.getOrDefault(fluid, Set.of()));
        }
        h.assertTrue(
                visited.contains(CEFluids.AMMONIA_GAS.get())
                        && visited.contains(CEProcessFluids.SYNTHESIS_GAS.get())
                        && visited.contains(CEFluids.HYDROGEN_GAS.get())
                        && visited.contains(CEFluids.NITROGEN_GAS.get()),
                "Nitric acid and ammonia trace back through synthesis gas to H2 and N2");
        var powder =
                h.getLevel()
                        .getRecipeManager()
                        .byKey(CreatePlant.location("gunpowder_from_ammonium_nitrate"))
                        .orElseThrow()
                        .value();
        h.assertTrue(
                powder.getIngredients().stream()
                                .anyMatch(i -> i.test(CEItems.AMMONIUM_NITRATE.toStack()))
                        && powder.getResultItem(h.getLevel().registryAccess()).is(Items.GUNPOWDER),
                "Gunpowder uses the live nitrate route");
        var ponder =
                PlantTutorialCatalog.all().stream()
                        .filter(t -> t.id().equals("manufacturing_mining_explosives"))
                        .findFirst()
                        .orElseThrow();
        h.assertTrue(
                ponder.components().contains("minecraft:gunpowder")
                        && ponder.steps().stream()
                                .anyMatch(s -> "operation:haber_bosch".equals(s.process().recipe()))
                        && ponder.steps().stream()
                                .anyMatch(
                                        s ->
                                                s.process()
                                                        .recipe()
                                                        .endsWith(
                                                                "pressure_reaction/synthesis_gas")),
                "Gunpowder Ponder actually shows the H2/N2 mixing and synthesis steps");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void cooledInputsAndAllManufacturingRecipesRemainValid(GameTestHelper h) {
        for (var recipe : ProcessRecipe.all())
            for (var required : recipe.inputs())
                for (var option : PlantProcess.acceptedFluids(required)) {
                    h.assertTrue(
                            option.getAmount() == required.getAmount()
                                    && ThermalStreams.accepts(option, required),
                            "Displayed alternatives are accepted with unchanged quantities: "
                                    + recipe.id());
                }
        for (var pair : ThermalStreams.cooledStreams().entrySet()) {
            var hot = pair.getKey().stack(100);
            var cooled = ThermalStreams.cooling(hot).result(hot, 100);
            var next = ProcessRecipe.all().stream().flatMap(process -> process.inputs().stream())
                .filter(feed -> feed.is(pair.getValue())).findFirst().orElseThrow();
            h.assertTrue(PlantProcess.acceptedFluids(next).stream().anyMatch(option -> ThermalStreams.accepts(cooled, option))
                    && PlantProcess.acceptedFluids(next).stream().noneMatch(option -> ThermalStreams.accepts(hot, option)),
                "The cooling stage produces the next recipe's listed feed and the hot fluid stays a distinct ingredient");
        }
        var haber = ManufacturingRecipe.resolve(h.getLevel(), "operation:haber_bosch");
        var ammonia = PlantProcess.ammoniaSynthesis().getFirst();
        h.assertTrue(haber.fluids().equals(ammonia.fluids())
                && FluidForm.COMPRESSED_SYNTHESIS_FEED.matches(haber.fluids().getFirst())
                && haber.catalyst().is(CEItems.IRON_CATALYST.get()),
            "Ponder and JEI both show the live compressed feed and iron catalyst");
        h.succeed();
    }
}
