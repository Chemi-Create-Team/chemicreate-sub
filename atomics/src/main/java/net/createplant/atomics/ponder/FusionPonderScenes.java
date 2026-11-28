package net.createplant.atomics.ponder;

import net.createplant.ponder.PonderText;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import java.util.List;
import net.createplant.atomics.fusion.FusionPhysics;
import net.createplant.atomics.fusion.FusionStructure;
import net.createplant.atomics.registry.CEFusion;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Assembly and operating sequence for the fixed tokamak and its deliberately electromechanical desk. */
public final class FusionPonderScenes {
    private static final String TOKAMAK = "ponder.create_refinery_n_reactors.fusion_tokamak.";
    private static final String CONSOLE = "ponder.create_refinery_n_reactors.fusion_console.";
    private static final String MOTOR_GENERATOR = "ponder.create_refinery_n_reactors.fusion_motor_generator.";

    private FusionPonderScenes() { }

    public static void tokamak(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fusion_tokamak", PonderText.title(TOKAMAK + "title", "Building a doughnut-shaped D-T tokamak"));
        int edge = FusionPhysics.WIDTH - 1, half = FusionPhysics.HALF_WIDTH;
        int mid = FusionPhysics.MIDPLANE + 1;
        BlockPos controller = new BlockPos(half, mid, 0);
        BlockPos centre = controller.south(half);
        List<FusionStructure.Cell> cells = FusionStructure.cells(controller, Direction.SOUTH);
        scene.configureBasePlate(0, 0, FusionPhysics.WIDTH);
        scene.scaleSceneView(4.4f / FusionPhysics.WIDTH);
        scene.setSceneOffsetY(-2);
        scene.rotateCameraY(180);
        scene.world().setBlocks(util.select().fromTo(0,1,0,edge,FusionPhysics.HEIGHT,edge),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),false);
        for(var cell:cells)scene.world().setBlock(cell.pos(),cell.expected(),false);
        scene.showBasePlate();
        scene.idle(10);

        Selection frame = util.select().fromTo(0, 1, 0, edge, 1, edge);
        scene.world().showSection(frame, Direction.DOWN);
        text(scene, util, TOKAMAK, new BlockPos(half, 1, 2), "shell");

        PonderText.build(scene, util, 0, 2, 0, edge, FusionPhysics.HEIGHT, edge, Direction.DOWN);
        scene.idle(20);
        Selection magnets = selectRole(util, cells, FusionStructure.Role.COIL);
        scene.overlay().showOutline(PonderPalette.BLUE, "magnets", magnets, 120);
        text(scene, util, TOKAMAK, rolePosition(cells, FusionStructure.Role.COIL), "magnets");

        Selection path = selectPositions(util, FusionStructure.plasmaChannel().stream().map(centre::offset).toList());
        scene.overlay().showOutline(PonderPalette.GREEN, "plasma_channel", path, 120);
        text(scene, util, TOKAMAK, centre.offset(FusionStructure.plasmaChannel().getFirst()), "vessel");

        scene.rotateCameraY(180);
        Selection ports = util.select().position(controller.below(2)).add(util.select().position(controller.below()))
            .add(util.select().position(controller)).add(util.select().position(controller.above()))
            .add(util.select().position(half, mid - 1, edge))
            .add(util.select().position(half, mid + 1, edge)).add(util.select().position(edge, mid - 1, half))
            .add(util.select().position(edge, mid + 1, half));
        scene.world().showSection(ports, Direction.NORTH);
        scene.overlay().showOutline(PonderPalette.INPUT, "ports", ports, 120);
        scene.overlay().showControls(util.vector().blockSurface(controller.below(2), Direction.NORTH), Pointing.RIGHT, 80)
            .withItem(com.simibubi.create.AllBlocks.FLUID_PIPE.asStack());
        text(scene, util, TOKAMAK, controller.below(), "ports");

        BlockPos pump = new BlockPos(0, mid, half);
        scene.overlay().showOutline(PonderPalette.BLUE, "pump", util.select().position(pump), 110);
        scene.overlay().showOutline(PonderPalette.RED, "feeder", util.select().position(controller), 110);
        scene.overlay().showControls(util.vector().blockSurface(pump, Direction.WEST), Pointing.RIGHT, 60)
            .withItem(AllBlocks.SHAFT.asStack());
        text(scene, util, TOKAMAK, pump, "drive");

        scene.rotateCameraY(180);
        scene.overlay().showOutline(PonderPalette.GREEN, "operating", util.select().position(controller), 110);
        text(scene, util, TOKAMAK, controller, "operation");

        List<BlockPos> windows = cells.stream().filter(cell -> cell.role() == FusionStructure.Role.VESSEL && cell.pos().getZ() == edge
            && Math.abs(cell.pos().getX() - half) >= 2 && Math.abs(cell.pos().getX() - half) <= 3).map(FusionStructure.Cell::pos).toList();
        for (BlockPos window : windows) scene.world().setBlock(window, CEFusion.VIEWPORT.get().defaultBlockState(), true);
        scene.overlay().showOutline(PonderPalette.WHITE, "viewport", selectPositions(util, windows), 110);
        text(scene, util, TOKAMAK, windows.getFirst(), "viewport");

        scene.overlay().showOutline(PonderPalette.RED, "safety", selectRole(util, cells, FusionStructure.Role.VESSEL), 110);
        text(scene, util, TOKAMAK, rolePosition(cells, FusionStructure.Role.VESSEL), "safety");
        scene.markAsFinished();
    }

    public static void console(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fusion_console", PonderText.title(CONSOLE + "title", "Operating the electromechanical fusion desk"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.72f);
        scene.showBasePlate();
        scene.idle(10);

        Selection desk = util.select().fromTo(2, 1, 2, 4, 1, 3);
        scene.world().showSection(desk, Direction.DOWN);
        text(scene, util, CONSOLE, new BlockPos(3, 1, 2), "build");

        scene.overlay().showControls(util.vector().topOf(new BlockPos(2, 1, 2)), Pointing.DOWN, 70)
            .rightClick().withItem(AllBlocks.DISPLAY_LINK.asStack());
        scene.overlay().showOutline(PonderPalette.BLUE, "link", desk, 110);
        text(scene, util, CONSOLE, new BlockPos(2, 1, 2), "link");

        scene.overlay().showControls(util.vector().topOf(new BlockPos(2, 1, 2)), Pointing.DOWN, 45).rightClick();
        scene.overlay().showControls(util.vector().topOf(new BlockPos(4, 1, 2)), Pointing.DOWN, 45).rightClick();
        scene.overlay().showControls(util.vector().topOf(new BlockPos(2, 1, 3)), Pointing.DOWN, 45).rightClick();
        scene.overlay().showControls(util.vector().topOf(new BlockPos(3, 1, 3)), Pointing.DOWN, 45).rightClick();
        text(scene, util, CONSOLE, new BlockPos(3, 1, 3), "sequence");

        scene.overlay().showOutline(PonderPalette.RED, "dump", util.select().position(3, 1, 2), 110);
        scene.overlay().showControls(util.vector().topOf(new BlockPos(3, 1, 2)), Pointing.DOWN, 70).rightClick();
        text(scene, util, CONSOLE, new BlockPos(3, 1, 2), "dump");
        scene.markAsFinished();
    }

    public static void motorGenerator(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fusion_motor_generator", PonderText.title(MOTOR_GENERATOR + "title", "Powering a tokamak from its turbine shaft"));
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(.62f);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos controller = new BlockPos(4, 2, 4);
        BlockPos generator = new BlockPos(5, 2, 4);
        BlockPos flywheel = new BlockPos(6, 2, 4);
        Selection assembly = util.select().fromTo(controller, flywheel);
        scene.world().showSection(util.select().position(controller), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(generator), Direction.WEST);
        scene.idle(15);
        scene.world().showSection(util.select().position(flywheel), Direction.WEST);
        scene.overlay().showOutline(PonderPalette.BLUE, "motor_generator_assembly", assembly, 120);
        text(scene, util, MOTOR_GENERATOR, generator, "assembly");

        Selection drive = util.select().fromTo(7, 2, 4, 10, 2, 4);
        scene.world().showSection(drive, Direction.WEST);
        scene.world().setKineticSpeed(util.select().fromTo(5, 2, 4, 10, 2, 4), 256);
        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(10, 2, 4), Direction.EAST), Pointing.RIGHT, 70)
            .withItem(AllBlocks.CREATIVE_MOTOR.asStack());
        text(scene, util, MOTOR_GENERATOR, flywheel, "cold_start");

        scene.world().replaceBlocks(util.select().position(10, 2, 4), AllBlocks.SHAFT.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X), true);
        scene.world().modifyBlock(generator, state -> state.hasProperty(net.createplant.atomics.block.FusionMotorGeneratorBlock.ACTIVE)
            ? state.setValue(net.createplant.atomics.block.FusionMotorGeneratorBlock.ACTIVE, true) : state, false);
        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(10, 2, 4), Direction.EAST), Pointing.RIGHT, 70)
            .withItem(new net.minecraft.world.item.ItemStack(net.createplant.registry.CEBlocks.HIGH_PRESSURE_STEAM_TURBINE.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, "motor_generator_transfer", util.select().fromTo(5, 2, 4, 10, 2, 4), 120);
        text(scene, util, MOTOR_GENERATOR, new BlockPos(9, 2, 4), "transfer");

        scene.overlay().showControls(util.vector().blockSurface(generator, Direction.UP), Pointing.DOWN, 70)
            .withItem(com.simibubi.create.AllItems.GOGGLES.asStack());
        text(scene, util, MOTOR_GENERATOR, generator, "observe");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, String prefix, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), prefix + key, 120);
    }

    private static Selection selectRole(SceneBuildingUtil util, List<FusionStructure.Cell> cells, FusionStructure.Role role) {
        return selectPositions(util, cells.stream().filter(cell -> cell.role() == role).map(FusionStructure.Cell::pos).toList());
    }

    private static Selection selectPositions(SceneBuildingUtil util, List<BlockPos> positions) {
        Selection selection = util.select().position(positions.getFirst());
        for (int i = 1; i < positions.size(); i++) selection = selection.add(util.select().position(positions.get(i)));
        return selection;
    }

    private static BlockPos rolePosition(List<FusionStructure.Cell> cells, FusionStructure.Role role) {
        return cells.stream().filter(cell -> cell.role() == role).map(FusionStructure.Cell::pos).findFirst().orElseThrow();
    }
    public static void coldBox(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("helium_cold_box", PonderText.title("ponder.create_refinery_n_reactors.helium_cold_box.title", "Helium cold box"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.7f);
        scene.showBasePlate();
        PonderText.build(scene, util, 3, 1, 4, 5, 1, 6, Direction.DOWN);
        coldBoxText(scene, util, new BlockPos(4, 1, 5), "base");
        scene.world().showSection(util.select().position(4, 2, 5), Direction.DOWN);
        coldBoxText(scene, util, new BlockPos(4, 2, 5), "core");
        PonderText.build(scene, util, 3, 2, 4, 5, 3, 6, Direction.DOWN);
        PonderText.build(scene, util, 4, 2, 1, 4, 2, 3, Direction.SOUTH);
        scene.world().setKineticSpeed(util.select().fromTo(4, 2, 1, 4, 2, 3), 64);
        coldBoxText(scene, util, new BlockPos(4, 2, 3), "drive");
        PonderText.build(scene, util, 4, 3, 3, 4, 4, 3, Direction.DOWN);
        coldBoxText(scene, util, new BlockPos(4, 3, 3), "gas");
        PonderText.build(scene, util, 2, 2, 3, 3, 2, 3, Direction.EAST);
        PonderText.build(scene, util, 5, 2, 3, 6, 2, 3, Direction.WEST);
        coldBoxText(scene, util, new BlockPos(5, 2, 3), "water");
        PonderText.build(scene, util, 3, 1, 3, 4, 1, 3, Direction.UP);
        coldBoxText(scene, util, new BlockPos(4, 1, 3), "liquid");
        scene.overlay().showControls(util.vector().centerOf(4, 2, 3), Pointing.RIGHT, 60)
            .withItem(com.simibubi.create.AllItems.GOGGLES.asStack());
        coldBoxText(scene, util, new BlockPos(4, 2, 3), "check");
        scene.markAsFinished();
    }
    private static void coldBoxText(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, String suffix) {
        PonderText.caption(scene, util.vector().centerOf(target), "ponder.create_refinery_n_reactors.helium_cold_box." + suffix, 130);
    }
}
