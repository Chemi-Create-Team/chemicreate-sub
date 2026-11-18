package net.createplant.block;

import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** A crafted casing, its rotors and its ends are placed and recovered as one unit. */
public final class TurbineUnit {
    private static final java.util.Map<Level, Set<BlockPos>> MUTATING =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private TurbineUnit() {}

    public enum Stage {
        HIGH(1, 5),
        LOW(4, 10);
        private final int radius;

        private final int sections;

        Stage(int radius, int sections) {
            this.radius = radius;
            this.sections = sections;
        }

        public int sections() {
            return sections;
        }

        public int radius() {
            return radius;
        }

        public net.minecraft.world.item.Item item() {
            return switch (this) {
                case HIGH -> CEItems.HIGH_PRESSURE_TURBINE_UNIT.get();
                case LOW -> CEItems.LOW_PRESSURE_TURBINE_UNIT.get();
            };
        }

        public SteamTurbineBlock block() {
            return switch (this) {
                case HIGH -> CEBlocks.HIGH_PRESSURE_STEAM_TURBINE.get();
                case LOW -> CEBlocks.STEAM_TURBINE.get();
            };
        }
    }

    public record Layout(BlockPos origin, Direction.Axis axis, Stage stage) {
        public int radius() {
            return stage.radius();
        }

        public boolean lowPressure() {
            return stage == Stage.LOW;
        }

        public int rotors() {
            return 1;
        }

        public Direction along() {
            return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        }

        public BlockPos cell(int along, int x, int y) {
            return HighPressureTurbinePartBlock.partPos(
                    origin.relative(along(), along), axis, x, y);
        }

        public List<BlockPos> volume() {
            List<BlockPos> positions = new ArrayList<>();
            for (int x = -radius(); x <= radius(); x++)
                for (int y = -radius(); y <= radius(); y++)
                    if (occupied(radius(), x, y)) positions.add(cell(0, x, y));
            return positions;
        }
    }

    private static final java.util.Map<Integer, net.minecraft.world.phys.shapes.VoxelShape> SHAPES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static net.minecraft.world.phys.shapes.VoxelShape shape(
            net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        var center = HighPressureTurbinePartBlock.centerPos(pos, state);
        var rotor = level.getBlockState(center);
        int radius =
                rotor.is(CEBlocks.STEAM_TURBINE.get()) ? Stage.LOW.radius() : Stage.HIGH.radius();
        int x =
                HighPressureTurbineEndBlock.ringOffsetFromState(
                        state.getValue(HighPressureTurbinePartBlock.RING_X));
        int y =
                HighPressureTurbineEndBlock.ringOffsetFromState(
                        state.getValue(HighPressureTurbinePartBlock.RING_Y));
        var axis = state.getValue(HighPressureTurbinePartBlock.AXIS);
        int key = radius * 2000 + axis.ordinal() * 500 + (x + 4) * 9 + y + 4;
        return SHAPES.computeIfAbsent(
                key,
                k -> {
                    double outer = radius + .46, diagonal = outer * Math.sqrt(2);
                    var shape = net.minecraft.world.phys.shapes.Shapes.empty();
                    for (int row = 0; row < 16; row++) {
                        double lo = Math.max(row / 16d, .5 - outer - y),
                                hi = Math.min((row + 1) / 16d, .5 + outer - y);
                        if (lo >= hi) continue;
                        double limit = Math.min(outer, diagonal - Math.abs(y + (lo + hi) / 2 - .5));
                        double z0 = Math.max(0, .5 - limit - x), z1 = Math.min(1, .5 + limit - x);
                        if (z0 >= z1) continue;
                        var part =
                                axis == Direction.Axis.X
                                        ? net.minecraft.world.phys.shapes.Shapes.box(
                                                0, lo, z0, 1, hi, z1)
                                        : net.minecraft.world.phys.shapes.Shapes.box(
                                                z0, lo, 0, z1, hi, 1);
                        shape = net.minecraft.world.phys.shapes.Shapes.or(shape, part);
                    }
                    return shape.optimize();
                });
    }

    public static boolean occupied(int radius, int x, int y) {
        // Reserve every voxel touched by the octagonal casing or its closed ends.
        double nearest = Math.max(0, Math.abs(x) - .5) + Math.max(0, Math.abs(y) - .5);
        return Math.abs(x) <= radius
                && Math.abs(y) <= radius
                && nearest < (radius + .5) * Math.sqrt(2);
    }

    public static boolean connected(Level level, Layout unit, Direction side) {
        if (level == null || side.getAxis() != unit.axis()) return false;
        Layout other = at(level, unit.origin().relative(side));
        return other != null && other.axis() == unit.axis() && other.stage() == unit.stage();
    }

    public static boolean intact(Level level, Layout unit) {
        for (var entry : states(unit).entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)
                    || !level.getBlockState(pos).equals(entry.getValue())
                    || !unit.equals(at(level, pos))) return false;
        }
        return true;
    }

    public static List<Layout> assembly(Level level, Layout unit) {
        var sections = connectedSections(level, unit);
        return sections.size() == unit.stage().sections() ? sections : List.of();
    }

    public static List<Layout> connectedSections(Level level, Layout unit) {
        if (level == null) return List.of();
        Layout first = unit;
        for (int i = 0; connected(level, first, first.along().getOpposite()); i++) {
            if (i >= unit.stage().sections()) return List.of();
            first = at(level, first.origin().relative(first.along().getOpposite()));
        }
        var result = new ArrayList<Layout>();
        Layout current = first;
        while (current != null) {
            if (!level.hasChunkAt(current.origin().relative(current.along()))
                    || !level.hasChunkAt(current.origin().relative(current.along().getOpposite())))
                return List.of();
            if (result.size() == unit.stage().sections() || !intact(level, current))
                return List.of();
            result.add(current);
            current =
                    connected(level, current, current.along())
                            ? at(level, current.origin().relative(current.along()))
                            : null;
        }
        return result;
    }

    public static Layout at(Level level, BlockPos pos) {
        if (level == null || !level.hasChunkAt(pos)) return null;
        var be = level.getBlockEntity(pos);
        if (be instanceof SteamTurbineBlockEntity rotor) return rotor.unitLayout();
        if (be instanceof net.createplant.block.entity.HighPressureTurbinePartBlockEntity part)
            return part.unitLayout();
        if (be instanceof net.createplant.block.entity.HighPressureTurbineEndBlockEntity end)
            return end.unitLayout();
        return null;
    }

    public static void writeLayout(net.minecraft.nbt.CompoundTag tag, Layout layout) {
        if (layout == null) return;
        var unit = new net.minecraft.nbt.CompoundTag();
        unit.putLong("Origin", layout.origin().asLong());
        unit.putString("Axis", layout.axis().getName());
        unit.putString("Stage", layout.stage().name());
        unit.putInt("Version", 2);
        tag.put("FactoryUnit", unit);
    }

    public static Layout readLayout(net.minecraft.nbt.CompoundTag tag) {
        if (!tag.contains("FactoryUnit")) return null;
        var unit = tag.getCompound("FactoryUnit");
        var axis = Direction.Axis.byName(unit.getString("Axis"));
        if (axis == null || axis == Direction.Axis.Y || unit.getInt("Version") != 2) return null;
        try {
            return new Layout(
                    BlockPos.of(unit.getLong("Origin")),
                    axis,
                    Stage.valueOf(unit.getString("Stage")));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isMutating(Level level, BlockPos pos) {
        return MUTATING.getOrDefault(level, Set.of()).contains(pos);
    }

    public static java.util.Map<BlockPos, BlockState> states(Layout unit) {
        var result = new java.util.LinkedHashMap<BlockPos, BlockState>();
        result.put(
                unit.origin(),
                unit.stage()
                        .block()
                        .defaultBlockState()
                        .setValue(SteamTurbineBlock.AXIS, unit.axis())
                        .setValue(SteamTurbineBlock.BLADES, unit.stage() == Stage.HIGH ? 6 : 2)
                        .setValue(SteamTurbineBlock.CASING, true)
                        .setValue(SteamTurbineBlock.SIZE_1, 4)
                        .setValue(SteamTurbineBlock.SIZE_2, 4)
                        .setValue(SteamTurbineBlock.SIZE_3, 4));
        for (int x = -unit.radius(); x <= unit.radius(); x++)
            for (int y = -unit.radius(); y <= unit.radius(); y++) {
                if (!occupied(unit.radius(), x, y) || x == 0 && y == 0) continue;
                result.put(
                        unit.cell(0, x, y),
                        HighPressureTurbinePartBlock.expectedCasingState(unit.axis(), x, y)
                                .setValue(HighPressureTurbinePartBlock.SECTION, true));
            }
        return result;
    }

    public static boolean place(Level level, Layout layout, Player player, ItemStack held) {
        if (layout.axis() == Direction.Axis.Y) return false;
        List<BlockPos> positions = layout.volume();
        for (BlockPos pos : positions) {
            if (level.isOutsideBuildHeight(pos)
                    || !level.hasChunkAt(pos)
                    || !level.getBlockState(pos).canBeReplaced()
                    || level.getBlockEntity(pos) != null
                    || player != null
                            && (!player.mayUseItemAt(pos, Direction.UP, held)
                                    || !level.mayInteract(player, pos))) return false;
        }
        BlockPos min =
                positions.stream()
                        .reduce(
                                (a, b) ->
                                        new BlockPos(
                                                Math.min(a.getX(), b.getX()),
                                                Math.min(a.getY(), b.getY()),
                                                Math.min(a.getZ(), b.getZ())))
                        .orElseThrow();
        BlockPos max =
                positions.stream()
                        .reduce(
                                (a, b) ->
                                        new BlockPos(
                                                Math.max(a.getX(), b.getX()),
                                                Math.max(a.getY(), b.getY()),
                                                Math.max(a.getZ(), b.getZ())))
                        .orElseThrow();
        if (!level.getEntities(
                        (net.minecraft.world.entity.Entity) null,
                        new AABB(
                                min.getX(),
                                min.getY(),
                                min.getZ(),
                                max.getX() + 1,
                                max.getY() + 1,
                                max.getZ() + 1),
                        entity -> !entity.isSpectator() && entity.blocksBuilding)
                .isEmpty()) return false;
        if (level.isClientSide()) return true;
        Set<BlockPos> guard = MUTATING.computeIfAbsent(level, l -> new HashSet<>());
        guard.addAll(positions);
        List<BlockState> previous = positions.stream().map(level::getBlockState).toList();
        boolean success = false;
        try {
            for (var entry : states(layout).entrySet()) {
                if (!level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS))
                    return false;
                var be = level.getBlockEntity(entry.getKey());
                if (be instanceof SteamTurbineBlockEntity rotor) rotor.setUnitLayout(layout);
                else if (be
                        instanceof
                        net.createplant.block.entity.HighPressureTurbinePartBlockEntity part)
                    part.setUnitLayout(layout);
                else return false;
            }
            success = !connectedSections(level, layout).isEmpty();
            return success;
        } finally {
            if (!success)
                for (int i = 0; i < positions.size(); i++)
                    level.setBlock(positions.get(i), previous.get(i), Block.UPDATE_CLIENTS);
            guard.removeAll(positions);
            if (guard.isEmpty()) MUTATING.remove(level);
            for (BlockPos pos : positions)
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            net.createplant.thermal.FluidRoutes.changed(level);
        }
    }

    public static boolean remove(Level level, BlockPos pos, boolean recover) {
        if (level == null || level.isClientSide() || isMutating(level, pos)) return false;
        Layout layout = at(level, pos);
        if (layout == null) return false;
        List<BlockPos> positions = layout.volume();
        // Do not discard an unloaded half of the machine.
        if (positions.stream().anyMatch(p -> !level.hasChunkAt(p))) return false;
        Set<BlockPos> guard = MUTATING.computeIfAbsent(level, l -> new HashSet<>());
        guard.addAll(positions);
        var owned = positions.stream().filter(p -> layout.equals(at(level, p))).toList();
        try {
            for (int i = 0; i < layout.rotors(); i++) {
                BlockPos center = layout.origin().relative(layout.along(), i);
                if (level.getBlockEntity(center) instanceof SteamTurbineBlockEntity be)
                    be.ventContents();
            }
            for (BlockPos part : owned)
                level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (recover) Block.popResource(level, pos, new ItemStack(layout.stage().item()));
        } finally {
            guard.removeAll(positions);
            if (guard.isEmpty()) MUTATING.remove(level);
            for (BlockPos part : positions)
                level.updateNeighborsAt(part, level.getBlockState(part).getBlock());
            net.createplant.thermal.FluidRoutes.changed(level);
        }
        return true;
    }
}
