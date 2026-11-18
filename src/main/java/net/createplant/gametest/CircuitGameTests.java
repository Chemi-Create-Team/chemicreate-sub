package net.createplant.gametest;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.entity.FuelCellBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFuelCell;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** The filter-press cell takes its power from a real Electro Energetics circuit. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CircuitGameTests {
    private static BlockPos battery(GameTestHelper h, BlockPos relative) {
        var pos = h.absolutePos(relative);
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery")).defaultBlockState(), 3);
        return pos;
    }
    /** Sets the battery to a direct voltage and runs copper wire from its two terminals to the device's. */
    private static void wire(GameTestHelper h, BlockPos battery, BlockPos device, double volts) {
        var cell = DevicesSavedData.load(h.getLevel()).getDevice(battery, CreativeBatteryDevice.class);
        h.assertTrue(cell != null, "The battery is a circuit device");
        cell.voltage = volts;cell.acFrequency = 0;
        connect(h, battery, device);
    }
    /** Copper wire from node 0 to node 0 and node 1 to node 1 of two devices. */
    private static void connect(GameTestHelper h, BlockPos a, BlockPos b) {
        var wires = InfrastructureSavedData.load(h.getLevel());
        var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
        for (int node = 0; node < 2; node++) wires.connect(new InWorldNode(node, a), new InWorldNode(node, b), copper);
    }
    @GameTest(template = "test_petrochemical", timeoutTicks = 200)
    public static void aWiredBatteryRunsTheElectrolysisCellThroughItsPanelTerminals(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);
        ElectrolysisCellGameTests.feed(owner, new FluidStack(Fluids.WATER, 900));
        var source = battery(h, new BlockPos(1, 1, 1));
        var terminals = owner.structure().terminal();
        h.runAfterDelay(25, () -> wire(h, source, terminals, 400));
        h.runAfterDelay(120, () -> {
            h.assertTrue(owner.watts() > 900 && owner.stored(1) > 0 && owner.volts() > 390, "The native two-wire circuit drives the shared panel owner: " + owner.watts() + " W, " + owner.volts() + " V, " + owner.stored(1) + " mB H2");h.succeed();
        });
    }
}
