package net.createplant.client;

import com.simibubi.create.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Opt-in integrated-world check: an engine on top of a cylinder, and tanks lying along X and Z, with their crank shafts turning from a creative motor. */
final class HorizontalTankClientCheck {
    /** Camera (x, y, z, yaw, pitch) relative to the first cylinder, with a narrow FOV. */
    private static final float[][] VIEWS = {{-3, 5, -5, -30, 30}, {2, 7, 2, 0, 90}, {8, 5, 6, 120, 25}};
    private static BlockPos origin;
    private static int view, ticks, fov;

    private HorizontalTankClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(35);
        origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            var tank = com.simibubi.create.AllBlocks.FLUID_TANK.get().defaultBlockState();
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) level.setBlock(origin.offset(x, y, z), tank, 3);
            level.setBlock(origin.offset(6, 0, 0), tank, 3);
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) if (level.getBlockEntity(origin.offset(x, y, z)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity be && be.isController())
                be.getTankInventory().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 14000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (level.getBlockEntity(origin.offset(6, 0, 0)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity be)
                be.getTankInventory().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        view = -1;
        ticks = 0;
    }

    /** Returns true once every view is captured. */
    static boolean tick(Minecraft mc) {
        ticks++;
        if (mc.screen != null) mc.setScreen(null);
        if (view < 0 && ticks >= 60 || view >= 0 && ticks == 25) {
            if (view >= 0) Screenshot.grab(mc.gameDirectory, "quality-horizontal-tank-" + view + ".png", mc.getMainRenderTarget(), message -> { });
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
        if (view >= 0 && ticks >= 15) {
            float[] v = VIEWS[view];
            mc.player.setYRot(v[3]);
            mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3];
            mc.player.xRotO = v[4];
        }
        return false;
    }
}
