package net.createplant.gametest;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.createplant.CreatePlant;
import net.createplant.block.ProcessPorts;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.createplant.chemistry.FluidChemicalRegistry.ChemicalHazard;
import net.createplant.chemistry.FluidItemChemistry;
import net.createplant.event.ProcessPortInteraction;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.thermal.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalUiGameTests {
    @GameTest(template = "test_fractionation")
    public static void everyRegisteredPlantFluidHasAChemicalLabel(GameTestHelper h) {
        for (var entry : BuiltInRegistries.FLUID.entrySet()) {
            var id = entry.getKey().location();
            if (id.getNamespace().equals(CreatePlant.MOD_ID)) {
                var info = FluidChemicalRegistry.base(entry.getValue());
                h.assertTrue(info != null && !info.formula().isBlank() && !info.formula().matches(".*[0-9].*"), "Chemical information is missing or not subscripted for " + id);
            }
        }
        for (var fuel : java.util.List.of(CEPetroFluids.CRUDE_OIL.get(), CEPetroFluids.DIESEL.get(), CEPetroFluids.GASOLINE.get()))
            h.assertTrue(FluidChemicalRegistry.base(fuel) != null, "Refinery fuels supplied by CDG have formulas");
        var acid = new FluidStack(CEUranium.SULFURIC_ACID.get(), 1);
        var info = FluidChemicalRegistry.of(acid);
        h.assertTrue(info.formula().equals("H₂SO₄") && info.hazards().contains(ChemicalHazard.CORROSIVE), "Formula and in-game hazard are separate registered data");
        var bar = ChemicalReadout.heat(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Heat.VERY_HOT).getString();
        h.assertTrue(bar.contains("□ ◆ □ ■ ■") && bar.contains("-123"), "Current stage markers remain distinct from the valid range, and the current grade is named with its range");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void registeredPlantFluidsAndTheirBucketsHaveMatchingStructureFormulas(GameTestHelper h) throws Exception {
        var models = new HashMap<ResourceLocation, String>();
        String file = "/data/" + CreatePlant.MOD_ID + "/chemistry/molecule_structures.json";
        try (var input = ChemicalUiGameTests.class.getResourceAsStream(file)) {
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var element : data) {
                var row = element.getAsJsonArray();
                for (var id : row.get(1).getAsJsonArray()) {
                    models.put(ResourceLocation.parse(id.getAsString()), row.get(0).getAsString());
                }
            }
        }
        for (var entry : BuiltInRegistries.FLUID.entrySet()) {
            var id = entry.getKey().location();
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || id.getPath().startsWith("flowing_")) continue;
            var fluid = new FluidStack(entry.getValue(), 1000);
            var info = FluidChemicalRegistry.base(fluid.getFluid());
            var canonical = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            String formula = info.structureFormula();
            if (formula.isEmpty()) {
                h.assertTrue(!models.containsKey(canonical), "A variable composition has no single structure: " + id);
                continue;
            }
            h.assertTrue(formula.equals(models.get(canonical)), "Missing or mismatched structure after fluid unification: " + id);
            h.assertTrue(formula.equals(displayedFormula(FluidItemChemistry.formulaLines(fluid).getFirst()).getString()),
                "The fluid's tooltip and model use the same formula: " + id);
            var bucket = FluidUtil.getFilledBucket(fluid);
            if (bucket.isEmpty()) continue;
            h.assertTrue(formula.equals(displayedFormula(FluidItemChemistry.formulaLines(bucket).getFirst()).getString()),
                "The bucket and JEI fluid data use the same formula: " + id);
        }
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void fluidContainersExposeActualFormulasWithoutConsumingContents(GameTestHelper h) {
        h.assertTrue(FluidItemChemistry.formulaLines(Items.BUCKET.getDefaultInstance()).isEmpty()
            && displayedFormula(FluidItemChemistry.formulaLines(Items.WATER_BUCKET.getDefaultInstance()).getFirst()).getString().equals("H₂O")
            && displayedFormula(FluidItemChemistry.formulaLines(Items.LAVA_BUCKET.getDefaultInstance()).getFirst()).getContents()
                instanceof TranslatableContents unknown && unknown.getKey().endsWith(".variable_composition"),
            "An empty bucket has no formula, water has its formula, and lava has variable composition");
        var waterBottle = Items.POTION.getDefaultInstance();
        waterBottle.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
        h.assertTrue(displayedFormula(FluidItemChemistry.formulaLines(waterBottle).getFirst()).getString().equals("H₂O")
            && displayedFormula(FluidItemChemistry.formulaLines(Items.HONEY_BOTTLE.getDefaultInstance()).getFirst()).getString().contains("C₆H₁₂O₆"),
            "Vanilla bottles also show their contents");
        var sprayer = CEPortableTools.CHEMICAL_SPRAYER.toStack();
        var methanol = new FluidStack(CEProcessFluids.METHANOL.get(), 250);
        var mixture = MixtureHelper.merge(new FluidStack(Fluids.WATER, 750), methanol, false);
        var handler = sprayer.getCapability(Capabilities.FluidHandler.ITEM);
        h.assertTrue(handler.fill(mixture, EXECUTE) == 1000, "The sprayer holds both liquids");
        var before = handler.getFluidInTank(0);
        var contents = FluidItemChemistry.contents(sprayer);
        var lines = FluidItemChemistry.formulaLines(sprayer);
        var stored = FluidItemChemistry.storedFluids(sprayer);
        h.assertTrue(stored.size() == 1 && MixtureHelper.isMixture(stored.getFirst()), "The structure preview receives the original mixture");
        h.assertTrue(contents.size() == 2 && contents.getFirst().getAmount() == 750 && lines.size() == 2
            && displayedFormula(lines.getFirst()).getString().equals("H₂O") && displayedFormula(lines.getLast()).getString().equals("CH₃OH"),
            "The portable container shows every actual mixture constituent by amount");
        h.assertTrue(FluidStack.matches(handler.getFluidInTank(0), before), "Reading the tooltip preserves the stored mixture");
        h.succeed();
    }

    private static Component displayedFormula(Component line) {
        var arguments = ((TranslatableContents) line.getContents()).getArgs();
        return (Component) arguments[arguments.length - 1];
    }

    @GameTest(template = "test_fractionation")
    public static void aqueousSolutesAndDieselGeneratorCompositionUseFluidDefinitions(GameTestHelper h) {
        var brine = new FluidStack(CEChlorAlkali.BRINE.get(), 1000);
        var brineBucket = net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(brine);
        h.assertTrue(displayedFormula(FluidItemChemistry.formulaLines(brine).getFirst()).getString().equals("NaCl")
            && displayedFormula(FluidItemChemistry.formulaLines(brineBucket).getFirst()).getString().equals("NaCl"),
            "Both fluid ingredients and buckets identify the solute in brine");
        var glycol = new FluidStack(CEProcessFluids.ANTIFREEZE.get(), 1000);
        h.assertTrue(displayedFormula(FluidItemChemistry.formulaLines(glycol).getFirst()).getString().equals("C₂H₆O₂")
            && FluidChemicalRegistry.base(glycol.getFluid()).formula().contains("H₂O"), "The full composition still includes the solvent");
        var biodiesel = BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse("createdieselgenerators:biodiesel"));
        var info = FluidChemicalRegistry.base(biodiesel);
        h.assertTrue(info != null && info.mixture() && info.formula().contains("RCOOCH₂CH₃")
            && info.compositionKey().equals("biodiesel"), "Diesel Generators biodiesel uses the fatty acid ethyl ester family in its ethanol recipe");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aBareHandPurgesOnlyAnExposedDrain(GameTestHelper h) {
        var relative = new BlockPos(5, 4, 5);
        var pos = h.absolutePos(relative);
        h.setBlock(relative, CETransport.LIDDED_VESSEL.get());
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(relative);
        vessel.getTankInventory().setFluid(new FluidStack(Fluids.WATER, 700));
        h.assertTrue(ProcessPorts.at(vessel, Direction.DOWN) == ProcessPorts.Kind.DRAIN, "The down-facing physical port is identified as a drain");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, -.5, 0), Direction.DOWN, pos, false);
        var blocked = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        h.setBlock(relative.below(), Blocks.STONE);
        ProcessPortInteraction.use(blocked);
        h.assertTrue(!blocked.isCanceled() && vessel.getTankInventory().getFluidAmount() == 700, "A covered drain is never purged");
        h.setBlock(relative.below(), Blocks.AIR);
        var exposed = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        ProcessPortInteraction.use(exposed);
        h.assertTrue(exposed.isCanceled() && vessel.getTankInventory().isEmpty(), "An exposed drain clears the real liquid precisely once");
        h.succeed();
    }
}
