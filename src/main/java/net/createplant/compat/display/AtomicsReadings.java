package net.createplant.compat.display;

import net.createplant.block.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.createplant.compat.display.MachineReadings.Reading;

/** The atomics machines' display-link readings and status, hooked into {@link MachineReadings}. */
public final class AtomicsReadings implements MachineReadings.Addon {
    private AtomicsReadings() { }

    public static void register() {
        MachineReadings.register(new AtomicsReadings());
        MachineReadings.registerOwner(GasCentrifugePortBlockEntity.class, GasCentrifugePortBlockEntity::owner);
        MachineReadings.registerOwner(GasCentrifugeBlockEntity.class, GasCentrifugeBlockEntity::leader);
        MachineReadings.registerOwner(ReactorControlConsoleBlockEntity.class, console -> console.owner() != null ? console.owner() : console);
        MachineReadings.registerOwner(FuelCycleOutletBlockEntity.class, FuelCycleOutletBlockEntity::owner);
        MachineReadings.registerOwner(FusionMotorGeneratorBlockEntity.class, FusionMotorGeneratorBlockEntity::controller);
        MachineReadings.registerOwner(FusionFluidPortBlockEntity.class, AtomicsReadings::fusionController);
        MachineReadings.registerOwner(FusionVacuumPumpBlockEntity.class, AtomicsReadings::fusionController);
        MachineReadings.registerOwner(ConverterShellBlockEntity.class, ConverterShellBlockEntity::controller);
        MachineReadings.registerOwner(FuelChannelBlockEntity.class, AtomicsReadings::reactorController);
        MachineReadings.registerOwner(RodCapBlockEntity.class, AtomicsReadings::reactorController);
    }

    @Override public Reading read(BlockEntity original, String key, Direction face) {
        FuelChannelBlockEntity sourceChannel = original instanceof FuelChannelBlockEntity channel ? channel
            : original instanceof RodCapBlockEntity cap ? cap.column() : null;
        if (sourceChannel != null && (key.equals("reactor_channel_flow") || key.equals("reactor_channel_outlet_temperature"))) {
            FuelChannelBlockEntity channel = sourceChannel;
            FuelChannelBlockEntity lead = reactorController(channel);
            if (lead == null || lead.core() == null || lead.core().box() == null) return null;
            BlockPos bottom = new BlockPos(channel.getBlockPos().getX(), lead.core().box().minY(), channel.getBlockPos().getZ());
            if (!(channel.getLevel().getBlockEntity(bottom) instanceof FuelChannelBlockEntity bundle)) return null;
            return key.equals("reactor_channel_flow") ? new Reading(bundle.flowRate(), 1000)
                : new Reading(bundle.coolantKelvin() - 273.15, 1000);
        }
        if (original instanceof RodCapBlockEntity cap) {
            if(key.equals("rod_target"))return new Reading(cap.target()*100,100);
            if(key.equals("rod_depth")) {
                var column=cap.column();
                if(column==null)return null;
                double total=0;int rods=0;
                for(int slot=0;slot<FuelChannelBlockEntity.RODS;slot++)if(column.controlInstalled(slot)) {
                    total+=column.controlDepth(slot)/column.layout().coreHeight();
                    rods++;
                }
                return rods==0 ? null : new Reading(total/rods*100,100);
            }
        }
        return MachineReadings.PASS;
    }

    @Override public Reading readOwned(BlockEntity original, BlockEntity be, String key, Direction face) {
        var level = be.getLevel();
        var pos = be.getBlockPos();
        if (be instanceof FuelCycleMachineBlockEntity machine) {
            if (key.equals("formed")) return new Reading(machine.formed() ? 1 : 0, 1);
            if (key.equals("progress")) return new Reading(machine.progress(), 0);
            if (key.equals("electric_power")) return new Reading(machine.watts() / 1000, 1500);
            for (int i = 0; i < 6; i++) if (key.equals("fluid_" + i)) return new Reading(machine.tank(i).getFluidAmount(), machine.tank(i).getCapacity());
            for (int i = 0; i < 3; i++) if (key.equals("item_" + i)) return new Reading(machine.inventory().getStackInSlot(i).getCount(), 64);
        }
        if (be instanceof FusionReactorControllerBlockEntity fusion) {
            return switch (key) {
                case "formed" -> new Reading(fusion.formed() ? 1 : 0, 1);
                case "fusion_power", "reactor_power" -> new Reading(fusion.fusionWatts() / 1e6, 500);
                case "fusion_target" -> new Reading(fusion.targetPercent(), 100);
                case "fusion_vacuum" -> new Reading(fusion.baseVacuumPascals(), 101_325);
                case "fusion_plasma_temperature" -> new Reading(fusion.plasmaKelvin() / 1e6, 150);
                case "fusion_coil_temperature" -> new Reading(fusion.coilKelvin(), 6.5);
                case "fusion_magnetic_field" -> new Reading(fusion.magneticFieldTesla(), net.createplant.fusion.FusionPhysics.MAGNET_AXIS_TESLA);
                case "fusion_magnet_charge" -> new Reading(fusion.magnetCharge() * 100, 100);
                case "fusion_blanket_temperature" -> new Reading(fusion.blanketKelvin() - 273.15, 376.85);
                case "fusion_coolant_flow" -> new Reading(fusion.coolantFlow(), net.createplant.fusion.FusionPhysics.RATED_COOLANT_MB_PER_TICK);
                case "fusion_deuterium" -> new Reading(fusion.deuterium().getAmount(), FusionReactorControllerBlockEntity.FUEL_CAPACITY);
                case "fusion_tritium" -> new Reading(fusion.tritium().getAmount(), FusionReactorControllerBlockEntity.FUEL_CAPACITY);
                case "fusion_liquid_helium" -> new Reading(fusion.liquidHelium().getAmount(), FusionReactorControllerBlockEntity.LIQUID_HELIUM_CAPACITY);
                case "fusion_blanket_life" -> new Reading(fusion.blanketRemaining(), 24_000);
                case "fusion_divertor_life" -> new Reading(fusion.componentRemaining(1), 16_000);
                case "fusion_electric_power" -> new Reading(fusion.electricalWatts() / 1e6, 80);
                case "fusion_emergency" -> new Reading(fusion.emergency() ? 1 : 0, 1);
                case "fusion_quench" -> new Reading(fusion.quench() ? 1 : 0, 1);
                default -> null;
            };
        }
        if (be instanceof FusionControlConsoleBlockEntity console) {
            return switch (key) {
                case "formed" -> new Reading(console.linked() ? 1 : 0, 1);
                case "fusion_power", "reactor_power" -> console.linked() ? new Reading(console.power() / 1e6, 500) : null;
                case "fusion_target" -> console.linked() ? new Reading(console.target(), 100) : null;
                case "fusion_vacuum" -> console.linked() ? new Reading(console.vacuumPa(), 101_325) : null;
                case "fusion_plasma_temperature" -> console.linked() ? new Reading(console.plasmaK() / 1e6, 150) : null;
                case "fusion_coil_temperature" -> console.linked() ? new Reading(console.coilK(), 6.5) : null;
                case "fusion_magnet_charge" -> console.linked() ? new Reading(console.charge() * 100, 100) : null;
                case "fusion_emergency" -> console.linked() ? new Reading(console.emergency() ? 1 : 0, 1) : null;
                default -> null;
            };
        }
        if (be instanceof FuelPoolBlockEntity pool) {
            switch (key) {
                case "formed":return new Reading(pool.cellCount()>0 ? 1 : 0,1);
                case "pool_cells":return new Reading(pool.cellCount(),net.createplant.nuclear.FuelPoolStructure.MAX*net.createplant.nuclear.FuelPoolStructure.MAX);
                case "pool_fresh":return new Reading(pool.freshCount(),pool.cellCount()*FuelChannelBlockEntity.RODS);
                case "pool_spent":return new Reading(pool.spentCount(),pool.cellCount()*FuelChannelBlockEntity.RODS);
                case "pool_heat":return new Reading(pool.spentCount()*FuelPoolBlockEntity.HEAT_PER_ROD,0);
                case "pool_temperature":return pool.waterAmount()>0 ? new Reading(pool.waterCelsius(),100) : null;
                case "fluid_0":return new Reading(pool.waterAmount(),pool.cellCount()*FuelPoolBlockEntity.WATER_PER_CELL);
            }
        }
        if (be instanceof ReactorControlConsoleBlockEntity console) {
            return switch (key) {
                case "formed" -> new Reading(console.linked() ? 1 : 0, 1);
                case "reactor_power" -> console.linked() ? new Reading(console.power() / 1e6, 0) : null;
                case "reactor_reactivity" -> console.linked() ? new Reading(console.reactivity() / net.createplant.nuclear.ReactorPhysics.BETA, 0) : null;
                case "reactor_void" -> console.linked() ? new Reading(console.voidFraction() * 100, 100) : null;
                case "reactor_fuel_temperature" -> console.linked() ? new Reading(console.hottestKelvin() - 273.15, net.createplant.nuclear.ReactorPhysics.CHANNEL_RUPTURE_KELVIN - 273.15) : null;
                case "reactor_coolant_temperature" -> console.linked() ? new Reading(console.coolantKelvin() - 273.15, 300) : null;
                case "reactor_graphite_temperature" -> console.linked() ? new Reading(console.graphiteKelvin() - 273.15, 1000) : null;
                case "reactor_channel_outlet_temperature" -> console.linked() ? new Reading(console.representativeOutletKelvin() - 273.15, 1000) : null;
                case "reactor_rods" -> console.linked() ? new Reading(console.rodInsertion() * 100, 100) : null;
                case "reactor_xenon" -> console.linked() ? new Reading(console.xenon()*100,100) : null;
                case "reactor_surface_temperature" -> console.linked() ? new Reading(console.surfaceKelvin()-273.15,0) : null;
                case "console_mode" -> new Reading(console.mode(),2);
                case "console_bank_target" -> console.linked() ? new Reading(console.bankTarget(),100) : null;
                case "console_control_target" -> console.linked() && console.controlTarget()>=0 ? new Reading(console.controlTarget(),100) : null;
                case "console_alarms" -> new Reading(Integer.bitCount(console.alarms()),5);
                case "console_trace_samples" -> new Reading(console.chartCount(),ReactorControlConsoleBlockEntity.SAMPLES);
                case "console_total_flow" -> console.linked() ? new Reading(console.totalFlow(),0) : null;
                case "rod_depth" -> console.selectedDepth() < 0 ? null : new Reading(console.selectedDepth(), 100);
                case "rod_target" -> console.selectedCommand() < 0 ? null : new Reading(console.selectedCommand(), 100);
                default -> null;
            };
        }
        if (be instanceof ConverterTrunnionBearingBlockEntity converter) {
            switch (key) {
                case "formed": return new Reading(converter.formed() ? 1 : 0, 1);
                case "structure": return new Reading(converter.formed() ? net.createplant.block.ConverterStructure.cells().size() : 0,
                    net.createplant.block.ConverterStructure.cells().size());
                case "progress": return new Reading(converter.progress(), 800);
                case "converter_angle": return new Reading(converter.angle(), ConverterTrunnionBearingBlockEntity.MAX_ANGLE);
                case "converter_input_gas": return new Reading(converter.gasInput().getFluidAmount(), ConverterTrunnionBearingBlockEntity.GAS_INPUT_CAPACITY);
                case "converter_offgas": return new Reading(converter.gasOutput().getFluidAmount(), ConverterTrunnionBearingBlockEntity.GAS_OUTPUT_CAPACITY);
                case "converter_items_in": {
                    int count = 0;
                    for (int slot = 0; slot < ConverterTrunnionBearingBlockEntity.INPUT_SLOTS; slot++) count += converter.inventory().getStackInSlot(slot).getCount();
                    return new Reading(count, ConverterTrunnionBearingBlockEntity.INPUT_SLOTS * 64);
                }
                case "converter_items_out": {
                    int count = 0;
                    for (int slot = ConverterTrunnionBearingBlockEntity.INPUT_SLOTS;
                         slot < ConverterTrunnionBearingBlockEntity.INPUT_SLOTS + ConverterTrunnionBearingBlockEntity.OUTPUT_SLOTS; slot++)
                        count += converter.inventory().getStackInSlot(slot).getCount();
                    return new Reading(count, ConverterTrunnionBearingBlockEntity.OUTPUT_SLOTS * 64);
                }
            }
        }
        if(be instanceof FuelChannelBlockEntity lead && lead.core()!=null && lead.core().formed()) {
            var core=lead.core();
            switch(key) {
                case "formed":return new Reading(1,1);
                case "reactor_power":return new Reading(core.power()/1e6,0);
                case "reactor_reactivity":return new Reading(core.reactivity()/net.createplant.nuclear.ReactorPhysics.BETA,0);
                case "reactor_void":return new Reading(core.averageVoid()*100,100);
                case "reactor_fuel_temperature":return new Reading(core.hottestKelvin()-273.15,net.createplant.nuclear.ReactorPhysics.CHANNEL_RUPTURE_KELVIN-273.15);
                case "reactor_surface_temperature":return new Reading(core.hottestSurfaceKelvin()-273.15,0);
                case "reactor_coolant_temperature":return new Reading(core.averageCoolantKelvin()-273.15,300);
                case "reactor_xenon":return new Reading(core.xenon()*100,0);
                case "reactor_rods":return new Reading(core.rodInsertion()*100,100);
                case "reactor_average_fuel_temperature":return new Reading(core.averageFuelKelvin()-273.15,net.createplant.nuclear.ReactorPhysics.CHANNEL_RUPTURE_KELVIN-273.15);
                case "reactor_instability":return new Reading(core.instability()*100,100);
                case "reactor_hazard":return new Reading(core.hazardNow()*100,100);
                case "reactor_radiation_rate":return be.getLevel() instanceof net.minecraft.server.level.ServerLevel server ? new Reading(net.createplant.nuclear.RadiationField.get(server).measure(server, net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()), null).external()*3.6e6,0) : null;
                case "reactor_fuel_channels":return new Reading(core.fuelChannels(),net.createplant.nuclear.ReactorCoreStructure.MAX_WIDTH
                    *net.createplant.nuclear.ReactorCoreStructure.MAX_WIDTH*FuelChannelBlockEntity.RODS);
                case "reactor_control_rods":return new Reading(core.installedControlRods(),net.createplant.nuclear.ReactorCoreStructure.MAX_WIDTH
                    *net.createplant.nuclear.ReactorCoreStructure.MAX_WIDTH*FuelChannelBlockEntity.RODS);
                case "reactor_scram":return new Reading(core.scram() ? 1 : 0,1);
                case "reactor_graphite_temperature":return new Reading(core.graphiteKelvin()-273.15,1000);
            }
        }
        if(be instanceof CraneDriveBlockEntity crane) {
            if(key.equals("crane_depth"))return new Reading(crane.depth(),0);
            if(key.startsWith("crane_")) {
                int count=0;boolean supplies=key.equals("crane_supplies");
                for(int slot=0;slot<CraneDriveBlockEntity.SLOTS;slot++) {
                    var stack=crane.store().getStackInSlot(slot);
                    if(stack.isEmpty())continue;
                    boolean supply=stack.is(net.createplant.registry.CENuclear.FUEL_ROD.get()) || stack.is(net.createplant.registry.CENuclear.CONTROL_ROD.get());
                    if(supply==supplies)count+=stack.getCount();
                }
                return new Reading(count,CraneDriveBlockEntity.SLOTS*64);
            }
        }
        if (be instanceof GasCentrifugeBlockEntity c) {
            switch (key) {
                case "cascade_units": return new Reading(c.unitCount(), 16);
                case "cascade_stages": return new Reading(c.stages(), 16);
                case "cascade_required": return new Reading(c.required(), 16);
                case "progress": return new Reading(c.progress(), c.cycleTicks());
                case "stress": return new Reading(32 * Math.abs(c.getSpeed()), 0);
                case "fluid_0": return new Reading(c.amount(0), c.unitCount() * 4000);
                case "fluid_1": return new Reading(c.amount(1), c.unitCount() * 4000);
                case "fluid_2": return new Reading(c.amount(2), c.unitCount() * 4000);
            }
        }
        if (be instanceof CubicPressBlockEntity p) {
            switch (key) {
                case "progress": return new Reading(p.progress(), p.duration());
                case "stress": return new Reading(256 * Math.abs(p.getSpeed()), 0);
                case "superheated": return new Reading(p.superheated() ? 1 : 0, 1);
            }
        }
        if (be instanceof LargeMechanicalPressBlockEntity p && key.equals("progress")) {
            var pressing = p.pressing();
            return new Reading(pressing.running ? Math.abs(pressing.runningTicks) : 0, com.simibubi.create.content.kinetics.press.PressingBehaviour.CYCLE);
        }
        if (be instanceof ContinuousFactoryBlockEntity p) {
            if (key.equals("progress")) return new Reading(p.progress(), 1);
            if (key.equals("formed")) return new Reading(p.formedLength() > 0 ? 1 : 0, 1);
        }
        return MachineReadings.PASS;
    }

    @Override public Component status(BlockEntity original) {
        if (original instanceof GasCentrifugePortBlockEntity port && port.owner() != null) return MachineReadings.status(port.owner());
        if (original instanceof GasCentrifugeBlockEntity c) return Component.translatable("centrifuge.create_refinery_n_reactors.status." + c.status());
        if (original instanceof CubicPressBlockEntity p) return Component.translatable("cubic_press.create_refinery_n_reactors.status." + p.status());
        if (original instanceof LargeMechanicalPressBlockEntity p) return p.status();
        if (original instanceof ContinuousFactoryBlockEntity p) return p.statusText();
        return null;
    }

    @Override public Component statusOwned(BlockEntity original, BlockEntity be) {
        if (be instanceof FuelCycleMachineBlockEntity machine) return Component.translatable("plant_systems.status." + machine.status());
        if (be instanceof ConverterTrunnionBearingBlockEntity converter)
            return Component.translatable("converter.create_refinery_n_reactors.status." + converter.status());
        if (be instanceof FuelChannelBlockEntity lead && lead.core()!=null) return Component.translatable("reactor_core.create_refinery_n_reactors.status."+lead.core().status());
        if (be instanceof RodCapBlockEntity cap && cap.column()!=null && cap.column().formedCore()!=null)
            return Component.translatable("reactor_core.create_refinery_n_reactors.status."+cap.column().formedCore().status());
        if (be instanceof CraneDriveBlockEntity crane) return Component.translatable("crane.create_refinery_n_reactors.status."+crane.status());
        if (be instanceof FuelPoolBlockEntity pool) return Component.translatable("fuel_pool.create_refinery_n_reactors.status."+pool.status());
        if (be instanceof FusionReactorControllerBlockEntity fusion)
            return Component.translatable("fusion_reactor.create_refinery_n_reactors.status." + fusion.status());
        if (be instanceof FusionControlConsoleBlockEntity console)
            return console.linked() ? Component.translatable("fusion_reactor.create_refinery_n_reactors.status." + console.status())
                : Component.translatable("fusion_console.create_refinery_n_reactors.link." + (console.assembled() ? console.linkStatus() : "incomplete"));
        if (be instanceof ReactorControlConsoleBlockEntity console)
            return console.linked() ? Component.translatable("reactor_core.create_refinery_n_reactors.status." + console.reactorStatus())
                : Component.translatable("reactor_control_console.create_refinery_n_reactors.status." + (console.assembled() ? console.linkStatus() : "incomplete"));
        return null;
    }

    @Override public net.minecraft.network.chat.MutableComponent line(String metric, BlockEntity source) {
        if (metric.equals("process_name") && MachineReadings.owner(source) instanceof GasCentrifugeBlockEntity c) return c.processName().copy();
        return null;
    }

    private static FusionReactorControllerBlockEntity fusionController(BlockEntity be) {
        var controller = FusionReactorControllerBlockEntity.find(be.getLevel(), be.getBlockPos());
        return controller != null && controller.owns(be.getBlockPos()) ? controller : null;
    }

    public static FuelChannelBlockEntity reactorController(BlockEntity part) {
        if (part == null || part.getLevel() == null) return null;
        if (part instanceof FuelChannelBlockEntity channel && channel.core() != null && channel.core().formed()) return channel;
        FuelChannelBlockEntity column = part instanceof FuelChannelBlockEntity channel ? channel
            : part instanceof RodCapBlockEntity cap ? cap.column() : null;
        BlockPos controller = column == null ? null : column.controllerPos();
        return controller != null && part.getLevel().hasChunkAt(controller)
            && part.getLevel().getBlockEntity(controller) instanceof FuelChannelBlockEntity lead
            && lead.core() != null && lead.core().formed() ? lead : null;
    }

}
