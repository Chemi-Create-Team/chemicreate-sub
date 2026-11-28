package net.createplant.atomics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CreateRnrAtomics.MOD_ID)
public class CreateRnrAtomics {
    public static final String MOD_ID = "create_rnr_atomics";

    public CreateRnrAtomics(IEventBus modEventBus, ModContainer container) {
        net.createplant.atomics.registry.AtomicsSetup.init(modEventBus);
        net.createplant.atomics.registry.AtomicsCreativeTabs.register();
        net.createplant.atomics.registry.CEAtomicsDisplays.register(modEventBus);
        net.createplant.atomics.registry.CEAtomicsRecipeTypes.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.atomics.nuclear.FuelPoolStructure::changed);
        net.createplant.atomics.registry.CEUranium.register(modEventBus);
        net.createplant.atomics.registry.CENuclear.register(modEventBus);
        net.createplant.atomics.registry.CEFusion.register(modEventBus);
        net.createplant.atomics.registry.CEFuelCycle.register(modEventBus);
        net.createplant.atomics.registry.CEFusionFuel.register(modEventBus);
        net.createplant.atomics.registry.CECrane.register(modEventBus);
        net.createplant.atomics.registry.CEHighStressMachines.register(modEventBus);
        net.createplant.atomics.registry.CELargePress.register(modEventBus);
        net.createplant.atomics.registry.CEContinuousFactory.register(modEventBus);
        net.createplant.atomics.registry.CEConverter.register(modEventBus);
        net.createplant.atomics.registry.AtomicsSounds.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.atomics.block.ConverterStructure::changed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.atomics.block.ConverterStructure::chunkLoaded);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.atomics.block.ConverterStructure::chunkUnloaded);
        net.createplant.atomics.ponder.ConverterRecipeSnapshot.register();
        net.createplant.atomics.compat.display.AtomicsReadings.register();
        if (FMLEnvironment.dist.isClient()) net.createplant.atomics.client.AtomicsClient.init(modEventBus);
    }
}
