package net.createplant.relay;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.util.List;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity.Command;
import net.createplant.fusion.FusionPhysics;
import net.createplant.nuclear.ReactorAutomaticControl;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorCores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Relay computer: keeps the nearest formed fission core or fusion reactor (within {@link #RANGE}) at the set output.
 * A redstone signal on the base hands control back to the operator.
 */
public final class RelayComputerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    static final String KEY = "relay_computer.create_refinery_n_reactors.";
    public static final int RANGE = 16, MIN_RACKS = 2, PERIOD = 10;
    public ScrollValueBehaviour target;
    private String status = "unformed";
    private double rodTarget = -1;
    private final RelayAlu alu = new RelayAlu();
    /** Ticks the relays are still busy with the last pass, and the switchings left to sound over them. */
    private int busy;
    private long clicks;

    public RelayComputerBlockEntity(BlockPos pos, BlockState state) { super(CERelayComputer.COMPUTER_ENTITY.get(), pos, state); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        target = new ScrollValueBehaviour(Component.translatable(KEY + "target"), this, new ValueBoxTransform.Sided() {
            @Override protected Vec3 getSouthLocation() { return new Vec3(.5, .5, 15.5 / 16); }
            @Override protected boolean isSideActive(BlockState state, Direction side) { return side == state.getValue(RelayComputerBlock.FACING); }
        });
        target.between(10, 100).withFormatter(v -> v + "%");
        target.setValue(80);
        behaviours.add(target);
    }

    public String status() { return status; }
    public int racks() {
        int n = 0;
        while (n < 8 && level.getBlockState(worldPosition.above(n + 1)).getBlock() instanceof RelayRackBlock) n++;
        return n;
    }

    @Override public void tick() {
        super.tick();
        if (level.isClientSide) return;
        if (busy > 0) {
            busy--;
            sound();
            if (rodTarget >= 0 && status.startsWith("reactor")) renewLease();
            return;
        }
        String next = run();
        // The relays take one contact transfer per step; more racks run more banks side by side.
        long steps = alu.takeSteps();
        clicks += alu.takeFlips();
        busy = (int) Math.max(PERIOD - 1, Math.ceil(steps / (double) stepsPerTick()));
        sound();
        boolean active = next.startsWith("reactor") || next.startsWith("fusion");
        setActive(active);
        if (!next.equals(status)) { status = next; setChanged(); sendData(); }
    }

    private String run() {
        int racks = racks();
        if (racks < MIN_RACKS) { release(); return "unformed"; }
        if (level.hasNeighborSignal(worldPosition)) { release(); return "manual"; }
        BlockPos controller = ReactorCores.nearest(level, worldPosition, RANGE);
        if (controller != null && level.getBlockEntity(controller) instanceof net.createplant.block.entity.FuelChannelBlockEntity lead
            && lead.core() != null && lead.core().formed()) return fission(lead.core());
        release();
        FusionReactorControllerBlockEntity fusion = nearestFusion();
        return fusion == null ? "no_target" : fusion(fusion);
    }

    /** Relay switchings per game tick of a computer with this many racks. */
    public static int stepsPerTick(int racks) { return 8 * Math.min(racks, 8); }
    private int stepsPerTick() { return stepsPerTick(racks()); }

    private String fission(ReactorCore core) {
        if (rodTarget < 0) rodTarget = Math.clamp(Math.round(core.rodInsertion() * 100), 0, 100);
        double flow = 0; var bottoms = core.fuelBottoms();
        for (BlockPos bottom : bottoms) flow += core.columnFlow(bottom);
        flow /= Math.max(1, bottoms.size());
        double setpoint = core.nominalWatts() * target.getValue() / 100d;
        int tenths = RelayPrograms.fission(alu, RelayAlu.input(rodTarget * 10), RelayAlu.input(core.hottestKelvin() / 10),
            RelayAlu.input(core.averageCoolantKelvin() / 2), RelayAlu.input(flow * 4), RelayAlu.input(core.power() / 1e6),
            RelayAlu.input(setpoint > 0 ? core.power() / setpoint * 100 : 0), RelayAlu.input(core.reactivity() * 1e4), core.scram());
        rodTarget = tenths / 10d;
        if (!core.setAutomaticTarget(worldPosition, rodTarget)) { rodTarget = -1; return "busy"; }
        return core.scram() ? "reactor_scram" : "reactor";
    }

    /** One step of the fusion start-up / hold sequence; returns the status key. */
    public static String fusionStep(FusionReactorControllerBlockEntity f, int wanted) {
        return RelayPrograms.fusion(new RelayAlu(), f, wanted);
    }

    private void renewLease() {
        BlockPos controller = ReactorCores.nearest(level, worldPosition, RANGE);
        if (controller != null && level.getBlockEntity(controller) instanceof net.createplant.block.entity.FuelChannelBlockEntity lead && lead.core() != null)
            lead.core().setAutomaticTarget(worldPosition, rodTarget);
    }

    /** Plays this tick's share of the pass's relay switchings. */
    private void sound() {
        if (clicks <= 0) return;
        long now = (long) Math.ceil(clicks / (double) (busy + 1));
        clicks -= now;
        if (level.getGameTime() % 2 != 0 && busy > 0) return;
        float volume = (float) Math.min(.6, .08 + now * .01);
        level.playSound(null, worldPosition.above(), CERelayComputer.CLICK.get(), net.minecraft.sounds.SoundSource.BLOCKS,
            volume, .9f + level.random.nextFloat() * .3f);
    }

    private String fusion(FusionReactorControllerBlockEntity f) { return RelayPrograms.fusion(alu, f, target.getValue()); }

    private FusionReactorControllerBlockEntity nearestFusion() {
        FusionReactorControllerBlockEntity best = null; double bestDistance = (double) RANGE * RANGE * 4;
        for (BlockPos pos : FusionReactorControllerBlockEntity.controllers(level)) {
            double d = pos.distSqr(worldPosition);
            if (d < bestDistance && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity f && f.formed()) {
                best = f; bestDistance = d;
            }
        }
        return best;
    }

    private void release() {
        rodTarget = -1;
        BlockPos controller = ReactorCores.nearest(level, worldPosition, RANGE);
        if (controller != null && level.getBlockEntity(controller) instanceof net.createplant.block.entity.FuelChannelBlockEntity lead && lead.core() != null)
            lead.core().clearAutomaticTarget(worldPosition);
    }

    private void setActive(boolean active) {
        if (getBlockState().getValue(RelayRackBlock.ACTIVE) != active) level.setBlock(worldPosition, getBlockState().setValue(RelayRackBlock.ACTIVE, active), 3);
        for (int i = 1; i <= 8; i++) {
            BlockPos at = worldPosition.above(i);
            BlockState rack = level.getBlockState(at);
            if (!(rack.getBlock() instanceof RelayRackBlock)) break;
            if (rack.getValue(RelayRackBlock.ACTIVE) != active) level.setBlock(at, rack.setValue(RelayRackBlock.ACTIVE, active), 3);
        }
    }

    public void describeTo(Player player) {
        player.displayClientMessage(Component.translatable(KEY + "status." + status).append(" / ").append(Component.translatable(KEY + "target")).append(" " + target.getValue() + "%"), true);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_refinery_n_reactors.relay_computer")));
        tooltip.add(Component.literal("    ").append(Component.translatable(KEY + "status." + status)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("    ").append(Component.translatable(KEY + "target")).append(" " + target.getValue() + "%").withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean client) {
        super.write(tag, registries, client); tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean client) {
        super.read(tag, registries, client); status = tag.getString("Status").isEmpty() ? "unformed" : tag.getString("Status");
    }
}
