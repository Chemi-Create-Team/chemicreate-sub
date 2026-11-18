package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.registry.CEGasPipes;
import net.createplant.transport.PipeMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/** A native Create pipe with reinforced material grades and gas-pipe geometry. */
public class GasPipeBlockEntity extends FluidPipeBlockEntity implements IHaveGoggleInformation {
    public GasPipeBlockEntity(BlockPos pos,BlockState state){super(CEGasPipes.GAS_PIPE_ENTITY.get(),pos,state);}
    @Override public void addBehaviours(List<BlockEntityBehaviour> list){super.addBehaviours(list);list.removeIf(com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour.class::isInstance);list.add(new BoilerSteamOutput(this));}
    @Override public boolean addToGoggleTooltip(List<Component> lines,boolean sneaking){PipeMaterial.of(getBlockState()).describe(lines);return true;}
}
