package net.createplant.atomics.client;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.Map;
import net.createplant.atomics.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.atomics.fusion.FusionPhysics;
import net.createplant.atomics.fusion.FusionStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Opt-in RTX render check for the complete compact tokamak and its operating preview state. */
public final class FusionClientCheck {
    private record View(double x, double y, double z, double lookX, double lookY, double lookZ, int fov, String name) { }

    private static final double VIEW_SCALE = FusionPhysics.WIDTH / 11d;
    private static final View[] VIEWS = {
        new View(13 * VIEW_SCALE, 13 * VIEW_SCALE, 14 * VIEW_SCALE, 0, 0, 0, 40, "quality-fusion-two-cell.png"),
        new View(4 * VIEW_SCALE, 22 * VIEW_SCALE, 4 * VIEW_SCALE, 0, 0, 0, 40, "quality-fusion-two-cell-top.png")
    };
    private static BlockPos centre, controller;
    private static int ticks, view, oldFov;
    private static volatile String failure;

    private FusionClientCheck() { }

    public static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        oldFov = mc.options.fov().get();
        mc.options.hideGui = true;
        centre = new BlockPos(0, mc.player.blockPosition().getY() + FusionPhysics.MIDPLANE + 1, 22);
        controller = centre.relative(Direction.NORTH, FusionPhysics.HALF_WIDTH);
        failure = null;
        mc.getSingleplayerServer().execute(() -> prepare(mc));
        view = -1;
        ticks = 0;
    }

    public static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException(failure);
        if (mc.screen != null) mc.setScreen(null);
        if (mc.level.getBlockEntity(controller) instanceof FusionReactorControllerBlockEntity reactor)
            reactor.previewForClient();
        ticks++;
        if (view < 0 && ticks >= 100 || view >= 0 && ticks == 55) {
            if (view >= 0) {
                if (!(mc.level.getBlockEntity(controller) instanceof FusionReactorControllerBlockEntity reactor)
                    || !reactor.formed() || reactor.plasmaKelvin() < FusionPhysics.PLASMA_KELVIN * .99)
                    throw new IllegalStateException("The complete compact tokamak did not receive its operating preview state");
                Screenshot.grab(mc.gameDirectory, VIEWS[view].name(), mc.getMainRenderTarget(), message -> { });
            }
            if (++view == VIEWS.length) {
                Files.writeString(mc.gameDirectory.toPath().resolve("fusion-client-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "structure", FusionPhysics.WIDTH + "x" + FusionPhysics.WIDTH + "x" + FusionPhysics.HEIGHT + " two-by-two plasma tokamak", "plasmaKelvin", FusionPhysics.PLASMA_KELVIN,
                        "views", VIEWS.length, "render", "captured")));
                mc.options.hideGui = false;
                mc.options.fov().set(oldFov);
                return true;
            }
            move(mc, VIEWS[view]);
            ticks = 0;
        }
        if (view >= 0 && ticks >= 25) orient(mc, VIEWS[view]);
        return false;
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        int floorRadius = FusionPhysics.HALF_WIDTH + 2;
        for (int x = -floorRadius; x <= floorRadius; x++) for (int z = -floorRadius; z <= floorRadius; z++)
            level.setBlock(centre.offset(x, -FusionPhysics.MIDPLANE - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        for (FusionStructure.Cell cell : FusionStructure.cells(controller, Direction.SOUTH)) {
            if (cell.role() != FusionStructure.Role.CLEAR) level.setBlock(cell.pos(), cell.expected(), Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(controller) instanceof FusionReactorControllerBlockEntity)) {
            failure = "The complete compact tokamak did not create its controller";
            return;
        }
        move(mc, VIEWS[0]);
    }

    private static void move(Minecraft mc, View next) {
        mc.options.fov().set(next.fov());
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            float[] angles = angles(next);
            player.connection.teleport(centre.getX() + next.x(), centre.getY() + next.y(), centre.getZ() + next.z(), angles[0], angles[1]);
        });
    }

    private static void orient(Minecraft mc, View next) {
        float[] angles = angles(next);
        mc.player.setYRot(angles[0]);
        mc.player.setXRot(angles[1]);
        mc.player.yRotO = angles[0];
        mc.player.xRotO = angles[1];
    }

    private static float[] angles(View view) {
        double dx = view.lookX() - view.x(), dy = view.lookY() - (view.y() + 1.62), dz = view.lookZ() - view.z();
        return new float[] {(float) Math.toDegrees(-Math.atan2(dx, dz)),
            (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }
}
