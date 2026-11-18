package net.createplant.block.entity;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public class HighPressureTurbineEndBlockEntity extends KineticBlockEntity {
    public HighPressureTurbineEndBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_END.get(), pos, state);
    }

    private net.createplant.block.TurbineUnit.Layout unit;
    public net.createplant.block.TurbineUnit.Layout unitLayout(){return unit;}
    public void setUnitLayout(net.createplant.block.TurbineUnit.Layout layout){unit=layout;setChanged();sendData();}
    @Override protected void write(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries,boolean clientPacket){super.write(tag,registries,clientPacket);net.createplant.block.TurbineUnit.writeLayout(tag,unit);}
    @Override protected void read(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries,boolean clientPacket){super.read(tag,registries,clientPacket);unit=net.createplant.block.TurbineUnit.readLayout(tag);}

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_END.get(), HighPressureTurbineEndBlockEntity::fluidPort);
    }

    /** Keep the center end-cap axis free for Create shafts; the surrounding end ring remains the fluid manifold. */
    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluidPort(Direction side) {
        if (side == null) {
            return exhaust;
        }
        BlockState state = getBlockState();
        boolean center = HighPressureTurbineEndBlock.ringOffsetFromState(
                state.getValue(HighPressureTurbineEndBlock.RING_X)) == 0
            && HighPressureTurbineEndBlock.ringOffsetFromState(
                state.getValue(HighPressureTurbineEndBlock.RING_Y)) == 0;
        return center && side.getAxis() == state.getValue(HighPressureTurbineEndBlock.AXIS)
            ? null
            : exhaust;
    }

    /** Reach the nearest rotor through at most sixteen loaded shaft cells. */
    public SteamTurbineBlockEntity exhaustSource() {
        if (isRemoved() || level==null) return null;
        BlockState end=getBlockState();BlockPos center=HighPressureTurbineEndBlock.centerPos(worldPosition,end);
        if (!level.hasChunkAt(center) || !(level.getBlockState(center).getBlock() instanceof HighPressureTurbineEndBlock)) return null;
        Direction.Axis axis=end.getValue(HighPressureTurbineEndBlock.AXIS);
        Direction inner=Direction.fromAxisAndDirection(axis,end.getValue(HighPressureTurbineEndBlock.POSITIVE)
            ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        for (int n=1;n<=16;n++) {
            BlockPos pos=center.relative(inner,n);
            if (!level.hasChunkAt(pos)) return null;
            BlockState section=level.getBlockState(pos);
            if (!isMatchingTurbineSection(section,axis)) return null;
            if (level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine)
                return turbine;
        }
        return null;
    }
    private final net.neoforged.neoforge.fluids.capability.IFluidHandler exhaust=LiveMachinePorts.fluid(this,
        new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
                var source=exhaustSource();return source==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : source.getSteamCapability().getFluidInTank(1);
            }
            @Override public int getTankCapacity(int tank) { var source=exhaustSource();return source==null ? 0 : source.getSteamCapability().getTankCapacity(1); }
            @Override public boolean isFluidValid(int tank,net.neoforged.neoforge.fluids.FluidStack stack) { var source=exhaustSource();return source!=null && source.getSteamCapability().isFluidValid(0,stack); }
            @Override public int fill(net.neoforged.neoforge.fluids.FluidStack stack,FluidAction action) { var source=exhaustSource();return source==null ? 0 : source.getSteamCapability().fill(stack,action); }
            @Override public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack stack,FluidAction action) {
                var source=exhaustSource();return source==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : source.getSteamCapability().drain(stack,action);
            }
            @Override public net.neoforged.neoforge.fluids.FluidStack drain(int amount,FluidAction action) {
                var source=exhaustSource();return source==null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : source.getSteamCapability().drain(amount,action);
            }
        });

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        try {
            if (level == null) {
                return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
            }

            BlockState endState = getBlockState();
            Direction.Axis axis = endState.getValue(HighPressureTurbineEndBlock.AXIS);
            BlockPos center = HighPressureTurbineEndBlock.centerPos(worldPosition, endState);
            Direction inner = Direction.fromAxisAndDirection(axis,
                endState.getValue(HighPressureTurbineEndBlock.POSITIVE)
                    ? Direction.AxisDirection.NEGATIVE
                    : Direction.AxisDirection.POSITIVE);

            BlockPos cursor = center.relative(inner);
            for (int distance = 0; distance < 16; distance++, cursor = cursor.relative(inner)) {
                BlockState state = level.getBlockState(cursor);
                if (!isMatchingTurbineSection(state, axis)) {
                    break;
                }

                BlockEntity blockEntity = level.getBlockEntity(cursor);
                if (blockEntity instanceof SteamTurbineBlockEntity turbine) {
                    return turbine.addToGoggleTooltip(tooltip, isPlayerSneaking);
                }

            }

            return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        } finally { net.createplant.compat.display.MachineReadings.compact(tooltip); }
    }

    private static boolean isMatchingTurbineSection(BlockState state, Direction.Axis axis) {
        return state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) == axis
            && (SteamTurbineBlock.isTurbine(state)
                || com.simibubi.create.AllBlocks.SHAFT.has(state)
                || CEBlocks.isHighPressureSteamTurbine(state)
                || CEBlocks.isLowPressureSteamTurbine(state));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3);
    }
}
