package net.createplant.client;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.registry.CEFusion;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.createplant.block.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

public class AtomicsClient {
    public static void init(IEventBus modEventBus) {
        if (Boolean.getBoolean("createplant.powerPlantClientCheck")) NeoForge.EVENT_BUS.addListener(net.createplant.client.PowerPlantClientCheck::tick);
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.radiationClientCheck", 90, RadiationClientCheck::start, RadiationClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.craneClientCheck", 17, CraneClientCheck::start, CraneClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.fuelPoolClientCheck", 16, FuelPoolClientCheck::start, FuelPoolClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.reactorConsoleClientCheck", 19, ReactorConsoleClientCheck::start, ReactorConsoleClientCheck::tick, ClientQualityCheck.Finish.THEN_RECIPROCATING));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.fusionClientCheck", 22, FusionClientCheck::start, FusionClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.converterClientCheck", 23, ConverterClientCheck::start, ConverterClientCheck::tick, ClientQualityCheck.Finish.STOP));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.continuousFactoryClientCheck", 35, ContinuousFactoryClientCheck::start, ContinuousFactoryClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.register(new ClientQualityCheck.AddonCheck("createplant.whiteThemeWorldCheck", 25, WhiteThemeWorldClientCheck::start, WhiteThemeWorldClientCheck::tick, ClientQualityCheck.Finish.SHOW));
        ClientQualityCheck.registerPonderCheck((mc, scene) -> {
            if (Boolean.getBoolean("createplant.craneClientCheck") || Boolean.getBoolean("createplant.cranePonderCheck")) CraneClientCheck.verifyPonder(mc);
            if (!Boolean.getBoolean("createplant.clientQualityExpectConverterTilt") || !scene.equals("converter_shell")) return;
            // Seeking restores the independent section transform, but does not replay
            // instantaneous block-entity mutations. The captured frame verifies the pose.
            if (!(mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui)
                || !(ui.getActiveScene().getWorld().getBlockEntity(new net.minecraft.core.BlockPos(1, 6, 6))
                    instanceof net.createplant.block.entity.ConverterTrunnionBearingBlockEntity))
                throw new IllegalStateException("The converter Ponder is missing its trunnion bearing");
        });
        MachineSoundSource.register(GasCentrifugeBlockEntity.class, centrifuge -> centrifuge.spinningCascade() ? MachineSoundSource.Kind.CENTRIFUGE : null);
        MachineSoundSource.register(CubicPressBlockEntity.class, press -> press.running() ? MachineSoundSource.Kind.CUBIC_PRESS : null);
        MachineSoundSource.register(FuelChannelBlockEntity.class, channel -> channel.core() != null && channel.core().formed() && channel.core().power() > 0 ? MachineSoundSource.Kind.REACTOR : null);
        MachineSoundSource.registerPosition(FuelChannelBlockEntity.class, channel -> channel.core().box() != null ? Vec3.atCenterOf(channel.core().box().getCenter()) : null);
        MachineSoundSource.register(FusionReactorControllerBlockEntity.class, fusion ->
            fusion.formed() && (fusion.fusionWatts() > 0 || fusion.electricalWatts() > 0) ? MachineSoundSource.Kind.FUSION : null);
        MachineSoundSource.register(FuelCycleMachineBlockEntity.class, cycle ->
            !cycle.formed() ? null : cycle.status().equals("vitrifying") ? MachineSoundSource.Kind.WASTE_MELTER
                : cycle.status().equals("extracting") ? MachineSoundSource.Kind.SOLVENT_EXTRACTOR : null);
        MachineSoundSource.register(ConverterTrunnionBearingBlockEntity.class, converter ->
            converter.formed() && (converter.status().equals("blowing") || converter.isRunning() && !converter.tilted()) ? MachineSoundSource.Kind.CONVERTER : null);
        MachineSoundSource.register(CraneDriveBlockEntity.class, crane ->
            crane.structure() != null && crane.working() ? (crane.fusion() ? MachineSoundSource.Kind.FUSION_CRANE : MachineSoundSource.Kind.CRANE) : null);
        MachineSoundSource.register(FusionMotorGeneratorBlockEntity.class, motor -> motor.electricalWatts() > 0 ? MachineSoundSource.Kind.MOTOR_GENERATOR : null);
        MachineSoundSource.register(FusionVacuumPumpBlockEntity.class, pump -> pump.getSpeed() != 0 ? MachineSoundSource.Kind.VACUUM_PUMP : null);
        GoggleSpec.registerStatus((owner, lines) -> {
            if (owner instanceof net.createplant.block.entity.FusionReactorControllerBlockEntity fusion && GoggleSpec.firstIs(lines, "fusion_reactor.create_refinery_n_reactors.goggle.status"))
                return new GoggleSpec.Status(fusion.status().equals("burning"), Component.translatable("fusion_reactor.create_refinery_n_reactors.status." + fusion.status()));
            if (owner instanceof net.createplant.block.entity.ReactorControlConsoleBlockEntity console && GoggleSpec.firstIs(lines, "reactor_control_console.create_refinery_n_reactors.status."))
                return new GoggleSpec.Status(console.linked() && java.util.Set.of("critical", "supercritical", "prompt_critical", "unstable").contains(console.reactorStatus()),
                    console.linked() ? Component.translatable("reactor_core.create_refinery_n_reactors.status." + console.reactorStatus()) : lines.getFirst());
            return null;
        });
        ProcessPortOverlay.hatchWithoutPrompt(net.createplant.block.entity.RodCapBlockEntity.class);
        StructureGuideRenderer.register(new net.createplant.guide.FusionGuide());
        ProcessGoggles.registerRedirect((level, pos, state) -> state.getBlock() instanceof net.createplant.block.LargePressFrameBlock && !state.getValue(net.createplant.block.LargePressFrameBlock.DEPOT)
            ? net.createplant.block.LargePressFrameBlock.center(level, pos, state) : null);
        // Ports and the controller show the reactor casing on their sides, so they join the same outer skin.
        CasingModels.register(CasingModels.plated("fusion/reinforced_casing"), List.<Supplier<? extends Block>>of(CEFusion.REINFORCED_CASING, CEFusion.CONTROLLER,
            CEFusion.DEUTERIUM_PORT, CEFusion.TRITIUM_PORT, CEFusion.ASH_PORT, CEFusion.HELIUM_INLET, CEFusion.HELIUM_OUTLET,
            CEFusion.COOLANT_INLET, CEFusion.COOLANT_OUTLET));
        CasingModels.register(CasingModels.plated("fusion/breeding_blanket"), List.<Supplier<? extends Block>>of(CEFusion.BREEDING_BLANKET));
        CasingModels.register(CasingModels.plated("fusion/vacuum_vessel"), List.<Supplier<? extends Block>>of(CEFusion.VACUUM_VESSEL));
        CasingModels.register(CasingModels.plated("fusion/divertor"), List.<Supplier<? extends Block>>of(CEFusion.DIVERTOR));
        CasingModels.register(List.of(CasingModels.shift("fusion/viewport")), List.<Supplier<? extends Block>>of(CEFusion.VIEWPORT));
        CasingModels.register(List.of(CasingModels.shift("fusion/toroidal_field_coil")), List.<Supplier<? extends Block>>of(CEFusion.TOROIDAL_FIELD_COIL));
        CasingModels.register(List.of(CasingModels.shift("fusion/central_solenoid")), List.<Supplier<? extends Block>>of(CEFusion.CENTRAL_SOLENOID));
        modEventBus.addListener(AtomicsClient::clientSetup);
        modEventBus.addListener(AtomicsClient::tintPowders);
        modEventBus.addListener(AtomicsClient::registerRenderers);
        net.createplant.client.CubicPressRenderer.init();
        net.createplant.client.LargeMechanicalPressRenderer.init();
        net.createplant.client.FuelChannelRenderer.init();
        net.createplant.client.RBMKPlugRenderer.init();
        net.createplant.client.CraneRenderer.init();
        net.createplant.client.FuelCycleMachineRenderer.init();
        net.createplant.client.ReactorControlConsoleRenderer.init();
        if (System.getProperty("createplant.rbmkVariantsPlaytest")!=null) NeoForge.EVENT_BUS.addListener(net.createplant.client.RbmkVariantsPlaytestWorld::tick);
        if (System.getProperty("createplant.fusionPlaytest")!=null) NeoForge.EVENT_BUS.addListener(net.createplant.client.FusionPlaytestWorld::tick);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        net.createplant.ponder.ConverterPonderScenes.register();
        event.enqueueWork(() -> net.createmod.ponder.foundation.PonderIndex.addPlugin(new net.createplant.ponder.AtomicsPonderPlugin()));
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer.builder(net.createplant.registry.CEContinuousFactory.BODY_ENTITY.get())
            .factory(net.createplant.client.FactoryBodyVisual::new).neverSkipVanillaRender().apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEHighStressMachines.CENTRIFUGE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CENuclear.SHIELD_DRIVE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        // The crane drive keeps its shaft in Flywheel while its bridge, trolley and mast use the block-entity renderer.
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CECrane.DRIVE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> false).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEFusion.VACUUM_PUMP_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEConverter.TRUNNION_ENTITY.get())
            .factory(com.simibubi.create.content.contraptions.bearing.BearingVisual::new).skipVanillaRender(be -> true).apply());
    }

    private static void tintPowders(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> 0xFFFFB850, net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.get());
        event.register((stack, layer) -> 0xFF8A7464, net.createplant.registry.CEUranium.URANIUM_DIOXIDE.get());
        event.register((stack, layer) -> 0xFF96C882, net.createplant.registry.CEUranium.URANIUM_TETRAFLUORIDE.get());
        event.register((stack, layer) -> 0xFF7A8296, net.createplant.registry.CEUranium.ENRICHED_URANIUM_DIOXIDE.get());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(net.createplant.registry.CEContinuousFactory.BODY_ENTITY.get(), net.createplant.client.FactoryBodyRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEHighStressMachines.CENTRIFUGE_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEHighStressMachines.PRESS_ENTITY.get(), net.createplant.client.CubicPressRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CELargePress.PRESS_ENTITY.get(), net.createplant.client.LargeMechanicalPressRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEContinuousFactory.CONTROLLER_ENTITY.get(), net.createplant.client.FactoryPonderRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CELargePress.DEPOT_ENTITY.get(), com.simibubi.create.content.logistics.depot.DepotRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.FUEL_CHANNEL_ENTITY.get(), net.createplant.client.FuelChannelRenderer::new);
        // The cap renderer offsets each pressure-channel plug independently and lets Create draw its frequency slots.
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.TOP_CAP_ENTITY.get(), net.createplant.client.RBMKPlugRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.ROD_LINK_ENTITY.get(), com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.ROD_CONTROLLER_ENTITY.get(), com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.FUEL_POOL_ENTITY.get(), net.createplant.client.FuelPoolRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.REACTOR_CONTROL_CONSOLE_ENTITY.get(), net.createplant.client.ReactorControlConsoleRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.REACTOR_CONSOLE_DISPLAY_ENTITY.get(), net.createplant.client.ReactorConsoleDisplayRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.SHIELD_DRIVE_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CECrane.DRIVE_ENTITY.get(), net.createplant.client.CraneRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEConverter.TRUNNION_ENTITY.get(), com.simibubi.create.content.contraptions.bearing.BearingRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFusion.CONTROLLER_ENTITY.get(), net.createplant.client.FusionReactorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFuelCycle.MACHINE_ENTITY.get(), net.createplant.client.FuelCycleMachineRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFusion.VACUUM_PUMP_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
    }
}
