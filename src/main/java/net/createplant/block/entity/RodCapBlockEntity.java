package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.nuclear.ControlRodLinks;
import net.createplant.nuclear.PlugPhysicsSimulator;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

/** The top-floor cap of a four-position channel bundle and the integrated drive command for its control rods. */
public class RodCapBlockEntity extends SmartBlockEntity {
    public static final double HANDLE_TURN = 360;
    private LinkBehaviour link;
    private int signal, remote = -1;
    private double handleAngle;
    private boolean handled, handleTurning;
    private final PlugPhysicsSimulator plugPhysics = new PlugPhysicsSimulator();
    private final double[] plugTemperature = new double[PlugPhysicsSimulator.PLUGS];
    private final double[] plugFlow = new double[PlugPhysicsSimulator.PLUGS];
    private final double[] plugInsertion = new double[PlugPhysicsSimulator.PLUGS];
    private BlockPos plugColumnPos;
    private static final int KNOCK_TICKS = 60;
    private long lastKnock = Long.MIN_VALUE / 2;

    public RodCapBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.TOP_CAP_ENTITY.get(), pos, state);
        setLazyTickRate(10);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Only the frequency is used; the cap takes nothing from Create's redstone link network.
        link = LinkBehaviour.receiver(this, TopFrequencySlot.pair(), strength -> { });
        behaviours.add(link);
    }

    public FuelChannelBlockEntity column() {
        if (level == null) return null;
        for (var layout : List.of(ReactorPhysics.COMPACT_LAYOUT, ReactorPhysics.STANDARD_LAYOUT)) {
            BlockPos top = worldPosition.below(layout.capOffset());
            if (!(level.getBlockEntity(top) instanceof FuelChannelBlockEntity cell) || !cell.top()) continue;
            var bottom = cell.bottom();
            if (bottom == null || top.getY() - bottom.getBlockPos().getY() + 1 != layout.coreHeight()) continue;
            boolean complete = true;
            for (int y = 1; y <= layout.upperStructureHeight(); y++)
                if (!level.getBlockState(top.above(y)).is(CENuclear.FUEL_CHANNEL_TUBE.get())) { complete = false;break; }
            if (complete) return bottom;
        }
        return null;
    }

    /** Cached for the every-frame renderer and every-tick relief calculation; the full tube path is rechecked by the lazy tick. */
    public FuelChannelBlockEntity plugColumn() {
        if (level == null) return null;
        if (plugColumnPos != null && level.getBlockEntity(plugColumnPos) instanceof FuelChannelBlockEntity column && !column.isRemoved()) return column;
        refreshPlugColumn();
        return plugColumnPos != null && level.getBlockEntity(plugColumnPos) instanceof FuelChannelBlockEntity column ? column : null;
    }

    private void refreshPlugColumn() {
        var column = column();
        plugColumnPos = column == null ? null : column.getBlockPos().immutable();
    }

    public IItemHandler itemPort(Direction side) {
        if (side != null && side != Direction.UP) return null;
        var column = column();
        return column == null ? null : column.topItems();
    }

    public boolean toggle(int slot) {
        var column = column();
        return column != null && column.selectChannel(slot, column.control(slot) ? FuelChannelBlockEntity.FUEL : FuelChannelBlockEntity.CONTROL);
    }

    public boolean installControl(int slot) {
        var column = column();
        return column != null && column.installControlRod(slot);
    }

    public boolean installFuel(int slot) { return installFuel(slot, new ItemStack(CENuclear.FUEL_ROD.get())); }

    public boolean installFuel(int slot, ItemStack stack) {
        if (!CENuclear.isFuel(stack) && !stack.is(net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.get())) return false;
        var column = column();
        if (column == null || CENuclear.isFuel(stack) && !net.createplant.nuclear.ReactorFuel.of(stack).acceptsHeight(column.layout().coreHeight())
            || !column.selectChannel(slot, FuelChannelBlockEntity.FUEL)) return false;
        IItemHandler items = column.topItems();
        return items != null && items.insertItem(slot, stack.copyWithCount(1), false).isEmpty();
    }

    public ItemStack removeControl(int slot) {
        var column = column();
        return column == null ? ItemStack.EMPTY : column.removeControlRod(slot);
    }

    /** Requested insertion, from zero withdrawn to one fully inserted. A link overrides a handle, then analogue redstone. */
    public double target() {
        return remote >= 0 ? remote / (double) ControlRodControllerBlockEntity.STEPS : handled ? handleAngle / HANDLE_TURN : signal / 15d;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        var column = plugColumn();
        int activeMask = 0;
        double flow = column == null ? 0 : column.flowRate() / Math.max(1, column.activeChannels());
        for (int i = 0; i < PlugPhysicsSimulator.PLUGS; i++) {
            boolean active = column != null && column.active(i);
            if (active) activeMask |= 1 << i;
            plugTemperature[i] = active ? column.coolantKelvin() : 0;
            plugFlow[i] = active ? flow : 0;
            plugInsertion[i] = !active ? 1 : column.control(i)
                ? column.controlInstalled(i) ? column.controlDepth(i) / column.layout().coreHeight() : 0
                : 0;
        }
        long gameTime = level.getGameTime();
        var step = plugPhysics.tick(worldPosition.getX(), worldPosition.getZ(), gameTime, activeMask,
            plugTemperature, plugFlow, plugInsertion);
        effects(step, gameTime);
        if (step.stateChanged()) setChanged();
        if (plugPhysics.shouldSync(gameTime, worldPosition.asLong())) {
            sendData();
            plugPhysics.markSynced();
        }
    }

    private void effects(PlugPhysicsSimulator.Step step, long gameTime) {
        if (!(level instanceof ServerLevel server)) return;
        if (step.movingMask() != 0 && Math.floorMod(gameTime + worldPosition.asLong(), 3) == 0)
            for (int i = 0; i < PlugPhysicsSimulator.PLUGS; i++) if ((step.movingMask() & 1 << i) != 0) {
                float[] hole = FuelChannelBlockEntity.HOLES[i];
                double lift = plugPhysics.serverOffset(i);
                server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5 + hole[0],
                    worldPosition.getY() + 1.01 + Math.min(.08, lift * .25), worldPosition.getZ() + .5 + hole[1],
                    0, 0, 1, 0, .06 + .05 * lift / PlugPhysicsSimulator.MAX_LIFT_METRES);
            }
        // One knock per cap at a time: a whole core of plugs would otherwise exhaust the client's sound channels.
        if (step.landedMask() != 0 && gameTime - lastKnock >= KNOCK_TICKS) {
            lastKnock = gameTime;
            level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                .8f, .7f + level.random.nextFloat() * .18f);
        }
    }

    public float plugOffset(int slot, float partialTicks) {
        return level == null ? 0 : plugPhysics.renderOffset(worldPosition.getX(), worldPosition.getZ(), slot,
            level.getGameTime() + partialTicks);
    }

    public float plugPressure(int slot) { return plugPhysics.pressure(slot); }

    /** Makes the dangerous motion visible in its Ponder step without mutating a live server reactor. */
    public void previewPlugPressure(float... pressure) {
        if (level == null || level.isClientSide()) plugPhysics.preview(pressure);
    }

    public void neighbourChanged() {
        if (level == null) return;
        int next = level.getBestNeighborSignal(worldPosition);
        if (next == signal) return;
        signal = next;
        setChanged();
        sendData();
    }

    @Override public void lazyTick() {
        super.lazyTick();
        if (level == null) return;
        refreshPlugColumn();
        if (level.isClientSide()) return;
        int next = ControlRodLinks.setting(level, link.getNetworkKey());
        if (next != remote) {
            remote = next;
            setChanged();
            sendData();
        }
        trackHandle();
    }

    private void trackHandle() {
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            if (!(level.getBlockEntity(worldPosition.relative(side)) instanceof ValveHandleBlockEntity handle)
                || handle.getBlockState().getValue(BlockStateProperties.FACING) != side) continue;
            boolean turning = handle.inUse > 0;
            if (turning && !handleTurning) {
                handleAngle = Math.clamp(handleAngle + handle.angleInput.getValue() * (handle.backwards ? -1 : 1), 0, HANDLE_TURN);
                setChanged();
                sendData();
            }
            handleTurning = turning;
            if (!handled) {
                handled = true;
                sendData();
            }
            return;
        }
        if (handled) {
            handled = false;
            sendData();
        }
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Signal", signal);
        tag.putInt("Remote", remote);
        tag.putDouble("HandleAngle", handleAngle);
        tag.putBoolean("Handled", handled);
        plugPhysics.write(tag, clientPacket);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        signal = tag.getInt("Signal");
        remote = tag.contains("Remote") ? tag.getInt("Remote") : -1;
        handleAngle = Math.clamp(tag.getDouble("HandleAngle"), 0, HANDLE_TURN);
        handled = tag.getBoolean("Handled");
        plugPhysics.read(tag, clientPacket);
    }

    @Override protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().expandTowards(0, PlugPhysicsSimulator.MAX_LIFT_METRES + .05, 0);
    }
}
