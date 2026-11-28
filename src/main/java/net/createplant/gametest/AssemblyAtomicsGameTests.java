package net.createplant.gametest;

import net.createplant.nuclear.ReactorCoreVariants;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.inventory.FacilityConstructorMenu;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.createplant.gametest.AssemblyGameTests.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.util.HashMap;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CECrane;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CENuclear;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static net.createplant.gametest.SurvivalReachabilityGameTests.has;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class AssemblyAtomicsGameTests {
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

    @GameTest(template = "test_fusion", batch = "assembly_fusion_service", timeoutTicks = 1600)
    public static void cannonBuildsBothReactorsAndTheirCranesUsingFiniteMaterials(GameTestHelper h) {
        var be = constructor(h, AssemblyPlan.Kind.FUSION, new BlockPos(20, 240, 4), Direction.SOUTH);
        be.configure(1, 2);
        print(h, be);
        var controller = be.plan().stream().filter(t -> t.state().is(CEFusion.CONTROLLER.get())).findFirst().orElseThrow();
        var result = FusionStructure.scan(h.getLevel(), controller.pos(), Direction.SOUTH);
        h.assertTrue(result.formed(), "Actual projectile placements form the fusion reactor: " + result.reason() + " at " + result.fault());
        var fusionCrane = formedCrane(h, be);

        var rbmk = constructor(h, AssemblyPlan.Kind.RBMK, new BlockPos(60, 240, 4), Direction.EAST);
        rbmk.configureHeight(0);
        print(h, rbmk);
        var channel = rbmk.plan().stream().filter(t -> t.state().is(CENuclear.FUEL_CHANNEL.get())).findFirst().orElseThrow();
        h.assertTrue(ReactorCoreStructure.scan(h.getLevel(), channel.pos()).formed(), "Actual projectile placements form the RBMK");
        formedCrane(h, rbmk);
        checkFusionServiceAfterShutdown(h, (FusionReactorControllerBlockEntity) h.getLevel().getBlockEntity(controller.pos()), fusionCrane);
    }

    private static void checkFusionServiceAfterShutdown(GameTestHelper h, FusionReactorControllerBlockEntity reactor, CraneDriveBlockEntity crane) {
        var level = h.getLevel();
        var drive = crane.getBlockPos();
        var desk = new BlockPos(20, 240, 0);
        Direction facing = Direction.NORTH;
        ReactorConsoleStructure.DESK.place(h::setBlock, desk, facing);
        h.assertTrue(ReactorConsoleStructure.DESK.tryForm(level, h.absolutePos(desk)), "The assembled console can control the printed crane");
        var player = ReactorConsoleGameTests.linkCrane(h, drive, h.absolutePos(desk));

        reactor.tick();
        var wornBlanket = CEFusion.LITHIUM_LEAD_BLANKET.toStack();
        wornBlanket.setDamageValue(12000);
        var wornDivertor = CEFusion.DIVERTOR_CASSETTE.toStack();
        wornDivertor.setDamageValue(8000);
        h.assertTrue(reactor.blanketPort().insertItem(0, wornBlanket, false).isEmpty()
            && reactor.blanketPort().insertItem(1, wornDivertor, false).isEmpty(), "Seed both worn parts from a previous run");
        var hot = reactor.saveWithoutMetadata(level.registryAccess());
        hot.putBoolean("MagnetCommand", true);hot.putBoolean("PlasmaCommand", true);
        hot.putDouble("CoilKelvin", FusionPhysics.LIQUID_HELIUM_KELVIN);
        hot.putDouble("MagnetJoules", FusionPhysics.MAGNET_STORED_JOULES * .03);
        hot.putDouble("BlanketKelvin", FusionPhysics.COOLANT_INLET_KELVIN + 5);
        reactor.loadWithComponents(hot, level.registryAccess());
        reactor.toggle(FusionReactorControllerBlockEntity.Command.PLASMA, player);
        reactor.toggle(FusionReactorControllerBlockEntity.Command.MAGNETS, player);
        h.assertTrue(!reactor.maintenanceReady(), "Turning both controls off does not skip the maintenance checks");

        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, crane.getBlockPos(), Direction.UP);
        var damagedCartridge = CEFusion.IRRADIATION_CARTRIDGE.toStack();
        damagedCartridge.setDamageValue(1);
        h.assertTrue(items.insertItem(0, damagedCartridge, false).isEmpty()
            && items.insertItem(1, CEFusion.LITHIUM_LEAD_BLANKET.toStack(), false).isEmpty()
            && items.insertItem(2, CEFusion.DIVERTOR_CASSETTE.toStack(), false).isEmpty(), "Supply parts through the crane's item capability");
        for (int slot = 3; slot < items.getSlots(); slot++)
            h.assertTrue(items.insertItem(slot, AllBlocks.METAL_GIRDER.asStack(64), false).isEmpty(), "Fill every remaining store slot");
        level.setBlockAndUpdate(drive.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        h.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) level.getBlockEntity(drive.north())).generatedSpeed.setValue(128));
        startAutomatic(h, desk, facing);

        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(crane.status().equals("fusion_unsafe"), "A hot, charged tokamak makes the crane wait"))
            .thenWaitUntil(() -> h.assertTrue(crane.working() && crane.status().equals("fusion_service") && !reactor.emergency(),
                "A normal shutdown reaches maintenance temperature and charge: " + reactor.blanketKelvin() + " K, " + reactor.magnetCharge()))
            .thenExecute(() -> ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE))
            .thenIdle(25)
            .thenExecute(() -> {
                h.assertTrue(crane.status().equals("console_paused") && reactor.componentRemaining(0) == 12000 && reactor.componentRemaining(1) == 8000,
                    "A stopped crane preserves the worn parts and has yet to consume the replacements");
                startAutomatic(h, desk, facing);
            })
            .thenWaitUntil(() -> h.assertTrue(reactor.componentRemaining(0) == 24000 && reactor.componentRemaining(1) == 16000,
                "AUTO finishes both exchanges with a full store and a damaged cartridge at the head: " + crane.status()))
            .thenExecute(() -> {
                int blankets = 0, divertors = 0;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    var stack = items.getStackInSlot(slot);
                    if (stack.is(CEFusion.SPENT_BLANKET.get())) blankets += items.extractItem(slot, 1, false).getCount();
                    if (stack.is(CEFusion.SPENT_DIVERTOR.get())) divertors += items.extractItem(slot, 1, false).getCount();
                }
                h.assertTrue(blankets == 1 && divertors == 1 && items.getStackInSlot(0).getDamageValue() == 1,
                    "The item port returns exactly one spent part of each kind and the damaged spare stays untouched");
            })
            .thenSucceed();
    }

    private static void startAutomatic(GameTestHelper h, BlockPos desk, Direction facing) {
        ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
        ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
    }

}
