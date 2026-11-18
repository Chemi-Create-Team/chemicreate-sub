package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createplant.block.entity.ThermalPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The one block a thermal machine is built of. A complete box turns one of its casings into the machine's controller; the others
 * pass ports and goggles on to it and, once formed, show the model of their place in the box.
 */
public abstract class ThermalCasingBlock extends Block implements EntityBlock, IWrenchable {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected ThermalCasingBlock(Properties properties) { super(properties); }

    /** Looks for a complete box and lets the machines nearby check themselves again. */
    protected abstract void changed(Level level, BlockPos pos);

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ThermalPartBlockEntity(pos, state); }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (old.getBlock() != state.getBlock()) changed(level, pos);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (next.getBlock() != state.getBlock()) changed(level, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
