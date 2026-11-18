package net.createplant.gametest;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrolysisFurnacePartBlock;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.compat.electro.FurnaceBusbarBlock;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ElectrolysisFurnaceGameTests {
    /** A furnace whose anode stands at (4, 4, 4), with its busbar three blocks south facing north. */
    private static ElectrolysisFurnaceBlockEntity build(GameTestHelper h) {
        var anode = h.absolutePos(new BlockPos(4, 4, 4));
        for (int y = -2; y <= 0; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            h.getLevel().setBlock(anode.offset(x, y, z), (y == 0 && x == 0 && z == 0 ? CEFurnace.ANODE : CEFurnace.SHELL).get().defaultBlockState(), 3);
        var busbar = anode.south(3);
        h.getLevel().setBlock(busbar, CEFurnace.BUSBAR.get().defaultBlockState().setValue(FurnaceBusbarBlock.FACING, Direction.NORTH), 3);
        return (ElectrolysisFurnaceBlockEntity)h.getLevel().getBlockEntity(busbar);
    }
    private static void run(ElectrolysisFurnaceBlockEntity furnace, double volts, int ticks) {
        for (int i = 0; i < ticks; i++) { furnace.setVoltageSquared(volts * volts);furnace.tick(); }
    }
    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void currentMeltsTheBathAndReducesAluminaAtTheCarbonAnode(GameTestHelper h) {
        var furnace = build(h);run(furnace, 0, 21);
        h.assertTrue(furnace.formed(), "Shell, anode and busbar form the furnace: " + furnace.status());
        var top = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(2, 4, 4)), Direction.UP);
        var bottom = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(4, 2, 4)), Direction.DOWN);
        h.assertTrue(top.insertItem(ElectrolysisFurnaceBlockEntity.OXIDE, new ItemStack(CEMetals.ALUMINA.get(), 2), false).isEmpty()
            && top.insertItem(ElectrolysisFurnaceBlockEntity.CARBON, new ItemStack(Items.COAL, 2), false).isEmpty(), "The shell takes alumina and anode coal");
        h.assertTrue(!top.insertItem(ElectrolysisFurnaceBlockEntity.OXIDE, new ItemStack(Items.IRON_INGOT), true).isEmpty(), "Other items are refused");
        run(furnace, 0, 40);
        h.assertTrue(furnace.status().equals("power"), "Without current nothing happens: " + furnace.status());
        run(furnace, 60, 2000);
        h.assertTrue(furnace.status().equals("power") && bottom.getStackInSlot(2).isEmpty(),"Below the fixed electrical threshold no oxide is consumed");
        run(furnace, 200, 1600);
        var metal = bottom.extractItem(ElectrolysisFurnaceBlockEntity.PRODUCT, 64, false);
        h.assertTrue(metal.is(CEItems.ALUMINUM_INGOT.get()) && metal.getCount() >= 1, "200 V (20 kW) melts the bath and gives aluminium: " + metal + " " + furnace.status());
        h.assertTrue(top.extractItem(ElectrolysisFurnaceBlockEntity.PRODUCT, 1, true).isEmpty(), "Metal leaves only from the bottom");
        int ingots = metal.getCount();run(furnace, 200, 400);ingots += bottom.extractItem(ElectrolysisFurnaceBlockEntity.PRODUCT, 64, false).getCount();
        h.assertTrue(ingots == 2 && furnace.status().equals("materials") && furnace.inventory().getStackInSlot(ElectrolysisFurnaceBlockEntity.CARBON).isEmpty(),
            "Each ingot takes one alumina and one coal: " + ingots);
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(4, 4, 4))).getValue(ElectrolysisFurnacePartBlock.LIT)
            && h.getLevel().getBlockState(h.absolutePos(new BlockPos(4, 3, 2))).getValue(ElectrolysisFurnacePartBlock.LIT), "The anode and the sight windows glow over the molten bath");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(2, 2, 2))).getValue(ElectrolysisFurnacePartBlock.CELL) == 0
            && h.getLevel().getBlockState(h.absolutePos(new BlockPos(4, 3, 2))).getValue(ElectrolysisFurnacePartBlock.CELL) == 27, "Formed shells take the shape of their place");
        var saved = furnace.saveWithoutMetadata(h.getLevel().registryAccess());double work=saved.getDouble("Progress");
        furnace.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("Progress")==work, "Paid work survives a reload");
        h.getLevel().setBlock(h.absolutePos(new BlockPos(5, 2, 5)), Blocks.AIR.defaultBlockState(), 3);run(furnace, 200, 21);
        h.assertTrue(!furnace.formed() && furnace.watts() == 0 && furnace.status().equals("structure"), "A missing shell block breaks the furnace and its circuit");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(2, 2, 2))).getValue(ElectrolysisFurnacePartBlock.CELL) == ElectrolysisFurnacePartBlock.LOOSE, "A broken furnace loosens its shells");
        h.succeed();
    }
    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void titaniaAndZirconiaNeedTwiceTheWorkOfAlumina(GameTestHelper h) {
        var furnace = build(h);run(furnace, 0, 21);
        furnace.inventory().insertItem(ElectrolysisFurnaceBlockEntity.OXIDE, new ItemStack(CEMetals.TITANIUM_DIOXIDE.get()), false);
        furnace.inventory().insertItem(ElectrolysisFurnaceBlockEntity.CARBON, new ItemStack(Items.CHARCOAL), false);
        run(furnace, 200, 2200);
        h.assertTrue(furnace.inventory().getStackInSlot(ElectrolysisFurnaceBlockEntity.PRODUCT).is(CEItems.TITANIUM_INGOT.get()), "Titanium dioxide gives titanium");
        h.assertTrue(ElectrolysisFurnaceBlockEntity.reductions().get(CEMetals.ZIRCONIA.get()).metal() == CEItems.ZIRCONIUM_INGOT.get()
            && ElectrolysisFurnaceBlockEntity.reductions().get(CEMetals.ZIRCONIA.get()).joules() == 2 * ElectrolysisFurnaceBlockEntity.reductions().get(CEMetals.ALUMINA.get()).joules(),
            "Zirconia gives zirconium for twice the work of aluminium");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void breakingAnyShellStopsAReadyBatchImmediately(GameTestHelper h) {
        var furnace = build(h);run(furnace, 0, 21);
        furnace.inventory().insertItem(ElectrolysisFurnaceBlockEntity.OXIDE, new ItemStack(CEMetals.ALUMINA.get()), false);
        furnace.inventory().insertItem(ElectrolysisFurnaceBlockEntity.CARBON, new ItemStack(Items.COAL), false);
        var saved = furnace.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putDouble("Progress", ElectrolysisFurnaceBlockEntity.reductions().get(CEMetals.ALUMINA.get()).joules() - 500);
        furnace.loadWithComponents(saved, h.getLevel().registryAccess());
        h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        run(furnace, 200, 1);
        h.assertTrue(!furnace.formed() && furnace.inventory().getStackInSlot(ElectrolysisFurnaceBlockEntity.OXIDE).getCount() == 1
            && furnace.inventory().getStackInSlot(ElectrolysisFurnaceBlockEntity.CARBON).getCount() == 1
            && furnace.inventory().getStackInSlot(ElectrolysisFurnaceBlockEntity.PRODUCT).isEmpty(),
            "Removing one owned shell stops a nearly complete batch before the next tick");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 200)
    public static void aWiredCreativeBatteryDrivesCurrentThroughTheFurnace(GameTestHelper h) {
        var furnace = build(h);var level = h.getLevel();
        var battery = h.absolutePos(new BlockPos(8, 2, 6));
        level.setBlock(battery, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_battery")).defaultBlockState(), 3);
        h.runAfterDelay(25, () -> {
            var device = DevicesSavedData.load(level).getDevice(battery, CreativeBatteryDevice.class);
            h.assertTrue(device != null && furnace.formed(), "The battery is a circuit device and the furnace is formed");
            device.voltage = 200;device.acFrequency = 0;
            var wires = InfrastructureSavedData.load(level);
            var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
            wires.connect(new InWorldNode(0, battery), new InWorldNode(0, furnace.getBlockPos()), copper);
            wires.connect(new InWorldNode(1, battery), new InWorldNode(1, furnace.getBlockPos()), copper);
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(furnace.watts() > 1000 && furnace.volts() > 30, "Current from the circuit reaches the furnace: " + furnace.watts() + " W at " + furnace.volts() + " V");
            h.succeed();
        });
    }
}
