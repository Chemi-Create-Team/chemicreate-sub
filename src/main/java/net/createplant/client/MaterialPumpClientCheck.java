package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native world and item-model fixture, inactive during normal play. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class MaterialPumpClientCheck {
    private static final BlockPos REFERENCE = new BlockPos(20, 80, 5);
    private static int phase, ticks;

    private MaterialPumpClientCheck() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.materialPumpClientCheck")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("material-pumps-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Material pump check", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;ticks = 0;mc.setScreen(null);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for (int x = -3; x <= 23; x++) for (int z = -6; z <= 8; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                for (int i = 0; i < CETransport.MATERIAL_PUMPS.size(); i++) {
                    var pump = CETransport.MATERIAL_PUMPS.get(i);
                    BlockPos pos = new BlockPos((i % 5) * 4, 80, (i / 5) * 5);
                    String pipeId = pump.getId().getPath().replace("_pump", "_pipe");
                    var pipe = BuiltInRegistries.BLOCK.get(CreatePlant.location(pipeId)).defaultBlockState()
                        .setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true)
                        .setValue(FluidPipeBlock.NORTH, false).setValue(FluidPipeBlock.SOUTH, false)
                        .setValue(FluidPipeBlock.UP, false).setValue(FluidPipeBlock.DOWN, false);
                    level.setBlockAndUpdate(pos.west(), pipe);level.setBlockAndUpdate(pos.east(), pipe);
                    level.setBlockAndUpdate(pos, pump.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
                    level.setBlockAndUpdate(pos.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                    level.setBlockAndUpdate(pos.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
                    ((CreativeMotorBlockEntity) level.getBlockEntity(pos.south().west())).generatedSpeed.setValue(32);
                }
                level.setBlockAndUpdate(REFERENCE, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(REFERENCE.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                level.setBlockAndUpdate(REFERENCE.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
                ((CreativeMotorBlockEntity) level.getBlockEntity(REFERENCE.south().west())).generatedSpeed.setValue(32);
                for (Direction direction : Direction.values())
                    level.setBlockAndUpdate(new BlockPos(direction.ordinal() * 3, 80, -4),
                        CETransport.MATERIAL_PUMPS.get(0).get().defaultBlockState().setValue(PumpBlock.FACING, direction));
                player.getInventory().clearContent();player.getAbilities().flying = true;player.onUpdateAbilities();
                if (Boolean.getBoolean("createplant.materialPumpCloseup")) player.connection.teleport(17.5, 82.5, 10, 180, 20);
                else player.connection.teleport(8.5, 88, 17, 180, 28);
            });
        }
        if (phase == 2 && ++ticks == 120) {
            verifyVisuals(mc);
            org.slf4j.LoggerFactory.getLogger(MaterialPumpClientCheck.class).info("MATERIAL PUMP WORLD READY");
        }
        if (phase == 2 && ticks == Integer.getInteger("createplant.materialPumpStopAt", -1))
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                for (int i = 0; i < CETransport.MATERIAL_PUMPS.size(); i++) {
                    var motor = (CreativeMotorBlockEntity) level.getBlockEntity(new BlockPos((i % 5) * 4 - 1, 80, (i / 5) * 5 + 1));
                    motor.generatedSpeed.setValue(0);
                }
                ((CreativeMotorBlockEntity) level.getBlockEntity(REFERENCE.south().west())).generatedSpeed.setValue(0);
            });
        if (phase == 2 && ticks > Integer.getInteger("createplant.materialPumpWorldTicks", 1000)) {
            phase = 3;showIcons(mc);
        }
    }

    private static void verifyVisuals(Minecraft mc) {
        boolean active = dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(mc.level);
        if (active) {
            var manager = (dev.engine_room.flywheel.impl.visualization.VisualManagerImpl<?, ?>)
                dev.engine_room.flywheel.api.visualization.VisualizationManager.getOrThrow(mc.level).blockEntities();
            var storage = (dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage) manager.getStorage();
            var positions = new ArrayList<BlockPos>();
            for (int i = 0; i < CETransport.MATERIAL_PUMPS.size(); i++) positions.add(new BlockPos((i % 5) * 4, 80, (i / 5) * 5));
            for (Direction direction : Direction.values()) positions.add(new BlockPos(direction.ordinal() * 3, 80, -4));
            positions.add(REFERENCE);
            for (BlockPos pos : positions)
                if (!(storage.visualAtPos(pos.asLong()) instanceof com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual<?>))
                    throw new IllegalStateException("Missing pump cog visual at " + pos);
        }
        org.slf4j.LoggerFactory.getLogger(MaterialPumpClientCheck.class).info("MATERIAL PUMP VISUAL CHECK PASSED: backend={}, 10 materials, 6 facings, native reference", active);
    }

    private static void showIcons(Minecraft mc) {
        List<Item> items = new ArrayList<>();
        CETransport.MATERIAL_PUMPS.forEach(pump -> items.add(pump.asItem()));
        for (String id : List.of("condenser_shell", "condenser_tube_bundle", "condenser_waterbox", "condenser_cooling_inlet",
            "condenser_cooling_outlet", "process_transmitter", "control_valve_actuator", "pid_controller", "pid_instrument_rack",
            "pulse_extraction_column", "extraction_column_shell", "extraction_pulse_plate", "extraction_column_outlet",
            "vitrification_melter", "vitrification_refractory_shell", "vitrification_electrode", "empty_waste_canister",
            "vitrified_waste_canister", "cryogenic_cold_box_drive", "cryogenic_regenerator", "horizontal_fluid_tank"))
            items.add(BuiltInRegistries.ITEM.get(CreatePlant.location(id)));
        var inventory = new SimpleContainer(54);
        for (int i = 0; i < items.size(); i++) {
            var stack = items.get(i).getDefaultInstance();
            if (stack.isEmpty() || mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0) == mc.getModelManager().getMissingModel())
                throw new IllegalStateException("Missing inventory model: " + items.get(i));
            inventory.setItem(i + 9, stack);
        }
        var menu = ChestMenu.sixRows(123, mc.player.getInventory(), inventory);
        mc.player.containerMenu = menu;
        mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal("ポンプ・設備の外観")));
        org.slf4j.LoggerFactory.getLogger(MaterialPumpClientCheck.class).info("MATERIAL PUMP ITEM MODELS READY: {}", items.size());
    }
}
