package net.createplant.logistics;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CreateRnrLogistics.MOD_ID)
public class CreateRnrLogistics {
    public static final String MOD_ID = "create_rnr_logistics";

    public CreateRnrLogistics(IEventBus modEventBus, ModContainer container) {
        net.createplant.registry.LogisticsSetup.init();
        net.createplant.registry.LogisticsCreativeTabs.register();
        net.createplant.registry.CELogisticsRecipeTypes.register(modEventBus);
        net.createplant.registry.CEBlasting.register(modEventBus);
        net.createplant.registry.CEIndustrialExpansion.register(modEventBus);
        net.createplant.registry.CEQuarry.register(modEventBus);
        net.createplant.relay.CERelayComputer.register(modEventBus);
        net.createplant.registry.CEArm.register(modEventBus);
        net.createplant.compat.display.LogisticsReadings.register();
        if (FMLEnvironment.dist.isClient()) net.createplant.client.LogisticsClient.init(modEventBus);
    }
}
