package net.createplant.atomics.client;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;

import net.createplant.atomics.block.ConverterStructure;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Opt-in real-world check for glued converter assembly, tilt and disassembly in two orientations. */
public final class ConverterClientCheck {
    private static final Direction[] FACINGS = { Direction.NORTH, Direction.EAST };
    private static final String[] NAMES = { "north", "east" };
    private static final float[][] CAMERAS = {
        { 10, 9, -14, 35, 18 },
        { -15, 9, 10, -124, 18 }
    };
    private static BlockPos[] origins;
    private static int orientation = -1, stage, ticks, fov;
    private static volatile Throwable failure;

    private ConverterClientCheck() { }

    public static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(36);
        int y = mc.player.blockPosition().getY() + 1;
        origins = new BlockPos[] { new BlockPos(0, y, 8), new BlockPos(22, y, 8) };
        failure = null;
        server(mc, () -> {
            for (int index = 0; index < FACINGS.length; index++) build(mc, origins[index], FACINGS[index]);
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        orientation = -1;
        stage = 0;
        ticks = 0;
    }

    public static boolean tick(Minecraft mc) {
        if (failure != null) throw new IllegalStateException("Converter client check failed", failure);
        ticks++;
        if (mc.screen != null) mc.setScreen(null);
        if (orientation < 0 && ticks >= 70) {
            orientation = 0;
            stage = 0;
            moveCamera(mc);
            ticks = 0;
            return false;
        }
        if (orientation < 0 || ticks < 45) return false;

        capture(mc);
        if (stage == 0) server(mc, () -> assembleAt(mc, orientation, 30));
        else if (stage == 1) server(mc, () -> setAngle(mc, orientation, 60));
        else if (stage == 2) server(mc, () -> returnUpright(mc, orientation));
        else if (++orientation < FACINGS.length) {
            stage = 0;
            moveCamera(mc);
            ticks = 0;
            return false;
        } else {
            mc.options.hideGui = false;
            mc.options.fov().set(fov);
            return true;
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static void capture(Minecraft mc) {
        String state = switch (stage) {
            case 0 -> "0-glued-upright";
            case 1 -> "1-assembled-30";
            case 2 -> "2-tilted-60";
            default -> "3-disassembled";
        };
        Screenshot.grab(mc.gameDirectory, "quality-converter-" + NAMES[orientation] + "-" + state + ".png",
            mc.getMainRenderTarget(), message -> { });
    }

    private static void moveCamera(Minecraft mc) {
        mc.options.hideGui = true;
        float[] camera = CAMERAS[orientation];
        BlockPos origin = origins[orientation];
        server(mc, () -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(origin.getX() + camera[0], origin.getY() + camera[1], origin.getZ() + camera[2],
                camera[3], camera[4]);
        });
    }

    private static void build(Minecraft mc, BlockPos origin, Direction facing) {
        var level = mc.getSingleplayerServer().overworld();
        for (ConverterStructure.Cell cell : ConverterStructure.cells())
            level.setBlock(ConverterStructure.at(origin, facing, cell.x(), cell.y(), cell.z()),
                ConverterStructure.stateFor(cell, facing), 3);
        List<BlockPos> positions = new ArrayList<>();
        for (ConverterStructure.Cell cell : ConverterStructure.movingCells())
            positions.add(ConverterStructure.at(origin, facing, cell.x(), cell.y(), cell.z()));
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        level.addFreshEntity(new SuperGlueEntity(level,
            SuperGlueEntity.span(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ))));
        ConverterTrunnionBearingBlockEntity controller = controller(mc, origin, facing);
        controller.validateStructure();
        if (!controller.formed()) throw new IllegalStateException("The " + facing + " converter did not form upright");
    }

    private static void assembleAt(Minecraft mc, int index, float angle) {
        ConverterTrunnionBearingBlockEntity controller = controller(mc, origins[index], FACINGS[index]);
        controller.setSpeedForTest(32);
        controller.assemble();
        if (!controller.isRunning() || controller.getMovedContraption() == null)
            throw new IllegalStateException("The " + FACINGS[index] + " glued vessel did not assemble");
        controller.setAngleForTest(angle);
    }

    private static void setAngle(Minecraft mc, int index, float angle) {
        ConverterTrunnionBearingBlockEntity controller = controller(mc, origins[index], FACINGS[index]);
        if (!controller.isRunning()) throw new IllegalStateException("The vessel disassembled before reaching 60 degrees");
        controller.setAngleForTest(angle);
    }

    private static void returnUpright(Minecraft mc, int index) {
        ConverterTrunnionBearingBlockEntity controller = controller(mc, origins[index], FACINGS[index]);
        for (int tick = 0; tick < 12 && controller.isRunning(); tick++) {
            controller.setSpeedForTest(-32);
            controller.tick();
        }
        if (controller.isRunning() || controller.angle() != 0)
            throw new IllegalStateException("The " + FACINGS[index] + " vessel did not return and disassemble");
        var level = mc.getSingleplayerServer().overworld();
        for (ConverterStructure.Cell cell : ConverterStructure.movingCells()) {
            BlockPos pos = ConverterStructure.at(origins[index], FACINGS[index], cell.x(), cell.y(), cell.z());
            if (!level.getBlockState(pos).is(ConverterStructure.stateFor(cell, FACINGS[index]).getBlock()))
                throw new IllegalStateException("A vessel block did not return at " + pos);
        }
    }

    private static ConverterTrunnionBearingBlockEntity controller(Minecraft mc, BlockPos origin, Direction facing) {
        var blockEntity = mc.getSingleplayerServer().overworld().getBlockEntity(ConverterStructure.bearing(origin, facing));
        if (!(blockEntity instanceof ConverterTrunnionBearingBlockEntity controller))
            throw new IllegalStateException("Missing converter trunnion bearing at " + origin);
        return controller;
    }

    private static void server(Minecraft mc, Runnable action) {
        mc.getSingleplayerServer().execute(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure = throwable;
            }
        });
    }
}
