package net.createplant.atomics.compat;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.TopFaceArmInteractionPoint;
import net.createplant.atomics.registry.CEHighStressMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class CubicPressArmPoint extends ArmInteractionPointType {
    @Override public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) { return state.is(CEHighStressMachines.PRESS.get()); }
    @Override public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new TopFaceArmInteractionPoint(this, level, pos, state);
    }
}
