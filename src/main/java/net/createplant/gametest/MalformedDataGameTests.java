package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CEElectrolysis;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.LargeFlywheelBlockEntity;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEDistillation;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFurnace;
import net.createplant.thermal.GradeFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MalformedDataGameTests {
    private static final String[] FLYWHEEL_DOUBLES = {
        "StoredEnergyJ", "RotorRpm", "ChargePowerW", "DischargePowerW", "AccelerationPowerW", "MaintenancePowerW",
        "DisplayedEnergyJ", "DisplayedRotorRpm", "DisplayedChargePowerW", "DisplayedDischargePowerW",
        "DisplayedAccelerationPowerW", "DisplayedMaintenancePowerW", "DisplayedInertiaKgM2"
    };

    @GameTest(template = "test_fractionation")
    public static void malformedMachineValuesReturnToFiniteBounds(GameTestHelper h) {
        BlockPos furnacePos = new BlockPos(4, 2, 4), flywheelPos = furnacePos.east(3), columnPos = flywheelPos.east(3), reactorPos = columnPos.east(3);
        h.setBlock(furnacePos, CEFurnace.BUSBAR.get());
        h.setBlock(flywheelPos, CEBlocks.LARGE_FLYWHEEL.get());
        h.setBlock(columnPos, CEDistillation.COLUMN.get());
        var reactor = ReactorGameTests.assemble(h.getLevel(), h.absolutePos(reactorPos), ReactorModule.STIRRED, 1);

        var furnace = (ElectrolysisFurnaceBlockEntity) h.getBlockEntity(furnacePos);
        CompoundTag furnaceTag = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        furnaceTag.putDouble("Kelvin", Double.NaN);
        furnaceTag.putDouble("Progress", Double.POSITIVE_INFINITY);
        furnace.loadWithComponents(furnaceTag, h.getLevel().registryAccess());
        CompoundTag repairedFurnace = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(!repairedFurnace.contains("Kelvin") && Double.isFinite(repairedFurnace.getDouble("Progress")),
            "The electrolysis furnace rejects non-finite bath and progress values");

        var flywheel = (LargeFlywheelBlockEntity) h.getBlockEntity(flywheelPos);
        CompoundTag flywheelTag = flywheel.saveWithoutMetadata(h.getLevel().registryAccess());
        for (String key : FLYWHEEL_DOUBLES) flywheelTag.putDouble(key, Double.NaN);
        flywheelTag.putFloat("SuppliedCapacityPerRpm", Float.NaN);
        flywheelTag.putFloat("ChargingStressPerRpm", Float.POSITIVE_INFINITY);
        flywheel.loadWithComponents(flywheelTag, h.getLevel().registryAccess());
        h.assertTrue(Double.isFinite(flywheel.storedEnergyJ()) && Double.isFinite(flywheel.rotorRpm())
                && Double.isFinite(flywheel.displayedEnergyJ()) && Double.isFinite(flywheel.displayedInertiaKgM2()),
            "The flywheel rejects non-finite physical and displayed values");

        var column = (FractionatingColumnBlockEntity) h.getBlockEntity(columnPos);
        CompoundTag columnTag = column.saveWithoutMetadata(h.getLevel().registryAccess());
        columnTag.putFloat("Progress", Float.NaN);
        column.loadWithComponents(columnTag, h.getLevel().registryAccess());
        h.assertTrue(Float.isFinite(column.progress()), "The fractionating column rejects NaN progress");

        CompoundTag reactorTag = reactor.saveWithoutMetadata(h.getLevel().registryAccess());
        reactorTag.putFloat("Progress", Float.NaN);
        reactor.loadWithComponents(reactorTag, h.getLevel().registryAccess());
        h.assertTrue(Float.isFinite(reactor.progress()), "The chemical reactor rejects NaN progress");

        var electrolysis = new net.createplant.block.entity.ElectrolysisControllerBlockEntity(BlockPos.ZERO, CEElectrolysis.CONTROLLER.get().defaultBlockState());
        var malformed = new CompoundTag();malformed.putDouble("Joules", Double.POSITIVE_INFINITY);malformed.putDouble("Consumed", Double.NaN);malformed.putInt("Limit", Integer.MAX_VALUE);
        electrolysis.loadWithComponents(malformed, h.getLevel().registryAccess());
        h.assertTrue(electrolysis.joules() == 0 && electrolysis.consumed() == 0 && electrolysis.limit() == 100, "Invalid saved electrical energy is rejected and the physical duty setting remains bounded");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void malformedPressureAndTemperatureValuesReturnToFiniteBounds(GameTestHelper h) {
        var phase = new GradeFluidTank(1_000, stack -> { }, null);
        phase.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(), 500), EXECUTE);
        CompoundTag phaseTag = phase.writeToNBT(h.getLevel().registryAccess(), new CompoundTag());
        phaseTag.putDouble("GasPressure", Double.POSITIVE_INFINITY);
        phase.readFromNBT(h.getLevel().registryAccess(), phaseTag);
        h.assertTrue(phase.getFluidAmount()==500 && !phase.writeToNBT(h.getLevel().registryAccess(),new CompoundTag()).contains("GasPressure"),
            "Retired pressure data is ignored and never written again");

        BlockPos turbinePos = new BlockPos(4, 2, 10);
        h.setBlock(turbinePos, CEBlocks.STEAM_TURBINE.get());
        var turbine = (SteamTurbineBlockEntity) h.getBlockEntity(turbinePos);
        CompoundTag turbineTag = new CompoundTag();
        turbineTag.putDouble("RotorWear", Double.NaN);
        turbine.thermodynamics().read(turbineTag, h.getLevel().registryAccess());
        h.assertTrue(turbine.thermodynamics().wear() == 0,
            "A turbine rejects a non-finite saved wear");

        h.succeed();
    }
}
