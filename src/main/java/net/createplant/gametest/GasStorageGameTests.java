package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.registry.CEFluids;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GasStorageGameTests {
    private static FluidStack gas(int amount) { return FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),300); }
    @GameTest(template="test_fractionation")
    public static void compressedGasStorageSeparatesReferenceQuotaFromPhysicalVolume(GameTestHelper h) {
        var tank=new ThermalFluidTank(1000,f->{});tank.fill(gas(12000),EXECUTE);
        h.assertTrue(tank.getFluidAmount()==12000 && tank.getCapacity()==16000 && tank.volumeCapacity()==1000,"Compressed stock and physical vessel volume use distinct units");
        int before=tank.getFluidAmount();tank.setCapacity(500);
        h.assertTrue(tank.getFluidAmount()==before && tank.fill(gas(1),EXECUTE)==0,"Shrinking retains finite gas stock and rejects additional filling until space returns");h.succeed();
    }
}
