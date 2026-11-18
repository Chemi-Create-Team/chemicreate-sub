package net.createplant.block.entity;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.TankCells;
import net.createplant.registry.CETransport;
import net.createplant.thermal.GradeFluidTank;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;

/** Native Create connectivity and graded gas storage, constrained to one axial tube. */
public class PulsationTubeBlockEntity extends PlantFluidTankBlockEntity {
    public float dial,previousDial;
    public PulsationTubeBlockEntity(BlockPos pos,BlockState state){super(pos,state);}
    @Override public Direction.Axis getMainConnectionAxis(){return getBlockState().getValue(PulsationTubeBlock.AXIS);}
    @Override public int getMaxWidth(){return 1;}
    @Override public int getMaxLength(Direction.Axis axis,int width){return axis==getMainConnectionAxis() ? getMaxHeight() : 1;}
    @Override public void updateBoilerState(){ }
    @Override public void toggleWindows(){ }
    @Override public void setWindows(boolean enabled){window=false;}
    @Override public void notifyMultiUpdated(){
        if(level==null)return;
        var axis=getMainConnectionAxis();int mine=axis.choose(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
        var start=getController();int first=axis.choose(start.getX(),start.getY(),start.getZ());
        var state=getBlockState().setValue(FluidTankBlock.BOTTOM,mine==first).setValue(FluidTankBlock.TOP,mine==first+height-1).setValue(FluidTankBlock.SHAPE,FluidTankBlock.Shape.PLAIN);
        if(!state.equals(getBlockState()))level.setBlock(worldPosition,state,6);
        onFluidStackChanged(tankInventory.getFluid());invalidateCapabilities();setChanged();
    }
    @Override protected void onFluidStackChanged(FluidStack fluid){
        if(level==null)return;
        TankCells.forEach(this,at->level.updateNeighbourForOutputSignal(at,getBlockState().getBlock()));
        if(!level.isClientSide){setChanged();sendData();}
        if(isVirtual() && getFluidLevel()==null)setFluidLevel(LerpedFloat.linear().startWithValue(getFillState()));
        if(getFluidLevel()!=null)getFluidLevel().chase(getFillState(),.5,LerpedFloat.Chaser.EXP);
    }
    public boolean connects(Direction side){
        if(side==null || side.getAxis()!=getMainConnectionAxis() || isRemoved())return false;
        var root=getControllerBE();if(root==null)return false;
        var axis=getMainConnectionAxis();int mine=axis.choose(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
        var at=root.getBlockPos();int first=axis.choose(at.getX(),at.getY(),at.getZ());
        return side.getAxisDirection()==Direction.AxisDirection.NEGATIVE ? mine==first : mine==first+root.getHeight()-1;
    }
    public float pressureDial(){var root=getControllerBE();if(root==null || root.getTankInventory().isEmpty())return 0;return (FluidHazards.of(root.getTankInventory().getFluid()).pressure().ordinal()+1)/3f;}
    @Override public void tick(){super.tick();if(level!=null && level.isClientSide){previousDial=dial;dial+=(pressureDial()-dial)*.25f;}}
    @Override protected AABB createRenderBoundingBox(){return isController() ? new AABB(worldPosition).minmax(new AABB(TankCells.at(this,height-1,0,0))) : new AABB(worldPosition);}
    public static void registerCapabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CETransport.GAS_RECEIVER_ENTITY.get(),(be,side)->be.connects(side) ? GradeFluidTank.port(be,side,be.fluidCapability,()->be.connects(side)) : null);}
}
