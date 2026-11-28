package net.createplant.atomics.registry;

import net.createplant.registry.CECreativeModeTabs;
import net.createplant.atomics.assembly.AtomicsAssemblyPlans;
import net.createplant.atomics.block.FusionFluidPortBlock;
import net.createplant.block.ProcessPorts;
import net.createplant.atomics.block.entity.ConverterShellBlockEntity;
import net.createplant.atomics.block.entity.CraneDriveBlockEntity;
import net.createplant.atomics.block.entity.CubicPressBlockEntity;
import net.createplant.atomics.block.entity.FuelCycleMachineBlockEntity;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.block.entity.FusionFluidPortBlockEntity;
import net.createplant.atomics.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.atomics.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.atomics.block.entity.RodCapBlockEntity;
import net.createplant.atomics.block.entity.TorpedoTankBlockEntity;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.createplant.atomics.chemistry.AtomicsProcessRecipes;
import net.createplant.atomics.compat.electro.AtomicsElectroDevices;
import net.createplant.control.RelayTargets;
import net.createplant.event.FacilityAchievements;
import net.createplant.mixture.GasPassThrough;
import net.createplant.atomics.relay.FissionRelayTarget;
import net.createplant.atomics.relay.FusionRelayTarget;
import net.createplant.thermal.TankGrade;
import net.neoforged.bus.api.IEventBus;

/** Hooks the atomics addon installs into core extension points. */
public final class AtomicsSetup {
    private AtomicsSetup() { }

    public static void init(IEventBus bus) {
        net.createplant.safety.RadiationHooks.install(new net.createplant.atomics.nuclear.RadiationHooksImpl());
        CECreativeModeTabs.EXTRA_EQUIPMENT.add(CERadiation.DOSIMETER::get);
        CECreativeModeTabs.EXTRA_EQUIPMENT.add(CERadiation.SURVEY_METER::get);
        ProcessPorts.register(FusionFluidPortBlockEntity.class, (port, face) -> {
            if (face != port.getBlockState().getValue(FusionFluidPortBlock.FACING)) return null;
            return switch (port.kind()) {
                case ASH, HELIUM_OUT, COOLANT_OUT -> ProcessPorts.Kind.OUTLET;
                default -> ProcessPorts.Kind.INLET;
            };
        });
        ProcessPorts.registerTopHatch(RodCapBlockEntity.class);
        for (var type : java.util.List.of(ConverterShellBlockEntity.class, CubicPressBlockEntity.class, FuelCycleMachineBlockEntity.class, FuelPoolBlockEntity.class))
            ProcessPorts.registerItemPorts(type);
        TankGrade.register(TorpedoTankBlockEntity.class, CEConverter.TORPEDO_TANK::get, TankGrade.REFRACTORY);
        GasPassThrough.register(CEConverter.TRUNNION_ENTITY::get);
        GasPassThrough.register(CEFusion.CONTROLLER_ENTITY::get);
        GasPassThrough.register(CEFuelCycle.MACHINE_ENTITY::get);
        GasPassThrough.register(CEHighStressMachines.CENTRIFUGE_ENTITY::get);
        AtomicsAssemblyPlans.register();
        FacilityAchievements.register("vitrification", FuelCycleMachineBlockEntity.class, FuelCycleMachineBlockEntity::formed);
        FacilityAchievements.register("fuel_pool", FuelPoolBlockEntity.class, FuelPoolBlockEntity::built);
        FacilityAchievements.register("fusion_reactor", FusionReactorControllerBlockEntity.class, FusionReactorControllerBlockEntity::formed);
        FacilityAchievements.register("converter", ConverterTrunnionBearingBlockEntity.class, ConverterTrunnionBearingBlockEntity::formed);
        FacilityAchievements.register("crane", CraneDriveBlockEntity.class, CraneDriveBlockEntity::built);
        FacilityAchievements.register("reactor_control_room", ReactorControlConsoleBlockEntity.class, ReactorControlConsoleBlockEntity::stationComplete);
        AtomicsElectroDevices.register(bus);
        AtomicsProcessRecipes.register();
        RelayTargets.register(FissionRelayTarget::new);
        RelayTargets.register(FusionRelayTarget::new);
    }
}
