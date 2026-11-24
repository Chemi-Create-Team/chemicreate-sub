package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.nuclear.FuelPoolStructure;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CENuclear;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelPoolGameTests {
    private static final BlockPos BASE = new BlockPos(5, 4, 5);
    private static final int POOL_CAPACITY = 4 * FuelPoolBlockEntity.WATER_PER_CELL;
    private static final IFluidHandler.FluidAction EXECUTE = IFluidHandler.FluidAction.EXECUTE, SIMULATE = IFluidHandler.FluidAction.SIMULATE;

    private FuelPoolGameTests() { }

    public static void build(GameTestHelper h, BlockPos base, int width, int length) {
        for (int x = -1; x <= width; x++) for (int z = -1; z <= length; z++) {
            boolean wall = x == -1 || x == width || z == -1 || z == length;
            if (wall) for (int y = 0; y <= FuelPoolStructure.DEPTH; y++) h.setBlock(base.offset(x, y, z), CEMetals.STEEL_BLOCK.get().getBlock());
            else h.setBlock(base.offset(x, 0, z), CENuclear.FUEL_POOL.get());
        }
    }

    private static net.minecraft.world.level.block.state.BlockState pipe(Direction... connections) {
        var state = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side : Direction.values()) state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side), java.util.Arrays.asList(connections).contains(side));
        return state;
    }

    private static IFluidHandler water(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.DOWN);
    }

    private static FuelPoolBlockEntity rack(GameTestHelper h, BlockPos pos) { return (FuelPoolBlockEntity) h.getBlockEntity(pos); }

    @GameTest(template = "test_rbmk")
    public static void poolPortsShareFiniteWaterAndSimulationsDoNotChangeIt(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var a = water(h, BASE);
        var b = water(h, BASE.east().south());
        h.assertTrue(a == b && a.getTankCapacity(0) == POOL_CAPACITY, "Every rack exposes the same shared endpoint");
        h.assertTrue(a.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 0, "The pool rejects lava");
        h.assertTrue(a.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY + 6000), SIMULATE) == POOL_CAPACITY && b.getFluidInTank(0).isEmpty(), "Fill simulation reserves finite space without filling it");
        h.assertTrue(a.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY - 1), EXECUTE) == POOL_CAPACITY - 1
            && b.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 1
            && b.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 1 && a.fill(new FluidStack(Fluids.WATER, 1), EXECUTE) == 0, "Branches cannot duplicate the final mB");
        var trial = a.drain(1000, SIMULATE);
        h.assertTrue(trial.getAmount() == 1000 && b.getFluidInTank(0).getAmount() == POOL_CAPACITY, "Drain simulation is read-only");
        trial.setAmount(1);
        h.assertTrue(a.drain(1000, EXECUTE).getAmount() == 1000 && a.getFluidInTank(0).getAmount() == POOL_CAPACITY - 1000, "Returned stacks do not alias storage");
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE), Direction.EAST) == null, "Only the visible underside accepts pipes");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void poolRodsRequireWaterAndPreserveSavedSlots(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(BASE), Direction.UP);
        var spent = new ItemStack(CENuclear.SPENT_FUEL_ROD.get(), 2);
        h.assertTrue(items.insertItem(0, spent, false).getCount() == 2, "A dry basin cannot load fuel");
        water(h, BASE).fill(new FluidStack(Fluids.WATER, POOL_CAPACITY), EXECUTE);
        h.assertTrue(items.insertItem(0, spent, true).getCount() == 1 && items.getStackInSlot(0).isEmpty(), "Insertion simulation changes no slots");
        h.assertTrue(items.insertItem(0, spent, false).getCount() == 1 && spent.getCount() == 2, "One place holds one spent rod without changing the caller's stack");
        items.insertItem(1, new ItemStack(CENuclear.FUEL_ROD.get()), false);
        var saved = rack(h, BASE).saveWithoutMetadata(h.getLevel().registryAccess());
        items.extractItem(0, 1, false);
        rack(h, BASE).loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(items.extractItem(0, 1, true).is(CENuclear.SPENT_FUEL_ROD.get()) && rack(h, BASE).slot(0) == FuelPoolBlockEntity.SPENT
            && rack(h, BASE).slot(1) == FuelPoolBlockEntity.FRESH && items.extractItem(1, 1, false).isEmpty(), "Reload restores rods; extraction simulation keeps them; fresh rods are reserved for the crane");
        h.assertTrue(water(h, BASE).getFluidInTank(0).getAmount() == POOL_CAPACITY, "Reload does not multiply coolant");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 20)
    public static void formerNinePositionRackSaveReturnsRemovedRods(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var pool = rack(h, BASE);
        var saved = pool.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putByteArray("Slots", new byte[] {2, 1, 2, 2, 1, 1, 2, 2, 2});
        pool.loadWithComponents(saved, h.getLevel().registryAccess());

        h.assertTrue(pool.slot(0) == FuelPoolBlockEntity.SPENT && pool.slot(1) == FuelPoolBlockEntity.SPENT
            && pool.slot(2) == FuelPoolBlockEntity.SPENT && pool.slot(3) == FuelPoolBlockEntity.SPENT,
            "The four old corner positions become the complete 2 x 2 rack");
        var migrated = pool.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(migrated.getByteArray("Slots").length == 4 && migrated.getInt("ReducedGridFresh") == 3
            && migrated.getInt("ReducedGridSpent") == 2,
            "The five retired rack positions remain queued for lossless return until the rack ticks");

        h.runAfterDelay(1, () -> {
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(BASE)).inflate(1));
            int fresh = drops.stream().mapToInt(e -> e.getItem().is(CENuclear.FUEL_ROD.get()) ? e.getItem().getCount() : 0).sum();
            int spent = drops.stream().mapToInt(e -> e.getItem().is(CENuclear.SPENT_FUEL_ROD.get()) ? e.getItem().getCount() : 0).sum();
            h.assertTrue(fresh == 3 && spent == 2, "Contents of all five retired rack positions are returned exactly once: " + fresh + "/" + spent);
            h.succeed();
        });
    }

    @GameTest(template = "test_rbmk")
    public static void mixedWaterDrainsAtItsObservedTemperatureAcrossRackBoundaries(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var water = water(h, BASE);
        water.fill(new FluidStack(Fluids.WATER, 32000), EXECUTE);
        h.assertTrue(water.fill(FluidState.with(new FluidStack(Fluids.WATER, 32000), 9, 4), EXECUTE) == 32000,
            "Cold and warm water must mix through the same supply port");
        var observed = water.getFluidInTank(0);
        h.assertTrue(observed.is(Fluids.WATER), "The open basin mixes both water streams by their heat capacity");
        h.assertTrue(FluidStack.isSameFluidSameComponents(observed, water.drain(1, SIMULATE))
            && FluidStack.isSameFluidSameComponents(observed, water.drain(20000, SIMULATE)), "Create sees the same fluid identity for single-mB and cross-rack previews");
        double before = FluidThermal.energy(observed);
        var taken = water.drain(20000, EXECUTE);
        h.assertTrue(water.getFluidInTank(0).getAmount() == 44000
            && Math.abs(before - FluidThermal.energy(taken) - FluidThermal.energy(water.getFluidInTank(0))) < 1e-5,
            "Draining the mixed body conserves both water mass and heat");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 120)
    public static void brokenWallAndObstructionStopLoadingWithoutLosingContents(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var handler = water(h, BASE);
        handler.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY), EXECUTE);
        rack(h, BASE).set(0, FuelPoolBlockEntity.SPENT);
        BlockPos wall = BASE.west().above(5);
        h.setBlock(wall, Blocks.AIR);
        h.assertTrue(!rack(h, BASE).ready() && handler.fill(new FluidStack(Fluids.WATER, 1), EXECUTE) == 0, "A retained capability immediately stops accepting through a breached wall");
        h.assertTrue(rack(h, BASE).slot(0) == FuelPoolBlockEntity.SPENT, "The stored rod survives the breach");
        h.setBlock(wall, CEMetals.STEEL_BLOCK.get().getBlock());
        h.assertTrue(water(h, BASE).getFluidInTank(0).getAmount() == POOL_CAPACITY && rack(h, BASE).ready(), "Repair restores the same pool without generating water");
        h.setBlock(BASE.above(4), Blocks.STONE);
        h.assertTrue(!rack(h, BASE).ready(), "A blocked rod column prevents fuel handling");
        h.setBlock(BASE.above(4), Blocks.AIR);
        h.assertTrue(rack(h, BASE).ready(), "Clearing the column restores handling");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void oneRackAndOversizedBasinsCannotOperate(GameTestHelper h) {
        build(h, BASE, 1, 1);
        h.assertTrue(water(h, BASE).fill(new FluidStack(Fluids.WATER, 16000), EXECUTE) == 0, "One block cannot operate as a fuel pool");
        build(h, BASE.offset(0, 0, 5), 10, 2);
        h.assertTrue(!rack(h, BASE.offset(0, 0, 5)).ready(), "A ten-wide rack field exceeds the bounded scan");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void maximumNineByNinePoolFormsWithFiniteCapacity(GameTestHelper h) {
        build(h, BASE, 9, 9);
        var water = water(h, BASE);
        int capacity = 81 * FuelPoolBlockEntity.WATER_PER_CELL;
        h.assertTrue(water.getTankCapacity(0) == capacity && water.fill(new FluidStack(Fluids.WATER, capacity + 1), EXECUTE) == capacity,
            "The largest pool forms with exactly 81 finite rack reservoirs");
        h.assertTrue(rack(h, BASE).ready() && water.getFluidInTank(0).getAmount() == capacity,
            "All 81 racks share the filled coolant body without truncating the structure scan");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 100)
    public static void residualHeatWarmsColdWaterWithoutRemovingIt(GameTestHelper h) {
        build(h,BASE,2,2);var water=water(h,BASE);var cold=new FluidStack(Fluids.WATER,POOL_CAPACITY);
        double inletKelvin=FluidThermal.kelvin(cold);
        water.fill(cold,EXECUTE);rack(h,BASE).set(0,FuelPoolBlockEntity.SPENT);
        h.runAfterDelay(45,()->{
            double paid=0;for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
                var tag=h.getBlockEntity(BASE.offset(x,0,z)).saveWithoutMetadata(h.getLevel().registryAccess());
                paid+=(tag.getCompound("PoolWater").getDouble("Kelvin")-inletKelvin)*FuelPoolBlockEntity.WATER_PER_CELL*4.18;
            }
            int held=water.getFluidInTank(0).getAmount();
            h.assertTrue(paid>=1999 && paid<=3001 && held==POOL_CAPACITY,"Residual heat warms the pool before it boils away water: "+paid+" J / "+held+" mB");
            water.drain(POOL_CAPACITY,EXECUTE);h.assertTrue(!rack(h,BASE).ready(),"Dry racks stop loading");h.succeed();
        });
    }

    @GameTest(template = "test_rbmk")
    public static void hotPoolRejectsFuelUntilCoolWaterIsSupplied(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var coolant = water(h, BASE);
        var hot = FluidState.with(new FluidStack(Fluids.WATER, POOL_CAPACITY), 9, 4);
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(BASE), Direction.DOWN);
        var fresh = CENuclear.FUEL_ROD.toStack();
        h.assertTrue(coolant.fill(hot, EXECUTE) == POOL_CAPACITY && !rack(h, BASE).ready()
            && !items.insertItem(0, fresh, true).isEmpty(), "93.75 °C water is stored but prevents fuel handling");
        coolant.drain(POOL_CAPACITY / 2, EXECUTE);
        h.assertTrue(coolant.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY / 2), EXECUTE) == POOL_CAPACITY / 2
            && rack(h, BASE).ready() && items.insertItem(0, fresh, false).isEmpty(), "Partial cold refill makes the full pool ready again");
        h.succeed();
    }
    @GameTest(template = "test_rbmk")
    public static void destroyingARackDropsRodsOnceAndRetainsOtherTilesWater(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var old = water(h, BASE);
        old.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY), EXECUTE);
        rack(h, BASE).set(0, FuelPoolBlockEntity.SPENT);
        h.getLevel().destroyBlock(h.absolutePos(BASE), false);
        var dropped = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(h.absolutePos(BASE)).inflate(1));
        h.assertTrue(dropped.stream().mapToInt(e -> e.getItem().is(CENuclear.SPENT_FUEL_ROD.get()) ? e.getItem().getCount() : 0).sum() == 1,
            "Removing the floor drops its one rod exactly once");
        h.assertTrue(old.getFluidInTank(0).isEmpty() && old.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 0,
            "A cached handler cannot access a removed controller");
        h.setBlock(BASE, CENuclear.FUEL_POOL.get());
        h.assertTrue(water(h, BASE).getFluidInTank(0).getAmount() == 3 * FuelPoolBlockEntity.WATER_PER_CELL && !rack(h, BASE).ready(),
            "The other three tiles retain their water; replacing the lost tile does not refill it");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 100)
    public static void boilingConsumesFiniteWaterInsteadOfCreatingSteamItems(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var water = water(h, BASE);
        water.fill(FluidState.with(new FluidStack(Fluids.WATER, POOL_CAPACITY), 9, 4), EXECUTE);
        for (int x=0; x<2; x++) for (int z=0; z<2; z++) {
            var rack = rack(h, BASE.offset(x, 0, z));
            var saved = rack.saveWithoutMetadata(h.getLevel().registryAccess());
            saved.getCompound("PoolWater").putDouble("Kelvin", 373.14);
            rack.loadWithComponents(saved, h.getLevel().registryAccess());
        }
        for (int i = 0; i < 4; i++) rack(h, BASE).set(i, FuelPoolBlockEntity.SPENT);
        // The 23,000 mB rack takes one more lazy-tick interval than the former deeper pool to cross the boiling threshold.
        h.runAfterDelay(81, () -> {
            int left = water.getFluidInTank(0).getAmount();
            h.assertTrue(left < POOL_CAPACITY && left > POOL_CAPACITY - 10 && FluidThermal.kelvin(water.getFluidInTank(0)) <= 373.15,
                "Residual heat boils off a small measured amount at 100 C: " + left);
            h.succeed();
        });
    }

    @GameTest(template = "test_rbmk", batch = "fuel_pool_pump", timeoutTicks = 200)
    public static void createPumpConnectsUnderRackAndTransfersRealWater(GameTestHelper h) {
        build(h, BASE, 2, 2);
        var sourcePos = BASE.west(4);
        build(h, sourcePos, 2, 2);
        var source = water(h, sourcePos);
        source.fill(new FluidStack(Fluids.WATER, POOL_CAPACITY), EXECUTE);
        var pump = BASE.west(3).below(2);
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(sourcePos.below(), pipe(Direction.UP, Direction.DOWN));
        h.setBlock(sourcePos.below(2), pipe(Direction.UP, Direction.EAST));
        for (int x = 1; x < 3; x++) h.setBlock(BASE.west(x).below(2), pipe(Direction.WEST, Direction.EAST));
        h.setBlock(BASE.below(2), pipe(Direction.WEST, Direction.UP));
        h.setBlock(BASE.below(), pipe(Direction.UP, Direction.DOWN));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        var motor = pump.south().west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.runAfterDelay(160, () -> {
            int stored = water(h, BASE).getFluidInTank(0).getAmount();
            h.assertTrue(h.getBlockState(BASE.below()).getValue(net.minecraft.world.level.block.PipeBlock.UP), "Create shapes the pipe connection toward the rack's real fluid handler");
            h.assertTrue(stored > 0 && stored + source.getFluidInTank(0).getAmount() == POOL_CAPACITY,
                "A driven Create pump drains one open pool and fills another through real pipes, conserving all water: " + stored + "/" + source.getFluidInTank(0).getAmount());
            h.succeed();
        });
    }
}
