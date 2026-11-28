package net.createplant.chemistry;

import java.util.List;
import net.createplant.registry.CEUranium;
import net.createplant.thermal.FluidIdentity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Uranium hexafluoride enrichment steps for the gas centrifuge. */
public final class AtomicsProcessRecipes {
    private AtomicsProcessRecipes() { }

    public static void register() { ProcessRecipe.register(AtomicsProcessRecipes::recipes); }

    private static ProcessRecipe r(String id, int ticks, Fluid in, int inAmount, Fluid out1, int out1Amount, Fluid out2, int out2Amount) {
        var input = new FluidStack(in, inAmount);
        return new ProcessRecipe(id, ProcessModule.GAS_CENTRIFUGE, ticks, List.of(input),
            List.of(new FluidStack(out1, out1Amount), new FluidStack(out2, out2Amount)), Items.AIR, HeatCondition.NONE,
            List.of(FluidIdentity.requirement(in)));
    }

    private static List<ProcessRecipe> recipes() {
        return List.of(
            // 0.7 % feed to 4 % product with 0.25 % tails: one part in eight of the feed leaves as product.
            r("enrich_uranium", 200, CEUranium.URANIUM_HEXAFLUORIDE.get(), 1000,
                CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(), 125, CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get(), 875),
            r("enrich_compact_uranium", 240, CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(), 1000,
                CEUranium.COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE.get(), 789, CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get(), 211));
    }
}
