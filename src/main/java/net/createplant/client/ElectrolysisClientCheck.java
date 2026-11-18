package net.createplant.client;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Opt-in integrated-world fixture. All material and circuit mutations run on the server thread. */
final class ElectrolysisClientCheck {
    private static final BlockPos MINIMUM = new BlockPos(0, 80, 0), OWNER = MINIMUM.north(), MOTOR = MINIMUM.offset(0, 2, 7);
    private static final Map<String, Object> readings = new LinkedHashMap<>();
    private static volatile Throwable failure;
    private static volatile boolean built, wired;
    private static int ticks, fov;
    private static boolean hideGui;
    private static boolean dampedNeedle, dampedLiquid;
    private static volatile int purged, returned;
    private static FluidStack purgedFeed = FluidStack.EMPTY;

    static void start(Minecraft mc) {
        ticks = 0;built = false;wired = false;failure = null;readings.clear();dampedNeedle = dampedLiquid = false;purged = returned = 0;purgedFeed = FluidStack.EMPTY;
        fov = mc.options.fov().get();hideGui = mc.options.hideGui;
        mc.options.fov().set(55);mc.options.hideGui = true;mc.options.pauseOnLostFocus = false;mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var level = server.overworld();level.setDayTime(6000);
                for (int x = -4; x <= 5; x++) for (int z = -3; z <= 10; z++) level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int z = 0; z < 8; z++) {
                    level.setBlockAndUpdate(MINIMUM.offset(0, 0, z), CEElectrolysis.CELL.get().defaultBlockState());
                    level.setBlockAndUpdate(MINIMUM.offset(0, 1, z), CEElectrolysis.HEAD.get().defaultBlockState());
                }
                for (int y = 0; y < 2; y++) level.setBlockAndUpdate(MINIMUM.offset(0, y, -1), CEElectrolysis.CONTROLLER.get().defaultBlockState());
                level.setBlockAndUpdate(MOTOR, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.DOWN));
                var comparison = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "fluid_tank"));
                for (int x = 4; x < 6; x++) for (int z = 3; z < 5; z++) for (int y = 0; y < 2; y++) level.setBlockAndUpdate(MINIMUM.offset(x, y, z), comparison.defaultBlockState());
                var owner = (ElectrolysisControllerBlockEntity) level.getBlockEntity(OWNER);
                if (!owner.validate() || owner.cells().size() != 8) throw new IllegalStateException("World fixture must form an eight-cell row");
                for (var head : owner.heads()) head.electrodes.insertItem(0, CEElectrolysis.ELECTRODE_PLATE.toStack(8), false);
                owner.cells().getFirst().port(Direction.DOWN).fill(new FluidStack(Fluids.WATER, 26000), EXECUTE);
                owner.setRunning(true);
                level.setBlockAndUpdate(MINIMUM.offset(-3, 3, -1), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery")).defaultBlockState());
                pose(mc, -7, 84, -8, -35, 20);built = true;
            } catch (Throwable error) { failure = error; }
        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException("Electrolysis world fixture", failure);
        ticks++;
        if (ticks == 35 && built) mc.getSingleplayerServer().execute(() -> {
            try {
                var level = mc.getSingleplayerServer().overworld();var battery = MINIMUM.offset(-3, 3, -1);
                var source = DevicesSavedData.load(level).getDevice(battery, CreativeBatteryDevice.class);
                source.voltage = 48;source.acFrequency = 0;
                ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) level.getBlockEntity(MOTOR)).generatedSpeed.setValue(64);
                var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
                var wires = InfrastructureSavedData.load(level);
                for (int node = 0; node < 2; node++) wires.connect(new InWorldNode(node, battery), new InWorldNode(node, MINIMUM.above()), copper);
                wired = true;
            } catch (Throwable error) { failure = error; }
        });
        if (ticks == 110) {
            if (!wired || !(mc.level.getBlockEntity(OWNER) instanceof ElectrolysisControllerBlockEntity owner) || !owner.formed()
                || owner.volts() < 45 || owner.watts() < 1600 || owner.running() < 1 || owner.running() > 2 || owner.stored(1) <= 0)
                throw new IllegalStateException("Real CEE power and synchronized fluids must reach the client: " + mc.level.getBlockEntity(OWNER));
            readings.put("volts", owner.volts());readings.put("watts", owner.watts());readings.put("cyclesPerTick", owner.running());
            readings.put("water", owner.stored(0));readings.put("hydrogen", owner.stored(1));readings.put("oxygen", owner.stored(2));
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: overview");
        }
        if (ticks == 130) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);
            purgedFeed = owner.cells().getFirst().port(Direction.DOWN).drain(16000, EXECUTE);purged = purgedFeed.getAmount();
        });
        if (ticks >= 130 && ticks < 195 && mc.level.getBlockEntity(OWNER) instanceof ElectrolysisControllerBlockEntity owner) {
            float target = owner.stored(0) / 32000f;
            float shown = ElectrolysisDisplayState.sample(owner, net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(mc.level)).value(ElectrolysisDisplayState.FEED);
            if (target < .4f && shown > target + .01f && shown < .85f) dampedLiquid = true;
        }
        if (ticks == 200 && (purged != 16000 || !dampedLiquid)) throw new IllegalStateException("Real inventory change must produce intermediate rendered liquid levels");
        if (ticks == 200) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);
            returned = owner.cells().getFirst().port(Direction.DOWN).fill(purgedFeed, EXECUTE);purgedFeed = FluidStack.EMPTY;
        });
        if (ticks == 220) mc.getSingleplayerServer().execute(() -> {
            pose(mc, -4, 80.2, 3.5, -90, 12);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: windows");
        });
        if (ticks == 420) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);owner.heads().getFirst().lower(false);
            pose(mc, -3.5, 84, -3, -35, 30);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: raised head");
        });
        if (ticks == 480) {
            var owner = (ElectrolysisControllerBlockEntity) mc.level.getBlockEntity(OWNER);
            if (owner.running() != 0 || owner.watts() != 0 || !owner.status().equals("raised")) throw new IllegalStateException("Raising a real electrode head must stop current and bubbles");
            readings.put("headInterlock", true);readings.put("formedCells", owner.cells().size());
        }
        if (ticks >= 420 && ticks < 480 && mc.level.getBlockEntity(OWNER) instanceof ElectrolysisControllerBlockEntity owner && owner.watts() == 0) {
            float shown = ElectrolysisDisplayState.sample(owner, net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(mc.level)).value(ElectrolysisDisplayState.AMPS);
            if (shown > .0001f && shown < .13f) dampedNeedle = true;
        }
        if (ticks == 550) mc.getSingleplayerServer().execute(() -> {
            pose(mc, .5, 82, 1.5, 180, 65);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: inspection");
        });
        if (ticks == 650) mc.options.fov().set(40);
        if (ticks == 650) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);owner.heads().getFirst().lower(true);
            pose(mc, 1, 79.4, -2.6, 0, 0);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: panel");
        });
        if (ticks == 740) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);owner.selectLoad(37);
        });
        if (ticks == 800) {
            var owner = (ElectrolysisControllerBlockEntity) mc.level.getBlockEntity(OWNER);
            if (owner.limit() != 37 || owner.watts() <= 0 || owner.watts() >= (double) readings.get("watts") * .6)
                throw new IllegalStateException("The 37 percent dial setting must reduce the real CEE load");
            readings.put("selectedLoad", owner.limit());readings.put("selectedWatts", owner.watts());
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: load");
        }
        if (ticks == 900) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);owner.selectLoad(100);
        });
        if (ticks == 940) mc.getSingleplayerServer().execute(() -> {
            mc.getSingleplayerServer().overworld().setDayTime(18000);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: night");
        });
        if (ticks == 1080) mc.getSingleplayerServer().execute(() -> {
            var owner = (ElectrolysisControllerBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(OWNER);owner.setRunning(false);
            org.slf4j.LoggerFactory.getLogger(ElectrolysisClientCheck.class).info("ELECTROLYSIS CAPTURE: off");
        });
        if (ticks < 1200) return false;
        var owner = (ElectrolysisControllerBlockEntity) mc.level.getBlockEntity(OWNER);
        if (returned != 16000 || !dampedNeedle || owner.watts() != 0 || owner.enabled()) throw new IllegalStateException("The inspection feed must be returned, needles must decay smoothly and the stopped cell must have no electrical supply");
        readings.put("dampedLiquidObserved", dampedLiquid);readings.put("dampedNeedleObserved", dampedNeedle);readings.put("fixturePurged_mB", purged);readings.put("fixtureReturned_mB", returned);
        readings.put("unpoweredAtEnd", true);
        Files.writeString(mc.gameDirectory.toPath().resolve("electrolysis-world-quality.json"), new GsonBuilder().setPrettyPrinting().create().toJson(readings));
        mc.options.fov().set(fov);mc.options.hideGui = hideGui;
        return true;
    }

    private static void pose(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.getAbilities().flying = true;player.onUpdateAbilities();player.connection.teleport(x, y, z, yaw, pitch);
    }
}
