package net.createplant.client;

import com.simibubi.create.AllBlocks;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CENuclear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

final class FuelPoolClientCheck {
    private static final float[][] VIEWS = {{-9, 25, -11, -39, 52}, {2.5f, 29, 3.5f, 0, 90}, {2.5f, 12, -4, 0, 25}};
    private static BlockPos origin;
    private static int ticks, view, fov;
    private static volatile String failure;

    private FuelPoolClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(50);
        origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            for (int x = -3; x <= 7; x++) for (int z = -3; z <= 9; z++) {
                boolean wall = x == -1 || x == 5 || z == -1 || z == 7;
                boolean outside = x < -1 || x > 5 || z < -1 || z > 7;
                if (outside) {
                    level.setBlock(origin.offset(x, 15, z), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), 3);
                    if (x == -3 || x == 7 || z == -3 || z == 9) level.setBlock(origin.offset(x, 16, z), Blocks.IRON_BARS.defaultBlockState(), 3);
                } else if (wall) for (int y = 0; y <= 15; y++) level.setBlock(origin.offset(x, y, z), CEMetals.STEEL_BLOCK.get().getBlock().defaultBlockState(), 3);
                else level.setBlock(origin.offset(x, 0, z), CENuclear.FUEL_POOL.get().defaultBlockState(), 3);
            }
            var coolant = level.getCapability(Capabilities.FluidHandler.BLOCK, origin, Direction.DOWN);
            if (coolant == null || coolant.fill(new FluidStack(Fluids.WATER, 560000), IFluidHandler.FluidAction.EXECUTE) != 560000) {
                failure = "The client-check pool did not form or accept exactly 560,000 mB";
                return;
            }
            for (int x = 1; x < 4; x++) for (int z = 2; z < 5; z++) {
                var pool = (FuelPoolBlockEntity) level.getBlockEntity(origin.offset(x, 0, z));
                for (int slot = 0; slot < 4; slot++) pool.set(slot, FuelPoolBlockEntity.SPENT);
            }
        });
        view = -1;
        ticks = 0;
    }

    static boolean tick(Minecraft mc) {
        if (failure != null) throw new IllegalStateException(failure);
        if (mc.screen != null) mc.setScreen(null);
        ticks++;
        if (view < 0 && ticks >= 100 || view >= 0 && ticks == 30) {
            if (view >= 0) Screenshot.grab(mc.gameDirectory, "quality-fuel-pool-" + view + ".png", mc.getMainRenderTarget(), message -> { });
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
            mc.player.setYRot(v[3]); mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3]; mc.player.xRotO = v[4];
        }
        return false;
    }
}
