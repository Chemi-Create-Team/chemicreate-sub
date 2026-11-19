package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.createplant.block.CoolingFanShroudBlock;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.entity.CoolingFanBlockEntity;
import net.createplant.block.entity.RadiatorBlockEntity;
import net.createplant.registry.CEProcessBlocks;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.slf4j.LoggerFactory;

/** Opt-in real-world audio lifecycle check, driven through a real motor and the synchronized bank controller. */
final class SoundClientCheck {
    private enum Stage { SETUP, STOPPED, STARTING, RUNNING, STOPPING, RESTART, MUTED, UNMUTED, AWAY, RETURN, UNLOADING, RELOADED, BROKEN,
        GAS_IDLE, GAS_RUNNING, GAS_STOPPED, CLEANUP }
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static Stage stage;
    private static BlockPos owner, fan, motor, gasSource, gasMotor;
    private static SoundInstance previous;
    private static int ticks;
    private static double blocksVolume, masterVolume;
    private static volatile boolean pending;
    private static volatile Throwable serverFailure;

    private SoundClientCheck() { }

    static void start(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("The sound check needs an integrated world with an audio device");
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        blocksVolume = mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).get();
        masterVolume = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1d);
        mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(1d);
        MachineSounds.clear(mc);
        owner = new BlockPos(0, mc.player.blockPosition().getY() + 2, 0);
        fan = owner.above(3);motor = owner.below();
        previous = null;serverFailure = null;RESULTS.clear();next(Stage.SETUP);
        server(mc, level -> {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 4; y++)
                level.setBlock(owner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++)
                level.setBlock(owner.offset(x, y, z), y < 3 ? CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState()
                    : x == 0 && z == 0 ? CEProcessBlocks.COOLING_FAN.get().defaultBlockState()
                    : CoolingFanShroudBlock.around(CEProcessBlocks.COOLING_FAN_SHROUD.get().defaultBlockState(), -x, -z), Block.UPDATE_ALL);
            var bank = (RadiatorBlockEntity) level.getBlockEntity(owner);
            bank.lazyTick();
            if (bank.owner() != bank || bank.fanCount() != 1) throw new IllegalStateException("The real fan bank did not form");
            var inlet = ((RadiatorBlockEntity) level.getBlockEntity(owner.above().north())).port(Direction.NORTH);
            if (inlet == null || inlet.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(), 1000), FluidAction.EXECUTE) != 1000)
                throw new IllegalStateException("The fan bank did not accept its finite water inventory");
            level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP), Block.UPDATE_ALL);
            ((CreativeMotorBlockEntity) level.getBlockEntity(motor)).generatedSpeed.setValue(0);
            teleport(mc, level, 0);
        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        try {
            if (serverFailure != null) throw new IllegalStateException("Sound fixture server action failed", serverFailure);
            if (mc.screen != null) mc.setScreen(null);
            if (++ticks > 600) throw new IllegalStateException("Sound lifecycle timed out at " + stage + ": loops=" + MachineSounds.activeCount());
            if (pending) return false;
            var bank = mc.level.getBlockEntity(owner) instanceof RadiatorBlockEntity be ? be : null;
            switch (stage) {
                case SETUP -> {
                    if (ticks < 30 || bank == null || bank.owner() != bank || bank.fanCount() != 1) return false;
                    for (var kind : MachineSoundSource.Kind.values()) {
                        var event = mc.getSoundManager().getSoundEvent(kind.event.get().getLocation());
                        if (event == null || event.getWeight() <= 0 || event.getSubtitle() == null)
                            throw new IllegalStateException("Unresolved machine sound or subtitle: " + kind);
                    }
                    RESULTS.put("registered_resources_and_subtitles", MachineSoundSource.Kind.values().length);
                    next(Stage.STOPPED);
                }
                case STOPPED -> {
                    if (ticks < 30) return false;
                    require(bank != null && bank.fanRpm() == 0 && !MachineSounds.contains(owner), "An unpowered bank must be silent");
                    RESULTS.put("stopped_silent", true);speed(mc, 64);next(Stage.STARTING);
                }
                case STARTING -> {
                    if (!playing(mc)) return false;
                    require(bank != null && bank.fanRpm() > 0 && mc.level.getBlockEntity(fan) instanceof CoolingFanBlockEntity drive
                        && Math.abs(drive.getSpeed()) > 0, "Audio must follow synchronized physical fan rotation");
                    previous = MachineSounds.instance(owner);RESULTS.put("sound_engine_started", previous.getLocation().toString());next(Stage.RUNNING);
                }
                case RUNNING -> {
                    require(MachineSounds.activeCount() == 1 && playing(mc) && MachineSounds.instance(owner) == previous,
                        "Repeated discovery must keep exactly one voice for the 27 radiator members and fan");
                    if (ticks < 80) return false;
                    RESULTS.put("one_voice_after_repeated_discovery", true);speed(mc, 0);next(Stage.STOPPING);
                }
                case STOPPING -> {
                    if (bank == null || bank.fanRpm() != 0 || !stopped(mc)) return false;
                    RESULTS.put("motor_stop_releases_engine", true);speed(mc, 96);next(Stage.RESTART);
                }
                case RESTART -> {
                    if (!playing(mc)) return false;
                    previous = MachineSounds.instance(owner);RESULTS.put("motor_restart", true);
                    mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(0d);next(Stage.MUTED);
                }
                case MUTED -> {
                    if (ticks < 10) return false;
                    // Vanilla retains isActive bookkeeping after a source is muted; its tickable must still stop and release our ownership.
                    require(!MachineSounds.contains(owner) && previous instanceof TickableSoundInstance sound && sound.isStopped(),
                        "The Blocks volume slider must stop the loop and clear its owner");
                    RESULTS.put("blocks_mute_stops_tickable", true);
                    mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(1d);next(Stage.UNMUTED);
                }
                case UNMUTED -> {
                    if (!playing(mc)) return false;
                    previous = MachineSounds.instance(owner);RESULTS.put("blocks_unmute_restarts_engine", true);
                    server(mc, level -> teleport(mc, level, 80));next(Stage.AWAY);
                }
                case AWAY -> {
                    if (ticks < 30 || mc.player.distanceToSqr(owner.getX(), owner.getY(), owner.getZ()) < 60 * 60 || !stopped(mc)) return false;
                    RESULTS.put("distance_culls_engine", true);server(mc, level -> teleport(mc, level, 0));next(Stage.RETURN);
                }
                case RETURN -> {
                    if (!playing(mc)) return false;
                    previous = MachineSounds.instance(owner);RESULTS.put("return_restarts_engine", true);
                    server(mc, level -> teleport(mc, level, 640));next(Stage.UNLOADING);
                }
                case UNLOADING -> {
                    if (ticks < 30 || mc.level.getChunkSource().getChunk(owner.getX() >> 4, owner.getZ() >> 4, ChunkStatus.FULL, false) != null
                        || !stopped(mc)) return false;
                    RESULTS.put("actual_chunk_unload_silent", true);server(mc, level -> teleport(mc, level, 0));next(Stage.RELOADED);
                }
                case RELOADED -> {
                    if (!playing(mc)) return false;
                    previous = MachineSounds.instance(owner);RESULTS.put("actual_chunk_reload_restarts_engine", true);
                    server(mc, level -> level.setBlock(fan.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));next(Stage.BROKEN);
                }
                case BROKEN -> {
                    if (ticks < 20 || !stopped(mc)) return false;
                    RESULTS.put("broken_structure_releases_engine", true);
                    MachineSounds.clear(mc);
                    require(MachineSounds.activeCount() == 0, "Explicit client cleanup must empty every owned loop");
                    gasSource = owner.south(7).west(3);gasMotor = gasSource.south();
                    server(mc, level -> {
                        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 3; y++)
                            level.setBlock(owner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        gasLine(level);
                    });
                    next(Stage.GAS_IDLE);
                }
                case GAS_IDLE -> {
                    if (ticks < 30 || !(mc.level.getBlockEntity(gasSource) instanceof FluidTankBlockEntity)) return false;
                    require(MachineSounds.activeCount() == 0, "Stored high-pressure gas stays silent with the pump off");
                    server(mc, level -> ((CreativeMotorBlockEntity) level.getBlockEntity(gasMotor)).generatedSpeed.setValue(64));
                    next(Stage.GAS_RUNNING);
                }
                case GAS_RUNNING -> {
                    SoundInstance gasSound = null;
                    for (int i = 2; i <= 5; i++) {
                        var instance = MachineSounds.instance(gasSource.east(i));
                        if (instance == null || !mc.getSoundManager().isActive(instance)) continue;
                        require(gasSound == null, "An adjacent run of pipe must share one audible hiss");
                        gasSound = instance;
                    }
                    var pumpSound = MachineSounds.instance(gasSource.east());
                    if (ticks < 45 || gasSound == null || pumpSound == null || !mc.getSoundManager().isActive(pumpSound)) return false;
                    require(gasSound.getLocation().equals(MachineSoundSource.Kind.HIGH_PRESSURE_GAS.event.get().getLocation()),
                        "Moving high-pressure gas uses the dedicated whistle");
                    require(pumpSound.getLocation().equals(MachineSoundSource.Kind.MATERIAL_PUMP.event.get().getLocation())
                        && Math.abs(pumpSound.getPitch() - .73f) < .02f, "The gas pump plays its own motor loop at the pitch of the large pump");
                    previous = gasSound;
                    RESULTS.put("high_pressure_pipe_sound_engine_started", gasSound.getLocation().toString());
                    RESULTS.put("one_voice_along_pipe", true);
                    RESULTS.put("mechanical_pump_pitch", pumpSound.getPitch());
                    server(mc, level -> ((CreativeMotorBlockEntity) level.getBlockEntity(gasMotor)).generatedSpeed.setValue(0));
                    next(Stage.GAS_STOPPED);
                }
                case GAS_STOPPED -> {
                    if (ticks < 12 || MachineSounds.activeCount() != 0 || mc.getSoundManager().isActive(previous)) return false;
                    RESULTS.put("high_pressure_pipe_sound_engine_stopped", true);
                    server(mc, level -> {
                        for (int i = 0; i <= 6; i++) level.removeBlock(gasSource.east(i), false);
                        level.removeBlock(gasMotor, false);
                        level.removeBlock(gasMotor.east(), false);
                    });
                    next(Stage.CLEANUP);
                }
                case CLEANUP -> {
                    if (ticks < 10) return false;
                    RESULTS.put("explicit_client_cleanup", true);RESULTS.put("maximum_voices", MachineSounds.MAX_LOOPS);
                    RESULTS.put("audible_radius_blocks", MachineSounds.RANGE);RESULTS.put("status", "PASS");
                    Files.writeString(mc.gameDirectory.toPath().resolve("machine-sound-client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
                    restore(mc);
                    LoggerFactory.getLogger(SoundClientCheck.class).info("MACHINE SOUND CLIENT: PASS {}", RESULTS);
                    return true;
                }
            }
            return false;
        } catch (Exception failure) { restore(mc);throw failure; }
    }

    private static void gasLine(ServerLevel level) {
        var storage = CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X);
        level.setBlock(gasSource, storage, Block.UPDATE_ALL);
        level.setBlock(gasSource.east(6), storage, Block.UPDATE_ALL);
        for (int i = 2; i <= 5; i++) {
            var pipe = CETransport.NICKEL_ALLOY_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true);
            level.setBlock(gasSource.east(i), pipe, Block.UPDATE_ALL);
        }
        level.setBlock(gasSource.east(), CETransport.MATERIAL_PUMPS.getLast().get().defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(gasMotor.east(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        level.setBlock(gasMotor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        ((CreativeMotorBlockEntity) level.getBlockEntity(gasMotor)).generatedSpeed.setValue(0);
        var storageEntity = (FluidTankBlockEntity) level.getBlockEntity(gasSource);
        require(storageEntity.getTankInventory().fill(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(), 16000), FluidAction.EXECUTE) >= 6000,
            "The source holds a finite gas supply long enough to confirm audio");
    }

    private static boolean playing(Minecraft mc) {
        var instance = MachineSounds.instance(owner);
        return instance != null && mc.getSoundManager().isActive(instance);
    }

    private static boolean stopped(Minecraft mc) {
        return !MachineSounds.contains(owner) && (previous == null || !mc.getSoundManager().isActive(previous));
    }

    private static void speed(Minecraft mc, int rpm) {
        server(mc, level -> ((CreativeMotorBlockEntity) level.getBlockEntity(motor)).generatedSpeed.setValue(rpm));
    }

    private static void server(Minecraft mc, Consumer<ServerLevel> action) {
        pending = true;
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().overworld()); }
            catch (Throwable failure) { serverFailure = failure; }
            finally { pending = false; }
        });
    }

    private static void teleport(Minecraft mc, ServerLevel level, int distance) {
        var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());
        if (player == null) throw new IllegalStateException("Missing sound fixture player");
        player.getAbilities().flying = true;player.onUpdateAbilities();
        player.connection.teleport(owner.getX() + distance + .5, owner.getY() + 2, owner.getZ() + 8, 180, 15);
    }

    private static void next(Stage next) { stage = next;ticks = 0; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void restore(Minecraft mc) {
        MachineSounds.clear(mc);
        mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(blocksVolume);
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(masterVolume);
    }
}
