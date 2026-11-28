package net.createplant.atomics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CreateRnrAtomics.MOD_ID)
public class CreateRnrAtomics {
    public static final String MOD_ID = "create_rnr_atomics";

    public CreateRnrAtomics(IEventBus modEventBus, ModContainer container) {
        net.createplant.registry.AtomicsSetup.init(modEventBus);
        net.createplant.registry.CEAtomicsDisplays.register(modEventBus);
        net.createplant.registry.CEAtomicsRecipeTypes.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.nuclear.FuelPoolStructure::changed);
        net.createplant.registry.CEUranium.register(modEventBus);
        net.createplant.registry.CENuclear.register(modEventBus);
        net.createplant.registry.CEFusion.register(modEventBus);
        net.createplant.registry.CEFuelCycle.register(modEventBus);
        net.createplant.registry.CEFusionFuel.register(modEventBus);
        net.createplant.registry.CECrane.register(modEventBus);
        net.createplant.registry.CEHighStressMachines.register(modEventBus);
        net.createplant.registry.CELargePress.register(modEventBus);
        net.createplant.registry.CEContinuousFactory.register(modEventBus);
        net.createplant.registry.CEConverter.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::changed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::chunkLoaded);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::chunkUnloaded);
    }
}
