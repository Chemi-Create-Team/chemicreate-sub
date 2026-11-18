package net.createplant.block;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import net.createplant.nuclear.ReactorCores;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A plain part of an RBMK vault, the biological shield or a fuel channel tube: goggles on it read the whole reactor. */
public class VaultBlock extends Block implements IProxyHoveringInformation {
    public VaultBlock(Properties properties) { super(properties); }

    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) { return ReactorCores.controllerUnder(level, pos); }
}
