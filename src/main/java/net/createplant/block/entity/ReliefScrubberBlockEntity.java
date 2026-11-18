package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.ReliefScrubberBlock;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CECompressor;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Caustic soda that neutralises acidic gas piped into the bottom; the wash state is shown on the block. */
public class ReliefScrubberBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY=8000;
    private long activeUntil=Long.MIN_VALUE;
    private final ThermalFluidTank caustic=new ThermalFluidTank(CAPACITY,stack->{setChanged();if(level!=null && !level.isClientSide)sendData();}) {
        @Override public boolean isFluidValid(FluidStack stack) { return !stack.isEmpty() && stack.is(CEChlorAlkali.CAUSTIC_SODA.get()); }
    };

    public ReliefScrubberBlockEntity(BlockPos pos,BlockState state) { super(CECompressor.RELIEF_SCRUBBER_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    private final IFluidHandler gasPort=new IFluidHandler() {
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int index){return FluidStack.EMPTY;}
        public int getTankCapacity(int index){return CAPACITY;}
        public boolean isFluidValid(int index,FluidStack stack){return net.createplant.safety.ToxicGas.causticScrubbable(stack);}
        public int fill(FluidStack stack,FluidAction action){if(!isFluidValid(0,stack) || !topOpen())return 0;int accepted=Math.min(stack.getAmount(),caustic.getFluidAmount());return action.execute() ? scrub(stack.copyWithAmount(accepted)) : accepted;}
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
    };
    public IFluidHandler port(Direction side) { return side==Direction.DOWN ? gasPort : side==null || side==getBlockState().getValue(ReliefScrubberBlock.FACING) ? caustic : null; }
    public int amount() { return caustic.getFluidAmount(); }
    public boolean connected() { return level!=null; }
    /** Washed gas leaves through the top, so a covered top accepts no gas. */
    public boolean topOpen() {
        BlockPos top=worldPosition.above();
        return level!=null && level.hasChunkAt(top) && level.getBlockState(top).canBeReplaced() && level.getFluidState(top).isEmpty();
    }
    public String status() {
        if(getBlockState().getValue(ReliefScrubberBlock.ACTIVE))return "scrubbing";
        if(!connected())return "disconnected";
        if(!topOpen())return "blocked";
        return caustic.isEmpty() ? "empty" : "ready";
    }

    /** Consumes at most the matching amount of caustic; callers remove exactly the returned amount of gas. */
    public int scrub(FluidStack gas) {
        if(level==null || level.isClientSide || gas.isEmpty() || !net.createplant.safety.ToxicGas.causticScrubbable(gas))return 0;
        int amount=Math.min(gas.getAmount(),caustic.getFluidAmount());
        if(amount<=0)return 0;
        int consumed=caustic.drain(amount,IFluidHandler.FluidAction.EXECUTE).getAmount();
        if(consumed>0){activeUntil=level.getGameTime()+20;setActive(true);}
        return consumed;
    }

    private void setActive(boolean active) {
        if(level!=null && getBlockState().getValue(ReliefScrubberBlock.ACTIVE)!=active)
            level.setBlockAndUpdate(worldPosition,getBlockState().setValue(ReliefScrubberBlock.ACTIVE,active));
    }
    @Override public void tick() {
        super.tick();
        if(level!=null && !level.isClientSide && getBlockState().getValue(ReliefScrubberBlock.ACTIVE) && level.getGameTime()>=activeUntil)setActive(false);
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);tag.put("Caustic",caustic.writeToNBT(lookup,new CompoundTag()));
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);caustic.readFromNBT(lookup,tag.getCompound("Caustic"));
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if(level==null)return;
        CompoundTag data=saveCustomOnly(level.registryAccess());BlockEntity.addEntityType(data,getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) {
        text.add(Component.translatable("scrubber.create_refinery_n_reactors.status."+status()));
        text.add(Component.translatable("scrubber.create_refinery_n_reactors.caustic",amount(),CAPACITY));
        return true;
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CECompressor.RELIEF_SCRUBBER_ENTITY.get(),(be,side)->be.port(side));
    }
}
