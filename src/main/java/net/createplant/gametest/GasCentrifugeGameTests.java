package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.GasCentrifugeBlock;
import net.createplant.block.entity.GasCentrifugeBlockEntity;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GasCentrifugeGameTests {
    public static GasCentrifugeBlockEntity build(GameTestHelper h, BlockPos origin, int count) {
        for (int i = 0; i < count; i++) {
            var pos = origin.east(i);
            // Place both halves together before sending neighbour updates, like a door item.
            h.getLevel().setBlock(h.absolutePos(pos), CEHighStressMachines.CENTRIFUGE.get().defaultBlockState(), 2);
            h.getLevel().setBlock(h.absolutePos(pos.above()), CEHighStressMachines.CENTRIFUGE.get().defaultBlockState().setValue(GasCentrifugeBlock.HALF, DoubleBlockHalf.UPPER), 2);
        }
        var be = (GasCentrifugeBlockEntity) h.getBlockEntity(origin);be.rebuild();drive(be, 32);return be;
    }
    public static void drive(GasCentrifugeBlockEntity be, float speed) {
        be.rebuild();
        for (int i = 0; i < be.port(0).getTanks(); i++) {
            // The fixtures are straight rows; integration tests use actual motors instead.
            var member = GasCentrifugeBlockEntity.at(be.getLevel(), be.getBlockPos().east(i));
            if (member != null) { float old = member.getSpeed();member.setSpeed(speed);member.onSpeedChanged(old); }
        }
    }
    public static void run(GasCentrifugeBlockEntity be, int ticks) { drive(be, 32);for (int i = 0; i < ticks; i++) be.leader().process(); }
    private static ProcessRecipe recipe(String id) { return ProcessRecipe.all().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(); }

    @GameTest(template = "test_fractionation")
    public static void everyRecipeUsesMinimumStagesAndOneLeader(GameTestHelper h) {
        int row = 2;
        for (var recipe : ProcessRecipe.all()) if (recipe.module() == ProcessModule.GAS_CENTRIFUGE) {
            int need = GasCentrifugeBlockEntity.requiredStages(recipe.id());
            var be = build(h, new BlockPos(2, 3, row), need);row += 4;
            be.port(0).fill(recipe.inputs().getFirst(), EXECUTE);
            var last = GasCentrifugeBlockEntity.at(h.getLevel(), be.getBlockPos().east(need - 1));last.setSpeed(0);
            for (int t = 0; t < 250; t++) be.process();
            h.assertTrue(be.amount(0) == 1000 && be.amount(1) == 0, "Insufficient powered stages preserve feed: " + recipe.id());
            last.setSpeed(32);
            for (int t = 0; t < recipe.ticks(); t++) for (int i = 0; i < need; i++) GasCentrifugeBlockEntity.at(h.getLevel(), be.getBlockPos().east(i)).process();
            h.assertTrue(be.amount(0) == 0 && be.amount(1) == recipe.outputs().getFirst().getAmount() && be.amount(2) == recipe.outputs().get(1).getAmount(), "Only one leader commits exactly one unchanged batch: " + recipe.id());
            h.assertTrue(be.calculateStressApplied() == 32, "Each unit draws 32 SU/RPM");
        }
        h.succeed();
    }
    @GameTest(template = "test_fractionation")
    public static void portsSimulationReloadAndFullOutputsConserveContents(GameTestHelper h) {
        var be = build(h, new BlockPos(2, 3, 2), 4);var level = h.getLevel();var pos = be.getBlockPos();
        var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
        var product = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.above(), Direction.UP);
        var tails = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.above(), Direction.EAST);
        h.assertTrue(input != null && product != null && tails != null && level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN) == null, "Native ports are available on the correct halves only");
        var recipe = recipe("enrich_uranium");var feed = recipe.inputs().getFirst().copyWithAmount(2000);
        var before = be.saveWithoutMetadata(level.registryAccess());input.fill(feed, SIMULATE);
        h.assertTrue(be.saveWithoutMetadata(level.registryAccess()).equals(before), "Input simulation is immutable");
        input.fill(feed, EXECUTE);
        h.assertTrue(input.drain(1000, EXECUTE).isEmpty() && product.fill(feed, EXECUTE) == 0 && tails.fill(feed, EXECUTE) == 0, "Input cannot be extracted and output ports reject insertion");
        for (int i = 0; i < 4; i++) GasCentrifugeBlockEntity.at(level, pos.east(i)).tails.setFluid(recipe.outputs().get(1).copyWithAmount(4000));
        run(be, 250);h.assertTrue(be.amount(0) == 2000 && be.progress() == 0, "Full tails never consume feed");
        tails.drain(Integer.MAX_VALUE, EXECUTE);run(be, 90);
        var saved = be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(saved, level.registryAccess());run(be, 310);
        h.assertTrue(be.amount(0) == 0 && product.drain(Integer.MAX_VALUE, SIMULATE).getAmount() == 250 && tails.drain(Integer.MAX_VALUE, SIMULATE).getAmount() == 1750, "Reload resumes exactly two batches with 2000 mB conserved: feed=" + be.amount(0) + " product=" + be.amount(1) + " tails=" + be.amount(2) + " progress=" + be.progress() + " status=" + be.status());
        h.assertTrue(product.drain(recipe.outputs().get(1), EXECUTE).isEmpty(), "Product cannot extract tails");h.succeed();
    }
    @GameTest(template = "test_fractionation")
    public static void adjacencyLimitSplitAndSpeedCap(GameTestHelper h) {
        var origin = new BlockPos(2, 3, 2);var be = build(h, origin, 17);
        var overflow = (GasCentrifugeBlockEntity) h.getBlockEntity(origin.east(16));
        h.assertTrue(be.unitCount() == 16 && overflow.unitCount() == 1, "The seventeenth unit is a separate cascade");
        var diagonal = build(h, origin.offset(18, 0, 1), 1);h.assertTrue(diagonal.unitCount() == 1, "Diagonal machines never join");
        be.port(0).fill(recipe("recover_helium").inputs().getFirst(), EXECUTE);run(be, 49);
        h.assertTrue(be.amount(1) == 0 && be.progress() == 196, "Sixteen stages cap at four times speed");run(be, 1);
        h.assertTrue(be.amount(1) == 750 && be.amount(2) == 250, "Capped speed completes at fifty ticks");
        var far = GasCentrifugeBlockEntity.at(h.getLevel(), be.getBlockPos().east(15));
        far.feed.setFluid(recipe("enrich_uranium").inputs().getFirst());
        h.setBlock(origin.east(8), Blocks.AIR);be.rebuild();far.rebuild();
        h.assertTrue(be.unitCount() == 8 && far.unitCount() == 8 && be.amount(0) + far.amount(0) == 1000 && be.amount(1) + far.amount(1) == 750 && be.amount(2) + far.amount(2) == 250, "Removing an empty bridge preserves every local tank and reconnects the former overflow");
        h.assertTrue(h.getBlockState(origin.east(8).above()).isAir(), "Breaking the lower half removes the upper half");h.succeed();
    }
    private static void pipe(GameTestHelper h, BlockPos pos, Direction.Axis axis, boolean nickel) {
        var state = nickel ? CETransport.NICKEL_ALLOY_PIPE.get().defaultBlockState() : AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side : Direction.values()) state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == axis);
        h.setBlock(pos, state);
    }
    private static void pump(GameTestHelper h, BlockPos pos, Direction direction, boolean nickel) {
        var block = nickel ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(CreatePlant.location("nickel_alloy_pump")) : AllBlocks.MECHANICAL_PUMP.get();
        h.setBlock(pos, block.defaultBlockState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, direction));
        var gear = pos.relative(direction.getAxis() == Direction.Axis.X ? Direction.NORTH : direction.getAxis() == Direction.Axis.Y ? Direction.WEST : Direction.EAST);
        h.setBlock(gear, AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, direction.getAxis()));
        var motor = gear.relative(direction.getOpposite());
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, direction));
        ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64);
    }
    @GameTest(template = "test_fractionation", timeoutTicks = 650)
    public static void actualCreatePumpsRespectAllThreePortsAndConserveTwoBatches(GameTestHelper h) { pumping(h, "concentrate_heavy_water", 2, false); }
    @GameTest(template = "test_fractionation", timeoutTicks = 650)
    public static void nativeNickelPumpsConserveTheFluorideEnrichmentBatch(GameTestHelper h) { pumping(h, "enrich_uranium", 4, true); }
    private static void pumping(GameTestHelper h, String recipeId, int units, boolean nickel) {
        var origin = new BlockPos(6, 3, 6);var be = build(h, origin, units);
        for (int i = 0; i < units; i++) {
            h.setBlock(origin.east(i).below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
            ((CreativeMotorBlockEntity) h.getBlockEntity(origin.east(i).below())).generatedSpeed.setValue(64);
        }
        var sourcePos = new BlockPos(2, nickel ? 2 : 3, 6);h.setBlock(sourcePos, CETransport.LIDDED_VESSEL.get());
        var source = (net.createplant.block.entity.LiddedVesselBlockEntity) h.getBlockEntity(sourcePos);
        h.runAfterDelay(20, () -> h.assertTrue(source.getTankInventory().fill(recipe(recipeId).inputs().getFirst().copyWithAmount(2000), EXECUTE) == 2000, "The native source receives exactly 2000 mB after its tank forms"));
        var corner = nickel ? CETransport.NICKEL_ALLOY_PIPE.get().defaultBlockState() : AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side : Direction.values()) corner = corner.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side), side == Direction.DOWN || side == Direction.EAST);
        if (nickel) h.setBlock(new BlockPos(2, 3, 6), corner);
        pipe(h, new BlockPos(3, 3, 6), Direction.Axis.X, nickel);pump(h, new BlockPos(4, 3, 6), Direction.EAST, nickel);pipe(h, new BlockPos(5, 3, 6), Direction.Axis.X, nickel);
        pump(h, new BlockPos(6, 5, 6), Direction.UP, nickel);pipe(h, new BlockPos(6, 6, 6), Direction.Axis.Y, nickel);
        h.setBlock(new BlockPos(6, 7, 6), CETransport.LIDDED_VESSEL.get());
        pipe(h, new BlockPos(6, 4, 7), Direction.Axis.Z, nickel);pump(h, new BlockPos(6, 4, 8), Direction.SOUTH, nickel);pipe(h, new BlockPos(6, 4, 9), Direction.Axis.Z, nickel);
        h.setBlock(new BlockPos(6, 4, 10), CETransport.LIDDED_VESSEL.get());
        h.runAfterDelay(600, () -> {
            var primary = (net.createplant.block.entity.LiddedVesselBlockEntity) h.getBlockEntity(new BlockPos(6, 7, 6));
            var waste = (net.createplant.block.entity.LiddedVesselBlockEntity) h.getBlockEntity(new BlockPos(6, 4, 10));
            h.assertTrue(source.getTankInventory().isEmpty() && be.amount(0) == 0 && be.amount(1) == 0 && be.amount(2) == 0
                && primary.getTankInventory().getFluidAmount() == recipe(recipeId).outputs().get(0).getAmount() * 2 && waste.getTankInventory().getFluidAmount() == recipe(recipeId).outputs().get(1).getAmount() * 2,
                "Native pumping conserves 2000 mB: units=" + be.unitCount() + " stages=" + be.stages() + " source=" + source.getTankInventory().getFluidAmount() + " machine=" + be.amount(0) + "/" + be.amount(1) + "/" + be.amount(2) + " products=" + primary.getTankInventory().getFluidAmount() + "/" + waste.getTankInventory().getFluidAmount() + " status=" + be.status());
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void oneItemPlacesBothHalvesAndEitherHalfDropsOnlyOneItem(GameTestHelper h) {
        var level = h.getLevel();var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var block = CEHighStressMachines.CENTRIFUGE.get();
        for (int i = 0; i < 2; i++) {
            var pos = h.absolutePos(new BlockPos(3 + i * 5, 3, 3));
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            var held = new net.minecraft.world.item.ItemStack(block.asItem(), 2);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
            var context = new net.minecraft.world.item.context.BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, held, hit);
            h.assertTrue(((net.minecraft.world.item.BlockItem) block.asItem()).place(context).consumesAction()
                && level.getBlockState(pos).is(block) && level.getBlockState(pos.above()).is(block) && held.getCount() == 1, "One survival item places exactly two halves");
            level.destroyBlock(i == 0 ? pos : pos.above(), true);
            int count = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(block.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && count == 1, "Breaking either half returns one item and removes both halves: " + count);
        }
        var pos = h.absolutePos(new BlockPos(3, 3, 8));level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        var held = new net.minecraft.world.item.ItemStack(block.asItem());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, held, hit);
        h.assertTrue(block.getStateForPlacement(context) == null, "Placement rejects an obstructed upper half");h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void savedRemovedInternalLoadsAsStorageWithoutCompatibilityRegistration(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 4);h.setBlock(pos, CETransport.LIDDED_VESSEL.get());
        var vessel = (net.createplant.block.entity.LiddedVesselBlockEntity) h.getBlockEntity(pos);
        var saved = vessel.saveWithoutMetadata(h.getLevel().registryAccess());var internal = new net.minecraft.nbt.CompoundTag();
        String retiredId = "create_refinery_n_reactors:reactor_" + "rotor_column";
        internal.putString("id", retiredId);internal.putInt("count", 1);saved.put("Internal", internal);
        vessel.loadWithComponents(saved, h.getLevel().registryAccess());vessel.validateStructure();
        h.assertTrue(vessel.module() == net.createplant.chemistry.ReactorModule.NONE && !vessel.formed()
            && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(retiredId)), "Unknown saved internal cannot select a process or require a compatibility item");h.succeed();
    }

}
