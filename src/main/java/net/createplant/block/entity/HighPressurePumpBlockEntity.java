package net.createplant.block.entity;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.HighPressurePumpBlock;
import net.createplant.block.PressurePumpStructure;
import net.createplant.block.PressurePumpStructure.Part;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.transport.HoseSocket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

public final class HighPressurePumpBlockEntity extends PumpBlockEntity implements HoseSocket.Endpoint {
    public static final int RANGE = 128;
    private static final int CAPACITY = 8_000;
    public static final String KEY = "pressure_pump." + CreatePlant.MOD_ID + ".";

    private final FluidTank reservoir = new FluidTank(CAPACITY) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private final IFluidHandler input = new InternalPort(true);
    private final IFluidHandler output = new InternalPort(false);
    private final HoseSocket hose = new HoseSocket(this);
    private int windowOutput, displayedFlow;
    private boolean creativeBreak;
    private boolean formed, lastFormed;
    private long checked = Long.MIN_VALUE;

    public HighPressurePumpBlockEntity(BlockPos pos, BlockState state) {
        super(CEPressureInfrastructure.PUMP_ENTITY.get(), pos, state);
    }

    private Part part() { return ((HighPressurePumpBlock) getBlockState().getBlock()).part(); }
    public boolean drive() { return part() == Part.DRIVE; }

    public boolean formed() {
        if (level == null || isRemoved()) return false;
        long now = level.getGameTime();
        if (now != checked) {
            checked = now;
            formed = PressurePumpStructure.complete(level, part().drive(worldPosition, getFront()), getFront());
        }
        return formed;
    }

    public IFluidHandler internalPort(boolean filling) { return filling ? input : output; }

    @Override public float calculateStressApplied() { return drive() ? 8 : 0; }

    @Override protected void distributePressureTo(Direction side) {
        if ((isVirtual() || formed()) && !hose.connected()) super.distributePressureTo(side);
    }

    @Override public void tick() {
        if (level != null && !level.isClientSide) {
            boolean due = (level.getGameTime() + worldPosition.asLong()) % 20 == 0;
            if (due) {
                hose.validate();
                if (drive()) {
                    int flow = (windowOutput + 10) / 20;
                    windowOutput = 0;
                    if (flow != displayedFlow) {
                        displayedFlow = flow;
                        sendData();
                    }
                }
            }
            var controller = controller();
            boolean ready = formed();
            if (ready != lastFormed) {
                lastFormed = ready;
                updatePressureChange();
            }
            if (!drive()) {
                float next = controller != null && ready ? Math.abs(controller.getSpeed()) : 0;
                float old = getSpeed();
                if (next != old) {
                    setSpeed(next);
                    onSpeedChanged(old);
                    sendData();
                }
            }
            if (hose.connected() && controller != null && ready && controller.getSpeed() != 0) {
                var remote = hose.peer();
                if (remote != null && remote.hoseReady()) {
                    int rate = Math.max(1, Math.round(Math.abs(controller.getSpeed()) * 2));
                    var target = remote.hoseFluid();
                    if (drive()) transfer(controller.output, target, rate);
                    else if (remote.hoseRole() == 0) transfer(target, controller.input, rate);
                }
            }
        }
        super.tick();
    }

    private HighPressurePumpBlockEntity controller() {
        if (drive()) return this;
        var pos = part().drive(worldPosition, getFront());
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof HighPressurePumpBlockEntity pump ? pump : null;
    }

    private void transfer(IFluidHandler from, IFluidHandler to, int limit) {
        if (from == null || to == null || from == to) return;
        var available = from.drain(limit, SIMULATE);
        if (available.isEmpty()) return;
        int admitted = to.fill(available, SIMULATE);
        if (admitted == 0) return;
        var drained = from.drain(available.copyWithAmount(admitted), EXECUTE);
        if (drained.isEmpty()) return;
        int remaining = drained.getAmount() - to.fill(drained, EXECUTE);
        if (remaining > 0) {
            if (from == output) {
                reservoir.fill(drained.copyWithAmount(remaining), EXECUTE);
                windowOutput -= remaining;
            } else from.fill(drained.copyWithAmount(remaining), EXECUTE);
        }
    }

    public boolean creativeBreak() { return creativeBreak; }
    public void setCreativeBreak() { creativeBreak = true; }
    public void disconnectForRemoval() {
        formed = false;
        checked = level.getGameTime();
        hose.disconnect(!creativeBreak);
    }

    public void storeInItem(ItemStack stack) {
        if (!reservoir.isEmpty()) stack.set(CEPortableTools.CONTENTS.get(), SimpleFluidContent.copyOf(reservoir.getFluid()));
    }

    public void loadFromItem(ItemStack stack) {
        var content = stack.getOrDefault(CEPortableTools.CONTENTS.get(), SimpleFluidContent.EMPTY);
        reservoir.setFluid(content.copy());
    }

    @Override public HoseSocket hose() { return hose; }
    @Override public Direction hoseFace() { return drive() ? getFront() : getFront().getOpposite(); }
    @Override public IFluidHandler hoseFluid() {
        var owner = controller();
        return owner == null ? null : owner.internalPort(!drive());
    }
    @Override public boolean hoseReady() { return formed(); }
    @Override public int hoseRole() { return drive() ? 1 : -1; }
    @Override public void hoseChanged() { updatePressureChange(); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        hose.write(tag);
        if (packet) tag.putInt("Flow", displayedFlow);
        if (drive() && !reservoir.isEmpty()) tag.put("Reservoir", reservoir.writeToNBT(lookup, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        hose.read(tag);
        if (packet) displayedFlow = tag.getInt("Flow");
        if (drive()) reservoir.readFromNBT(lookup, tag.getCompound("Reservoir"));
    }

    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        var controller = controller();
        float rpm = controller == null ? 0 : controller.getSpeed();
        lines.add(Component.translatable(KEY + (formed() ? "rpm" : "unformed"), Math.round(rpm)));
        lines.add(Component.translatable(KEY + "stress", Math.round(Math.abs(rpm) * 8)));
        if (controller != null) lines.add(Component.translatable(KEY + "rate", controller.displayedFlow));
        lines.add(hose.connected() ? Component.translatable(KEY + "hose", hose.remote().toShortString())
            : Component.translatable(KEY + "no_hose"));
        return true;
    }

    private final class InternalPort implements IFluidHandler {
        private final boolean filling;
        InternalPort(boolean filling) { this.filling = filling; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return reservoir.getFluidInTank(tank).copy(); }
        @Override public int getTankCapacity(int tank) { return CAPACITY; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && formed() && !stack.isEmpty(); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return filling && formed() ? reservoir.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return !filling && formed() ? record(reservoir.drain(stack, action), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return !filling && formed() ? record(reservoir.drain(amount, action), action) : FluidStack.EMPTY;
        }
        private FluidStack record(FluidStack result, FluidAction action) {
            if (action.execute()) windowOutput += result.getAmount();
            return result;
        }
    }
}
