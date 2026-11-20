package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ColumnLadder;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class LiddedVesselPlacementGameTests {
    private static final BlockPos BASE = new BlockPos(3, 2, 3);

    private static LiddedVesselBlockEntity root(GameTestHelper h, BlockPos at) {
        return (LiddedVesselBlockEntity) ((LiddedVesselBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at))).getControllerBE();
    }

    private static void square(GameTestHelper h, int width) { square(h, width, Direction.NORTH); }

    private static void square(GameTestHelper h, int width, Direction hinge) { square(h, BASE, width, hinge); }

    private static void square(GameTestHelper h, BlockPos base, int width, Direction hinge) {
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
            h.setBlock(base.offset(x, 0, z), CETransport.LIDDED_VESSEL.get().defaultBlockState().setValue(LiddedVesselBlock.HINGE, hinge));
    }

    private static int vessels(GameTestHelper h, BlockPos base, int width) {
        int count = 0;
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) if (h.getBlockState(base.offset(x, 0, z)).is(CETransport.LIDDED_VESSEL.get())) count++;
        return count;
    }

    private static Player holding(GameTestHelper h, GameType mode, InteractionHand hand, ItemStack stack) {
        var player = h.makeMockPlayer(mode);
        mode.updatePlayerAbilities(player.getAbilities());
        player.setYRot(Direction.NORTH.toYRot());
        player.setItemInHand(hand, stack);
        return player;
    }

    private static BlockHitResult hit(GameTestHelper h, BlockPos at, Direction face) {
        BlockPos absolute = h.absolutePos(at);
        return new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, absolute, false);
    }

    private static InteractionResult place(GameTestHelper h, Player player, InteractionHand hand, BlockPos at, Direction face) {
        return player.getItemInHand(hand).useOn(new UseOnContext(player, hand, hit(h, at, face)));
    }

    @GameTest(template = "test_fractionation")
    public static void aTwoWideLayerUsesExactlyFourItemsWithoutOpeningTheHatch(GameTestHelper h) {
        square(h, 2);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 4);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND, stack);
        var at = BASE.offset(1, 0, 1);
        h.assertTrue(h.getBlockState(at).useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h, at, Direction.UP))
            == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "Holding a vessel skips the empty-hand hatch interaction");
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, at, Direction.UP).consumesAction(), "A vessel can be placed on the outer corner of the 2x2");
        h.assertTrue(vessels(h, BASE.above(), 2) == 4 && root(h, BASE).getWidth() == 2 && root(h, BASE).getHeight() == 2 && stack.isEmpty(),
            "The whole 2x2 layer is placed and exactly four items are consumed");
        h.assertTrue(!root(h, BASE).open(), "Placing a layer keeps the hatch closed");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aThreeWideVesselAlsoGrowsByAWholeLayerFromBelow(GameTestHelper h) {
        BlockPos base = BASE.above();
        square(h, base, 3, Direction.NORTH);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            h.assertTrue(h.getBlockState(base.offset(x, -1, z)).isAir(), "The fixture leaves clear space below the vessel");
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 10);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND, stack);
        var result = place(h, player, InteractionHand.MAIN_HAND, base.offset(2, 0, 0), Direction.DOWN);
        h.assertTrue(result.consumesAction(), "The lower outer corner accepts another layer: " + result);
        h.assertTrue(vessels(h, base.below(), 3) == 9 && root(h, base).getBlockPos().equals(h.absolutePos(base.below()))
            && root(h, base).getWidth() == 3 && root(h, base).getHeight() == 2 && stack.getCount() == 1,
            "Nine blocks form the lower layer, relocate the controller and consume nine items");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aVesselInTheOffHandDoesNotLoseItsClickToTheEmptyMainHand(GameTestHelper h) {
        square(h, 3);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 9);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.OFF_HAND, stack);
        var at = BASE.offset(2, 0, 1);
        h.assertTrue(h.getBlockState(at).useWithoutItem(h.getLevel(), player, hit(h, at, Direction.UP)) == InteractionResult.PASS && !root(h, BASE).open(),
            "The empty main-hand fallback leaves the hatch closed while the off hand holds a vessel");
        h.assertTrue(place(h, player, InteractionHand.OFF_HAND, at, Direction.UP).consumesAction() && vessels(h, BASE.above(), 3) == 9 && stack.isEmpty(),
            "The off hand can place all nine vessels");
        h.assertTrue(!root(h, BASE).open(), "Off-hand placement never opens the hatch");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var top = at.above();
        h.assertTrue(h.getBlockState(top).useWithoutItem(h.getLevel(), player, hit(h, top, Direction.UP)).consumesAction() && root(h, BASE).open(),
            "With both hands empty, the hatch still opens normally");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tooFewItemsOrAnObstructedLayerPlacesNothingAndConsumesNothing(GameTestHelper h) {
        square(h, 3);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 8);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND, stack);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.UP) == InteractionResult.FAIL
            && vessels(h, BASE.above(), 3) == 0 && stack.getCount() == 8, "Eight items cannot leave a partial 3x3 layer or consume one item");
        stack.setCount(9);
        var obstruction = BASE.offset(2, 1, 2);
        h.setBlock(obstruction, Blocks.STONE);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.UP) == InteractionResult.FAIL
            && vessels(h, BASE.above(), 3) == 0 && stack.getCount() == 9, "A blocked far corner prevents the entire layer before any item is spent");
        h.assertBlockPresent(Blocks.STONE, obstruction);
        h.assertTrue(root(h, BASE).getWidth() == 3 && root(h, BASE).getHeight() == 1 && !root(h, BASE).open(), "A failed placement keeps the original vessel whole and closed");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aCollidingEntityPreventsTheEntireLayer(GameTestHelper h) {
        square(h, 2);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 4);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND, stack);
        var occupied = h.absolutePos(BASE.offset(1, 1, 1));
        var stand = new ArmorStand(h.getLevel(), occupied.getX() + .5, occupied.getY(), occupied.getZ() + .5);
        stand.setNoGravity(true);
        h.getLevel().addFreshEntity(stand);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.UP) == InteractionResult.FAIL
            && vessels(h, BASE.above(), 2) == 0 && stack.getCount() == 4, "Collision at a different cell stops the whole layer before placement or consumption");
        stand.discard();
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void adventureModeCannotBypassPlacementPermissionsThroughTheBulkItem(GameTestHelper h) {
        square(h, 2);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 4);
        var player = holding(h, GameType.ADVENTURE, InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit(h, BASE, Direction.UP));
        h.assertTrue(((net.minecraft.world.item.BlockItem) stack.getItem()).place(context) == InteractionResult.FAIL
            && vessels(h, BASE.above(), 2) == 0 && stack.getCount() == 4, "The bulk item respects mayUseItemAt instead of placing directly");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void creativeModeDoesNotConsumeAndSneakingPlacesOnlyOneBlock(GameTestHelper h) {
        square(h, 3);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 1);
        var player = holding(h, GameType.CREATIVE, InteractionHand.MAIN_HAND, stack);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.UP).consumesAction() && vessels(h, BASE.above(), 3) == 9 && stack.getCount() == 1,
            "Creative placement fills a 3x3 layer without consuming its one held item");
        player.setShiftKeyDown(true);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE.above(), Direction.UP).consumesAction() && vessels(h, BASE.above(2), 3) == 1 && stack.getCount() == 1,
            "Sneaking keeps ordinary single-block placement");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aSingleVesselAndSidePlacementStaySingleBlockOperations(GameTestHelper h) {
        square(h, 1);
        var stack = new ItemStack(CETransport.LIDDED_VESSEL.get(), 3);
        var player = holding(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND, stack);
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.UP).consumesAction() && stack.getCount() == 2
            && root(h, BASE).getWidth() == 1 && root(h, BASE).getHeight() == 2, "A 1x1 column grows by only one block");
        h.assertTrue(place(h, player, InteractionHand.MAIN_HAND, BASE, Direction.EAST).consumesAction() && stack.getCount() == 1
            && h.getBlockState(BASE.east()).is(CETransport.LIDDED_VESSEL.get()), "Clicking a side places one vessel without invoking vertical bulk placement");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void eachFacingAndWidthHasOneClimbableLadderColumnOnOnlyOneSide(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int width = 1; width <= 3; width++) for (Direction hinge : Direction.Plane.HORIZONTAL) {
            clearColumn(h);
            for (int y = 0; y < 4; y++) square(h, BASE.above(y), width, hinge);
            h.assertTrue(root(h, BASE).getHeight() == 4, "Every fixture merges into one four-layer vessel");
            root(h, BASE).toggleWindows();
            for (int y = 0; y < 4; y++) assertLadderLayer(h, player, width, hinge, y);
        }
        h.succeed();
    }

    private static void clearColumn(GameTestHelper h) {
        for (int y = 3; y >= 0; y--) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(BASE.offset(x, y, z), Blocks.AIR);
    }

    private static BlockPos leftEdge(int width, Direction hinge, int y) {
        return switch (hinge) {
            case NORTH -> BASE.offset(0, y, 0);
            case SOUTH -> BASE.offset(width - 1, y, width - 1);
            case WEST -> BASE.offset(0, y, width - 1);
            case EAST -> BASE.offset(width - 1, y, 0);
            default -> throw new IllegalArgumentException(hinge.toString());
        };
    }

    private static void assertLadderLayer(GameTestHelper h, Player player, int width, Direction hinge, int y) {
        int ladders = 0, climbable = 0;
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            BlockPos at = BASE.offset(x, y, z);
            h.assertTrue(h.getBlockState(at).getValue(LiddedVesselBlock.LADDER) == at.equals(leftEdge(width, hinge, y)),
                "Only the outside viewer's leftmost wall cell carries a ladder: " + width + ", " + hinge + ", layer " + y);
            h.assertTrue(root(h, at).windowsEnabled(), "Windows remain enabled along the complete climbing column");
            if (h.getBlockState(at).getValue(LiddedVesselBlock.LADDER)) ladders++;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (x + side.getStepX() >= 0 && x + side.getStepX() < width && z + side.getStepZ() >= 0 && z + side.getStepZ() < width) continue;
                BlockPos beside = at.relative(side);
                boolean expected = h.getBlockState(at).getValue(LiddedVesselBlock.LADDER) && side == hinge.getCounterClockWise();
                h.assertTrue(ColumnLadder.isLadderAt(h.getLevel(), h.absolutePos(beside)) == expected, "Only the ladder-facing exterior cell is climbable: " + width + ", " + hinge + ", " + side);
                if (expected) {
                    climbable++;
                    BlockPos absolute = h.absolutePos(beside);
                    player.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
                    h.assertTrue(player.onClimbable(), "The actual living-entity climbing path recognizes the vessel ladder");
                }
            }
        }
        h.assertTrue(ladders == 1 && climbable == 1, "There is exactly one continuous ladder column per layer for width " + width + " and hinge " + hinge);
    }

    @GameTest(template = "test_fractionation")
    public static void oldFrontLaddersMoveToTheLeftSideWithoutMovingStoredMaterials(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (int width = 2; width <= 3; width++) for (Direction hinge : Direction.Plane.HORIZONTAL) {
            clearColumn(h);
            for (int y = 0; y < 4; y++) square(h, BASE.above(y), width, hinge);
            var owner = root(h, BASE);owner.toggleWindows();
            var impeller = CEReactor.INTERNALS.get(ReactorModule.STIRRED).get();
            for (int y = 0; y < 4; y++) {
                if (width == 3) owner.putInternal(impeller.getDefaultInstance());
                else owner.slot(y).setInternal(impeller.getDefaultInstance());
            }
            owner.validateStructure();
            owner.getTankInventory().fill(FluidThermal.at(new FluidStack(Fluids.WATER, 1000), 310), EXECUTE);
            owner.itemPort().insertItem(0, new ItemStack(Items.IRON_INGOT, 7), false);
            double energy = FluidThermal.energy(owner.getTankInventory().getFluid());
            var lookup = h.getLevel().registryAccess();
            for (int y = 0; y < 4; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                BlockPos at = BASE.offset(x, y, z);var part = (LiddedVesselBlockEntity) h.getBlockEntity(at);
                var saved = part.saveWithoutMetadata(lookup);Direction side = hinge.getOpposite();
                int across = side.getAxis() == Direction.Axis.Z ? x : z, out = side.getAxis() == Direction.Axis.Z ? z : x;
                int oldColumn = y % 2 == 0 ? (width - 1) / 2 : side == Direction.NORTH || side == Direction.EAST ? width - 1 : 0;
                boolean old = across == oldColumn && out == (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? width - 1 : 0);
                h.setBlock(at, h.getBlockState(at).setValue(LiddedVesselBlock.LADDER, old));
                part.loadWithComponents(saved, lookup);part.lazyTick();
            }
            for (int y = 0; y < 4; y++) {
                assertLadderLayer(h, player, width, hinge, y);
                h.assertTrue(((LiddedVesselBlockEntity) h.getBlockEntity(BASE.above(y))).internal().is(impeller), "Migration keeps each impeller in its original layer slot");
            }
            h.assertTrue(owner.getTankInventory().getFluidAmount() == 1000 && Math.abs(FluidThermal.energy(owner.getTankInventory().getFluid()) - energy) < 1e-6
                && owner.itemPort().getStackInSlot(0).is(Items.IRON_INGOT) && owner.itemPort().getStackInSlot(0).getCount() == 7,
                "Reloading an old ladder layout preserves all fluid heat and stored items");
        }
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void ladderMigrationWaitsForTheControllerWithoutLoadingItsChunk(GameTestHelper h) {
        square(h, 3);
        BlockPos at = BASE.above();square(h, at, 3, Direction.NORTH);var part = (LiddedVesselBlockEntity) h.getBlockEntity(at);
        var lookup = h.getLevel().registryAccess();var saved = part.saveWithoutMetadata(lookup);
        var detached = saved.copy();BlockPos absent = h.absolutePos(BASE).offset(1_000_000, 0, 1_000_000);
        h.assertTrue(!h.getLevel().hasChunkAt(absent), "The fixture controller chunk starts unloaded");
        detached.put("Controller", net.minecraft.nbt.NbtUtils.writeBlockPos(absent));
        h.setBlock(at, h.getBlockState(at).setValue(LiddedVesselBlock.LADDER, false));
        part.loadWithComponents(detached, lookup);part.initialize();part.lazyTick();
        h.assertTrue(!h.getLevel().hasChunkAt(absent) && !h.getBlockState(at).getValue(LiddedVesselBlock.LADDER),
            "A pending migration neither loads the missing owner chunk nor guesses a ladder position");
        part.loadWithComponents(saved, lookup);part.lazyTick();
        h.assertTrue(h.getBlockState(at).getValue(LiddedVesselBlock.LADDER) && ColumnLadder.isLadderAt(h.getLevel(), h.absolutePos(at.west())),
            "The deferred migration updates the correct climbable left edge once its owner is available");h.succeed();
    }
}
