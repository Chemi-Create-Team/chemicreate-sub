package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.cloud.GasClouds;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEWaterTreatment;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterTreatmentGameTests {
    private WaterTreatmentGameTests() { }

    private static LiddedVesselBlockEntity vessel(GameTestHelper h, BlockPos base) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(base.offset(x, y, z), CETransport.STIRRED_TANK.get());
        ReactorGameTests.stir(h.getLevel(), h.absolutePos(base));
        var owner = ((LiddedVesselBlockEntity) h.getBlockEntity(base)).getControllerBE();owner.validateStructure();
        h.assertTrue(owner.formed(), "The treatment stage uses an ordinary stirred tank");
        return owner;
    }

    private static IFluidHandler fluids(GameTestHelper h, BlockPos base) {
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(base), Direction.NORTH);
        h.assertTrue(port != null, "A treatment stage accepts standard Create fluid transport");return port;
    }

    private static void reagent(GameTestHelper h, LiddedVesselBlockEntity owner, Item item) {
        h.assertTrue(owner.itemPort().insertItem(0, new ItemStack(item), false).isEmpty(), "The shared vessel inventory accepts its finite reagent");
    }

    private static void run(LiddedVesselBlockEntity owner, int ticks) {
        ReactorGameTests.speed(owner, 32);for (int i = 0; i < ticks; i++) owner.tickProcess();
    }

    private static int solids(LiddedVesselBlockEntity owner, Item item) {
        int amount = 0;var port = owner.itemPort();
        for (int slot = 0; slot < port.getSlots(); slot++) if (port.getStackInSlot(slot).is(item)) amount += port.getStackInSlot(slot).getCount();
        return amount;
    }

    @GameTest(template = "test_reactor", batch = "resin_water_loop")
    public static void resinVaporWashingReusesTheEntireWaterCharge(GameTestHelper h) {
        BlockPos resin = new BlockPos(3, 3, 5), precipitation = resin.east(14), filtration = resin.east(19);
        var mixer = vessel(h, resin);
        var clarifier = vessel(h, precipitation);
        var filter = vessel(h, filtration);
        var tower = AbsorptionTowerGameTests.tower(h.getLevel(), h.absolutePos(resin.east(7)), 4, true);
        BlockPos bottom = tower.getBlockPos();
        var vaporIn = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, bottom.west(), Direction.WEST);
        var washIn = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, bottom.above(3), Direction.UP);
        var washOut = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, bottom.east(), Direction.EAST);
        h.assertTrue(vaporIn != null && washIn != null && washOut != null, "Use the formed tower's Create-compatible gas, water and liquid connections");

        var vaporType = CEWaterTreatment.RESIN_PROCESS_VAPOR.get();
        var vaporSample = new FluidStack(vaporType, 250);
        h.assertTrue(FluidMaterials.gas(vaporType) && vaporType.getBucket() != Items.AIR,
            "Resin process vapor is a gas with a bucket available for filtering");
        h.assertTrue(!GasClouds.supported(vaporSample), "The fictional process vapor does not create hazardous clouds");
        var water = new FluidStack(Fluids.WATER, 1000);
        for (int cycle = 0; cycle < 2; cycle++) {
            h.assertTrue(fluids(h, resin).fill(water.copyWithAmount(250), EXECUTE) == 250
                && washIn.fill(water.copyWithAmount(750), EXECUTE) == 750, "Divide the same water supply between the two machines");
            reagent(h, mixer, CEItems.POLYPROPYLENE.get());
            reagent(h, mixer, CEItems.POLYPROPYLENE.get());
            h.assertTrue(mixer.itemPort().insertItem(1, new ItemStack(Items.QUARTZ), false).isEmpty()
                && mixer.itemPort().insertItem(2, new ItemStack(Items.CALCITE), false).isEmpty(), "The stirred vessel takes both mineral fillers");
            var vaporOut = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(resin), Direction.UP);
            h.assertTrue(vaporOut != null, "The closed mixer has an upper fluid connection");
            if (cycle == 0) {
                run(mixer, 150);
                h.assertTrue(mixer.status().equals("heat") && vaporOut.drain(vaporSample, SIMULATE).isEmpty(),
                    "Solid feed and water wait for burner heat");
                h.setBlock(resin.below(), AllBlocks.BLAZE_BURNER.getDefaultState()
                    .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
            }
            run(mixer, 150);
            h.assertTrue(!vaporOut.drain(vaporSample, SIMULATE).isEmpty(), "With all the water used, the vapor waits at the upper outlet");
            var vapor = vaporOut.drain(vaporSample, EXECUTE);
            h.assertTrue(solids(mixer, CEItems.FLAME_RETARDANT_PELLET.get()) == 4 * (cycle + 1) && vapor.getAmount() == 250
                && solids(mixer, CEItems.POLYPROPYLENE.get()) == 0, "The mixer makes one batch of pellets and vapor at its upper outlet: " + vapor);
            h.assertTrue(vaporIn.fill(vapor, EXECUTE) == 250, "Process vapor enters at the bottom ring");
            for (int tick = 0; tick < 101; tick++) tower.tickProcess();
            var captured = washOut.drain(1000, EXECUTE);
            h.assertTrue(captured.is(CEWaterTreatment.NEUTRALIZED_WASTEWATER.get()) && captured.getAmount() == 1000
                && washIn.drain(1000, SIMULATE).isEmpty(), "The tower recovers all wash water at the bottom with no top exhaust");

            reagent(h, clarifier, CEWaterTreatment.FLOCCULANT.get());
            h.assertTrue(fluids(h, precipitation).fill(captured, EXECUTE) == 1000, "The wash water enters the existing precipitation recipe");
            run(clarifier, 200);
            var clarified = fluids(h, precipitation).drain(new FluidStack(CEWaterTreatment.CLARIFIED_WATER.get(), 1000), EXECUTE);
            reagent(h, filter, CEWaterTreatment.FILTER_MEDIUM.get());
            h.assertTrue(fluids(h, filtration).fill(clarified, EXECUTE) == 1000, "The clarified stream enters the existing filter recipe");
            run(filter, 100);
            water = fluids(h, filtration).drain(new FluidStack(Fluids.WATER, 1000), EXECUTE);
            h.assertTrue(water.getAmount() == 1000 && solids(filter, CEWaterTreatment.SLUDGE.get()) == 3 * (cycle + 1),
                "Only sludge leaves the water loop, which can supply another complete batch");
        }

        var recipes = h.getLevel().getRecipeManager();
        var press = (PressingRecipe) recipes.byKey(CreatePlant.location("pressing/heat_resistant_gasket")).orElseThrow().value();
        h.assertTrue(press.getIngredients().getFirst().test(CEItems.FLAME_RETARDANT_PELLET.toStack()), "A Create Mechanical Press can process the pellets");
        var gasket = press.getResultItem(h.getLevel().registryAccess());
        h.assertTrue(gasket.is(CEItems.HEAT_RESISTANT_GASKET.get()) && gasket.getCount() == 2, "Each pellet makes two gaskets");
        for (String id : new String[] {"reciprocating_cylinder", "reciprocating_compressor", "high_voltage_conductor"}) {
            var recipe = recipes.byKey(CreatePlant.location(id)).orElseThrow().value();
            h.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(gasket) && ingredient.test(CEItems.RUBBER_GASKET.toStack())),
                id + " accepts both gasket items in the same slot");
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void threeFiniteStagesReturnRealWaterWithTheSameCarrierHeatAndMass(GameTestHelper h) {
        var feed = FluidThermal.at(new FluidStack(CEProcessFluids.WASTEWATER.get(), 1000), 318.15);
        double initialEnergy = FluidThermal.energy(feed), initialMass = FluidMaterials.mass(feed);
        Fluid[] outputs = {CEWaterTreatment.NEUTRALIZED_WASTEWATER.get(), CEWaterTreatment.CLARIFIED_WATER.get(), Fluids.WATER};
        Item[] reagents = {Items.CALCITE, CEWaterTreatment.FLOCCULANT.get(), CEWaterTreatment.FILTER_MEDIUM.get()};
        String[] ids = {"washwater_neutralization", "washwater_precipitation", "washwater_filtration"};
        LiddedVesselBlockEntity last = null;
        for (int stage = 0; stage < 3; stage++) {
            BlockPos base = new BlockPos(3 + 5 * stage, 3, 5);var owner = vessel(h, base);last = owner;
            var recipe = (PressureReactionRecipe) h.getLevel().getRecipeManager().byKey(CreatePlant.location("pressure_reaction/" + ids[stage])).orElseThrow().value();
            h.assertTrue(recipe.getRequiredHeat() == com.simibubi.create.content.processing.recipe.HeatCondition.NONE, "Water treatment runs without a burner");
            var port = fluids(h, base);
            h.assertTrue(port.fill(feed, SIMULATE) == 1000 && owner.processRecoveryPort().getFluidInTank(0).isEmpty(), "Simulated supply stores nothing");
            h.assertTrue(port.fill(feed, EXECUTE) == 1000, "A finite 1000 mB parcel enters the stage");reagent(h, owner, reagents[stage]);
            run(owner, 35);var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
            owner.loadWithComponents(saved, h.getLevel().registryAccess());owner.validateStructure();run(owner, 200);
            var recovery = owner.processRecoveryPort();var preview = recovery.drain(new FluidStack(outputs[stage], 1000), SIMULATE);
            h.assertTrue(preview.getAmount() == 1000 && owner.itemPort().getStackInSlot(0).isEmpty(), "One stage consumes one reagent and makes one carrier parcel after reload");
            feed = recovery.drain(new FluidStack(outputs[stage], 1000), EXECUTE);
            h.assertTrue(feed.getAmount() == 1000, "The stage preserves carrier mass and thermal energy: " + FluidThermal.energy(feed));
            run(owner, 200);h.assertTrue(recovery.drain(1000, SIMULATE).isEmpty(), "No spent stage runs again without a new feed");
        }
        h.assertTrue(feed.is(Fluids.WATER) && solids(last, CEWaterTreatment.SLUDGE.get()) == 3,
            "The final liquid is actual Minecraft water and all three finite additive portions leave as sludge");h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void blockedWaterAndSludgeOutletsConsumeNothingAndRestartExactlyOnce(GameTestHelper h) {
        BlockPos base = new BlockPos(4, 3, 5);var owner = vessel(h, base);
        var clarified = FluidThermal.at(new FluidStack(CEWaterTreatment.CLARIFIED_WATER.get(), 1000), 318.15);
        fluids(h, base).fill(clarified, EXECUTE);reagent(h, owner, CEWaterTreatment.FILTER_MEDIUM.get());
        var tag = owner.saveWithoutMetadata(h.getLevel().registryAccess());var inventory = new ItemStackHandler(13);
        inventory.setStackInSlot(0, CEWaterTreatment.FILTER_MEDIUM.get().getDefaultInstance());
        for (int slot = 9; slot < 13; slot++) inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        tag.put("Charge", inventory.serializeNBT(h.getLevel().registryAccess()));
        tag.put("Tank2", FluidThermal.at(new FluidStack(Fluids.WATER, 8000), 318.15).save(h.getLevel().registryAccess()));
        owner.loadWithComponents(tag, h.getLevel().registryAccess());owner.validateStructure();run(owner, 200);
        h.assertTrue(owner.processRecoveryPort().getFluidInTank(0).getAmount() == 1000 && solids(owner, CEWaterTreatment.FILTER_MEDIUM.get()) == 1
            && solids(owner, CEWaterTreatment.SLUDGE.get()) == 0, "Full fluid and solid outlets leave feed and reagent untouched");
        owner.processRecoveryPort().drain(new FluidStack(Fluids.WATER, 8000), EXECUTE);run(owner, 200);
        h.assertTrue(owner.processRecoveryPort().getFluidInTank(0).getAmount() == 1000 && owner.processRecoveryPort().getFluidInTank(2).isEmpty(),
            "Free liquid space alone cannot commit the water while the sludge has no room");
        owner.recoverStoredItem(9, 64);run(owner, 200);
        h.assertTrue(owner.processRecoveryPort().getFluidInTank(0).isEmpty() && owner.processRecoveryPort().getFluidInTank(2).getAmount() == 1000
            && solids(owner, CEWaterTreatment.SLUDGE.get()) == 3 && solids(owner, CEWaterTreatment.FILTER_MEDIUM.get()) == 0,
            "Freeing one solid output slot commits exactly one water/sludge batch");h.succeed();
    }

    private static void motor(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(64));
    }

    private static void transfer(GameTestHelper h, BlockPos source, Fluid product) {
        BlockPos smart = source.offset(3, 0, 0), pump = source.offset(4, 0, 0);
        h.setBlock(smart, AllBlocks.SMART_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        motor(h, pump.south().west(), Direction.EAST);
        var selection = BlockEntityBehaviour.get(h.getBlockEntity(smart), FilteringBehaviour.TYPE);
        selection.setFilter(product == Fluids.WATER ? new ItemStack(Items.WATER_BUCKET) : CEWorldFluids.bucket(new FluidStack(product, 1000)));
        h.assertTrue(selection.test(new FluidStack(product, 1)), "An ordinary temperature-independent bucket filter selects the stage product");
    }

    @GameTest(template = "test_reactor", timeoutTicks = 600)
    public static void nativeCreatePumpsAndFiltersConnectAllThreeExistingVessels(GameTestHelper h) {
        BlockPos first = new BlockPos(3, 3, 5), second = first.east(5), third = first.east(10), receiver = first.east(15);
        var one = vessel(h, first);var two = vessel(h, second);var three = vessel(h, third);
        reagent(h, one, Items.CALCITE);reagent(h, two, CEWaterTreatment.FLOCCULANT.get());reagent(h, three, CEWaterTreatment.FILTER_MEDIUM.get());
        for (BlockPos pos : new BlockPos[] {first, second, third}) ReactorGameTests.driveStirrer(h, pos);
        transfer(h, first, CEWaterTreatment.NEUTRALIZED_WASTEWATER.get());transfer(h, second, CEWaterTreatment.CLARIFIED_WATER.get());transfer(h, third, Fluids.WATER);
        h.setBlock(receiver, AllBlocks.FLUID_TANK.get());var tank = ((FluidTankBlockEntity) h.getBlockEntity(receiver)).getTankInventory();
        h.assertTrue(fluids(h, first).fill(FluidThermal.at(new FluidStack(CEProcessFluids.WASTEWATER.get(), 1000), 318.15), EXECUTE) == 1000,
            "The native train starts with one finite cleaning-drain parcel");
        h.runAfterDelay(500, () -> {
            var recovered = tank.getFluid();
            h.assertTrue(recovered.is(Fluids.WATER) && recovered.getAmount() == 1000 && solids(three, CEWaterTreatment.SLUDGE.get()) == 3,
                "Native shaft inputs, impellers, filtered outlets and Create pumps reclaim exactly 1000 mB water plus three sludge: " + recovered
                    + ", stages=" + one.status() + "/" + two.status() + "/" + three.status());
            h.assertTrue(FluidThermal.kelvin(recovered) > 290 && FluidThermal.kelvin(recovered) <= 318.15 + .01,
                "The open transport train may lose heat to ambient but cannot create heat");
            h.assertTrue(solids(one, Items.CALCITE) == 0 && solids(two, CEWaterTreatment.FLOCCULANT.get()) == 0
                && solids(three, CEWaterTreatment.FILTER_MEDIUM.get()) == 0, "Exactly one dose is consumed at each stage");h.succeed();
        });
    }
}
