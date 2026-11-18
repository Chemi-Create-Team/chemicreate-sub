package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.ReactorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A column of identical storeys stacked straight up. The bottom storey runs the process and keeps every buffer; the others only hand out its
 * ports at their own faces.
 */
public abstract class StackedReactorBlockEntity extends ReactorMachineBlockEntity {
    protected StackedReactorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    protected abstract int minStoreys();
    /** The blocks of one storey whose centre stands at {@code centre}. */
    protected abstract List<BlockPos> storeyCells(BlockPos centre);
    /** The port this storey gives at a face, taken from the formed bottom storey. */
    protected abstract IFluidHandler storeyPort(StackedReactorBlockEntity bottom, Direction side);
    protected IItemHandler storeyItems(StackedReactorBlockEntity bottom, Direction side) { return null; }

    protected boolean storey(BlockPos pos) { return level != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(getBlockState().getBlock()); }
    public boolean bottom() { return !storey(worldPosition.below()); }
    public boolean top() { return !storey(worldPosition.above()); }

    /** The storey at the bottom of this column, or null if part of the column below is unloaded. */
    public StackedReactorBlockEntity bottomStorey() {
        BlockPos pos = worldPosition;
        for (int step = 0; step <= maxSections() && level != null; step++) {
            if (!level.hasChunkAt(pos.below())) return null;
            if (!level.getBlockState(pos.below()).is(getBlockState().getBlock()))
                return level.getBlockEntity(pos) instanceof StackedReactorBlockEntity storey ? storey : null;
            pos = pos.below();
        }
        return null;
    }

    @Override protected boolean processController() { return level != null && bottom(); }

    /** Columns have no axis; the batch ports read from any face. */
    @Override public Direction inlet() { return Direction.NORTH; }

    @Override protected boolean structureLoaded() {
        return level != null && level.hasChunkAt(worldPosition) && level.hasChunkAt(worldPosition.above(maxSections()));
    }

    @Override protected ReactorStructure.Result scanStructure() {
        List<BlockPos> members = new ArrayList<>();
        int storeys = 0;
        while (storey(worldPosition.above(storeys))) {
            BlockPos centre = worldPosition.above(storeys);
            if (storeys > 0 && level.getBlockEntity(centre) instanceof ReactorMachineBlockEntity upper && upper.hasProcessContents())
                return new ReactorStructure.Result(0, List.of(), centre, "contents");
            if (++storeys > maxSections()) return new ReactorStructure.Result(0, List.of(), centre, "column");
            members.addAll(storeyCells(centre));
        }
        if (storeys < minStoreys()) return new ReactorStructure.Result(0, List.of(), worldPosition.above(storeys), "column");
        return new ReactorStructure.Result(storeys, List.copyOf(members), worldPosition, "ready");
    }

    public IFluidHandler storeyPort(Direction side) {
        var owner = bottomStorey();
        return owner == null || !owner.formed() || side == null ? null : storeyPort(owner, side);
    }

    public IItemHandler items(Direction side) {
        var owner = bottomStorey();
        return owner == null || !owner.formed() || side == null ? null : storeyItems(owner, side);
    }

    /** A storey placed or removed changes the columns above and below it. */
    public static void changed(Level level, BlockPos pos, Block block) {
        if (level.isClientSide()) return;
        for (BlockPos start : new BlockPos[] {pos.below(), pos, pos.above()}) {
            if (!level.hasChunkAt(start) || !(level.getBlockEntity(start) instanceof StackedReactorBlockEntity storey) || !storey.getBlockState().is(block)) continue;
            var owner = storey.bottomStorey();
            if (owner != null) owner.invalidateStructure();
            storey.invalidateStructure();
        }
    }
}
