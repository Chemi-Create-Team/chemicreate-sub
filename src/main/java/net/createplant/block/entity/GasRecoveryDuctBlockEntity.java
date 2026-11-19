package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.GasRecoveryDuctBlock;
import net.createplant.registry.CESafety;
import net.createplant.cloud.GasClouds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Takes a finite charge of residue into an outlet-only Create fluid connection. */
public class GasRecoveryDuctBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int CAPACITY = 4000;
    private static final int SAMPLE_TICKS = 10;
    private static final int SAMPLE_LIMIT = 250;
    private static final int MIN_SPEED = 32;
    private String status = "unpowered";
    private boolean syncPending;

    private final FluidTank buffer = new FluidTank(CAPACITY, stack -> GasClouds.supported(stack) || stack.is(CESafety.RAW_PROCESS_GAS.get())) {
        @Override protected void onContentsChanged() {
            setChanged();
            syncPending = true;
        }
    };
    private final IFluidHandler outlet = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return isRemoved() ? FluidStack.EMPTY : buffer.getFluidInTank(tank).copy(); }
        @Override public int getTankCapacity(int tank) { return buffer.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return isRemoved() ? FluidStack.EMPTY : buffer.drain(stack, action);
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return isRemoved() ? FluidStack.EMPTY : buffer.drain(amount, action);
        }
    };

    public GasRecoveryDuctBlockEntity(BlockPos pos, BlockState state) {
        super(CESafety.GAS_RECOVERY_DUCT_ENTITY.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public IFluidHandler port(Direction side) {
        return side == getBlockState().getValue(GasRecoveryDuctBlock.OUTLET) ? outlet : null;
    }

    @Override public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || server.getGameTime() % SAMPLE_TICKS != 0) return;
        ProductionMonitor.plan(this, SAMPLE_LIMIT * 20d / SAMPLE_TICKS, 0, 0);
        String step = sample(server);
        if (!step.equals("collecting")) ProductionMonitor.limitedBy(this, switch (step) {
            case "unpowered" -> "fan";
            case "blocked" -> "intake";
            case "full" -> "output";
            case "ready" -> "feed";
            default -> step;
        });
        if (!status.equals(step)) {
            status = step;
            syncPending = true;
        }
        boolean active = status.equals("collecting");
        if (getBlockState().getValue(GasRecoveryDuctBlock.ACTIVE) != active)
            server.setBlock(worldPosition, getBlockState().setValue(GasRecoveryDuctBlock.ACTIVE, active), 2);
        if (syncPending) {
            syncPending = false;
            sendData();
        }
    }

    private String sample(ServerLevel level) {
        if (!(level.getBlockEntity(worldPosition.above()) instanceof EncasedFanBlockEntity fan)
            || fan.getAirflowOriginSide() != Direction.DOWN || Math.abs(fan.getSpeed()) < MIN_SPEED) return "unpowered";
        BlockPos intake = worldPosition.relative(getBlockState().getValue(GasRecoveryDuctBlock.OUTLET).getOpposite());
        if (!level.hasChunkAt(intake) || !level.getBlockState(intake).isAir()) return "blocked";
        int space = CAPACITY - buffer.getFluidAmount();
        if (space == 0) return "full";
        var captured = GasClouds.collect(level, intake, buffer.getFluid(), Math.min(space, SAMPLE_LIMIT));
        if (captured.isEmpty()) return "ready";
        int amount = buffer.fill(captured, IFluidHandler.FluidAction.EXECUTE);
        ProductionMonitor.produced(this, amount, 0, 0);
        ProductionMonitor.limitedBy(this, amount >= SAMPLE_LIMIT ? "rated" : space < SAMPLE_LIMIT && amount == space ? "output" : "feed");
        return "collecting";
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.put("Buffer", buffer.writeToNBT(lookup, new CompoundTag()));
        if (packet) tag.putString("Status", status);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        buffer.readFromNBT(lookup, tag.getCompound("Buffer"));
        var saved = buffer.getFluid();
        if (!saved.isEmpty() && !buffer.isFluidValid(saved)) buffer.setFluid(FluidStack.EMPTY);
        if (buffer.getFluidAmount() > CAPACITY) buffer.setFluid(saved.copyWithAmount(CAPACITY));
        if (packet && tag.contains("Status")) status = tag.getString("Status");
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level == null || buffer.isEmpty()) return;
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("gas_recovery_duct.create_refinery_n_reactors.status." + status));
        text.add(Component.translatable("gas_recovery_duct.create_refinery_n_reactors.buffer", buffer.getFluidAmount(), CAPACITY));
        if (!buffer.isEmpty()) text.add(buffer.getFluid().getHoverName());
        return true;
    }
}
