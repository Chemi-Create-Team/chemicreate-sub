package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialPipeBlockEntity extends FluidPipeBlockEntity implements IHaveGoggleInformation {
    public MaterialPipeBlockEntity(BlockPos p,BlockState s) { super(CETransport.PIPE.get(),p,s); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> list) {
        super.addBehaviours(list);
        list.removeIf(BracketedBlockEntityBehaviour.class::isInstance);
        list.add(new BoilerSteamOutput(this));
    }
    @Override public boolean addToGoggleTooltip(List<Component> lines,boolean sneaking) {
        net.createplant.transport.PipeMaterial.of(getBlockState()).describe(lines);
        return true;
    }
}
