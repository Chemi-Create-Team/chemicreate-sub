package net.createplant.client;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.Map;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.registry.CENuclear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Opt-in rendered check of the complete five-by-five reactor control-room wall. */
public final class ReactorConsoleClientCheck {
    private static BlockPos master;
    private static int ticks, fov;
    private static volatile String failure;

    private ReactorConsoleClientCheck() { }

    public static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(55);
        mc.options.hideGui = true;
        master = new BlockPos(0, mc.player.blockPosition().getY() + 1, 8);
        mc.getSingleplayerServer().execute(() -> prepare(mc));
        ticks = 0;
    }

    public static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException(failure);
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) mc.setScreen(null);
        if (mc.level.getBlockEntity(master) instanceof ReactorControlConsoleBlockEntity console) console.preview();
        if (++ticks == 90) {
            if (!(mc.level.getBlockEntity(master) instanceof ReactorControlConsoleBlockEntity console)
                || !console.linked() || !console.walled() || console.fuelMap().length < 100 || console.bundles() < 20)
                throw new IllegalStateException("The rendered console did not receive its dynamic preview data");
            Screenshot.grab(mc.gameDirectory, "quality-reactor-console.png", mc.getMainRenderTarget(), message -> { });
        }
        if (ticks < Integer.getInteger("createplant.consoleClientHold",120)) return false;
        var console = (ReactorControlConsoleBlockEntity) mc.level.getBlockEntity(master);
        Files.writeString(mc.gameDirectory.toPath().resolve("reactor-console-result.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "facing",console.facing().getName(),"map", "4x4", "wall", "7x4", "fuelDots", console.fuelMap().length,
                "bundles", console.bundles(), "render", "captured")));
        mc.options.hideGui = false;
        mc.options.fov().set(fov);
        return true;
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        Direction facing = Direction.byName(System.getProperty("createplant.consoleClientFacing","north"));
        if(facing==null || facing.getAxis()==Direction.Axis.Y)facing=Direction.NORTH;
        Direction right=facing.getCounterClockWise();
        for (int x = -8; x <= 7; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(master.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos board = net.createplant.nuclear.ReactorConsoleStructure.boardBehind(master, facing);
        net.createplant.nuclear.ReactorConsoleStructure.DESK.place((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL), master, facing);
        net.createplant.nuclear.ReactorConsoleStructure.DISPLAY.place((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL), board, facing);
        net.createplant.nuclear.ReactorConsoleStructure.DESK.tryForm(level, master);
        net.createplant.nuclear.ReactorConsoleStructure.DISPLAY.tryForm(level, board);
        var console = (ReactorControlConsoleBlockEntity) level.getBlockEntity(master);
        if (!console.stationComplete() || !console.linkDisplay(board)) {
            failure = "The rendered control room did not form";
            return;
        }
        var player = server.getPlayerList().getPlayers().getFirst();
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        double distance=Double.parseDouble(System.getProperty("createplant.consoleClientDistance","11.5"));
        var eye=net.minecraft.world.phys.Vec3.atCenterOf(master).add(right.getStepX(),2.7,right.getStepZ())
            .add(facing.getStepX()*distance,0,facing.getStepZ()*distance);
        var target=net.minecraft.world.phys.Vec3.atCenterOf(master).add(right.getStepX(),2,right.getStepZ())
            .add(-facing.getStepX()*.5,0,-facing.getStepZ()*.5);
        double x=eye.x,y=eye.y,z=eye.z,tx=target.x,ty=target.y,tz=target.z;
        float yaw = (float) Math.toDegrees(-Math.atan2(tx - x, tz - z));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx - x, tz - z)));
        player.connection.teleport(x, y, z, yaw, pitch);
    }
}
