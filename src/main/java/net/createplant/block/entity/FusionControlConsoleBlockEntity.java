package net.createplant.block.entity;
import net.createplant.registry.CEAtomicsDisplays;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.createplant.block.FusionConsolePanelBlock;
import net.createplant.block.FusionControlConsoleBlock;
import net.createplant.fusion.FusionPhysics;
import net.createplant.block.Finite;
import net.createplant.registry.CEDisplays;
import net.createplant.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Six hard-wired switches and lamps linked to exactly one tokamak by Create's ordinary Display Link. */
public class FusionControlConsoleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int WIDTH = 3, DEPTH = 2, MASTER_COLUMN = 1, MASTER_DEPTH = 0;
    private static final int MAX_LINKS = 16;
    private static final String KEY = "fusion_console.create_refinery_n_reactors.";
    private final Set<BlockPos> displayLinks = new LinkedHashSet<>();
    private boolean assembled, linked, vacuum, cryogenics, magnets, plasma, emergency, quench;
    private String status = "unlinked", linkStatus = "missing";
    private BlockPos source;
    private int target = 80, fingerprint;
    private double power, vacuumPa = 101_325, coilK = 293.15, charge, plasmaK;

    public FusionControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(CEFusion.CONSOLE_ENTITY.get(), pos, state);
        setLazyTickRate(5);
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public Direction facing() { return getBlockState().getValue(FusionControlConsoleBlock.FACING); }
    public Direction right() { return facing().getCounterClockWise(); }
    public BlockPos origin() { return worldPosition.relative(right(), -MASTER_COLUMN).relative(facing(), -MASTER_DEPTH); }
    public BlockPos panel(int column, int depth) { return origin().relative(right(), column).relative(facing(), depth); }

    public static BlockPos masterAt(Level level, BlockPos pos) {
        if (level == null || !level.hasChunkAt(pos)) return null;
        if (level.getBlockState(pos).is(CEFusion.CONSOLE.get())) return pos;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos candidate = pos.offset(dx, 0, dz);
            if (!(level.getBlockEntity(candidate) instanceof FusionControlConsoleBlockEntity console)) continue;
            for (int depth = 0; depth < DEPTH; depth++) for (int column = 0; column < WIDTH; column++)
                if (console.panel(column, depth).equals(pos)) return candidate;
        }
        return null;
    }

    public boolean stationComplete() {
        if (level == null) return false;
        for (int depth = 0; depth < DEPTH; depth++) for (int column = 0; column < WIDTH; column++) {
            BlockPos pos = panel(column, depth);
            if (!level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (column == MASTER_COLUMN && depth == MASTER_DEPTH) {
                if (!state.is(CEFusion.CONSOLE.get()) || state.getValue(FusionControlConsoleBlock.FACING) != facing()) return false;
            } else if (!state.is(CEFusion.CONSOLE_PANEL.get()) || state.getValue(FusionConsolePanelBlock.FACING) != facing()) return false;
        }
        if (!level.isClientSide()) indexPanels();
        return true;
    }

    private void indexPanels() {
        for (int depth = 0; depth < DEPTH; depth++) for (int column = 0; column < WIDTH; column++) {
            if (column == MASTER_COLUMN && depth == MASTER_DEPTH) continue;
            BlockPos pos = panel(column, depth);
            BlockState state = level.getBlockState(pos);
            BlockState indexed = state.setValue(FusionConsolePanelBlock.COLUMN, column).setValue(FusionConsolePanelBlock.DEPTH, depth);
            if (state != indexed) level.setBlock(pos, indexed, Block.UPDATE_CLIENTS);
        }
    }

    public void stationChanged() { fingerprint = 0; if (level != null && !level.isClientSide()) refresh(); }

    public void receiveDisplayLink(DisplayLinkBlockEntity link) {
        if (level == null || level.isClientSide() || link.getLevel() != level
            || !worldPosition.equals(masterAt(level, link.getTargetPosition()))) return;
        if (displayLinks.size() < MAX_LINKS || displayLinks.contains(link.getBlockPos())) {
            displayLinks.add(link.getBlockPos().immutable()); fingerprint = 0; setChanged();
        }
    }

    @Override public void lazyTick() { super.lazyTick(); if (level != null && !level.isClientSide()) refresh(); }

    private void refresh() {
        assembled = stationComplete();
        FusionReactorControllerBlockEntity controller = assembled ? linkedController() : null;
        linked = controller != null;
        if (controller == null) {
            status = assembled ? "unlinked" : "incomplete";
            vacuum = cryogenics = magnets = plasma = emergency = quench = false;
            power = charge = plasmaK = 0; vacuumPa = 101_325; coilK = 293.15;
        } else sample(controller);
        updateLamps();
        int next = Objects.hash(assembled, linked, status, linkStatus, source, target, vacuum, cryogenics, magnets, plasma,
            emergency, quench, q(power), q(vacuumPa), q(coilK), q(charge), q(plasmaK));
        if (next != fingerprint) { fingerprint = next; setChanged(); sendData(); }
    }

    private FusionReactorControllerBlockEntity linkedController() {
        Set<BlockPos> sources = new LinkedHashSet<>();
        boolean endpoint = false;
        var iterator = displayLinks.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof DisplayLinkBlockEntity link)
                || !worldPosition.equals(masterAt(level, link.getTargetPosition()))) { iterator.remove(); continue; }
            endpoint = true;
            if (link.activeSource != CEDisplays.MACHINE.get() || link.activeTarget != CEAtomicsDisplays.FUSION_CONSOLE.get()) continue;
            BlockPos sourcePos = link.getSourcePosition();
            if (!level.hasChunkAt(sourcePos)) continue;
            var owner = net.createplant.compat.display.MachineReadings.owner(level.getBlockEntity(sourcePos));
            if (owner instanceof FusionReactorControllerBlockEntity controller) sources.add(controller.getBlockPos());
        }
        if (!endpoint) linkStatus = "missing";
        else if (sources.isEmpty()) linkStatus = "unlinked";
        else if (sources.size() > 1) linkStatus = "ambiguous";
        else {
            source = sources.iterator().next();
            if (level.getBlockEntity(source) instanceof FusionReactorControllerBlockEntity controller) {
                linkStatus = "linked"; return controller;
            }
        }
        source = null;
        return null;
    }

    private void sample(FusionReactorControllerBlockEntity controller) {
        status = controller.status(); target = controller.targetPercent(); power = controller.fusionWatts();
        vacuumPa = controller.baseVacuumPascals(); coilK = controller.coilKelvin(); charge = controller.magnetCharge();
        plasmaK = controller.plasmaKelvin(); vacuum = controller.vacuumCommand(); cryogenics = controller.cryogenicCommand();
        magnets = controller.magnetCommand(); plasma = controller.plasmaCommand(); emergency = controller.emergency(); quench = controller.quench();
    }

    private void updateLamps() {
        for (int depth = 0; depth < DEPTH; depth++) for (int column = 0; column < WIDTH; column++) {
            BlockPos pos = panel(column, depth);
            BlockState state = level.getBlockState(pos);
            boolean on = switch (column + depth * 3) {
                case 0 -> vacuum; case 1 -> emergency; case 2 -> cryogenics;
                case 3 -> magnets; case 4 -> plasma; case 5 -> power > 0;
                default -> false;
            };
            if (state.hasProperty(FusionConsolePanelBlock.ACTIVE) && state.getValue(FusionConsolePanelBlock.ACTIVE) != on)
                level.setBlock(pos, state.setValue(FusionConsolePanelBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
            if (state.hasProperty(FusionControlConsoleBlock.ACTIVE) && state.getValue(FusionControlConsoleBlock.ACTIVE) != on)
                level.setBlock(pos, state.setValue(FusionControlConsoleBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    public void operate(int cell, Player player, boolean reverse) {
        FusionReactorControllerBlockEntity controller = stationComplete() ? linkedController() : null;
        if (controller == null) { failure(player); return; }
        switch (cell) {
            case 0 -> controller.toggle(FusionReactorControllerBlockEntity.Command.VACUUM, player);
            case 2 -> controller.toggle(FusionReactorControllerBlockEntity.Command.CRYOGENICS, player);
            case 3 -> controller.toggle(FusionReactorControllerBlockEntity.Command.MAGNETS, player);
            case 4 -> controller.toggle(FusionReactorControllerBlockEntity.Command.PLASMA, player);
            case 5 -> controller.adjustTarget(reverse ? -10 : 10, player);
            default -> player.displayClientMessage(Component.translatable(KEY + "reserved"), true);
        }
        refresh();
    }

    public void emergency(Player player, boolean reset) {
        FusionReactorControllerBlockEntity controller = stationComplete() ? linkedController() : null;
        if (controller == null) { failure(player); return; }
        controller.emergency(player, reset); refresh();
    }

    private void failure(Player player) {
        player.displayClientMessage(Component.translatable(KEY + (assembled ? "link." + linkStatus : "incomplete")), true);
    }

    private static int q(double value) { return Double.isFinite(value) ? (int) Math.round(value * 10) : 0; }
    public boolean assembled() { return assembled; } public boolean linked() { return linked; }
    public String status() { return status; } public String linkStatus() { return linkStatus; }
    public double power() { return power; } public double vacuumPa() { return vacuumPa; }
    public double coilK() { return coilK; } public double charge() { return charge; } public double plasmaK() { return plasmaK; }
    public int target() { return target; } public boolean emergency() { return emergency; }

    @Override protected AABB createRenderBoundingBox() {
        return AABB.encapsulatingFullBlocks(origin(), panel(WIDTH - 1, DEPTH - 1)).inflate(.25);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putLongArray("DisplayLinks", displayLinks.stream().mapToLong(BlockPos::asLong).toArray());
        if (source != null) tag.putLong("Source", source.asLong());
        if (!packet) return;
        tag.putBoolean("Assembled", assembled); tag.putBoolean("Linked", linked); tag.putString("Status", status); tag.putString("LinkStatus", linkStatus);
        tag.putInt("Target", target); tag.putDouble("Power", power); tag.putDouble("Vacuum", vacuumPa); tag.putDouble("Coil", coilK);
        tag.putDouble("Charge", charge); tag.putDouble("Plasma", plasmaK); tag.putBoolean("Emergency", emergency); tag.putBoolean("Quench", quench);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        displayLinks.clear(); for (long value : tag.getLongArray("DisplayLinks")) if (displayLinks.size() < MAX_LINKS) displayLinks.add(BlockPos.of(value));
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        if (!packet) return;
        assembled = tag.getBoolean("Assembled"); linked = tag.getBoolean("Linked"); status = tag.getString("Status"); linkStatus = tag.getString("LinkStatus");
        target = tag.getInt("Target"); power = Finite.of(tag.getDouble("Power"), 0, 0, Double.MAX_VALUE);
        vacuumPa = Finite.of(tag.getDouble("Vacuum"), 101325, 0, Double.MAX_VALUE); coilK = Finite.of(tag.getDouble("Coil"), 293.15, 0, Double.MAX_VALUE);
        charge = Finite.of(tag.getDouble("Charge"), 0, 0, 1); plasmaK = Finite.of(tag.getDouble("Plasma"), 0, 0, Double.MAX_VALUE); emergency = tag.getBoolean("Emergency"); quench = tag.getBoolean("Quench");
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(linked ? Component.translatable(KEY + "goggle.status", Component.translatable("fusion_reactor.create_refinery_n_reactors.status." + status))
            : Component.translatable(KEY + "link." + (assembled ? linkStatus : "incomplete")));
        text.add(Component.translatable(KEY + "goggle.power", String.format(Locale.ROOT, "%.1f", power / 1e6), target));
        text.add(Component.translatable(KEY + "goggle.vacuum", String.format(Locale.ROOT, "%.2e", vacuumPa), String.format(Locale.ROOT, "%.2f", coilK)));
        text.add(Component.translatable(KEY + (emergency ? "goggle.dump" : "goggle.magnet"), String.format(Locale.ROOT, "%.1f", charge * 100),
            String.format(Locale.ROOT, "%.1f", plasmaK / 1e6)));
        return true;
    }
}
