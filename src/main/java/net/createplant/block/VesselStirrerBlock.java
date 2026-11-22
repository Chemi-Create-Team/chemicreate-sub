package net.createplant.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.VesselStirrerBlockEntity;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stirrer of a stainless tank: it stands directly under the bottom centre of a 3x3 tank two or more layers high and, like Create's mechanical mixer,
 * is turned by cogwheels meshing with the gear round its middle. It turns the impellers in the tank's two lowest layers.
 */
public class VesselStirrerBlock extends KineticBlock implements IBE<VesselStirrerBlockEntity>, ICogWheel {
    public VesselStirrerBlock(Properties properties) { super(properties); }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return false; }

    /** The tank over it starts or stops stirring. */
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!state.is(old.getBlock())) ReactorStructure.changed(level, pos);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!state.is(next.getBlock())) ReactorStructure.changed(level, pos);
    }

    @Override public Class<VesselStirrerBlockEntity> getBlockEntityClass() { return VesselStirrerBlockEntity.class; }
    @Override public BlockEntityType<? extends VesselStirrerBlockEntity> getBlockEntityType() { return CEReactor.STIRRER_ENTITY.get(); }
}
