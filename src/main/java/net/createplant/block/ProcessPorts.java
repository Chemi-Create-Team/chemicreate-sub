package net.createplant.block;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.createplant.CreatePlant;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.ColumnRingBlockEntity;
import net.createplant.block.entity.ConverterShellBlockEntity;
import net.createplant.block.entity.CubicPressBlockEntity;
import net.createplant.block.entity.ElectrolysisFurnacePartBlockEntity;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.FuelCycleMachineBlockEntity;
import net.createplant.block.entity.FusionFluidPortBlockEntity;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.IndustrialDispatchBlockEntity;
import net.createplant.block.entity.PressurePumpPortBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.block.entity.WetProcessPartBlockEntity;
import net.createplant.transport.HoseSocket;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class ProcessPorts {
    public enum Kind { INLET, OUTLET, HOSE, SHAFT, HATCH, DRAIN }

    private ProcessPorts() { }

    public static boolean plant(BlockEntity be) {
        return !be.isRemoved() && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace().equals(CreatePlant.MOD_ID);
    }

    public static Kind at(BlockEntity be, Direction face) {
        var state = be.getBlockState();
        var level = be.getLevel();
        if (level == null || !plant(be) || face == null) return null;
        var pos = be.getBlockPos();
        if (be instanceof HoseSocket.Endpoint hose && hose.hoseFace() == face) return hose.hoseRole() < 0 ? Kind.INLET : Kind.HOSE;
        if (state.getBlock() instanceof IRotate rotation && !(be instanceof GeneratingKineticBlockEntity)
            && rotation.hasShaftTowards(level, pos, state, face)) return Kind.SHAFT;
        if (be instanceof FluidPipeBlockEntity) return null;
        if (be instanceof HorizontalTankBlockEntity tank && tank.isSteamOutlet(face)) return Kind.OUTLET;
        if (be instanceof ReactionTowerBlockEntity tower) {
            if (face == Direction.UP && tower.hasServiceHatch()) return Kind.HATCH;
            if (fluid(be, face) == null) return null;
            if (face == Direction.DOWN) return Kind.DRAIN;
            return tower.owner().getBlockPos().getY() == pos.getY() ? Kind.INLET : Kind.OUTLET;
        }
        if (be instanceof LiddedVesselBlockEntity vessel) {
            var owner = vessel.getControllerBE();
            if (owner == null) return null;
            if (face == Direction.UP && state.getValue(LiddedVesselBlock.HATCH) != LiddedVesselBlock.Hatch.NONE) return Kind.HATCH;
            if (face == Direction.DOWN && state.getValue(LiddedVesselBlock.BOTTOM) && fluid(be, face) != null) return Kind.DRAIN;
        }
        if (be instanceof ColumnRingBlockEntity && face.getAxis().isHorizontal() && fluid(be, face) != null) {
            var centre = ColumnRingBlock.centre(level, pos, state);
            if (centre != null && level.getBlockEntity(centre) instanceof AbsorptionTowerBlockEntity storey && !storey.bottom()) return Kind.OUTLET;
        }
        if (be instanceof FractionatingColumnBlockEntity) {
            if (face == Direction.EAST && state.getValue(FractionatingColumnBlock.INLET) > 0) return Kind.INLET;
            if (face == Direction.NORTH && state.getValue(FractionatingColumnBlock.CUT) > 0) return Kind.OUTLET;
            return null;
        }
        if (be instanceof WetProcessPartBlockEntity && state.getBlock() instanceof WetProcessPartBlock part) {
            if (items(be, face) != null) return Kind.HATCH;
            if (fluid(be, face) == null) return null;
            return part.role == WetProcessPartBlock.Role.FEED ? Kind.INLET : Kind.DRAIN;
        }
        if (be instanceof TankCasingBlockEntity casing && casing.clarifier() != null) {
            if (casing.itemPort(face) != null) return Kind.HATCH;
            if (casing.fluidPort(face) == null) return null;
            return casing.clarifier().rimPort(pos) == WetProcessStructure.RimPort.SLURRY_INLET ? Kind.INLET : Kind.OUTLET;
        }
        if (be instanceof FusionFluidPortBlockEntity port) {
            if (face != state.getValue(FusionFluidPortBlock.FACING)) return null;
            return switch (port.kind()) {
                case ASH, HELIUM_OUT, COOLANT_OUT -> Kind.OUTLET;
                default -> Kind.INLET;
            };
        }
        if (be instanceof ElectrodeHeadBlockEntity && face == Direction.UP || be instanceof RodCapBlockEntity && face == Direction.UP)
            return Kind.HATCH;
        if (items(be, face) != null) return Kind.HATCH;
        var fluid = fluid(be, face);
        if (fluid == null) return null;
        if (be instanceof PressurePumpPortBlockEntity || be instanceof GasifierBlockEntity)
            return face == Direction.UP || be instanceof PressurePumpPortBlockEntity && state.getValue(PressurePumpCasingBlock.PART) == PressurePumpStructure.Part.FEED ? Kind.OUTLET : Kind.INLET;
        if (face != Direction.DOWN) for (int slot = 0; slot < fluid.getTanks(); slot++) {
            var content = fluid.getFluidInTank(slot);
            if (!content.isEmpty() && !fluid.isFluidValid(slot, content)
                && !fluid.drain(content.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE).isEmpty()) return Kind.OUTLET;
        }
        return face == Direction.DOWN ? Kind.OUTLET : Kind.INLET;
    }

    public static IFluidHandler fluid(BlockEntity be, Direction face) {
        return be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), face);
    }

    public static IItemHandler items(BlockEntity be, Direction face) {
        if (be instanceof TankCasingBlockEntity casing && casing.clarifier() != null) return casing.itemPort(face);
        if (be instanceof GasifierBlockEntity || be instanceof WetProcessPartBlockEntity || be instanceof FuelCycleMachineBlockEntity
            || be instanceof ElectrolysisFurnacePartBlockEntity || be instanceof CubicPressBlockEntity || be instanceof ConverterShellBlockEntity
            || be instanceof FuelPoolBlockEntity || be instanceof IndustrialDispatchBlockEntity)
            return be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), face);
        return null;
    }

    public static ItemStack shownItem(BlockEntity be, Direction face) {
        if (be instanceof ReactionTowerBlockEntity part && part.hasServiceHatch()) return part.owner().topCatalyst();
        if (be instanceof ElectrodeHeadBlockEntity head && face == Direction.UP)
            return head.electrodes.getStackInSlot(head.electrodes.getStackInSlot(0).isEmpty() ? 1 : 0);
        var handler = items(be, face);
        if (handler != null) for (int slot = 0; slot < handler.getSlots(); slot++) {
            var stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }
}
