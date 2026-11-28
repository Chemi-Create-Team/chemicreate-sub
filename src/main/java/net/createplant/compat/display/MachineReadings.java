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
        "production_fluid","production_fluid_max","production_items","production_items_max","production_power","production_power_max","production_bottleneck",
        "pool_temperature");
    private MachineReadings() { }

    /** Returned by an addon reading hook that does not handle the request, so the shared readings continue. */
    public static final Reading PASS = new Reading(Double.NaN, Double.NaN);

    /** What an addon contributes for its own machines. Every hook runs in the order the shared checks would have, and passes when the machine is not its own. */
    public interface Addon {
        default Reading read(BlockEntity original, String key, Direction face) { return PASS; }
        default Reading readOwned(BlockEntity original, BlockEntity be, String key, Direction face) { return PASS; }
        default Component status(BlockEntity original) { return null; }
        default Component statusOwned(BlockEntity original, BlockEntity be) { return null; }
        default net.minecraft.network.chat.MutableComponent line(String metric, BlockEntity source) { return null; }
    }

    private record OwnerRule<T extends BlockEntity>(Class<T> type, java.util.function.Function<T, ? extends BlockEntity> owner) {
        BlockEntity apply(BlockEntity be) { return owner.apply(type.cast(be)); }
    }

    private static final List<Addon> ADDONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<OwnerRule<?>> OWNERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void register(Addon addon) { ADDONS.add(addon); }

    /** The block entity that owns a part, for part classes an addon adds; the result is final, also when null. */
    public static <T extends BlockEntity> void registerOwner(Class<T> type, java.util.function.Function<T, ? extends BlockEntity> owner) { OWNERS.add(new OwnerRule<>(type, owner)); }

    static net.minecraft.network.chat.MutableComponent line(String metric, BlockEntity source) {
        for (var addon : ADDONS) {
            var text = addon.line(metric, source);
            if (text != null) return text;
        }
        return null;
    }

    public static BlockEntity owner(BlockEntity be) {
        for (var rule : OWNERS) if (rule.type().isInstance(be)) return rule.apply(be);
        if (be instanceof GasifierBlockEntity gasifier) return gasifier.owner() == null ? gasifier : gasifier.owner();
        if (be instanceof ReactionTowerBlockEntity tower) return tower.owner();
        if (be instanceof WetProcessPartBlockEntity part) return part.owner();
        if (be instanceof TankCasingBlockEntity casing && casing.clarifier() != null) return casing.clarifier();
        if (be instanceof ElectrodeHeadBlockEntity head) return head.controller();
        if (be instanceof ElectrolysisCellBlockEntity cell) return cell.controller();
        if (be instanceof ElectrolysisFurnacePartBlockEntity part && part.owner() != null && part.getLevel() != null
            && part.getLevel().hasChunkAt(part.owner())) return part.getLevel().getBlockEntity(part.owner());
        if (be instanceof StackedReactorBlockEntity column) return column.bottomStorey();
        if (be instanceof ElectrolysisControllerBlockEntity control) return control.master();
        if (be instanceof LiddedVesselBlockEntity vessel) return vessel.getControllerBE();
        if (be instanceof CoolingTowerPortBlockEntity port) return port.owner();
        if (be instanceof CondenserCoolingPortBlockEntity port) return port.owner();
        if (be instanceof RadiatorBlockEntity radiator) return radiator.owner();
        if (be instanceof ThermalPartBlockEntity part) {
            var controller=part.owner();return controller!=null && controller.owns(part.getBlockPos()) ? controller : null;
        }
        if (be instanceof FractionatingColumnBlockEntity column) return column.getControllerBE();
        if (be instanceof HighPressureTurbineEndBlockEntity end) return end.exhaustSource();
        if (be instanceof HighPressureTurbinePartBlockEntity part) return part.rotor();
        if (be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank) {
            if (!tank.isController() && (tank.getLevel()==null || !tank.getLevel().hasChunkAt(tank.getController()))) return null;
            return tank.getControllerBE();
        }
        return be;
    }

    public static Component status(BlockEntity original) {
        if (owner(original) instanceof GasifierBlockEntity gasifier) return GasifierBlockEntity.message(gasifier.status());
        if (owner(original) instanceof WetProcessBlockEntity wet) return Component.translatable("wet_process.create_refinery_n_reactors.status." + wet.status());
        if (original instanceof ElectricPumpBlockEntity pump) return Component.translatable("electric_utility.create_refinery_n_reactors."+pump.status());
        if (original instanceof GasHolderBlockEntity part) {var bell=part.getControllerBE();return Component.translatable("gas_bell.create_refinery_n_reactors."+(bell==null ? "unloaded" : bell.status()));}
        if (original instanceof FlareTipBlockEntity flare) return Component.translatable("flare.create_refinery_n_reactors."+flare.status());
        if (original instanceof CryogenicColdBoxBlockEntity box) return Component.translatable("cold_box.create_refinery_n_reactors.status." + box.status());
        if (original instanceof HorizontalTankBlockEntity boiler) {
            var reading = boiler.boilerReading();
            if (reading != null) return Component.translatable("tubular_boiler." + reading.status());
        }
        for (var addon : ADDONS) {
            var text = addon.status(original);
            if (text != null) return text;
        }
        var be=owner(original);
        if (be==null || be.isRemoved() || be.getLevel()==null) return Component.literal("--");
        if (be instanceof ElectrolysisControllerBlockEntity control) return Component.translatable("electrolysis.status." + control.status());
        if (be instanceof CoolingTowerBlockEntity tower) return Component.translatable("plant_systems.status." + tower.status());
        if (be instanceof ProcessControlBlockEntity control) return Component.translatable("plant_systems.status." + control.status());
        if (be instanceof CondenserWaterboxBlockEntity condenser) return Component.translatable("plant_systems.status." + condenser.status());
        if(be instanceof ReliefScrubberBlockEntity scrubber)return Component.translatable("scrubber.create_refinery_n_reactors.status."+scrubber.status());
        if(be instanceof SteamTurbineBlockEntity turbine)return Component.translatable("turbine.create_refinery_n_reactors."+turbine.thermodynamics().status());
        if (be instanceof LiddedVesselBlockEntity vessel) {
            if (vessel.module()==net.createplant.chemistry.ReactorModule.NONE) return Component.translatable("lidded_vessel.create_refinery_n_reactors.lid." + (vessel.open() ? "open" : "closed"));
        }
        if (be instanceof ReactorMachineBlockEntity r) return Component.translatable("reactor.create_refinery_n_reactors.status."+r.status());
        if (be instanceof FractionatingColumnBlockEntity c) return Component.translatable("distillation.create_refinery_n_reactors.status."+c.status());
        if (be instanceof LiddedVesselBlockEntity vessel) return Component.translatable("lidded_vessel.create_refinery_n_reactors.lid." + (vessel.open() ? "open" : "closed"));
        for (var addon : ADDONS) {
            var text = addon.statusOwned(original, be);
            if (text != null) return text;
        }
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
        for (var addon : ADDONS) {
            var reading = addon.read(original, key, face);
            if (reading != PASS) return reading;
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
                case "vessel_fouling": return new Reading(0,100); // vessels no longer foul; the measurement keeps its place for saved display links
                case "vessel_drain": return new Reading(0,1);
                case "vessel_waste": return new Reading(0,1);
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
        if (be instanceof CondenserWaterboxBlockEntity condenser) {
            if (key.equals("cooling_power")) return new Reading(condenser.watts() / 1e6, 338);
            if (key.equals("formed")) return new Reading(condenser.formed() ? 1 : 0, 1);
            for (int i = 0; i < 2; i++) {
                var tank = i == 0 ? condenser.cold() : condenser.hot();
                if (key.equals("fluid_" + i)) return new Reading(tank.getFluidAmount(), tank.getCapacity());
            }
        }
        if (be instanceof PipeGaugeBlockEntity gauge) return switch (key) {
            case "redstone_signal" -> gauge.measured() && gauge.kind()==PipeGaugeBlock.Kind.PRESSURE ? new Reading(gauge.analogSignal(),15) : null;
            case "pipe_temperature" -> null;
            case "section_pressure" -> null;
            case "flow_rate" -> gauge.measured() && gauge.kind()==PipeGaugeBlock.Kind.FLOW ? new Reading(gauge.reading(),0) : null;
            default -> null;
        };
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
        for (var addon : ADDONS) {
            var reading = addon.readOwned(original, be, key, face);
            if (reading != PASS) return reading;
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
