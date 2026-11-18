package net.createplant.block;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A passive but structurally distinct tokamak component; goggles proxy to the controller of the complete machine. */
public class FusionPartBlock extends Block implements IProxyHoveringInformation {
    public FusionPartBlock(Properties properties) { super(properties); }

    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        FusionReactorControllerBlockEntity controller = FusionReactorControllerBlockEntity.find(level, pos);
        return controller == null ? pos : controller.getBlockPos();
    }
}
