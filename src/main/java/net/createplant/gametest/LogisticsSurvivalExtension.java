package net.createplant.gametest;

import static net.createplant.gametest.SurvivalReachabilityGameTests.has;

import java.util.Set;
import net.createplant.arm.ArmRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;

/** The Logistics & Mining part of the survival reachability audit; loaded by name, so the audit runs without the addon too. */
final class LogisticsSurvivalExtension implements SurvivalReachabilityGameTests.Extension {
    static { SurvivalReachabilityGameTests.EXTENSIONS.add(new LogisticsSurvivalExtension()); }

    @Override public Boolean equipment(Recipe<?> recipe, Set<Item> reached) {
        return recipe instanceof ArmRecipe arm ? has(reached, "industrial_arm") && (!arm.usesTorch() || has(reached, "welding_torch")) : null;
    }
    @Override public boolean produces(Recipe<?> recipe, Set<Item> items, Set<Object> fluids) {
        return recipe instanceof ArmRecipe arm && arm.transitional().isPresent() && items.add(arm.transitional().get());
    }
}
