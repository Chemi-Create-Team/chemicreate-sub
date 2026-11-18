package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.createplant.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.nuclear.FuelPoolStructure;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CECrane;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CENuclear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Opt-in integrated-server transfer and renderer check in a separate development world. */
final class CraneClientCheck {
    private static final int REACTOR_BOTTOM_Y = 9;
    private static final int REACTOR_TOP_Y = REACTOR_BOTTOM_Y + ReactorPhysics.FUEL_ASSEMBLY_LENGTH;
    private static final int RAIL_Y = REACTOR_TOP_Y + CraneDriveBlockEntity.SERVICE_MAST_LENGTH;
    private static final float[][] VIEWS = {{-17, RAIL_Y + 16, -20, -40, 40}, {1, RAIL_Y + 5, 0, -42, 27}, {13, RAIL_Y + 3, 1, 28, 44}};
    private static BlockPos origin;
    private static CompletableFuture<?> pending;
    private static int phase, ticks, elapsed, view, fov;
    private static volatile boolean transferred;
    private static boolean movingCaptured;
    private static double minSlide = Double.POSITIVE_INFINITY, maxSlide;
    private static volatile Map<String, Object> result;

    private CraneClientCheck() { }

    private static BlockPos at(int x, int y, int z) { return origin.offset(x, y, z); }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(50);
        origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 6);
        pending = mc.getSingleplayerServer().submit(() -> build(mc.getSingleplayerServer().overworld()));
        phase = ticks = elapsed = 0;
        view = -1;
    }

    private static void build(ServerLevel level) {
        for (int x = 0; x <= 16; x++) for (int z = 1; z <= 13; z++) {
            boolean pool = x >= 10 && x <= 13 && z >= 5 && z <= 8;
            boolean reactor = x >= 3 && x <= 9 && z >= 3 && z <= 9;
            if (!pool && !reactor) level.setBlock(at(x, REACTOR_TOP_Y, z), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), 3);
        }
        int lower = REACTOR_BOTTOM_Y - ReactorPhysics.LOWER_STRUCTURE_HEIGHT;
        for (int x = 3; x <= 9; x++) for (int z = 3; z <= 9; z++) {
            boolean core = x >= 5 && x <= 7 && z >= 5 && z <= 7;
            if (!core) {
                for (int y = lower; y <= REACTOR_TOP_Y; y++) level.setBlock(at(x, y, z), CENuclear.BIOLOGICAL_SHIELD.get().defaultBlockState(), 3);
                continue;
            }
            for (int y = lower; y < REACTOR_BOTTOM_Y; y++) level.setBlock(at(x, y, z), CENuclear.FEED_BUNDLE.get().defaultBlockState(), 3);
            for (int y = REACTOR_BOTTOM_Y; y < REACTOR_BOTTOM_Y + ReactorPhysics.CORE_HEIGHT; y++)
                level.setBlock(at(x, y, z), (x == 5 && z == 5 ? CENuclear.FUEL_CHANNEL.get() : CENuclear.GRAPHITE.get()).defaultBlockState(), 3);
            for (int y = REACTOR_BOTTOM_Y + ReactorPhysics.CORE_HEIGHT; y < REACTOR_TOP_Y; y++)
                level.setBlock(at(x, y, z), CENuclear.FUEL_CHANNEL_TUBE.get().defaultBlockState(), 3);
            level.setBlock(at(x, REACTOR_TOP_Y, z), CENuclear.TOP_CAP.get().defaultBlockState(), 3);
        }
        for (int x = 10; x <= 13; x++) for (int z = 5; z <= 8; z++) {
            boolean wall = x == 10 || x == 13 || z == 5 || z == 8;
            if (wall) for (int y = 8; y <= 8 + FuelPoolStructure.DEPTH; y++) level.setBlock(at(x, y, z), CEMetals.STEEL_BLOCK.get().getBlock().defaultBlockState(), 3);
            else level.setBlock(at(x, 8, z), CENuclear.FUEL_POOL.get().defaultBlockState(), 3);
        }
        var coolant = level.getCapability(Capabilities.FluidHandler.BLOCK, at(11, 8, 6), Direction.DOWN);
        int poolWater = 4 * FuelPoolBlockEntity.WATER_PER_CELL;
        if (coolant == null || coolant.fill(new FluidStack(Fluids.WATER, poolWater), IFluidHandler.FluidAction.EXECUTE) != poolWater)
            throw new IllegalStateException("Adjacent pool did not accept its full water inventory");
        var pool = (FuelPoolBlockEntity) level.getBlockEntity(at(11, 8, 6));
        pool.set(0, FuelPoolBlockEntity.FRESH);
        pool.set(1, FuelPoolBlockEntity.FRESH);
        // The rail ends rest directly on building columns, so the crane has no girder legs.
        for (int x : new int[] {2, 15}) for (int z : new int[] {2, 12})
            for (int y = 0; y < RAIL_Y; y++) level.setBlock(at(x, y, z), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), 3);
        for (int x = 2; x <= 15; x++) for (int z : new int[] {2, 12})
            level.setBlock(at(x, RAIL_Y, z), CraneFrame.girder(level, at(x, RAIL_Y, z), Direction.Axis.X), 3);
        level.setBlock(at(1, RAIL_Y, 2), CECrane.DRIVE.get().defaultBlockState().setValue(CraneDriveBlock.FACING, Direction.EAST).setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        ((CraneDriveBlockEntity) level.getBlockEntity(at(1, RAIL_Y, 2))).size(14, 9);
        level.setBlock(at(0, RAIL_Y, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        ((CreativeMotorBlockEntity) level.getBlockEntity(at(0, RAIL_Y, 2))).generatedSpeed.setValue(128);
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (++elapsed > 3000) throw new IllegalStateException("Crane client transfer timed out");
        if (pending != null) {
            if (!pending.isDone()) return false;
            pending.join();
            pending = null;
        }
        if (mc.screen != null) mc.setScreen(null);
        if (phase == 1 && mc.level.getBlockEntity(at(1, RAIL_Y, 2)) instanceof CraneDriveBlockEntity shown && shown.working() && shown.carried() != 0) {
            minSlide = Math.min(minSlide, shown.slide());
            maxSlide = Math.max(maxSlide, shown.slide());
            if (!movingCaptured && ticks > 60) {
                Screenshot.grab(mc.gameDirectory, "quality-crane-moving.png", mc.getMainRenderTarget(), message -> { });
                movingCaptured = true;
            }
        }
        if (phase == 0 && ++ticks >= 60) {
            pending = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var column = level.getCapability(Capabilities.ItemHandler.BLOCK, at(5, REACTOR_TOP_Y, 5), Direction.UP);
                if (column == null || !column.insertItem(2, CENuclear.FUEL_ROD.toStack(), false).isEmpty())
                    throw new IllegalStateException("Shielded reactor did not form");
                ((FuelChannelBlockEntity) level.getBlockEntity(at(5, 9, 5))).burn(FuelChannelBlockEntity.ROD_JOULES);
                level.setBlock(at(1, RAIL_Y + 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            });
            phase = 1;
            ticks = 0;
            look(mc, VIEWS[0]);
        } else if (phase == 1 && ++ticks % 10 == 0) {
            pending = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var column = (FuelChannelBlockEntity) level.getBlockEntity(at(5, REACTOR_BOTTOM_Y, 5));
                var crane = (CraneDriveBlockEntity) level.getBlockEntity(at(1, RAIL_Y, 2));
                int fresh = 0, spent = 0;
                for (int x = 11; x <= 12; x++) for (int z = 6; z <= 7; z++) for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) {
                    byte rod = ((FuelPoolBlockEntity) level.getBlockEntity(at(x, 8, z))).slot(slot);
                    if (rod == FuelPoolBlockEntity.FRESH) fresh++;
                    if (rod == FuelPoolBlockEntity.SPENT) spent++;
                }
                transferred = column.fresh() == 2 && column.spent() == 0 && fresh == 0 && spent == 1 && crane.carried() == 0;
                if (transferred) result = Map.of("freshInReactor", column.fresh(), "spentInPool", spent, "carried", crane.carried(),
                    "overheadSupports", crane.structure() != null && crane.structure().legs() == 0, "lowerMastBlocks", CraneDriveBlockEntity.LOWER_MAST_LENGTH,
                    "upperMastBlocks", CraneDriveBlockEntity.UPPER_MAST_LENGTH, "railHeightAboveCap", RAIL_Y - REACTOR_TOP_Y,
                    "fixedMastDepth", crane.depth(), "transfer", "passed");
            });
            if (transferred) { phase = 2; ticks = 0; }
        } else if (phase == 2 && ++ticks >= 35) {
            if (view >= 0) Screenshot.grab(mc.gameDirectory, "quality-crane-" + view + ".png", mc.getMainRenderTarget(), message -> { });
            if (++view == VIEWS.length) {
                if (!movingCaptured || maxSlide - minSlide < 2) throw new IllegalStateException("Fuel sliding animation was not observed");
                var report = new java.util.LinkedHashMap<String, Object>(result);
                report.put("movingFrame", "captured");
                report.put("observedSlideRange", maxSlide - minSlide);
                Files.writeString(mc.gameDirectory.toPath().resolve("crane-client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                mc.options.hideGui = false;
                mc.options.fov().set(fov);
                return true;
            }
            look(mc, VIEWS[view]);
            ticks = 0;
        }
        if (phase == 2 && view >= 0 && ticks >= 15) {
            float[] v = VIEWS[view];
            mc.player.setYRot(v[3]); mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3]; mc.player.xRotO = v[4];
        }
        return false;
    }

    static void verifyPonder(Minecraft mc) {
        if (!(mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui)) throw new IllegalStateException("Crane Ponder is not open");
        var scene = ui.getActiveScene();
        BlockPos drive = scene.getId().getPath().equals("crane_refuelling") ? new BlockPos(1, 45, 2) : new BlockPos(1, 14, 1);
        if (!(scene.getWorld().getBlockEntity(drive) instanceof CraneDriveBlockEntity crane) || crane.structure() == null)
            throw new IllegalStateException("Crane moving parts missing from Ponder: " + scene.getId());
    }

    private static void look(Minecraft mc, float[] v) {
        mc.options.hideGui = true;
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(origin.getX() + v[0], origin.getY() + v[1], origin.getZ() + v[2], v[3], v[4]);
        });
    }
}
