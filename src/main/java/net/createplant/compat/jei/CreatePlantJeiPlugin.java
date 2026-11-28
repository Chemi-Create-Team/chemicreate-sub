package net.createplant.compat.jei;

import net.createplant.registry.CEIsotopeGases;
import net.createplant.registry.CESharedChemicals;
import net.createplant.thermal.FluidForm;
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
import net.createplant.factory.FactoryKind;
import net.createplant.recipe.ContinuousFactoryRecipe;
import net.createplant.recipe.ConverterRecipe;
import net.createplant.recipe.LargePressingRecipe;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.recipe.SprayingRecipe;
import net.createplant.registry.*;
import net.createplant.thermal.ThermalStreams;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
            new Kind("large_pressing", "plant/large_press", CELargePress.PRESS.get(), CELargePress.PRESS.get(), CELargePress.DEPOT.get()),
            new Kind("rotary_kiln", "plant/rotary_kiln", CEContinuousFactory.KILN.get(), CEContinuousFactory.KILN.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("rolling_mill", "plant/rolling_mill", CEContinuousFactory.MILL.get(), CEContinuousFactory.MILL.get(), CEContinuousFactory.STAND.get()),
            new Kind("coil_winder", "plant/coil_winder", CEContinuousFactory.WINDER.get(), CEContinuousFactory.WINDER.get(), CEContinuousFactory.BRAKE.get()),
            new Kind("stirred_reactor", "plant/reactor_plant_stirred", CEReactor.STIRRER.get(), CETransport.STIRRED_TANK.get(), CEReactor.STIRRER.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("tray_reactor", "plant/uranium_preparation", CEReactor.TRAY.get(), CETransport.REACTION_TOWER.get(), CEReactor.TRAY.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("reaction_vessel", "plant/reaction_tower", CEReactor.TRAY.get(), CETransport.REACTION_TOWER.get(), CEReactor.TRAY.get()),
            new Kind("fractionating_column", "plant/fractionating_column", CEDistillation.COLUMN.get(), CEDistillation.COLUMN.get()),
            new Kind("gas_centrifuge", "plant/gas_centrifuge", CEHighStressMachines.CENTRIFUGE.get(), CEHighStressMachines.CENTRIFUGE.get()),
            new Kind("cell_electrolysis", "plant/electrolysis_cell", CEElectrolysis.CELL.get(), CEElectrolysis.CONTROLLER.get(), CEElectrolysis.ELECTRODE_PLATE.get(), CEElectrolysis.CELL.get()),
            new Kind("solar_salt", "plant/open_tank", CEBlocks.TANK_CASING.get(), CEBlocks.TANK_CASING.get()),
            new Kind("steam_generation", "plant/tubular_boiler", CETransport.STAINLESS_BOILER.get(), CETransport.STAINLESS_BOILER.get(), CETransport.HORIZONTAL_FLUID_TANK.get(), AllBlocks.FLUID_TANK.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("steam_turbine", "turbine/multiblock", CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.LOW_PRESSURE_TURBINE_UNIT.get()),
            new Kind("fuel_cell", "plant/fuel_cell", CEFuelCell.FUEL_CELL.get(), CEFuelCell.FUEL_CELL.get()),
            new Kind("electrolysis_furnace", "plant/electrolysis_furnace", CEFurnace.BUSBAR.get(), CEFurnace.BUSBAR.get(), CEFurnace.SHELL.get(), CEFurnace.ANODE.get()),
            new Kind("gas_compression", "chemistry/reciprocating_compressor", CEReciprocating.COMPRESSOR.get(), CEReciprocating.COMPRESSOR.get(), CEReciprocating.CYLINDER.get()),
            new Kind("haber_bosch", "plant/reaction_tower", CETransport.REACTION_TOWER.get(), CETransport.REACTION_TOWER.get(), CEReactor.TRAY.get(), CEItems.IRON_CATALYST.get()),
            new Kind("absorption_tower", "plant/large_absorption_tower", CEReactor.LARGE_ABSORPTION_TOWER.get(), CEReactor.LARGE_ABSORPTION_TOWER.get()),
            new Kind("entrained_gasification", "plant/entrained_gasifier", CEGasifier.GASIFIER.get(), CEGasifier.GASIFIER.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("spraying", "plant/agricultural_sprayer_gantry", CEAgriculture.SPRAYER.get(), CEAgriculture.SPRAYER.get()),
            new Kind("fertilizer_filling", "plant/liquid_fertilizer", AllBlocks.SPOUT.get(), AllBlocks.SPOUT.get()),
            new Kind("clarifying", "plant/wet_processing", CEWetProcessing.CLARIFIER.get(), CEWetProcessing.CLARIFIER.get()),
            new Kind("converter", "plant/converter", CEConverter.SHELL.get(), CEConverter.SHELL.get()),
            new Kind("solvent_extraction", "plant/fuel_cycle", CEFuelCycle.EXTRACTION_COLUMN.get(), CEFuelCycle.EXTRACTION_COLUMN.get()),
            new Kind("vitrification", "plant/fuel_cycle", CEFuelCycle.MELTER.get(), CEFuelCycle.MELTER.get()),
            new Kind("industrial_delivery", "plant/industrial_dispatch", CEIndustrialExpansion.DISPATCH.get(), CEIndustrialExpansion.DISPATCH.get(), AllBlocks.ITEM_VAULT.get()),
            new Kind("helium_refrigeration", "plant/fusion_helium", CEIsotopeGases.COLD_BOX.get(), CEIsotopeGases.COLD_BOX.get(), CEIsotopeGases.REGENERATOR.get()),
            new Kind("ammonia_separator", "chemistry/ammonia_separator", CETransport.AMMONIA_SEPARATOR.get(), CETransport.AMMONIA_SEPARATOR.get(), CETransport.REACTION_TOWER.get()),
            new Kind("process_heating", "plant/ore_recovery", AllBlocks.BLAZE_BURNER.get(), CETransport.LIDDED_VESSEL.get(), AllBlocks.BASIN.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("heat_exchange", "plant/heat_exchanger", CEHeating.TUBE_TANK.get(), CEHeating.TUBE_TANK.get(), CEHeating.JACKET.get()),
            new Kind("arm_assembly", "plant/industrial_arm", net.createplant.registry.CEArm.ARM_ITEM.get(), net.createplant.registry.CEArm.ARM_ITEM.get(),
                net.createplant.registry.CEArm.WELDING_TORCH.get(), AllBlocks.DEPOT.get()),
            new Kind("process_cooling", "plant/module_cooling", CEProcessBlocks.COOLER_RADIATOR.get(), CEProcessBlocks.COOLER_RADIATOR.get(), CEProcessBlocks.COOLING_FAN.get()));
        return kinds;
    }

    private static RecipeType<PlantProcess> type(String id) {
        return kinds().stream().filter(kind -> kind.type().getUid().getPath().equals(id)).findFirst().orElseThrow().type();
    }

    private static Component note(String key, Object... args) { return Component.translatable("jei." + CreatePlant.MOD_ID + ".note." + key, args); }
    private static FluidStack f(Fluid fluid, int amount) { return new FluidStack(fluid, amount); }
    private static FluidStack f(FluidForm form, int amount) { return form.stack(amount); }

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
    /** The item put on the depot and every part the steps use up go in; the torch is kept; the note lists the steps in order. */
    private static PlantProcess armAssembly(net.createplant.arm.ArmRecipe recipe) {
        List<List<ItemStack>> in = new java.util.ArrayList<>();
        in.add(List.of(new ItemStack(recipe.input())));
        var steps = Component.empty();
        for (int i = 0; i < recipe.steps().size(); i++) {
            var step = recipe.steps().get(i);
            step.consume().ifPresent(part -> in.add(List.of(new ItemStack(part.item(), part.count()))));
            if (i > 0) steps.append(" > ");
            steps.append(Component.translatable("jei." + CreatePlant.MOD_ID + ".arm_step." + step.pattern().id()));
            if (step.helpers() > 0) steps.append(Component.translatable("jei." + CreatePlant.MOD_ID + ".arm_step.helped"));
        }
        List<ItemStack> kept = recipe.usesTorch() ? List.of(net.createplant.registry.CEArm.WELDING_TORCH.toStack()) : List.of();
        return new PlantProcess(in, List.of(), kept, List.of(recipe.result().copy()), List.of(), note("arm_assembly", steps));
    }

    private static PlantProcess continuous(ContinuousFactoryRecipe recipe) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        inputs.add(Arrays.stream(recipe.feed().ingredient().getItems()).map(stack -> {
            var feed = stack.copyWithCount(recipe.feed().count());
            if (recipe.minimumDamage() != 0) feed.setDamageValue(recipe.minimumDamage());
            return feed;
        }).toList());
        recipe.auxiliary().ifPresent(aux -> inputs.add(Arrays.stream(aux.ingredient().getItems()).map(stack -> stack.copyWithCount(aux.count())).toList()));
        var text = note(recipe.machine().id + (recipe.cold() ? "_cold" : recipe.minimumDamage() > 0 ? "_worn" : recipe.heat() == 2 ? "_strong" : "")).copy();
        if (recipe.minStands() > 1 || recipe.maxStands() < 8)
            text.append(" ").append(note("stands", recipe.minStands() > 1 ? recipe.minStands() : 2, recipe.maxStands() < 8 ? recipe.maxStands() : 5));
        if (recipe.rpmTolerance() != .10f) text.append(" ").append(note("rpm_tolerance", Math.round(recipe.rpmTolerance() * 100)));
        return new PlantProcess(inputs, List.of(), List.of(), List.of(recipe.result().copy()), List.of(), text);
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var world = Minecraft.getInstance().level;
        if (world != null) for (var kind : FactoryKind.values()) {
            registration.addRecipes(type(kind.id), world.getRecipeManager()
                .getAllRecipesFor(CERecipeTypes.CONTINUOUS_PROCESSING.<RecipeInput, ContinuousFactoryRecipe>getType()).stream()
                .filter(holder -> holder.value().machine() == kind)
                .sorted(java.util.Comparator.comparing((RecipeHolder<ContinuousFactoryRecipe> holder) -> holder.value().minimumDamage() > 0)
                    .thenComparing(holder -> holder.id().getPath()))
                .map(holder -> continuous(holder.value())).toList());
        }
        if (world != null) registration.addRecipes(type("large_pressing"), world.getRecipeManager()
            .getAllRecipesFor(CERecipeTypes.LARGE_PRESSING.<SingleRecipeInput, LargePressingRecipe>getType()).stream().map(holder -> {
                var recipe = holder.value();
                return new PlantProcess(List.of(Arrays.asList(recipe.ingredient().getItems())), List.of(), List.of(),
                    List.of(recipe.result().copy()), List.of(), note("large_pressing"));
            }).toList());
        if (world != null) registration.addRecipes(type("spraying"), SprayingRecipe.available(world).stream().flatMap(holder -> {
            var recipe = holder.value();
            var fluids = Arrays.asList(recipe.liquid().getFluids());
            if (fluids.isEmpty()) return java.util.stream.Stream.empty();
            return java.util.stream.Stream.of(PlantProcess.fluids(List.of(fluids.getFirst()), List.of(),
                recipe.effect().description(recipe.liquid().amount())).withFluidOptions(List.of(fluids)));
        }).toList());
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
        if (world != null) registration.addRecipes(type("arm_assembly"), net.createplant.arm.ArmRecipe.all(world).stream().map(holder -> armAssembly(holder.value())).toList());
        registration.addRecipes(type("clarifying"), ordinaryRoutesFirst(wetReactions(CERecipeTypes.CLARIFYING)));
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
            registration.addRecipes(type(module == ProcessModule.REACTION_VESSEL ? "reaction_vessel" : module == ProcessModule.FRACTION_SEPARATOR ? "fractionating_column" : module == ProcessModule.ABSORPTION_TRAYS ? "absorption_tower" : "gas_centrifuge"), ordinaryRoutesFirst(list));
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
            PlantProcess.fluids(List.of(f(CESharedChemicals.HYDROGEN_FLUORIDE.get(), ElectrolysisProcess.FLUORIDE_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), ticks), f(CESharedChemicals.FLUORINE.get(), ticks)), note("electrolysis_fluoride", ticks, energy)),
            PlantProcess.fluids(List.of(f(CESharedChemicals.SULFUROUS_ACID.get(), ElectrolysisProcess.SULFUROUS_PER_PLATE * ticks)), List.of(f(CEFluids.HYDROGEN_GAS.get(), ticks), f(CESharedChemicals.SULFURIC_ACID.get(), ticks)),
                note("electrolysis_sulfurous", ticks, Math.round(ticks * ElectrolysisProcess.SULFUROUS_JOULES_PER_PLATE / 1000)))));
        registration.addRecipes(type("fuel_cell"), List.of(PlantProcess.fluids(List.of(f(CEFluids.HYDROGEN_GAS.get(), 100), f(CEFluids.OXYGEN_GAS.get(), 50)),
            List.of(f(Fluids.WATER, (int) (100 * FuelCellBlockEntity.WATER_PER_HYDROGEN))), note("fuel_cell", Math.round(100 * FuelCellBlockEntity.JOULES_PER_HYDROGEN / 1000 * 10) / 10.0))));
        registration.addRecipes(type("solar_salt"), List.of(new PlantProcess(List.of(), List.of(f(Fluids.WATER, TankCasingBlockEntity.WATER_PER_SALT)), List.of(),
            List.of(new ItemStack(CEChlorAlkali.SALT.get())), List.of(), note("solar_salt", TankCasingBlockEntity.EVAPORATION_PER_CELL))));
        registration.addRecipes(type("electrolysis_furnace"), ElectrolysisFurnaceBlockEntity.reductions().entrySet().stream().map(entry -> new PlantProcess(
            List.of(List.of(new ItemStack(entry.getKey())), List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL))), List.of(), List.of(),
            List.of(new ItemStack(entry.getValue().metal())), List.of(), note("electrolysis_furnace", Math.round(entry.getValue().joules() / 1000)))).toList());
        registration.addRecipes(type("gas_compression"), net.createplant.thermal.Compression.steps().stream().map(step -> {
            boolean air = step.low().is(CEFluids.AIR_GAS.get());
            int amount = air ? 1600 : 1000;
            int speed = net.createplant.block.entity.ReciprocatingCompressorBlockEntity.MIN_RPM;
            return PlantProcess.fluids(List.of(step.low().copyWithAmount(amount)), List.of(step.high().copyWithAmount(amount)),
                air ? note("air_compression", speed, amount, amount / net.createplant.thermal.Grade.Pressure.PRESSURISED.storageRatio())
                    : note("compression", speed));
        }).toList());
        registration.addRecipes(type("haber_bosch"), PlantProcess.ammoniaSynthesis());
        registration.addRecipes(type("converter"), ordinaryRoutesFirst(converterReactions()));
        registration.addRecipes(type("solvent_extraction"), ordinaryRoutesFirst(fuelCycleReactions(CERecipeTypes.SOLVENT_EXTRACTION)));
        registration.addRecipes(type("vitrification"), ordinaryRoutesFirst(fuelCycleReactions(CERecipeTypes.VITRIFICATION)));
        registration.addRecipes(type("industrial_delivery"), deliveries());
        var liquefaction=new ArrayList<PlantProcess>();
        for(var gas:List.of(CEFluids.NITROGEN_GAS.get(),CEFluids.OXYGEN_GAS.get(),CEIsotopeGases.HELIUM_GAS.get())){
            var conversion=ThermalStreams.liquefaction(f(gas,1));int water=gas==CEIsotopeGases.HELIUM_GAS.get() ? 200 : 50;
            liquefaction.add(PlantProcess.fluids(List.of(f(gas,conversion.inputAmount()),f(Fluids.WATER,water)),List.of(conversion.result(1),f(FluidForm.WARM_WATER,water)),note("helium_refrigeration")));
        }
        registration.addRecipes(type("helium_refrigeration"),liquefaction);
        registration.addRecipes(type("cell_electrolysis"), List.of(PlantProcess.fluids(List.of(f(CEIsotopeGases.HEAVY_WATER.get(), ElectrolysisProcess.WATER_PER_PLATE * ticks)),
            List.of(f(CEIsotopeGases.DEUTERIUM_GAS.get(), 2 * ticks), f(CEFluids.OXYGEN_GAS.get(), ticks)), note("electrolysis", ticks, energy))));
        var ordinarySteam = net.createplant.thermal.SteamGrades.ORDINARY.stack(1000);
        var highPressureSteam = net.createplant.thermal.SteamGrades.HIGH_PRESSURE.stack(1000);
        var water = List.of(f(Fluids.WATER,1000));
        var burner = AllBlocks.BLAZE_BURNER.asStack();
        var generation = List.of(
            new PlantProcess(List.of(), water, List.of(AllBlocks.FLUID_TANK.asStack(), burner), List.of(), List.of(ordinarySteam), note("steam_generation_ordinary")),
            new PlantProcess(List.of(), water, List.of(CETransport.HORIZONTAL_FLUID_TANK.toStack(), burner), List.of(), List.of(ordinarySteam), note("steam_generation_horizontal")),
            new PlantProcess(List.of(), water, List.of(CETransport.STAINLESS_BOILER.toStack(), burner), List.of(), List.of(highPressureSteam), note("steam_generation_high_pressure")));
        var expansion = new ArrayList<PlantProcess>();
        for (var grade : net.createplant.thermal.SteamGrades.values()) for (boolean low : List.of(false,true)) {
            var feed = grade.stack(1000);
            if (!low && grade == net.createplant.thermal.SteamGrades.EXHAUST) continue;
            String key = grade == net.createplant.thermal.SteamGrades.EXHAUST ? "steam_turbine_low_exhaust" : low ? "steam_turbine_low" : "steam_turbine_high";
            expansion.add(PlantProcess.fluids(List.of(feed), List.of(net.createplant.thermal.SteamGrades.outlet(feed, 1000, low)), note(key, Math.round(grade.workPerMb(low)))));
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
        var air=List.of(f(FluidForm.COMPRESSED_AIR,DistillationProfile.AIR_BATCH*airBatches));
        registration.addRecipes(type("fractionating_column"),List.of(PlantProcess.fluids(air,fractions,note("air_separation",DistillationProfile.CYCLE_TICKS,perLiquid))));
        List<PlantProcess> heating=new ArrayList<>(), cooling=new ArrayList<>(), exchanges=new ArrayList<>();
        for(var fluid:List.of(Fluids.WATER,CEPetroFluids.CRUDE_OIL.get(),CEProcessFluids.SLIME_COOLANT.get())) {
            var sample=f(fluid,1000);
            var transfer=net.createplant.thermal.PhaseChange.transfer(sample,9,4);
            int batches=transfer.batches(sample.getAmount());
            if(batches>0)heating.add(PlantProcess.fluids(List.of(sample.copyWithAmount(batches*transfer.consumed())),
                List.of(transfer.output(batches)),Component.translatable("fluid_state.create_refinery_n_reactors.jei.heating")));
        }
        for(var hot:List.of(f(FluidForm.WARM_WATER,1000),f(FluidForm.STEAM,1000),
            f(FluidForm.HIGH_PRESSURE_STEAM,1000),f(FluidForm.HOT_NITRIC_OXIDE_MIXTURE,1000),
            f(FluidForm.WARM_SLIME_COOLANT,1000))) {
            int temperature=net.createplant.thermal.FluidState.temperature(hot), pressure=net.createplant.thermal.FluidState.pressure(hot);
            var coolingStep=net.createplant.thermal.PhaseChange.transfer(hot,net.createplant.block.entity.RadiatorBlockEntity.cooling(hot,net.createplant.thermal.FluidState.kelvin(temperature)).level(),pressure);
            int batches=coolingStep.batches(hot.getAmount());
            if(batches>0)cooling.add(PlantProcess.fluids(List.of(hot.copyWithAmount(batches*coolingStep.consumed())),
                List.of(coolingStep.output(batches)),Component.translatable("fluid_state.create_refinery_n_reactors.jei.radiator")));
            for(boolean counterflow:List.of(false,true)) {
                var cold=net.createplant.thermal.FluidState.with(f(Fluids.WATER,1000),6,8);
                var exchange=net.createplant.thermal.ThermalEquipment.exchange(hot,net.createplant.thermal.FluidState.kelvin(temperature),cold,net.createplant.thermal.FluidState.kelvin(6),counterflow);
                int outHot=exchange.firstLevel(), outCold=exchange.secondLevel();
                var a=net.createplant.thermal.PhaseChange.transfer(hot,outHot,pressure);
                var b=net.createplant.thermal.PhaseChange.transfer(cold,outCold,8);
                if(1000%a.consumed()!=0 || 1000%b.consumed()!=0)continue;
                exchanges.add(PlantProcess.fluids(List.of(hot,cold),List.of(a.output(a.batches(1000)),b.output(b.batches(1000))),
                    Component.translatable("fluid_state.create_refinery_n_reactors.jei.exchange")));
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
            boolean heatOnly = recipe.module() == ReactorModule.NONE;
            if (recipe.module() != module && !(module == ReactorModule.STIRRED && heatOnly)) continue;
            List<List<ItemStack>> items = recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList();
            List<FluidStack> fluids = recipe.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList();
            int ticks = recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : 100;
            Component conditions=note(heatOnly ? "heated_tank" : module==ReactorModule.ABSORBER ? "tray_reactor" : "stirred_reactor",ticks,Component.translatable("create."+recipe.getRequiredHeat().getTranslationKey()));
            if(recipe.reactionHeat()!=0)conditions=conditions.copy().append(" · ").append(Component.translatable("reaction_heat.create_refinery_n_reactors."+(recipe.reactionHeat()>0 ? "exothermic" : "endothermic")));
            var products = recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList();
            var entry = new PlantProcess(items, fluids, List.of(), products, recipe.getFluidResults(), conditions)
                .withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
            if (!heatOnly && items.size() + fluids.size() <= 6 && products.size() + recipe.getFluidResults().size() <= 6)
                entry = entry.withConditions(ticks, recipe.getRequiredHeat(), module.minSections);
            list.add(heatOnly ? entry.withConditions(ticks, recipe.getRequiredHeat(), 0) : entry);
        }
        return ordinaryRoutesFirst(list);
    }

    private static List<PlantProcess> ordinaryRoutesFirst(List<PlantProcess> list) {
        return list.stream().sorted(java.util.Comparator.comparingInt(CreatePlantJeiPlugin::routeCost)).toList();
    }

    public static int routeCost(PlantProcess recipe) {
        int cost = recipe.conditions() != null && recipe.conditions().minimumLevels() > 0 ? 2 : 0;
        for (var options : recipe.items()) cost += 1 + options.stream().mapToInt(CreatePlantJeiPlugin::itemCost).min().orElse(4);
        for (var stack : recipe.kept()) cost += 1 + itemCost(stack);
        for (var options : recipe.fluidOptions()) cost += 1 + options.stream().mapToInt(CreatePlantJeiPlugin::fluidCost).min().orElse(4);
        return cost + Math.max(0, recipe.results().size() + recipe.fluidResults().size() - 1);
    }

    private static int itemCost(ItemStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String name = id.getPath();
        if (id.getNamespace().equals("minecraft") || name.startsWith("crushed_") || name.endsWith("_ore")
            || name.startsWith("raw_") || name.equals("sulfur") || name.equals("sulfur_dust") || name.equals("salt")) return 0;
        return name.contains("waste") || name.contains("spent") || name.contains("residue") ? 6 : 3;
    }

    private static int fluidCost(FluidStack stack) {
        String name = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath();
        return switch (name) {
            case "water", "air_gas", "crude_oil", "brine" -> 0;
            case "oxygen_gas", "hydrogen_gas", "nitrogen_gas", "compressed_air", "lava" -> 1;
            default -> name.contains("waste") || name.contains("spent") || name.contains("residue") || name.contains("offgas") ? 6 : 3;
        };
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
            if (recipe.legacy()) continue;
            List<List<ItemStack>> items = recipe.itemIngredients().stream()
                .map(ingredient -> Arrays.asList(ingredient.getItems())).toList();
            List<List<FluidStack>> options = new ArrayList<>();
            options.add(Arrays.asList(recipe.gasIngredient().getFluids()));
            recipe.metalIngredient().ifPresent(ingredient -> options.add(Arrays.asList(ingredient.getFluids())));
            List<FluidStack> fluids = options.stream().map(List::getFirst).toList();
            List<FluidStack> fluidResults = java.util.stream.Stream.of(recipe.metalResult(), recipe.byproduct())
                .filter(fluid -> !fluid.isEmpty()).toList();
            list.add(new PlantProcess(items, fluids, List.of(), List.of(recipe.result()), fluidResults,
                note("converter", recipe.duration())).withFluidOptions(options));
        }
        return list;
    }

    private static void verifyRecipeTrace(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var manager=runtime.getRecipeManager();
        var focus=runtime.getJeiHelpers().getFocusFactory();
        var feedFocus=focus.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(FluidForm.COMPRESSED_SYNTHESIS_FEED,1000));
        var synthesis=manager.createRecipeLookup(type("haber_bosch")).limitFocus(List.of(feedFocus)).get().toList();
        if(synthesis.isEmpty() || synthesis.stream().noneMatch(r->r.kept().stream().anyMatch(i->i.is(CEItems.IRON_CATALYST.get()))))throw new IllegalStateException("JEI cannot trace the compressed feed into Haber-Bosch with iron catalyst");
        var gasFocus=focus.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,f(CEProcessFluids.SYNTHESIS_GAS.get(),1000));
        if(manager.createRecipeLookup(type("gas_compression")).limitFocus(List.of(gasFocus)).get().noneMatch(r->r.fluidResults().stream().anyMatch(f->FluidForm.COMPRESSED_SYNTHESIS_FEED.matches(f))))
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
            int expected=(int)level.getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<net.minecraft.world.item.crafting.RecipeInput,PressureReactionRecipe>getType()).stream()
                .filter(r->r.value().module()==module || module==ReactorModule.STIRRED && r.value().module()==ReactorModule.NONE).count();
            if(counts.get(module==ReactorModule.STIRRED ? "stirred_reactor" : "tray_reactor")!=expected)throw new IllegalStateException("A pressure reaction is missing from JEI");
        }
        try {java.nio.file.Files.writeString(java.nio.file.Path.of("recipe-trace-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(counts));}catch(java.io.IOException e){throw new RuntimeException(e);}
        org.slf4j.LoggerFactory.getLogger(CreatePlantJeiPlugin.class).info("RECIPE TRACE PASSED: H2 + N2 -> synthesis gas -> Haber-Bosch; recipe counts {}",counts);
    }

    private static mezz.jei.api.runtime.IJeiRuntime checkRuntime;
    private static void auditAvailability(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var missing = new java.util.TreeSet<String>();var names = new java.util.TreeMap<String, String>();var keys = new java.util.TreeMap<String, String>();var itemNames = new java.util.TreeMap<String, String>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || item instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem) continue;
            itemNames.put(id.toString(), item.getDefaultInstance().getHoverName().getString());
            var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, item.getDefaultInstance());
            if (runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().findAny().isEmpty()) missing.add(id.getPath());
        }
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            if ((!id.getNamespace().equals(CreatePlant.MOD_ID) && !id.getNamespace().equals("createdieselgenerators")) || id.getPath().startsWith("flowing_")) continue;
            names.put(id.toString(), fluid.getFluidType().getDescription().getString());keys.put(id.toString(), fluid.getFluidType().getDescriptionId());
        }
        var untranslated = new java.util.TreeMap<String, String>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var name = item.getDefaultInstance().getHoverName().getString();
            if (!name.codePoints().anyMatch(c -> c >= 0x3040)) untranslated.put("item " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item) + " " + item.getDescriptionId(), name);
        }
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            if (fluid.defaultFluidState().isEmpty() || !fluid.isSource(fluid.defaultFluidState())) continue;
            var name = fluid.getFluidType().getDescription().getString();
            if (!name.codePoints().anyMatch(c -> c >= 0x3040)) untranslated.put("fluid " + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid) + " " + fluid.getFluidType().getDescriptionId(), name);
        }
        var jeiTooltips = new java.util.TreeMap<String, List<String>>();
        var fluidRenderer = runtime.getIngredientManager().getIngredientRenderer(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK);
        for (var fluid : List.of(CESharedChemicals.SULFUROUS_ACID.get(), CESharedChemicals.SULFUR_DIOXIDE.get())) {
            @SuppressWarnings("removal") var lines = fluidRenderer.getTooltip(new FluidStack(fluid, 200), net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            jeiTooltips.put(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).toString(), lines.stream().map(Component::getString).toList());
        }
        try {java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("recipe-availability.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("withoutRecipe", missing, "fluidNames", names, "fluidKeys", keys, "itemNames", itemNames, "jeiTooltips", jeiTooltips, "untranslated", untranslated)));} catch (java.io.IOException error) {throw new RuntimeException(error);}
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
