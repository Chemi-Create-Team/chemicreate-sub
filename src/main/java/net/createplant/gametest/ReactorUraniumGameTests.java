package net.createplant.gametest;

import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.chemistry.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.gametest.ReactorGameTests.*;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEItems;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CEUranium;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorUraniumGameTests {
    @GameTest(template="test_reactor")
    public static void compactFuelManufacturingKeepsTheExistingMachinesAndAllIngredients(GameTestHelper h) {
        var enrichment=ProcessRecipe.all().stream().filter(recipe->recipe.id().equals("enrich_compact_uranium")).findFirst().orElseThrow();
        h.assertTrue(enrichment.module()==ProcessModule.GAS_CENTRIFUGE && enrichment.inputs().size()==1
            && enrichment.inputs().getFirst().is(net.createplant.registry.CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.inputs().getFirst().getAmount()==1000 && enrichment.outputs().getFirst().is(net.createplant.registry.CEUranium.COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.outputs().getFirst().getAmount()==789 && enrichment.outputs().getLast().is(net.createplant.registry.CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.outputs().getLast().getAmount()==211,"The existing centrifuge further enriches real 4% feed and preserves the 1000 mB product/tails balance");
        var manager=h.getLevel().getRecipeManager();
        var dioxide=(net.createplant.recipe.PressureReactionRecipe)manager.byKey(CreatePlant.location("pressure_reaction/compact_uranium_dioxide")).orElseThrow().value();
        h.assertTrue(dioxide.module()==ReactorModule.STIRRED && dioxide.getFluidIngredients().size()==3 && dioxide.getIngredients().isEmpty()
            && dioxide.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.NONE,"Reconversion combines enriched gas, liquid water and hydrogen in one stirred batch");
        var pellet=(net.createplant.recipe.PressureReactionRecipe)manager.byKey(CreatePlant.location("pressure_reaction/compact_uranium_pellet")).orElseThrow().value();
        h.assertTrue(pellet.module()==ReactorModule.ABSORBER && pellet.getIngredients().size()==1 && pellet.getFluidIngredients().isEmpty()
            && pellet.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.HEATED,"A heated tray fires pressed pellets without another gas feed");
        h.assertTrue(manager.byKey(CreatePlant.location("smelting/compact_uranium_pellet")).orElseThrow().value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe,
            "The ordinary furnace and Create's burner fan can also fire the pressed pellet");
        var pressing=manager.byKey(CreatePlant.location("pressing/green_compact_uranium_pellet")).orElseThrow().value();
        h.assertTrue(pressing instanceof com.simibubi.create.content.kinetics.press.PressingRecipe
            && pressing.getResultItem(h.getLevel().registryAccess()).is(net.createplant.registry.CEUranium.GREEN_COMPACT_URANIUM_PELLET.get())
            && pressing.getResultItem(h.getLevel().registryAccess()).getCount()==2,"The ordinary mechanical press makes the two green compact pellets");
        var rod=manager.byKey(CreatePlant.location("compact_enriched_fuel_rod")).orElseThrow().value();
        h.assertTrue(rod instanceof net.minecraft.world.item.crafting.ShapedRecipe
            && rod.getResultItem(h.getLevel().registryAccess()).is(net.createplant.registry.CENuclear.COMPACT_ENRICHED_FUEL_ROD.get())
            && rod.getIngredients().stream().filter(ingredient->ingredient.test(net.createplant.registry.CEUranium.COMPACT_URANIUM_PELLET.toStack())).count()==7
            && rod.getIngredients().stream().filter(ingredient->ingredient.test(CEItems.ZIRCALOY_CLADDING_TUBE.toStack())).count()==2,"Seven compact pellets and two existing cladding tubes make the identifiable short rod");
        h.succeed();
    }

}
