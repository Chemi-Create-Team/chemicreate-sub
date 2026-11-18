package net.createplant.client;

import com.simibubi.create.AllBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.block.FuelCycleStructure;
import net.createplant.block.FusionConsolePanelBlock;
import net.createplant.block.FusionControlConsoleBlock;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.FusionMotorGeneratorBlock;
import net.createplant.block.FusionReactorControllerBlock;
import net.createplant.block.ProcessControlBlock;
import net.createplant.block.entity.CoolingTowerBlockEntity;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.CECrane;
import net.createplant.registry.CEFuelCycle;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CEHeating;
import net.createplant.registry.CEPlantSystems;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Opt-in disposable integrated-server gallery. Screenshots are taken through the desktop, not a synthetic renderer. */
public final class PowerPlantClientCheck {
    private record View(double x, double y, double z, double lx, double ly, double lz, int fov) { }
    private record Sample(BlockPos pos, BlockState state) { }
    private static final List<Sample> FUSION_SAMPLES = new ArrayList<>();
    private static final View[] VIEWS = {
        new View(-42, 32, -48, 0, 17, 0, 55), new View(45, 9, -46, 0, 5, 0, 65),
        new View(79, 17, -15, 57, 4, 10, 55), new View(54, 3.2, -4, 54, 1.4, 1, 50),
        new View(94, 24, 19, 70, 6, 44, 50), new View(72, 7, 0, 62, 2, 11, 55),
        new View(-14, 20, 110, 0, 4, 95, 42), new View(-13, 9, 111, 0, 4, 95, 42),
        new View(26, 8, 103, 30, 1.5, 122, 42), new View(62, 10, 110, 63, .5, 122, 46), new View(15, 4, 114, 18.5, 1.3, 123, 48)
    };
    private static final Path VIEW_FILE = Path.of("/workspace/outputs/power-plant-view.txt");
    private static int phase, ticks, view = -1;
    private static BlockPos origin;
    private static volatile boolean prepared;
    private PowerPlantClientCheck() { }
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null && ++ticks > 80) {
            phase = 1;ticks = 0;
            mc.createWorldOpenFlows().createFreshLevel("power-plant-gallery-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Power plant gallery", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.level != null && mc.player != null && mc.getOverlay() == null && ++ticks > 100) {
            phase = 2;ticks = 0;mc.options.pauseOnLostFocus = false;mc.options.hideGui = true;mc.options.renderDistance().set(16);
            origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 0);mc.getSingleplayerServer().execute(() -> prepare(mc));
        }
        if (phase != 2 || !prepared || mc.player == null) return;
        if (mc.screen != null) mc.setScreen(null);
        if (++ticks % 20 == 0) {
            try {
                int requested = Files.exists(VIEW_FILE) ? Integer.parseInt(Files.readString(VIEW_FILE).trim()) : 0;
                requested = Math.clamp(requested, 0, VIEWS.length - 1);
                if (requested != view) { view = requested;move(mc, VIEWS[view]); }
            } catch (Exception exception) { throw new IllegalStateException("Gallery view control", exception); }
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                if (level.getBlockEntity(origin.offset(0, 1, -CoolingTowerStructure.RADIUS)) instanceof CoolingTowerBlockEntity tower && tower.formed()) {
                    tower.tank(0).fill(FluidThermal.at(new FluidStack(Fluids.WATER, 1_000_000), 310.15), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    tower.tank(1).drain(Integer.MAX_VALUE, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    tower.tank(2).fill(FluidThermal.at(new FluidStack(Fluids.WATER, 100_000), 293.15), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                }
            });
        }
        if (view >= 8) for (Sample sample : FUSION_SAMPLES)
            if (mc.level.getBlockState(sample.pos()) != sample.state()) mc.level.setBlock(sample.pos(), sample.state(), Block.UPDATE_CLIENTS);
        if (view >= 0) {
            float[] angles = angles(VIEWS[view]);mc.player.setYRot(angles[0]);mc.player.setXRot(angles[1]);mc.player.yRotO = angles[0];mc.player.xRotO = angles[1];
        }
    }
    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();var level = server.overworld();
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        for (int x = -35; x <= 92; x++) for (int z = -35; z <= 79; z++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int x = -7; x <= 7; x++) for (int z = 88; z <= 102; z++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (var cell : CoolingTowerStructure.cells()) if (cell.role() != CoolingTowerStructure.Role.CLEAR)
            level.setBlock(origin.offset(cell.offset()), cell.block().defaultBlockState(), 2);
        for (var cell : FuelCycleStructure.cells(origin.offset(49, 0, 8), Direction.NORTH, false)) level.setBlock(cell.pos(), cell.state(), 2);
        for (var cell : FuelCycleStructure.cells(origin.offset(62, 0, 8), Direction.NORTH, true)) level.setBlock(cell.pos(), cell.state(), 2);
        for (int i = 0; i < 3; i++) level.setBlock(origin.offset(67, i, 8), CEFuelCycle.WASTE_CANISTER.get().defaultBlockState().setValue(net.createplant.block.VitrificationCanisterBlock.SECTION, i), 2);
        level.setBlock(origin.offset(53, 0, 1), CEPlantSystems.PID_CONTROLLER.get().defaultBlockState(), 2);
        level.setBlock(origin.offset(53, 1, 1), CEPlantSystems.PID_RACK.get().defaultBlockState(), 2);
        level.setBlock(origin.offset(55, 0, 1), CEHeating.REGULATOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 2);
        level.setBlock(origin.offset(55, 1, 1), CEPlantSystems.VALVE_ACTUATOR.get().defaultBlockState().setValue(ProcessControlBlock.FACING, Direction.UP), 2);
        level.setBlock(origin.offset(56, 1, 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        for (int y = 0; y < 3; y++) level.setBlock(origin.offset(50, y, 1), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(origin.offset(50, 1, 0), CEPlantSystems.LEVEL_TRANSMITTER.get().defaultBlockState().setValue(ProcessControlBlock.FACING, Direction.NORTH), 2);

        for (var cell : FusionStructure.cells(origin.offset(0, net.createplant.fusion.FusionPhysics.MIDPLANE, 90), Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) level.setBlock(cell.pos(), cell.expected(), 2);
        BlockPos reactor = origin.offset(70, net.createplant.fusion.FusionPhysics.MIDPLANE, 40);
        for (var cell : FusionStructure.cells(reactor, Direction.SOUTH)) if (cell.role() != FusionStructure.Role.CLEAR) level.setBlock(cell.pos(), cell.expected(), 2);
        var controller = (FusionReactorControllerBlockEntity)level.getBlockEntity(reactor);
        ItemStack blanket = new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), divertor = new ItemStack(CEFusion.DIVERTOR_CASSETTE.get());
        blanket.setDamageValue(12000);divertor.setDamageValue(8000);controller.blanketPort().insertItem(0, blanket, false);controller.blanketPort().insertItem(1, divertor, false);
        for (int x : new int[]{63, 77}) for (int z : new int[]{37, 51}) for (int y = 0; y < 14; y++) level.setBlock(origin.offset(x, y, z), CraneFrame.girder(level, origin.offset(x, y, z), Direction.Axis.Y), 2);
        for (int z : new int[]{37, 51}) for (int x = 63; x <= 77; x++) level.setBlock(origin.offset(x, 14, z), CraneFrame.girder(level, origin.offset(x, 14, z), Direction.Axis.X), 3);
        BlockPos drive = origin.offset(62, 14, 37);level.setBlock(drive, CECrane.FUSION_DRIVE.get().defaultBlockState().setValue(CraneDriveBlock.FACING, Direction.EAST).setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        level.setBlock(drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);level.setBlock(drive.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        var crane = (CraneDriveBlockEntity)level.getBlockEntity(drive);
        crane.size(15, 13);
        ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(drive.west())).generatedSpeed.setValue(128);
        prepareFusionSamples(level);
        prepared = true;
    }
    private static void prepareFusionSamples(ServerLevel level) {
        FUSION_SAMPLES.clear();
        for (int x = 18; x <= 42; x++) for (int z = 117; z <= 127; z++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int x = 52; x <= 75; x++) for (int z = 118; z <= 127; z++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        for (int x = 17; x <= 19; x++) for (int y = 0; y < 3; y++)
            placeSample(level, origin.offset(x, y, 123), net.createplant.registry.CEBlocks.DIORITE_CASING.get().defaultBlockState());
        placeSample(level, origin.offset(21, 0, 123), net.createplant.registry.CEBlocks.DIORITE_CASING.get().defaultBlockState());
        BlockState[] materials = {
            AllBlocks.ANDESITE_CASING.getDefaultState(), AllBlocks.BRASS_CASING.getDefaultState(),
            AllBlocks.COPPER_CASING.getDefaultState(), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(),
            CEFusion.REINFORCED_CASING.get().defaultBlockState(), CEFusion.TOROIDAL_FIELD_COIL.get().defaultBlockState(),
            CEFusion.CENTRAL_SOLENOID.get().defaultBlockState(), CEFusion.VACUUM_VESSEL.get().defaultBlockState(),
            CEFusion.BREEDING_BLANKET.get().defaultBlockState(), CEFusion.DIVERTOR.get().defaultBlockState(),
            CEFusion.VIEWPORT.get().defaultBlockState()
        };
        for (int i = 0; i < materials.length; i++) {
            BlockPos sample = origin.offset(20 + i * 2, 2, 124);
            level.setBlock(sample.below(2), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            level.setBlock(sample.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            placeSample(level, sample, materials[i]);
        }
        BlockState[] ports = {
            CEFusion.DEUTERIUM_PORT.get().defaultBlockState(), CEFusion.TRITIUM_PORT.get().defaultBlockState(),
            CEFusion.ASH_PORT.get().defaultBlockState(), CEFusion.HELIUM_INLET.get().defaultBlockState(),
            CEFusion.HELIUM_OUTLET.get().defaultBlockState(), CEFusion.COOLANT_INLET.get().defaultBlockState(),
            CEFusion.COOLANT_OUTLET.get().defaultBlockState()
        };
        for (int i = 0; i < ports.length; i++)
            placeSample(level, origin.offset(22 + i * 2, 0, 120), ports[i].setValue(FusionFluidPortBlock.FACING, Direction.NORTH));
        for (int active = 0; active < 2; active++) {
            placeSample(level, origin.offset(36 + active * 2, 0, 120), CEFusion.CONTROLLER.get().defaultBlockState()
                .setValue(FusionReactorControllerBlock.FACING, Direction.NORTH).setValue(FusionReactorControllerBlock.ACTIVE, active == 1));
            for (int depth = 0; depth < 2; depth++) for (int column = 0; column < 3; column++) {
                BlockState panel = column == 1 && depth == 0 ? CEFusion.CONSOLE.get().defaultBlockState()
                    .setValue(FusionControlConsoleBlock.FACING, Direction.NORTH).setValue(FusionControlConsoleBlock.ACTIVE, active == 1)
                    : CEFusion.CONSOLE_PANEL.get().defaultBlockState().setValue(FusionConsolePanelBlock.FACING, Direction.NORTH)
                        .setValue(FusionConsolePanelBlock.COLUMN, column).setValue(FusionConsolePanelBlock.DEPTH, depth)
                        .setValue(FusionConsolePanelBlock.ACTIVE, active == 1);
                placeSample(level, origin.offset(56 + active * 7 - column, 0, 123 - depth), panel);
            }
            placeSample(level, origin.offset(67 + active * 2, 0, 122), CEFusion.MOTOR_GENERATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X).setValue(FusionMotorGeneratorBlock.ACTIVE, active == 1));
        }
        placeSample(level, origin.offset(71, 0, 122), CEFusion.MOTOR_GENERATOR.get().defaultBlockState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        placeSample(level, origin.offset(73, 0, 122), CEFusion.VACUUM_PUMP.get().defaultBlockState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
    }
    private static void placeSample(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        // The disposable client previews fixed model states; these samples do not claim to be operating.
        FUSION_SAMPLES.add(new Sample(pos, state));
    }
    private static void move(Minecraft mc, View next) {
        mc.options.fov().set(next.fov());var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());player.getAbilities().flying = true;player.onUpdateAbilities();
            float[] angles = angles(next);player.connection.teleport(origin.getX()+next.x(), origin.getY()+next.y(), origin.getZ()+next.z(), angles[0], angles[1]);
        });
    }
    private static float[] angles(View view) {
        double x = view.lx()-view.x(), y = view.ly()-view.y()-1.62, z = view.lz()-view.z();
        return new float[]{(float)Math.toDegrees(-Math.atan2(x, z)), (float)-Math.toDegrees(Math.atan2(y, Math.hypot(x, z)))};
    }
}
