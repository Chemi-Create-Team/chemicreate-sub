package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.entity.*;
import net.createplant.chemistry.ReactorModule;
import net.createplant.recipe.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class OreLeachingGameTests {
    private static final String[] METALS = {"iron", "copper", "zinc", "tin"};
    static Fluid fluid(String name) { return BuiltInRegistries.FLUID.get(CreatePlant.location(name)); }
    static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":") ? id : CreatePlant.MOD_ID + ":" + id)); }
    @GameTest(template = "test_wet_processing")
    public static void fourMetalsLeachThenClarifyAndYieldThreeIngotsPerOre(GameTestHelper h) {
        var level = h.getLevel();
        var electro = ElectrolysisCellGameTests.assemble(level, h.absolutePos(new BlockPos(24, 2, 4)), 1, Direction.NORTH);
        ElectrolysisCellGameTests.load(electro, 1);electro.setRunning(true);ElectrolysisCellGameTests.lower(electro);
        var leach = ReactorGameTests.assemble(level, h.absolutePos(new BlockPos(4,2,4)), ReactorModule.STIRRED, 2);
        var clarifier = WetProcessingGameTests.assemble(h, new BlockPos(15, 4, 12), 7, 4, Direction.SOUTH);
        ReactorGameTests.speed(leach,64);ReactorGameTests.heat(leach,com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);clarifier.setSpeed(2);
        var inputs=level.getCapability(Capabilities.ItemHandler.BLOCK,leach.getBlockPos().offset(2,0,1),Direction.EAST);
        var fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,leach.getBlockPos().offset(1,0,0),Direction.NORTH);
        var oxidant=level.getCapability(Capabilities.FluidHandler.BLOCK,leach.getBlockPos().offset(2,1,0),Direction.EAST);
        var output=level.getCapability(Capabilities.FluidHandler.BLOCK,leach.getBlockPos().offset(1,0,2),Direction.SOUTH);
        h.assertTrue(inputs!=null && fluids!=null && oxidant!=null && output!=null,"The stirred vessel exposes standard funnel and fluid capabilities on the actual lesson faces");
        for (var metal : METALS) {
            var crushed = item(metal.equals("tin") ? "crushed_raw_tin" : "create:crushed_raw_" + metal);
            h.assertTrue(inputs.insertItem(0, new ItemStack(crushed, 3), false).isEmpty(), "Ore enters through the vessel funnel face");
            h.assertTrue(fluids.fill(new FluidStack(CEMetals.DILUTE_SULFURIC_ACID.get(), 750), EXECUTE) == 750, "Acid enters the process tank");
            h.assertTrue(oxidant.fill(FluidForm.COMPRESSED_AIR.stack(250), EXECUTE)==250,"The upper process face accepts the finite gas reagent");
            for (int n = 0; n < 200; n++) leach.tickProcess();
            var slurry = output.drain(new FluidStack(fluid(metal + "_leach_slurry"),4000), EXECUTE);
            h.assertTrue(slurry.getAmount() == 4000 && slurry.is(fluid(metal + "_leach_slurry")) && fluids.getFluidInTank(0).isEmpty() && fluids.getFluidInTank(1).isEmpty(), "Leaching consumes the exact finite ore, acid and gas batch");
            h.assertTrue(clarifier.fluidPort(net.createplant.block.WetProcessPartBlock.Role.FEED).fill(slurry, EXECUTE) == 4000, "Slurry feeds the clarifier");
            for (int n = 0; n < 800; n++) clarifier.process();
            var solution = clarifier.fluidPort(net.createplant.block.WetProcessPartBlock.Role.OVERFLOW).drain(3000, EXECUTE);
            h.assertTrue(solution.is(fluid(metal + "_sulfate_solution")) && solution.getAmount() == 3000 && clarifier.tanks[0].isEmpty() && clarifier.items.getStackInSlot(4).getCount() == 8, "Four settling batches yield exactly 3000 mB solution and eight mud");
            clarifier.itemPort(net.createplant.block.WetProcessPartBlock.Role.UNDERFLOW).extractItem(1, 64, false);
            h.assertTrue(ElectrolysisCellGameTests.feed(electro, solution) == 3000, "Clear overflow goes to electrolysis");
            electro.dropCharge();double before = electro.consumed();ElectrolysisCellGameTests.run(electro, 1600, 400);
            h.assertTrue(electro.metalStored() == 9 && electro.stored(0) == 0 && electro.stored(3) == 750 && Math.abs(electro.consumed() - before - 60000) < .001, metal + " three ores yield nine ingots, recycle 750 mB acid and use 60 kJ");
            for (var head : electro.heads()) head.products.setStackInSlot(0, ItemStack.EMPTY);
            for (var cell : electro.cells()) cell.tanks.get(3).drain(8000, EXECUTE);
        }
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void blockedElectrowinningHasNoMaterialCostAndNickelStillDiscountsEnergy(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);var top = owner.heads().getFirst();
        top.electrodes.setStackInSlot(0, ItemStack.EMPTY);top.electrodes.setStackInSlot(1, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack());
        ElectrolysisCellGameTests.feed(owner, new FluidStack(fluid("copper_sulfate_solution"), 1000));
        for (var head : owner.heads()) head.products.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));ElectrolysisCellGameTests.run(owner, 400, 400);
        h.assertTrue(owner.stored(0) == 1000 && owner.consumed() == 0, "No feed or electricity accepted with all metal outlets blocked");
        for (var head : owner.heads()) head.products.setStackInSlot(0, ItemStack.EMPTY);
        for (var cell : owner.cells()) cell.tanks.get(3).fill(new FluidStack(CEMetals.DILUTE_SULFURIC_ACID.get(), ElectrolysisCellBlockEntity.CAPACITY), EXECUTE);
        ElectrolysisCellGameTests.run(owner, 400, 400);h.assertTrue(owner.stored(0) == 1000 && owner.consumed() == 0, "Full regenerated acid bank blocks the batch");
        for (var cell : owner.cells()) cell.tanks.get(3).drain(ElectrolysisCellBlockEntity.CAPACITY, EXECUTE);
        ElectrolysisCellGameTests.run(owner, 400, 400);
        h.assertTrue(owner.metalStored() == 3 && owner.stored(3) == 250 && Math.abs(owner.consumed() - 14000) < .001, "Nickel electrowinning recovers exactly three ingots and acid for 14 kJ");
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, top.getBlockPos(), Direction.UP);
        h.assertTrue(port != null && port.extractItem(3, 64, false).isEmpty() && owner.metalStored() == 3, "The metal stays on the plates while they are down");
        owner.setRunning(false);ElectrolysisCellGameTests.lower(owner);
        h.assertTrue(port.extractItem(3, 64, true).getCount() == 3 && owner.metalStored() == 3 && port.extractItem(3, 64, false).getCount() == 3 && owner.metalStored() == 0, "Raised plates give up their metal: preview first, then extraction");h.succeed();
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 120)
    public static void aRealFunnelTakesTheMetalFromTheRaisedHeadAndTheHeadKeepsIt(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);ElectrolysisCellGameTests.feed(owner, new FluidStack(fluid("tin_sulfate_solution"), 1000));ElectrolysisCellGameTests.run(owner, 400, 400);
        var head = owner.heads().getFirst();var lookup = h.getLevel().registryAccess();
        head.loadWithComponents(head.saveWithoutMetadata(lookup), lookup);owner.validate();
        h.assertTrue(owner.metalStored() == 3 && owner.stored(3) == 250, "Reloading the head keeps its three ingots and the acid once");
        owner.setRunning(false);ElectrolysisCellGameTests.lower(owner);
        var funnel = head.getBlockPos().above();
        h.getLevel().setBlock(funnel, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP)
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, true).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3);
        h.succeedWhen(() -> {
            int count = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(funnel).inflate(4))
                .stream().filter(item -> item.getItem().is(CEMetals.TIN_INGOT.get())).mapToInt(item -> item.getItem().getCount()).sum();
            h.assertTrue(count == 3 && owner.metalStored() == 0 && head.plates() == 1, "A funnel on the raised head takes the three tin ingots and leaves the plate: " + count + ", owner " + owner.metalStored());h.succeed();
        });
    }

    @GameTest(template = "test_petrochemical")
    public static void goldHasNoAddedChemicalLineAndTinHasRawOreAndNativeCrushing(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (var id : new String[]{"gold_leaching", "gold_pressure_leaching", "gold_from_gold_chloride_solution", "iron_pressure_leaching", "copper_pressure_leaching", "zinc_pressure_leaching"})
            h.assertTrue(manager.byKey(CreatePlant.location("pressure_reaction/" + id)).isEmpty(), "Obsolete and gold routes are removed: " + id);
        h.assertTrue(!BuiltInRegistries.FLUID.containsKey(CreatePlant.location("gold_chloride_solution")), "Gold chemical fluid is unregistered");
        // Copper, zinc, nickel, tin and, since the lead wet route, lead.
        var won = net.createplant.chemistry.ElectrolysisProcess.all().stream().filter(net.createplant.chemistry.ElectrolysisProcess::electrowinning).toList();
        h.assertTrue(won.size() == 5 && won.stream().noneMatch(process -> process.metal() == Items.GOLD_INGOT || process.metal() == Items.GOLD_NUGGET),
            "Exactly five generic metals, never gold: " + won.stream().map(process -> process.metal().toString()).toList());
        var crushing = (com.simibubi.create.content.kinetics.crusher.CrushingRecipe) manager.byKey(CreatePlant.location("crushing/raw_tin")).orElseThrow().value();
        h.assertTrue(crushing.getIngredients().getFirst().test(CEMetals.MINERALS.get("cassiterite").get().getDefaultInstance()) && crushing.getRollableResults().getFirst().getStack().is(CEMetals.CRUSHED_TIN.get()), "Tin raw tag resolves to the normal Create crusher path");
        h.assertTrue(!crushing.getIngredients().getFirst().test(Items.RAW_GOLD.getDefaultInstance()), "Raw gold cannot enter the tin route");h.succeed();
    }
}
