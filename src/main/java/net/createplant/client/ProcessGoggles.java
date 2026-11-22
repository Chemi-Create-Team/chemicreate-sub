package net.createplant.client;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.ColumnRingBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.block.entity.GasTankContainerBlockEntity;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.block.entity.HoseConnectorBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.mixture.MixtureGoggleHelper;
import net.createplant.compat.display.MachineReadings;
import net.createplant.thermal.Grade;
import net.createplant.thermal.TankGrade;
import net.createplant.thermal.TankLayers;
import net.createplant.transport.FluidHazards;
import net.createplant.transport.HoseSocket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class ProcessGoggles {
    private static final Set<String> BLOCKED = Set.of("heat", "preheat", "heat_required", "pressure", "catalyst", "mixed", "hatch", "structure",
        "no_feed", "temperature_delta", "feed", "input", "output", "products_full", "gas_output", "power", "no_power", "speed", "no_speed", "overstressed", "rpm",
        "blocked", "fouled", "redstone", "coolant", "no_coolant", "voltage", "incomplete", "unformed", "unloaded");
    private static final Direction[] FACES = Direction.values();

    private ProcessGoggles() { }

    public static void add(BlockEntity source, List<Component> lines, boolean sneaking) {
        if (source instanceof HoseConnectorBlockEntity && !sneaking) return;
        var owner = MachineReadings.owner(source);
        if (owner == null) return;
        if (owner instanceof HorizontalTankBlockEntity tank && tank.hasBoilerGauge()) return;
        if (owner instanceof ReactorMachineBlockEntity reactor && reactor.horizontal()) return;
        if (source instanceof LiddedVesselBlockEntity && owner instanceof LiddedVesselBlockEntity vessel
            && vessel.showGasComposition() && MixtureGoggleHelper.appendGasLayers(lines, vessel.gasLayers(), ((net.createplant.thermal.ThermalFluidTank) vessel.getTankInventory()).gasCapacity())) return;
        if (source instanceof FluidTankBlockEntity && owner instanceof FluidTankBlockEntity tank) {
            var fluid = tank.getTankInventory().getFluid();
            if (!fluid.isEmpty() && FluidHazards.of(fluid).gas() && MixtureGoggleHelper.appendGasLayers(lines, TankLayers.ordered(List.of(fluid)), tank.getTankInventory().getCapacity())) {
                if (tank instanceof GasTankContainerBlockEntity container) container.addDrainReadout(lines,true);
                if (tank instanceof GasHolderBlockEntity holder) holder.addDrainReadout(lines,true);
                return;
            }
        }
        var hit = Minecraft.getInstance().hitResult;
        var face = hit instanceof BlockHitResult onBlock && onBlock.getBlockPos().equals(source.getBlockPos()) ? onBlock.getDirection() : null;
        var stock = stock(source, owner, face);
        var gauges = gauges(source, owner, stock);
        var stopped = stopped(owner);
        if (stock == null && gauges == null && stopped == null) {
            MachineReadings.appendProduction(source, lines);
            return;
        }
        var prior = List.copyOf(lines);
        var production = new ArrayList<Component>(4);
        MachineReadings.appendProduction(source, production);
        lines.clear();
        if (stopped != null) lines.add(ChemicalReadout.warning(stopped));
        if (gauges != null) lines.add(gauges);
        var nativeStock = prior.stream().filter(line -> line.getContents() instanceof TranslatableContents text
            && text.getKey().equals(ChemicalReadout.KEY + "contents")).findFirst().orElse(null);
        if (nativeStock != null) lines.add(nativeStock);
        else if (stock != null) lines.add(stock.capacity < 0 ? ChemicalReadout.name(stock.fluid) : ChemicalReadout.contents(stock.fluid, stock.capacity));
        if (!production.isEmpty()) {
            var combined = Component.empty();
            for (var line : production) {
                if (!combined.getSiblings().isEmpty()) combined.append("  ·  ");
                combined.append(line);
            }
            lines.add(combined);
        }
        var hazards = sneaking && stock != null ? ChemicalReadout.hazards(stock.fluid) : null;
        if (hazards != null) lines.add(hazards);
        for (var line : prior) if (line != nativeStock && !line.equals(gauges)
            && (stopped == null || !line.getString().equals(stopped.getString()))) lines.add(line);
        MachineReadings.compact(lines);
    }

    public static FluidStack pipeFluid(FluidPipeBlockEntity pipe) {
        var behaviour = pipe.getBehaviour(FluidTransportBehaviour.TYPE);
        if (behaviour != null) for (var face : FACES) {
            var connection = behaviour.getConnection(face);
            if (connection != null) {
                var fluid = connection.getProvidedFluid();
                if (!fluid.isEmpty()) return fluid;
            }
        }
        return FluidStack.EMPTY;
    }

    private record Stock(FluidStack fluid, int capacity) { }

    private static Stock stock(BlockEntity source, BlockEntity owner, Direction face) {
        if (source instanceof FluidPipeBlockEntity pipe) return stockOf(pipeFluid(pipe), -1);
        if (source instanceof HoseSocket.Endpoint hose && hose.hose().connected()) {
            var measured = stockOf(hose.hose().measuredFluid(), -1);
            if (measured != null) return measured;
        }
        if (owner instanceof HighPressurePumpBlockEntity pump) return stockOf(pump.contents(), pump.internalPort(true).getTankCapacity(0));
        if (owner instanceof ReactionTowerBlockEntity tower) {
            int first = face == Direction.DOWN || source.getBlockPos().getY() > owner.getBlockPos().getY() ? 2 : 0;
            for (int bank = 0; bank < 4; bank++) {
                int i = (first + bank) % 4;
                var stock = stockOf(tower.contents(i), tower.contentsCapacity(i));
                if (stock != null) return stock;
            }
        }
        if (source instanceof FractionatingColumnBlockEntity column && face != null) {
            var stock = stockOf(column.port(face));
            if (stock != null) return stock;
        }
        if (owner instanceof FractionatingColumnBlockEntity column) return stockOf(column.content(0), column.capacity());
        if (owner instanceof GasifierBlockEntity gasifier) {
            for (int bank : new int[]{2, 1}) {
                var stock = stockOf(gasifier.fluid(bank), gasifier.capacity(bank));
                if (stock != null) return stock;
            }
        }
        if (owner instanceof FluidTankBlockEntity tank && !tank.getTankInventory().isEmpty()) return stockOf(tank.getTankInventory());
        if (owner instanceof ReactorMachineBlockEntity reactor) {
            for (int bank = 0; bank < reactor.displayedFluidSlots(); bank++) {
                var stock = stockOf(reactor.displayedFluid(bank), reactor.getDisplayedCapacity(bank));
                if (stock != null) return stock;
            }
        }
        if (source.getLevel() == null) return null;
        var cap = Capabilities.FluidHandler.BLOCK;
        var stock = stockOf(source.getLevel().getCapability(cap, source.getBlockPos(), face));
        return stock != null || source == owner || owner.getLevel() == null ? stock : stockOf(owner.getLevel().getCapability(cap, owner.getBlockPos(), null));
    }

    private static Stock stockOf(IFluidHandler tanks) {
        if (tanks != null) for (int slot = 0; slot < tanks.getTanks(); slot++) {
            var stack = tanks.getFluidInTank(slot);
            if (!stack.isEmpty()) return new Stock(stack, tanks.getTankCapacity(slot));
        }
        return null;
    }

    /** Pipes, pumps and rated tanks draw the contents' temperature and pressure on their own scale. */
    private static boolean rated(BlockEntity source, BlockEntity owner) {
        return net.createplant.transport.PipeMaterial.carrier(source.getBlockState()) != null
            || owner instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity tank && TankGrade.of(tank) != null;
    }

    private static Stock stockOf(FluidStack fluid, int capacity) { return fluid.isEmpty() ? null : new Stock(fluid, capacity); }

    private static Component gauges(BlockEntity source, BlockEntity owner, Stock stock) {
        Component heat = null, pressure = null;
        if (owner instanceof ReactionTowerBlockEntity tower) {
            var recipe = tower.recipe();
            if (recipe != null && recipe.requiredHeat() != HeatCondition.NONE) heat = ChemicalReadout.heat(tower.heaterGrade(), minimum(recipe.requiredHeat()), Grade.Heat.VERY_HOT);
            pressure = ChemicalReadout.pressure(Grade.Pressure.of(tower.pressure()), null, null);
        } else if (owner instanceof GasifierBlockEntity gas && gas.formed()) {
            heat = ChemicalReadout.processHeat(gas.heatingStage());
        } else if (stock != null && !rated(source, owner)) {
            return net.createplant.thermal.FluidState.describe(stock.fluid);
        }
        return heat == null ? pressure : pressure == null ? heat : Component.empty().append(heat).append("   ").append(pressure);
    }

    private static Grade.Heat minimum(HeatCondition heat) {
        return heat == HeatCondition.SUPERHEATED ? Grade.Heat.VERY_HOT : Grade.Heat.HOT;
    }

    private static Component stopped(BlockEntity machine) {
        if (machine instanceof ColumnRingBlockEntity ring && ring.storey() instanceof AbsorptionTowerBlockEntity storey) machine = storey.bottomStorey();
        if (machine instanceof AbsorptionTowerBlockEntity tower) return tower.running() ? null : MachineReadings.status(tower);
        var key = ChemicalReadout.KEY;
        if (machine instanceof HighPressurePumpBlockEntity pump) {
            if (!pump.formed()) return Component.translatable(HighPressurePumpBlockEntity.KEY + "unformed");
            if (pump.getSpeed() == 0) return Component.translatable(key + "drive_missing");
            return null;
        }
        if (machine instanceof ReactionTowerBlockEntity tower) {
            var status = tower.status();
            var recipe = status.equals("heat") || status.equals("catalyst") ? tower.recipe() : null;
            if (recipe != null && status.equals("heat")) return Component.translatable(key + "heat_short", tower.heaterGrade().ordinal() + 1, minimum(recipe.requiredHeat()).ordinal() + 1);
            if (recipe != null && status.equals("catalyst") && recipe.catalyst() != Items.AIR) return Component.translatable(key + "catalyst_missing", recipe.catalyst().getDescription());
            if (status.equals("output")) return Component.translatable(key + "output_full");
            return BLOCKED.contains(status) ? Component.translatable("reaction_tower.create_refinery_n_reactors.status." + status) : null;
        }
        if (machine instanceof GasifierBlockEntity gas && gas.status().equals("preheat")) return Component.translatable(key + "heat_short", gas.heatingStage(), 5);
        var status = MachineReadings.status(machine);
        if (!(status.getContents() instanceof TranslatableContents contents)) return null;
        var id = contents.getKey();
        var reason = id.substring(id.lastIndexOf('.') + 1);
        if (reason.equals("output") || reason.equals("gas_output") || reason.equals("products_full")) return Component.translatable(key + "output_full");
        return BLOCKED.contains(reason) ? status : null;
    }
}
