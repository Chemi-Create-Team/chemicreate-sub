package net.createplant.atomics.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.decoration.girder.GirderBlock;

import net.createplant.atomics.block.ConverterShellBlock.Part;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.createplant.atomics.registry.CEConverter;
import net.createplant.registry.CEMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Fixed 9 by 9 frame and the exact glued five-block-wide converter vessel. */
public final class ConverterStructure {
    public enum Material {
        SHELL, TRUNNION_BEARING, GIRDER_Y, GIRDER_X, GIRDER_Z, GIRDER_CROSS,
        FLUID_PIPE, CHUTE, STEEL, REFRACTORY, BLACKSTONE
    }

    public record Cell(int x, int y, int z, Material material, Part part) {
        public boolean shell() { return material == Material.SHELL; }
    }

    public record Validation(String status, BlockPos fault) {
        public boolean valid() { return status.equals("ready"); }
        public boolean unloaded() { return status.equals("unloaded"); }
    }

    private record Local(int x, int y, int z) { }
    private static final List<Cell> FIXED_CELLS = buildFixedCells();
    private static final List<Cell> MOVING_CELLS = buildMovingCells();
    private static final List<Cell> CELLS = allCells();
    private static final Map<Level, Map<Long, Set<BlockPos>>> CONTROLLERS = new WeakHashMap<>();

    private ConverterStructure() { }

    public static List<Cell> cells() { return CELLS; }
    public static List<Cell> fixedCells() { return FIXED_CELLS; }
    public static List<Cell> movingCells() { return MOVING_CELLS; }

    public static BlockPos originFromBearing(BlockPos bearing, Direction bearingFacing) {
        return bearing.relative(bearingFacing, 4).below(5);
    }

    public static Direction structureFacing(Direction bearingFacing) { return bearingFacing.getCounterClockWise(); }
    public static Direction bearingFacing(Direction structureFacing) { return structureFacing.getClockWise(); }
    public static BlockPos bearing(BlockPos origin, Direction facing) { return at(origin, facing, -4, 5, 0); }
    public static BlockPos anchor(BlockPos origin, Direction facing) { return at(origin, facing, -3, 5, 0); }
    public static BlockPos passiveBearing(BlockPos origin, Direction facing) { return at(origin, facing, 4, 5, 0); }
    public static BlockPos lance(BlockPos origin, Direction facing) { return at(origin, facing, 0, 7, 0); }
    public static BlockPos rawPort(BlockPos origin, Direction facing) { return at(origin, facing, 2, 7, 1); }
    public static BlockPos gasPort(BlockPos origin, Direction facing) { return at(origin, facing, 0, 9, -4); }
    public static BlockPos tap(BlockPos origin, Direction facing) { return at(origin, facing, 0, 3, 3); }

    public static BlockPos at(BlockPos origin, Direction facing, int x, int y, int z) {
        Direction right = facing.getClockWise();
        return origin.offset(right.getStepX() * x + facing.getStepX() * z, y,
            right.getStepZ() * x + facing.getStepZ() * z);
    }

    public static Validation validateFixed(Level level, BlockPos origin, Direction facing) {
        return validate(level, origin, facing, FIXED_CELLS);
    }

    public static Validation validateVessel(Level level, BlockPos origin, Direction facing) {
        return validate(level, origin, facing, MOVING_CELLS);
    }

    public static Validation validate(Level level, BlockPos origin, Direction facing) {
        Validation fixed = validateFixed(level, origin, facing);
        return fixed.valid() ? validateVessel(level, origin, facing) : fixed;
    }

    private static Validation validate(Level level, BlockPos origin, Direction facing, List<Cell> cells) {
        for (Cell cell : cells) {
            BlockPos pos = at(origin, facing, cell.x(), cell.y(), cell.z());
            if (!level.hasChunkAt(pos)) return new Validation("unloaded", pos);
            if (!matches(level.getBlockState(pos), cell.material(), facing)) return new Validation("missing", pos);
        }
        return new Validation("ready", origin);
    }

    public static Map<BlockPos, BlockState> expectedMovingBlocks(BlockPos origin, Direction facing) {
        BlockPos anchor = anchor(origin, facing);
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (Cell cell : MOVING_CELLS) {
            BlockPos world = at(origin, facing, cell.x(), cell.y(), cell.z());
            result.put(world.subtract(anchor), stateFor(cell, facing));
        }
        return Map.copyOf(result);
    }

    public static List<BlockPos> positions(BlockPos origin, Direction facing) {
        List<BlockPos> result = new ArrayList<>(CELLS.size());
        for (Cell cell : CELLS) result.add(at(origin, facing, cell.x(), cell.y(), cell.z()));
        return result;
    }

    /** Canonical state used by GameTests and Ponder; normal girder connections may differ. */
    public static BlockState stateFor(Cell cell, Direction facing) {
        return switch (cell.material()) {
            case SHELL -> CEConverter.SHELL.get().defaultBlockState()
                .setValue(ConverterShellBlock.FACING, facing).setValue(ConverterShellBlock.PART, cell.part());
            case TRUNNION_BEARING -> CEConverter.TRUNNION_BEARING.get().defaultBlockState()
                .setValue(BearingBlock.FACING, bearingFacing(facing));
            case GIRDER_Y -> girder(Direction.Axis.Y, false, false, false);
            case GIRDER_X -> girder(facing.getClockWise().getAxis(),
                facing.getClockWise().getAxis() == Direction.Axis.X,
                facing.getClockWise().getAxis() == Direction.Axis.Z, false);
            case GIRDER_Z -> girder(facing.getAxis(), facing.getAxis() == Direction.Axis.X,
                facing.getAxis() == Direction.Axis.Z, false);
            case GIRDER_CROSS -> girder(facing.getClockWise().getAxis(), true, true, cell.y() == 10);
            case FLUID_PIPE -> AllBlocks.FLUID_PIPE.getDefaultState()
                .setValue(PipeBlock.UP, true).setValue(PipeBlock.DOWN, true);
            case CHUTE -> AllBlocks.CHUTE.getDefaultState();
            case STEEL -> CEMetals.STEEL_BLOCK.get().getBlock().defaultBlockState();
            case REFRACTORY -> CEConverter.SILICA_BRICKS.get().defaultBlockState();
            case BLACKSTONE -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        };
    }

    private static boolean matches(BlockState state, Material material, Direction facing) {
        return switch (material) {
            case SHELL -> state.is(CEConverter.SHELL.get());
            case TRUNNION_BEARING -> state.is(CEConverter.TRUNNION_BEARING.get())
                && state.getValue(BearingBlock.FACING) == bearingFacing(facing);
            case GIRDER_Y, GIRDER_X, GIRDER_Z, GIRDER_CROSS -> state.is(AllBlocks.METAL_GIRDER.get());
            case FLUID_PIPE -> state.is(AllBlocks.FLUID_PIPE.get());
            case CHUTE -> state.is(AllBlocks.CHUTE.get());
            case STEEL -> state.is(CEMetals.STEEL_BLOCK.get().getBlock());
            case REFRACTORY -> state.is(CEConverter.SILICA_BRICKS.get()) || state.is(CEConverter.REFRACTORY_BRICKS.get());
            case BLACKSTONE -> state.is(Blocks.POLISHED_BLACKSTONE_BRICKS);
        };
    }

    public static boolean matchesMovingState(BlockState state, BlockState expected) {
        return state.equals(expected) || expected.is(CEConverter.SILICA_BRICKS.get()) && state.is(CEConverter.REFRACTORY_BRICKS.get());
    }

    private static BlockState girder(Direction.Axis axis, boolean worldX, boolean worldZ, boolean bottom) {
        return AllBlocks.METAL_GIRDER.getDefaultState()
            .setValue(GirderBlock.AXIS, axis)
            .setValue(GirderBlock.X, worldX)
            .setValue(GirderBlock.Z, worldZ)
            .setValue(GirderBlock.BOTTOM, bottom);
    }

    private static List<Cell> buildFixedCells() {
        Map<Local, Cell> cells = new LinkedHashMap<>();
        for (int x : new int[] { -4, 4 }) for (int z : new int[] { -4, 4 })
            put(cells, x, 0, z, Material.BLACKSTONE, Part.LOOSE);
        for (int y = 1; y <= 9; y++) for (int x : new int[] { -4, 4 }) for (int z : new int[] { -4, 4 })
            put(cells, x, y, z, Material.GIRDER_Y, Part.LOOSE);
        for (int x = -4; x <= 4; x++) {
            put(cells, x, 10, -4, x == -4 || x == 4 ? Material.GIRDER_CROSS : Material.GIRDER_X, Part.LOOSE);
            put(cells, x, 10, 4, x == -4 || x == 4 ? Material.GIRDER_CROSS : Material.GIRDER_X, Part.LOOSE);
        }
        for (int z = -3; z <= 3; z++) {
            put(cells, -4, 10, z, Material.GIRDER_Z, Part.LOOSE);
            put(cells, 4, 10, z, Material.GIRDER_Z, Part.LOOSE);
        }
        for (int x = -3; x <= 3; x++) if (x != 0)
            put(cells, x, 10, 0, Material.GIRDER_X, Part.LOOSE);
        for (int z = -3; z <= 3; z++) if (z != 0)
            put(cells, 0, 10, z, Material.GIRDER_Z, Part.LOOSE);
        for (int x : new int[] { -4, 4 }) for (int z = -3; z <= 3; z++) if (z != 0)
            put(cells, x, 5, z, Material.GIRDER_Z, Part.LOOSE);

        put(cells, -4, 5, 0, Material.TRUNNION_BEARING, Part.LOOSE);
        put(cells, 4, 5, 0, Material.SHELL, Part.BEARING);
        put(cells, 0, 7, 0, Material.SHELL, Part.LANCE);
        put(cells, 2, 7, 1, Material.SHELL, Part.RAW_PORT);
        for (int y = 8; y <= 10; y++) {
            put(cells, 0, y, 0, Material.FLUID_PIPE, Part.LOOSE);
            put(cells, 2, y, 1, Material.CHUTE, Part.LOOSE);
        }
        put(cells, 0, 9, -4, Material.SHELL, Part.GAS_PORT);
        put(cells, 0, 3, 3, Material.SHELL, Part.TAP);
        return List.copyOf(cells.values());
    }

    private static List<Cell> buildMovingCells() {
        Map<Local, Cell> cells = new LinkedHashMap<>();
        put(cells, -3, 5, 0, Material.STEEL, Part.LOOSE);
        put(cells, 3, 5, 0, Material.STEEL, Part.LOOSE);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            put(cells, x, 2, z, Material.STEEL, Part.LOOSE);
        for (int y = 3; y <= 6; y++) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                if (Math.abs(x) == 2 || Math.abs(z) == 2)
                    put(cells, x, y, z, Material.STEEL, Part.LOOSE);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                if (Math.abs(x) == 1 || Math.abs(z) == 1)
                    put(cells, x, y, z, Material.REFRACTORY, Part.LOOSE);
        }
        return List.copyOf(cells.values());
    }

    private static List<Cell> allCells() {
        List<Cell> result = new ArrayList<>(FIXED_CELLS.size() + MOVING_CELLS.size());
        result.addAll(FIXED_CELLS);
        result.addAll(MOVING_CELLS);
        return List.copyOf(result);
    }

    private static void put(Map<Local, Cell> cells, int x, int y, int z, Material material, Part part) {
        cells.put(new Local(x, y, z), new Cell(x, y, z, material, part));
    }

    public static void track(ConverterTrunnionBearingBlockEntity controller) {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        long chunk = new ChunkPos(controller.getBlockPos()).toLong();
        CONTROLLERS.computeIfAbsent(level, key -> new HashMap<>())
            .computeIfAbsent(chunk, key -> new HashSet<>()).add(controller.getBlockPos().immutable());
    }

    public static void untrack(ConverterTrunnionBearingBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null || !CONTROLLERS.containsKey(level)) return;
        Map<Long, Set<BlockPos>> chunks = CONTROLLERS.get(level);
        long chunk = new ChunkPos(controller.getBlockPos()).toLong();
        Set<BlockPos> positions = chunks.get(chunk);
        if (positions != null) {
            positions.remove(controller.getBlockPos());
            if (positions.isEmpty()) chunks.remove(chunk);
        }
        if (chunks.isEmpty()) CONTROLLERS.remove(level);
    }

    public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) notifyNear(level, event.getPos(), false);
    }

    public static void chunkLoaded(ChunkEvent.Load event) { chunkChanged(event); }
    public static void chunkUnloaded(ChunkEvent.Unload event) { chunkChanged(event); }

    private static void chunkChanged(ChunkEvent event) {
        if (event.getLevel() instanceof ServerLevel level)
            notifyNear(level, event.getChunk().getPos().getMiddleBlockPosition(5), true);
    }

    private static void notifyNear(ServerLevel level, BlockPos changed, boolean wholeChunk) {
        Map<Long, Set<BlockPos>> chunks = CONTROLLERS.get(level);
        if (chunks == null) return;
        ChunkPos changedChunk = new ChunkPos(changed);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Set<BlockPos> positions = chunks.get(ChunkPos.asLong(changedChunk.x + dx, changedChunk.z + dz));
            if (positions == null) continue;
            for (BlockPos pos : List.copyOf(positions)) {
                if (!(level.getBlockEntity(pos) instanceof ConverterTrunnionBearingBlockEntity controller)) {
                    positions.remove(pos);
                    continue;
                }
                if (wholeChunk || controller.containsStructurePosition(changed)) controller.structureChanged();
            }
        }
    }
}
