package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.registry.CEElectrolysis;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Each physical cell retains its fluids through assembly, splitting and item placement; the electrodes hang from the head above. */
public class ElectrolysisCellBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY = 4000;
    private BlockPos controller;
    private boolean dirty;
    public final List<ThermalFluidTank> tanks = java.util.stream.IntStream.range(0, 4).<ThermalFluidTank>mapToObj(index -> new ThermalFluidTank(CAPACITY, fluid -> changed()) {
        @Override protected int storageRatio() { return 1; }
    }).toList();
    public ElectrolysisCellBlockEntity(BlockPos pos, BlockState state) { super(CEElectrolysis.CELL_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    private void changed() { dirty = true;setChanged(); }
    public void bind(BlockPos owner) {
        if (owner.equals(controller)) return;
        controller = owner;
        if (level != null) level.invalidateCapabilities(worldPosition);
        changed();
        sendData();
    }
    public ElectrolysisControllerBlockEntity controller() {
        return controller != null && level != null && level.hasChunkAt(controller)
            && level.getBlockEntity(controller) instanceof ElectrolysisControllerBlockEntity owner && owner.formed() ? owner : null;
    }
    public void invalidateMachine() { var owner = controller();if (owner != null) owner.invalidateMachine(); }

    @Override public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && dirty && level.getGameTime() % 10 == 0) { dirty = false;sendData(); }
    }

    public ItemInteractionResult use(ItemStack stack, Player player, InteractionHand hand, Direction side) {
        if (FluidUtil.getFluidContained(stack).isEmpty() && !stack.is(Items.BUCKET)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return level.isClientSide || FluidUtil.interactWithFluidHandler(player, hand, level, worldPosition, side)
            ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private List<ElectrolysisCellBlockEntity> members() {
        var owner = controller();
        return owner != null && owner.intact() ? owner.cells() : List.of(this);
    }
    /**
     * Feed fills and drains through the bottom; the sides across the row give the cathode gas on the low side and the anode gas on the
     * high side, as the plates alternate inside; the faces along the row take feed and give soda or recovered acid.
     */
    private int channel(Direction side) {
        if (side == null) return -1;
        if (side == Direction.DOWN) return 0;
        if (side == Direction.UP || side.getAxis() == getBlockState().getValue(ElectrolysisCellBlock.AXIS)) return 3;
        return side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 : 2;
    }

    public IFluidHandler port(Direction side) {
        return LiveMachinePorts.fluid(this, new IFluidHandler() {
            private boolean exposed() { return side == null || !getBlockState().getValue(ElectrolysisCellBlock.JOIN.get(side.ordinal())); }
            @Override public int getTanks() { return !exposed() ? 0 : channel(side) < 0 ? 4 : channel(side) == 3 ? 2 : 1; }
            private int index(int tank) { return channel(side) < 0 ? tank : channel(side) == 3 ? tank == 0 ? 0 : 3 : channel(side); }
            @Override public FluidStack getFluidInTank(int tank) {
                if (tank < 0 || tank >= getTanks()) return FluidStack.EMPTY;
                FluidStack total = FluidStack.EMPTY;
                for (var member : members()) {
                    var stored = member.tanks.get(index(tank)).getFluid();
                    if (stored.isEmpty()) continue;
                    if (total.isEmpty()) total = stored.copy();
                    else if (net.createplant.thermal.FluidThermal.compatible(total, stored)) total = net.createplant.thermal.FluidThermal.mix(total, stored, stored.getAmount());
                }
                return total;
            }
            @Override public int getTankCapacity(int tank) { return tank >= 0 && tank < getTanks() ? members().size() * CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack fluid) {
                return tank >= 0 && tank < getTanks() && index(tank) == 0 && ElectrolysisProcess.of(fluid) != null;
            }
            @Override public int fill(FluidStack resource, FluidAction action) {
                int channel = channel(side);
                if (!exposed() || resource.isEmpty() || channel == 1 || channel == 2 || ElectrolysisProcess.of(resource) == null) return 0;
                var cells = members();
                for (var cell : cells) if (!cell.tanks.getFirst().isEmpty() && !cell.tanks.getFirst().getFluid().is(resource.getFluid())) return 0;
                int left = resource.getAmount();
                for (var cell : cells) left -= cell.tanks.getFirst().fill(resource.copyWithAmount(left), action);
                return resource.getAmount() - left;
            }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) {
                return drain(resource, resource.getAmount(), action);
            }
            @Override public FluidStack drain(int maximum, FluidAction action) { return drain(FluidStack.EMPTY, maximum, action); }
            private FluidStack drain(FluidStack resource, int maximum, FluidAction action) {
                int channel = channel(side);
                if (!exposed() || maximum <= 0) return FluidStack.EMPTY;
                if (channel < 0) channel = 3;
                FluidStack result = FluidStack.EMPTY;
                for (var cell : members()) {
                    var tank = cell.tanks.get(channel);
                    var request = result.isEmpty() ? resource : result;
                    var taken = request.isEmpty() ? tank.drain(maximum, action) : tank.drain(request.copyWithAmount(maximum), action);
                    if (taken.isEmpty()) continue;
                    if (result.isEmpty()) result = taken.copy();
                    else result = net.createplant.thermal.FluidThermal.mix(result, taken, taken.getAmount());
                    maximum -= taken.getAmount();
                    if (maximum == 0) break;
                }
                return result;
            }
        });
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEElectrolysis.CELL_ENTITY.get(), (cell, side) ->
            side != null && cell.getBlockState().getValue(ElectrolysisCellBlock.JOIN.get(side.ordinal())) ? null
                : net.createplant.mixture.GasMixturePort.both(cell.port(side), cell.controller()));
    }

    public void writeContents(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putString("id", "create_refinery_n_reactors:electrolysis_cell");
        for (int index = 0; index < tanks.size(); index++) tag.put("Tank" + index, tanks.get(index).writeToNBT(lookup, new CompoundTag()));
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);writeContents(tag, lookup);
        if (controller != null) tag.putLong("Controller", controller.asLong());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        for (int index = 0; index < tanks.size(); index++) {
            tanks.get(index).readFromNBT(lookup, tag.getCompound("Tank" + index));
            if (tanks.get(index).getFluidAmount() > CAPACITY) tanks.get(index).setFluid(tanks.get(index).getFluid().copyWithAmount(CAPACITY));
        }
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var owner = controller();
        if (owner != null && !sneaking) return owner.addToGoggleTooltip(tooltip, false);
        tooltip.add(Component.translatable("electrolysis.status." + (owner == null ? "structure" : owner.status())));
        return true;
    }
}
