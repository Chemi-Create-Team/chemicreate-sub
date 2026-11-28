package net.createplant.logistics.block;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
        return matches(level, segment, along.getOpposite());
    }

    private boolean matches(Level level, Segment segment, Direction facing) {
        boolean reversed = facing == along;
        int count = segment.last() - segment.first() + 1;
        BlockPos controller = belt(reversed ? segment.first() : segment.last());
        for (int u = segment.first(); u <= segment.last(); u++) {
            BlockPos pos = belt(u);
            if (!level.isLoaded(pos)) return false;
            var state = level.getBlockState(pos);
            if (!AllBlocks.BELT.has(state) || state.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL
                || state.getValue(BeltBlock.HORIZONTAL_FACING) != facing
                || !(level.getBlockEntity(pos) instanceof BeltBlockEntity entity) || !controller.equals(entity.getController())
                || entity.beltLength != count || entity.index != (reversed ? u - segment.first() : segment.last() - u)) return false;
            BeltPart part = state.getValue(BeltBlock.PART);
            if (u == segment.first() && part != (reversed ? BeltPart.START : BeltPart.END)
                || u == segment.last() && part != (reversed ? BeltPart.END : BeltPart.START)
                || u > segment.first() && u < segment.last() && part != BeltPart.MIDDLE && part != BeltPart.PULLEY) return false;
        }
        return true;
    }

    public boolean repairReversed(Level level, Segment segment) {
        if (level.isClientSide() || !matches(level, segment, along)) return false;
        int count = segment.last() - segment.first() + 1;
        var belts = new ArrayList<BeltBlockEntity>(count);
        var states = new ArrayList<BlockState>(count);
        for (int u = segment.first(); u <= segment.last(); u++) {
            var entity = (BeltBlockEntity) level.getBlockEntity(belt(u));
            belts.add(entity);
            states.add(entity.getBlockState());
        }
        var registries = level.registryAccess();
        var oldInventory = belts.getFirst().getInventory();
        CompoundTag inventory = oldInventory.write(registries);
        for (Tag item : inventory.getList("Items", Tag.TAG_COMPOUND)) reversePosition((CompoundTag) item, count);
        if (inventory.contains("LazyItem")) reversePosition(inventory.getCompound("LazyItem"), count);
        inventory.putBoolean("PositiveOrder", !inventory.getBoolean("PositiveOrder"));
        for (var entity : belts) {
            entity.detachKinetics();
            entity.removeSource();
        }
        for (int i = 0; i < count; i++) {
            BlockState state = states.get(i).setValue(BeltBlock.HORIZONTAL_FACING, along.getOpposite());
            if (i == 0) state = state.setValue(BeltBlock.PART, BeltPart.END);
            if (i == count - 1) state = state.setValue(BeltBlock.PART, BeltPart.START);
            if (level.setBlock(belts.get(i).getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) continue;
            for (int n = 0; n < count; n++) level.setBlock(belts.get(n).getBlockPos(), states.get(n), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            for (var entity : belts) entity.attachKinetics();
            return false;
        }
        oldInventory.read(new CompoundTag(), registries);
        BlockPos controller = belt(segment.last());
        for (int i = 0; i < count; i++) {
            var entity = belts.get(i);
            entity.setController(controller);
            entity.index = count - 1 - i;
            entity.invalidateItemHandler();
        }
        belts.getLast().getInventory().read(inventory, registries);
        for (var entity : belts) {
            entity.attachKinetics();
            entity.notifyUpdate();
            level.updateNeighborsAt(entity.getBlockPos(), entity.getBlockState().getBlock());
        }
        return true;
    }

    private static void reversePosition(CompoundTag item, int count) {
        item.putFloat("Pos", count - item.getFloat("Pos"));
        item.putFloat("PrevPos", count - item.getFloat("PrevPos"));
        item.putInt("InSegment", count - 1 - item.getInt("InSegment"));
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
        for (int u = -1; u < length; u++) {
            BlockPos pos = belt(u);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BeltBlockEntity entity && entity.isController()
                && !entity.getInventory().write(level.registryAccess()).getList("Items", Tag.TAG_COMPOUND).isEmpty()) return true;
        }
        return false;
    }
}
