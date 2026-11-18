package net.createplant.client;

import net.createplant.block.CoolingFanShroudBlock;
import net.createplant.block.entity.RadiatorBlockEntity;
import net.createplant.registry.CEProcessBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

/** Opt-in integrated-world check: builds a formed four-fan bank and captures it from the reference's isometric angle. */
final class FinFanClientCheck {
    /** Camera (x, y, z, yaw, pitch) relative to the bank origin with a narrow FOV, close to the reference's isometric view. */
    private static final float[][] VIEWS = {{-14, 18, 20, -135, 33}, {6.5f, 20, 20, 180, 45}, {-12, 6, 10, -125, 15}, {26, 18, -20, 45, 33}, {3, 5, 7, 180, 25}};
    private static BlockPos origin;
    private static int view;
    private static int ticks;
    private static int fov;

    private FinFanClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(30);
        origin = new BlockPos(0, mc.player.blockPosition().getY(), 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            for (int x = 0; x < 12; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 0; y < 3; y++)
                        level.setBlock(origin.offset(x, y, z), CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState(), 3);
                }
            }
            for (int hub = 1; hub < 12; hub += 3) {
                level.setBlock(origin.offset(hub, 3, 0), CEProcessBlocks.COOLING_FAN.get().defaultBlockState(), 3);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0)
                            level.setBlock(origin.offset(hub + dx, 3, dz), CoolingFanShroudBlock.around(CEProcessBlocks.COOLING_FAN_SHROUD.get().defaultBlockState(), -dx, -dz), 3);
                    }
                }
            }
            ((RadiatorBlockEntity) level.getBlockEntity(origin.offset(1, 0, 0))).lazyTick();
        });
        view = -1;
        ticks = 0;
    }

    /** Returns true once every view is captured. */
    static boolean tick(Minecraft mc) {
        ticks++;
        if (mc.screen != null)
            mc.setScreen(null);
        if (view < 0 && ticks >= 20 || view >= 0 && ticks == 45) {
            if (view >= 0)
                Screenshot.grab(mc.gameDirectory, "quality-fin-fan-" + view + ".png", mc.getMainRenderTarget(), message -> { });
            if (++view == VIEWS.length) {
                mc.options.hideGui = false;
                mc.options.fov().set(fov);
                return true;
            }
            float[] v = VIEWS[view];
            mc.options.hideGui = true;
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(origin.getX() + v[0], origin.getY() + v[1], origin.getZ() + v[2], v[3], v[4]);
            });
            ticks = 0;
        }
        if (view >= 0 && ticks >= 35) {
            float[] v = VIEWS[view];
            mc.player.setYRot(v[3]);
            mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3];
            mc.player.xRotO = v[4];
        }
        return false;
    }
}
