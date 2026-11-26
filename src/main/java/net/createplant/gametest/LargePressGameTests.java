package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.LargePressFootprint;
import net.createplant.block.entity.LargeMechanicalPressBlockEntity;
import net.createplant.registry.CELargePress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class LargePressGameTests {
    private static final BlockPos DEPOT = new BlockPos(5, 2, 5);
    private static final BlockPos PRESS = DEPOT.above(2);

    @GameTest(template = "test_wet_processing", timeoutTicks = 250)
    public static void largePressStampsAnIronBlockFromACreateFunnel(GameTestHelper h) {
        h.setBlock(DEPOT.below(), Blocks.STONE);
        h.assertTrue(place(h, DEPOT.below(), new ItemStack(CELargePress.DEPOT.get())), "The large depot can be placed with its item");
        h.assertTrue(place(h, DEPOT.west().north(), new ItemStack(CELargePress.PRESS.get())), "A corner click places the press over the middle");
        var level = h.getLevel();
        h.assertTrue(LargePressFootprint.complete(level, h.absolutePos(DEPOT), true)
            && LargePressFootprint.complete(level, h.absolutePos(PRESS), false), "Both footprints occupy nine matching positions");
        var press = (LargeMechanicalPressBlockEntity) h.getBlockEntity(PRESS);
        var depot = (DepotBlockEntity) h.getBlockEntity(DEPOT);
        var outlet = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(DEPOT.east()), Direction.EAST);
        h.assertTrue(outlet != null && level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(DEPOT.east()), Direction.WEST) == null,
            "The deck exposes the inventory to the exterior only");
        var inlet = DEPOT.west(2);
        h.setBlock(inlet, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.WEST)
            .setValue(FunnelBlock.EXTRACTING, false));
        h.setBlock(PRESS.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        ((CreativeMotorBlockEntity) h.getBlockEntity(PRESS.above())).generatedSpeed.setValue(64);
        h.setBlock(DEPOT.above().east(), Blocks.STONE);
        h.runAfterDelay(10, () -> {
            var iron = new ItemStack(Items.IRON_BLOCK, 2);
            h.assertTrue(AbstractFunnelBlock.tryInsert(level, h.absolutePos(inlet), iron, true).isEmpty() && depot.getHeldItem().isEmpty(),
                "Funnel simulation does not change the depot");
            h.assertTrue(AbstractFunnelBlock.tryInsert(level, h.absolutePos(inlet), iron, false).isEmpty(), "The Create funnel feeds the large depot from an edge");
        });
        h.runAfterDelay(55, () -> {
            h.assertTrue(press.getSpeed() != 0 && !press.pressing().running && depot.getHeldItem().getCount() == 2,
                "A blocked corner stops the stroke without consuming the stock");
            h.setBlock(DEPOT.above().east(), Blocks.AIR);
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(depot.getHeldItem().is(Items.IRON_BLOCK) && depot.getHeldItem().getCount() == 1,
                "A stroke consumes exactly one of two iron blocks; the next waits for pickup");
            int outputs = 0;
            for (int slot = 0; slot < outlet.getSlots(); slot++) {
                if (outlet.getStackInSlot(slot).is(CELargePress.LARGE_IRON_PLATE.get())) outputs += outlet.extractItem(slot, 64, false).getCount();
            }
            h.assertTrue(outputs == 1, "First plate can be removed from the exterior");
        });
        h.runAfterDelay(180, () -> {
            h.assertTrue(outlet.getStackInSlot(0).is(CELargePress.LARGE_IRON_PLATE.get()) && outlet.getStackInSlot(0).getCount() == 1,
                "The remaining block makes exactly one plate after the first is exported");
            var saved = BlockEntity.loadStatic(h.absolutePos(DEPOT), depot.getBlockState(), depot.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            h.assertTrue(saved instanceof DepotBlockEntity restored && restored.getHeldItem().is(CELargePress.LARGE_IRON_PLATE.get())
                && restored.getHeldItem().getCount() == 1, "The depot serializes its finished piece");
            var funnel = DEPOT.east(2);
            h.setBlock(funnel, AllBlocks.BRASS_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.EAST)
                .setValue(FunnelBlock.EXTRACTING, true));
            BlockEntityBehaviour.get(level, h.absolutePos(funnel), FilteringBehaviour.TYPE).setFilter(CELargePress.LARGE_IRON_PLATE.toStack());
        });
        h.runAfterDelay(220, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(DEPOT.east(2))).inflate(3));
            h.assertTrue(depot.getHeldItem().isEmpty()
                && drops.stream().filter(e -> e.getItem().is(CELargePress.LARGE_IRON_PLATE.get())).mapToInt(e -> e.getItem().getCount()).sum() == 1,
                "A filtered, extracting Create funnel exports the finished plate from the rim");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing")
    public static void blockedPlacementAndBreakingTheRimPreserveItems(GameTestHelper h) {
        h.setBlock(DEPOT.below(), Blocks.STONE);
        var stack = new ItemStack(CELargePress.DEPOT.get());
        var corner = DEPOT.west().north();
        h.setBlock(corner, Blocks.STONE);
        h.assertTrue(!place(h, DEPOT.below(), stack) && stack.getCount() == 1 && h.getBlockState(DEPOT).isAir(),
            "A blocked corner cancels placement and keeps the item");
        h.setBlock(corner, Blocks.AIR);
        h.assertTrue(place(h, DEPOT.below(), stack) && stack.isEmpty(), "A whole clear square uses one kit");
        var level = h.getLevel();
        var port = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(corner), Direction.NORTH);
        h.assertTrue(port != null && port.insertItem(0, new ItemStack(Items.IRON_BLOCK, 2), false).isEmpty(), "The standalone deck holds a stack");
        level.destroyBlock(h.absolutePos(corner), true);
        h.assertTrue(h.getBlockState(DEPOT).isAir(), "Breaking a frame piece also removes the centre");
        for (var edge : LargePressFootprint.Edge.values()) h.assertTrue(h.getBlockState(edge.at(DEPOT)).isAir(), "A broken deck leaves no loose frame");
        h.assertTrue(port.extractItem(0, 64, false).isEmpty(), "A cached peripheral handler closes after disassembly");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(DEPOT)).inflate(2));
        h.assertTrue(drops.stream().filter(e -> e.getItem().is(CELargePress.DEPOT.asItem())).mapToInt(e -> e.getItem().getCount()).sum() == 1,
            "Disassembly drops the depot kit once");
        h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.IRON_BLOCK)).mapToInt(e -> e.getItem().getCount()).sum() == 2,
            "Disassembly returns the stock once");
        h.succeed();
    }

    private static boolean place(GameTestHelper h, BlockPos on, ItemStack stack) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var at = h.absolutePos(on);
        var hit = new BlockHitResult(at.getCenter().add(0, .5, 0), Direction.UP, at, false);
        return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction();
    }
}
