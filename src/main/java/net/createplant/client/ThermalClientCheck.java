package net.createplant.client;

import java.util.ArrayList;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Opt-in client verification of the native goggles method, which Create keeps client-dependent. */
public final class ThermalClientCheck {
    private ThermalClientCheck() { }
    public static void verify(Minecraft mc) throws java.io.IOException {
        var tank=AllBlockEntityTypes.FLUID_TANK.create(BlockPos.ZERO,AllBlocks.FLUID_TANK.getDefaultState());
        tank.setLevel(mc.level);
        tank.getTankInventory().fill(FluidThermal.at(new FluidStack(Fluids.WATER,1000),300),EXECUTE);
        tank.getTankInventory().fill(FluidThermal.at(new FluidStack(Fluids.WATER,1000),400),EXECUTE);
        var tooltip=new ArrayList<Component>();tank.addToGoggleTooltip(tooltip,false);
        var lines=tooltip.stream().map(Component::getString).toList();
        String actual=String.format(java.util.Locale.ROOT,"%.2f",FluidThermal.kelvin(tank.getTankInventory().getFluid())-273.15);
        if (tank.getTankInventory().getFluidAmount()!=2000 || Math.abs(FluidThermal.energy(tank.getTankInventory().getFluid())-2*4180*350)>1e-6 || lines.stream().noneMatch(line->line.contains(actual)))
            throw new IllegalStateException("Native tank temperature tooltip failed: "+lines);
        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("thermal-client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(lines));
    }
}
