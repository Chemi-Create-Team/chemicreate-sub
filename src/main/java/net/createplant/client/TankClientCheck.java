package net.createplant.client;

import net.createplant.block.TankFootprint;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.LoggerFactory;

/**
 * Opt-in integrated-world check of open tanks filled through the fluid capability.
 */
final class TankClientCheck {
    private static final int DIAMETER = 8;
    private static final int HEIGHT = 4;
    private static BlockPos water;
    private static BlockPos lava;
    private static int phase;
    private static int ticks;

    private TankClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        int y = mc.player.blockPosition().getY();
        water = new BlockPos(0, y, 6);
        lava = new BlockPos(10, y, 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            for (BlockPos origin : new BlockPos[]{water, lava}) {
                for (int x = 0; x < DIAMETER; x++) {
                    for (int z = 0; z < DIAMETER; z++) {
                        if (!TankFootprint.contains(x, z, DIAMETER))
                            continue;
                        level.setBlock(origin.offset(x, 0, z), CEBlocks.TANK_CASING.get().defaultBlockState(), 3);
                        if (TankFootprint.isWall(x, z, DIAMETER)) {
                            for (int dy = 1; dy <= HEIGHT; dy++)
                                level.setBlock(origin.offset(x, dy, z), CEBlocks.TANK_CASING.get().defaultBlockState(), 3);
                        }
                    }
                }
            }
        });
        phase = 0;
        ticks = 0;
    }

    /** Returns true once the screenshots are taken. */
    static boolean tick(Minecraft mc) {
        ticks++;
        if (mc.screen != null)
            mc.setScreen(null);
        if (phase == 0 && ticks >= 10) {
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                fill(level, water, new FluidStack(Fluids.WATER, Integer.MAX_VALUE), .5f);
                fill(level, lava, new FluidStack(Fluids.LAVA, Integer.MAX_VALUE), .25f);
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(water.getX() + 9, water.getY() + 12, water.getZ() - 4, 0, 50);
            });
            mc.options.hideGui = true;
            phase = 1;
            ticks = 0;
        }
        if (phase == 1 && ticks >= 40) {
            mc.player.setYRot(0);
            mc.player.setXRot(50);
            mc.player.yRotO = 0;
            mc.player.xRotO = 50;
            if (ticks < 45)
                return false;
            Screenshot.grab(mc.gameDirectory, "quality-open-tank-fluid.png", mc.getMainRenderTarget(), message -> { });
            var center = (TankCasingBlockEntity) mc.level.getBlockEntity(water.offset((DIAMETER - 1) / 2, 0, (DIAMETER - 1) / 2));
            LoggerFactory.getLogger(TankClientCheck.class).info("TANK CLIENT: shape={} fluid={} level={}",
                center.getShape(), center.getFluid().getAmount(), center.getFluidLevel(1));
            mc.options.hideGui = false;
            return true;
        }
        return false;
    }

    private static void fill(net.minecraft.server.level.ServerLevel level, BlockPos origin, FluidStack fluid, float fraction) {
        BlockPos center = origin.offset((DIAMETER - 1) / 2, 0, (DIAMETER - 1) / 2);
        var tank = (TankCasingBlockEntity) level.getBlockEntity(center);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, center, Direction.DOWN);
        handler.fill(fluid.copyWithAmount((int) (tank.getCapacity() * fraction)), IFluidHandler.FluidAction.EXECUTE);
    }
}
