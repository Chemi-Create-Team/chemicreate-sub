package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.createplant.CreatePlant;
import net.createplant.block.entity.CubicPressBlockEntity;
import net.createplant.recipe.CubicPressRecipe;
import net.createplant.registry.CEHighStressMachines;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class CubicPressGameTests {
    private static final BlockPos BASE = new BlockPos(3, 3, 3);
    private static CubicPressBlockEntity build(GameTestHelper h) {
        h.setBlock(BASE, CEHighStressMachines.PRESS.get());heat(h, BlazeBurnerBlock.HeatLevel.SEETHING);
        var be = (CubicPressBlockEntity) h.getBlockEntity(BASE);be.setSpeed(64);return be;
    }
    private static void heat(GameTestHelper h, BlazeBurnerBlock.HeatLevel heat) {
        h.setBlock(BASE.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
        ((com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity) h.getBlockEntity(BASE.below())).isCreative = true;
    }
    private static ItemStack item(String name) { return new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location(name))); }
    private static void run(CubicPressBlockEntity be, int ticks) { for (int i = 0; i < ticks; i++) be.process(); }
    private static void insert(GameTestHelper h, CubicPressBlockEntity be, ItemStack catalyst) {
        h.assertTrue(be.port().insertItem(0, item("reactor_graphite"), false).isEmpty() && be.port().insertItem(1, catalyst, false).isEmpty(), "Funnel input accepts graphite and tagged catalyst");
    }
    @GameTest(template = "test_reactor")
    public static void heatSpeedReloadAndCatalystReservation(GameTestHelper h) {
        var be = build(h);insert(h, be, item("nickel_ingot"));run(be, 120);
        h.assertTrue(be.progress() == 120 && be.inventory.getStackInSlot(0).isEmpty(), "Payment happens once at the start: " + be.status() + " progress=" + be.progress() + " heat=" + be.superheated() + " rpm=" + be.getSpeed() + " inv=" + be.inventory.serializeNBT(h.getLevel().registryAccess()));
        heat(h, BlazeBurnerBlock.HeatLevel.KINDLED);run(be, 80);h.assertTrue(be.progress() == 120 && be.status().equals("heat"), "Kindled heat pauses without resetting progress");
        h.setBlock(BASE.below(), Blocks.MAGMA_BLOCK);
        run(be, 20);h.assertTrue(be.progress() == 120 && be.status().equals("heat"), "A passive hot block cannot supply superheated Blaze Burner heat");
        heat(h, BlazeBurnerBlock.HeatLevel.SEETHING);be.setSpeed(32);run(be, 80);h.assertTrue(be.progress() == 120 && be.status().equals("speed"), "Insufficient rotation also pauses");
        var lookup = h.getLevel().registryAccess();var saved = be.saveWithoutMetadata(lookup);
        be.loadWithComponents(saved, lookup);be.setSpeed(64);run(be, 280);
        h.assertTrue(be.inventory.getStackInSlot(2).is(Items.DIAMOND) && be.inventory.getStackInSlot(2).getCount() == 1, "Saved payment and rolled results complete exactly once");
        run(be, 500);h.assertTrue(be.inventory.getStackInSlot(2).getCount() == 1, "Reloading never creates a second unpaid product");
        h.assertTrue(be.calculateStressApplied() == 256, "Press draws 256 SU/RPM");h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void fullOutputAndNativeItemFacesAreTransactional(GameTestHelper h) {
        var be = build(h);var level = h.getLevel();var pos = be.getBlockPos();
        for (Direction side : Direction.values()) h.assertTrue((level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null) == (side != Direction.DOWN && (side == Direction.UP || side.getAxis() != be.getBlockState().getValue(net.createplant.block.CubicPressBlock.HORIZONTAL_AXIS))), "Only top and non-shaft horizontal faces expose inventory: " + side);
        var port = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        var before = be.saveWithoutMetadata(level.registryAccess());port.insertItem(0, item("reactor_graphite"), true);
        h.assertTrue(before.equals(be.saveWithoutMetadata(level.registryAccess())), "Simulating insertion does not change inventory");
        insert(h, be, new ItemStack(Items.IRON_INGOT));
        h.assertTrue(port.extractItem(0, 64, false).isEmpty() && port.extractItem(1, 64, false).isEmpty(), "Funnels cannot remove inputs");
        be.inventory.setStackInSlot(2, new ItemStack(Items.DIAMOND, 64));run(be, 700);
        h.assertTrue(be.inventory.getStackInSlot(0).getCount() == 1 && be.inventory.getStackInSlot(1).getCount() == 1 && be.progress() == 0, "A full output never charges materials");
        port.extractItem(2, 64, false);run(be, 600);h.assertTrue(port.extractItem(2, 64, false).is(Items.DIAMOND), "Iron fallback completes after 600 ticks and only output is extracted");
        h.assertTrue(ArmInteractionPoint.isInteractable(level, pos, be.getBlockState()), "The standard mechanical arm recognizes the press");
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void interruptedPaymentIsReturnedWhenBrokenAndRecipesRemainNative(GameTestHelper h) {
        var be = build(h);insert(h, be, item("nickel_ingot"));run(be, 20);
        h.setBlock(BASE, Blocks.AIR);
        var box = new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(2);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
        h.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 2 && drops.stream().noneMatch(e -> e.getItem().is(Items.DIAMOND)), "Breaking unfinished work returns its paid inputs exactly once: " + drops.stream().map(e -> e.getItem().toString()).toList());
        for (String id : new String[]{"synthetic_diamond", "synthetic_diamond_iron"}) {
            var recipe = h.getLevel().getRecipeManager().byKey(CreatePlant.location("cubic_pressing/" + id)).orElseThrow().value();
            h.assertTrue(recipe instanceof CubicPressRecipe && ((CubicPressRecipe) recipe).getProcessingDuration() == (id.endsWith("_iron") ? 600 : 400) && ((CubicPressRecipe) recipe).getRequiredHeat() == com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED, "Native processing recipes retain their superheat condition");
        }
        h.succeed();
    }
    @GameTest(template = "test_reactor", timeoutTicks = 120)
    public static void realFunnelInsertsMaterialsAndExtractsOnlyTheDiamond(GameTestHelper h) {
        var be = build(h);be.setSpeed(0);
        var funnelPos = BASE.north();
        var state = AllBlocks.ANDESITE_FUNNEL.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH)
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, false);
        h.setBlock(funnelPos, state);
        h.runAfterDelay(10, () -> {
            var absolute = h.absolutePos(funnelPos);
            for (var stack : new ItemStack[]{item("reactor_graphite"), item("nickel_ingot")}) {
                var entity = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), absolute.getX() + .5, absolute.getY() + .2, absolute.getZ() + .5, stack);
                h.getLevel().addFreshEntity(entity);
                state.entityInside(h.getLevel(), absolute, entity);
            }
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(be.inventory.getStackInSlot(0).getCount() == 1 && be.inventory.getStackInSlot(1).getCount() == 1, "The real funnel inserts into both filtered inputs");
            be.setSpeed(64);run(be, 400);be.setSpeed(0);
            h.assertTrue(be.inventory.getStackInSlot(2).is(Items.DIAMOND), "The paid batch completes before extraction: " + be.status());
            h.setBlock(funnelPos, state.setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, true));
        });
        h.runAfterDelay(100, () -> {
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(10));
            h.assertTrue(be.inventory.getStackInSlot(2).isEmpty() && drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND))
                && drops.stream().noneMatch(e -> e.getItem().is(item("nickel_ingot").getItem())), "The extracting funnel exports the diamond and retains any returned catalyst: inventory=" + be.inventory.serializeNBT(h.getLevel().registryAccess()) + " drops=" + drops.stream().map(e -> e.getItem().toString()).toList());h.succeed();
        });
    }

}
