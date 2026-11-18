package net.createplant.block.entity;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.createplant.block.HighPressureTurbinePartBlock;
import net.createplant.registry.CEBlockEntityTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HighPressureTurbinePartBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public HighPressureTurbinePartBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_PART.get(), pos, state);
    }

    private net.createplant.block.TurbineUnit.Layout unit;
    public net.createplant.block.TurbineUnit.Layout unitLayout(){return unit;}
    public void setUnitLayout(net.createplant.block.TurbineUnit.Layout layout){unit=layout;setChanged();}
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.saveAdditional(tag,registries);net.createplant.block.TurbineUnit.writeLayout(tag,unit);}
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.loadAdditional(tag,registries);unit=net.createplant.block.TurbineUnit.readLayout(tag);}

    private final net.neoforged.neoforge.fluids.capability.IFluidHandler drainPort=new net.neoforged.neoforge.fluids.capability.IFluidHandler(){
        public int getTanks(){return 1;}public int getTankCapacity(int i){return rotor()==null ? 0 : rotor().liquidPort().getTankCapacity(i);}
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int i){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().liquidPort().getFluidInTank(i);}
        public boolean isFluidValid(int i,net.neoforged.neoforge.fluids.FluidStack f){return false;}public int fill(net.neoforged.neoforge.fluids.FluidStack f,FluidAction a){return 0;}
        public net.neoforged.neoforge.fluids.FluidStack drain(int n,FluidAction a){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().liquidPort().drain(n,a);}
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack f,FluidAction a){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().liquidPort().drain(f,a);}
    };
    private final net.neoforged.neoforge.fluids.capability.IFluidHandler port=new net.neoforged.neoforge.fluids.capability.IFluidHandler(){
        public int getTanks(){return 2;}public int getTankCapacity(int i){return rotor()==null ? 0 : rotor().getSteamCapability().getTankCapacity(i);}
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int i){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().getSteamCapability().getFluidInTank(i);}
        public boolean isFluidValid(int i,net.neoforged.neoforge.fluids.FluidStack f){return rotor()!=null && rotor().getSteamCapability().isFluidValid(i,f);}
        public int fill(net.neoforged.neoforge.fluids.FluidStack f,FluidAction a){return rotor()==null ? 0 : rotor().getSteamCapability().fill(f,a);}
        public net.neoforged.neoforge.fluids.FluidStack drain(int n,FluidAction a){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().getSteamCapability().drain(n,a);}
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack f,FluidAction a){return rotor()==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : rotor().getSteamCapability().drain(f,a);}
    };
    public SteamTurbineBlockEntity rotor(){if(isRemoved() || level==null || !level.hasChunkAt(centerPos()))return null;if(!getBlockState().getValue(HighPressureTurbinePartBlock.ATTACHED))return null;return level.getBlockEntity(centerPos()) instanceof SteamTurbineBlockEntity rotor ? rotor : null;}
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event){event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_PART.get(),(be,side)->side==net.minecraft.core.Direction.DOWN ? be.drainPort : be.port);}
    public BlockPos centerPos() {
        return HighPressureTurbinePartBlock.centerPos(worldPosition, getBlockState());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            if (unit != null) {
                var offset = worldPosition.subtract(unit.origin());
                int across = unit.axis() == net.minecraft.core.Direction.Axis.X ? offset.getZ() : offset.getX();
                if (unit.axis().choose(offset.getX(), offset.getY(), offset.getZ()) == 0
                        && (across != 0 || offset.getY() != 0)
                        && net.createplant.block.TurbineUnit.occupied(unit.radius(), across, offset.getY())) {
                    var expected = HighPressureTurbinePartBlock.expectedCasingState(unit.axis(), across, offset.getY())
                            .setValue(HighPressureTurbinePartBlock.SECTION, true);
                    if (!state.equals(expected)) level.setBlock(worldPosition, expected, net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
                return;
            }
            if (state.getValue(HighPressureTurbinePartBlock.CASING)) {
                HighPressureTurbinePartBlock.refreshAttachment(level, worldPosition, state);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        try {
            if (level == null) {
                return false;
            }
            if (!getBlockState().getValue(HighPressureTurbinePartBlock.ATTACHED)) {
                return false;
            }
            BlockEntity blockEntity = level.getBlockEntity(centerPos());
            if (blockEntity instanceof SteamTurbineBlockEntity turbine) {
                return turbine.addToGoggleTooltip(tooltip, isPlayerSneaking);
            }
            return false;
        } finally { net.createplant.compat.display.MachineReadings.compact(tooltip); }
    }
}
