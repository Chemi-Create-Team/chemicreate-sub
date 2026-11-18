package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.block.entity.QuarryConveyor;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CECrane;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Legs, gantry shaft rails, the drive, then the bridge and hoist moving over the work area to service it. The storyboard
 * has its rails at y = 14 from x = 2 to 8 along z = 1 and z = 7, the drive at (1, 14, 1) and a motor west of it.
 */
public final class CranePonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.gantry_crane.";
    private static final BlockPos DRIVE = new BlockPos(1, 14, 1);
    private static final BlockPos REFUEL_DRIVE = new BlockPos(1, 45, 2);
    private static final BlockPos REFUEL_START = new BlockPos(2, 45, 2);
    private static final int REFUEL_LENGTH = 14, REFUEL_SPAN = 10;
    private static final BlockPos QUARRY_DRIVE = new BlockPos(3, 4, 2), QUARRY_START = new BlockPos(4, 4, 2);

    private CranePonderScenes() { }

    public static void crane(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("gantry_crane", PonderText.title(LANG + "title", "Gantry crane"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.28f);
        scene.setSceneOffsetY(-3);
        scene.showBasePlate();
        PonderText.build(scene, util, 3, 1, 2, 7, 1, 6, Direction.UP);
        scene.idle(10);
        // The drive comes first; it builds its own legs and rails from the girders it is given.
        PonderText.build(scene, util, 0, 14, 1, 1, 14, 1, Direction.EAST);
        scene.overlay().showControls(util.vector().blockSurface(DRIVE, Direction.NORTH), Pointing.RIGHT, 55).rightClick().withItem(AllItems.WRENCH.asStack());
        text(scene, util, DRIVE, "drive");

        scene.overlay().showControls(util.vector().topOf(DRIVE), Pointing.DOWN, 50).withItem(AllBlocks.METAL_GIRDER.asStack());
        scene.world().setKineticSpeed(util.select().fromTo(0, 14, 1, 1, 14, 1), 64);
        for (int[] c : new int[][] {{2, 1}, {8, 1}, {2, 7}, {8, 7}}) PonderText.build(scene, util, c[0], 1, c[1], c[0], 13, c[1], Direction.DOWN);
        text(scene, util, new BlockPos(2, 7, 1), "legs");

        scene.world().showSection(util.select().fromTo(2, 14, 1, 8, 14, 1).add(util.select().fromTo(2, 14, 7, 8, 14, 7)), Direction.DOWN);
        hoist(scene, util, 0, 3, false);
        text(scene, util, new BlockPos(5, 14, 7), "rails");

        hoist(scene, util, 3, 3, true);
        scene.world().createItemEntity(Vec3.atCenterOf(DRIVE.above()), new Vec3(0, .04, 0), CENuclear.FUEL_ROD.toStack());
        text(scene, util, DRIVE, "service");

        scene.markAsFinished();
    }

    public static void refuel(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String lang = "ponder.create_refinery_n_reactors.crane_refuelling.";
        scene.title("crane_refuelling", PonderText.title(lang + "title", "Fuel transfer over a reactor and pool"));
        scene.configureBasePlate(0, 0, 16);
        scene.scaleSceneView(.11f);
        scene.setSceneOffsetY(-12);
        scene.showBasePlate();
        PonderText.build(scene, util, 0, 1, 2, 15, 46, 12, Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(0, 45, 2, 15, 45, 2)
            .add(util.select().fromTo(2, 45, 12, 15, 45, 12)), 128);
        refuelHoist(scene, util, 3.25, 3.25, 0);
        refuelText(scene, util, lang, "layout", new BlockPos(15, 44, 12));
        var racks = util.select().fromTo(11, 8, 6, 12, 8, 7);
        scene.world().modifyBlockEntityNBT(racks, FuelPoolBlockEntity.class, tag -> {
            tag.putInt("PoolCells", 4);
            tag.putInt("PoolAmount", 4 * FuelPoolBlockEntity.WATER_PER_CELL);
            tag.putString("PoolStatus", "ready");
        });
        scene.world().modifyBlockEntityNBT(util.select().position(11, 8, 6), FuelPoolBlockEntity.class,
            tag -> tag.putByteArray("Slots", new byte[] {1, 1, 0, 0}));
        refuelText(scene, util, lang, "prepare", new BlockPos(11, 30, 6));
        refuelHoist(scene, util, 3.25, 3.25, 2);
        refuelHoist(scene, util, 9.25, 4.25, 2);
        refuelHoist(scene, util, 9.25, 4.25, 0);
        scene.world().modifyBlockEntityNBT(util.select().position(11, 8, 6), FuelPoolBlockEntity.class,
            tag -> tag.putByteArray("Slots", new byte[] {1, 1, 2, 0}));
        refuelText(scene, util, lang, "take", new BlockPos(11, 30, 6));
        scene.world().modifyBlockEntityNBT(util.select().position(11, 8, 6), FuelPoolBlockEntity.class,
            tag -> tag.putByteArray("Slots", new byte[] {0, 1, 2, 0}));
        refuelHoist(scene, util, 9.25, 4.25, 1);
        refuelHoist(scene, util, 3.25, 3.25, 1);
        refuelHoist(scene, util, 3.25, 3.25, 0);
        refuelText(scene, util, lang, "load", new BlockPos(5, 30, 5));
        refuelText(scene, util, lang, "wait", REFUEL_DRIVE);
        scene.markAsFinished();
    }

    public static void quarryFrame(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String lang = "ponder.create_refinery_n_reactors.quarry_frame.";
        var input = QUARRY_DRIVE.above();var lever = QUARRY_DRIVE.below();
        scene.title("quarry_frame", PonderText.title(lang + "title", "Build and run an automatic quarry"));
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(.6f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        PonderText.build(scene, util, 4, 1, 3, 11, 3, 7, Direction.DOWN);
        scene.world().showSection(util.select().position(QUARRY_DRIVE), Direction.DOWN);
        quarryMaterials(scene, util, 28, 1, 1, 2, 9);
        quarryText(scene, util, lang, "place", QUARRY_DRIVE);
        scene.overlay().showControls(util.vector().blockSurface(QUARRY_DRIVE, Direction.WEST), Pointing.RIGHT, 55).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.overlay().showOutline(PonderPalette.INPUT, "length", util.select().position(QUARRY_DRIVE), 80);
        scene.idle(30);
        scene.overlay().showControls(util.vector().blockSurface(QUARRY_DRIVE, Direction.NORTH), Pointing.RIGHT, 55).rightClick().withItem(AllItems.WRENCH.asStack());
        quarryText(scene, util, lang, "size", QUARRY_DRIVE);
        scene.overlay().showControls(util.vector().topOf(QUARRY_DRIVE), Pointing.DOWN, 60).withItem(AllItems.GOGGLES.asStack());
        quarryText(scene, util, lang, "missing", QUARRY_DRIVE);

        scene.world().showSection(util.select().position(input).add(util.select().position(input.above())), Direction.DOWN);
        for (var stack : new net.minecraft.world.item.ItemStack[] {AllBlocks.METAL_GIRDER.asStack(28),
            AllBlocks.MECHANICAL_DRILL.asStack(), AllItems.BELT_CONNECTOR.asStack(), AllBlocks.SHAFT.asStack(2), AllBlocks.ENCASED_CHAIN_DRIVE.asStack(9)}) {
            scene.overlay().showControls(util.vector().topOf(input.above()), Pointing.DOWN, 25).rightClick().withItem(stack);
            scene.idle(25);
        }
        quarryMaterials(scene, util, 0, 0, 0, 0, 0);
        quarryText(scene, util, lang, "supply", input);
        scene.world().showSection(util.select().position(QUARRY_DRIVE.south()).add(util.select().position(lever)), Direction.UP);
        scene.world().setKineticSpeed(util.select().position(QUARRY_DRIVE).add(util.select().position(QUARRY_DRIVE.south())), 64);
        scene.world().toggleRedstonePower(util.select().position(lever));
        quarryText(scene, util, lang, "power", QUARRY_DRIVE.south());

        for (int[] c : new int[][] {{4, 2}, {11, 2}, {4, 8}, {11, 8}})
            PonderText.build(scene, util, c[0], 1, c[1], c[0], 3, c[1], Direction.DOWN);
        PonderText.build(scene, util, 4, 4, 2, 11, 4, 2, Direction.WEST);
        PonderText.build(scene, util, 4, 4, 8, 11, 4, 8, Direction.NORTH);
        quarryText(scene, util, lang, "build", QUARRY_START);
        PonderText.build(scene, util, 3, 4, 0, 11, 4, 0, Direction.SOUTH);
        scene.world().setKineticSpeed(util.select().fromTo(3, 4, 0, 11, 4, 0), 64);
        quarryText(scene, util, lang, "drive_line", new BlockPos(7, 4, 0));
        PonderText.build(scene, util, 3, 4, 1, 11, 4, 1, Direction.SOUTH);
        scene.world().setKineticSpeed(util.select().fromTo(3, 4, 1, 11, 4, 1), 64);
        quarryHoist(scene, util, 0, 1, 0, false);
        quarryText(scene, util, lang, "belt", new BlockPos(7, 4, 1));

        BlockPos water = new BlockPos(6, 3, 5), lava = new BlockPos(9, 3, 5);
        scene.world().setBlock(water, Blocks.WATER.defaultBlockState(), false);
        scene.world().setBlock(lava, Blocks.LAVA.defaultBlockState(), false);
        quarryText(scene, util, lang, "pumping", water);
        scene.world().setBlock(water, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(lava, Blocks.AIR.defaultBlockState(), false);

        for (int[] b : new int[][] {{4, 3}, {5, 4}, {6, 3}, {7, 5}}) {
            quarryHoist(scene, util, b[0] - 4, b[1] - 2, 0.5, true);
            scene.idle(8);scene.world().destroyBlock(new BlockPos(b[0], 3, b[1]));
        }
        quarryText(scene, util, lang, "dig", new BlockPos(7, 3, 5));
        scene.world().setBlocks(util.select().fromTo(4, 1, 3, 11, 3, 7), Blocks.AIR.defaultBlockState(), true);
        quarryHoist(scene, util, 3, 3, 3, true);
        quarryParcels(scene, util);
        quarryText(scene, util, lang, "flow", new BlockPos(7, 4, 5));
        for (int i = 0; i < 3; i++) {
            scene.world().createItemOnBelt(new BlockPos(7, 4, 1), Direction.SOUTH,
                new net.minecraft.world.item.ItemStack(i == 1 ? net.minecraft.world.item.Items.RAW_IRON : net.minecraft.world.item.Items.COBBLESTONE, 8));
            scene.idle(10);
        }
        quarryText(scene, util, lang, "outlet", new BlockPos(3, 4, 1));
        quarryText(scene, util, lang, "work_time", QUARRY_DRIVE);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.world().modifyBlockEntityNBT(util.select().position(QUARRY_DRIVE), QuarryDriveBlockEntity.class, tag -> {
            tag.putBoolean("Working", false);tag.putString("Status", "redstone");
        });
        quarryText(scene, util, lang, "stop", lever);
        scene.markAsFinished();
    }

    private static void quarryMaterials(CreateSceneBuilder scene, SceneBuildingUtil util, int girders, int drills, int belts, int beltShafts, int chains) {
        scene.world().modifyBlockEntityNBT(util.select().position(QUARRY_DRIVE), QuarryDriveBlockEntity.class, tag -> {
            tag.putInt("QuarryLength", 8);tag.putInt("QuarryWidth", 5);
            tag.putLong("FrameStart", QUARRY_START.asLong());tag.putInt("FrameAlong", Direction.EAST.get3DDataValue());
            tag.putInt("FrameAcross", Direction.SOUTH.get3DDataValue());tag.putInt("FrameLength", 8);tag.putInt("FrameWidth", 5);
            tag.putBoolean("MaterialsKnown", true);tag.putInt("MissingGirders", girders);
            tag.putInt("MissingDrills", drills);tag.putInt("MissingBelts", belts);tag.putString("Status", "redstone");
            tag.putInt("MissingBeltShafts", beltShafts);tag.putInt("MissingChains", chains);
            tag.putInt("Supplies", 41 - girders - drills - belts - beltShafts - chains);
        });
    }

    /** Parcels on their way: two rising in the chute and one crossing the bridge, timed from the scene's start. */
    private static void quarryParcels(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlockEntityNBT(util.select().position(QUARRY_DRIVE), QuarryDriveBlockEntity.class, tag -> {
            var parcels = new net.minecraft.nbt.ListTag();
            int[][] timing = {{0, 0, 60}, {0, -30, 60}, {1, 0, 80}};
            String[][] stacks = {{"minecraft:cobblestone", "16"}, {"minecraft:raw_iron", "4"}, {"minecraft:cobblestone", "16"}};
            for (int i = 0; i < 3; i++) {
                var entry = new net.minecraft.nbt.CompoundTag();
                var stack = new net.minecraft.nbt.CompoundTag();
                stack.putString("id", stacks[i][0]);
                stack.putInt("count", Integer.parseInt(stacks[i][1]));
                entry.put("Stack", stack);
                entry.putBoolean("Bridge", timing[i][0] == 1);
                entry.putLong("Start", tag.getLong("Now") * QuarryConveyor.SAVED_CLOCK_SCALE + timing[i][1]);
                entry.putInt("Duration", timing[i][2]);
                parcels.add(entry);
            }
            tag.put("Parcels", parcels);
        });
        scene.idle(30);
    }

    private static void quarryText(CreateSceneBuilder scene, SceneBuildingUtil util, String lang, String key, BlockPos target) {
        PonderText.caption(scene, util.vector().centerOf(target), lang + key, 140);
    }

    private static void quarryHoist(CreateSceneBuilder scene, SceneBuildingUtil util, double u, double v, double depth, boolean working) {
        scene.world().modifyBlockEntityNBT(util.select().position(QUARRY_DRIVE), QuarryDriveBlockEntity.class, tag -> {
            tag.putLong("Start", QUARRY_START.asLong());
            tag.putInt("Along", Direction.EAST.get3DDataValue());
            tag.putInt("Across", Direction.SOUTH.get3DDataValue());
            tag.putInt("Length", 8);
            tag.putInt("Span", 6);
            tag.putInt("Legs", 3);
            tag.putDouble("U", u);
            tag.putDouble("V", v);
            tag.putDouble("Depth", depth);
            tag.putBoolean("Drill", true);
            tag.putBoolean("Belt", true);
            tag.putBoolean("Working", working);tag.putString("Status", working ? "mining" : "idle");
            tag.remove("FrameStart");tag.putInt("Supplies", 0);tag.putInt("Outputs", 0);
        });
        scene.idle(10);
    }

    private static void refuelHoist(CreateSceneBuilder scene, SceneBuildingUtil util, double u, double v, int carried) {
        scene.world().modifyBlockEntityNBT(util.select().position(REFUEL_DRIVE), CraneDriveBlockEntity.class, tag -> {
            tag.putLong("Start", REFUEL_START.asLong());
            tag.putInt("Along", Direction.EAST.get3DDataValue());
            tag.putInt("Across", Direction.SOUTH.get3DDataValue());
            tag.putInt("Length", REFUEL_LENGTH); tag.putInt("Span", REFUEL_SPAN); tag.putInt("Legs", 0);
            tag.putDouble("U", u); tag.putDouble("V", v);
            tag.putDouble("Depth", CraneDriveBlockEntity.SERVICE_MAST_DEPTH);
            tag.putInt("Carried", carried); tag.putInt("Remaining", 0);
            tag.putBoolean("Working", false);
        });
        scene.idle(30);
    }

    private static void refuelText(CreateSceneBuilder scene, SceneBuildingUtil util, String lang, String key, BlockPos target) {
        PonderText.caption(scene, util.vector().centerOf(target), lang + key, 140);
    }

    /** Sets the drawn crane: bridge along the rails and trolley across, the mast at its service depth. */
    private static void hoist(CreateSceneBuilder scene, SceneBuildingUtil util, double u, double v, boolean working) {
        scene.world().modifyBlockEntityNBT(util.select().position(DRIVE), CraneDriveBlockEntity.class, tag -> {
            tag.putLong("Start", new BlockPos(2, 14, 1).asLong());
            tag.putInt("Along", Direction.EAST.get3DDataValue());
            tag.putInt("Across", Direction.SOUTH.get3DDataValue());
            tag.putInt("Length", 7);
            tag.putInt("Span", 6);
            tag.putInt("Legs", 4);
            tag.putDouble("U", u);
            tag.putDouble("V", v);
            tag.putDouble("Depth", CraneDriveBlockEntity.SERVICE_MAST_DEPTH);
            tag.putBoolean("Working", working);
        });
        scene.idle(20);
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, 120);
    }
}
