package net.createplant.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Stateless endpoint: the formed controller owns every tank and returns the handler appropriate to this labelled penetration. */
public class FusionFluidPortBlockEntity extends SmartBlockEntity {
    public FusionFluidPortBlockEntity(BlockPos pos, BlockState state) { super(CEFusion.PORT_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public FusionFluidPortBlock.Kind kind() { return ((FusionFluidPortBlock) getBlockState().getBlock()).kind; }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEFusion.PORT_ENTITY.get(), (port, side) -> {
            FusionReactorControllerBlockEntity controller = FusionReactorControllerBlockEntity.find(port.getLevel(), port.getBlockPos());
            return controller != null && controller.owns(port.getBlockPos()) ? net.createplant.mixture.GasMixturePort.both(controller.port(port.kind()), controller) : null;
        });
    }
}
