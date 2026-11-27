package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class LiddedVesselGameTests {
    private static final BlockPos BASE = new BlockPos(3, 2, 3);

    private static LiddedVesselBlockEntity at(GameTestHelper h, BlockPos pos) { return (LiddedVesselBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos)); }

    private static LiddedVesselBlockEntity root(GameTestHelper h, BlockPos pos) { return (LiddedVesselBlockEntity) at(h, pos).getControllerBE(); }

    private static void square(GameTestHelper h, int width) {
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) h.setBlock(BASE.offset(x, 0, z), CETransport.LIDDED_VESSEL.get().defaultBlockState());
    }

    private static void stirredSquare(GameTestHelper h, int width, int layers) {
        for (int y = 0; y < layers; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) h.setBlock(BASE.offset(x, y, z), CETransport.STIRRED_TANK.get().defaultBlockState());
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void aStirredTankIsABoreWithoutItsStirrerCatchesThrownItemsAndStopsAtThreeHigh(GameTestHelper h) {
        stirredSquare(h, 3, 4);
        var root = root(h, BASE);
        h.assertTrue(root.getWidth() == 3 && root.getHeight() == LiddedVesselBlockEntity.STIRRED_MAX_HEIGHT && root(h, BASE.above(3)).getHeight() == 1, "A stirred tank stands at most three blocks high");
        h.assertTrue(root.bored() && root.openTop() && !root.open() && !root.stirred(), "A 3x3 stirred tank has its open round bore before any stirrer is fitted");
        root.toggleLid();
        h.assertTrue(!root.open(), "The bore has no hatch to toggle");
        int capacity = root.getTankInventory().getCapacity();
        h.assertTrue(capacity == 76000 && capacity < 9 * 3 * LiddedVesselBlockEntity.getCapacityMultiplier(), "The round bore holds less than the square blocks round it: " + capacity);
        var items = root.itemPort();
        int accepted = 0;
        for (var item : new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.COPPER_INGOT, net.minecraft.world.item.Items.COAL, net.minecraft.world.item.Items.FLINT})
            if (net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(items, new net.minecraft.world.item.ItemStack(item, 64), false).isEmpty()) accepted++;
        h.assertTrue(accepted == 4, "The charge holds four stacks: " + accepted);
        for (int slot = 0; slot < 4; slot++) items.extractItem(slot, 64, false);
        var thrown = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), h.absolutePos(BASE).getX() + 1.5, h.absolutePos(BASE).getY() + 2.5, h.absolutePos(BASE).getZ() + 1.5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        h.getLevel().addFreshEntity(thrown);
        h.runAfterDelay(60, () -> {
            h.assertTrue(thrown.isRemoved() && items.getStackInSlot(0).is(net.minecraft.world.item.Items.IRON_INGOT) && items.getStackInSlot(0).getCount() == 5, "An item thrown into the bore lands in the item slots: " + items.getStackInSlot(0));
            stirredSquare(h, 2, 1);
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void storageAndBuffersShareTheStirredTanksVolume(GameTestHelper h) {
        stirredSquare(h, 3, 3);
        var root = root(h, BASE);
        ReactorGameTests.stir(h.getLevel(), h.absolutePos(BASE));
        root.validateStructure();
        h.assertTrue(root.stirred() && root.getTankInventory().getCapacity() == 76000, "A three-high stirred tank holds 76000 mB");
        root.getTankInventory().fill(new FluidStack(Fluids.WATER, 40000), FluidAction.EXECUTE);
        h.assertTrue(root.getDisplayedCapacity(2) == 36000, "With 40000 mB of water in, a product buffer has the remaining 36000 mB: " + root.getDisplayedCapacity(2));
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.offset(2, 1, 1)), Direction.EAST);
        h.assertTrue(port != null && port.fill(new FluidStack(Fluids.WATER, 50000), FluidAction.EXECUTE) == 36000 && port.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
            "Pipes fill only the free part of the shared volume");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aFullStirredTankStillTurnsItsWaterIntoBrine(GameTestHelper h) {
        stirredSquare(h, 3, 3);
        var root = root(h, BASE);
        ReactorGameTests.stir(h.getLevel(), h.absolutePos(BASE));
        root.validateStructure();
        root.getTankInventory().fill(new FluidStack(Fluids.WATER, 76000), FluidAction.EXECUTE);
        root.itemPort().insertItem(0, new net.minecraft.world.item.ItemStack(net.createplant.registry.CEChlorAlkali.SALT.get(), 4), false);
        ReactorGameTests.speed(root, 32);
        for (int i = 0; i < 1200; i++) root.tickProcess();
        long brine = 0, total = 0;
        for (int slot = 0; slot < root.displayedFluidSlots(); slot++) {
            var fluid = root.displayedLiquid(slot);
            total += fluid.getAmount();
            if (fluid.is(net.createplant.registry.CEChlorAlkali.BRINE.get())) brine += fluid.getAmount();
        }
        h.assertTrue(brine == 1000 && total == 76000, "Water becomes brine in a full tank because the reaction adds no volume: brine " + brine + ", total " + total + ", " + root.status());
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aTwoByTwoStirredTankIsAPlainTank(GameTestHelper h) {
        stirredSquare(h, 2, 2);
        var root = root(h, BASE);
        h.assertTrue(root.getWidth() == 2 && !root.bored() && root.getTankInventory().getCapacity() == 8 * LiddedVesselBlockEntity.getCapacityMultiplier(), "Smaller stirred tanks keep the square hatch and full volume");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void funnelsCanTakeTheFeedOutOfAStirringTank(GameTestHelper h) {
        stirredSquare(h, 3, 2);
        var root = root(h, BASE);
        ReactorGameTests.stir(h.getLevel(), h.absolutePos(BASE));
        root.validateStructure();
        h.assertTrue(root.stirred(), "The tank stirs");
        var side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(BASE.offset(2, 1, 1)), Direction.EAST);
        h.assertTrue(side != null && side.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLINT, 3), false).isEmpty(), "A side port accepts feed");
        h.assertTrue(side.extractItem(0, 64, false).getCount() == 3, "Like a basin, the unused feed can be pulled back out through a side port");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void onlyAStirredTankStirsAndItDoesNotJoinAStainlessTank(GameTestHelper h) {
        square(h, 3);
        var root = root(h, BASE);
        ReactorGameTests.stir(h.getLevel(), h.absolutePos(BASE));
        root.validateStructure();
        h.assertTrue(root.getWidth() == 3 && !root.stirred(), "A stirrer on a plain stainless tank stirs nothing");
        h.setBlock(BASE.offset(3, 0, 0), CETransport.STIRRED_TANK.get().defaultBlockState());
        h.assertTrue(root(h, BASE).getWidth() == 3 && root(h, BASE.offset(3, 0, 0)).getWidth() == 1, "A stirred tank never merges with a stainless tank beside it");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void vesselsJoinUpToThreeByThreeButStayOneBlockHigh(GameTestHelper h) {
        square(h, 3);
        h.setBlock(BASE.offset(1, 1, 1), CETransport.LIDDED_VESSEL.get().defaultBlockState());
        var root = root(h, BASE);
        h.assertTrue(root.getWidth() == 3 && root.getHeight() == 1, "Nine vessels form one 3x3 vessel one block high: " + root.getWidth() + "x" + root.getHeight());
        h.assertTrue(root(h, BASE.offset(1, 1, 1)).getWidth() == 1, "A vessel stacked on top stays a vessel of its own");
        h.assertTrue(root.getTankInventory().getCapacity() == 9 * LiddedVesselBlockEntity.getCapacityMultiplier(), "Capacity grows with every block");
        var corner = h.getBlockState(BASE);
        var middle = h.getBlockState(BASE.offset(1, 0, 1));
        h.assertTrue(corner.getValue(LiddedVesselBlock.NORTH) && corner.getValue(LiddedVesselBlock.WEST) && !corner.getValue(LiddedVesselBlock.EAST) && !corner.getValue(LiddedVesselBlock.SOUTH),
            "The north-west block has walls on its two outer faces only");
        h.assertTrue(!middle.getValue(LiddedVesselBlock.NORTH) && !middle.getValue(LiddedVesselBlock.EAST) && !middle.getValue(LiddedVesselBlock.SOUTH) && !middle.getValue(LiddedVesselBlock.WEST),
            "The middle block has no walls");
        h.getLevel().destroyBlock(h.absolutePos(BASE.offset(2, 0, 2)), false);
        var after = h.getBlockState(BASE.offset(2, 0, 1));
        h.assertTrue(root(h, BASE.offset(2, 0, 1)).getWidth() < 3 && after.getValue(LiddedVesselBlock.EAST), "Breaking a corner splits the vessel and the cut faces become outer walls again");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void fullLayersStackUpToFourHighWithTheFloorAndLidOnlyAtTheEnds(GameTestHelper h) {
        for (int y = 0; y < 5; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) h.setBlock(BASE.offset(x, y, z), CETransport.LIDDED_VESSEL.get().defaultBlockState());
        var root = root(h, BASE);
        h.assertTrue(root.getWidth() == 2 && root.getHeight() == 4, "Four 2x2 layers join into one vessel: " + root.getWidth() + "x" + root.getHeight());
        h.assertTrue(root(h, BASE.above(4)).getHeight() == 1, "A fifth layer is a vessel of its own");
        h.assertTrue(h.getBlockState(BASE).getValue(LiddedVesselBlock.BOTTOM) && !h.getBlockState(BASE.above()).getValue(LiddedVesselBlock.BOTTOM), "Only the bottom layer has the floor");
        root.toggleLid();
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.above(3)), Direction.UP) == null, "The open lid is on the top layer");
        var lower = h.getBlockState(BASE.above()).getCollisionShape(level, h.absolutePos(BASE.above()), CollisionContext.empty());
        var top = h.getBlockState(BASE.above(3)).getCollisionShape(level, h.absolutePos(BASE.above(3)), CollisionContext.empty());
        h.assertTrue(lower.toAabbs().size() == 1 && lower.bounds().getSize() == 1, "Only the top is opened by the hatch: " + lower.toAabbs());
        h.assertTrue(top.toAabbs().size() > 1, "The top block has its quarter of the hatch hollow: " + top.toAabbs());
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void theHatchInTheMiddleOpensByHandAndOnlyItsTopPortLeaves(GameTestHelper h) {
        square(h, 3);
        var level = h.getLevel();
        BlockPos middle = BASE.offset(1, 0, 1), edge = BASE.offset(2, 0, 1);
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(middle), Direction.UP) != null, "A closed vessel takes a pipe on its top");
        at(h, edge).toggleLid();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.assertTrue(h.getBlockState(BASE.offset(x, 0, z)).getValue(LiddedVesselBlock.OPEN), "Every block knows the hatch is open");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(middle), Direction.UP) == null, "The block under the open hatch has no top port");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(edge), Direction.UP) != null, "A pipe on the top beside the hatch stays connected");
        var side = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(edge), Direction.EAST);
        h.assertTrue(side != null && side.fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE) == 3000, "The sides still take fluid while it is open");
        var under = h.getBlockState(middle).getCollisionShape(level, h.absolutePos(middle), CollisionContext.empty());
        h.assertTrue(under.max(Direction.Axis.Y) <= 2 / 16d && !under.isEmpty(), "Under the open hatch only the floor is left: " + under.toAabbs());
        var beside = h.getBlockState(edge).getCollisionShape(level, h.absolutePos(edge), CollisionContext.empty());
        h.assertTrue(beside.toAabbs().size() == 1 && beside.bounds().getSize() == 1, "Beside the hatch the top stays closed");
        at(h, BASE).toggleLid();
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(middle), Direction.UP) != null, "Closing it again reconnects the top");
        h.assertTrue(root(h, BASE).getTankInventory().getFluidAmount() == 3000, "Opening and closing keeps the liquid");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aHeldVesselPlacesAWholeLayerAndTheLadderIsClimbedFromItsSide(GameTestHelper h) {
        square(h, 3);
        var level = h.getLevel();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stack = new net.minecraft.world.item.ItemStack(CETransport.LIDDED_VESSEL.get(), 64);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        BlockPos top = h.absolutePos(BASE.offset(2, 0, 0));
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(top).add(0, .5, 0), Direction.UP, top, false);
        var result = h.getBlockState(BASE.offset(2, 0, 0)).useItemOn(stack, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(result == net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && !h.getBlockState(BASE).getValue(LiddedVesselBlock.OPEN), "A vessel in the hand never opens the hatch");
        stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var root = root(h, BASE);
        h.assertTrue(root.getWidth() == 3 && root.getHeight() == 2 && stack.getCount() == 55, "One vessel placed on a 3x3 fills the whole layer above: " + root.getWidth() + "x" + root.getHeight() + ", left " + stack.getCount());
        BlockPos beside = BASE.offset(-1, 1, 0);
        h.assertTrue(net.createplant.block.ColumnLadder.isLadderAt(level, h.absolutePos(beside)) && !net.createplant.block.ColumnLadder.isLadderAt(level, h.absolutePos(beside.south())),
            "The ladder is climbed at the west wall's left edge when viewed from outside");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void openingUnderPressureVentsTheGasAndKeepsTheVesselWhole(GameTestHelper h) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)h.setBlock(BASE.offset(x,0,z), CETransport.LIDDED_VESSEL.get().defaultBlockState());
        var root = root(h, BASE);
        var gas = FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(), 160000), 293.15);
        h.assertTrue(root.getTankInventory().fill(gas, FluidAction.EXECUTE) == 160000, "The closed vessel holds compressed nitrogen");
        root.toggleLid();
        h.assertTrue(root.getTankInventory().isEmpty(), "The open lid lets all the gas out");
        h.assertBlockPresent(CETransport.LIDDED_VESSEL.get(), BASE);
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aGasHeavierThanAirSettlesInTheOpenVessel(GameTestHelper h) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)h.setBlock(BASE.offset(x,0,z), CETransport.LIDDED_VESSEL.get().defaultBlockState());
        var root = root(h, BASE);
        var tank = (net.createplant.thermal.GradeFluidTank) root.getTankInventory();
        int volume = tank.volumeCapacity();
        var gas = FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.CARBON_DIOXIDE.get(), volume * 4), 293.15);
        h.assertTrue(tank.fill(gas, FluidAction.EXECUTE) == volume * 4, "The closed vessel holds compressed carbon dioxide");
        root.toggleLid();
        var left = tank.getFluid();
        h.assertTrue(left.getAmount() == volume && net.createplant.thermal.FluidState.pressure(left) == net.createplant.thermal.FluidState.pressureLevel(1),
            "Opening lets out the compression but carbon dioxide, heavier than air, stays at atmospheric pressure: " + left.getAmount() + " of " + volume);
        root.toggleLid();root.toggleLid();
        h.assertTrue(tank.getFluidAmount() == volume, "Opening again loses none of the settled gas");
        h.succeed();
    }
}
