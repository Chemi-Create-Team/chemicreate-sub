package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ReactorModule;
import net.createplant.item.PortableToolFluidHandler;
import net.createplant.item.SprayToolItem;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFuelCycle;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransmission;
import net.createplant.registry.CEUranium;
import net.createplant.registry.CEWaterTreatment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DownstreamMaterialsGameTests {
    @GameTest(template = "test_fractionation")
    public static void fractionatedWaxAndResidualOilReturnToTheNaphthaLine(GameTestHelper h) {
        var crude = stack(CEProcessFluids.SYNTHETIC_CRUDE_OIL.get(), 1000);
        var wax = cut(h, new BlockPos(4, 3, 4), "distill_synthetic_crude", crude, 3);
        var corner = new BlockPos(9, 3, 4);
        var tower = ReactionTowerGameTests.tower(h.getLevel(), h.absolutePos(corner), 3, CEItems.PLATINUM_ZEOLITE_CORE.toStack());
        var port = ReactionTowerGameTests.port(tower);
        ReactionTowerGameTests.heat(tower);
        fill(h, port, wax);
        fill(h, port, stack(net.createplant.thermal.FluidForm.COMPRESSED_HYDROGEN, 250));
        tower.process(125);
        h.assertTrue(tower.status().equals("heat") && tower.tank(0).getAmount() == wax.getAmount(),
            "Normal heat keeps the wax in the feed");
        superheat(h, corner);
        tower.process(125);
        var naphtha = take(h, port, CEProcessFluids.SYNTHETIC_NAPHTHA.get(), 100);
        take(h, port, CEProcessFluids.FT_OFFGAS.get(), 50);
        h.assertTrue(tower.layer(2).catalystStack().getDamageValue() == 1, "One working bed pays one wear");

        fill(h, port, stack(CEPetroFluids.HEAVY_OIL.get(), 500));
        fill(h, port, stack(net.createplant.thermal.FluidForm.COMPRESSED_HYDROGEN, 500));
        tower.process(155);
        var moreNaphtha = take(h, port, CEProcessFluids.SYNTHETIC_NAPHTHA.get(), 350);
        take(h, port, CEProcessFluids.FT_OFFGAS.get(), 150);
        var butadiene = cut(h, new BlockPos(16, 3, 4), "separate_butadiene",
            naphtha.copyWithAmount(naphtha.getAmount() + moreNaphtha.getAmount()), 0);
        h.assertTrue(butadiene.is(CEProcessFluids.BUTADIENE.get()) && butadiene.getAmount() == 300,
            "Both naphtha streams rejoin the working column");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void btxAndNaphthaSupplyBothPartsOfSyntheticRubber(GameTestHelper h) {
        var benzene = cut(h, new BlockPos(4, 3, 4), "separate_btx", stack(CEProcessFluids.BTX.get(), 300), 0);
        var alkylation = tower(h, new BlockPos(8, 3, 4), CEItems.ZSM5_CATALYST_CORE.toStack(), false);
        fill(h, ReactionTowerGameTests.port(alkylation), benzene);
        fill(h, ReactionTowerGameTests.port(alkylation), stack(CEPetroFluids.ETHYLENE.get(), 150));
        alkylation.process(110);
        var ethylbenzene = take(h, ReactionTowerGameTests.port(alkylation), CEProcessFluids.ETHYLBENZENE.get(), 240);
        var dehydrogenation = tower(h, new BlockPos(13, 3, 4), CEItems.IRON_CATALYST.toStack(), true);
        fill(h, ReactionTowerGameTests.port(dehydrogenation), ethylbenzene);
        dehydrogenation.process(130);
        var styrene = take(h, ReactionTowerGameTests.port(dehydrogenation), CEProcessFluids.STYRENE.get(), 200);
        take(h, ReactionTowerGameTests.port(dehydrogenation), CEFluids.HYDROGEN_GAS.get(), 40);

        var butadiene = cut(h, new BlockPos(18, 3, 4), "separate_butadiene",
            stack(CEProcessFluids.SYNTHETIC_NAPHTHA.get(), 300), 0);
        var vessel = vessel(h, new BlockPos(22, 3, 4));
        var port = vessel.port(Direction.NORTH);
        fill(h, port, styrene);
        fill(h, port, butadiene);
        tick(vessel);
        h.assertTrue(port.getFluidInTank(0).getAmount() == styrene.getAmount(), "The vessel keeps its liquid without sulfur");
        insert(h, vessel.itemPort(), CEItems.SULFUR_DUST.toStack());
        tick(vessel);
        take(h, vessel.itemPort(), CEItems.SYNTHETIC_RUBBER.get(), 8);
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void cumeneAndMethanolSupplyPhenolicBoards(GameTestHelper h) {
        var cumeneTower = tower(h, new BlockPos(4, 3, 4), CEItems.CUMENE_CATALYST_CORE.toStack(), false);
        fill(h, ReactionTowerGameTests.port(cumeneTower), stack(CEProcessFluids.BENZENE.get(), 140));
        fill(h, ReactionTowerGameTests.port(cumeneTower), stack(CEPetroFluids.PROPYLENE.get(), 100));
        cumeneTower.process(105);
        var cumene = take(h, ReactionTowerGameTests.port(cumeneTower), CEProcessFluids.CUMENE.get(), 200);
        var oxidizer = tower(h, new BlockPos(9, 3, 4), CEItems.OXIDATION_CATALYST.toStack(), false);
        fill(h, ReactionTowerGameTests.port(oxidizer), cumene);
        fill(h, ReactionTowerGameTests.port(oxidizer), stack(CEFluids.OXYGEN_GAS.get(), 100));
        oxidizer.process(100);
        var liquor = take(h, ReactionTowerGameTests.port(oxidizer), CEProcessFluids.CUMENE_PROCESS_LIQUOR.get(), 250);
        var separator = tower(h, new BlockPos(14, 3, 4), CEItems.CUMENE_CATALYST_CORE.toStack(), false);
        fill(h, ReactionTowerGameTests.port(separator), liquor);
        fill(h, ReactionTowerGameTests.port(separator), stack(CEUranium.SULFURIC_ACID.get(), 50));
        separator.process(110);
        var phenol = take(h, ReactionTowerGameTests.port(separator), CEProcessFluids.PHENOL.get(), 150);
        take(h, ReactionTowerGameTests.port(separator), CEProcessFluids.ACETONE.get(), 100);

        fill(h, ReactionTowerGameTests.port(oxidizer), stack(CEProcessFluids.METHANOL.get(), 250));
        fill(h, ReactionTowerGameTests.port(oxidizer), stack(CEFluids.OXYGEN_GAS.get(), 100));
        oxidizer.process(100);
        var formaldehyde = take(h, ReactionTowerGameTests.port(oxidizer), CEProcessFluids.FORMALDEHYDE_SOLUTION.get(), 250);
        var vessel = vessel(h, new BlockPos(19, 3, 4));
        var port = vessel.port(Direction.NORTH);
        fill(h, port, phenol);
        fill(h, port, formaldehyde);
        tick(vessel);
        take(h, port, net.createplant.thermal.FluidForm.WARM_WATER, 100);
        var resin = take(h, vessel.itemPort(), CEItems.PHENOLIC_RESIN.get(), 4);
        var pressing = recipe(h, "pressing/phenolic_board");
        h.assertTrue(pressing.getIngredients().getFirst().test(resin)
            && pressing.getResultItem(h.getLevel().registryAccess()).is(CEItems.PHENOLIC_BOARD.get()),
            "The produced resin is accepted by the native press recipe for a phenolic board");
        recipe(h, "pressing/molded_polymer");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void solventIsRecoveredAndCoatingFitsRealTransmissionHardware(GameTestHelper h) {
        var vessel = vessel(h, new BlockPos(4, 3, 4));
        var port = vessel.port(Direction.NORTH);
        fill(h, port, stack(CEProcessFluids.BENZENE.get(), 400));
        fill(h, port, stack(CEChlorAlkali.CHLORINE.get(), 400));
        for (int batch = 0; batch < 4; batch++) tick(vessel);
        var solvent = take(h, port, CEProcessFluids.CHLOROBENZENE.get(), 400);
        take(h, port, CEChlorAlkali.HYDROGEN_CHLORIDE.get(), 400);
        var basin = basin(h, new BlockPos(10, 3, 4));
        fill(h, basin.inputTank.getCapability(), solvent);
        insert(h, basin.getInputInventory(), new ItemStack(Items.SPRUCE_LOG, 2));
        var extraction = recipe(h, "mixing/solvent_resin_extraction");
        h.assertTrue(BasinRecipe.apply(basin, extraction) && BasinRecipe.apply(basin, extraction),
            "The native mixer extracts resin twice from the chlorinated solvent");
        take(h, basin.getOutputInventory(), CEItems.INDUSTRIAL_RESIN.get(), 2);
        var recovered = take(h, basin.getTanks().getSecond().getCapability(), CEProcessFluids.RECOVERED_SOLVENT.get(), 300);
        fill(h, basin.inputTank.getCapability(), recovered);
        insert(h, basin.getInputInventory(), new ItemStack(Items.SPRUCE_LOG));
        h.assertTrue(BasinRecipe.apply(basin, extraction), "Recovered solvent qualifies for the same extraction");

        insert(h, vessel.itemPort(), CEChlorAlkali.PVC.toStack());
        insert(h, vessel.itemPort(), CEItems.PHOSPHATE_COMPOUND.toStack());
        fill(h, port, stack(Fluids.WATER, 250));
        tick(vessel);
        take(h, port, CEWaterTreatment.RESIN_PROCESS_VAPOR.get(), 250);
        var pellets = take(h, vessel.itemPort(), CEItems.FLAME_RETARDANT_PELLET.get(), 4);

        var coatingPos = new BlockPos(14, 3, 4);
        var coatingBasin = basin(h, coatingPos);
        h.setBlock(coatingPos.below(), AllBlocks.BLAZE_BURNER.getDefaultState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        coatingBasin.tick();
        insert(h, coatingBasin.getInputInventory(), pellets.copyWithCount(1));
        insert(h, coatingBasin.getInputInventory(), CEItems.PHENOLIC_RESIN.toStack());
        insert(h, coatingBasin.getInputInventory(), CEMetals.ZIRCONIA.toStack());
        h.assertTrue(BasinRecipe.apply(coatingBasin, recipe(h, "mixing/heat_resistant_coating")),
            "The new pellets feed the heated coating recipe");
        var coating = take(h, coatingBasin.getOutputInventory(), CEItems.HEAT_RESISTANT_COATING.get(), 2);
        var deploying = recipe(h, "deploying/fire_resistant_conductor");
        h.assertTrue(deploying.getIngredients().stream().anyMatch(ingredient -> ingredient.test(coating))
            && deploying.getResultItem(h.getLevel().registryAccess()).is(CETransmission.FIRE_RESISTANT_CONDUCTOR.get()),
            "Create's deployer uses the coating to produce the conductor");
        recipe(h, "fire_resistant_cable");
        h.assertTrue(CETransmission.fireResistantWire().getMaxTemperature() > CETransmission.wire().getMaxTemperature(),
            "The cable has greater heat tolerance in the actual transmission simulation");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void phosphateBlendFillsAndTreatsCropsThroughTheNativeSpout(GameTestHelper h) {
        var basin = basin(h, new BlockPos(9, 3, 4));
        insert(h, basin.getInputInventory(), CEItems.PHOSPHATE_FERTILIZER.toStack());
        insert(h, basin.getInputInventory(), CEFuelCycle.BORAX.toStack());
        fill(h, basin.inputTank.getCapability(), stack(Fluids.WATER, 250));
        h.assertTrue(BasinRecipe.apply(basin, recipe(h, "mixing/phosphate_compound")), "The basin produces phosphate compound");
        var compound = take(h, basin.getOutputInventory(), CEItems.PHOSPHATE_COMPOUND.get(), 3);

        var vessel = vessel(h, new BlockPos(4, 3, 4));
        insert(h, vessel.itemPort(), compound.copyWithCount(1));
        insert(h, vessel.itemPort(), CEItems.UREA.toStack());
        insert(h, vessel.itemPort(), CEItems.POTASH.toStack());
        var port = vessel.port(Direction.NORTH);
        fill(h, port, stack(net.createplant.thermal.FluidForm.LIQUID_AMMONIA, 100));
        fill(h, port, stack(Fluids.WATER, 400));
        tick(vessel);
        var fertilizer = take(h, port, CEProcessFluids.LIQUID_FERTILIZER.get(), 500);
        var empty = CEPortableTools.CHEMICAL_SPRAYER.toStack();
        h.assertTrue(FillingBySpout.canItemBeFilled(h.getLevel(), empty), "Create's spout recognizes its fill capability");
        int needed = FillingBySpout.getRequiredAmountForItem(h.getLevel(), empty, fertilizer);
        h.assertTrue(needed == 500, "The spout can transfer the available partial fill, got " + needed);
        var filled = FillingBySpout.fillItem(h.getLevel(), needed, empty, fertilizer);
        h.assertTrue(PortableToolFluidHandler.contents(filled).getAmount() == 500, "Native filling retained the full batch");

        var center = new BlockPos(12, 3, 12);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var pos = center.offset(x, 0, z);
                h.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                h.setBlock(pos, Blocks.WHEAT);
            }
        }
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, filled);
        var eye = Vec3.atCenterOf(h.absolutePos(center.north(4))).add(0, .6, 0);
        var target = Vec3.atBottomCenterOf(h.absolutePos(center)).add(0, .1, 0);
        var delta = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-delta.y, delta.horizontalDistance()));
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        var tool = (SprayToolItem) filled.getItem();
        tool.use(h.getLevel(), player, InteractionHand.MAIN_HAND);

        int duration = tool.getUseDuration(filled, player);
        h.setBlock(center.north(2), Blocks.STONE);
        h.setBlock(center.north(2).above(), Blocks.STONE);
        tool.onUseTick(h.getLevel(), player, filled, duration);
        h.assertTrue(PortableToolFluidHandler.contents(filled).getAmount() == 500, "A blocked spray charges nothing");
        h.setBlock(center.north(2), Blocks.AIR);
        h.setBlock(center.north(2).above(), Blocks.AIR);
        tool.onUseTick(h.getLevel(), player, filled, duration);
        h.assertTrue(PortableToolFluidHandler.contents(filled).getAmount() == 400,
            "A successful area application costs 100 mB");
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                h.assertTrue(h.getBlockState(center.offset(x, 0, z)).getValue(CropBlock.AGE) == 1, "All nine crops grow");
        tool.onUseTick(h.getLevel(), player, filled, duration);
        h.assertTrue(PortableToolFluidHandler.contents(filled).getAmount() == 400, "Retreating the same plot charges nothing");
        h.succeed();
    }

    private static FluidStack cut(GameTestHelper h, BlockPos pos, String name, FluidStack feed, int index) {
        var profile = DistillationProfile.fraction(name);
        int height = DistillationProfile.requiredHeight(name);
        var column = DistillationGameTests.build(h.getLevel(), h.absolutePos(pos), 2, height);
        fill(h, DistillationGameTests.inlet(column, DistillationProfile.feedLayer(height)), feed);
        DistillationGameTests.run(column, profile.ticks() + 10);
        var output = profile.outputs().get(index);
        int layer = DistillationProfile.fractionLayer(index + 1, height, profile.outputs().size());
        return take(h, DistillationGameTests.outlet(column, layer), output.getFluid(),
            output.getAmount() * (feed.getAmount() / profile.input().getAmount()));
    }

    private static ReactionTowerBlockEntity tower(GameTestHelper h, BlockPos corner,
            ItemStack core, boolean superheated) {
        var tower = ReactionTowerGameTests.tower(h.getLevel(), h.absolutePos(corner), 3, core);
        if (superheated) superheat(h, corner);
        else ReactionTowerGameTests.heat(tower);
        return tower;
    }

    private static void superheat(GameTestHelper h, BlockPos corner) {
        h.setBlock(corner.below(), AllBlocks.BLAZE_BURNER.getDefaultState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
    }

    private static ReactorMachineBlockEntity vessel(GameTestHelper h, BlockPos pos) {
        var vessel = ReactorGameTests.assemble(h.getLevel(), h.absolutePos(pos), ReactorModule.STIRRED, 1);
        ReactorGameTests.heat(vessel, HeatCondition.HEATED);
        ReactorGameTests.speed(vessel, 32);
        return vessel;
    }

    private static void tick(ReactorMachineBlockEntity vessel) {
        for (int tick = 0; tick < 200; tick++) vessel.tickProcess();
    }

    private static FluidStack stack(net.createplant.thermal.FluidForm form, int amount) {
        return form.stack(amount);
    }

    private static FluidStack take(GameTestHelper h, IFluidHandler port, net.createplant.thermal.FluidForm form, int amount) {
        var made = port.drain(form.stack(amount + 1), SIMULATE);
        if (made.isEmpty()) {
            for (int tank = 0; tank < port.getTanks(); tank++) {
                var stored = port.getFluidInTank(tank);
                if (!form.matches(stored)) continue;
                made = port.drain(stored.copyWithAmount(amount + 1), SIMULATE);
                if (!made.isEmpty()) break;
            }
        }
        if (made.isEmpty()) made = port.drain(amount + 1, SIMULATE);
        h.assertTrue(form.matches(made) && made.getAmount() == amount, "Expected " + amount + " of " + form.id() + "; got " + made);
        return port.drain(made, EXECUTE);
    }

    private static FluidStack stack(Fluid fluid, int amount) {
        return new FluidStack(fluid, amount);
    }

    private static void fill(GameTestHelper h, IFluidHandler port, FluidStack fluid) {
        h.assertTrue(port.fill(fluid, EXECUTE) == fluid.getAmount(), "The inlet accepts " + fluid);
    }

    private static FluidStack take(GameTestHelper h, IFluidHandler port, Fluid fluid, int amount) {
        var request = stack(fluid, amount + 1);
        var made = port.drain(request, SIMULATE);
        if (made.isEmpty()) {
            for (int tank = 0; tank < port.getTanks(); tank++) {
                var stored = port.getFluidInTank(tank);
                if (!stored.is(fluid)) continue;
                made = port.drain(stored.copyWithAmount(amount + 1), SIMULATE);
                if (!made.isEmpty()) break;
            }
        }
        if (made.isEmpty()) made = port.drain(amount + 1, SIMULATE);
        h.assertTrue(made.is(fluid) && made.getAmount() == amount, "Expected " + amount + " of " + fluid + "; got " + made);
        return port.drain(made, EXECUTE);
    }

    private static void insert(GameTestHelper h, IItemHandler port, ItemStack input) {
        h.assertTrue(ItemHandlerHelper.insertItemStacked(port, input, false).isEmpty(), "The inlet accepts " + input);
    }

    private static ItemStack take(GameTestHelper h, IItemHandler port, Item item, int amount) {
        for (int slot = 0; slot < port.getSlots(); slot++) {
            if (!port.getStackInSlot(slot).is(item)) continue;
            var made = port.extractItem(slot, amount + 1, false);
            h.assertTrue(made.getCount() == amount, "Expected " + amount + " of " + item + "; got " + made);
            return made;
        }
        throw new AssertionError("Missing output " + item);
    }

    private static BasinBlockEntity basin(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AllBlocks.BASIN.get());
        var basin = (BasinBlockEntity) h.getBlockEntity(pos);
        basin.tick();
        return basin;
    }

    private static Recipe<?> recipe(GameTestHelper h, String name) {
        return h.getLevel().getRecipeManager().byKey(CreatePlant.location(name)).orElseThrow().value();
    }
}
