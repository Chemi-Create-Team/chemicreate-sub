package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import java.util.LinkedHashMap;
import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.inventory.FacilityConstructorMenu;
import net.createplant.registry.CEAssembly;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class AssemblyGameTests {
    public static FacilityConstructorBlockEntity constructor(GameTestHelper h, AssemblyPlan.Kind kind, BlockPos at, Direction facing) {
        h.setBlock(at, CEAssembly.CONSTRUCTOR.get().defaultBlockState().setValue(FacilityConstructorBlock.FACING, facing));
        var be = (FacilityConstructorBlockEntity) h.getBlockEntity(at);
        h.assertTrue(be.configure(kind.ordinal(), 0), "Facility selection is accepted while stopped");
        return be;
    }

    public static void build(GameTestHelper h, FacilityConstructorBlockEntity be) {
        for (var target : be.plan()) {
            h.getLevel().setBlock(target.pos(), target.state(), Block.UPDATE_CLIENTS);
            if (target.blockEntityData() != null)
                h.getLevel().getBlockEntity(target.pos()).loadWithComponents(target.blockEntityData().copy(), h.getLevel().registryAccess());
        }
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void internalBlueprintCannotBeTakenAndSuppliesUseStandardSlots(GameTestHelper h) {
        var be = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(5, 2, 5), Direction.SOUTH);
        be.tick();
        h.assertTrue(be.inventory.getStackInSlot(0).has(AllDataComponents.SCHEMATIC_ANCHOR), "Internal plan is anchored");
        h.assertTrue(be.state == SchematicannonBlockEntity.State.STOPPED && be.canConfigure() && be.flyingBlocks.isEmpty(), "An unfuelled constructor stays configurable and does not start itself");
        h.assertTrue(be.supplies().getSlots() == 3, "Only checklist input/output and fuel are exposed");
        h.assertTrue(be.supplies().insertItem(2, new ItemStack(Items.GUNPOWDER, 4), false).isEmpty(), "Standard item handler accepts gunpowder");
        be.tick();
        h.assertTrue(be.state == SchematicannonBlockEntity.State.STOPPED && be.inventory.getStackInSlot(4).getCount() == 4, "Viewing a plan does not consume fuel or begin construction");
        h.assertTrue(!be.supplies().insertItem(0, be.inventory.getStackInSlot(0).copy(), false).isEmpty(), "Blueprint cannot enter public slots");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(be.getBlockPos().getCenter());
        var menu = new FacilityConstructorMenu(1, player.getInventory(), be);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().isEmpty() && menu.quickMoveStack(player, 0).isEmpty(), "Hidden blueprint cannot be picked up or shift-clicked");
        h.assertTrue(menu.clickMenuButton(player, 2) && be.kind() == AssemblyPlan.Kind.COOLING_TOWER, "Menu changes facility on the server");
        h.assertTrue(!menu.clickMenuButton(player, 99), "Unknown menu buttons do nothing");
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void stoppedConstructorPrintsChecklistsTurnsAndHasAValidPonder(GameTestHelper h) throws Exception {
        var be = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(5, 2, 5), Direction.SOUTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(be.getBlockPos().getCenter());
        var hit = new net.minecraft.world.phys.BlockHitResult(be.getBlockPos().getCenter(), Direction.UP, be.getBlockPos(), false);
        var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        CEAssembly.CONSTRUCTOR.get().onWrenched(be.getBlockState(), context);
        h.assertTrue(be.facing() == Direction.WEST, "Wrench turns the plan by exactly 90 degrees");
        be.inventory.setStackInSlot(2, new ItemStack(Items.BOOK));
        for (int i = 0; i < 25; i++) be.tick();
        h.assertTrue(be.state == SchematicannonBlockEntity.State.STOPPED && be.inventory.getStackInSlot(2).isEmpty()
            && be.inventory.getStackInSlot(3).is(Items.WRITTEN_BOOK), "Checklist printing works while stopped without starting construction");
        h.assertTrue(be.supplies().extractItem(1, 1, false).is(Items.WRITTEN_BOOK), "Printed material lists can leave through the standard item handler");
        try (var stream = net.createplant.ResourceStack.open(AssemblyGameTests.class, "/assets/create_refinery_n_reactors/ponder/plant/facility_constructor.nbt")) {
            h.assertTrue(stream != null, "Facility Ponder ships its structure");
            PlantTutorialGameTests.validateStructure(h, stream);
        }
        h.succeed();
    }

    // The three towers stand beyond the template, so they run in their own batch where they cannot reach another test's area.
    @GameTest(template = "test_fusion", batch = "assembly_cooling_towers", timeoutTicks = 100)
    public static void allTowerSizesDetectConnectCoolAndRevalidateAfterReload(GameTestHelper h) {
        for (int size = 0; size < 3; size++) {
            var be = constructor(h, AssemblyPlan.Kind.COOLING_TOWER, new BlockPos(40 + size * 70, 2, 160), Direction.WEST);
            be.configure(2, size);build(h, be);
            var control = be.plan().stream().filter(t -> t.state().is(CEPlantSystems.COOLING_TOWER.get())).findFirst().orElseThrow().pos();
            var tower = (CoolingTowerBlockEntity) h.getLevel().getBlockEntity(control);
            for (int i = 0; i < 100 && !tower.formed(); i++) tower.validateSlice();
            h.assertTrue(tower.formed() && tower.sizeIndex() == size && tower.size().width() == be.selectedSize(), "Tower detects its actual construction dimensions");
            IFluidHandler hot = null, cold = null;
            for (var cell : CoolingTowerStructure.cells(size)) {
                var at = tower.centre().offset(cell.offset());
                if (cell.role() == CoolingTowerStructure.Role.HOT) hot = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.NORTH);
                if (cell.role() == CoolingTowerStructure.Role.COLD) cold = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.SOUTH);
            }
            h.assertTrue(hot != null && cold != null, "Each size exposes real Create-compatible hot and cold ports");
            h.assertTrue(hot.fill(FluidForm.WARM_WATER.stack(1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "Sized tower accepts warm water");
            tower.process();
            var water = cold.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(water.getAmount() == 1000 && water.is(Fluids.WATER), "Sized tower cools without losing water");
            var saved = tower.saveWithoutMetadata(h.getLevel().registryAccess());tower.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(!tower.formed() && tower.sizeIndex() == size, "Reload preserves size and requires a complete new audit");
            for (int i = 0; i < 100 && !tower.formed(); i++) tower.validateSlice();
            h.assertTrue(tower.formed(), "All tower sizes re-form after reload");
            var shell = CoolingTowerStructure.cells(size).stream().filter(c -> c.role() == CoolingTowerStructure.Role.SHELL && c.offset().getY() == tower.size().height() - 1).findFirst().orElseThrow();
            h.getLevel().setBlock(tower.centre().offset(shell.offset()), Blocks.AIR.defaultBlockState(), 2);tower.structureChanged();
            h.assertTrue(!tower.formed() && cold.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Removing a selected-size rim stops output");
        }
        h.succeed();
    }

    public static void print(GameTestHelper h, FacilityConstructorBlockEntity be) {
        h.assertTrue(be.plan().stream().allMatch(t -> h.getLevel().isEmptyBlock(t.pos())), "The complete cannon fixture starts in clear space above the other parallel test structures");
        BlockPos chestPos = be.getBlockPos().west();
        h.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(chestPos);
        BlockPos extraPos = be.getBlockPos().north();
        h.getLevel().setBlockAndUpdate(extraPos, Blocks.CHEST.defaultBlockState());
        var extra = (ChestBlockEntity) h.getLevel().getBlockEntity(extraPos);
        var required = new LinkedHashMap<Item, Integer>();
        for (var target : be.plan()) {
            h.getLevel().getChunkAt(target.pos());
            if (!target.state().isAir()) required.merge(target.state().getBlock().asItem(), 1, Integer::sum);
        }
        int slot = 0;
        for (var entry : required.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                h.assertTrue(slot < chest.getContainerSize() + extra.getContainerSize(), "Fixture chests hold finite building materials");
                int count = Math.min(remaining, entry.getKey().getDefaultMaxStackSize());
                if (slot < chest.getContainerSize()) chest.setItem(slot, new ItemStack(entry.getKey(), count));
                else extra.setItem(slot - chest.getContainerSize(), new ItemStack(entry.getKey(), count));
                slot++;remaining -= count;
            }
        }
        be.inventory.setStackInSlot(4, new ItemStack(Items.GUNPOWDER, 64));
        be.findInventories();
        be.state = SchematicannonBlockEntity.State.RUNNING;
        // Advance the actual cannon ticks synchronously, including flying blocks, without changing global server configuration.
        int ticks = 0;
        while (ticks++ < 30_000 && (!be.statusMsg.equals("finished") || !be.flyingBlocks.isEmpty())) be.tick();
        h.assertTrue(be.statusMsg.equals("finished") && be.flyingBlocks.isEmpty(), "Cannon finishes the complete plan: " + be.statusMsg + " after " + ticks + " ticks");
        h.assertTrue(be.inventory.getStackInSlot(1).isEmpty(), "No free empty schematic is produced");
        h.assertTrue(be.inventory.getStackInSlot(4).getCount() < 64, "Construction consumes real gunpowder");
        for (int i = 0; i < chest.getContainerSize(); i++) h.assertTrue(chest.getItem(i).isEmpty(), "Exactly the supplied building materials were consumed");
        for (int i = 0; i < extra.getContainerSize(); i++) h.assertTrue(extra.getItem(i).isEmpty(), "The extra chest supplied the remaining building materials");
    }
}
