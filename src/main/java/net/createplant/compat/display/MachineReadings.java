package net.createplant.compat.display;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.block.PipeGaugeBlock;
import net.createplant.block.entity.*;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Display links sample a single measurement without changing machine state. */
public final class MachineReadings {
    public record Reading(double value,double maximum) { }
    // This migration retires obsolete indices; later changes append new measurements.
    public static final List<String> KEYS=List.of(
        "speed","energy","progress","flow_lower","flow_upper","structure",
        "formed","flow_limit","status","fluid_0","fluid_1","fluid_2",
        "fluid_3","fluid_4","fluid_5","fluid_6","fluid_7","item_0",
        "item_1","item_2","flywheel_energy","flywheel_rpm","flywheel_charge","flywheel_discharge",
        "flywheel_acceleration","flywheel_maintenance","flywheel_inertia","flywheel_count","electric_power","steam_flow",
        "exchange_mode","flow_rate","rotor_power","rotor_work","rotor_feed","reactor_power",
        "reactor_reactivity","reactor_void","reactor_fuel_temperature","reactor_xenon","reactor_rods","rod_depth",
        "rod_target","crane_depth","crane_supplies","pool_fresh","pool_spent","pool_cells",
        "pool_heat","reactor_coolant_temperature","reactor_average_fuel_temperature","reactor_instability","reactor_hazard","reactor_radiation_rate",
        "reactor_fuel_channels","reactor_control_rods","reactor_scram","reactor_graphite_temperature","reactor_channel_flow","reactor_channel_outlet_temperature",
        "fusion_power","fusion_target","fusion_vacuum","fusion_plasma_temperature","fusion_coil_temperature","fusion_magnetic_field",
        "fusion_magnet_charge","fusion_blanket_temperature","fusion_coolant_flow","fusion_deuterium","fusion_tritium","fusion_liquid_helium",
        "fusion_blanket_life","fusion_electric_power","fusion_emergency","fusion_quench","converter_angle","converter_items_in",
        "converter_items_out","converter_input_gas","converter_offgas","control_measurement","control_output","control_setpoint",
        "control_kp","control_ti","control_td","cooling_power","condenser_heat","fusion_divertor_life",
        "vessel_fouling","vessel_drain","vessel_waste","fluid_8","item_3","item_4",
        "item_5","item_6","item_7","item_8","item_9","item_10",
        "item_11","item_12","dispatch_completed","dispatch_order","boiler_heat",
        "cold_box_power","flare_height","flare_burnt","flare_vented","redstone_signal","bell_height",
        "bell_travel","console_mode","console_bank_target","console_control_target","console_alarms",
        "console_trace_samples","console_total_flow","reactor_surface_temperature","electrolysis_voltage","electrolysis_current","electrolysis_plates",
        "electrolysis_limit","electrolysis_rate","electrolysis_cathode","electrolysis_anode","electrolysis_soda","cascade_units","cascade_stages","cascade_required","process_name","stress","superheated","electrolysis_metal","electrolysis_metal_storage","electrolysis_recovered_acid","separation","gasifier_temperature",
        "production_fluid","production_fluid_max","production_items","production_items_max","production_power","production_power_max","production_bottleneck");
    private MachineReadings() { }

    public static BlockEntity owner(BlockEntity be) {
        if (be instanceof GasifierBlockEntity gasifier) return gasifier.owner() == null ? gasifier : gasifier.owner();
        if (be instanceof ReactionTowerBlockEntity tower) return tower.owner();
        if (be instanceof WetProcessPartBlockEntity part) return part.owner();
        if (be instanceof TankCasingBlockEntity casing && casing.clarifier() != null) return casing.clarifier();
        if (be instanceof GasCentrifugePortBlockEntity port) return port.owner();
        if (be instanceof GasCentrifugeBlockEntity centrifuge) return centrifuge.leader();
        if (be instanceof ElectrodeHeadBlockEntity head) return head.controller();
        if (be instanceof ElectrolysisCellBlockEntity cell) return cell.controller();
        if (be instanceof ElectrolysisFurnacePartBlockEntity part && part.owner() != null && part.getLevel() != null
            && part.getLevel().hasChunkAt(part.owner())) return part.getLevel().getBlockEntity(part.owner());
        if (be instanceof StackedReactorBlockEntity column) return column.bottomStorey();
        if (be instanceof ElectrolysisControllerBlockEntity control) return control.master();
        if(be instanceof ReactorControlConsoleBlockEntity console && console.owner()!=null)return console.owner();
        if (be instanceof LiddedVesselBlockEntity vessel) return vessel.getControllerBE();
        if (be instanceof CoolingTowerPortBlockEntity port) return port.owner();
        if (be instanceof FuelCycleOutletBlockEntity port) return port.owner();
        if (be instanceof CondenserCoolingPortBlockEntity port) return port.owner();
        if (be instanceof FusionMotorGeneratorBlockEntity generator) return generator.controller();
        if (be instanceof FusionFluidPortBlockEntity || be instanceof FusionVacuumPumpBlockEntity) {
            var controller = FusionReactorControllerBlockEntity.find(be.getLevel(), be.getBlockPos());
            return controller != null && controller.owns(be.getBlockPos()) ? controller : null;
        }
        if (be instanceof RadiatorBlockEntity radiator) return radiator.owner();
        if (be instanceof ThermalPartBlockEntity part) {
            var controller=part.owner();return controller!=null && controller.owns(part.getBlockPos()) ? controller : null;
        }
        if (be instanceof FractionatingColumnBlockEntity column) return column.getControllerBE();
        if (be instanceof HighPressureTurbineEndBlockEntity end) return end.exhaustSource();
        if (be instanceof HighPressureTurbinePartBlockEntity part) return part.rotor();
        if (be instanceof ConverterShellBlockEntity converter) return converter.controller();
        if (be instanceof FuelChannelBlockEntity || be instanceof RodCapBlockEntity) return reactorController(be);
        if (be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank) {
            if (!tank.isController() && (tank.getLevel()==null || !tank.getLevel().hasChunkAt(tank.getController()))) return null;
            return tank.getControllerBE();
        }
        return be;
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

    public static Component status(BlockEntity original) {
        if (owner(original) instanceof GasifierBlockEntity gasifier) return GasifierBlockEntity.message(gasifier.status());
        if (owner(original) instanceof WetProcessBlockEntity wet) return Component.translatable("wet_process.create_refinery_n_reactors.status." + wet.status());
        if (original instanceof GasCentrifugePortBlockEntity port && port.owner() != null) return status(port.owner());
        if (original instanceof GasCentrifugeBlockEntity c) return Component.translatable("centrifuge.create_refinery_n_reactors.status." + c.status());
        if (original instanceof CubicPressBlockEntity p) return Component.translatable("cubic_press.create_refinery_n_reactors.status." + p.status());
        if (original instanceof ElectricPumpBlockEntity pump) return Component.translatable("electric_utility.create_refinery_n_reactors."+pump.status());
        if (original instanceof GasHolderBlockEntity part) {var bell=part.getControllerBE();return Component.translatable("gas_bell.create_refinery_n_reactors."+(bell==null ? "unloaded" : bell.status()));}
        if (original instanceof FlareTipBlockEntity flare) return Component.translatable("flare.create_refinery_n_reactors."+flare.status());
        if (original instanceof CryogenicColdBoxBlockEntity box) return Component.translatable("cold_box.create_refinery_n_reactors.status." + box.status());
        if (original instanceof HorizontalTankBlockEntity boiler) {
            var reading = boiler.boilerReading();
            if (reading != null) return Component.translatable("tubular_boiler." + reading.status());
        }
        if (original instanceof IndustrialDispatchBlockEntity dispatch) return Component.translatable("industrial_dispatch." + dispatch.status());
        var be=owner(original);
        if (be==null || be.isRemoved() || be.getLevel()==null) return Component.literal("--");
        if (be instanceof ElectrolysisControllerBlockEntity control) return Component.translatable("electrolysis.status." + control.status());
        if (be instanceof CoolingTowerBlockEntity tower) return Component.translatable("plant_systems.status." + tower.status());
        if (be instanceof ProcessControlBlockEntity control) return Component.translatable("plant_systems.status." + control.status());
        if (be instanceof FuelCycleMachineBlockEntity machine) return Component.translatable("plant_systems.status." + machine.status());
        if (be instanceof CondenserWaterboxBlockEntity condenser) return Component.translatable("plant_systems.status." + condenser.status());
        if(be instanceof ReliefScrubberBlockEntity scrubber)return Component.translatable("scrubber.create_refinery_n_reactors.status."+scrubber.status());
        if(be instanceof SteamTurbineBlockEntity turbine)return Component.translatable("turbine.create_refinery_n_reactors."+turbine.thermodynamics().status());
        if (be instanceof ConverterTrunnionBearingBlockEntity converter)
            return Component.translatable("converter.create_refinery_n_reactors.status." + converter.status());
        if (be instanceof LiddedVesselBlockEntity vessel) {
            if (vessel.fouling().blocked()) return Component.translatable("reactor.create_refinery_n_reactors.status.fouled");
            if (vessel.module()==net.createplant.chemistry.ReactorModule.NONE) return Component.translatable("lidded_vessel.create_refinery_n_reactors.lid." + (vessel.open() ? "open" : "closed"));
        }
        if (be instanceof ReactorMachineBlockEntity r) return Component.translatable("reactor.create_refinery_n_reactors.status."+r.status());
        if (be instanceof FractionatingColumnBlockEntity c) return Component.translatable("distillation.create_refinery_n_reactors.status."+c.status());
        if (be instanceof FuelChannelBlockEntity lead && lead.core()!=null) return Component.translatable("reactor_core.create_refinery_n_reactors.status."+lead.core().status());
        if (be instanceof RodCapBlockEntity cap && cap.column()!=null && cap.column().formedCore()!=null)
            return Component.translatable("reactor_core.create_refinery_n_reactors.status."+cap.column().formedCore().status());
        if (be instanceof LiddedVesselBlockEntity vessel) return Component.translatable("lidded_vessel.create_refinery_n_reactors.lid." + (vessel.open() ? "open" : "closed"));
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
        return Component.literal("--");
    }

    public static Component exchangeMode(BlockEntity original) {
        var be=owner(original);
        return be instanceof ReactorMachineBlockEntity r && r.horizontal() ? r.thermal().exchangeMode() : Component.literal("--");
    }

    public static Reading read(BlockEntity original,String key) { return read(original,key,null); }

    public static Reading read(BlockEntity original,String key,Direction face) {
        if (key.startsWith("production_")) return ProductionReadings.read(owner(original), key);
        if (owner(original) instanceof GasifierBlockEntity gasifier) {
            if (key.equals("formed")) return new Reading(gasifier.formed() ? 1 : 0, 1);
            if (!gasifier.formed()) return null;
            if (key.matches("fluid_[0-5]")) {
                int index = key.charAt(6) - '0';
                return new Reading(gasifier.fluid(index).getAmount(), gasifier.capacity(index));
            }
            if (key.matches("item_[0-3]")) return new Reading(gasifier.itemCount(key.charAt(5) - '0'), 64);
            return switch (key) {
                case "progress" -> new Reading(gasifier.progress(), gasifier.duration());
                case "gasifier_temperature" -> new Reading(gasifier.temperature(), 0);
                default -> null;
            };
        }
        if (owner(original) instanceof WetProcessBlockEntity wet) {
            if (key.startsWith("fluid_") && key.length() == 7) { int i = key.charAt(6) - '0';return i >= 0 && i < 3 ? new Reading(wet.tanks[i].getFluidAmount(), wet.capacity()) : null; }
            if (key.startsWith("item_") && key.length() == 6) { int i = key.charAt(5) - '0';return i >= 0 && i < 5 ? new Reading(wet.items.getStackInSlot(i).getCount(), 64) : null; }
            return switch (key) {
                case "formed" -> new Reading(wet.formed() ? 1 : 0, 1);
                case "structure" -> new Reading(wet.width(), 0);
                case "speed" -> new Reading(Math.abs(wet.getSpeed()), 0);
                case "stress" -> new Reading(Math.abs(wet.getSpeed()) * 8, 0);
                case "progress" -> new Reading(wet.progress(), wet.duration());
                case "separation" -> new Reading(1000 - WetProcessBlockEntity.impurity(wet.getSpeed()), 1000);
                default -> null;
            };
        }

        if (original instanceof ElectricPumpBlockEntity pump) return switch (key) {
            case "electric_power" -> new Reading(pump.watts()/1000,0);
            case "energy" -> new Reading(pump.storedJoules(),ElectricPumpBlockEntity.CAPACITY);
            case "speed" -> new Reading(pump.getSpeed(),128);
            default -> null;
        };
        if (original instanceof GasHolderBlockEntity part) {var bell=part.getControllerBE();if (bell==null) return null;return switch (key) {
            case "formed" -> new Reading(bell.formed() ? 1 : 0,1);
            case "fluid_0" -> new Reading(bell.gas().getFluidAmount(),bell.capacity());
            case "bell_height" -> new Reading(bell.offset(),bell.travel());
            case "bell_travel" -> new Reading(bell.travel(),0);
            default -> null;
        };}
        if (original instanceof FlareTipBlockEntity flare) return switch (key) {
            case "flow_rate" -> new Reading(flare.flow(),FlareTipBlockEntity.MAX_FLOW);
            case "flare_height" -> new Reading(flare.flameHeight(0),FlareTipBlockEntity.MAX_FLAME);
            case "flare_burnt" -> new Reading(flare.burnt(),0);
            case "flare_vented" -> new Reading(flare.vented(),0);
            case "fluid_0" -> new Reading(flare.gas().getFluidAmount(),FlareTipBlockEntity.VOLUME*net.createplant.thermal.Grade.Pressure.PRESSURISED.storageRatio());
            default -> null;
        };
        if (original instanceof CryogenicColdBoxBlockEntity box) {
            if (key.equals("formed")) return new Reading(box.formed() ? 1 : 0, 1);
            if (key.equals("cold_box_power")) return new Reading(box.watts() / 1000, 0);
            var tanks = List.of(box.gas(), box.liquid(), box.water(), box.warm());
            for (int i = 0; i < tanks.size(); i++) {
                var tank = tanks.get(i);
                if (key.equals("fluid_" + i)) return new Reading(tank.getFluidAmount(), tank.getCapacity());
            }
        }
        if (original instanceof HorizontalTankBlockEntity boiler && (key.equals("boiler_heat") || key.equals("fluid_1"))) {
            var reading = boiler.boilerReading();
            if (reading != null) return key.equals("boiler_heat") ? new Reading(reading.heatWatts() / 1000, 0)
                : new Reading(reading.steamAmount(), reading.steamCapacity());
        }
        if (original instanceof IndustrialDispatchBlockEntity dispatch) {
            if (key.equals("dispatch_completed")) return new Reading(dispatch.completed(), 0);
            if (key.equals("progress")) { var order = dispatch.order();return order == null ? null : new Reading(dispatch.progress(), order.value().getProcessingDuration()); }
            if (key.equals("fluid_0")) return new Reading(dispatch.cargo().getFluidAmount(), dispatch.cargo().getCapacity());
            if (key.equals("item_0")) return new Reading(dispatch.rewards().getStackInSlot(0).getCount(), 64);
        }
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
        var be=owner(original);
        if (be==null || be.isRemoved() || be.getLevel()==null) return null;
        if (be instanceof ElectrolysisControllerBlockEntity control) return switch (key) {
            case "formed" -> new Reading(control.formed() ? 1 : 0, 1);
            case "electric_power" -> new Reading(control.watts() / 1000, 0);
            case "energy" -> new Reading(control.joules(), net.createplant.chemistry.ElectrolysisProcess.BUFFER_JOULES);
            case "electrolysis_voltage" -> new Reading(control.volts(), 0);
            case "electrolysis_current" -> new Reading(control.amps(), 0);
            case "electrolysis_plates" -> new Reading(control.plates(), control.plateCapacity());
            case "electrolysis_limit" -> new Reading(control.limit(), 100);
            case "electrolysis_metal" -> new Reading(control.metalRate(), 0);
            case "electrolysis_metal_storage" -> new Reading(control.metalStored(), control.cells().size() * 64);
            case "electrolysis_recovered_acid" -> new Reading(control.recoveredAcidRate(), 0);
            case "electrolysis_cathode" -> new Reading(control.cathodeRate(), 256);
            case "electrolysis_anode" -> new Reading(control.anodeRate(), 128);
            case "electrolysis_soda" -> new Reading(control.sodaRate(), 320);
            case "electrolysis_rate" -> new Reading(control.running(), control.plates());
            case "fluid_0", "fluid_1", "fluid_2", "fluid_3" -> new Reading(control.stored(key.charAt(6) - '0'), control.cells().size() * ElectrolysisCellBlockEntity.CAPACITY);
            default -> null;
        };
        var level=be.getLevel();var pos=be.getBlockPos();
        if (be instanceof LiddedVesselBlockEntity vessel) {
            switch (key) {
                case "structure": return new Reading(vessel.getHeight(),net.createplant.block.ReactorStructure.MAX_SECTIONS);
                case "vessel_fouling": return new Reading(vessel.fouling().foulingFraction()*100,100);
                case "vessel_drain": return new Reading(vessel.fouling().drainOpen() ? 1 : 0,1);
                case "vessel_waste": return new Reading(vessel.fouling().drainPort().getFluidInTank(0).getAmount(),net.createplant.chemistry.VesselFouling.WASTE_CAPACITY);
            }
            if (key.startsWith("fluid_")) {
                int index=Integer.parseInt(key.substring(key.lastIndexOf('_')+1));
                if (index<0 || index>=vessel.displayedFluidSlots()) return null;
                var fluid=vessel.displayedFluid(index);
                return new Reading(fluid.getAmount(),vessel.getDisplayedCapacity(index));
            }
            if (key.startsWith("item_")) {
                int index=Integer.parseInt(key.substring(5));
                if (index<0 || index>=vessel.displayedItemSlots()) return null;
                var stack=vessel.displayedItem(index);
                return new Reading(stack.getCount(),stack.isEmpty() ? 64 : stack.getMaxStackSize());
            }
        }
        if (be instanceof ProcessControlBlockEntity control) return switch (key) {
            case "control_measurement" -> control.valid() ? new Reading(control.measurement(), 100) : null;
            case "control_output" -> new Reading(control.output(), 100);
            case "control_setpoint" -> new Reading(control.target(), 100);
            case "control_kp" -> new Reading(control.kp(), 0);
            case "control_ti" -> new Reading(control.ti(), 0);
            case "control_td" -> new Reading(control.td(), 0);
            default -> null;
        };
        if (be instanceof CoolingTowerBlockEntity tower) {
            if (key.equals("formed")) return new Reading(tower.formed() ? 1 : 0, 1);
            if (key.equals("cooling_power")) return new Reading(tower.watts() / 1e6, tower.maximumFlow() * .0004);
            for (int i = 0; i < 3; i++) {
                if (key.equals("fluid_" + i)) return new Reading(tower.tank(i).getFluidAmount(), tower.tank(i).getCapacity());
            }
        }
        if (be instanceof FuelCycleMachineBlockEntity machine) {
            if (key.equals("formed")) return new Reading(machine.formed() ? 1 : 0, 1);
            if (key.equals("progress")) return new Reading(machine.progress(), 0);
            if (key.equals("electric_power")) return new Reading(machine.watts() / 1000, 1500);
            for (int i = 0; i < 6; i++) if (key.equals("fluid_" + i)) return new Reading(machine.tank(i).getFluidAmount(), machine.tank(i).getCapacity());
            for (int i = 0; i < 3; i++) if (key.equals("item_" + i)) return new Reading(machine.inventory().getStackInSlot(i).getCount(), 64);
        }
        if (be instanceof CondenserWaterboxBlockEntity condenser) {
            if (key.equals("cooling_power")) return new Reading(condenser.watts() / 1e6, 338);
            if (key.equals("formed")) return new Reading(condenser.formed() ? 1 : 0, 1);
            for (int i = 0; i < 2; i++) {
                var tank = i == 0 ? condenser.cold() : condenser.hot();
                if (key.equals("fluid_" + i)) return new Reading(tank.getFluidAmount(), tank.getCapacity());
            }
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
            }
        }
        if (be instanceof PipeGaugeBlockEntity gauge) return switch (key) {
            case "redstone_signal" -> gauge.measured() && gauge.kind()==PipeGaugeBlock.Kind.PRESSURE ? new Reading(gauge.analogSignal(),15) : null;
            case "pipe_temperature" -> null;
            case "section_pressure" -> null;
            case "flow_rate" -> gauge.measured() && gauge.kind()==PipeGaugeBlock.Kind.FLOW ? new Reading(gauge.reading(),0) : null;
            default -> null;
        };
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
        if (be instanceof RadiatorBlockEntity radiator) {
            switch(key) {
                case "structure":return new Reading(radiator.fanCount(),8);
                case "flow_lower":return new Reading(radiator.actualFlow()/5d,radiator.fanCount()*20d*net.createplant.thermal.SeasonalThermal.cooling(radiator.getLevel()));
            }
        }
        if (be instanceof LargeFlywheelBlockEntity flywheel) {
            double value=switch (key) {
                case "flywheel_energy" -> flywheel.displayedEnergyJ();
                case "flywheel_rpm" -> flywheel.displayedRotorRpm();
                case "flywheel_charge" -> flywheel.displayedChargePowerW();
                case "flywheel_discharge" -> flywheel.displayedDischargePowerW();
                case "flywheel_acceleration" -> flywheel.displayedAccelerationPowerW();
                case "flywheel_maintenance" -> flywheel.displayedMaintenancePowerW();
                case "flywheel_inertia" -> flywheel.displayedInertiaKgM2();
                case "flywheel_count" -> flywheel.displayedFlywheelCount();
                default -> Double.NaN;
            };
            if (Double.isFinite(value)) return new Reading(value,0);
        }
        if(be instanceof SteamTurbineBlockEntity turbine){
            switch(key){
                case "condenser_heat":return new Reading(turbine.thermodynamics().pendingCondenserJoules()/1e6,net.createplant.thermal.TurbineThermal.CONDENSER_HEAT_CAPACITY/1e6);
                case "steam_flow":return new Reading(turbine.localSteamInput(),64);
                case "rotor_power":return new Reading(turbine.thermodynamics().work()*20,net.createplant.thermal.TurbineThermal.MAX_J*20);
                case "rotor_feed":return new Reading(turbine.thermodynamics().input().getFluidAmount(),64000);
                case "rotor_work":return new Reading(turbine.thermodynamics().totalWork(),0);
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
        if (key.equals("speed")) return be instanceof KineticBlockEntity kinetic ? new Reading(Math.abs(kinetic instanceof ReactorMachineBlockEntity reactor ? reactor.rotationSpeed() : kinetic.getSpeed()),0) : null;
        if (be instanceof ReactorMachineBlockEntity r) {
            if (key.equals("structure")) return new Reading(r.sections(),r.exchanger()!=null ? r.exchanger().max*r.exchanger().sections : 12);
            if (key.equals("formed")) return new Reading(r.formed() ? 1 : 0,1);
            if (r.horizontal()) {
                var thermal=r.thermal();
                switch (key) {
                    case "flow_lower":return new Reading(thermal.coldFlow()/5d,thermal.flowPerStep()/5d);
                    case "flow_upper":return new Reading(thermal.hotFlow()/5d,thermal.flowPerStep()/5d);
                }
            }
        }
        if (be instanceof FlowRegulatorBlockEntity regulator && key.equals("flow_limit")) return new Reading(regulator.effectiveLimit(),0);
        if (be instanceof FractionatingColumnBlockEntity c) {
            if (key.equals("structure")) return new Reading(c.getHeight(),0);
            if (key.equals("formed")) return new Reading(c.ready() ? 1 : 0,1);
            if (key.equals("progress")) return new Reading(c.progress(),c.cycleTicks());
        }
        if (key.equals("progress")) {
            if (be instanceof ReactorMachineBlockEntity r && !r.horizontal()) return new Reading(r.progress(),r.cycleTicks());
        }
        if (key.startsWith("item_")) {
            int index=Integer.parseInt(key.substring(5));
            var items=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,null);
            if (items==null && face!=null) items=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,face);
            if (items==null) for (Direction side:Direction.values()) {
                items=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,side);if (items!=null) break;
            }
            if (items==null || index>=items.getSlots()) return null;
            var stack=items.getStackInSlot(index);
            return new Reading(stack.getCount(),stack.isEmpty() ? items.getSlotLimit(index) : Math.min(items.getSlotLimit(index),stack.getMaxStackSize()));
        }
        if (key.startsWith("fluid_")) {
            int index=Integer.parseInt(key.substring(key.lastIndexOf('_')+1));
            IFluidHandler fluids;
            if (be instanceof ReactorMachineBlockEntity r && r.horizontal()) {
                if (index>=4) return null;
                fluids=r.thermal().tank(index);index=0;
            } else {
                fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,null);
                if (fluids==null && face!=null) fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,face);
                if (fluids==null) for (Direction side:Direction.values()) {
                    fluids=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,side);if (fluids!=null) break;
                }
            }
            if (fluids==null || index>=fluids.getTanks()) return null;
            var fluid=fluids.getFluidInTank(index);
            return new Reading(fluid.getAmount(),fluids.getTankCapacity(index));
        }
        return null;
    }

    public static Component productionBottleneck(BlockEntity original) { return ProductionReadings.limit(owner(original)); }

    public static void appendProduction(BlockEntity original, List<Component> tooltip) { ProductionReadings.append(owner(original), tooltip); }

    public static void compact(List<Component> text) {
        text.removeIf(c->c.getString().isBlank());
    }
}
