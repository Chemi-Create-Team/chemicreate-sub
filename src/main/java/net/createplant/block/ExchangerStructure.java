package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.block.entity.ThermalPartBlockEntity;
import net.createplant.registry.CEHeating;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The fluid heat exchangers, each a horizontal run of one casing block: the shell-and-tube exchanger, tube-piped tanks 2 x 2 across
 * and 2 to 12 long, the large one, the same tanks 3 x 3 across and 3 to 12 long, and the jacketed pipe, 1 x 1 across and 1 to 8 long.
 * A complete run turns one end's bottom casing into its controller.
 * The tube side (the jacketed pipe's core) flows in and out of the two end faces; the shell side (its jacket) through the top of
 * the first end slice and the top of the last (the jacketed pipe: the bottom of the last).
 */
public final class ExchangerStructure {
    public enum Kind {
        SHELL(2, 2, 12, 1, 0), LARGE(3, 3, 12, 2, 4), JACKET(1, 1, 8, 1, 0);

        /** Tube sections per block of length: the large shell holds about twice the tubes of the 2 x 2 one. {@code offset} is the first place of its cells. */
        public final int width, min, max, sections, offset;

        Kind(int width, int min, int max, int sections, int offset) {
            this.width = width; this.min = min; this.max = max; this.sections = sections; this.offset = offset;
        }

        public Block casing() { return this == JACKET ? CEHeating.JACKET.get() : CEHeating.TUBE_TANK.get(); }
        public Block controller() {
            return switch (this) {
                case SHELL -> CEHeating.EXCHANGER_CONTROLLER.get(); case LARGE -> CEHeating.LARGE_EXCHANGER_CONTROLLER.get(); case JACKET -> CEHeating.JACKET_CONTROLLER.get();
            };
        }
    }

    /**
     * Casing cells: the segment along the run (0 first end, 1 middle, 2 last end, 3 a one-block jacketed pipe) times 13, plus the place
     * across it: across + width * up, after the 2 x 2 shell's four places for the 3 x 3 one, so a cell alone tells its exchanger's size.
     */
    public static final int SEGMENTS = 4, PLACES = 13;

    private ExchangerStructure() { }

    private static Direction along(ReactorMachineBlockEntity be) { return be.getBlockState().getValue(ThermalMachineBlock.FACING); }

    /** The block at {@code across} (0 or 1 sideways) and {@code up} of the slice whose controller-side bottom block is {@code origin}. */
    private static BlockPos cell(BlockPos origin, Direction along, int across, int up) { return origin.relative(along.getClockWise(), across).above(up); }

    /** Whether the slice is all casings lying along the run; {@code growing} also takes in a 2 x 2 exchanger's controller for a 3 x 3 one. */
    private static boolean slice(Level level, BlockPos origin, Direction along, Kind kind, BlockPos controller, boolean growing) {
        for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++) {
            BlockPos pos = cell(origin, along, across, up);
            if (!level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            boolean casing = (state.is(kind.casing()) || growing && kind == Kind.LARGE && state.is(Kind.SHELL.controller()))
                && state.getValue(ThermalCasingBlock.FACING).getAxis() == along.getAxis();
            if (!casing && !pos.equals(controller)) return false;
        }
        return true;
    }

    /** How many complete slices follow {@code origin} in {@code direction}, up to {@code limit}. */
    private static int run(Level level, BlockPos origin, Direction direction, Direction along, Kind kind, BlockPos controller, int limit, boolean growing) {
        int count = 0;
        while (count < limit && slice(level, origin.relative(direction, count + 1), along, kind, controller, growing)) count++;
        return count;
    }

    public static ReactorStructure.Result scan(ReactorMachineBlockEntity be, Kind kind) {
        Level level = be.getLevel();
        BlockPos controller = be.getBlockPos();
        Direction along = along(be);
        if (!slice(level, controller, along, kind, controller, false)) return new ReactorStructure.Result(0, List.of(), controller, "jacket");
        int back = run(level, controller, along.getOpposite(), along, kind, controller, kind.max, false), ahead = run(level, controller, along, along, kind, controller, kind.max, false);
        int length = back + 1 + ahead;
        BlockPos first = controller.relative(along.getOpposite(), back);
        if (length < kind.min || length > kind.max) return new ReactorStructure.Result(0, List.of(), first, "thermal_length");
        List<BlockPos> parts = new ArrayList<>(length * kind.width * kind.width);
        for (int index = 0; index < length; index++)
            for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++) {
                BlockPos pos = cell(first.relative(along, index), along, across, up);
                if (level.getBlockEntity(pos) instanceof ThermalPartBlockEntity proxy && proxy.owner() != null && proxy.owner() != be && proxy.owner().owns(pos))
                    return new ReactorStructure.Result(0, List.of(), pos, "shared");
                parts.add(pos);
            }
        return new ReactorStructure.Result(length * kind.sections, List.copyOf(parts), controller, "ready");
    }

    /** Where {@code pos} is in the run: its slice from the first end and its quadrant across, or null outside it. */
    private static int[] place(ReactorMachineBlockEntity be, BlockPos pos, List<BlockPos> members, Kind kind) {
        int index = members.indexOf(pos);
        if (index < 0) return null;
        int cells = kind.width * kind.width, quadrant = index % cells;
        return new int[] {index / cells, quadrant / kind.width + quadrant % kind.width * kind.width};
    }

    public static void shape(ReactorMachineBlockEntity be, List<BlockPos> members, int sections, Kind kind) {
        int length = sections / kind.sections;
        Level level = be.getLevel();
        Direction along = along(be);
        for (BlockPos pos : members) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(kind.casing())) continue;
            int[] place = place(be, pos, members, kind);
            int segment = length == 1 ? 3 : place[0] == 0 ? 0 : place[0] == length - 1 ? 2 : 1;
            BlockState formed = state.setValue(ThermalCasingBlock.FORMED, true).setValue(ExchangerCasingBlock.CELL, segment * PLACES + kind.offset + place[1])
                .setValue(ThermalCasingBlock.FACING, along);
            if (formed != state) level.setBlock(pos, formed, Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof ThermalPartBlockEntity proxy) proxy.assign(be.getBlockPos());
        }
    }

    public static IFluidHandler port(ReactorMachineBlockEntity be, List<BlockPos> members, int sections, Kind kind, BlockPos pos, Direction side) {
        int length = sections / kind.sections;
        int[] place = place(be, pos, members, kind);
        if (place == null) return null;
        Direction along = along(be);
        boolean first = place[0] == 0, last = place[0] == length - 1, top = place[1] >= kind.width * (kind.width - 1);
        if (first && side == along.getOpposite()) return be.thermal().rowPort(0, 0);
        if (last && side == along) return be.thermal().rowPort(0, 1);
        if (first && top && side == Direction.UP) return be.thermal().rowPort(1, 0);
        Direction lastJacket = kind == Kind.JACKET ? Direction.DOWN : Direction.UP;
        if (last && (kind == Kind.JACKET || top) && side == lastJacket) return be.thermal().rowPort(1, 1);
        return null;
    }

    /** Where a formed shell-and-tube cell sits in its exchanger: segment, across, up and width; null for a loose tank or any other block. */
    public static int[] cell(BlockState state) {
        if (state.getBlock() instanceof ExchangerControllerBlock controller) return controller.kind == Kind.JACKET ? null : new int[] {0, 0, 0, controller.kind.width};
        if (!state.is(CEHeating.TUBE_TANK.get()) || !state.getValue(ThermalCasingBlock.FORMED)) return null;
        int cell = state.getValue(ExchangerCasingBlock.CELL), place = cell % PLACES;
        Kind kind = place < Kind.LARGE.offset ? Kind.SHELL : Kind.LARGE;
        int local = place - kind.offset;
        return new int[] {cell / PLACES, local % kind.width, local / kind.width, kind.width};
    }

    /** Whether two blocks are neighbouring cells of the same formed shell-and-tube exchanger, so their tank textures run on into each other. */
    public static boolean joined(BlockState state, BlockPos pos, BlockState other, BlockPos otherPos) {
        int[] at = cell(state), to = cell(other);
        if (at == null || to == null || at[3] != to[3]) return false;
        Direction along = state.getValue(ThermalCasingBlock.FACING);
        if (other.getValue(ThermalCasingBlock.FACING) != along) return false;
        BlockPos offset = otherPos.subtract(pos);
        Direction side = along.getClockWise();
        int ahead = offset.getX() * along.getStepX() + offset.getZ() * along.getStepZ(), across = offset.getX() * side.getStepX() + offset.getZ() * side.getStepZ();
        if (to[1] != at[1] + across || to[2] != at[2] + offset.getY()) return false;
        return ahead == 0 ? at[0] == to[0] : ahead == 1 ? at[0] != 2 && to[0] != 0 : ahead == -1 && at[0] != 0 && to[0] != 2;
    }

    /**
     * After a casing is placed or removed: exchangers along its line check themselves again, and a complete run of casings with no
     * controller yet gets one at its first end's bottom corner. Tube-piped tanks try the 3 x 3 exchanger before the 2 x 2 one.
     */
    public static void changed(Level level, BlockPos pos, Block casing) {
        if (level.isClientSide) return;
        Kind[] kinds = casing == CEHeating.JACKET.get() ? new Kind[] {Kind.JACKET} : new Kind[] {Kind.LARGE, Kind.SHELL};
        int width = kinds[0].width, max = kinds[0].max;
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            for (int step = -max; step <= max; step++)
                for (int across = 1 - width; across < width; across++) for (int up = 1 - width; up <= 0; up++)
                    if (level.getBlockEntity(pos.relative(along, step).relative(along.getClockWise(), across).above(up)) instanceof ReactorMachineBlockEntity be
                        && be.exchanger() != null && be.exchanger().casing() == casing && affects(be, pos)) be.invalidateStructure();
        }
        BlockState placed = level.getBlockState(pos);
        if (!placed.is(casing)) return;
        for (Kind kind : kinds) if (form(level, pos, placed.getValue(ThermalCasingBlock.FACING), kind)) return;
    }

    /** Whether a casing changed at {@code pos} can change the exchanger: one of its own, or one in line with its ends; not one beside it. */
    private static boolean affects(ReactorMachineBlockEntity be, BlockPos pos) {
        Direction.Axis axis = be.getBlockState().getValue(ThermalMachineBlock.FACING).getAxis();
        return !be.formed() || be.owns(pos) || be.owns(pos.relative(axis, 1)) || be.owns(pos.relative(axis, -1));
    }

    private static boolean form(Level level, BlockPos pos, Direction facing, Kind kind) {
        // The run is tried first the way its casings were laid, so it starts where the player started it.
        for (Direction along : new Direction[] {facing, facing.getOpposite()}) {
            for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++) {
                BlockPos origin = pos.relative(along.getClockWise(), -across).below(up);
                if (!slice(level, origin, along, kind, null, true)) continue;
                BlockPos first = origin.relative(along.getOpposite(), run(level, origin, along.getOpposite(), along, kind, null, kind.max, true));
                int length = 1 + run(level, first, along, along, kind, null, kind.max, true);
                if (length < kind.min || length > kind.max || !alone(level, first, along, kind, length)
                    || controlled(level, first.relative(along.getOpposite()), along, kind) || controlled(level, first.relative(along, length), along, kind)) continue;
                List<BlockPos> smaller = smaller(level, first, along, kind, length);
                if (owned(level, first, along, kind, length, smaller)) continue;
                if (!smaller.isEmpty()) {
                    // A 2 x 2 exchanger inside the new 3 x 3 run gives its controller back as a tank; that tank forms the larger one.
                    BlockPos shell = smaller.getFirst();
                    level.setBlock(shell, kind.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING, level.getBlockState(shell).getValue(ThermalMachineBlock.FACING)), Block.UPDATE_ALL);
                    return true;
                }
                level.setBlock(first, kind.controller().defaultBlockState().setValue(ThermalMachineBlock.FACING, along)
                    .setValue(ReactorMachineBlock.AXIS, along.getAxis()), Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether no loose tank lies beside the run's cross-section: a run that is part of a wider block of tanks is a larger exchanger
     * still being built, not a smaller one. Tanks that a formed exchanger holds do not count, so exchangers may stand side by side.
     */
    private static boolean alone(Level level, BlockPos first, Direction along, Kind kind, int length) {
        if (kind.width == 1) return true;
        for (int index = 0; index < length; index++) {
            BlockPos origin = first.relative(along, index);
            for (int edge = 0; edge < kind.width; edge++)
                for (BlockPos pos : new BlockPos[] {cell(origin, along, -1, edge), cell(origin, along, kind.width, edge), cell(origin, along, edge, -1), cell(origin, along, edge, kind.width)}) {
                    BlockState state = level.getBlockState(pos);
                    if (state.is(kind.casing()) && state.getValue(ThermalCasingBlock.FACING).getAxis() == along.getAxis()
                        && !(level.getBlockEntity(pos) instanceof ThermalPartBlockEntity proxy && proxy.owner() != null && proxy.owner().owns(pos))) return false;
                }
        }
        return true;
    }

    /** The 2 x 2 exchangers' controllers inside a run of the 3 x 3 one. */
    private static List<BlockPos> smaller(Level level, BlockPos first, Direction along, Kind kind, int length) {
        List<BlockPos> found = new ArrayList<>();
        if (kind != Kind.LARGE) return found;
        for (int index = 0; index < length; index++)
            for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++) {
                BlockPos pos = cell(first.relative(along, index), along, across, up);
                if (level.getBlockState(pos).is(Kind.SHELL.controller())) found.add(pos);
            }
        return found;
    }

    /** Whether the slice at {@code origin} holds an exchanger's controller, which then takes the new casings into its run. */
    private static boolean controlled(Level level, BlockPos origin, Direction along, Kind kind) {
        for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++)
            if (level.getBlockState(cell(origin, along, across, up)).is(kind.controller())) return true;
        return false;
    }

    /** Whether any casing of the run already belongs to an exchanger other than the smaller ones it takes in, which then takes the new casing itself. */
    private static boolean owned(Level level, BlockPos first, Direction along, Kind kind, int length, List<BlockPos> smaller) {
        for (int index = 0; index < length; index++)
            for (int across = 0; across < kind.width; across++) for (int up = 0; up < kind.width; up++)
                if (level.getBlockEntity(cell(first.relative(along, index), along, across, up)) instanceof ThermalPartBlockEntity proxy && proxy.owner() != null
                    && !smaller.contains(proxy.owner().getBlockPos()))
                    return true;
        return false;
    }
}
