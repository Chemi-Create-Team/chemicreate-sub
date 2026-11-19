package net.createplant.gametest;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import java.util.LinkedHashMap;
import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.fusion.FusionStructure;
import net.createplant.inventory.FacilityConstructorMenu;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorCoreVariants;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CEAssembly;
import net.createplant.registry.CECrane;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CEPlantSystems;
import net.createplant.registry.CEProcessFluids;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class AssemblyGameTests {
    private static FacilityConstructorBlockEntity constructor(GameTestHelper h, AssemblyPlan.Kind kind, BlockPos at, Direction facing) {
        h.setBlock(at, CEAssembly.CONSTRUCTOR.get().defaultBlockState().setValue(FacilityConstructorBlock.FACING, facing));
        var be = (FacilityConstructorBlockEntity) h.getBlockEntity(at);
        h.assertTrue(be.configure(kind.ordinal(), 0), "Facility selection is accepted while stopped");
        return be;
    }

    private static void build(GameTestHelper h, FacilityConstructorBlockEntity be) {
        for (var target : be.plan()) {
            h.getLevel().setBlock(target.pos(), target.state(), Block.UPDATE_CLIENTS);
            if (target.blockEntityData() != null)
                h.getLevel().getBlockEntity(target.pos()).loadWithComponents(target.blockEntityData().copy(), h.getLevel().registryAccess());
        }
    }

    private static CraneDriveBlockEntity formedCrane(GameTestHelper h, FacilityConstructorBlockEntity be) {
        boolean fusion = be.kind() == AssemblyPlan.Kind.FUSION;
        var block = fusion ? CECrane.FUSION_DRIVE.get() : CECrane.DRIVE.get();
        var drive = be.plan().stream().filter(t -> t.state().is(block)).findFirst().orElseThrow();
        h.assertTrue(h.getLevel().getBlockEntity(drive.pos()) instanceof CraneDriveBlockEntity, "The expected service drive was placed");
        var crane = (CraneDriveBlockEntity) h.getLevel().getBlockEntity(drive.pos());
        crane.tick();
        var frame = crane.structure();
        h.assertTrue(frame != null && frame.along() == be.facing() && frame.legs() > 0
            && frame.length() == be.dimensions().depth() - 1 && frame.span() == be.dimensions().width() - 1,
            "Configured metal-girder frame forms on the supplied supports: " + crane.status());
        return crane;
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void selectedPlansFormTheirFacilitiesInFrontOfTheConstructor(GameTestHelper h) {
        var fusion = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(5, 2, 5), Direction.EAST);
        build(h, fusion);
        var controller = fusion.plan().stream().filter(t -> t.state().is(CEFusion.CONTROLLER.get())).findFirst().orElseThrow();
        h.assertTrue(FusionStructure.scan(h.getLevel(), controller.pos(), Direction.EAST).formed(), "Fusion plan forms to the east");
        formedCrane(h, fusion);
        var rbmk = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(30, 2, 30), Direction.NORTH);
        build(h, rbmk);
        BlockPos channel = rbmk.plan().stream().filter(t -> t.state().is(CENuclear.FUEL_CHANNEL.get())).findFirst().orElseThrow().pos();
        var core = ReactorCoreStructure.scan(h.getLevel(), channel);
        h.assertTrue(core.formed() && core.circular() && core.width() == 5, "Selected 5 m RBMK forms: " + core.reason());
        formedCrane(h, rbmk);
        var tower = constructor(h, AssemblyPlan.Kind.COOLING_TOWER, new BlockPos(65, 2, 10), Direction.WEST);
        build(h, tower);
        var at = tower.plan().stream().filter(t -> t.state().is(CEPlantSystems.COOLING_TOWER.get())).findFirst().orElseThrow().pos();
        var towerEntity = (CoolingTowerBlockEntity) h.getLevel().getBlockEntity(at);
        for (int i = 0; i < 12 && !towerEntity.formed(); i++) towerEntity.validateSlice();
        h.assertTrue(towerEntity.formed() && towerEntity.centre().getX() < tower.getBlockPos().getX(), "Cooling tower forms west of constructor");
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void settingsValidateRotateSaveAndRejectChangesDuringConstruction(GameTestHelper h) {
        var be = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(4, 2, 4), Direction.SOUTH);
        for (var direction : Direction.Plane.HORIZONTAL) {
            h.getLevel().setBlock(be.getBlockPos(), be.getBlockState().setValue(FacilityConstructorBlock.FACING, direction), 2);
            be.planChanged();
            for (int i = 0; i < ReactorCoreVariants.RECOMMENDED_DIAMETERS.size(); i++) {
                h.assertTrue(be.configure(0, i), "All recommended core sizes can be selected");
                int diameter = ReactorCoreVariants.outerDiameter(be.coreSize());
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                for (var target : be.plan()) {
                    var relative = target.pos().subtract(be.getBlockPos());
                    h.assertTrue(relative.getX() * direction.getStepX() + relative.getZ() * direction.getStepZ() >= 3, "Every block lies ahead of the constructor");
                    int across = direction.getAxis() == Direction.Axis.X ? target.pos().getZ() : target.pos().getX();
                    min = Math.min(min, across);max = Math.max(max, across);
                }
                h.assertTrue(max - min + 1 == diameter + 2, "Crane supports stand just outside the selected shield diameter");
            }
        }
        h.assertTrue(!be.configure(-1, 0) && !be.configure(AssemblyPlan.Kind.values().length, 0) && !be.configure(0, 99), "Unknown selections are rejected");
        be.configure(1, 2);
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.configure(0, 0);be.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(be.kind() == AssemblyPlan.Kind.FUSION && be.sizeIndex() == 2, "Selection and remembered diameter survive reload");
        be.state = SchematicannonBlockEntity.State.RUNNING;
        h.assertTrue(!be.configure(2, 0), "Running construction locks settings");
        be.state = SchematicannonBlockEntity.State.PAUSED;
        h.assertTrue(!be.configure(2, 0), "Paused construction must be stopped before reconfiguration");
        h.succeed();
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
        try (var stream = AssemblyGameTests.class.getResourceAsStream("/assets/create_refinery_n_reactors/ponder/plant/facility_constructor.nbt")) {
            h.assertTrue(stream != null, "Facility Ponder ships its structure");
            PlantTutorialGameTests.validateStructure(h, stream);
        }
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void dimensionsHaveIndependentSavedSelectionsAndValidatedButtons(GameTestHelper h) {
        var be = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(5, 2, 5), Direction.SOUTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);player.setPos(be.getBlockPos().getCenter());
        var menu = new FacilityConstructorMenu(1, player.getInventory(), be);
        h.assertTrue(menu.clickMenuButton(player, 14) && menu.clickMenuButton(player, 20), "RBMK diameter and height are independent menu settings");
        h.assertTrue(be.coreSize() == 13 && be.coreHeight() == 3 && be.totalHeight() == 11 + CraneDriveBlockEntity.SERVICE_MAST_LENGTH,
            "Compact layout adds the service crane above the reduced reactor structure");
        String compact = net.createplant.assembly.AssemblyPlan.fileName(be.kind(), be.facing(), be.selectedSize(), be.coreHeight());
        menu.clickMenuButton(player, 21);
        h.assertTrue(!compact.equals(net.createplant.assembly.AssemblyPlan.fileName(be.kind(), be.facing(), be.selectedSize(), be.coreHeight())), "Different heights cannot share stale schematic files");
        menu.clickMenuButton(player, 20);menu.clickMenuButton(player, 1);menu.clickMenuButton(player, 12);
        h.assertTrue(be.selectedSize() == 19 && !menu.clickMenuButton(player, 13) && !menu.clickMenuButton(player, 20), "Fusion sizes reject RBMK-only height and invalid index packets");
        menu.clickMenuButton(player, 2);menu.clickMenuButton(player, 12);
        h.assertTrue(be.selectedSize() == 33 && be.totalHeight() == 48, "Tower selects its actual width and height");
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.configure(0, 0);be.configureHeight(1);be.configure(1, 0);be.configure(2, 0);
        be.loadWithComponents(saved, h.getLevel().registryAccess());
        menu.clickMenuButton(player, 0);
        h.assertTrue(be.coreSize() == 13 && be.coreHeight() == 3, "RBMK dimensions survive switching and NBT reload");
        menu.clickMenuButton(player, 1);
        h.assertTrue(be.selectedSize() == 19, "Fusion size survives switching and NBT reload");
        menu.clickMenuButton(player, 2);
        h.assertTrue(be.selectedSize() == 33, "Tower size survives switching and NBT reload");
        be.state = SchematicannonBlockEntity.State.RUNNING;
        h.assertTrue(!menu.clickMenuButton(player, 10) && !menu.clickMenuButton(player, 0), "Running construction locks every dimension");
        be.state = SchematicannonBlockEntity.State.STOPPED;
        var old = saved.copy();old.remove("FusionSize");old.remove("TowerSize");old.remove("CoreHeight");
        be.loadWithComponents(old, h.getLevel().registryAccess());
        h.assertTrue(be.totalHeight() == 36 && be.coreHeight() == 7 && be.sizeIndex(AssemblyPlan.Kind.FUSION) == 0, "Existing saved constructors retain the original default dimensions");
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void bothRbmkHeightsBuildMatchingCoresCapsAndDrives(GameTestHelper h) {
        int n = 0;
        for (int height = 0; height < 2; height++) for (int size : new int[]{0, 4}) {
            var be = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(15 + n++ * 40, 2, 40), Direction.SOUTH);
            be.configure(0, size);be.configureHeight(height);build(h, be);
            var channel = be.plan().stream().filter(t -> t.state().is(CENuclear.FUEL_CHANNEL.get())).findFirst().orElseThrow().pos();
            var result = ReactorCoreStructure.scan(h.getLevel(), channel);
            h.assertTrue(result.formed() && result.height() == be.coreHeight() && result.width() == be.coreSize(), "Selected RBMK core forms with both dimensions: " + result.reason());
            var layout = ReactorPhysics.layout(be.coreHeight());
            var cap = (RodCapBlockEntity) h.getLevel().getBlockEntity(channel.above(layout.fuelAssemblyLength()));
            h.assertTrue(cap != null && cap.column() != null, "Operating cap reaches the actual selected-height fuel column");
            var crane = formedCrane(h, be);
            h.assertTrue(crane.getBlockPos().getY() - cap.getBlockPos().getY() == CraneDriveBlockEntity.SERVICE_MAST_LENGTH,
                "The crane's fixed mast reaches both RBMK cap elevations");
            var drive = be.plan().stream().filter(t -> t.state().is(CENuclear.SHIELD_DRIVE.get())).findFirst().orElseThrow();
            h.assertTrue(drive.pos().getY() >= channel.getY() && drive.pos().getY() < channel.getY() + be.coreHeight(), "Drive remains within the selected active height");
            int maxY = be.plan().stream().mapToInt(t -> t.pos().getY()).max().orElseThrow();
            h.assertTrue(maxY - be.getBlockPos().getY() + 1 == be.totalHeight(), "Preview and actual overall height agree");
        }
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void fusionSizesFormInEveryDirectionAndExposeTheirRealPorts(GameTestHelper h) {
        int n = 0;
        for (int size = 0; size < 3; size++) for (var direction : Direction.Plane.HORIZONTAL) {
            var be = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(20 + n++ * 35, 2, 100), direction);
            be.configure(1, size);build(h, be);
            var control = be.plan().stream().filter(t -> t.state().is(CEFusion.CONTROLLER.get())).findFirst().orElseThrow().pos();
            var controller = (FusionReactorControllerBlockEntity) h.getLevel().getBlockEntity(control);
            controller.tick();
            h.assertTrue(controller.formed() && controller.structureWidth() == be.selectedSize(), "Controller detects the selected fusion width in every direction");
            var frame = formedCrane(h, be).structure();
            var tops = FusionStructure.serviceTops(control, direction, be.selectedSize());
            h.assertTrue(tops.stream().anyMatch(top -> {
                BlockPos offset = top.subtract(frame.start());
                int u = offset.getX() * frame.along().getStepX() + offset.getZ() * frame.along().getStepZ();
                int v = offset.getX() * frame.across().getStepX() + offset.getZ() * frame.across().getStepZ();
                int depth = -offset.getY() - 1;
                return u >= 0 && u < frame.length() && v > 0 && v < frame.span() && depth >= 1 && depth <= CraneDriveBlockEntity.SERVICE_MAST_DEPTH;
            }), "The fusion crane can reach a blanket service position");
            var centre = controller.centre();
            var cells = FusionStructure.cells(control, direction, be.selectedSize());
            var map = new java.util.HashMap<BlockPos, FusionStructure.Cell>();for (var cell : cells) map.put(cell.pos(), cell);
            for (var plasma : FusionStructure.plasmaChannel(be.selectedSize())) {
                var at = centre.offset(plasma);
                h.assertTrue(map.get(at).role() == FusionStructure.Role.CLEAR && h.getLevel().isEmptyBlock(at), "Scaled plasma channel stays clear");
                for (var normal : Direction.values()) {
                    var neighbour = at.relative(normal);
                    if (FusionStructure.plasmaChannel(be.selectedSize()).contains(neighbour.subtract(centre))) continue;
                    var wall = map.get(neighbour);
                    h.assertTrue(wall != null && wall.role() != FusionStructure.Role.CLEAR, "Scaled plasma channel remains enclosed");
                }
            }
            for (var cell : cells) if (cell.expected().getBlock() instanceof FusionFluidPortBlock port) {
                h.assertTrue(controller.owns(cell.pos()) && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    cell.pos(), cell.expected().getValue(FusionFluidPortBlock.FACING)) != null, "Selected-size ports connect to the detected controller");
            }
            h.assertTrue(FusionStructure.serviceTops(control, direction, be.selectedSize()).stream().allMatch(at -> h.getLevel().isEmptyBlock(at.above())), "Crane service locations follow the selected torus");
            var saved = controller.saveWithoutMetadata(h.getLevel().registryAccess());controller.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(controller.structureWidth() == be.selectedSize(), "Detected fusion width survives reload");
        }
        h.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
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
            h.assertTrue(hot.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(), 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "Sized tower accepts warm water");
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

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void cannonBuildsBothReactorsAndTheirCranesUsingFiniteMaterials(GameTestHelper h) {
        var be = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(20, 240, 4), Direction.SOUTH);
        be.configure(1, 2);
        print(h, be);
        var controller = be.plan().stream().filter(t -> t.state().is(CEFusion.CONTROLLER.get())).findFirst().orElseThrow();
        var result = FusionStructure.scan(h.getLevel(), controller.pos(), Direction.SOUTH);
        h.assertTrue(result.formed(), "Actual projectile placements form the fusion reactor: " + result.reason() + " at " + result.fault());
        formedCrane(h, be);

        var rbmk = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(60, 240, 4), Direction.EAST);
        rbmk.configureHeight(0);
        print(h, rbmk);
        var channel = rbmk.plan().stream().filter(t -> t.state().is(CENuclear.FUEL_CHANNEL.get())).findFirst().orElseThrow();
        h.assertTrue(ReactorCoreStructure.scan(h.getLevel(), channel.pos()).formed(), "Actual projectile placements form the RBMK");
        formedCrane(h, rbmk);
        h.succeed();
    }

    private static void print(GameTestHelper h, FacilityConstructorBlockEntity be) {
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
