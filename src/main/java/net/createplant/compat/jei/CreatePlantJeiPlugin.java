package net.createplant.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.block.entity.FuelCellBlockEntity;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.recipe.ConverterRecipe;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.*;
import net.createplant.thermal.ThermalStreams;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Every conversion the plant's machines make has a category here; Create's own recipe types stay in Create's categories. */
@JeiPlugin
public class CreatePlantJeiPlugin implements IModPlugin {
    private final SynthesisRecipeLinks synthesisLinks = new SynthesisRecipeLinks();
    /** A category, the Ponder storyboard that shows its machine, and the blocks that make the machine. */
    private record Kind(RecipeType<PlantProcess> type, String storyboard, ItemLike icon, ItemLike... catalysts) {
        Kind(String id, String storyboard, ItemLike icon, ItemLike... catalysts) { this(RecipeType.create(CreatePlant.MOD_ID, id, PlantProcess.class), storyboard, icon, catalysts); }
    }
    private static List<Kind> kinds;

    private static List<Kind> kinds() {
        if (kinds == null) kinds = List.of(
            new Kind("irradiation_products", "nuclear/rbmk_core", CENuclear.FUEL_ROD.get(), CENuclear.FUEL_ROD.get()),
            new Kind("fusion_service", "nuclear/fusion_tokamak", CEFusion.IRRADIATION_CARTRIDGE.get(), CEFusion.IRRADIATION_CARTRIDGE.get()),
            new Kind("forging_byproducts", "plant/steel_forging", CEItems.STEEL_SLAG.get(), CEItems.PIG_IRON.get()),
            new Kind("cubic_pressing", "plant/cubic_press", CEHighStressMachines.PRESS.get(), CEHighStressMachines.PRESS.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("stirred_reactor", "plant/reactor_plant_stirred", CEReactor.INTERNALS.get(ReactorModule.STIRRED).get(), CETransport.LIDDED_VESSEL.get(), CEReactor.INTERNALS.get(ReactorModule.STIRRED).get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("tray_reactor", "plant/uranium_preparation", CEReactor.INTERNALS.get(ReactorModule.ABSORBER).get(), CETransport.LIDDED_VESSEL.get(), CEReactor.INTERNALS.get(ReactorModule.ABSORBER).get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("reaction_vessel", "plant/reaction_tower", CEReactor.CATALYST_BASKET.get(), CETransport.REACTION_TOWER.get(), CEReactor.CATALYST_BASKET.get()),
            new Kind("fractionating_column", "plant/fractionating_column", CEDistillation.COLUMN.get(), CEDistillation.COLUMN.get()),
            new Kind("gas_centrifuge", "plant/gas_centrifuge", CEHighStressMachines.CENTRIFUGE.get(), CEHighStressMachines.CENTRIFUGE.get()),
            new Kind("cell_electrolysis", "plant/electrolysis_cell", CEElectrolysis.CELL.get(), CEElectrolysis.CONTROLLER.get(), CEElectrolysis.ELECTRODE_PLATE.get(), CEElectrolysis.CELL.get()),
            new Kind("solar_salt", "plant/open_tank", CEBlocks.TANK_CASING.get(), CEBlocks.TANK_CASING.get()),
            new Kind("steam_generation", "plant/tubular_boiler", CETransport.HORIZONTAL_FLUID_TANK.get(), CETransport.HORIZONTAL_FLUID_TANK.get(), AllBlocks.FLUID_TANK.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("steam_turbine", "turbine/multiblock", CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.LOW_PRESSURE_TURBINE_UNIT.get()),
            new Kind("fuel_cell", "plant/fuel_cell", CEFuelCell.FUEL_CELL.get(), CEFuelCell.FUEL_CELL.get()),
            new Kind("electrolysis_furnace", "plant/electrolysis_furnace", CEFurnace.BUSBAR.get(), CEFurnace.BUSBAR.get(), CEFurnace.SHELL.get(), CEFurnace.ANODE.get()),
            new Kind("gas_compression", "chemistry/reciprocating_compressor", CEReciprocating.COMPRESSOR.get(), CEReciprocating.COMPRESSOR.get(), CEReciprocating.CYLINDER.get()),
            new Kind("haber_bosch", "plant/reaction_tower", CETransport.REACTION_TOWER.get(), CETransport.REACTION_TOWER.get(), CEReactor.CATALYST_BASKET.get(), CEItems.IRON_CATALYST.get()),
            new Kind("absorption_tower", "plant/large_absorption_tower", CEReactor.LARGE_ABSORPTION_TOWER.get(), CEReactor.LARGE_ABSORPTION_TOWER.get()),
            new Kind("entrained_gasification", "plant/entrained_gasifier", CEGasifier.GASIFIER.get(), CEGasifier.GASIFIER.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("fertilizer_filling", "plant/liquid_fertilizer", AllBlocks.SPOUT.get(), AllBlocks.SPOUT.get()),
            new Kind("clarifying", "plant/wet_processing", CEWetProcessing.CLARIFIER.get(), CEWetProcessing.CLARIFIER.get()),
            new Kind("converter", "plant/converter", CEConverter.SHELL.get(), CEConverter.SHELL.get()),
            new Kind("solvent_extraction", "plant/fuel_cycle", CEFuelCycle.EXTRACTION_COLUMN.get(), CEFuelCycle.EXTRACTION_COLUMN.get()),
            new Kind("vitrification", "plant/fuel_cycle", CEFuelCycle.MELTER.get(), CEFuelCycle.MELTER.get()),
            new Kind("industrial_delivery", "plant/industrial_dispatch", CEIndustrialExpansion.DISPATCH.get(), CEIndustrialExpansion.DISPATCH.get(), AllBlocks.ITEM_VAULT.get()),
            new Kind("helium_refrigeration", "plant/fusion_helium", CEFusionFuel.COLD_BOX.get(), CEFusionFuel.COLD_BOX.get(), CEFusionFuel.REGENERATOR.get()),
            new Kind("ammonia_separator", "chemistry/ammonia_separator", CETransport.AMMONIA_SEPARATOR.get(), CETransport.AMMONIA_SEPARATOR.get(), CETransport.REACTION_TOWER.get()),
            new Kind("process_heating", "plant/ore_recovery", AllBlocks.BLAZE_BURNER.get(), CETransport.LIDDED_VESSEL.get(), AllBlocks.BASIN.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("heat_exchange", "plant/heat_exchanger", CEHeating.TUBE_TANK.get(), CEHeating.TUBE_TANK.get(), CEHeating.JACKET.get()),
            new Kind("process_cooling", "plant/module_cooling", CEProcessBlocks.COOLER_RADIATOR.get(), CEProcessBlocks.COOLER_RADIATOR.get(), CEProcessBlocks.COOLING_FAN.get()));
        return kinds;
    }

    private static RecipeType<PlantProcess> type(String id) {
        return kinds().stream().filter(kind -> kind.type().getUid().getPath().equals(id)).findFirst().orElseThrow().type();
    }

    private static Component note(String key, Object... args) { return Component.translatable("jei." + CreatePlant.MOD_ID + ".note." + key, args); }
    private static FluidStack f(Fluid fluid, int amount) { return new FluidStack(fluid, amount); }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        for (Kind kind : kinds()) registration.addRecipeCategories(new PlantProcessCategory(kind.type(), kind.icon(), kind.storyboard(), registration.getJeiHelpers().getGuiHelper()));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Kind kind : kinds()) registration.addRecipeCatalysts(kind.type(), kind.catalysts());
    }

    @Override public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(synthesisLinks);
    }

    private static ItemStack stack(String name) { return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(CreatePlant.location(name)).getDefaultInstance(); }
    private static PlantProcess service(String input, String output, String key) { return new PlantProcess(List.of(List.of(stack(input))), List.of(), List.of(), List.of(stack(output)), List.of(), note(key)); }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level != null) registration.addRecipes(type("entrained_gasification"), Minecraft.getInstance().level.getRecipeManager()
            .getAllRecipesFor(CERecipeTypes.ENTRAINED_GASIFICATION.<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.EntrainedGasificationRecipe>getType())
            .stream().flatMap(holder -> {
                var recipe = holder.value();
                var steam = Arrays.asList(recipe.steam().getFluids());
                if (steam.isEmpty()) return java.util.stream.Stream.empty();
                return java.util.stream.Stream.of(new PlantProcess(List.of(Arrays.asList(recipe.ingredient().getItems())),
                    List.of(steam.getFirst()), List.of(), List.of(), List.of(recipe.hotGasResult()),
                    note("entrained_gasification", recipe.duration())).withFluidOptions(List.of(steam)));
            }).toList());
        registration.addRecipes(type("clarifying"), wetReactions(CERecipeTypes.CLARIFYING));
        registration.addRecipes(type("irradiation_products"), List.of(
            new PlantProcess(List.of(List.of(stack("fuel_rod"), stack("compact_enriched_fuel_rod"), stack("mox_fuel_rod"))), List.of(), List.of(), List.of(stack("spent_fuel_rod")), List.of(), note("spent_fuel")),
            service("control_rod", "spent_control_rod", "spent_control"),
            service("lithium_target_rod", "irradiated_lithium_target_rod", "irradiated_target")));
        registration.addRecipes(type("fusion_service"), List.of(service("irradiation_cartridge", "irradiated_cartridge", "irradiated_cartridge"), service("tungsten_divertor_cassette", "spent_divertor_cassette", "spent_divertor")));
        registration.addRecipes(type("forging_byproducts"), List.of(new PlantProcess(List.of(List.of(CEItems.PIG_IRON.toStack())), List.of(), List.of(), List.of(CEItems.STEEL_INGOT.toStack(), CEItems.STEEL_SLAG.toStack()), List.of(), note("forging_byproducts"))));
        if (Minecraft.getInstance().level != null) {
            registration.addRecipes(type("cell_electrolysis"), ElectrolysisProcess.all().stream().filter(ElectrolysisProcess::electrowinning).map(recipe -> new PlantProcess(List.of(), List.of(f(recipe.feed(), recipe.input())), List.of(), List.of(new ItemStack(recipe.metal(), recipe.metalAmount())), List.of(f(recipe.recovered(), recipe.recoveredAmount())), note("electrowinning", Math.round(recipe.cycleWork() / 1000)))).toList());
        }
        if (Minecraft.getInstance().level != null) registration.addRecipes(type("cubic_pressing"), Minecraft.getInstance().level.getRecipeManager()
            .getAllRecipesFor(CERecipeTypes.CUBIC_PRESSING.<net.createplant.recipe.CubicPressRecipe.Input, net.createplant.recipe.CubicPressRecipe>getType()).stream().map(holder -> {
                var recipe = holder.value();
                return new PlantProcess(recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList(), List.of(), List.of(),
                    recipe.getRollableResultsAsItemStacks(), List.of(), note("cubic_pressing", recipe.getProcessingDuration(), Math.round(recipe.getRollableResults().size() > 1 ? recipe.getRollableResults().get(1).getChance() * 100 : 0)));
            }).toList());
        registration.addRecipes(type("stirred_reactor"), reactions(ReactorModule.STIRRED));
        registration.addRecipes(type("tray_reactor"), reactions(ReactorModule.ABSORBER));
        var sprayer = CEPortableTools.CHEMICAL_SPRAYER.toStack();
        var filled = sprayer.copy();
        var fertilizer = f(CEProcessFluids.LIQUID_FERTILIZER.get(), 1000);
        new net.createplant.item.PortableToolFluidHandler(filled).fill(fertilizer,
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        registration.addRecipes(type("fertilizer_filling"), List.of(new PlantProcess(
            List.of(List.of(sprayer)), List.of(fertilizer), List.of(), List.of(filled), List.of(), note("fertilizer_filling"))));
        for (var module : List.of(ProcessModule.REACTION_VESSEL, ProcessModule.FRACTION_SEPARATOR, ProcessModule.GAS_CENTRIFUGE, ProcessModule.ABSORPTION_TRAYS)) {
            List<PlantProcess> list = new ArrayList<>();
            for (ProcessRecipe recipe : ProcessRecipe.all()) if (recipe.module() == module && !recipe.id().equals("wash_raw_process_gas")) {
                boolean fractionation=module==ProcessModule.FRACTION_SEPARATOR;
                var inputs=recipe.inputs();
                Component conditions = fractionation ? note("fractionation",recipe.ticks(),DistillationProfile.requiredHeight(recipe.id()))
                    : module == ProcessModule.GAS_CENTRIFUGE ? note("centrifuge_stages", recipe.ticks(), net.createplant.block.entity.GasCentrifugeBlockEntity.requiredStages(recipe.id())) : module == ProcessModule.ABSORPTION_TRAYS ? note("absorption_tower", recipe.ticks()) : note("reactor", recipe.ticks());
                if(recipe.requiredHeat()!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE) conditions=conditions.copy().append(" · ").append(Component.translatable("create."+recipe.requiredHeat().getTranslationKey()));
                if(inputs.stream().anyMatch(input->PlantProcess.acceptedFluids(input).size()>1))conditions=note("cooled_feed").copy().append(" ").append(conditions);
                var entry = new PlantProcess(List.of(), inputs, recipe.catalyst() == Items.AIR ? List.of() : List.of(new ItemStack(recipe.catalyst())),
                    List.of(), fractionation ? DistillationProfile.fraction(recipe.id()).outputs() : recipe.outputs(), conditions)
                    .withFluidOptions(inputs.stream().map(PlantProcess::acceptedFluids).toList());
                if (module != ProcessModule.GAS_CENTRIFUGE) {
                    int levels = fractionation ? DistillationProfile.requiredHeight(recipe.id()) : module == ProcessModule.ABSORPTION_TRAYS
                        ? ReactorModule.TOWER.minSections : net.createplant.block.entity.ReactionTowerBlockEntity.MIN_LAYERS;
                    entry = entry.withConditions(recipe.ticks(), recipe.requiredHeat(), levels);
                }
                list.add(entry);
            }
            if (module == ProcessModule.FRACTION_SEPARATOR) for (var fraction : DistillationProfile.registered())
                list.add(new PlantProcess(List.of(), List.of(fraction.input()), List.of(), List.of(), DistillationProfile.fraction(fraction.id()).outputs(), note("fractionation", fraction.ticks())));
            registration.addRecipes(type(module == ProcessModule.REACTION_VESSEL ? "reaction_vessel" : module == ProcessModule.FRACTION_SEPARATOR ? "fractionating_column" : module == ProcessModule.ABSORPTION_TRAYS ? "absorption_tower" : "gas_centrifuge"), list);
        }
        int ticks = 100;long energy = Math.round(ticks * ElectrolysisProcess.JOULES_PER_PLATE / 1000);
        int diaphragmTicks = 5000;long diaphragmEnergy = Math.round(diaphragmTicks * ElectrolysisProcess.JOULES_PER_PLATE / 1000);
        registration.addRecipes(type("cell_electrolysis"), List.of(
            PlantProcess.fluids(List.of(f(Fluids.WATER, ElectrolysisProcess.WATER_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), 2 * ticks), f(CEFluids.OXYGEN_GAS.get(), ticks)), note("electrolysis", ticks, energy)),
            PlantProcess.fluids(List.of(f(CEChlorAlkali.BRINE.get(), ElectrolysisProcess.BRINE_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), ticks), f(CEChlorAlkali.CHLORINE.get(), ticks),
                f(CEChlorAlkali.CAUSTIC_SODA.get(), ElectrolysisProcess.SODA_PER_PLATE * ticks)), note("electrolysis_brine", ticks, energy)),
            new PlantProcess(List.of(List.of(CEItems.ASBESTOS.toStack())), List.of(f(CEChlorAlkali.BRINE.get(), ElectrolysisProcess.BRINE_PER_PLATE * diaphragmTicks)), List.of(), List.of(),
                List.of(f(CEFluids.HYDROGEN_GAS.get(), diaphragmTicks), f(CEChlorAlkali.CHLORINE.get(), diaphragmTicks),
                    f(CEChlorAlkali.CAUSTIC_SODA.get(), ElectrolysisProcess.SODA_PER_PLATE * diaphragmTicks * 5 / 4)), note("electrolysis_brine_diaphragm", diaphragmTicks, diaphragmEnergy)),
            PlantProcess.fluids(List.of(f(CEUranium.HYDROGEN_FLUORIDE.get(), ElectrolysisProcess.FLUORIDE_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), ticks), f(CEUranium.FLUORINE.get(), ticks)), note("electrolysis_fluoride", ticks, energy)),
            PlantProcess.fluids(List.of(f(CEUranium.SULFUROUS_ACID.get(), ElectrolysisProcess.SULFUROUS_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), ticks), f(CEUranium.SULFURIC_ACID.get(), ticks)),
                note("electrolysis_sulfurous", ticks, Math.round(ticks * ElectrolysisProcess.SULFUROUS_JOULES_PER_PLATE / 1000)))));
        registration.addRecipes(type("fuel_cell"), List.of(PlantProcess.fluids(List.of(f(CEFluids.HYDROGEN_GAS.get(), 100), f(CEFluids.OXYGEN_GAS.get(), 50)),
            List.of(f(Fluids.WATER, (int) (100 * FuelCellBlockEntity.WATER_PER_HYDROGEN))), note("fuel_cell", Math.round(100 * FuelCellBlockEntity.JOULES_PER_HYDROGEN / 1000 * 10) / 10.0))));
        registration.addRecipes(type("solar_salt"), List.of(new PlantProcess(List.of(), List.of(f(Fluids.WATER, TankCasingBlockEntity.WATER_PER_SALT)), List.of(),
            List.of(new ItemStack(CEChlorAlkali.SALT.get())), List.of(), note("solar_salt", TankCasingBlockEntity.EVAPORATION_PER_CELL))));
        registration.addRecipes(type("electrolysis_furnace"), ElectrolysisFurnaceBlockEntity.reductions().entrySet().stream().map(entry -> new PlantProcess(
            List.of(List.of(new ItemStack(entry.getKey())), List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL))), List.of(), List.of(),
            List.of(new ItemStack(entry.getValue().metal())), List.of(), note("electrolysis_furnace", Math.round(entry.getValue().joules() / 1000)))).toList());
        registration.addRecipes(type("gas_compression"), net.createplant.thermal.Compression.steps().entrySet().stream().map(step -> {
            boolean air = step.getKey() == CEFluids.AIR_GAS.get();
            int amount = air ? 1600 : 1000;
            int speed = net.createplant.block.entity.ReciprocatingCompressorBlockEntity.MIN_RPM;
            return PlantProcess.fluids(List.of(f(step.getKey(), amount)), List.of(f(step.getValue(), amount)),
                air ? note("air_compression", speed, amount, amount / net.createplant.thermal.Grade.Pressure.PRESSURISED.storageRatio())
                    : note("compression", speed));
        }).toList());
        registration.addRecipes(type("haber_bosch"), PlantProcess.ammoniaSynthesis());
        registration.addRecipes(type("converter"), converterReactions());
        registration.addRecipes(type("solvent_extraction"), fuelCycleReactions(CERecipeTypes.SOLVENT_EXTRACTION));
        registration.addRecipes(type("vitrification"), fuelCycleReactions(CERecipeTypes.VITRIFICATION));
        registration.addRecipes(type("industrial_delivery"), deliveries());
        var liquefaction=new ArrayList<PlantProcess>();
        for(var gas:List.of(CEFluids.NITROGEN_GAS.get(),CEFluids.OXYGEN_GAS.get(),CEFusion.HELIUM_GAS.get())){
            var conversion=ThermalStreams.liquefaction(f(gas,1));int water=gas==CEFusion.HELIUM_GAS.get() ? 200 : 50;
            liquefaction.add(PlantProcess.fluids(List.of(f(gas,conversion.inputAmount()),f(Fluids.WATER,water)),List.of(conversion.result(1),f(CEProcessFluids.WARM_WATER.get(),water)),note("helium_refrigeration")));
        }
        registration.addRecipes(type("helium_refrigeration"),liquefaction);
        registration.addRecipes(type("cell_electrolysis"), List.of(PlantProcess.fluids(List.of(f(CEFusionFuel.HEAVY_WATER.get(), ElectrolysisProcess.WATER_PER_PLATE * ticks)),
            List.of(f(CEFusion.DEUTERIUM_GAS.get(), 2 * ticks), f(CEFluids.OXYGEN_GAS.get(), ticks)), note("electrolysis", ticks, energy))));
        var generation=new ArrayList<PlantProcess>();var expansion=new ArrayList<PlantProcess>();
        for(var grade:net.createplant.thermal.SteamGrades.values()){
            generation.add(new PlantProcess(List.of(),List.of(f(Fluids.WATER,1000)),List.of(new ItemStack(grade==net.createplant.thermal.SteamGrades.ORDINARY ? AllBlocks.FLUID_TANK.get() : CETransport.HORIZONTAL_FLUID_TANK.get()),AllBlocks.BLAZE_BURNER.asStack()),List.of(),List.of(f(grade.fluid(),1000)),note("steam_generation_"+grade.name().toLowerCase(java.util.Locale.ROOT))));
            for(boolean low:List.of(false,true))expansion.add(PlantProcess.fluids(List.of(f(grade.fluid(),1000)),List.of(net.createplant.thermal.SteamGrades.outlet(f(grade.fluid(),1000),1000,low)),note("steam_turbine_"+(low ? "low" : "high"),Math.round(grade.workPerMb(low)))));
        }
        registration.addRecipes(type("steam_generation"),generation);registration.addRecipes(type("steam_turbine"),expansion);
        registration.addRecipes(type("ammonia_separator"),net.createplant.block.entity.AmmoniaSeparatorBlockEntity.recipes().stream().map(recipe->PlantProcess.fluids(recipe.inputs(),recipe.outputs(),note("ammonia_separator"))).toList());
        // A hundred batches, so that the oxygen leaving as liquid shows in whole mB.
        int airBatches = 100, perLiquid = DistillationProfile.gasPerLiquid(DistillationProfile.OXYGEN);
        List<FluidStack> fractions = new ArrayList<>();
        for (int i = 0; i < DistillationProfile.PRODUCT.length; i++) {
            int slot = DistillationProfile.PRODUCT[i], amount = DistillationProfile.PRODUCT_AMOUNT[i] * airBatches;
            fractions.add(slot == DistillationProfile.OXYGEN ? f(DistillationProfile.liquid(slot), amount / perLiquid) : f(DistillationProfile.fluid(slot), amount));
        }
        var air=List.of(f(CEProcessFluids.COMPRESSED_AIR.get(),DistillationProfile.AIR_BATCH*airBatches));
        registration.addRecipes(type("fractionating_column"),List.of(PlantProcess.fluids(air,fractions,note("air_separation",DistillationProfile.CYCLE_TICKS,perLiquid))));
        List<PlantProcess> heating = new ArrayList<>(), cooling = new ArrayList<>();
        heating.add(PlantProcess.fluids(List.of(f(CEPetroFluids.CRUDE_OIL.get(),1000)),List.of(f(CEProcessFluids.PREHEATED_CRUDE_OIL.get(),1000)),Component.translatable("create."+com.simibubi.create.content.processing.recipe.HeatCondition.HEATED.getTranslationKey())));
        var exchanges=new ArrayList<PlantProcess>();
        for(var hot:ThermalStreams.coolingInputs()){
            var conversion=ThermalStreams.cooling(hot);var hotInput=hot.copyWithAmount(conversion.inputAmount()*100);
            cooling.add(PlantProcess.fluids(List.of(hotInput),List.of(conversion.result(100)),note("cooling")));
            for(var coolant:ThermalStreams.coolants()){
                var cold=ThermalStreams.coolant(f(coolant,1));exchanges.add(PlantProcess.fluids(List.of(hotInput,f(coolant,cold.inputAmount()*100)),List.of(conversion.result(100),cold.result(100)),note("heat_exchange")));
            }
        }
        registration.addRecipes(type("heat_exchange"),exchanges);
        registration.addRecipes(type("process_heating"), heating);
        registration.addRecipes(type("process_cooling"), cooling);
    }

    private static List<PlantProcess> reactions(ReactorModule module) {
        List<PlantProcess> list = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return list;
        for (RecipeHolder<PressureReactionRecipe> holder : level.getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<net.minecraft.world.item.crafting.RecipeInput, PressureReactionRecipe>getType())) {
            var recipe = holder.value();
            if (recipe.module() != module) continue;
            List<List<ItemStack>> items = recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList();
            List<FluidStack> fluids = recipe.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList();
            int ticks = recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : 100;
            Component conditions=note(module==ReactorModule.ABSORBER ? "tray_reactor" : "stirred_reactor",ticks,Component.translatable("create."+recipe.getRequiredHeat().getTranslationKey()));
            var products = recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList();
            var entry = new PlantProcess(items, fluids, List.of(), products, recipe.getFluidResults(), conditions)
                .withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
            if (items.size() + fluids.size() <= 6 && products.size() + recipe.getFluidResults().size() <= 6)
                entry = entry.withConditions(ticks, recipe.getRequiredHeat(), module.minSections);
            list.add(entry);
        }
        return list;
    }

    private static List<PlantProcess> wetReactions(CERecipeTypes type) {
        var level = Minecraft.getInstance().level;if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(type.<net.minecraft.world.item.crafting.RecipeInput, com.simibubi.create.content.processing.recipe.StandardProcessingRecipe<net.minecraft.world.item.crafting.RecipeInput>>getType()).stream().map(holder -> {
            var recipe = holder.value();
            return new PlantProcess(recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList(),
                recipe.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList(),
                type == CERecipeTypes.CLARIFYING ? List.of(CEWetProcessing.FLOCCULANT.toStack()) : List.of(),
                recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList(), recipe.getFluidResults(), note(type.getId().getPath(), recipe.getProcessingDuration()))
                .withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getFluids())).toList());
        }).toList();
    }

    private static List<PlantProcess> fuelCycleReactions(CERecipeTypes type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(type.<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.FuelCycleRecipe>getType()).stream().map(holder -> {
            var recipe = holder.value();
            return new PlantProcess(recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList(),
                recipe.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList(), List.of(),
                recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList(), recipe.getFluidResults(), note(type.getId().getPath(), recipe.getProcessingDuration())).withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
        }).toList();
    }
    private static List<PlantProcess> deliveries() {
        var level = Minecraft.getInstance().level;if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(CERecipeTypes.INDUSTRIAL_DELIVERY.<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.IndustrialDeliveryRecipe>getType()).stream().map(holder -> {
            var recipe = holder.value();var inputs = new java.util.LinkedHashMap<String, List<ItemStack>>();
            for (var ingredient : recipe.getIngredients()) {
                var options = Arrays.asList(ingredient.getItems());
                String key = options.stream().map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().collect(java.util.stream.Collectors.joining(","));
                if (!inputs.containsKey(key)) inputs.put(key, options.stream().map(ItemStack::copy).toList());
                else inputs.get(key).forEach(stack -> stack.grow(1));
            }
            return new PlantProcess(List.copyOf(inputs.values()), recipe.getFluidIngredients().stream().map(input -> input.getFluids()[0]).toList(), List.of(),
                recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList(), List.of(), note("industrial_delivery", recipe.getProcessingDuration())).withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
        }).toList();
    }

    /** Converter recipes retain their large ingredient counts instead of expanding them into crafting-grid duplicates. */
    private static List<PlantProcess> converterReactions() {
        List<PlantProcess> list = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return list;
        for (RecipeHolder<ConverterRecipe> holder : level.getRecipeManager().getAllRecipesFor(
            CERecipeTypes.CONVERTER.<net.minecraft.world.item.crafting.RecipeInput, ConverterRecipe>getType())) {
            ConverterRecipe recipe = holder.value();
            List<List<ItemStack>> items = recipe.itemIngredients().stream()
                .map(ingredient -> Arrays.asList(ingredient.getItems())).toList();
            List<FluidStack> fluids = List.of(recipe.gasIngredient().getFluids()[0]);
            List<FluidStack> byproducts = recipe.byproduct().isEmpty() ? List.of() : List.of(recipe.byproduct());
            list.add(new PlantProcess(items, fluids, List.of(), List.of(recipe.result()), byproducts,
                note("converter", recipe.duration())).withFluidOptions(List.of(Arrays.asList(recipe.gasIngredient().getFluids()))));
        }
        return list;
    }

    private static void verifyRecipeTrace(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var manager=runtime.getRecipeManager();
        var focus=runtime.getJeiHelpers().getFocusFactory();
        var feedFocus=focus.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),1000));
        var synthesis=manager.createRecipeLookup(type("haber_bosch")).limitFocus(List.of(feedFocus)).get().toList();
        if(synthesis.isEmpty() || synthesis.stream().noneMatch(r->r.kept().stream().anyMatch(i->i.is(CEItems.IRON_CATALYST.get()))))throw new IllegalStateException("JEI cannot trace the compressed feed into Haber-Bosch with iron catalyst");
        var gasFocus=focus.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(CEProcessFluids.SYNTHESIS_GAS.get(),1000));
        if(manager.createRecipeLookup(type("gas_compression")).limitFocus(List.of(gasFocus)).get().noneMatch(r->r.fluidResults().stream().anyMatch(f->f.is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()))))
            throw new IllegalStateException("JEI cannot trace synthesis gas through the compressor");
        var productFocus=focus.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(CEProcessFluids.SYNTHESIS_GAS.get(),1000));
        if(manager.createRecipeLookup(type("stirred_reactor")).limitFocus(List.of(productFocus)).get().noneMatch(r->r.fluids().stream().anyMatch(f->f.is(CEFluids.HYDROGEN_GAS.get())) && r.fluids().stream().anyMatch(f->f.is(CEFluids.NITROGEN_GAS.get()))))throw new IllegalStateException("JEI has no H2/N2 recipe for synthesis gas");
        var counts=new java.util.LinkedHashMap<String,Integer>();
        for(var kind:kinds()) {
            var recipes=manager.createRecipeLookup(kind.type()).get().toList();counts.put(kind.type().getUid().getPath(),recipes.size());
            for(var recipe:recipes)for(int i=0;i<recipe.fluids().size();i++) {
                var options=recipe.fluidOptions().get(i);
                int amount=recipe.fluids().get(i).getAmount();
                if(options.isEmpty() || options.stream().anyMatch(f->f.isEmpty() || f.getAmount()!=amount))throw new IllegalStateException("Missing or mis-sized JEI fluid alternatives");
            }
        }
        var level=Minecraft.getInstance().level;
        for(var module:List.of(ReactorModule.STIRRED,ReactorModule.ABSORBER)) {
            int expected=(int)level.getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<net.minecraft.world.item.crafting.RecipeInput,PressureReactionRecipe>getType()).stream().filter(r->r.value().module()==module).count();
            if(counts.get(module==ReactorModule.STIRRED ? "stirred_reactor" : "tray_reactor")!=expected)throw new IllegalStateException("A pressure reaction is missing from JEI");
        }
        try {java.nio.file.Files.writeString(java.nio.file.Path.of("recipe-trace-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(counts));}catch(java.io.IOException e){throw new RuntimeException(e);}
        org.slf4j.LoggerFactory.getLogger(CreatePlantJeiPlugin.class).info("RECIPE TRACE PASSED: H2 + N2 -> synthesis gas -> Haber-Bosch; recipe counts {}",counts);
    }

    private static mezz.jei.api.runtime.IJeiRuntime checkRuntime;
    private static void auditAvailability(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var missing = new java.util.TreeSet<String>();var names = new java.util.TreeMap<String, String>();var keys = new java.util.TreeMap<String, String>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || item instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem) continue;
            var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, item.getDefaultInstance());
            if (runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().findAny().isEmpty()) missing.add(id.getPath());
        }
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            if ((!id.getNamespace().equals(CreatePlant.MOD_ID) && !id.getNamespace().equals("createdieselgenerators")) || id.getPath().startsWith("flowing_")) continue;
            names.put(id.toString(), fluid.getFluidType().getDescription().getString());keys.put(id.toString(), fluid.getFluidType().getDescriptionId());
        }
        try {java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("recipe-availability.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("withoutRecipe", missing, "fluidNames", names, "fluidKeys", keys)));} catch (java.io.IOException error) {throw new RuntimeException(error);}
    }

    public static void showCheckCategory(String id) {
        if(checkRuntime==null)throw new IllegalStateException("JEI trace check is not ready");
        if (id.startsWith("item/")) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id.substring(5)));
            var focus = checkRuntime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(item));
            checkRuntime.getRecipesGui().show(focus);
        } else if(id.equals("stirred_reactor")) {
            var focus=checkRuntime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(CEProcessFluids.SYNTHESIS_GAS.get(),1000));
            checkRuntime.getRecipesGui().show(focus);
        } else checkRuntime.getRecipesGui().showTypes(List.of(type(id)));
    }
    @Override public void onRuntimeUnavailable() {
        synthesisLinks.runtime(null);
        checkRuntime = null;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        synthesisLinks.runtime(runtime);
        if (Boolean.getBoolean("createplant.recipeTraceCheck") || Boolean.getBoolean("createplant.recipeScreenCheck")) checkRuntime = runtime;
        if (Boolean.getBoolean("createplant.recipeTraceCheck")) verifyRecipeTrace(runtime);
        if (Boolean.getBoolean("createplant.recipeAvailabilityCheck")) auditAvailability(runtime);
        var hidden=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .map(ItemStack::new).filter(net.createplant.compat.diesel.DieselIntegration::disabledItem).toList();
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,hidden);
        var legacyGas = net.createplant.registry.CESafety.RAW_PROCESS_GAS.get();
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
            List.of(new ItemStack(legacyGas.getBucket())));
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,
            List.of(new FluidStack(legacyGas, 1000)));
        if (Boolean.getBoolean("createplant.clientQualityCheck")) {
            for (var stack:hidden) if (runtime.getIngredientVisibility().isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,stack))
                throw new IllegalStateException("CDG distillation remains visible in JEI");
            org.slf4j.LoggerFactory.getLogger(CreatePlantJeiPlugin.class).info("CDG JEI PASSED: distillation equipment hidden");
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return CreatePlant.location("jei_plugin");
    }

}
