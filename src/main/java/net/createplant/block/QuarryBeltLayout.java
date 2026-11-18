package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Separate native belts, powered by a parallel row of native encased chain drives. */
public record QuarryBeltLayout(BlockPos start, Direction along, Direction across, int length) {
    public record Segment(int first, int last) { }

    public List<Segment> segments() {
        int limit = Math.max(2, BeltConnectorItem.maxLength());
        var segments = new ArrayList<Segment>();
        for (int first = -1; first < length;) {
            int count = Math.min(limit, length - first);
            if (length - first - count == 1) count--;
            segments.add(new Segment(first, first + count - 1));
            first += count;
        }
        return segments;
    }

    public BlockPos belt(int u) { return start.relative(across.getOpposite()).relative(along, u); }
    public BlockPos chain(int u) { return belt(u).relative(across.getOpposite()); }
    public boolean shaft(Level level, int u) {
        var state = level.getBlockState(belt(u));
        return AllBlocks.SHAFT.has(state) && state.getValue(BlockStateProperties.AXIS) == across.getAxis();
    }
    public boolean chain(Level level, int u) {
        var state = level.getBlockState(chain(u));
        return AllBlocks.ENCASED_CHAIN_DRIVE.has(state) && state.getValue(BlockStateProperties.AXIS) == across.getAxis();
    }
    public BlockState chainState() {
        return AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState().setValue(BlockStateProperties.AXIS, across.getAxis())
            .setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, across.getAxis() == Direction.Axis.Z);
    }
    public boolean loaded(Level level) {
        for (int u = -1; u < length; u++) if (!level.hasChunkAt(belt(u)) || !level.hasChunkAt(chain(u))) return false;
        return true;
    }
    public boolean complete(Level level, Segment segment) {
        BlockPos controller = belt(segment.last());
        for (int u = segment.first(); u <= segment.last(); u++) {
            BlockPos pos = belt(u);var state = level.getBlockState(pos);
            if (!AllBlocks.BELT.has(state) || state.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL
                || state.getValue(BeltBlock.HORIZONTAL_FACING) != along.getOpposite()
                || !(level.getBlockEntity(pos) instanceof BeltBlockEntity entity) || !controller.equals(entity.getController())) return false;
            BeltPart part = state.getValue(BeltBlock.PART);
            if (u == segment.first() && part != BeltPart.END || u == segment.last() && part != BeltPart.START) return false;
        }
        return true;
    }
    public boolean complete(Level level) {
        if (!loaded(level)) return false;
        for (var segment : segments()) if (!complete(level, segment)) return false;
        for (int u = -1; u < length; u++) {
            if (!chain(level, u)) return false;
            if (u < length - 1 && !ChainDriveBlock.areBlocksConnected(level.getBlockState(chain(u)), level.getBlockState(chain(u + 1)), along)) return false;
        }
        return true;
    }

    public boolean hasItems(Level level) {
        for (var segment : segments()) {
            BlockPos controller = belt(segment.last());
            if (level.getBlockEntity(controller) instanceof BeltBlockEntity entity && entity.isController()
                && !entity.getInventory().getTransportedItems().isEmpty()) return true;
        }
        return false;
    }
}
