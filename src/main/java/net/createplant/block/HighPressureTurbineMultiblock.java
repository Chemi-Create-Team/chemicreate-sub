package net.createplant.block;

import java.util.List;

import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class HighPressureTurbineMultiblock {
    public static final int MAX_SHAFTS = 3;
    public static final int REQUIRED_BLADES = 18;
    public static final int REQUIRED_TARBIN_ENDS = 2;
    public static final int HIGH_PRESSURE_RING_RADIUS = 1;
    public static final int LOW_PRESSURE_RING_RADIUS = 2;
    public static final int LOW_PRESSURE_CASING_BLOCKS_PER_SHAFT = 16;
    private static final int LOW_PRESSURE_REQUIRED_SHAFTS = 5;
    private static final int LOW_PRESSURE_REQUIRED_BLADES = 8;

    private HighPressureTurbineMultiblock() {
    }

    public static void refreshAround(Level level, BlockPos changedPos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        refreshPipe(level, changedPos);
        for (Direction direction : Direction.values()) {
            refreshPipe(level, changedPos.relative(direction));
        }
    }

    public static boolean isFormed(BlockGetter level, BlockPos turbinePos, BlockState turbineState) {
        return inspect(level, turbinePos, turbineState).formed();
    }

    public static AssemblyInfo assemblyInfo(BlockGetter level, BlockPos turbinePos, BlockState turbineState) {
        FormationStatus status = inspect(level, turbinePos, turbineState);
        boolean highPressure = CEBlocks.isHighPressureSteamTurbine(turbineState);
        if (!highPressure && status.shaftCount() > 0) {
            highPressure = CEBlocks.isHighPressureSteamTurbine(level.getBlockState(status.start()));
        }
        return new AssemblyInfo(status.formed(), highPressure, status.lowPressureTurbine(), status.axis(),
            status.start(), status.shaftCount(), status.casingCount(), status.tarbinEndCount(),
            status.bladeCount(), status.requiredBladeCount(), status.requiredShaftCount());
    }

    public static int assemblyTurbineCount(BlockGetter level, BlockPos turbinePos, BlockState turbineState) {
        AssemblyInfo info = assemblyInfo(level, turbinePos, turbineState);
        if (!info.formed()) {
            return 0;
        }
        int count = 0;
        BlockPos cursor = info.start();
        Direction positive = positive(info.axis());
        for (int i = 0; i < info.shaftCount(); i++) {
            if (SteamTurbineBlock.isTurbine(level.getBlockState(cursor))) {
                count++;
            }
            cursor = cursor.relative(positive);
        }
        return count;
    }

    public static float performanceMultiplier(BlockGetter level, BlockPos turbinePos, BlockState turbineState) {
        FormationStatus status = inspect(level, turbinePos, turbineState);
        if (!status.formed()) {
            return 0;
        }
        return Math.min(1, status.bladeCount() / (float) status.requiredBladeCount());
    }

    public static SteamStats assemblySteamStats(BlockGetter level, BlockPos turbinePos, BlockState turbineState) {
        FormationStatus status = inspect(level, turbinePos, turbineState);
        if (!status.formed() || !(level instanceof BlockAndTintGetter blockLevel)) {
            return SteamStats.empty();
        }

        int buffer = 0;
        int input = 0;
        float speed = 0;
        Direction positive = positive(status.axis());
        BlockPos cursor = status.start();
        for (int i = 0; i < status.shaftCount(); i++) {
            BlockEntity blockEntity = blockLevel.getBlockEntity(cursor);
            if (blockEntity instanceof SteamTurbineBlockEntity turbine) {
                buffer += turbine.localSteamBuffer();
                input += turbine.localSteamInput();
                speed = Math.max(speed, turbine.localSteamOutputSpeed());
            }
            cursor = cursor.relative(positive);
        }
        return new SteamStats(buffer, input, speed, status.shaftCount());
    }

    public static void addToGoggleTooltip(BlockGetter level, BlockPos pos, BlockState state, List<Component> tooltip) {
        FormationStatus status = inspect(level, pos, state);
        tooltip.add(Component.translatable("goggle.create_refinery_n_reactors.turbine_multiblock")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(indented("goggle.create_refinery_n_reactors.status", status.formed()
            ? Component.translatable("goggle.create_refinery_n_reactors.status.formed").withStyle(ChatFormatting.GREEN)
            : Component.translatable("goggle.create_refinery_n_reactors.status.incomplete").withStyle(ChatFormatting.RED)));
        if (status.formed()) {
            tooltip.add(indented("goggle.create_refinery_n_reactors.turbine_blades",
                Component.literal(status.bladeCount() + "/" + status.requiredBladeCount())
                    .withStyle(status.bladeCount() >= status.requiredBladeCount()
                        ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
            return;
        }
        if (status.casedTurbine() && !status.lowPressureTurbine()) {
            tooltip.add(indented("goggle.create_refinery_n_reactors.high_pressure_turbine",
                Component.translatable("goggle.create_refinery_n_reactors.present").withStyle(ChatFormatting.GREEN)));
        }
        tooltip.add(indented("goggle.create_refinery_n_reactors.turbine_shafts",
            Component.literal(status.shaftCount() + "/" + status.requiredShaftCount())
                .withStyle(status.shaftCount() > 0 && status.shaftCount() <= status.requiredShaftCount()
                    ? ChatFormatting.AQUA : ChatFormatting.GOLD)));
        if (status.casedTurbine()) {
            tooltip.add(indented("goggle.create_refinery_n_reactors.turbine_casings",
                Component.literal(status.casingCount() + "/"
                    + Math.max(1, Math.min(status.shaftCount(), status.requiredShaftCount())))
                    .withStyle(status.casingCount() >= status.shaftCount() && status.shaftCount() > 0
                        ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
            tooltip.add(indented("goggle.create_refinery_n_reactors.tarbin_end",
                Component.literal(status.tarbinEndCount() + "/" + REQUIRED_TARBIN_ENDS)
                    .withStyle(status.tarbinEndCount() >= REQUIRED_TARBIN_ENDS
                        ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        }
        tooltip.add(indented("goggle.create_refinery_n_reactors.turbine_blades",
            Component.literal(status.bladeCount() + "/" + status.requiredBladeCount())
                .withStyle(status.bladeCount() >= status.requiredBladeCount()
                    ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
        tooltip.add(indented("goggle.create_refinery_n_reactors.turbine_performance",
            Component.literal(Math.round(Math.min(1, status.bladeCount() / (float) status.requiredBladeCount()) * 100) + "%")
                .withStyle(status.bladeCount() >= status.requiredBladeCount()
                    ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
    }

    public static void showCompletionEffect(Level level, BlockPos pos, Direction.Axis axis) {
        if (level instanceof ServerLevel serverLevel) {
            showCompletionEffect(serverLevel, pos, positive(axis));
            showCompletionEffect(serverLevel, pos, negative(axis));
        }
    }

    public static int ringRadius(BlockState state) {
        if (CEBlocks.isLowPressureSteamTurbine(state) || com.simibubi.create.AllBlocks.SHAFT.has(state)) {
            return LOW_PRESSURE_RING_RADIUS;
        }
        return HIGH_PRESSURE_RING_RADIUS;
    }

    private static FormationStatus inspect(BlockGetter level, BlockPos pos, BlockState state) {
        if (level == null || !(level instanceof BlockAndTintGetter blockLevel)) {
            return FormationStatus.empty(Direction.Axis.X, pos, REQUIRED_BLADES);
        }

        Direction.Axis axis = axisFor(state);
        if (axis == null) {
            return FormationStatus.empty(Direction.Axis.X, pos, REQUIRED_BLADES);
        }

        var section = level instanceof Level world ? TurbineUnit.at(world,pos) : null;
        if (section!=null && blockLevel.getBlockEntity(pos) instanceof SteamTurbineBlockEntity rotor) {
            var sections=rotor.unitAssembly();
            int count=sections.size(), blades=state.getValue(SteamTurbineBlock.BLADES)*count;
            return new FormationStatus(count>0,true,section.lowPressure(),count,count,count>0 ? 2 : 0,blades,
                section.axis(),count>0 ? sections.getFirst().origin() : pos,Math.max(1,blades),Math.max(1,count));
        }
        boolean highPressureTurbine = CEBlocks.isHighPressureSteamTurbine(state);
        boolean lowPressureTurbine = CEBlocks.isLowPressureSteamTurbine(state)
            || com.simibubi.create.AllBlocks.SHAFT.has(state);
        boolean casedTurbine = highPressureTurbine || lowPressureTurbine;
        if (!casedTurbine) {
            return new FormationStatus(false, false, false, 0, 0, 0, 0, axis, pos,
                REQUIRED_BLADES, MAX_SHAFTS);
        }
        int ringRadius = ringRadius(state);

        Direction positive = positive(axis);
        Direction negative = negative(axis);
        BlockPos start = pos;
        int searched=0;
        while (isAssemblyTurbine(blockLevel, start.relative(negative), axis, highPressureTurbine)) {
            if(++searched>16)return FormationStatus.empty(axis,pos,REQUIRED_BLADES);
            start = start.relative(negative);
        }

        int shafts = 0;
        int casings = 0;
        int blades = 0;
        BlockPos cursor = start;
        while (shafts<=16 && isAssemblyTurbine(blockLevel, cursor, axis, highPressureTurbine)) {
            BlockState turbineState = blockLevel.getBlockState(cursor);
            shafts++;
            if (SteamTurbineBlock.isTurbine(turbineState)) {
                blades += turbineState.getValue(SteamTurbineBlock.BLADES);
            }
            if (HighPressureTurbinePartBlock.isCompleteCasing(blockLevel, cursor, axis, ringRadius)) {
                casings++;
            }
            cursor = cursor.relative(positive);
        }

        int tarbinEnds = 0;
        int requiredShafts = lowPressureTurbine ? LOW_PRESSURE_REQUIRED_SHAFTS : MAX_SHAFTS;
        if (shafts > 0 && shafts <= requiredShafts) {
            BlockPos negativeEnd = start;
            BlockPos positiveEnd = start.relative(positive, shafts - 1);
            if (hasTarbinEnd(blockLevel, negativeEnd, axis, Direction.AxisDirection.NEGATIVE)) {
                tarbinEnds++;
            }
            if (hasTarbinEnd(blockLevel, positiveEnd, axis, Direction.AxisDirection.POSITIVE)) {
                tarbinEnds++;
            }
        }

        boolean formed = lowPressureTurbine
            ? shafts == LOW_PRESSURE_REQUIRED_SHAFTS
                && casings == shafts
                && tarbinEnds >= REQUIRED_TARBIN_ENDS
                && blades >= LOW_PRESSURE_REQUIRED_BLADES
            : shafts > 0
                && shafts <= MAX_SHAFTS
                && casings == shafts
                && tarbinEnds >= REQUIRED_TARBIN_ENDS
                && blades >= REQUIRED_BLADES;
        int requiredBlades = lowPressureTurbine ? LOW_PRESSURE_REQUIRED_BLADES : REQUIRED_BLADES;
        return new FormationStatus(formed, casedTurbine, lowPressureTurbine, shafts, casings, tarbinEnds,
            Math.min(blades, requiredBlades), axis, start, requiredBlades, requiredShafts);
    }

    private static Direction.Axis axisFor(BlockState state) {
        if (SteamTurbineBlock.isTurbine(state)) {
            return state.getValue(SteamTurbineBlock.AXIS);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.getValue(BlockStateProperties.AXIS);
        }
        return null;
    }

    private static boolean isAssemblyTurbine(BlockGetter level, BlockPos pos, Direction.Axis axis,
        boolean highPressureTurbine) {
        if(level instanceof net.minecraft.world.level.Level world && !world.hasChunkAt(pos))return false;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.AXIS)
            && !(SteamTurbineBlock.isTurbine(state) && state.hasProperty(SteamTurbineBlock.AXIS))) {
            return false;
        }
        Direction.Axis stateAxis = axisFor(state);
        if (stateAxis != axis) {
            return false;
        }
        if (highPressureTurbine) {
            return CEBlocks.isHighPressureSteamTurbine(state);
        }
        return CEBlocks.isLowPressureSteamTurbine(state) || com.simibubi.create.AllBlocks.SHAFT.has(state);
    }

    private static boolean hasTarbinEnd(BlockAndTintGetter level, BlockPos pos, Direction.Axis axis,
        Direction.AxisDirection direction) {
        if (hasHighPressureEnd(level, pos, axis, direction)) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamTurbineBlockEntity turbine) {
            return turbine.hasTarbinEnd(direction);
        }
        return false;
    }

    private static boolean hasHighPressureEnd(BlockAndTintGetter level, BlockPos pos, Direction.Axis axis,
        Direction.AxisDirection direction) {
        Direction facing = Direction.fromAxisAndDirection(axis, direction);
        BlockPos endCenter = pos.relative(facing);
        boolean positive = direction == Direction.AxisDirection.POSITIVE;
        return HighPressureTurbineEndBlock.isCompleteEnd(level, endCenter, axis, positive,
            ringRadius(level.getBlockState(pos)));
    }

    private static Direction positive(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }

    private static Direction negative(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
    }

    private static void showCompletionEffect(ServerLevel level, BlockPos turbinePos, Direction direction) {
        BlockPos casingPos = turbinePos.relative(direction);
        level.sendParticles(ParticleTypes.END_ROD, casingPos.getX() + .5, casingPos.getY() + .5, casingPos.getZ() + .5,
            18, .45, .45, .45, .035);
        level.sendParticles(ParticleTypes.CLOUD, casingPos.getX() + .5, casingPos.getY() + .5, casingPos.getZ() + .5,
            8, .25, .25, .25, .015);
        level.playSound(null, casingPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, .65f, 1.45f);
    }

    private static void refreshPipe(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.createplant.block.MaterialPipeBlock steamPipe) {
            BlockState refreshed = ((net.createplant.block.MaterialPipeBlock)state.getBlock()).updateBlockState(state,Direction.NORTH,null,level,pos);
            if (!state.equals(refreshed)) {
                level.setBlock(pos, refreshed, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }
    }

    private static Component indented(String key, Component value) {
        return Component.literal("  ")
            .append(Component.translatable(key))
            .append(Component.literal(": "))
            .append(value);
    }

    private record FormationStatus(boolean formed, boolean casedTurbine, boolean lowPressureTurbine,
        int shaftCount, int casingCount, int tarbinEndCount, int bladeCount, Direction.Axis axis, BlockPos start,
        int requiredBladeCount,
        int requiredShaftCount) {
        private static FormationStatus empty(Direction.Axis axis, BlockPos start, int requiredBladeCount) {
            return new FormationStatus(false, false, false, 0, 0, 0, 0, axis, start, requiredBladeCount, MAX_SHAFTS);
        }
    }

    public record SteamStats(int buffer, int input, float speed, int shaftCount) {
        private static SteamStats empty() {
            return new SteamStats(0, 0, 0, 0);
        }
    }

    public record AssemblyInfo(boolean formed, boolean highPressure, boolean lowPressure, Direction.Axis axis,
        BlockPos start, int shaftCount, int casingCount, int tarbinEndCount, int bladeCount,
        int requiredBladeCount, int requiredShaftCount) {
        public BlockPos end() {
            return start.relative(positive(axis), Math.max(0, shaftCount - 1));
        }
    }
}
