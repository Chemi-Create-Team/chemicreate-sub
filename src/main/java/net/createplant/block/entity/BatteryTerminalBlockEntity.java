package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Locale;
import net.createplant.block.BatteryChemistry;
import net.createplant.block.BatteryStructure;
import net.createplant.block.BatteryTerminalBlock;
import net.createplant.registry.CEBattery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The owner of a battery bank. In the circuit it is a voltage source whose voltage follows the charge, behind the resistance of all its casings in
 * parallel. Current out of the source discharges the bank; current driven into it by a higher voltage charges it, with a loss. The bank works only
 * while the box of casings is whole and the electrolyte fills it.
 */
public class BatteryTerminalBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int REVALIDATE_TICKS = 40, FULL_PAUSE = 20;
    private static final double AMPS_NOISE = .01;
    private final BatteryChemistry chemistry;
    private final FluidTank tank;
    private int casings;
    private String structure = "casing";
    private double charge;
    private double volts, amps;
    private long pausedUntil, lastSupplied = Long.MIN_VALUE / 2;
    private String status = "structure";

    public BatteryTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(CEBattery.TERMINAL_ENTITY.get(), pos, state);
        chemistry = ((BatteryTerminalBlock) state.getBlock()).chemistry();
        tank = new FluidTank(0, fluid -> fluid.getFluid().isSame(chemistry.electrolyte())) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEBattery.TERMINAL_ENTITY.get(), (be, side) -> be.tank);
    }

    public double capacityJoules() { return casings * chemistry.joulesPerCasing; }
    public double stateOfCharge() { return casings == 0 ? 0 : charge / capacityJoules(); }
    public double emf() { return chemistry.volts(stateOfCharge()); }
    public double ohms() { return chemistry.ohmsPerCasing / Math.max(1, casings); }

    /** Whether the bank is in the circuit this tick: a whole box, a full electrolyte and not paused for a full charge. */
    public boolean ready(long gameTime) {
        return casings > 0 && tank.getCapacity() > 0 && tank.getFluidAmount() >= tank.getCapacity() && gameTime >= pausedUntil;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (level.getGameTime() % REVALIDATE_TICKS == (worldPosition.hashCode() & 0x7fffffff) % REVALIDATE_TICKS || casings == 0 && level.getGameTime() % 5 == 0) revalidate();
        if (level.getGameTime() - lastSupplied > 2) status = "unwired";
    }

    // ponytail: the box is searched every two seconds, up to 4096 casings; upgrade to searching only on neighbour changes if banks get numerous.
    private void revalidate() {
        int result = BatteryStructure.find(level, worldPosition, chemistry);
        int found = Math.max(result, 0);
        String reason = result > 0 ? "ok" : BatteryStructure.reason(result);
        if (result == BatteryStructure.UNLOADED) return;
        if (found == casings && reason.equals(structure)) return;
        casings = found;
        structure = reason;
        tank.setCapacity(casings * BatteryChemistry.ELECTROLYTE_PER_CASING);
        if (tank.getFluidAmount() > tank.getCapacity()) tank.setFluid(tank.getFluid().copyWithAmount(tank.getCapacity()));
        charge = Math.min(charge, capacityJoules());
        setChanged();
        sendData();
    }

    /** The circuit's answer: the voltage across the terminals, node 0 positive, while the bank was in the circuit. */
    public void supplied(double terminalVolts, boolean sourced, long gameTime) {
        String before = status;
        lastSupplied = gameTime;
        volts = sourced ? terminalVolts : 0;
        amps = sourced ? (emf() - terminalVolts) / ohms() : 0;
        if (sourced && amps > AMPS_NOISE) {
            charge = Math.max(0, charge - emf() * amps / 20);
            status = "discharging";
        } else if (sourced && amps < -AMPS_NOISE) {
            charge = Math.min(capacityJoules(), charge - amps * terminalVolts / 20 * chemistry.efficiency);
            status = "charging";
            if (charge >= capacityJoules()) pausedUntil = gameTime + FULL_PAUSE;
        } else {
            amps = 0;
            status = sourced ? "idle" : casings == 0 ? "structure" : tank.getFluidAmount() < tank.getCapacity() ? "electrolyte" : gameTime < pausedUntil ? "full" : "idle";
        }
        setChanged();
        if (!status.equals(before) || gameTime % 20 == 0) sendData();
    }

    public double watts() { return volts * amps; }
    public String status() { return level != null && level.getGameTime() - lastSupplied > 2 ? "unwired" : status; }
    public FluidTank electrolyte() { return tank; }
    public int casings() { return casings; }
    public double charge() { return charge; }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tank.writeToNBT(registries, tag);
        tag.putInt("Casings", casings);
        tag.putString("Structure", structure);
        tag.putDouble("Charge", charge);
        tag.putString("Status", status);
        if (clientPacket) { tag.putDouble("Volts", volts);tag.putDouble("Amps", amps);tag.putLong("LastSupplied", lastSupplied); }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        casings = Math.max(0, tag.getInt("Casings"));
        tank.setCapacity(casings * BatteryChemistry.ELECTROLYTE_PER_CASING);
        tank.readFromNBT(registries, tag);
        structure = tag.contains("Structure") ? tag.getString("Structure") : "casing";
        double stored = tag.getDouble("Charge");
        charge = Double.isFinite(stored) ? Math.clamp(stored, 0, capacityJoules()) : 0;
        status = tag.contains("Status") ? tag.getString("Status") : "structure";
        if (clientPacket) { volts = tag.getDouble("Volts");amps = tag.getDouble("Amps");lastSupplied = tag.getLong("LastSupplied"); }
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level == null) return;
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        String key = "battery.create_refinery_n_reactors.";
        String shown = status();
        text.add(Component.translatable(key + "status." + (shown.equals("structure") ? "structure." + structure : shown)));
        text.add(Component.translatable(key + "charge", String.format(Locale.ROOT, "%.0f", stateOfCharge() * 100), String.format(Locale.ROOT, "%.1f", charge / 1000), String.format(Locale.ROOT, "%.1f", capacityJoules() / 1000)));
        text.add(Component.translatable(key + "power", String.format(Locale.ROOT, "%.2f", watts() / 1000), String.format(Locale.ROOT, "%.0f", volts), String.format(Locale.ROOT, "%.1f", amps)));
        text.add(Component.translatable(key + "electrolyte", tank.getFluidAmount(), tank.getCapacity()));
        return true;
    }
}
