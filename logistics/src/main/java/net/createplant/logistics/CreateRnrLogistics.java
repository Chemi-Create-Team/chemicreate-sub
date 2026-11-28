package net.createplant.logistics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CreateRnrLogistics.MOD_ID)
public class CreateRnrLogistics {
    public static final String MOD_ID = "create_rnr_logistics";

    public CreateRnrLogistics(IEventBus modEventBus, ModContainer container) {
        net.createplant.logistics.registry.LogisticsSetup.init();
        net.createplant.logistics.registry.LogisticsCreativeTabs.register();
        net.createplant.logistics.registry.CELogisticsRecipeTypes.register(modEventBus);
        net.createplant.logistics.registry.CEBlasting.register(modEventBus);
        net.createplant.logistics.registry.CEIndustrialExpansion.register(modEventBus);
        net.createplant.logistics.registry.CEQuarry.register(modEventBus);
        net.createplant.logistics.relay.CERelayComputer.register(modEventBus);
        net.createplant.logistics.registry.CEArm.register(modEventBus);
        net.createplant.logistics.compat.display.LogisticsReadings.register();
        if (FMLEnvironment.dist.isClient()) net.createplant.logistics.client.LogisticsClient.init(modEventBus);
    }
}
