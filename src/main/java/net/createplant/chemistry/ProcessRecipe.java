package net.createplant.chemistry;

import java.util.*;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.createplant.registry.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.createplant.chemistry.ProcessModule.*;

/** Fixed batches with typed fluid inputs and Create heat-source requirements. */
public record ProcessRecipe(String id, ProcessModule module, int ticks,
    List<FluidStack> inputs, List<FluidStack> outputs, Item catalyst, HeatCondition requiredHeat) {
    public static List<ProcessRecipe> all() { return Recipes.ALL; }
    private static FluidStack f(Fluid fluid, int amount) { return new FluidStack(fluid,amount); }
    private static ProcessRecipe r(String id, ProcessModule kind, int ticks, List<FluidStack> in, List<FluidStack> out) {
        return new ProcessRecipe(id,kind,ticks,in,out,Items.AIR,HeatCondition.NONE);
    }
    private ProcessRecipe catalyst(Item item) { return new ProcessRecipe(id,module,ticks,inputs,outputs,item,requiredHeat); }
    private ProcessRecipe heat(HeatCondition heat) { return new ProcessRecipe(id,module,ticks,inputs,outputs,catalyst,heat); }
    public boolean heatAllowed(HeatLevel level) { return requiredHeat.testBlazeBurner(level); }
    private static final class Recipes {
        static final List<ProcessRecipe> ALL = List.of(
            r("recover_carbon_monoxide",FRACTION_SEPARATOR,60,List.of(f(CEFluids.CONVERTER_GAS.get(),1000)),List.of(f(CEProcessFluids.CARBON_MONOXIDE.get(),1000))),
            r("oxidize_carbon_monoxide",REACTION_VESSEL,100,List.of(f(CEProcessFluids.CARBON_MONOXIDE.get(),1000),f(CEFluids.OXYGEN_GAS.get(),465)),List.of(f(CEProcessFluids.CARBON_DIOXIDE.get(),1000))).catalyst(CEItems.OXIDATION_CATALYST.get()),
            r("concentrate_heavy_water",GAS_CENTRIFUGE,200,List.of(f(Fluids.WATER,1000)),List.of(f(CEFusionFuel.HEAVY_WATER.get(),10),f(CEFusionFuel.DEPLETED_WATER.get(),990))),
            r("recover_helium",GAS_CENTRIFUGE,200,List.of(f(CEFusionFuel.HELIUM_FEED.get(),1000)),List.of(f(CEFusion.HELIUM_GAS.get(),750),f(CEFluids.NITROGEN_GAS.get(),250))),
            r("synthesize_ammonia",CATALYST_UNIT,100,List.of(f(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),10000)),List.of(f(CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get(),9000))).catalyst(CEItems.IRON_CATALYST.get()).heat(HeatCondition.HEATED),
            r("flash_hot_coolant",GAS_LIQUID_SEPARATOR,5,List.of(f(CEProcessFluids.HOT_REACTOR_COOLANT.get(),12000)),List.of(f(CEProcessFluids.PRESSURISED_WATER.get(),11000),f(CEProcessFluids.HIGH_PRESSURE_STEAM.get(),1000))),
            r("separate_ammonia",GAS_LIQUID_SEPARATOR,60,List.of(f(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000)),List.of(f(CEProcessFluids.LIQUID_AMMONIA.get(),1),f(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),8000))),
            r("oxidize_ammonia",REACTION_VESSEL,100,List.of(f(CEFluids.AMMONIA_GAS.get(),100),f(CEFluids.OXYGEN_GAS.get(),125)),List.of(f(CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get(),250))).catalyst(CEItems.OXIDATION_CATALYST.get()),
            r("separate_nitric_oxide",FRACTION_SEPARATOR,60,List.of(f(CEProcessFluids.COLD_NITRIC_OXIDE_MIXTURE.get(),250)),List.of(f(CEOstwaldFluids.NITRIC_OXIDE.get(),100),f(Fluids.WATER,150))),
            r("oxidize_nitric_oxide",REACTION_VESSEL,100,List.of(f(CEOstwaldFluids.NITRIC_OXIDE.get(),100),f(CEFluids.OXYGEN_GAS.get(),50)),List.of(f(CEOstwaldFluids.NITROGEN_DIOXIDE.get(),100))),
            // Ostwald absorption, 3 NO2 + H2O -> 2 HNO3 + NO: the nitric oxide is oxidised and sent round again.
            r("absorb_nitric_acid",ABSORPTION_TRAYS,100,List.of(f(CEOstwaldFluids.NITROGEN_DIOXIDE.get(),300),f(Fluids.WATER,100)),List.of(f(CEFluids.NITRIC_ACID.get(),200),f(CEOstwaldFluids.NITRIC_OXIDE.get(),100))),
            r("absorb_sulfuric_acid",ABSORPTION_TRAYS,100,List.of(f(CEUranium.SULFUR_TRIOXIDE.get(),250),f(Fluids.WATER,250)),List.of(f(CEUranium.SULFURIC_ACID.get(),250))),
            r("absorb_sulfurous_acid",ABSORPTION_TRAYS,100,List.of(f(CEUranium.SULFUR_DIOXIDE.get(),250),f(Fluids.WATER,250)),List.of(f(CEUranium.SULFUROUS_ACID.get(),250))),
            r("absorb_hydrochloric_acid",ABSORPTION_TRAYS,100,List.of(f(CEChlorAlkali.HYDROGEN_CHLORIDE.get(),500),f(Fluids.WATER,500)),List.of(f(CEChlorAlkali.HYDROCHLORIC_ACID.get(),1000))),
            r("distill_crude",FRACTION_SEPARATOR,100,List.of(f(CEProcessFluids.PREHEATED_CRUDE_OIL.get(),1000)),List.of(f(CEPetroFluids.GASOLINE.get(),200),f(CEPetroFluids.DIESEL.get(),300),f(CEPetroFluids.HEAVY_OIL.get(),200),f(CEPetroFluids.NAPHTHA.get(),200),f(CEPetroFluids.LUBRICATING_OIL.get(),100))),
            r("crack_naphtha",REACTION_VESSEL,120,List.of(f(CEPetroFluids.NAPHTHA.get(),200),f(Fluids.WATER,100)),List.of(f(CEProcessFluids.HOT_OLEFINS.get(),200),f(Fluids.WATER,100))).catalyst(CEItems.CRACKING_CATALYST.get()).heat(HeatCondition.SUPERHEATED),
            r("separate_olefins",FRACTION_SEPARATOR,60,List.of(f(CEProcessFluids.COOLED_OLEFINS.get(),200)),List.of(f(CEPetroFluids.ETHYLENE.get(),150),f(CEPetroFluids.PROPYLENE.get(),50))),
            r("polymerize_ethylene",REACTION_VESSEL,100,List.of(f(CEPetroFluids.ETHYLENE.get(),250)),List.of(f(CEProcessFluids.MOLTEN_POLYETHYLENE.get(),250))).catalyst(CEItems.POLYMERIZATION_CATALYST.get()).heat(HeatCondition.HEATED),
            r("polymerize_propylene",REACTION_VESSEL,100,List.of(f(CEPetroFluids.PROPYLENE.get(),250)),List.of(f(CEProcessFluids.MOLTEN_POLYPROPYLENE.get(),250))).catalyst(CEItems.POLYMERIZATION_CATALYST.get()).heat(HeatCondition.HEATED),
            // Hydrodesulfurization over cobalt-molybdenum: the sulfur leaves as hydrogen sulfide, one hydrogen taken for each.
            r("hydrotreat_diesel",REACTION_VESSEL,120,List.of(f(CEPetroFluids.DIESEL.get(),1000),f(CEProcessFluids.COMPRESSED_HYDROGEN.get(),600)),List.of(f(CEProcessFluids.HOT_HYDROTREATER_EFFLUENT.get(),1000))).catalyst(CEItems.HYDROTREATING_CATALYST.get()).heat(HeatCondition.HEATED),
            r("separate_hydrotreater_effluent",FRACTION_SEPARATOR,60,List.of(f(CEProcessFluids.COOLED_HYDROTREATER_EFFLUENT.get(),1000)),List.of(f(CEProcessFluids.LOW_SULFUR_DIESEL.get(),1000),f(CEProcessFluids.HYDROGEN_SULFIDE.get(),500),f(CEFluids.HYDROGEN_GAS.get(),100))),
            // Mild hydrocracking over nickel-molybdenum on zeolite: the heavy oil breaks into lighter, sulfur-free cuts and swells in volume.
            r("hydrocrack_heavy_oil",REACTION_VESSEL,160,List.of(f(CEPetroFluids.HEAVY_OIL.get(),1000),f(CEProcessFluids.COMPRESSED_HYDROGEN.get(),1000)),List.of(f(CEProcessFluids.HOT_HYDROCRACKER_EFFLUENT.get(),1000))).catalyst(CEItems.HYDROCRACKING_CATALYST.get()).heat(HeatCondition.SUPERHEATED),
            r("separate_hydrocracker_effluent",FRACTION_SEPARATOR,80,List.of(f(CEProcessFluids.COOLED_HYDROCRACKER_EFFLUENT.get(),1000)),List.of(f(CEProcessFluids.LOW_SULFUR_DIESEL.get(),600),f(CEPetroFluids.NAPHTHA.get(),450),f(CEProcessFluids.HYDROGEN_SULFIDE.get(),250),f(CEFluids.HYDROGEN_GAS.get(),200))),
            // Ethylene oxide: partial oxidation of ethylene; the oxidation catalyst stands in for silver.
            r("oxidize_ethylene",REACTION_VESSEL,100,List.of(f(CEPetroFluids.ETHYLENE.get(),200),f(CEFluids.OXYGEN_GAS.get(),100)),List.of(f(CEProcessFluids.ETHYLENE_OXIDE.get(),200))).catalyst(CEItems.OXIDATION_CATALYST.get()).heat(HeatCondition.HEATED),
            r("oxidize_sulfur_dioxide",REACTION_VESSEL,100,List.of(f(CEUranium.SULFUR_DIOXIDE.get(),100),f(CEFluids.OXYGEN_GAS.get(),50)),List.of(f(CEUranium.SULFUR_TRIOXIDE.get(),100))).catalyst(CEItems.OXIDATION_CATALYST.get()),
            // 0.7 % feed to 4 % product with 0.25 % tails: one part in eight of the feed leaves as product.
            r("enrich_uranium",GAS_CENTRIFUGE,200,List.of(f(CEUranium.URANIUM_HEXAFLUORIDE.get(),1000)),List.of(f(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(),125),f(CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get(),875))),
            r("enrich_compact_uranium",GAS_CENTRIFUGE,240,List.of(f(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(),1000)),List.of(f(CEUranium.COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE.get(),789),f(CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get(),211)))
        );
    }
}
