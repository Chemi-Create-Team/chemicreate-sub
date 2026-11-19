package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.registry.CEBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Create's actual pressure-driven conduit, with the addon's own registry ID. */
public final class ResinPipeBlockEntity extends FluidPipeBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public ResinPipeBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntityTypes.RESIN_PIPE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // The opaque multipart resin model has its own collars and no dynamic
        // bracket model. Reject bracket insertion instead of hiding an attached
        // item that the player would then need to recover blindly.
        behaviours.removeIf(BracketedBlockEntityBehaviour.class::isInstance);
    }

    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> lines, boolean sneaking) {
        net.createplant.transport.PipeMaterial.of(getBlockState()).describe(lines);
        return true;
    }
}
