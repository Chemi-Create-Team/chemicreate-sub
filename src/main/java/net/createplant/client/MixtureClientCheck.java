package net.createplant.client;

import java.nio.file.Files;
import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.mixture.*;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Disposable real-world fixture; P starts its finite pump, and P again opens the authored lesson. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class MixtureClientCheck {
    private static int phase, ticks;
    private static volatile boolean built, transferred;
    private static boolean reportWritten;
    private static final BlockPos SOURCE = new BlockPos(0, 80, 0), TARGET = new BlockPos(8, 80, 0), MOTOR = new BlockPos(3, 80, 1);
    private static final MixtureContents EXPECTED = new MixtureContents(Map.of(ResourceLocation.withDefaultNamespace("water"), 2500,
        CreatePlant.location("antifreeze"), 2500, ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "ethanol"), 2500,
        ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "plant_oil"), 2500));
    private MixtureClientCheck() { }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!Boolean.getBoolean("createplant.mixtureClientCheck") || event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS || event.getKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_P) return;
        var mc = Minecraft.getInstance();if (mc.screen != null || !built) return;
        if (phase == 2) {
            mc.getSingleplayerServer().execute(() -> ((CreativeMotorBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(MOTOR)).generatedSpeed.setValue(64));phase = 3;
        } else if (phase == 4 && reportWritten) phase = 5;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.mixtureClientCheck")) return;
        var mc = Minecraft.getInstance();if (mc.getOverlay() != null) return;
        if (phase == 5) { ClientQualityCheck.tick(event);return; }
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("mixture-check-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Mixture check", net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(7112026L, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.level != null && mc.player != null) {
            phase = 2;ticks = 0;mc.setScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                for (int x = -3; x <= 12; x++) for (int z = -3; z <= 5; z++) level.setBlock(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setBlock(SOURCE.offset(x, y, z), CETransport.LIDDED_VESSEL.get().defaultBlockState(), 3);
                var vessel = (LiddedVesselBlockEntity) level.getBlockEntity(SOURCE);ConnectivityHandler.formMulti(vessel);vessel = vessel.getControllerBE();vessel.toggleWindows();
                for (var entry : EXPECTED.amounts().entrySet()) vessel.getTankInventory().fill(new FluidStack(BuiltInRegistries.FLUID.get(entry.getKey()), entry.getValue()), EXECUTE);
                for (int y = 0; y < 2; y++) level.setBlock(TARGET.above(y), AllBlocks.FLUID_TANK.getDefaultState(), 3);
                ConnectivityHandler.formMulti((FluidTankBlockEntity) level.getBlockEntity(TARGET));
                for (int x = 2; x <= 7; x++) level.setBlock(new BlockPos(x, 80, 0), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true), 3);
                level.setBlock(new BlockPos(4, 80, 0), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), 3);
                level.setBlock(MOTOR.east(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);level.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
                ((CreativeMotorBlockEntity) level.getBlockEntity(MOTOR)).generatedSpeed.setValue(0);
                var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.getAbilities().flying = true;player.onUpdateAbilities();player.connection.teleport(4, 83, -10, 0, 10);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(CETransport.LIDDED_VESSEL.get()));
                vessel.sendData();built = true;
                org.slf4j.LoggerFactory.getLogger(MixtureClientCheck.class).info("MIXTURE FIXTURE READY: finite 10000 mB; P starts the native pump");
            });
        }
        if (phase == 3 && built && ++ticks % 10 == 0) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();var from = ((LiddedVesselBlockEntity) level.getBlockEntity(SOURCE)).getControllerBE();var to = ((FluidTankBlockEntity) level.getBlockEntity(TARGET)).getControllerBE();
            if (from.getTankInventory().isEmpty() && MixtureHelper.extractContents(to.getTankInventory().getFluid()).equals(EXPECTED)) transferred = true;
        });
        if (phase == 3 && transferred) phase = 4;
        if (phase == 4 && !reportWritten && mc.level.getBlockEntity(TARGET) instanceof FluidTankBlockEntity tank) {
            var fluid = tank.getControllerBE().getTankInventory().getFluid();if (!MixtureHelper.isMixture(fluid) || !MixtureHelper.extractContents(fluid).equals(EXPECTED)) return;
            var lines = new ArrayList<net.minecraft.network.chat.Component>();MixtureGoggleHelper.appendSummary(lines, fluid);
            if (MixtureGoggleHelper.describe(fluid).size() != 5) throw new IllegalStateException("Composition display lost constituents");
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("mixture-client-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("nativeTransport", "passed", "clientSync", "passed", "components", EXPECTED.amounts(), "tint", Integer.toHexString(MixtureClient.tint(fluid)), "goggles", lines.stream().map(net.minecraft.network.chat.Component::getString).toList(), "fullComposition", MixtureGoggleHelper.describe(fluid).stream().map(net.minecraft.network.chat.Component::getString).toList())));
                reportWritten = true;org.slf4j.LoggerFactory.getLogger(MixtureClientCheck.class).info("MIXTURE CLIENT PASSED: P opens the Ponder");
            } catch (java.io.IOException error) { throw new RuntimeException(error); }
        }
    }
}
