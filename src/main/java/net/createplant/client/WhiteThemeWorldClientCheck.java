package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEDistillation;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.slf4j.LoggerFactory;

/** Opt-in real-world material/CT gallery; uses ordinary blocks and idle multiblocks rather than Ponder previews. */
final class WhiteThemeWorldClientCheck {
    private record View(double x, double y, double z, double lookX, double lookY, double lookZ, int fov, String name) { }

    private static final View[] VIEWS = {
        new View(23, 19, 37, 16, 4, 7, 65, "quality-white-theme-overview.png"),
        new View(6.8, 2.6, 6.3, 6.8, .9, .5, 74, "quality-white-theme-casing-comparison.png"),
        new View(4.5, 2, 5, 3.8, .9, .5, 40, "quality-white-theme-casing-connected.png"),
        new View(7, 4, 17, 7, 1, 8, 58, "quality-white-theme-vessels.png"),
        new View(10, 3, 13, 10.5, .9, 7.5, 43, "quality-white-theme-tank-connected.png"),
        new View(22, 8, 17, 18.7, 6, 7.7, 49, "quality-white-theme-fractionation.png"),
        new View(38, 12, 23, 29, 3.5, 8, 49, "quality-white-theme-fusion.png")
    };
    private static final BlockPos SOLID = new BlockPos(0, 0, 7), WINDOW = new BlockPos(5, 0, 7);
    private static final BlockPos PRESSURE = new BlockPos(10, 0, 7), CRYO = new BlockPos(14, 0, 7), COLUMN = new BlockPos(18, 0, 7);
    private static BlockPos controller;
    private static int baseY, phase, ticks, view, previousFov;
    private static boolean previousHideGui;
    private static volatile boolean built, ready;
    private static volatile Throwable serverFailure;
    private static File output;

    private WhiteThemeWorldClientCheck() { }

    static void start(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("The white-theme gallery needs a disposable integrated world");
        mc.options.pauseOnLostFocus = false;mc.setScreen(null);
        previousFov = mc.options.fov().get();previousHideGui = mc.options.hideGui;mc.options.hideGui = true;
        output = new File(System.getProperty("createplant.whiteThemeOutputDir", mc.gameDirectory.getPath()));
        if (!new File(output, "screenshots").isDirectory() && !new File(output, "screenshots").mkdirs())
            throw new IllegalStateException("Could not create the white-theme screenshot directory");
        baseY = mc.player.blockPosition().getY() + 1;
        controller = new BlockPos(29, baseY + FusionPhysics.MIDPLANE, 8).relative(Direction.NORTH, FusionPhysics.HALF_WIDTH);
        phase = 0;ticks = 0;view = 0;built = false;ready = false;serverFailure = null;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { build(server.overworld());built = true; }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (serverFailure != null) throw new IllegalStateException("White-theme gallery setup failed", serverFailure);
        if (mc.screen != null) mc.setScreen(null);
        if (++ticks > 400) throw new IllegalStateException("White-theme gallery did not synchronize");
        if (phase == 0 && built && ticks >= 20) {
            phase = 1;ticks = 0;
            var server = mc.getSingleplayerServer();var playerId = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    prepareVessel(level, absolute(SOLID), false);prepareVessel(level, absolute(WINDOW), true);
                    for (var origin : new BlockPos[]{PRESSURE, CRYO}) {
                        if (!(level.getBlockEntity(absolute(origin)) instanceof FluidTankBlockEntity tank)
                            || tank.getControllerBE() == null || tank.getControllerBE().getWidth() != 2 || tank.getControllerBE().getHeight() != 2)
                            throw new IllegalStateException("Gallery tank did not join at " + origin);
                    }
                    if (!(level.getBlockEntity(absolute(COLUMN)) instanceof FractionatingColumnBlockEntity column))
                        throw new IllegalStateException("Missing gallery fractionating column");
                    column.rebuild();
                    if (!column.getControllerBE().ready()) throw new IllegalStateException("Gallery fractionating column did not form");
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) throw new IllegalStateException("Missing gallery player");
                    player.setGameMode(GameType.SPECTATOR);player.setNoGravity(true);
                    player.getAbilities().flying = true;player.onUpdateAbilities();
                    ready = true;
                } catch (Throwable failure) { serverFailure = failure; }
            });
        }
        if (phase == 1 && ready) { move(mc, VIEWS[view]);phase = 2;ticks = 0; }
        if (phase != 2) return false;
        orient(mc, VIEWS[view]);
        if (ticks < 65) return false;
        verify(mc);
        Screenshot.grab(output, VIEWS[view].name(), mc.getMainRenderTarget(), message -> { });
        LoggerFactory.getLogger(WhiteThemeWorldClientCheck.class).info("WHITE THEME WORLD: captured {} baseY={} camera={}", VIEWS[view].name(), baseY, mc.player.position());
        if (++view < VIEWS.length) { move(mc, VIEWS[view]);ticks = 0;return false; }
        Files.createDirectories(output.toPath());
        Files.writeString(output.toPath().resolve("white-theme-client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
            "render", "captured_real_world", "views", List.of(VIEWS).stream().map(View::name).toList(),
            "dioriteCasing", "single and 2x2 connected wall", "comparison", "Create andesite, brass, copper casing and vanilla diorite",
            "idleMultiblocks", "solid/windowed 2x2x2 lidded vessels, pressure/cryogenic tanks, 2x2x14 column, complete compact tokamak")));
        mc.options.hideGui = previousHideGui;mc.options.fov().set(previousFov);phase = 3;
        return true;
    }

    private static BlockPos absolute(BlockPos relative) { return relative.offset(0, baseY, 0); }

    private static void build(ServerLevel level) {
        level.setDayTime(6000);level.setWeatherParameters(12000, 0, false, false);
        for (int x = -2; x <= 36; x++) for (int z = -2; z <= 18; z++)
            level.setBlock(new BlockPos(x, baseY - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(0, baseY, 0), CEBlocks.DIORITE_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int x = 3; x <= 4; x++) for (int y = 0; y < 2; y++)
            level.setBlock(new BlockPos(x, baseY + y, 0), CEBlocks.DIORITE_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
        var reference = new BlockState[]{AllBlocks.ANDESITE_CASING.getDefaultState(), AllBlocks.BRASS_CASING.getDefaultState(), AllBlocks.COPPER_CASING.getDefaultState(), Blocks.DIORITE.defaultBlockState()};
        for (int i = 0; i < reference.length; i++) level.setBlock(new BlockPos(7 + 2 * i, baseY, 0), reference[i], Block.UPDATE_ALL);
        var vessel = CETransport.LIDDED_VESSEL.get().defaultBlockState().setValue(LiddedVesselBlock.HINGE, Direction.SOUTH);
        for (var origin : new BlockPos[]{SOLID, WINDOW}) stack(level, absolute(origin), 2, 2, vessel);
        stack(level, absolute(PRESSURE), 2, 2, CETransport.PRESSURE_TANK.get().defaultBlockState());
        stack(level, absolute(CRYO), 2, 2, CETransport.CRYOGENIC_TANK.get().defaultBlockState());
        stack(level, absolute(COLUMN), 2, 14, CEDistillation.COLUMN.get().defaultBlockState());
        for (FusionStructure.Cell cell : FusionStructure.cells(controller, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) level.setBlock(cell.pos(), cell.expected(), Block.UPDATE_ALL);
    }

    private static void stack(ServerLevel level, BlockPos origin, int width, int height, BlockState state) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
            level.setBlock(origin.offset(x, y, z), state, Block.UPDATE_ALL);
    }

    private static void prepareVessel(ServerLevel level, BlockPos origin, boolean windowed) {
        if (!(level.getBlockEntity(origin) instanceof LiddedVesselBlockEntity part)) throw new IllegalStateException("Missing gallery vessel");
        var owner = part.getControllerBE();
        if (owner == null || owner.getWidth() != 2 || owner.getHeight() != 2) throw new IllegalStateException("Gallery vessel did not join");
        if (owner.getTankInventory().fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE) != 4000)
            throw new IllegalStateException("Gallery vessel refused its finite liquid preview");
        owner.validateStructure();
        if (windowed) owner.toggleWindows();
        owner.sendData();
    }

    private static void verify(Minecraft mc) {
        if (!mc.level.getBlockState(new BlockPos(0, baseY, 0)).is(CEBlocks.DIORITE_CASING.get())) throw new IllegalStateException("Client gallery casing is missing");
        for (var origin : new BlockPos[]{SOLID, WINDOW}) {
            if (!(mc.level.getBlockEntity(absolute(origin)) instanceof LiddedVesselBlockEntity part)) throw new IllegalStateException("Client gallery vessel is missing");
            var owner = part.getControllerBE();
            if (owner == null || owner.getWidth() != 2 || owner.getHeight() != 2 || owner.formed() || owner.windowsEnabled() != origin.equals(WINDOW))
                throw new IllegalStateException("Client gallery vessel CT/windows did not synchronize");
        }
        if (!(mc.level.getBlockEntity(absolute(COLUMN)) instanceof FractionatingColumnBlockEntity column)
            || column.getControllerBE() == null || column.getControllerBE().getWidth() != 2 || column.getControllerBE().getHeight() != 14)
            throw new IllegalStateException("Client gallery column geometry did not synchronize");
        if (!(mc.level.getBlockEntity(controller) instanceof FusionReactorControllerBlockEntity reactor) || !reactor.formed())
            throw new IllegalStateException("Client gallery tokamak did not form");
    }

    private static void move(Minecraft mc, View next) {
        mc.options.fov().set(next.fov());
        var server = mc.getSingleplayerServer();var playerId = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { serverFailure = new IllegalStateException("Missing gallery player during camera change");return; }
            var angles = angles(next);
            player.setNoGravity(true);player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(next.x(), baseY + next.y(), next.z(), angles[0], angles[1]);
        });
    }

    private static void orient(Minecraft mc, View next) {
        // The local movement tick otherwise cancels server-only creative flight and lets a waiting camera fall to the floor.
        mc.player.getAbilities().flying = true;mc.player.noPhysics = true;mc.player.setNoGravity(true);mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setPos(next.x(), baseY + next.y(), next.z());
        mc.player.xo = mc.player.xOld = next.x();mc.player.yo = mc.player.yOld = baseY + next.y();mc.player.zo = mc.player.zOld = next.z();
        var angles = angles(next);mc.player.setYRot(angles[0]);mc.player.setXRot(angles[1]);mc.player.yRotO = angles[0];mc.player.xRotO = angles[1];
    }

    private static float[] angles(View next) {
        double dx = next.lookX() - next.x(), dy = next.lookY() - next.y() - 1.62, dz = next.lookZ() - next.z();
        return new float[]{(float) Math.toDegrees(-Math.atan2(dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }
}
