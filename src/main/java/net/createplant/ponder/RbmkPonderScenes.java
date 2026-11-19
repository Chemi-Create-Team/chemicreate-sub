package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Storyboards for the 5 + 2 + 7 + 2 + 12 m reactor profile and its physical reactor controls. */
public final class RbmkPonderScenes {
    private static final String CORE = "ponder.create_refinery_n_reactors.rbmk_core.";
    private static final String RODS = "ponder.create_refinery_n_reactors.rbmk_control_rods.";
    private static final String ROOM = "ponder.create_refinery_n_reactors.rbmk_control_room.";
    private static final String COMPACT = "ponder.create_refinery_n_reactors.rbmk_compact_core.";
    private static final int CORE_SIZE = ReactorCoreStructure.MIN_WIDTH, CORE_MIN = 2, CORE_MAX = CORE_MIN + CORE_SIZE - 1;
    private static final int VAULT_SIZE = CORE_SIZE + 2 * ReactorCoreStructure.SHIELD_THICKNESS, CENTRE = VAULT_SIZE / 2;
    private static final int[][] CONTROL_BUNDLES = {{1, 2}, {2, 1}, {2, 2}, {2, 3}, {3, 2}};
    private static final int LOWER = 2, Y0 = 9, TOP = 15, PLUG_TOP = 17, CAP = 30;

    private RbmkPonderScenes() { }

    public static void compactCore(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("rbmk_compact_core", PonderText.title(COMPACT + "title", "Building a compact three-metre RBMK core"));
        scene.configureBasePlate(0, 0, VAULT_SIZE);
        scene.scaleSceneView(.34f);scene.setSceneOffsetY(-3);
        scene.showBasePlate();scene.idle(10);

        PonderText.build(scene, util, CORE_MIN, 5, CORE_MIN, CORE_MAX, 7, CORE_MAX, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "compact_fuel", util.select().fromTo(CORE_MIN, 5, CORE_MIN, CORE_MAX, 7, CORE_MAX), 120);
        text(scene, util, COMPACT, new BlockPos(CENTRE, 6, CORE_MIN), "core");

        PonderText.build(scene, util, CORE_MIN, 2, CORE_MIN, CORE_MAX, 4, CORE_MAX, Direction.DOWN);
        PonderText.build(scene, util, CENTRE, 1, CENTRE - 1, CENTRE, 1, CENTRE, Direction.DOWN);
        PonderText.build(scene, util, 0, 2, 0, VAULT_SIZE - 1, 12, CORE_MIN - 1, Direction.SOUTH);
        PonderText.build(scene, util, 0, 2, CORE_MIN, CORE_MIN - 1, 12, VAULT_SIZE - 1, Direction.EAST);
        text(scene, util, COMPACT, new BlockPos(CENTRE, 3, CORE_MIN), "lower");

        PonderText.build(scene, util, CORE_MIN, 8, CORE_MIN, CORE_MAX, 12, CORE_MAX, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "compact_upper", util.select().fromTo(CORE_MIN, 8, CORE_MIN, CORE_MAX, 12, CORE_MAX), 120);
        text(scene, util, COMPACT, new BlockPos(CENTRE, 12, CENTRE), "upper");

        scene.overlay().showControls(util.vector().topOf(new BlockPos(CENTRE, 12, CENTRE)), Pointing.DOWN, 100).rightClick().withItem(CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack());
        text(scene, util, COMPACT, new BlockPos(CENTRE, 12, CENTRE), "fuel");
        text(scene, util, COMPACT, new BlockPos(CENTRE, 6, CENTRE), "fuel_line");

        scene.world().showSection(util.select().position(0, 6, CENTRE), Direction.EAST);
        scene.world().setKineticSpeed(util.select().fromTo(0, 6, CENTRE, 1, 6, CENTRE), 32);
        scene.overlay().showControls(util.vector().topOf(new BlockPos(CENTRE, 12, CENTRE)), Pointing.DOWN, 60).rightClick().withItem(CENuclear.CONTROL_ROD.toStack());
        text(scene, util, COMPACT, new BlockPos(CENTRE, 10, CENTRE), "rods");

        PonderText.build(scene, util, CORE_MAX, 9, CORE_MIN, VAULT_SIZE - 1, 9, CORE_MIN, Direction.WEST);
        scene.overlay().showOutline(PonderPalette.INPUT, "compact_feed", util.select().fromTo(CENTRE, 1, CENTRE - 1, CENTRE, 1, CENTRE), 120);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "compact_outlet", util.select().fromTo(CORE_MAX, 9, CORE_MIN, VAULT_SIZE - 1, 9, CORE_MIN), 120);
        text(scene, util, COMPACT, new BlockPos(CENTRE, 1, CENTRE - 1), "cooling");

        scene.world().createItemEntity(util.vector().topOf(new BlockPos(CENTRE - 1, 12, CENTRE)), new Vec3(0, .02, 0), CENuclear.FUEL_ROD.toStack());
        text(scene, util, COMPACT, new BlockPos(CENTRE - 1, 12, CENTRE), "scaling");

        BlockPos data = new BlockPos(CENTRE - 1, 13, CENTRE);
        scene.world().showSection(util.select().position(data), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(data), Pointing.DOWN, 60).withItem(AllItems.GOGGLES.asStack());
        text(scene, util, COMPACT, data, "readout");
        scene.markAsFinished();
    }

    public static void core(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("rbmk_core", PonderText.title(CORE + "title", "Building an RBMK core"));
        scene.configureBasePlate(0, 0, VAULT_SIZE);
        scene.scaleSceneView(.24f);
        scene.setSceneOffsetY(-7);
        scene.showBasePlate();
        scene.idle(10);

        PonderText.build(scene, util, CORE_MIN, Y0, CORE_MIN, CORE_MAX, TOP, CORE_MAX, Direction.DOWN);
        text(scene, util, CORE, new BlockPos(CENTRE, TOP, CORE_MIN), "graphite");

        scene.overlay().showOutline(PonderPalette.GREEN, "fuel", columns(util, false), 120);
        text(scene, util, CORE, new BlockPos(CENTRE - 1, TOP, CENTRE - 1), "fuel");

        scene.overlay().showOutline(PonderPalette.BLUE, "controls", columns(util, true), 120);
        text(scene, util, CORE, new BlockPos(CENTRE, TOP, CENTRE), "rods");

        PonderText.build(scene, util, 1, 1, 1, VAULT_SIZE - 2, 1, VAULT_SIZE - 2, Direction.UP);
        PonderText.build(scene, util, 0, 2, 0, VAULT_SIZE - 1, CAP, CORE_MIN, Direction.SOUTH);
        PonderText.build(scene, util, 0, 2, CORE_MIN + 1, 1, CAP, VAULT_SIZE - 1, Direction.EAST);
        text(scene, util, CORE, new BlockPos(1, 16, CENTRE + 1), "shield");

        scene.overlay().showOutline(PonderPalette.GREEN, "sizes",
            util.select().fromTo(CORE_MIN, TOP, CORE_MIN, CORE_MAX, TOP, CORE_MAX), 120);
        text(scene, util, CORE, new BlockPos(CENTRE, TOP, CENTRE), "sizes");

        PonderText.build(scene, util, CORE_MIN, LOWER, CORE_MIN, CORE_MAX, Y0 - 1, CORE_MAX, Direction.DOWN);
        PonderText.build(scene, util, CENTRE, LOWER - 1, CENTRE - 1, CENTRE, LOWER - 1, CENTRE, Direction.DOWN);
        PonderText.build(scene, util, CORE_MAX, PLUG_TOP + 4, CORE_MIN, VAULT_SIZE - 1, PLUG_TOP + 4, CORE_MIN, Direction.WEST);
        scene.overlay().showOutline(PonderPalette.INPUT, "inlet",
            util.select().fromTo(CENTRE, LOWER - 1, CENTRE - 1, CENTRE, LOWER - 1, CENTRE), 120);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "outlet",
            util.select().fromTo(CORE_MAX, PLUG_TOP + 4, CORE_MIN, VAULT_SIZE - 1, PLUG_TOP + 4, CORE_MIN), 120);
        text(scene, util, CORE, new BlockPos(CENTRE, LOWER - 1, CENTRE - 1), "water");

        scene.world().showSection(util.select().position(0, Y0 + 3, CENTRE), Direction.EAST);
        scene.world().setKineticSpeed(util.select().fromTo(0, Y0 + 3, CENTRE, 1, Y0 + 3, CENTRE), 32);
        text(scene, util, CORE, new BlockPos(1, Y0 + 3, CENTRE), "drive");

        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(1, 16, CENTRE + 1), Direction.WEST), Pointing.RIGHT, 60)
            .withItem(AllItems.GOGGLES.asStack());
        text(scene, util, CORE, new BlockPos(1, 16, CENTRE + 1), "readout");

        BlockPos dataLink = new BlockPos(CENTRE - 1, CAP + 1, CENTRE);
        PonderText.build(scene, util, CENTRE - 1, CAP, CENTRE, CENTRE - 1, CAP + 1, CENTRE, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "data_link", util.select().position(dataLink), 120);
        scene.overlay().showControls(util.vector().topOf(dataLink), Pointing.DOWN, 70).rightClick()
            .withItem(AllBlocks.DISPLAY_LINK.asStack());
        text(scene, util, CORE, dataLink, "data_link");

        PonderText.build(scene, util, CORE_MIN, TOP + 1, CORE_MIN, CORE_MAX, CAP - 1, CORE_MAX, Direction.DOWN);
        scene.idle(15);
        PonderText.build(scene, util, 0, CAP, 0, VAULT_SIZE - 1, CAP, VAULT_SIZE - 1, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "elena",
            util.select().fromTo(CORE_MIN, TOP + 1, CORE_MIN, CORE_MAX, PLUG_TOP, CORE_MAX), 120);
        text(scene, util, CORE, new BlockPos(CENTRE, CAP, CENTRE - 1), "hall");

        scene.world().createItemEntity(util.vector().topOf(new BlockPos(CENTRE - 1, CAP, CENTRE)), new Vec3(0, .02, 0), CENuclear.FUEL_ROD.toStack());
        text(scene, util, CORE, new BlockPos(CENTRE - 1, CAP, CENTRE), "crane");

        scene.world().showSection(util.select().position(0, Y0 + 2, CENTRE + 1), Direction.EAST);
        scene.world().toggleRedstonePower(util.select().position(0, Y0 + 2, CENTRE + 1));
        text(scene, util, CORE, new BlockPos(0, Y0 + 2, CENTRE + 1), "scram");
        scene.markAsFinished();
    }

    public static void controlRods(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("rbmk_control_rods", PonderText.title(RODS + "title", "Integrated control rods"));
        scene.configureBasePlate(0, 0, VAULT_SIZE);
        scene.scaleSceneView(.24f);
        scene.setSceneOffsetY(-7);
        scene.showBasePlate();
        scene.idle(10);

        PonderText.build(scene, util, 1, 1, 1, VAULT_SIZE - 2, 1, VAULT_SIZE - 2, Direction.UP);
        PonderText.build(scene, util, CORE_MIN, Y0, CORE_MIN, CORE_MAX, TOP, CORE_MAX, Direction.DOWN);
        PonderText.build(scene, util, 0, 2, 0, VAULT_SIZE - 1, CAP, CORE_MIN - 1, Direction.SOUTH);
        PonderText.build(scene, util, 0, 2, CORE_MIN, CORE_MIN - 1, CAP, VAULT_SIZE - 1, Direction.EAST);
        PonderText.build(scene, util, CORE_MIN, TOP + 1, CORE_MIN, CORE_MAX, CAP, CORE_MAX, Direction.DOWN);
        scene.world().showSection(util.select().position(0, Y0 + 2, CENTRE), Direction.EAST);
        scene.world().setKineticSpeed(util.select().fromTo(0, Y0 + 2, CENTRE, 1, Y0 + 2, CENTRE), 32);

        BlockPos cap = new BlockPos(CENTRE, CAP, CENTRE);
        scene.overlay().showControls(util.vector().topOf(cap), Pointing.DOWN, 45).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.overlay().showControls(util.vector().topOf(cap), Pointing.DOWN, 70).rightClick().withItem(CENuclear.CONTROL_ROD.toStack());
        text(scene, util, RODS, cap, "winch");

        scene.world().modifyBlockEntity(cap, net.createplant.block.entity.RodCapBlockEntity.class,
            top -> top.previewPlugPressure(1.55f, .65f, 1.25f, .8f));
        scene.overlay().showOutline(PonderPalette.RED, "relief", util.select().position(cap), 120);
        text(scene, util, RODS, cap, "relief");
        scene.world().modifyBlockEntity(cap, net.createplant.block.entity.RodCapBlockEntity.class,
            top -> top.previewPlugPressure(0, 0, 0, 0));

        scene.overlay().showOutline(PonderPalette.BLACK, "graphite",
            util.select().fromTo(CENTRE, Y0 + 1, CENTRE, CENTRE, Y0 + 5, CENTRE), 120);
        scene.overlay().showOutline(PonderPalette.BLUE, "connector",
            util.select().fromTo(CENTRE, TOP, CENTRE, CENTRE, TOP + 1, CENTRE), 120);
        scene.overlay().showOutline(PonderPalette.RED, "absorber",
            util.select().fromTo(CENTRE, TOP + 1, CENTRE, CENTRE, TOP + 7, CENTRE), 120);
        text(scene, util, RODS, new BlockPos(CENTRE, TOP + 4, CENTRE), "rod");

        scene.world().showSection(util.select().position(CENTRE, CAP + 1, CENTRE), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(new BlockPos(CENTRE, CAP + 1, CENTRE)), Pointing.DOWN, 40).rightClick();
        text(scene, util, RODS, new BlockPos(CENTRE, CAP + 1, CENTRE), "signal");

        scene.overlay().showOutline(PonderPalette.RED, "tip", util.select().position(CENTRE, Y0, CENTRE), 120);
        text(scene, util, RODS, new BlockPos(CENTRE, Y0, CENTRE), "tip");

        scene.world().createItemEntity(util.vector().topOf(cap), new Vec3(0, .02, 0), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
        text(scene, util, RODS, cap, "scram");
        scene.markAsFinished();
    }

    public static void controlRoom(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("rbmk_control_room", PonderText.title(ROOM + "title", "Operating the reactor control desk"));
        scene.configureBasePlate(0, 0, 12);
        scene.scaleSceneView(.5f);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos console = new BlockPos(7, 1, 3), rodButtons = new BlockPos(6, 1, 3), automatic = new BlockPos(5, 1, 3);
        BlockPos recorder = new BlockPos(4, 1, 3), map = new BlockPos(6, 4, 4), instruments = new BlockPos(3, 3, 4);
        Selection desk = util.select().fromTo(4, 1, 3, 7, 1, 3);
        Selection wall = util.select().fromTo(2, 2, 4, 8, 5, 4);
        Selection mapWall = util.select().fromTo(5, 2, 4, 8, 5, 4), instrumentWall = util.select().fromTo(2, 2, 4, 4, 5, 4);
        scene.world().showSection(desk, Direction.DOWN);
        BlockPos board = new BlockPos(8, 2, 4);
        scene.world().modifyBlockEntity(console, net.createplant.block.entity.ReactorControlConsoleBlockEntity.class, be -> {
            be.preview();be.previewDisplay(board);
        });
        scene.world().modifyBlockEntity(board, net.createplant.block.entity.ReactorConsoleDisplayBlockEntity.class, be -> be.previewLink(console));
        scene.idle(10);
        scene.world().showSection(wall, Direction.DOWN);
        text(scene, util, ROOM, console, "build");

        scene.overlay().showControls(util.vector().topOf(console), Pointing.DOWN, 70).rightClick()
            .withItem(net.createplant.registry.CENuclear.REACTOR_CONSOLE_DISPLAY_ITEM.get().getDefaultInstance());
        scene.overlay().showOutline(PonderPalette.GREEN, "map", mapWall, 120);
        text(scene, util, ROOM, map, "link");

        scene.overlay().showControls(util.vector().blockSurface(map, Direction.NORTH), Pointing.RIGHT, 55).rightClick();
        scene.overlay().showControls(util.vector().topOf(rodButtons), Pointing.DOWN, 55).rightClick();
        scene.overlay().showOutline(PonderPalette.GREEN, "rods", util.select().position(rodButtons), 100);
        text(scene, util, ROOM, rodButtons, "select");

        scene.overlay().showControls(util.vector().topOf(console), Pointing.DOWN, 70).rightClick();
        scene.overlay().showOutline(PonderPalette.RED, "az5", util.select().position(console), 100);
        text(scene, util, ROOM, console, "az5");

        scene.overlay().showOutline(PonderPalette.BLUE, "readout", instrumentWall, 120);
        text(scene, util, ROOM, instruments, "readout");

        scene.overlay().showControls(util.vector().topOf(recorder), Pointing.DOWN, 60).rightClick();
        scene.overlay().showOutline(PonderPalette.BLUE, "recorder", util.select().position(recorder), 100);
        text(scene, util, ROOM, recorder, "recorder");

        scene.overlay().showControls(util.vector().topOf(automatic), Pointing.DOWN, 60).rightClick();
        scene.overlay().showOutline(PonderPalette.GREEN, "automatic", util.select().position(automatic), 100);
        text(scene, util, ROOM, automatic, "automatic");

        var crane = new BlockPos(9, 1, 2);
        scene.world().setBlock(crane, net.createplant.registry.CECrane.DRIVE.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(crane), Direction.DOWN);
        var unit = net.createplant.registry.CENuclear.REACTOR_CONTROL_CONSOLE_ITEM.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(crane), Pointing.DOWN, 65).withItem(unit).rightClick();
        scene.idle(25);
        scene.overlay().showControls(util.vector().topOf(console), Pointing.DOWN, 65).withItem(unit).rightClick();
        scene.world().modifyBlockEntity(console, net.createplant.block.entity.ReactorControlConsoleBlockEntity.class,
            be -> be.previewCrane(crane, net.createplant.block.entity.ReactorControlConsoleBlockEntity.CraneMode.STOPPED));
        text(scene, util, ROOM, console, "crane_link");
        scene.overlay().showControls(Vec3.atLowerCornerOf(console).add(1 - 2.15 / 16, .7, 2.6 / 16), Pointing.DOWN, 65).rightClick();
        scene.world().modifyBlockEntity(console, net.createplant.block.entity.ReactorControlConsoleBlockEntity.class,
            be -> be.previewCrane(crane, net.createplant.block.entity.ReactorControlConsoleBlockEntity.CraneMode.MANUAL));
        text(scene, util, ROOM, console, "crane_mode");
        scene.overlay().showControls(Vec3.atLowerCornerOf(console).add(1 - 2.15 / 16, .8, 10.5 / 16), Pointing.DOWN, 65).rightClick();
        text(scene, util, ROOM, console, "crane_jog");
        scene.markAsFinished();
    }

    private static Selection columns(SceneBuildingUtil util, boolean controls) {
        Selection selection = null;
        for (int i = 0; i < CORE_SIZE; i++) for (int j = 0; j < CORE_SIZE; j++) {
            if (!ReactorCoreStructure.inCircle(i, j, CORE_SIZE) || controlBundle(i, j) != controls) continue;
            var column = util.select().fromTo(CORE_MIN + i, Y0, CORE_MIN + j, CORE_MIN + i, TOP, CORE_MIN + j);
            selection = selection == null ? column : selection.add(column);
        }
        return selection;
    }

    private static boolean controlBundle(int i, int j) {
        for (int[] c : CONTROL_BUNDLES) if (c[0] == i && c[1] == j) return true;
        return false;
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, String lang, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), lang + key, 120);
    }
}
