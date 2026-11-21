package net.createplant.client;

import com.simibubi.create.AllBlocks;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Opt-in integrated-world check: Create's speedometer and stressometer beside the pipe thermometer, pressure gauge and flow meter
 * in copper pipe runs and a gas pipe, with readings held at 600 ℃, 12 bar and 64 mB/t so the dials stand off zero.
 */
final class PipeGaugeClientCheck {
    /** Camera (x, y, z, yaw, pitch) relative to the origin with a narrow FOV: front-left isometric, straight on at each row, then the front row again with the three items in the hotbar. */
    private static final float[][] VIEWS = {{-9, 10, 26, -145, 28}, {6.5f, -1, 32, 180, 0}, {3, -1, 10, 180, 0}, {2, 0, 5, 180, 0}, {6.5f, -1, 32, 180, 0}};
    private static BlockPos origin;
    private static int view;
    private static int ticks;
    private static int fov;

    private PipeGaugeClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(30);
        origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 0);
        mc.getSingleplayerServer().execute(() -> {
            build(mc.getSingleplayerServer().overworld());
            var inventory = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).getInventory();
            inventory.setItem(0, new net.minecraft.world.item.ItemStack(CETransport.PIPE_THERMOMETER.get()));
            inventory.setItem(1, new net.minecraft.world.item.ItemStack(CETransport.PIPE_PRESSURE_GAUGE.get()));
            inventory.setItem(2, new net.minecraft.world.item.ItemStack(CETransport.PIPE_FLOW_METER.get()));
        });
        view = -1;
        ticks = 0;
    }

    /** Front row: Create's speedometer, the thermometer, Create's stressometer, the pressure gauge and the flow meter; then a gas pipe; at the back a riser. */
    private static void build(ServerLevel level) {
        BlockPos front = origin.south(12);
        level.setBlock(front, AllBlocks.SPEEDOMETER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
        level.setBlock(front.east(5), AllBlocks.STRESSOMETER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
        for (int x : new int[]{2, 3, 4, 7, 8, 9, 10, 11, 12}) level.setBlock(front.east(x), AllBlocks.FLUID_PIPE.getDefaultState(), 3);
        level.setBlock(front.east(11), gauge(CETransport.PIPE_FLOW_METER.get(), Direction.Axis.X), 3);
        level.setBlock(front.east(3), gauge(CETransport.PIPE_THERMOMETER.get(), Direction.Axis.X), 3);
        level.setBlock(front.east(8), gauge(CETransport.PIPE_PRESSURE_GAUGE.get(), Direction.Axis.X), 3);
        BlockPos gas = origin.south(6);
        for (int x = 0; x <= 6; x++) level.setBlock(gas.east(x), CETransport.STAINLESS_PIPE.get().defaultBlockState(), 3);
        level.setBlock(gas.east(3), gauge(CETransport.PIPE_PRESSURE_GAUGE.get(), Direction.Axis.X), 3);
        for (int y = 0; y <= 3; y++) level.setBlock(origin.east(2).above(y), AllBlocks.FLUID_PIPE.getDefaultState(), 3);
        level.setBlock(origin.east(2).above(1), gauge(CETransport.PIPE_THERMOMETER.get(), Direction.Axis.Y), 3);
        for (int x = -1; x <= 13; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 12; z++) {
            BlockPos p = origin.offset(x, y, z);
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
        }
    }

    private static net.minecraft.world.level.block.state.BlockState gauge(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    /** Holds the readings a running line would report, so the captured dials stand off zero. */
    private static void hold(ServerLevel level) {
        for (int x = -1; x <= 13; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 12; z++)
            if (level.getBlockEntity(origin.offset(x, y, z)) instanceof PipeGaugeBlockEntity gauge) switch (gauge.kind()) {
                case TEMPERATURE -> gauge.observe(873.15);
                case PRESSURE -> gauge.observe(12e5);
                case FLOW -> gauge.observeFlow(64, 2);
            }
    }

    /** Returns true once every view is captured. */
    static boolean tick(Minecraft mc) {
        if (view >= VIEWS.length) return true;
        ticks++;
        if (mc.screen != null) mc.setScreen(null);
        mc.getSingleplayerServer().execute(() -> hold(mc.getSingleplayerServer().overworld()));
        if (view < 0 && ticks >= 40 || view >= 0 && ticks == 45) {
            if (view >= 0) Screenshot.grab(mc.gameDirectory, "quality-pipe-gauge-" + view + ".png", mc.getMainRenderTarget(), message -> { });
            if (++view == VIEWS.length) {
                mc.options.hideGui = false;
                mc.options.fov().set(fov);
                return true;
            }
            float[] v = VIEWS[view];
            mc.options.hideGui = view < VIEWS.length - 1;
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
