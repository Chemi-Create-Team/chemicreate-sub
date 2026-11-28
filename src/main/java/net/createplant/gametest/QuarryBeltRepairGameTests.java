package net.createplant.gametest;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.CraneFrame;
import net.createplant.block.QuarryBeltLayout;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CEQuarry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class QuarryBeltRepairGameTests {
    private static final BlockPos DRIVE = new BlockPos(3, 8, 3);

    private static QuarryBeltLayout layout(GameTestHelper h) {
        return new QuarryBeltLayout(h.absolutePos(DRIVE.east()), Direction.EAST, Direction.SOUTH, 64);
    }

    private static BeltBlockEntity belt(GameTestHelper h, BlockPos pos) { return (BeltBlockEntity) h.getLevel().getBlockEntity(pos); }

    private static void buildBelt(GameTestHelper h, BlockPos first, BlockPos last) {
        var level = h.getLevel();
        Direction.Axis axis = first.getX() == last.getX() ? Direction.Axis.X : Direction.Axis.Z;
        var shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
        level.setBlockAndUpdate(first, shaft);
        level.setBlockAndUpdate(last, shaft);
        BeltConnectorItem.createBelts(level, first, last);
        BeltBlock.initBelt(level, first);
        h.assertTrue(belt(h, first).isController(), "The first shaft becomes this belt's controller");
    }

    @GameTest(template = "test_quarry_belt_repair")
    public static void theFiveBlockTailPreservesActiveAndPendingItemsWhenReversed(GameTestHelper h) {
        var layout = layout(h);
        var segments = layout.segments();
        var tail = segments.getLast();
        h.assertTrue(tail.last() - tail.first() + 1 == 5, "The 64-block quarry ends with a five-block belt");
        var previous = segments.get(segments.size() - 2);
        buildBelt(h, layout.belt(previous.last()), layout.belt(previous.first()));
        buildBelt(h, layout.belt(tail.first()), layout.belt(tail.last()));
        BlockPos pulley = layout.belt(tail.first() + 2);
        h.getLevel().setBlock(pulley, h.getLevel().getBlockState(pulley).setValue(BeltBlock.PART, BeltPart.PULLEY)
            .setValue(BeltBlock.CASING, true), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        BeltBlock.initBelt(h.getLevel(), pulley);

        var oldInventory = belt(h, layout.belt(tail.first())).getInventory();
        var named = new ItemStack(Items.RAW_IRON, 23);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Quarry shipment"));
        var active = new TransportedItemStack(named);
        active.beltPosition = 1.25f;
        active.prevBeltPosition = 1.125f;
        active.insertedAt = 1;
        active.insertedFrom = Direction.NORTH;
        active.lockedExternally = true;
        oldInventory.getTransportedItems().add(active);
        var pending = new TransportedItemStack(new ItemStack(Items.RAW_GOLD, 17));
        pending.beltPosition = pending.prevBeltPosition = 3.5f;
        pending.insertedAt = 3;
        h.assertTrue(layout.hasItems(h.getLevel()), "The reversed controller's cargo is counted");

        var registries = h.getLevel().registryAccess();
        var oldController = belt(h, layout.belt(tail.first()));
        var activePosition = BeltHelper.getVectorForOffset(oldController, active.beltPosition);
        var pendingPosition = BeltHelper.getVectorForOffset(oldController, pending.beltPosition);
        oldInventory.addItem(pending);
        var neighbor = belt(h, layout.belt(previous.last()));
        var neighborData = neighbor.saveWithFullMetadata(registries);
        var states = new ArrayList<BlockState>();
        var entities = new ArrayList<BeltBlockEntity>();
        for (int u = tail.first(); u <= tail.last(); u++) {
            states.add(h.getLevel().getBlockState(layout.belt(u)));
            entities.add(belt(h, layout.belt(u)));
        }
        h.assertTrue(layout.repairReversed(h.getLevel(), tail) && layout.complete(h.getLevel(), tail), "The reversed tail becomes a complete westbound section");
        var controller = belt(h, layout.belt(tail.last()));
        var cargo = controller.getInventory().getTransportedItems();
        h.assertTrue(cargo.size() == 2 && ItemStack.matches(cargo.get(0).stack, named) && cargo.get(1).stack.is(Items.RAW_GOLD)
            && cargo.get(1).stack.getCount() == 17 && cargo.get(0).lockedExternally && cargo.get(0).insertedFrom == Direction.NORTH,
            "Both the active and pending shipments retain their components, quantities and processing state");
        h.assertTrue(BeltHelper.getVectorForOffset(controller, cargo.get(0).beltPosition).distanceToSqr(activePosition) < 1e-8
            && BeltHelper.getVectorForOffset(controller, cargo.get(1).beltPosition).distanceToSqr(pendingPosition) < 1e-8
            && cargo.get(0).prevBeltPosition == 3.875f && cargo.get(0).insertedAt == 3,
            "Create locates both shipments at their original world positions after reversal");
        h.assertTrue(oldInventory.getTransportedItems().isEmpty(), "The former controller keeps no duplicate cargo");
        for (int i = 0; i < 5; i++) {
            BlockPos pos = layout.belt(tail.first() + i);
            BlockState expectedState = states.get(i).setValue(BeltBlock.HORIZONTAL_FACING, Direction.WEST);
            if (i == 0) expectedState = expectedState.setValue(BeltBlock.PART, BeltPart.END);
            if (i == 4) expectedState = expectedState.setValue(BeltBlock.PART, BeltPart.START);
            h.assertTrue(belt(h, pos) == entities.get(i) && h.getLevel().getBlockState(pos).equals(expectedState), "The existing belt, pulley and casing stay in place");
        }
        h.assertTrue(!layout.repairReversed(h.getLevel(), tail) && neighborData.equals(neighbor.saveWithFullMetadata(registries)), "Repeated repair leaves the repaired and neighboring sections alone");
        for (int u = tail.first(); u <= tail.last(); u++) reload(h, layout.belt(u));
        controller = belt(h, layout.belt(tail.last()));
        cargo = controller.getInventory().getTransportedItems();
        h.assertTrue(layout.complete(h.getLevel(), tail) && !layout.repairReversed(h.getLevel(), tail)
            && cargo.size() == 2 && ItemStack.matches(cargo.get(0).stack, named) && cargo.get(1).stack.is(Items.RAW_GOLD)
            && cargo.get(1).stack.getCount() == 17 && cargo.get(0).lockedExternally
            && BeltHelper.getVectorForOffset(controller, cargo.get(0).beltPosition).distanceToSqr(activePosition) < 1e-8
            && BeltHelper.getVectorForOffset(controller, cargo.get(1).beltPosition).distanceToSqr(pendingPosition) < 1e-8,
            "Reload preserves both shipments and their positions without another repair");
        h.succeed();
    }

    @GameTest(template = "test_quarry_belt_repair")
    public static void aForeignBeltExtendingPastEitherEndIsPreserved(GameTestHelper h) {
        var layout = layout(h);
        var tail = layout.segments().getLast();
        buildBelt(h, layout.belt(tail.first()), layout.belt(tail.last() + 1));
        var controller = belt(h, layout.belt(tail.first()));
        controller.getInventory().addItem(new TransportedItemStack(new ItemStack(Items.DIAMOND, 3)));
        var registries = h.getLevel().registryAccess();
        var saved = controller.saveWithFullMetadata(registries);
        h.assertTrue(layout.hasItems(h.getLevel()) && !layout.repairReversed(h.getLevel(), tail), "A longer foreign belt cannot be adopted");
        h.assertTrue(saved.equals(controller.saveWithFullMetadata(registries)), "The foreign belt and pending diamonds are preserved");

        var other = new QuarryBeltLayout(layout.start().south(8), Direction.EAST, Direction.SOUTH, 64);
        buildBelt(h, other.belt(tail.first() - 1), other.belt(tail.last()));
        var outsideController = belt(h, other.belt(tail.first() - 1));
        saved = outsideController.saveWithFullMetadata(registries);
        h.assertTrue(!other.repairReversed(h.getLevel(), tail) && saved.equals(outsideController.saveWithFullMetadata(registries)), "A controller outside the selected tail is preserved");
        h.succeed();
    }

    @GameTest(template = "test_quarry_belt_repair", timeoutTicks = 400)
    public static void anExistingQuarryRepairsItsTailReloadsAndMinesTheNextBlock(GameTestHelper h) {
        h.setBlock(DRIVE, CEQuarry.QUARRY_DRIVE.get().defaultBlockState());
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
        quarry.size(64, 64);
        var layout = layout(h);
        var level = h.getLevel();
        for (int u = 0; u < 64; u++) for (int v : new int[] {0, 65}) {
            BlockPos pos = layout.start().east(u).south(v);
            level.setBlockAndUpdate(pos, CraneFrame.girder(level, pos, Direction.Axis.X));
            if (u == 0 || u == 63) level.setBlockAndUpdate(pos.below(), Blocks.BEDROCK.defaultBlockState());
        }
        for (int u = -1; u < 64; u++) level.setBlockAndUpdate(layout.chain(u), layout.chainState());
        var tail = layout.segments().getLast();
        for (var segment : layout.segments()) {
            boolean reverse = segment.equals(tail);
            buildBelt(h, layout.belt(reverse ? segment.first() : segment.last()), layout.belt(reverse ? segment.last() : segment.first()));
        }
        var shipment = new ItemStack(Items.AMETHYST_SHARD, 31);
        shipment.set(DataComponents.CUSTOM_NAME, Component.literal("Existing quarry shipment"));
        var pending = new TransportedItemStack(shipment);
        pending.beltPosition = pending.prevBeltPosition = 1.5f;
        pending.insertedAt = 1;
        var oldController = belt(h, layout.belt(tail.first()));
        var shipmentPosition = BeltHelper.getVectorForOffset(oldController, pending.beltPosition);
        oldController.getInventory().addItem(pending);
        // A separate belt immediately beyond the quarry belongs to a neighboring installation.
        buildBelt(h, layout.belt(68), layout.belt(64));
        BlockPos nextDrive = layout.belt(64).south();
        level.setBlockAndUpdate(nextDrive, CEQuarry.QUARRY_DRIVE.get().defaultBlockState());
        var neighbor = (QuarryDriveBlockEntity) level.getBlockEntity(nextDrive);
        neighbor.conveyor().collect(List.of(new ItemStack(Items.EMERALD, 9)));

        var registries = level.registryAccess();
        int layer = h.absolutePos(DRIVE).getY() - 3;
        var saved = quarry.saveWithoutMetadata(registries);
        saved.putInt("Layer", layer);
        saved.putInt("Cell", 1000);
        saved.putInt("NextLiquidRow", 144);
        saved.putDouble("U", 15);
        saved.putDouble("V", 24);
        saved.putDouble("Depth", 2);
        saved.putBoolean("Drill", true);
        saved.putBoolean("Belt", false);
        saved.putString("Status", "belt_blocked");
        quarry.loadWithComponents(saved, registries);
        BlockPos target = layout.start().east(15).south(24).atY(layer);
        level.setBlockAndUpdate(target, Blocks.POLISHED_GRANITE.defaultBlockState());
        h.setBlock(DRIVE.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                ((CreativeMotorBlockEntity) h.getBlockEntity(DRIVE.south())).generatedSpeed.setValue(256);
                h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> h.assertTrue(layout.complete(level, tail), "The normal server tick repairs the reversed five-block tail"))
            .thenExecute(() -> {
                h.assertTrue(quarry.saveWithoutMetadata(registries).getInt("Cell") == 1000, "Repair preserves the next cell");
                h.setBlock(DRIVE.above(), Blocks.AIR);
                for (var segment : layout.segments()) for (int u = segment.first(); u <= segment.last(); u++) reload(h, layout.belt(u));
                reload(h, h.absolutePos(DRIVE));
                h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> {
                var restored = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(level.getBlockState(target).isAir() && restored.saveWithoutMetadata(registries).getInt("Cell") > 1000, "The reloaded quarry mines its original next block");
            })
            .thenIdle(45)
            .thenExecute(() -> {
                var restored = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(layout.complete(level) && !layout.repairReversed(level, tail) && restored.constructionObstruction() == null, "Repeated ticks and reload leave the repaired belt complete with no obstruction");
                int recovered = 0;
                boolean moved = false;
                for (var segment : layout.segments()) {
                    var controller = belt(h, layout.belt(segment.last()));
                    controller.getInventory().write(registries);
                    for (var item : controller.getInventory().getTransportedItems()) if (ItemStack.isSameItemSameComponents(item.stack, shipment)) {
                        recovered += item.stack.getCount();
                        moved |= BeltHelper.getVectorForOffset(controller, item.beltPosition).x < shipmentPosition.x - 1;
                    }
                }
                h.assertTrue(recovered == 31 && moved && belt(h, layout.belt(tail.last())).getSpeed() != 0,
                    "The preexisting cargo survives repair and reload, then moves west along the powered native belts");
                h.assertTrue(belt(h, layout.belt(68)).isController() && neighbor.conveyor().contents().stream().mapToInt(ItemStack::getCount).sum() == 9,
                    "The adjacent installation and its cargo remain intact");
                h.setBlock(DRIVE.above(), Blocks.AIR);
            })
            .thenSucceed();
    }

    private static void reload(GameTestHelper h, BlockPos pos) {
        var level = h.getLevel();
        BlockEntity original = level.getBlockEntity(pos);
        var saved = original.saveWithFullMetadata(level.registryAccess());
        BlockState state = original.getBlockState();
        original.onChunkUnloaded();
        level.removeBlockEntity(pos);
        BlockEntity restored = BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
        h.assertTrue(restored != null, "The existing block entity reloads");
        level.setBlockEntity(restored);
    }
}
