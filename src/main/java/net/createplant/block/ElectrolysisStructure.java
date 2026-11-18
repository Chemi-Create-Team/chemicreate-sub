package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.ElectrolysisCellBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Bounded discovery of a single row of cells with an electrode head on every cell and exactly one two-unit end panel, dial below and meters above. */
public record ElectrolysisStructure(BlockPos panel, Direction facing, BlockPos minimum, int length,
    List<BlockPos> cells, List<BlockPos> controls, List<BlockPos> heads) {
    public static final int MAX_LENGTH = 8;

    public static ElectrolysisStructure find(Level level, BlockPos candidate) {
        var first = level.getBlockState(candidate);
        if (!first.is(CEElectrolysis.CONTROLLER.get())) return null;
        Direction face = first.getValue(ElectrolysisControllerBlock.FACING);
        BlockPos panel = controller(level, candidate.below(), face) ? candidate.below() : candidate;
        if (!controller(level, panel.above(), face)) return null;
        var controls = List.of(panel, panel.above());
        var cells = new ArrayList<BlockPos>();
        for (var at = panel.relative(face.getOpposite()); cell(level, at, face.getAxis()); at = at.relative(face.getOpposite())) {
            if (cells.size() == MAX_LENGTH) return null;
            cells.add(at);
        }
        if (cells.isEmpty()) return null;
        var end = cells.getLast().relative(face.getOpposite());
        if (!level.hasChunkAt(end) || level.getBlockState(end).is(CEElectrolysis.CONTROLLER.get())) return null;
        // No other cell or panel unit may touch the row from the side, nor the panel from above or below.
        for (var at : cells) for (var direction : Direction.values()) if (direction.getAxis() != face.getAxis() && direction != Direction.UP && foreign(level, at.relative(direction))) return null;
        for (var at : controls) for (var direction : Direction.values()) {
            var next = at.relative(direction);
            if (direction.getAxis() != face.getAxis() && !controls.contains(next) && foreign(level, next)) return null;
        }
        var heads = heads(cells);
        for (var at : heads) if (!head(level, at, face.getAxis())) return null;
        int x = cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(), z = cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        return new ElectrolysisStructure(panel, face, new BlockPos(x, panel.getY(), z), cells.size(), List.copyOf(cells), controls, heads);
    }

    /** The positions over the cells, in the order of the cells. */
    public static List<BlockPos> heads(List<BlockPos> cells) { return cells.stream().map(BlockPos::above).toList(); }

    /** The unit that carries the power terminals: the upper panel unit. */
    public BlockPos terminal() { return controls.get(1); }

    private static boolean foreign(Level level, BlockPos at) {
        if (!level.hasChunkAt(at)) return true;
        var state = level.getBlockState(at);
        return state.is(CEElectrolysis.CELL.get()) || state.is(CEElectrolysis.CONTROLLER.get());
    }

    private static boolean head(Level level, BlockPos at, Direction.Axis axis) {
        return level.hasChunkAt(at) && level.getBlockState(at).is(CEElectrolysis.HEAD.get())
            && level.getBlockState(at).getValue(ElectrodeHeadBlock.AXIS) == axis;
    }

    private static boolean cell(Level level, BlockPos at, Direction.Axis axis) {
        return level.hasChunkAt(at) && level.getBlockState(at).is(CEElectrolysis.CELL.get())
            && level.getBlockState(at).getValue(ElectrolysisCellBlock.AXIS) == axis;
    }

    private static boolean controller(Level level, BlockPos at, Direction facing) {
        return level.hasChunkAt(at) && level.getBlockState(at).is(CEElectrolysis.CONTROLLER.get())
            && level.getBlockState(at).getValue(ElectrolysisControllerBlock.FACING) == facing;
    }

    public boolean intact(Level level) {
        for (var at : cells) if (!cell(level, at, facing.getAxis())) return false;
        for (var at : controls) if (!controller(level, at, facing)) return false;
        for (var at : heads) if (!head(level, at, facing.getAxis())) return false;
        return true;
    }

    public void bind(Level level, boolean running) {
        for (var at : cells) if (level.getBlockEntity(at) instanceof ElectrolysisCellBlockEntity cell) cell.bind(panel);
        for (var at : heads) {
            if (level.getBlockEntity(at) instanceof ElectrodeHeadBlockEntity head) head.bind(panel, running);
        }
        for (int index = 0; index < controls.size(); index++) {
            var at = controls.get(index);
            if (level.getBlockEntity(at) instanceof ElectrolysisControllerBlockEntity part) part.bind(panel);
            var state = level.getBlockState(at);
            if (state.getValue(ElectrolysisControllerBlock.PART) != index)
                level.setBlock(at, state.setValue(ElectrolysisControllerBlock.PART, index), 3);
        }
    }
}
