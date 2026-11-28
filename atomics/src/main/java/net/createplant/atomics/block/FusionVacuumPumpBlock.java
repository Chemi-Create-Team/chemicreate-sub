package net.createplant.atomics.block;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.atomics.block.entity.FusionVacuumPumpBlockEntity;
import net.createplant.atomics.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Mechanical forepump and cryopump drive: it must keep turning while fuel and helium ash are exhausted. */
public class FusionVacuumPumpBlock extends RotatedPillarKineticBlock
    implements IBE<FusionVacuumPumpBlockEntity>, IProxyHoveringInformation {
    public FusionVacuumPumpBlock(Properties properties) { super(properties); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        FusionReactorControllerBlockEntity controller = FusionReactorControllerBlockEntity.find(level, pos);
        return controller == null ? pos : controller.getBlockPos();
    }
    @Override public Class<FusionVacuumPumpBlockEntity> getBlockEntityClass() { return FusionVacuumPumpBlockEntity.class; }
    @Override public BlockEntityType<? extends FusionVacuumPumpBlockEntity> getBlockEntityType() { return CEFusion.VACUUM_PUMP_ENTITY.get(); }
}
