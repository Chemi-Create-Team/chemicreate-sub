package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.slf4j.LoggerFactory;

/** Opt-in integrated-world comparison of solid, warm-windowed and cold-windowed vessels, without attached ports. */
final class VesselWorldClientCheck {
    private static final int CONTENTS = 32000;
    private static final boolean DRIVE_VIEW = Boolean.getBoolean("createplant.vesselDriveClientCheck");
    private static final float YAW = 180, PITCH = 15;
    private static BlockPos[] origins;
    private static BlockPos reference;
    private static int phase, ticks, fov;
    private static boolean previousHideGui;
    private static volatile boolean built, ready;
    private static volatile Throwable serverFailure;

    private VesselWorldClientCheck() { }

    static void start(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("The vessel world check needs an integrated world");
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        previousHideGui = mc.options.hideGui;
        mc.options.fov().set(50);
        mc.options.hideGui = true;
        int y = mc.player.blockPosition().getY() + (DRIVE_VIEW ? 7 : 1);
        origins = new BlockPos[]{new BlockPos(0, y, 0), new BlockPos(5, y, 0), new BlockPos(10, y, 0), new BlockPos(15, y, 0)};
        reference = new BlockPos(20, y, 0);
        phase = 0;ticks = 0;built = false;ready = false;serverFailure = null;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var level = server.overworld();
                level.setDayTime(6000);
                for (int x = -1; x <= (DRIVE_VIEW ? 24 : 18); x++) for (int z = -1; z <= 3; z++)
                    level.setBlock(new BlockPos(x, y - (DRIVE_VIEW ? 7 : 1), z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                // Inspect the left-side ladders and the tank windows from the same front view.
                var state = CETransport.LIDDED_VESSEL.get().defaultBlockState().setValue(LiddedVesselBlock.HINGE, Direction.NORTH);
                for (int i = 0; i < origins.length; i++) {
                    int width = i == 3 ? 3 : 2;
                    for (int dy = 0; dy < 2; dy++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                        level.setBlock(origins[i].offset(x, dy, z), state, Block.UPDATE_ALL);
                }
                if (DRIVE_VIEW) for (int dy = 0; dy < 2; dy++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                    level.setBlock(reference.offset(x, dy, z), AllBlocks.FLUID_TANK.getDefaultState(), Block.UPDATE_ALL);
                built = true;
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    /** Returns true after the synchronized real inventories have been checked and the comparison captured. */
    static boolean tick(Minecraft mc) {
        if (serverFailure != null) throw new IllegalStateException("Vessel world setup failed", serverFailure);
        if (mc.screen != null) mc.setScreen(null);
        if (++ticks > 300) throw new IllegalStateException("Vessel world setup did not finish");
        if (phase == 0 && built && ticks >= 10) {
            phase = 1;ticks = 0;
            var server = mc.getSingleplayerServer();var playerId = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    for (int i = 0; i < origins.length; i++) prepare(level, origins[i], i);
                    if (DRIVE_VIEW) {
                        var tank = ((FluidTankBlockEntity) level.getBlockEntity(reference)).getControllerBE();
                        if (tank == null || tank.getWidth() != 3 || tank.getHeight() != 2 || tank.getTankInventory().fill(new FluidStack(Fluids.WATER, CONTENTS), FluidAction.EXECUTE) != CONTENTS)
                            throw new IllegalStateException("The native Create reference tank did not form");
                    }
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) throw new IllegalStateException("Missing integrated-world player");
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.connection.teleport(DRIVE_VIEW ? 11.5 : 9, origins[0].getY() + 4, DRIVE_VIEW ? 23 : 18, YAW, PITCH);
                    ready = true;
                } catch (Throwable failure) { serverFailure = failure; }
            });
        }
        if (phase == 1 && ready) { phase = 2;ticks = 0; }
        if (phase >= 3 && phase <= 5) {
            float pitch = phase == 3 ? -40 : 40;
            mc.player.setYRot(YAW);mc.player.setXRot(pitch);
            mc.player.yRotO = YAW;mc.player.xRotO = pitch;
            if (ticks < 45) return false;
            verify(mc);
            Screenshot.grab(mc.gameDirectory, phase == 3 ? "quality-vessel-drive.png" : phase == 4 ? "quality-vessel-top.png" : "quality-vessel-top-open.png", mc.getMainRenderTarget(), message -> { });
            if (phase == 3) {
                phase = 4;ticks = 0;
                var server = mc.getSingleplayerServer();var playerId = mc.player.getUUID();
                server.execute(() -> server.getPlayerList().getPlayer(playerId).connection.teleport(19, origins[0].getY() + 6, 9, YAW, 40));
                return false;
            }
            if (phase == 4) {
                phase = 5;ticks = 0;
                var server = mc.getSingleplayerServer();
                server.execute(() -> ((LiddedVesselBlockEntity) server.overworld().getBlockEntity(origins[3])).toggleLid());
                return false;
            }
            mc.options.hideGui = previousHideGui;
            mc.options.fov().set(fov);
            phase = 6;
            return true;
        }
        if (phase != 2) return false;
        mc.player.setYRot(YAW);mc.player.setXRot(PITCH);
        mc.player.yRotO = YAW;mc.player.xRotO = PITCH;
        if (ticks < 45) return false;
        verify(mc);
        if (DRIVE_VIEW) verifyCaps(mc);
        Screenshot.grab(mc.gameDirectory, "quality-vessel-world.png", mc.getMainRenderTarget(), message -> { });
        if (DRIVE_VIEW) {
            phase = 3;ticks = 0;
            var server = mc.getSingleplayerServer();var playerId = mc.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(playerId).connection.teleport(19, origins[0].getY() - 6, 9, YAW, -40));
            return false;
        }
        mc.options.hideGui = previousHideGui;
        mc.options.fov().set(fov);
        phase = 3;
        return true;
    }

    private static void prepare(ServerLevel level, BlockPos origin, int index) {
        if (!(level.getBlockEntity(origin) instanceof LiddedVesselBlockEntity vessel))
            throw new IllegalStateException("Missing vessel at " + origin);
        var owner = vessel.getControllerBE();
        if (owner == null || owner.getWidth() != (index == 3 ? 3 : 2) || owner.getHeight() != 2)
            throw new IllegalStateException("Vessel did not join into its square footprint at " + origin);
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, origin, Direction.NORTH);
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, origin, Direction.NORTH);
        if (fluids == null || items == null) throw new IllegalStateException("Missing real vessel capability");
        var content = index == 2 ? new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(), CONTENTS) : new FluidStack(Fluids.WATER, CONTENTS);
        content = FluidThermal.at(content, index == 2 ? 77.36 : 293.15);
        if (fluids.fill(content, FluidAction.EXECUTE) != CONTENTS)
            throw new IllegalStateException("Vessel capability did not accept its complete display inventory");
        if (!items.insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false).isEmpty()
            || !items.insertItem(1, new ItemStack(Items.GOLD_INGOT, 4), false).isEmpty())
            throw new IllegalStateException("Vessel capability did not accept its display ingredients");
        // Only the 3x3 vessel stirs over its stirrer; the smaller vessels retain plain storage.
        if (index == 3) level.setBlock(owner.stirrerPos(), CEReactor.STIRRER.get().defaultBlockState(), 3);
        owner.validateStructure();
        if (owner.formed() != (index == 3) || index == 3 && owner.sections() != 2 || owner.rotationSpeed() != 0)
            throw new IllegalStateException("The unpowered two-layer stirred vessel did not form");
        if (index > 0) owner.toggleWindows();
        owner.sendData();
    }

    private static void verifyCaps(Minecraft mc) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            int actual = capIndex(mc, origins[3].offset(x, 0, z), Direction.DOWN, PlantTankModels.VESSEL_CAP);
            int expected = capIndex(mc, reference.offset(x, 0, z), Direction.DOWN, AllSpriteShifts.FLUID_TANK_TOP);
            if (actual != expected) throw new IllegalStateException("Bottom cap differs from Create at " + x + "," + z + ": " + actual + "/" + expected);
            int top = capIndex(mc, reference.offset(x, 1, z), Direction.UP, AllSpriteShifts.FLUID_TANK_TOP);
            if (LiddedVesselRenderer.capTextureIndex(x, z) != top)
                throw new IllegalStateException("Top cap differs from Create at " + x + "," + z + ": " + LiddedVesselRenderer.capTextureIndex(x, z) + "/" + top);
        }
        LoggerFactory.getLogger(VesselWorldClientCheck.class).info("VESSEL CAPS CLIENT: all nine top and bottom tiles match the native Create 3x3 tank");
    }

    private static int capIndex(Minecraft mc, BlockPos pos, Direction face, CTSpriteShiftEntry shift) {
        var state = mc.level.getBlockState(pos);var model = mc.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(mc.level, pos, state, ModelData.EMPTY);
        for (int side = -1; side <= 0; side++) for (var quad : model.getQuads(state, side < 0 ? null : face, RandomSource.create(42), data, null)) {
            var sprite = quad.getSprite();
            if (quad.getDirection() != face || sprite != shift.getOriginal() && sprite != shift.getTarget()) continue;
            var target = shift.getTarget();var vertices = quad.getVertices();int stride = vertices.length / 4;float u = 0, v = 0;
            for (int i = 0; i < 4; i++) { u += Float.intBitsToFloat(vertices[i * stride + 4]);v += Float.intBitsToFloat(vertices[i * stride + 5]); }
            int column = (int) Math.floor((u / 4 - target.getU0()) / (target.getU1() - target.getU0()) * 4);
            int row = (int) Math.floor((v / 4 - target.getV0()) / (target.getV1() - target.getV0()) * 4);
            if (column < 0 || column > 3 || row < 0 || row > 3) throw new IllegalStateException("Cap is outside its connected sheet at " + pos);
            return column + row * 4;
        }
        throw new IllegalStateException("Missing connected cap at " + pos + " facing " + face);
    }

    private static void verify(Minecraft mc) {
        for (int i = 0; i < origins.length; i++) {
            var origin = origins[i];
            if (!(mc.level.getBlockEntity(origin) instanceof LiddedVesselBlockEntity vessel))
                throw new IllegalStateException("Client is missing vessel " + i);
            var owner = vessel.getControllerBE();boolean windowed = i > 0;
            if (owner == null || owner.getWidth() != (i == 3 ? 3 : 2) || owner.getHeight() != 2 || owner.formed() != (i == 3) || i == 3 && owner.sections() != 2)
                throw new IllegalStateException("Client vessel structure has not synchronized: " + i);
            int width = owner.getWidth();
            for (int y = 0; y < 2; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                if (!(mc.level.getBlockEntity(origin.offset(x, y, z)) instanceof LiddedVesselBlockEntity cell) || cell.windowsEnabled() != windowed)
                    throw new IllegalStateException("Client window flags disagree for vessel " + i);
                if (cell.getBlockState().getValue(LiddedVesselBlock.LADDER) != (x == 0 && z == 0))
                    throw new IllegalStateException("The ladder is not at the left end of the left wall: " + i);
            }
            for (int y = 0; y < 2; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                var pos = origin.offset(x, y, z);var state = mc.level.getBlockState(pos);
                boolean centre = width == 3 && y == 0 && x == 1 && z == 1;
                if (CETransport.LIDDED_VESSEL.get().hasShaftTowards(mc.level, pos, state, Direction.DOWN) != centre)
                    throw new IllegalStateException("Client shows an invalid bottom shaft at " + pos);
            }
            if (windowed) VesselClientCheck.verifyWindowQuads(mc, mc.level, owner);
            var liquid = owner.displayedFluid(7);
            if (!liquid.is(i == 2 ? CEProcessFluids.LIQUID_NITROGEN.get() : Fluids.WATER) || liquid.isEmpty()
                || owner.getTankInventory().getFluidAmount() != CONTENTS)
                throw new IllegalStateException("Client is missing the actual stored liquid for vessel " + i);
            if (!owner.displayedItem(0).is(Items.IRON_INGOT) || owner.displayedItem(0).getCount() != 8
                || !owner.displayedItem(1).is(Items.GOLD_INGOT) || owner.displayedItem(1).getCount() != 4)
                throw new IllegalStateException("Client ingredient inventory has not synchronized for vessel " + i);
            if (owner.mixing() || owner.rotationSpeed() != 0 || i == 2 && owner.frostLevel() <= .5f)
                throw new IllegalStateException("Client cold/static state is incorrect for vessel " + i);
            LoggerFactory.getLogger(VesselWorldClientCheck.class).info(
                "VESSEL WORLD CLIENT: index={} windows={} fluid={} physical={} items={}/{} temperature={} frost={}",
                i, owner.windowsEnabled(), liquid, owner.getTankInventory().getFluidAmount(),
                owner.displayedItem(0).getCount(), owner.displayedItem(1).getCount(), net.createplant.thermal.FluidThermal.kelvin(owner.getTankInventory().getFluid()), owner.frostLevel());
        }
    }
}
