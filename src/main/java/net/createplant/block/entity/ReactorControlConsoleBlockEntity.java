package net.createplant.block.entity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.compat.display.MachineReadings;
import net.createplant.nuclear.Finite;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleLink;
import net.createplant.nuclear.ReactorConsoleStation;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.nuclear.ReactorAutomaticControl;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CEDisplays;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Server-owned state of the physical control room. Create Display Links targeted at the room select one formed core;
 * the console samples it four times a second and sends only compact instrument and rod-map snapshots to clients.
 */
public class ReactorControlConsoleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, ReactorConsoleStation {
    public enum CraneMode { STOPPED, MANUAL, AUTOMATIC }
    public static final int DESK_WIDTH = 4;
    /** Below this per-bundle flow a powered channel is shown blinking on the map and raises the flow alarm. */
    private static final double LOW_FLOW = .5;
    public static final int LOW_FLOW_BIT = 1 << 24;
    private static final String KEY = "reactor_control_console.create_refinery_n_reactors.";
    private static final int MAX_DISPLAY_LINKS = 32;
    private BlockPos controller, selectedBottom;
    /** The linked display board's master, and the board a placed unit remembered until the desk forms. */
    private BlockPos display, remembered;
    private BlockPos cranePos;
    private CraneMode craneMode = CraneMode.STOPPED;
    private String craneStatus = "unloaded";
    private int selectedSlot = -1, selectedIndex = -1, sentFingerprint;
    private boolean assembled, walled, linked, scram;
    public static final int SAMPLES = 48;
    private boolean topologyDirty = true, guardOpen, acknowledged;
    private double controlTarget = -1;
    private int mode, bankTarget = 100, alarms, chartCount, chartCursor, lastButton = -1;
    private long buttonTick = -100, lampTestUntil, lastControlTick = -10, lastRecordTick = -20;
    private byte[] waterTrace = new byte[SAMPLES], graphiteTrace = new byte[SAMPLES];
    private double totalFlow, xenon, surfaceKelvin = 273.15, nominalWatts;
    private int bundles;
    private String reactorStatus = "unlinked", linkStatus = "missing";
    private double power, reactivity, hottestKelvin = 273.15, coolantKelvin = 273.15, graphiteKelvin = 273.15,
        representativeOutletKelvin = 273.15, voidFraction, rodInsertion;
    /** Compact top-down snapshots: rods and individual loaded fuel positions with their temperature and low-flow flag. */
    private int[] rodMap = new int[0], fuelMap = new int[0];
    private final Set<BlockPos> displayLinks = new LinkedHashSet<>();

    public ReactorControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.REACTOR_CONTROL_CONSOLE_ENTITY.get(), pos, state);
        setLazyTickRate(5);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public Direction facing() { return getBlockState().getValue(ReactorControlConsoleBlock.FACING); }
    public Direction right() { return facing().getCounterClockWise(); }
    public BlockPos deskPos(int column) { return worldPosition.relative(right(), column); }
    public BlockPos display() { return display; }

    public boolean isMaster() {
        return getBlockState().getValue(ReactorControlConsoleBlock.SECTION) == ReactorConsoleStructure.MASTER;
    }
    public ReactorControlConsoleBlockEntity owner() {
        BlockPos root = level == null ? null : masterAt(level, worldPosition);
        return root != null && level.getBlockEntity(root) instanceof ReactorControlConsoleBlockEntity console ? console : null;
    }
    public static BlockPos masterAt(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos))
            return null;
        var state = level.getBlockState(pos);
        BlockPos root = ReactorConsoleStructure.DESK.master(pos, state);
        if (root == null || !level.hasChunkAt(root))
            return null;
        var lead = level.getBlockState(root);
        return lead.is(CENuclear.REACTOR_CONTROL_CONSOLE.get())
                && lead.getValue(ReactorControlConsoleBlock.SECTION) == ReactorConsoleStructure.MASTER
                && lead.getValue(ReactorControlConsoleBlock.FACING) == state.getValue(ReactorControlConsoleBlock.FACING)
            ? root
            : null;
    }

    public void receiveDisplayLink(DisplayLinkBlockEntity link) {
        if (level == null || level.isClientSide() || link.getLevel() != level
            || !targetedBy(link.getTargetPosition())) return;
        if (displayLinks.size() >= MAX_DISPLAY_LINKS && !displayLinks.contains(link.getBlockPos())) return;
        if (displayLinks.add(link.getBlockPos().immutable())) {
            sentFingerprint = 0;
            setChanged();
        }
    }

    /** A Create Display Link may target any unit of this desk or of its linked display board. */
    private boolean targetedBy(BlockPos target) {
        return worldPosition.equals(masterAt(level, target)) || display != null && display.equals(ReactorConsoleDisplayBlockEntity.masterAt(level, target));
    }
    /** The desk serving a Display Link target: a desk unit, or a unit of a display board linked to a desk. */
    public static ReactorControlConsoleBlockEntity forTarget(net.minecraft.world.level.Level level, BlockPos target) {
        BlockPos master = masterAt(level, target);
        if (master != null)
            return level.getBlockEntity(master) instanceof ReactorControlConsoleBlockEntity desk ? desk : null;
        BlockPos board = ReactorConsoleDisplayBlockEntity.masterAt(level, target);
        return board != null && level.getBlockEntity(board) instanceof ReactorConsoleDisplayBlockEntity display ? display.linkedConsole() : null;
    }

    public boolean stationComplete() {
        return level != null && isMaster() && ReactorConsoleStructure.DESK.complete(level, worldPosition, facing());
    }
    @Override public void stationChanged() {
        topologyDirty = true;
        sentFingerprint = 0;
        if (level == null || level.isClientSide || !isMaster())
            return;
        for (int part = 0; part < ReactorConsoleStructure.DESK.cells; part++)
            if (level.getBlockEntity(deskPos(part)) instanceof ReactorControlConsoleBlockEntity unit && unit.remembered != null) {
                linkDisplay(unit.remembered);
                unit.remembered = null;
                unit.setChanged();
            }
        refresh();
    }
    @Override public void invalidateStation() {
        releaseBank();
        craneMode = CraneMode.STOPPED;
        assembled = walled = false;
        topologyDirty = true;
        sentFingerprint = 0;
        if (level != null && !level.isClientSide)
            sendData();
    }
    /** Called when a unit is placed from a display-board item that remembered a board. */
    public void remember(BlockPos board) {
        remembered = board;
        setChanged();
    }
    /** Links this desk to a formed display board within range; the board follows the desk. */
    public boolean linkDisplay(BlockPos board) {
        if (level == null || !isMaster() || !ReactorConsoleLink.inRange(worldPosition, board) || !level.hasChunkAt(board)
            || !(level.getBlockEntity(board) instanceof ReactorConsoleDisplayBlockEntity target) || !target.isMaster() || !target.assembled())
            return false;
        if (display != null && !display.equals(board) && level.hasChunkAt(display)
            && level.getBlockEntity(display) instanceof ReactorConsoleDisplayBlockEntity old && worldPosition.equals(old.console()))
            old.linkTo(null);
        display = board.immutable();
        target.linkTo(worldPosition);
        topologyDirty = true;
        sentFingerprint = 0;
        setChanged();
        if (!level.isClientSide)
            refresh();
        return true;
    }
    /** A formed board within range that names this desk back; checked when the topology may have changed. */
    private boolean displayLinked() {
        return display != null && level.hasChunkAt(display) && level.getBlockEntity(display) instanceof ReactorConsoleDisplayBlockEntity board
            && board.isMaster() && board.assembled() && worldPosition.equals(board.console());
    }
    public void dismantle() {
        releaseBank();
    }

    public boolean linksCrane(BlockPos pos) { return pos.equals(cranePos); }

    public CraneMode craneModeFor(BlockPos pos) {
        return craneMode != CraneMode.STOPPED && linksCrane(pos) && assembled && stationComplete() ? craneMode : CraneMode.STOPPED;
    }

    private CraneDriveBlockEntity linkedCrane() {
        return cranePos != null && level != null && level.hasChunkAt(cranePos)
            && level.getBlockEntity(cranePos) instanceof CraneDriveBlockEntity crane && worldPosition.equals(crane.console()) ? crane : null;
    }

    public void linkCrane(Player player, BlockPos pos) {
        if (!stationComplete() || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CraneDriveBlockEntity crane)) {
            player.displayClientMessage(Component.translatable(KEY + "crane.unavailable"), true);
            return;
        }
        if (!crane.bindConsole(worldPosition)) {
            player.displayClientMessage(Component.translatable(KEY + "crane.in_use"), true);
            return;
        }
        var previous = linkedCrane();
        if (previous != null && previous != crane) previous.unbindConsole(worldPosition);
        cranePos = pos.immutable();
        craneMode = CraneMode.STOPPED;
        refresh();
        player.displayClientMessage(Component.translatable(KEY + "crane.connected", pos.getX(), pos.getY(), pos.getZ()), true);
    }

    private void pressCrane(Player player, ReactorConsoleButton button, boolean positive) {
        var crane = linkedCrane();
        if (button == ReactorConsoleButton.CRANE_MODE && player.isShiftKeyDown() && cranePos != null) {
            if (crane != null) crane.unbindConsole(worldPosition);
            cranePos = null;
            craneMode = CraneMode.STOPPED;
            player.displayClientMessage(Component.translatable(KEY + "crane.disconnected"), true);
        } else if (crane == null) {
            player.displayClientMessage(Component.translatable(KEY + (cranePos == null ? "crane.no_link" : "crane.unavailable")), true);
        } else if (button == ReactorConsoleButton.CRANE_MODE) {
            craneMode = switch (craneMode) {
                case STOPPED -> CraneMode.MANUAL;
                case MANUAL -> CraneMode.AUTOMATIC;
                case AUTOMATIC -> CraneMode.STOPPED;
            };
            crane.clearJog();
            player.displayClientMessage(Component.translatable(KEY + "crane.mode_changed", craneModeLabel()), true);
        } else if (craneMode == CraneMode.MANUAL) {
            String reason = crane.jog(button == ReactorConsoleButton.CRANE_ALONG, positive ? 1 : -1);
            if (reason != null) player.displayClientMessage(Component.translatable("crane.create_refinery_n_reactors.status." + reason), true);
        } else {
            player.displayClientMessage(Component.translatable(KEY + "crane.manual_required"), true);
        }
    }

    public Component craneModeLabel() {
        return Component.translatable(KEY + "crane." + (cranePos == null ? "no_link_short" : "mode." + craneMode.ordinal()));
    }
    private ReactorCore previousCore() {
        return controller != null && level != null && level.hasChunkAt(controller)
                && level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead
            ? lead.core()
            : null;
    }
    private void releaseBank() {
        var core = previousCore();
        if (core != null)
            core.clearAutomaticTarget(worldPosition);
    }

    @Override public void tick() {
        if (!isMaster())
            return;
        super.tick();
    }

    @Override public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide() && isMaster())
            refresh();
    }

    private void refresh() {
        if (!isMaster())
            return;
        if (topologyDirty || !ReactorConsoleStructure.DESK.loaded(level, worldPosition, facing())) {
            assembled = stationComplete();
            topologyDirty = !assembled;
        }
        walled = assembled && displayLinked();
        var crane = linkedCrane();
        craneStatus = crane == null ? "unloaded" : crane.status();
        ReactorCore previous = previousCore();
        if (!assembled) linkStatus = "incomplete";
        ReactorCore core = assembled ? linkedCore() : null;
        linked = core != null;
        if (previous != null && previous != core)
            previous.clearAutomaticTarget(worldPosition);
        if (core == null) {
            reactorStatus = assembled ? "unlinked" : "incomplete";
            scram = false;
            power = reactivity = voidFraction = rodInsertion = 0;
            totalFlow = xenon = nominalWatts = 0;
            bundles = 0;
            surfaceKelvin = 273.15;
            controlTarget = -1;
            alarms = 0;
            hottestKelvin = coolantKelvin = 273.15;
            graphiteKelvin = representativeOutletKelvin = 273.15;
            rodMap = fuelMap = new int[0];
            selectedIndex = -1;
        } else {
            sample(core);
            updateBank(core);
            updateAlarms();
            if (level.getGameTime() - lastRecordTick >= 20) {
                record();
                lastRecordTick = level.getGameTime();
            }
        }
        int fingerprint = Objects.hash(assembled, walled, display, linked, scram, reactorStatus, linkStatus, selectedBottom, selectedSlot,
            quantize(power, 10), quantize(reactivity, 100000), quantize(hottestKelvin, 10), quantize(coolantKelvin, 10),
            quantize(graphiteKelvin, 10), quantize(representativeOutletKelvin, 10), quantize(voidFraction, 1000),
            quantize(rodInsertion, 1000), selectedIndex, Arrays.hashCode(rodMap), Arrays.hashCode(fuelMap), bundles, mode,
            bankTarget, controlTarget, alarms, acknowledged, guardOpen, chartCount, chartCursor, quantize(totalFlow, 10),
            quantize(xenon, 1000), quantize(surfaceKelvin, 10), quantize(nominalWatts, 1), cranePos, craneMode, craneStatus);
        if (fingerprint != sentFingerprint) {
            sentFingerprint = fingerprint;
            setChanged();
            sendData();
        }
    }

    private ReactorCore linkedCore() {
        Set<BlockPos> controllers = new LinkedHashSet<>();
        boolean endpoint = false, changed = false;
        var links = displayLinks.iterator();
        while (links.hasNext()) {
            BlockPos pos = links.next();
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof DisplayLinkBlockEntity link)
                || !targetedBy(link.getTargetPosition())) {
                links.remove();
                changed = true;
                continue;
            }
            endpoint = true;
            if (link.activeSource != CEDisplays.MACHINE.get() || link.activeTarget != CEDisplays.REACTOR_CONSOLE.get()) continue;
            BlockPos source = link.getSourcePosition();
            if (!level.hasChunkAt(source)) continue;
            FuelChannelBlockEntity lead = MachineReadings.reactorController(level.getBlockEntity(source));
            if (lead != null && lead.core() != null && lead.core().formed()) controllers.add(lead.getBlockPos());
        }
        if (changed) setChanged();
        if (!endpoint) linkStatus = "missing";
        else if (controllers.isEmpty()) linkStatus = "unlinked";
        else if (controllers.size() > 1) linkStatus = "ambiguous";
        else {
            controller = controllers.iterator().next();
            if (level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead
                && lead.core() != null && lead.core().formed()) {
                linkStatus = "linked";
                return lead.core();
            }
            linkStatus = "unlinked";
        }
        controller = null;
        return null;
    }

    /** The formed reactor selected by Create Display Links, for control-room components directly touching this multiblock. */
    public ReactorCore linkedReactor() {
        return level != null && !level.isClientSide() && stationComplete() ? linkedCore() : null;
    }

    private void sample(ReactorCore core) {
        reactorStatus = core.status();
        scram = core.scram();
        power = core.power();
        reactivity = core.reactivity();
        hottestKelvin = core.hottestKelvin();
        coolantKelvin = core.averageCoolantKelvin();
        graphiteKelvin = core.graphiteKelvin();
        xenon = core.xenon();
        surfaceKelvin = core.hottestSurfaceKelvin();
        nominalWatts = core.nominalWatts();
        voidFraction = core.averageVoid();
        rodInsertion = core.rodInsertion();
        int count = core.controlRodCount();
        rodMap = new int[count];
        selectedIndex = -1;
        var box = core.box();
        double width = Math.max(1, box.getXSpan()), depth = Math.max(1, box.getZSpan());
        for (int i = 0; i < count; i++) {
            BlockPos bottom = core.controlBottom(i);
            int slot = core.controlSlot(i);
            float[] hole = FuelChannelBlockEntity.HOLES[slot];
            int u = (int) Math.round(Math.clamp((bottom.getX() + .5 + hole[0] - box.minX()) / width, 0, 1) * 255);
            int v = (int) Math.round(Math.clamp(1 - (bottom.getZ() + .5 + hole[1] - box.minZ()) / depth, 0, 1) * 255);
            int insertion = (int) Math.round(Math.clamp(core.controlInsertion(i), 0, 1) * 100);
            int target = core.individualTarget(bottom, slot);
            rodMap[i] = u | v << 8 | insertion << 16 | (target < 0 ? 255 : target) << 24;
            if (bottom.equals(selectedBottom) && slot == selectedSlot) selectedIndex = i;
        }
        var fuels = new java.util.ArrayList<Integer>();
        representativeOutletKelvin = 273.15;
        totalFlow = 0;
        bundles = 0;
        for (BlockPos bottom : core.fuelBottoms()) {
            if (!(level.getBlockEntity(bottom) instanceof FuelChannelBlockEntity channel)) continue;
            int lowFlow = power > 1e6 && channel.flowRate() < LOW_FLOW ? LOW_FLOW_BIT : 0;
            boolean loaded = false;
            for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) if (channel.loaded(slot)) {
                loaded = true;
                int[] point = finePoint(core, bottom, slot);
                int temperature = temperatureByte(channel.fuelKelvin());
                fuels.add(point[0] | point[1] << 8 | temperature << 16 | lowFlow);
            }
            if (loaded) {
                bundles++;
                totalFlow += channel.flowRate();
                representativeOutletKelvin = Math.max(representativeOutletKelvin, channel.coolantKelvin());
            }
        }
        fuelMap = fuels.stream().mapToInt(Integer::intValue).toArray();
        if (selectedIndex < 0) {
            selectedBottom = null;
            selectedSlot = -1;
        }
    }

    private static int[] finePoint(ReactorCore core, BlockPos bottom, int slot) {
        float[] hole = FuelChannelBlockEntity.HOLES[slot];
        return point(core, bottom.getX() + .5 + hole[0], bottom.getZ() + .5 + hole[1]);
    }

    private static int[] point(ReactorCore core, double x, double z) {
        var box = core.box();
        double width = Math.max(1, box.getXSpan()), depth = Math.max(1, box.getZSpan());
        return new int[] {(int) Math.round(Math.clamp((x - box.minX()) / width, 0, 1) * 255),
            (int) Math.round(Math.clamp(1 - (z - box.minZ()) / depth, 0, 1) * 255)};
    }

    private static int temperatureByte(double kelvin) {
        return (int) Math.round(Math.clamp((kelvin - 273.15) / (ReactorPhysics.CHANNEL_RUPTURE_KELVIN - 273.15), 0, 1) * 255);
    }

    public void selectRod(Player player, double u, double v) {
        boolean complete = stationComplete() && displayLinked();
        ReactorCore core = complete ? linkedCore() : null;
        if (core == null || core.controlRodCount() == 0) {
            player.displayClientMessage(Component.translatable(KEY + (complete ? connectionFailure() : "incomplete")), true);
            return;
        }
        var box = core.box();
        double width = Math.max(1, box.getXSpan()), depth = Math.max(1, box.getZSpan()), best = Double.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < core.controlRodCount(); i++) {
            BlockPos bottom = core.controlBottom(i);
            float[] hole = FuelChannelBlockEntity.HOLES[core.controlSlot(i)];
            double ru = Math.clamp((bottom.getX() + .5 + hole[0] - box.minX()) / width, 0, 1);
            double rv = Math.clamp(1 - (bottom.getZ() + .5 + hole[1] - box.minZ()) / depth, 0, 1);
            double distance = (ru - u) * (ru - u) + (rv - v) * (rv - v);
            if (distance < best) { best = distance; bestIndex = i; }
        }
        if (best > .0016) {
            player.displayClientMessage(Component.translatable(KEY + "button.select_indicator"), true);
            return;
        }
        selectedBottom = core.controlBottom(bestIndex);
        selectedSlot = core.controlSlot(bestIndex);
        int target = core.individualTarget(selectedBottom, selectedSlot);
        if (target < 0) target = Math.round(core.controlInsertion(bestIndex) * 100);
        setChanged();
        sample(core);
        sendData();
        player.displayClientMessage(Component.translatable(KEY + "selected", selectedBottom.getX(), selectedBottom.getZ(), selectedSlot + 1, target), true);
    }

    public void releaseSelectedRod(Player player) {
        boolean complete = stationComplete();
        ReactorCore core = complete ? linkedCore() : null;
        if (core == null) player.displayClientMessage(Component.translatable(KEY + (complete ? connectionFailure() : "incomplete")), true);
        else if (selectedBottom == null || !core.clearIndividualTarget(selectedBottom, selectedSlot))
            player.displayClientMessage(Component.translatable(KEY + "no_selection"), true);
        else {
            player.displayClientMessage(Component.translatable(KEY + "released", selectedBottom.getX(), selectedBottom.getZ(), selectedSlot + 1), true);
            refresh();
        }
    }

    public void pressAz5(Player player, boolean reset) {
        boolean complete = stationComplete();
        ReactorCore core = complete ? linkedCore() : null;
        if (core == null) {
            player.displayClientMessage(Component.translatable(KEY + (complete ? connectionFailure() : "incomplete")), true);
            return;
        }
        core.setConsoleScram(!reset);
        player.displayClientMessage(Component.translatable(KEY + (reset ? "az5_reset" : "az5_pressed")), true);
        refresh();
    }

    /**
     * One physical control. {@code up} is the operator's right half of a dial; sneaking turns a dial in 1% steps, releases every rod
     * from the LOCAL button and runs the lamp test from ACK.
     */
    public void press(Player player, ReactorConsoleButton button, boolean up) {
        if (!stationComplete()) {
            player.displayClientMessage(Component.translatable(KEY + "incomplete"), true);
            return;
        }
        boolean fine = player.isShiftKeyDown();
        lastButton = button.ordinal();
        buttonTick = level.getGameTime();
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
            net.minecraft.sounds.SoundSource.BLOCKS, .35f, .8f);
        switch (button) {
            case CRANE_MODE, CRANE_ALONG, CRANE_ACROSS -> pressCrane(player, button, up);
            case AZ5 -> {
                if (guardOpen)
                    pressAz5(player, false);
                else {
                    guardOpen = true;
                    player.displayClientMessage(Component.translatable(KEY + "button.guard_open"), true);
                }
            }
            case AZ5_RESET -> {
                guardOpen = false;
                pressAz5(player, true);
            }
            case ACKNOWLEDGE -> {
                if (fine)
                    lampTestUntil = level.getGameTime() + 40;
                else
                    acknowledged = true;
            }
            case CHART -> {
                java.util.Arrays.fill(waterTrace, (byte) 0);
                java.util.Arrays.fill(graphiteTrace, (byte) 0);
                chartCount = chartCursor = 0;
            }
            default -> {
                var core = linkedReactor();
                if (core == null) {
                    player.displayClientMessage(Component.translatable(KEY + connectionFailure()), true);
                    return;
                }
                int step = (fine ? 1 : 5) * (up ? 1 : -1);
                switch (button) {
                    case PREVIOUS_ROD, NEXT_ROD -> stepSelection(player, core, button == ReactorConsoleButton.NEXT_ROD ? 1 : -1);
                    case RELEASE_ROD -> {
                        if (fine)
                            for (int i = 0; i < core.controlRodCount(); i++) core.clearIndividualTarget(core.controlBottom(i), core.controlSlot(i));
                        else
                            releaseSelectedRod(player);
                    }
                    case MODE -> {
                        releaseBank();
                        mode = (mode + 1) % 3;
                        bankTarget = Math.clamp((int) Math.round(core.rodInsertion() * 100), 0, 100);
                        controlTarget = bankTarget;
                        updateBank(core);
                    }
                    case BANK_DIAL -> {
                        if (mode != 1) {
                            player.displayClientMessage(Component.translatable(KEY + "button.manual_required"), true);
                            break;
                        }
                        bankTarget = Math.clamp(bankTarget + step, 0, 100);
                        updateBank(core);
                    }
                    default -> {
                        if (selectedBottom == null || !validSelection(core)) {
                            player.displayClientMessage(Component.translatable(KEY + "no_selection"), true);
                            break;
                        }
                        int now = core.individualTarget(selectedBottom, selectedSlot);
                        if (now < 0)
                            now = selectedDepth() < 0 ? 100 : selectedDepth();
                        core.setIndividualTarget(selectedBottom, selectedSlot, Math.clamp(now + step, 0, 100));
                    }
                }
                sample(core);
            }
        }
        setChanged();
        sendData();
    }
    private void stepSelection(Player player, ReactorCore core, int step) {
        if (core.controlRodCount() == 0)
            return;
        int index = selectedIndex < 0 ? 0 : Math.floorMod(selectedIndex + step, core.controlRodCount());
        selectedBottom = core.controlBottom(index);
        selectedSlot = core.controlSlot(index);
        sample(core);
        player.displayClientMessage(
            Component.translatable(KEY + "selected", selectedBottom.getX(), selectedBottom.getZ(), selectedSlot + 1, selectedDepth()),
            true);
    }
    private void updateBank(ReactorCore core) {
        if (mode == 0) {
            core.clearAutomaticTarget(worldPosition);
            controlTarget = -1;
            return;
        }
        if (mode == 1)
            controlTarget = bankTarget;
        else {
            if (controlTarget < 0)
                controlTarget = Math.clamp((int) Math.round(core.rodInsertion() * 100), 0, 100);
            if (level.getGameTime() - lastControlTick >= 10) {
                controlTarget = ReactorAutomaticControl.nextTarget(controlTarget, core.hottestKelvin(), representativeOutletKelvin,
                    totalFlow / Math.max(1, bundles), core.power(), core.nominalWatts(), core.reactivity(), core.scram());
                lastControlTick = level.getGameTime();
            }
        }
        if (!core.setAutomaticTarget(worldPosition, controlTarget))
            controlTarget = -1;
    }
    private void updateAlarms() {
        int next = (scram ? 1 : 0) | (hottestKelvin >= ReactorPhysics.ALARM_KELVIN ? 2 : 0) | (representativeOutletKelvin >= 575 ? 4 : 0)
            | (power > 1e6 && totalFlow / Math.max(1, bundles) < LOW_FLOW ? 8 : 0) | (mode != 0 && controlTarget < 0 ? 16 : 0);
        if ((next & ~alarms) != 0)
            acknowledged = false;
        alarms = next;
    }
    private void record() {
        waterTrace[chartCursor] = trace(representativeOutletKelvin);
        graphiteTrace[chartCursor] = trace(graphiteKelvin);
        chartCursor = (chartCursor + 1) % SAMPLES;
        chartCount = Math.min(SAMPLES, chartCount + 1);
    }
    private static byte trace(double kelvin) {
        return (byte) Math.round(Math.clamp((kelvin - 273.15) / 1000, 0, 1) * 255);
    }
    public int mode() {
        return mode;
    }
    public int bankTarget() {
        return bankTarget;
    }
    public int controlTarget() {
        return (int) Math.round(controlTarget);
    }
    public int alarms() {
        return alarms;
    }
    public boolean acknowledged() {
        return acknowledged;
    }
    public boolean guardOpen() {
        return guardOpen;
    }
    public boolean lampTest() {
        return level != null && level.getGameTime() < lampTestUntil;
    }
    public boolean pressed(ReactorConsoleButton button) {
        return lastButton == button.ordinal() && level != null && level.getGameTime() - buttonTick < 8;
    }
    public double totalFlow() {
        return totalFlow;
    }
    public double xenon() {
        return xenon;
    }
    public double surfaceKelvin() {
        return surfaceKelvin;
    }
    public double nominalWatts() {
        return nominalWatts;
    }
    public int chartCount() {
        return chartCount;
    }
    public int waterSample(int i) {
        return Byte.toUnsignedInt(waterTrace[Math.floorMod(chartCursor - chartCount + i, SAMPLES)]);
    }
    public int graphiteSample(int i) {
        return Byte.toUnsignedInt(graphiteTrace[Math.floorMod(chartCursor - chartCount + i, SAMPLES)]);
    }

    private String connectionFailure() {
        return switch (linkStatus) {
            case "missing" -> "no_display_link";
            case "ambiguous" -> "ambiguous_link";
            default -> "no_core";
        };
    }

    private boolean validSelection(ReactorCore core) {
        for (int i = 0; i < core.controlRodCount(); i++)
            if (selectedBottom.equals(core.controlBottom(i)) && selectedSlot == core.controlSlot(i)) return true;
        return false;
    }

    private static int quantize(double value, double scale) { return Double.isFinite(value) ? (int) Math.round(value * scale) : 0; }

    @Override public boolean assembled() { return assembled; }
    public boolean walled() { return walled; }
    public boolean linked() { return linked; }
    public boolean scram() { return scram; }
    public String reactorStatus() { return reactorStatus; }
    public String linkStatus() { return linkStatus; }
    public double power() { return power; }
    public double reactivity() { return reactivity; }
    public double hottestKelvin() { return hottestKelvin; }
    public double coolantKelvin() { return coolantKelvin; }
    public double graphiteKelvin() { return graphiteKelvin; }
    public double representativeOutletKelvin() { return representativeOutletKelvin; }
    public double voidFraction() { return voidFraction; }
    public double rodInsertion() { return rodInsertion; }
    public int[] rodMap() { return rodMap; }
    public int[] fuelMap() { return fuelMap; }
    public int bundles() { return bundles; }
    public int selectedIndex() { return selectedIndex; }
    public int selectedDepth() { return selectedIndex < 0 ? -1 : rodMap[selectedIndex] >>> 16 & 255; }
    public int selectedTarget() { return selectedIndex < 0 ? -1 : rodMap[selectedIndex] >>> 24 & 255; }

    /** Ponder-only link to a display board, without the server-side range and formation checks. */
    public void previewDisplay(BlockPos board) {
        display = board;
    }

    public void previewCrane(BlockPos crane, CraneMode mode) {
        cranePos = crane;
        craneMode = mode;
    }

    /** Ponder-only moving panel values; never called by ordinary gameplay. */
    public void preview() {
        assembled = walled = linked = true;
        linkStatus = "linked";
        reactorStatus = "critical";
        power = 612e6;
        nominalWatts = 765e6;
        totalFlow = 176000;
        surfaceKelvin = 795;
        xenon = .96;
        reactivity = .001;
        hottestKelvin = 910;
        coolantKelvin = representativeOutletKelvin = 548;
        graphiteKelvin = 695; voidFraction = .34; rodInsertion = .46;
        rodMap = new int[] {pack(40, 45, 20, 20), pack(88, 62, 45, 45), pack(132, 42, 72, 72), pack(184, 70, 30, 30),
            pack(62, 125, 85, 85), pack(112, 118, 52, 52), pack(164, 132, 12, 12), pack(210, 118, 66, 66), pack(88, 190, 38, 38), pack(168, 194, 92, 92)};
        var fuels = new java.util.ArrayList<Integer>();
        bundles = 0;
        for (int z = 0; z < 13; z++) for (int x = 0; x < 13; x++) {
            double dx = x - 6, dz = z - 6;
            if (dx * dx + dz * dz > 38) continue;
            int u = 26 + x * 17, v = 26 + z * 17;
            int temperature = Math.clamp(54 + (int) (85 * (1 - Math.sqrt(dx * dx + dz * dz) / 7)), 0, 255);
            fuels.add(u | v << 8 | temperature << 16 | (x == 9 && z == 4 ? LOW_FLOW_BIT : 0));
            bundles++;
        }
        fuelMap = fuels.stream().mapToInt(Integer::intValue).toArray();
        selectedIndex = 5;
        chartCount = SAMPLES;
        chartCursor = 0;
        for (int i = 0; i < SAMPLES; i++) {
            waterTrace[i] = trace(530 + i * .4);
            graphiteTrace[i] = trace(670 + i * .7);
        }
    }
    public int selectedCommand() {
        int target=selectedTarget();
        return target==255 ? controlTarget() : target;
    }


    private static int pack(int u, int v, int depth, int target) { return u | v << 8 | depth << 16 | target << 24; }

    @Override protected AABB createRenderBoundingBox() {
        if (!isMaster())
            return new AABB(worldPosition);
        return AABB.encapsulatingFullBlocks(worldPosition, deskPos(DESK_WIDTH - 1)).inflate(.25);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putInt("ControlMode", mode);
        tag.putInt("BankTarget", bankTarget);
        tag.putDouble("ControlTarget", controlTarget);
        tag.putBoolean("GuardOpen", guardOpen);
        tag.putBoolean("Acknowledged", acknowledged);
        tag.putInt("Alarms", alarms);
        tag.putByteArray("WaterTrace", waterTrace);
        tag.putByteArray("GraphiteTrace", graphiteTrace);
        tag.putInt("ChartCount", chartCount);
        tag.putInt("ChartCursor", chartCursor);
        if (packet) {
            tag.putDouble("TotalFlow", totalFlow);
            tag.putDouble("Xenon", xenon);
            tag.putDouble("SurfaceKelvin", surfaceKelvin);
            tag.putDouble("NominalWatts", nominalWatts);
            tag.putInt("Bundles", bundles);
            tag.putLong("LampTestUntil", lampTestUntil);
            tag.putLong("ButtonTick", buttonTick);
            tag.putInt("LastButton", lastButton);
        }
        if (controller != null) tag.putLong("Controller", controller.asLong());
        if (display != null) tag.putLong("Display", display.asLong());
        if (cranePos != null) tag.putLong("Crane", cranePos.asLong());
        if (!packet && remembered != null) tag.putLong("Remembered", remembered.asLong());
        if (selectedBottom != null) tag.putLong("SelectedBottom", selectedBottom.asLong());
        tag.putLongArray("DisplayLinks", displayLinks.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("SelectedSlot", selectedSlot);
        if (!packet) return;
        tag.putBoolean("Assembled", assembled);
        tag.putInt("CraneMode", craneMode.ordinal());
        tag.putString("CraneStatus", craneStatus);
        tag.putBoolean("Walled", walled);
        tag.putBoolean("Linked", linked);
        tag.putBoolean("Scram", scram);
        tag.putString("ReactorStatus", reactorStatus);
        tag.putString("LinkStatus", linkStatus);
        tag.putDouble("Power", power);
        tag.putDouble("Reactivity", reactivity);
        tag.putDouble("Hottest", hottestKelvin);
        tag.putDouble("Coolant", coolantKelvin);
        tag.putDouble("Graphite", graphiteKelvin);
        tag.putDouble("Outlet", representativeOutletKelvin);
        tag.putDouble("Void", voidFraction);
        tag.putDouble("Rods", rodInsertion);
        tag.putIntArray("RodMap", rodMap);
        tag.putIntArray("FuelMap", fuelMap);
        tag.putInt("SelectedIndex", selectedIndex);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        topologyDirty = true;
        mode = Math.clamp(tag.getInt("ControlMode"), 0, 2);
        bankTarget = tag.contains("BankTarget") ? Math.clamp(tag.getInt("BankTarget"), 0, 100) : 100;
        controlTarget = tag.contains("ControlTarget") ? Math.clamp(tag.getDouble("ControlTarget"), -1, 100) : -1;
        guardOpen = tag.getBoolean("GuardOpen");
        acknowledged = tag.getBoolean("Acknowledged");
        alarms = tag.getInt("Alarms") & 31;
        waterTrace = Arrays.copyOf(tag.getByteArray("WaterTrace"), SAMPLES);
        graphiteTrace = Arrays.copyOf(tag.getByteArray("GraphiteTrace"), SAMPLES);
        chartCount = Math.clamp(tag.getInt("ChartCount"), 0, SAMPLES);
        chartCursor = Math.floorMod(tag.getInt("ChartCursor"), SAMPLES);
        if (packet) {
            totalFlow = Finite.of(tag.getDouble("TotalFlow"), 0, 0, Double.MAX_VALUE);
            xenon = Finite.of(tag.getDouble("Xenon"), 0, 0, 10);
            surfaceKelvin = Finite.of(tag.getDouble("SurfaceKelvin"), 273.15, 1, 10000);
            nominalWatts = Finite.of(tag.getDouble("NominalWatts"), 0, 0, Double.MAX_VALUE);
            bundles = Math.max(0, tag.getInt("Bundles"));
            lampTestUntil = tag.getLong("LampTestUntil");
            buttonTick = tag.getLong("ButtonTick");
            lastButton = tag.getInt("LastButton");
        }
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        display = tag.contains("Display") ? BlockPos.of(tag.getLong("Display")) : null;
        cranePos = tag.contains("Crane") ? BlockPos.of(tag.getLong("Crane")) : null;
        craneMode = packet ? CraneMode.values()[Math.clamp(tag.getInt("CraneMode"), 0, CraneMode.values().length - 1)] : CraneMode.STOPPED;
        if (!packet) remembered = tag.contains("Remembered") ? BlockPos.of(tag.getLong("Remembered")) : null;
        selectedBottom = tag.contains("SelectedBottom") ? BlockPos.of(tag.getLong("SelectedBottom")) : null;
        displayLinks.clear();
        long[] links = tag.getLongArray("DisplayLinks");
        for (int i = 0; i < Math.min(MAX_DISPLAY_LINKS, links.length); i++) displayLinks.add(BlockPos.of(links[i]));
        selectedSlot = tag.getInt("SelectedSlot");
        if (!packet) return;
        assembled = tag.getBoolean("Assembled");
        craneStatus = tag.contains("CraneStatus") ? tag.getString("CraneStatus") : "unloaded";
        walled = tag.getBoolean("Walled");
        linked = tag.getBoolean("Linked");
        scram = tag.getBoolean("Scram");
        reactorStatus = tag.getString("ReactorStatus");
        linkStatus = tag.contains("LinkStatus") ? tag.getString("LinkStatus") : "missing";
        power = Finite.of(tag.getDouble("Power"), 0, 0, Double.MAX_VALUE);
        reactivity = Finite.of(tag.getDouble("Reactivity"), 0, -1, 1);
        hottestKelvin = Finite.of(tag.getDouble("Hottest"), 273.15, 1, 10000);
        coolantKelvin = Finite.of(tag.getDouble("Coolant"), 273.15, 1, 10000);
        graphiteKelvin = tag.contains("Graphite") ? Finite.of(tag.getDouble("Graphite"), 273.15, 1, 10000) : 273.15;
        representativeOutletKelvin = tag.contains("Outlet") ? Finite.of(tag.getDouble("Outlet"), coolantKelvin, 1, 10000) : coolantKelvin;
        voidFraction = Finite.of(tag.getDouble("Void"), 0, 0, 1);
        rodInsertion = Finite.of(tag.getDouble("Rods"), 0, 0, 1);
        rodMap = tag.getIntArray("RodMap");
        fuelMap = tag.getIntArray("FuelMap");
        selectedIndex = Math.clamp(tag.getInt("SelectedIndex"), -1, rodMap.length - 1);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var master = owner();
        if (master != null && master != this)
            return master.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(linked ? Component.translatable(KEY + "status.linked", Component.translatable("reactor_core.create_refinery_n_reactors.status." + reactorStatus))
            : Component.translatable(KEY + "status." + (assembled ? linkStatus : "incomplete")));
        if (sneaking && cranePos != null) {
            tooltip.add(Component.translatable(KEY + "crane.goggle", craneModeLabel(),
                Component.translatable("crane.create_refinery_n_reactors.status." + craneStatus)));
            return true;
        }
        if (selectedBottom == null || selectedIndex < 0) tooltip.add(Component.translatable(KEY + "goggle.no_selection"));
        else tooltip.add(Component.translatable(KEY + "goggle.selection", selectedBottom.getX(), selectedBottom.getZ(), selectedSlot + 1));
        int target = selectedTarget();
        tooltip.add(Component.translatable(KEY + "goggle.rod", selectedDepth() < 0 ? "--" : selectedDepth(),
            target == 255 && controlTarget>=0 ? Component.translatable(KEY+"bank_control",controlTarget()) : target < 0 || target == 255 ? Component.translatable(KEY + "cap_control") : target));
        tooltip.add(Component.translatable(KEY + (scram ? "goggle.az5_active" : "goggle.az5_ready")));
        return true;
    }
}
