package net.createplant.block.entity;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.block.PlantFluidTankBlock;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Create's tank logic with the owning block's fluid filter applied to the shared multiblock inventory. */
public class PlantFluidTankBlockEntity extends FluidTankBlockEntity {
    public boolean insulated() { return getBlockState().is(CETransport.CRYOGENIC_TANK.get()); }

    public PlantFluidTankBlockEntity(BlockPos pos, BlockState state) { super(((PlantFluidTankBlock)state.getBlock()).getBlockEntityType(),pos,state); }
    @Override protected SmartFluidTank createInventory() {
        // Like Create's own tank, the wall exchanges heat with the air; the cryogenic tank's vacuum jacket makes that zero.
        SmartFluidTank tank=new net.createplant.thermal.GradeFluidTank(getCapacityMultiplier(),this::onFluidStackChanged,this);
        tank.setValidator(((PlantFluidTankBlock)getBlockState().getBlock()).accepts);
        return tank;
    }
    @Override public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        addVesselBehaviour(behaviours);
    }
    protected void addVesselBehaviour(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {

    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        PulsationTubeBlockEntity.registerCapabilities(event);register(event,CETransport.PRESSURE_TANK_ENTITY.get());register(event,CETransport.STAINLESS_STEEL_TANK_ENTITY.get());register(event,CETransport.CRYOGENIC_TANK_ENTITY.get());AmmoniaSeparatorBlockEntity.register(event);ReactionTowerBlockEntity.register(event);LiddedVesselBlockEntity.register(event);
    }
    private static void register(RegisterCapabilitiesEvent event,BlockEntityType<PlantFluidTankBlockEntity> type) {
        // Create sets the handler in the constructor and refreshes it on every connectivity change.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,type,(be,side)->net.createplant.thermal.GradeFluidTank.port(be,side,be.fluidCapability));
    }
}
