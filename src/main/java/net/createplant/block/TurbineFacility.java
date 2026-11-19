package net.createplant.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

import net.createplant.block.HighPressureTurbineMultiblock.AssemblyInfo;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class TurbineFacility {
    public static final int MAX_CONNECTING_SHAFTS = 5;
    public static final int MAX_BOILERS = 4;
    public static final int STEAM_PER_MAX_BOILER = 6_400;
    public static final int HIGH_PRESSURE_MAX_STEAM = STEAM_PER_MAX_BOILER * MAX_BOILERS;
    public static final int LOW_PRESSURE_MAX_STEAM = HIGH_PRESSURE_MAX_STEAM / 2;
    private static final int MAX_SEARCHED_PIPES = 256;

    private TurbineFacility() {
    }

    public static FacilityLink find(BlockGetter level, BlockPos pos, BlockState state) {
        AssemblyInfo local = HighPressureTurbineMultiblock.assemblyInfo(level, pos, state);
        if (!local.formed() || local.highPressure() == local.lowPressure()) {
            return FacilityLink.empty(local);
        }

        FacilityLink mechanicalFallback = FacilityLink.empty(local);
        for (Direction.AxisDirection side : Direction.AxisDirection.values()) {
            FacilityLink link = findAcrossSide(level, local, side);
            if (link.mechanicallyConnected()) {
                link = link.withSteamConnected(
                    hasSeparateExhaustPipe(level, link.highPressure(), link.lowPressure()));
                if (link.steamConnected()) {
                    return link;
                }
                mechanicalFallback = link;
            }
        }
        return mechanicalFallback;
    }

    public static boolean isOperational(BlockGetter level, BlockPos pos, BlockState state) {
        return find(level, pos, state).operational();
    }

    public static float totalSU(BlockGetter level, FacilityLink link) {
        if (!link.operational()) {
            return 0;
        }
        double watts=0;
        for(var assembly:List.of(link.highPressure(),link.lowPressure())) {
            Direction step=Direction.fromAxisAndDirection(assembly.axis(),Direction.AxisDirection.POSITIVE);
            for(int i=0;i<assembly.shaftCount();i++)if(level.getBlockEntity(assembly.start().relative(step,i)) instanceof SteamTurbineBlockEntity turbine)watts+=turbine.thermodynamics().capacityWork()*20;
        }
        return (float)watts;
    }

    public static void addToGoggleTooltip(BlockGetter level, BlockPos pos, BlockState state,
        List<Component> tooltip) {
        FacilityLink link = find(level, pos, state);
        tooltip.add(Component.literal("\u8907\u5408\u84b8\u6c17\u30bf\u30fc\u30d3\u30f3\u65bd\u8a2d")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(line(Component.translatable("goggle.create_refinery_n_reactors.status"),
            Component.translatable(link.operational()
                ? "goggle.create_refinery_n_reactors.status.formed"
                : "goggle.create_refinery_n_reactors.status.incomplete")
                .withStyle(link.operational() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        tooltip.add(line(Component.literal("\u30b7\u30e3\u30d5\u30c8\u63a5\u7d9a"),
            Component.literal(link.mechanicallyConnected()
                ? link.connectingShafts() + "/" + MAX_CONNECTING_SHAFTS
                : "0/" + MAX_CONNECTING_SHAFTS)
                .withStyle(link.mechanicallyConnected() ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        tooltip.add(line(Component.literal("\u9ad8\u5727\u6392\u6c17\u30d1\u30a4\u30d7"),
            Component.translatable(link.steamConnected()
                ? "goggle.create_refinery_n_reactors.present"
                : "goggle.create_refinery_n_reactors.status.incomplete")
                .withStyle(link.steamConnected() ? ChatFormatting.GREEN : ChatFormatting.RED)));

        if (!link.operational()) {
            return;
        }
        SteamStats stats = steamStats(level, link);
        tooltip.add(line(Component.literal("\u9ad8\u5727\u84b8\u6c17\u5165\u529b"),
            Component.literal(stats.highInput() + "/" + HIGH_PRESSURE_MAX_STEAM + " mB/t")
                .withStyle(ChatFormatting.AQUA)));
        tooltip.add(line(Component.literal("\u4f4e\u5727\u84b8\u6c17\u5165\u529b"),
            Component.literal(stats.lowInput() + "/" + LOW_PRESSURE_MAX_STEAM + " mB/t")
                .withStyle(ChatFormatting.AQUA)));
        tooltip.add(line(Component.literal("\u65bd\u8a2d\u5168\u4f53\u306e\u5fdc\u529b\u5bb9\u91cf"),
            Component.literal(String.format(Locale.ROOT, "%,.0f SU", totalSU(level, link)))
                .withStyle(ChatFormatting.AQUA)));
    }

    public static SteamStats steamStats(BlockGetter level, FacilityLink link) {
        if (!link.operational()) {
            return new SteamStats(0, 0);
        }
        int highInput = HighPressureTurbineMultiblock.assemblySteamStats(level,
            link.highPressure().start(), level.getBlockState(link.highPressure().start())).input();
        int lowInput = HighPressureTurbineMultiblock.assemblySteamStats(level,
            link.lowPressure().start(), level.getBlockState(link.lowPressure().start())).input();
        return new SteamStats(highInput, lowInput);
    }

    private static FacilityLink findAcrossSide(BlockGetter level, AssemblyInfo local,
        Direction.AxisDirection side) {
        Direction outward = Direction.fromAxisAndDirection(local.axis(), side);
        BlockPos boundary = side == Direction.AxisDirection.POSITIVE ? local.end() : local.start();
        BlockPos endCenter = boundary.relative(outward);
        boolean localPositive = side == Direction.AxisDirection.POSITIVE;
        if (!HighPressureTurbineEndBlock.isCompleteEnd(level, endCenter, local.axis(), localPositive,
            HighPressureTurbineMultiblock.ringRadius(level.getBlockState(boundary)))) {
            return FacilityLink.empty(local);
        }

        BlockPos cursor = endCenter.relative(outward);
        int shafts = 0;
        while (shafts < MAX_CONNECTING_SHAFTS && (!(level instanceof Level world) || world.hasChunkAt(cursor)) && isConnectingShaft(level.getBlockState(cursor), local.axis())) {
            shafts++;
            cursor = cursor.relative(outward);
        }
        if (shafts == 0) {
            return FacilityLink.empty(local);
        }

        boolean partnerPositive = outward.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        if(level instanceof Level world && !world.hasChunkAt(cursor))return FacilityLink.empty(local);
        BlockState partnerEnd = level.getBlockState(cursor);
        if (!HighPressureTurbineEndBlock.isEndCenter(partnerEnd, local.axis(), partnerPositive)) {
            return FacilityLink.empty(local);
        }

        BlockPos partnerBoundary = cursor.relative(outward);
        if(level instanceof Level world && !world.hasChunkAt(partnerBoundary))return FacilityLink.empty(local);
        BlockState partnerState = level.getBlockState(partnerBoundary);
        AssemblyInfo partner = HighPressureTurbineMultiblock.assemblyInfo(level, partnerBoundary, partnerState);
        if (!partner.formed() || partner.axis() != local.axis()
            || local.highPressure() == partner.highPressure()
            || local.lowPressure() == partner.lowPressure()) {
            return FacilityLink.empty(local);
        }

        AssemblyInfo high = local.highPressure() ? local : partner;
        AssemblyInfo low = local.lowPressure() ? local : partner;
        return new FacilityLink(true, false, shafts, high, low);
    }

    private static boolean isConnectingShaft(BlockState state, Direction.Axis axis) {
        return com.simibubi.create.AllBlocks.SHAFT.has(state)
            && state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) == axis;
    }

    private static boolean hasSeparateExhaustPipe(BlockGetter level, AssemblyInfo high, AssemblyInfo low) {
        return findExhaustNetwork(level, high, low) != null;
    }

    private static PipeNetwork findExhaustNetwork(BlockGetter level, AssemblyInfo high, AssemblyInfo low) {
        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos contact : contacts(level, high)) {
            for (Direction direction : Direction.values()) {
                BlockPos pipePos = contact.relative(direction);
                if (level instanceof Level world && !world.hasChunkAt(pipePos) || com.simibubi.create.content.fluids.FluidPropagator.getPipe(level,pipePos)==null || checked.contains(pipePos)) {
                    continue;
                }
                if (!net.createplant.thermal.FluidPipeTopology.opens(level, pipePos, direction.getOpposite())) continue;
                PipeNetwork network = inspectPipeNetwork(level, pipePos, low);
                checked.addAll(network.pipes());
                if (network.touchesTarget()) {
                    return network;
                }
            }
        }
        return null;
    }

    private static PipeNetwork inspectPipeNetwork(BlockGetter level, BlockPos origin, AssemblyInfo target) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> pipes = new HashSet<>();
        queue.add(origin);
        pipes.add(origin);
        boolean touchesTarget = false;

        while (!queue.isEmpty() && pipes.size() <= MAX_SEARCHED_PIPES) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                if (!net.createplant.thermal.FluidPipeTopology.opens(level, current, direction)) continue;
                BlockPos adjacent = current.relative(direction);
                if(level instanceof Level world && !world.hasChunkAt(adjacent))continue;
                BlockState state = level.getBlockState(adjacent);
                if (com.simibubi.create.content.fluids.FluidPropagator.getPipe(level,adjacent)!=null) {
                    if (net.createplant.thermal.FluidPipeTopology.opens(level, adjacent, direction.getOpposite()) && pipes.add(adjacent)) {
                        queue.add(adjacent);
                    }
                    continue;
                }
                if (belongsToAssembly(level, target, adjacent, state)) {
                    touchesTarget = true;
                }
            }
        }
        return new PipeNetwork(pipes, queue.isEmpty() && touchesTarget);
    }

    private static Set<BlockPos> contacts(BlockGetter level, AssemblyInfo assembly) {
        Set<BlockPos> contacts = new HashSet<>();
        Direction positive = Direction.fromAxisAndDirection(assembly.axis(), Direction.AxisDirection.POSITIVE);
        BlockPos cursor = assembly.start();
        for (int i = 0; i < assembly.shaftCount(); i++) {
            contacts.add(cursor);
            int radius = HighPressureTurbineMultiblock.ringRadius(level.getBlockState(cursor));
            for (int ringX = -radius; ringX <= radius; ringX++) {
                for (int ringY = -radius; ringY <= radius; ringY++) {
                    if (HighPressureTurbinePartBlock.isRequiredCasingPosition(
                        level, cursor, ringX, ringY, radius)) {
                        contacts.add(HighPressureTurbinePartBlock.partPos(
                            cursor, assembly.axis(), ringX, ringY));
                    }
                }
            }
            cursor = cursor.relative(positive);
        }
        int radius=assembly.lowPressure()?2:1;
        for(var end:List.of(assembly.start().relative(positive.getOpposite()),assembly.end().relative(positive)))
            for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)if(x!=0 || y!=0)
                contacts.add(HighPressureTurbineEndBlock.partPos(end,assembly.axis(),x,y));
        return contacts;
    }

    private static boolean belongsToAssembly(BlockGetter level, AssemblyInfo assembly, BlockPos pos,
        BlockState state) {
        BlockPos center = turbineCenter(level, pos);
        return center != null && contains(assembly, center);
    }

    private static BlockPos turbineCenter(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (SteamTurbineBlock.isTurbine(state) || com.simibubi.create.AllBlocks.SHAFT.has(state)) {
            return pos;
        }
        if (state.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get())
            && state.getValue(HighPressureTurbinePartBlock.ATTACHED)) {
            return HighPressureTurbinePartBlock.centerPos(pos, state);
        }
        if(state.getBlock() instanceof HighPressureTurbineEndBlock) {
            BlockPos center=HighPressureTurbineEndBlock.centerPos(pos,state);
            Direction inward=Direction.fromAxisAndDirection(state.getValue(HighPressureTurbineEndBlock.AXIS),state.getValue(HighPressureTurbineEndBlock.POSITIVE)?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE);
            return center.relative(inward);
        }
        return null;
    }

    private static boolean contains(AssemblyInfo assembly, BlockPos pos) {
        Direction positive = Direction.fromAxisAndDirection(assembly.axis(), Direction.AxisDirection.POSITIVE);
        BlockPos cursor = assembly.start();
        for (int i = 0; i < assembly.shaftCount(); i++) {
            if (cursor.equals(pos)) {
                return true;
            }
            cursor = cursor.relative(positive);
        }
        return false;
    }

    private static Component line(Component label, Component value) {
        return Component.literal("  ")
            .append(label)
            .append(Component.literal(": "))
            .append(value);
    }

    private record PipeNetwork(Set<BlockPos> pipes, boolean touchesTarget) {
    }

    public record SteamStats(int highInput, int lowInput) {
    }

    public record FacilityLink(boolean mechanicallyConnected, boolean steamConnected, int connectingShafts,
        AssemblyInfo highPressure, AssemblyInfo lowPressure) {
        private static FacilityLink empty(AssemblyInfo local) {
            return new FacilityLink(false, false, 0,
                local.highPressure() ? local : null,
                local.lowPressure() ? local : null);
        }

        private FacilityLink withSteamConnected(boolean connected) {
            return new FacilityLink(mechanicallyConnected, connected, connectingShafts, highPressure, lowPressure);
        }

        public boolean operational() {
            return mechanicallyConnected && steamConnected && highPressure != null && lowPressure != null
                && highPressure.formed() && lowPressure.formed();
        }
    }
}
