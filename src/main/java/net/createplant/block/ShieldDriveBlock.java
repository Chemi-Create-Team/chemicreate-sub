package net.createplant.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ShieldDriveBlockEntity;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rotation input of an RBMK vault, a shaft through the biological shield. The core it belongs to runs every integrated
 * control-rod drive at its speed, so no shaft is needed inside the vault; it bears the drives' stress.
 */
public class ShieldDriveBlock extends RotatedPillarKineticBlock implements IBE<ShieldDriveBlockEntity>, com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    public ShieldDriveBlock(Properties properties) { super(properties); }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face.getAxis() == state.getValue(AXIS); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }

    /** Goggles on any part of the vault read the whole reactor. */
    @Override public net.minecraft.core.BlockPos getInformationSource(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return net.createplant.nuclear.ReactorCores.controllerUnder(level, pos);
    }

    @Override public Class<ShieldDriveBlockEntity> getBlockEntityClass() { return ShieldDriveBlockEntity.class; }
    @Override public BlockEntityType<? extends ShieldDriveBlockEntity> getBlockEntityType() { return CENuclear.SHIELD_DRIVE_ENTITY.get(); }
}
