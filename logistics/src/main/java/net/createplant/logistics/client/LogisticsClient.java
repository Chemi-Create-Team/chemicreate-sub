package net.createplant.logistics.client;

import net.createplant.client.MachineSoundSource;
import net.createplant.logistics.registry.CEQuarry;
import net.createplant.block.entity.*;
import net.createplant.logistics.block.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class LogisticsClient {
    public static void init(IEventBus modEventBus) {
        MachineSoundSource.register(QuarryDriveBlockEntity.class, quarry -> quarry.working() ? MachineSoundSource.Kind.QUARRY : null);
        MachineSoundSource.register(IndustrialDispatchBlockEntity.class, dispatch -> dispatch.status().equals("running") ? MachineSoundSource.Kind.DISPATCH : null);
        modEventBus.addListener(LogisticsClient::clientSetup);
        modEventBus.addListener(LogisticsClient::registerRenderers);
        net.createplant.logistics.client.IndustrialArmRenderer.init();
        net.createplant.logistics.client.QuarryRenderer.init();
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.createmod.ponder.foundation.PonderIndex.addPlugin(new net.createplant.logistics.ponder.LogisticsPonderPlugin()));
        // The quarry drive's two shaft halves may turn opposite ways, the outer one driving the side belt.
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(CEQuarry.QUARRY_DRIVE_ENTITY.get())
            .factory(net.createplant.logistics.client.QuarryVisual::new).skipVanillaRender(be -> false).apply());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CEQuarry.QUARRY_DRIVE_ENTITY.get(), net.createplant.logistics.client.QuarryRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.logistics.registry.CEArm.ARM_ENTITY.get(), net.createplant.logistics.client.IndustrialArmRenderer::new);
    }
}
