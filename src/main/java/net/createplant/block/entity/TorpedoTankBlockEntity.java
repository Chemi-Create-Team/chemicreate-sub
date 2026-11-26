package net.createplant.block.entity;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.CreatePlant;
import net.createplant.registry.CEConverter;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class TorpedoTankBlockEntity extends HorizontalTankBlockEntity {
    private static final TagKey<net.minecraft.world.level.material.Fluid> MOLTEN_METALS =
        TagKey.create(Registries.FLUID, CreatePlant.location("torpedo_molten_metals"));

    public TorpedoTankBlockEntity(BlockPos pos, BlockState state) {
        super(CEConverter.TORPEDO_ENTITY.get(), pos, state);
    }

    public static void registerTorpedoCapability(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEConverter.TORPEDO_ENTITY.get(),
            (tank, side) -> tank.tankPort(side));
    }

    public static boolean accepts(FluidStack stack) {
        return !stack.isEmpty() && stack.is(MOLTEN_METALS) && FluidThermal.valid(stack)
            && !FluidState.gas(stack) && FluidState.temperature(stack) == FluidState.MAX
            && FluidState.pressure(stack) == FluidState.NORMAL_PRESSURE;
    }

    @Override protected SmartFluidTank createInventory() {
        var tank = new SmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
        tank.setValidator(TorpedoTankBlockEntity::accepts);
        return tank;
    }

    @Override public void updateBoilerState() { }
}
