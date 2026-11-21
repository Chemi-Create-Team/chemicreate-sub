package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.createplant.chemistry.ReactorModule;
import net.createplant.chemistry.VesselFouling;
import net.createplant.recipe.PressureReactionRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.nbt.NbtUtils;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.ReactorStructure;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.GradeFluidTank;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Create's tank inventory and joining, up to 3x3 across and four blocks high. Whether the hatch is open is the block state of every part, set from
 * the controller; while it is open the contents stand at atmospheric pressure, and opening it under pressure throws out what was held back.
 * In a 3x3 vessel each layer holds one reaction-plant internal, kept by the layer's block over the controller. The vessel itself receives
 * kinetic power and runs the process selected by those internals.
 */
public class LiddedVesselBlockEntity extends ReactorMachineBlockEntity implements IMultiBlockEntityContainer.Fluid {
    /** Reinforced lidded vessels share Create's 16 bar gameplay pressure rating. */
    private final GradeFluidTank tankInventory;
    private BlockPos controller, lastKnownPos, powerInput;
    private int width = 1, height = 1;
    private boolean updateConnectivity, selectingPower, connectionsPending;
    private float inputSpeed;
    private LerpedFloat fluidLevel = LerpedFloat.linear().startWithValue(0);
    private final LerpedFloat lid;
    private ItemStack internal = ItemStack.EMPTY;
    private boolean windows;
    private final FluidStack[] storageDisplay = {FluidStack.EMPTY,FluidStack.EMPTY};
    private final FluidStack[] liquidDisplay=new FluidStack[12],gasDisplay=new FluidStack[12];
    private VesselFouling fouling;
    private final net.createplant.thermal.TankHeating heating=new net.createplant.thermal.TankHeating();

    public LiddedVesselBlockEntity(BlockPos pos, BlockState state) {
        super(CETransport.LIDDED_VESSEL_ENTITY.get(), pos, state);
        tankInventory = new GradeFluidTank(getCapacityMultiplier(), this::onFluidStackChanged, this);
        lid = LerpedFloat.linear().startWithValue(open(state) ? 1 : 0);
    }

    private static boolean open(BlockState state) { return state.hasProperty(LiddedVesselBlock.OPEN) && state.getValue(LiddedVesselBlock.OPEN); }

    public boolean open() { return open(state()); }

    public Direction hinge() { return state().hasProperty(LiddedVesselBlock.HINGE) ? state().getValue(LiddedVesselBlock.HINGE) : Direction.NORTH; }

    /** The block state in the world: while a block is being placed, the cached one is reset after joining has already updated it. */
    private BlockState state() { return level == null ? getBlockState() : level.getBlockState(worldPosition); }

    /** 0 closed to 1 fully open, eased on the client. */
    public float lidOpening(float partialTicks) { return lid.getValue(partialTicks); }

    public ItemStack internal() { return internal; }
    public VesselFouling fouling() { if (fouling==null) fouling=new VesselFouling(this);return fouling; }







    public float frostLevel() {
        var owner=getControllerBE();if(owner==null)return 0;
        if(!owner.tankInventory.isEmpty() && net.createplant.transport.FluidHazards.of(owner.tankInventory.getFluid()).heat().ordinal()<=1)return 1;
        for(int i=0;i<PROCESS_TANKS;i++)if(!owner.climateFluid(i).isEmpty() && net.createplant.transport.FluidHazards.of(owner.climateFluid(i)).heat().ordinal()<=1)return 1;
        return 0;
    }
    public boolean maintenanceSafe() {
        return rotationSpeed()==0 && !currentBurnerHeat().isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED);
    }
    @Override public boolean hasProcessContents() { return super.hasProcessContents() || fouling!=null && (fouling.dirtLevel()>0 || !fouling.drainPort().getFluidInTank(0).isEmpty()); }
    @Override public boolean blocksControllerMove() { return super.blocksControllerMove() || fouling!=null && (fouling.dirtLevel()>0 || !fouling.drainPort().getFluidInTank(0).isEmpty()); }
    @Override public void dismantle() { super.dismantle();if (fouling!=null) ToxicGas.release(level,worldPosition,fouling.clear()); }
    public IFluidHandler processGasPort(Direction side,int bank) { return processGasPort(bank); }
    @Override protected String processInhibit() { return fouling().blocked() ? "fouled" : ""; }
    @Override protected float processingSpeedFactor() { return fouling().speedFactor(); }


    @Override protected void completedStirredBatch(StandardProcessingRecipe<RecipeInput> recipe,int count) { fouling().onBatch(recipe,count); }
    @Override protected void changed() { super.changed(); }
    @Override public void thermalChanged() { changed(); }
    public FluidStack consumeWashWater(int amount,FluidAction action) {
        if (amount<=0 || !isController()) return FluidStack.EMPTY;
        var fluids=mixingFluids();int left=amount;FluidStack parcel=FluidStack.EMPTY;
        for (int i=0;i<fluids.length;i++) if (fluids[i].is(net.minecraft.world.level.material.Fluids.WATER)) {
            int take=Math.min(left,fluids[i].getAmount());var part=fluids[i].copyWithAmount(take);
            if (take>0) parcel=parcel.isEmpty() ? part : FluidThermal.mix(parcel,part,take);
            fluids[i].shrink(take);left-=take;if (left==0) break;
        }
        if (left>0) return FluidStack.EMPTY;
        if (action.execute()) { commitMixingFluids(fluids);changed(); }
        return parcel;
    }
    public boolean windowsEnabled() { var owner=getControllerBE();return owner!=null && owner.windows; }
    public void toggleWindows() {
        var owner=getControllerBE();
        if (owner==null || level==null || level.isClientSide) return;
        boolean enabled=!owner.windows;
        for (int y=0;y<owner.height;y++) for (int x=0;x<owner.width;x++) for (int z=0;z<owner.width;z++) {
            BlockPos at=owner.worldPosition.offset(x,y,z);
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof LiddedVesselBlockEntity part && part.getController().equals(owner.worldPosition)) {
                part.windows=enabled;part.setChanged();part.sendData();
            }
        }
    }
    @Override public Object getExtraData() { return windows; }
    @Override public void setExtraData(Object data) { if (data instanceof Boolean enabled) windows=enabled; }
    @Override public Object modifyExtraData(Object data) { return data instanceof Boolean enabled ? enabled || windows : data; }
    private void updateWindowModel(int oldWidth,int oldHeight) {
        if (level==null || !level.isClientSide) return;
        if (!isController()) { requestModelDataUpdate();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);return; }
        for (int y=0;y<Math.max(height,oldHeight);y++) for (int x=0;x<Math.max(width,oldWidth);x++) for (int z=0;z<Math.max(width,oldWidth);z++) {
            BlockPos at=worldPosition.offset(x,y,z);
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof LiddedVesselBlockEntity part) {
                part.requestModelDataUpdate();var state=level.getBlockState(at);level.sendBlockUpdated(at,state,state,Block.UPDATE_CLIENTS);
            }
        }
    }
    @Override public int displayedFluidSlots() { return 12; }
    @Override public FluidStack displayedFluid(int slot) { ensureDisplay();return slot==11 || slot==9 ? FluidStack.EMPTY : slot>=7 && slot<9 ? storageDisplay[slot-7] : super.displayedFluid(slot==10 ? THIRD_FEED : slot); }
    @Override public int getDisplayedCapacity() {
        ensureDisplay();
        var kind=module();
        int capacity=kind==ReactorModule.NONE || kind==ReactorModule.STIRRED || !liquidDisplay[7].isEmpty() ? tankInventory.volumeCapacity() : 0;
        for (int i=0;i<PROCESS_TANKS;i++) if (!liquidDisplay[i==THIRD_FEED ? 10 : i].isEmpty()) capacity+=CAPACITY;
        return Math.max(1,capacity);
    }
    @Override public int getDisplayedCapacity(int slot) { return slot==11 || slot==9 ? 0 : slot>=7 && slot<9 ? tankInventory.volumeCapacity() : super.getDisplayedCapacity(slot==10 ? THIRD_FEED : slot); }
    @Override protected void refreshDisplay() {
        super.refreshDisplay();
        storageDisplay[0]=physicalPhase(false);storageDisplay[1]=physicalPhase(true);
        for (int i=0;i<PROCESS_TANKS;i++) { int display=i==THIRD_FEED ? 10 : i;liquidDisplay[display]=processLiquidContent(i);gasDisplay[display]=processGasContent(i); }
        liquidDisplay[11]=FluidStack.EMPTY;gasDisplay[11]=FluidStack.EMPTY;
        liquidDisplay[7]=storageDisplay[0];liquidDisplay[8]=FluidStack.EMPTY;gasDisplay[7]=FluidStack.EMPTY;gasDisplay[8]=storageDisplay[1];
    }
    public FluidStack displayedLiquid(int slot) { ensureDisplay();return slot>=0 && slot<liquidDisplay.length && slot!=9 ? liquidDisplay[slot] : FluidStack.EMPTY; }
    public FluidStack displayedGas(int slot) { ensureDisplay();return slot!=9 && slot>=0 && slot<gasDisplay.length ? gasDisplay[slot] : FluidStack.EMPTY; }
    private FluidStack physicalPhase(boolean gas) {
        var fluid=tankInventory.getFluid();return !fluid.isEmpty() && net.createplant.transport.FluidHazards.of(fluid).gas()==gas ? fluid.copy() : FluidStack.EMPTY;
    }
    @Override protected FluidStack[] mixingFluids() {
        var fluids=java.util.Arrays.copyOf(super.mixingFluids(),9);fluids[8]=tankInventory.getFluid().copy();return fluids;
    }
    @Override protected int[] mixingInputs() {
        return new int[]{0,1,THIRD_FEED,8};
    }
    @Override protected void commitMixingFluids(FluidStack[] fluids) {
        super.commitMixingFluids(fluids);tankInventory.setFluid(fluids[8].copy());
    }

    public void connect() { if (level != null && !level.isClientSide && isController()) { updateConnectivity = false;ConnectivityHandler.formMulti(this); } }

    public static int getCapacityMultiplier() { return FluidTankBlockEntity.getCapacityMultiplier(); }
    public FluidTank getTankInventory() { return tankInventory; }
    public LerpedFloat getFluidLevel() { return fluidLevel; }
    public float getFillState() { return tankInventory.gasStored() ? (float)tankInventory.gasVolume() / tankInventory.volumeCapacity()
        : (float)tankInventory.getFluidAmount() / tankInventory.getCapacity(); }
    public int getTotalTankSize() { return width * width * height; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public void setWidth(int width) { this.width = width; }
    @Override public void setHeight(int height) { this.height = height; }
    @Override public int getMaxWidth() { return 3; }
    @Override public Direction.Axis getMainConnectionAxis() { return Direction.Axis.Y; }
    @Override public BlockPos getLastKnownPos() { return lastKnownPos; }
    @Override public boolean isController() { return controller == null || controller.equals(worldPosition); }
    @Override public BlockPos getController() { return isController() ? worldPosition : controller; }
    @Override public LiddedVesselBlockEntity getControllerBE() {
        return isController() || level == null ? this : level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof LiddedVesselBlockEntity owner ? owner : null;
    }
    @Override public void setController(BlockPos pos) {
        if (pos.equals(controller)) return;
        if (!pos.equals(worldPosition) && isController() && level!=null && !level.isClientSide && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity owner) transferPlainItemsTo(owner);
        controller = pos;invalidateStructure();setChanged();sendData();invalidateCapabilities();
    }
    @Override public void preventConnectivityUpdate() { updateConnectivity = false; }
    @Override public boolean hasTank() { return true; }
    @Override public int getTankSize(int tank) { return getCapacityMultiplier(); }
    @Override public void setTankSize(int tank, int blocks) {
        tankInventory.setCapacity(Math.max(1, blocks) * getCapacityMultiplier());
        int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
        if (overflow > 0) {
            if (net.createplant.mixture.MixtureHelper.isMixture(tankInventory.getFluid())) tankInventory.setFluid(tankInventory.getFluid().copyWithAmount(tankInventory.getCapacity()));
            else tankInventory.drain(overflow, FluidAction.EXECUTE);
        }
    }
    @Override public IFluidTank getTank(int tank) { return tankInventory; }
    @Override public FluidStack getFluid(int tank) { return tankInventory.getFluid().copy(); }

    private void onFluidStackChanged(FluidStack fluid) {
        if (level == null) return;
        fluidLevel.chase(getFillState(), .5, LerpedFloat.Chaser.EXP);
        changed();level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override public ReactorModule module() {
        if (level == null || internal == null) return ReactorModule.NONE;
        var owner = getControllerBE();
        if (owner == null || owner.width != 3 && owner.width != 1) return ReactorModule.NONE;
        ReactorModule module = CEReactor.module(owner.internal);
        return module == null || owner.width!=3 ? ReactorModule.NONE : module;
    }
    @Override protected boolean processController() { return isController() && module() != ReactorModule.NONE; }
    @Override protected boolean vesselOpen() { return open(); }
    @Override public float rotationSpeed() {
        if (level == null) return 0;
        var owner = getControllerBE();
        if (owner == null || owner.width != 3) return 0;
        if (owner != this) return owner.rotationSpeed();
        if (!ReactorStructure.loaded(level, worldPosition)) return 0;
        if (level.isClientSide) return powerInput == null ? 0 : inputSpeed;
        return powerInput != null && level.hasChunkAt(powerInput) && level.getBlockEntity(powerInput) instanceof LiddedVesselBlockEntity part && part.acceptsRotation() ? part.getSpeed() : 0;
    }
    @Override public float calculateStressApplied() {
        var owner = getControllerBE();
        return lastStressApplied = owner != null && owner.formed() && worldPosition.equals(owner.powerInput) && acceptsRotation() ? owner.module().stress * owner.sections() : 0;
    }
    @Override public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        var owner = getControllerBE();
        if (level != null && !level.isClientSide && owner != null) owner.selectPowerInput();
    }
    private boolean acceptsRotation() {
        var current = state();
        if (!(current.getBlock() instanceof LiddedVesselBlock block)) return false;
        return block.hasShaftTowards(level, worldPosition, current, Direction.DOWN);
    }
    private void selectPowerInput() {
        if (selectingPower || level == null || level.isClientSide || !isController()) return;
        selectingPower = true;
        try {
            BlockPos selected = null;float speed = 0;
            for (int y = 0; width == 3 && y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
                BlockPos pos = worldPosition.offset(x, y, z);
                // An overstressed network still owns this load even though its effective speed has fallen to zero.
                if (selected == null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity part && part.getController().equals(worldPosition) && part.acceptsRotation() && part.getTheoreticalSpeed() != 0) {
                    selected = pos;speed = part.getSpeed();
                }
            }
            boolean changed = !java.util.Objects.equals(powerInput, selected) || inputSpeed != speed;
            powerInput = selected;inputSpeed = speed;
            if (changed) {
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                    if (level.hasChunkAt(worldPosition.offset(x, y, z)) && level.getBlockEntity(worldPosition.offset(x, y, z)) instanceof LiddedVesselBlockEntity part && part.hasNetwork()) part.getOrCreateNetwork().updateStressFor(part, part.calculateStressApplied());
                sendData();
            }
        } finally { selectingPower = false; }
    }
    @Override public void invalidateStructure() {
        super.invalidateStructure();
        if (level == null || level.isClientSide || selectingPower) return;
        var owner = getControllerBE();
        if (owner == null || owner.selectingPower) return;
        owner.selectingPower = true;
        try {
            for (int y = 0; y < owner.height; y++) for (int x = 0; x < owner.width; x++) for (int z = 0; z < owner.width; z++)
                if (level.hasChunkAt(owner.worldPosition.offset(x, y, z)) && level.getBlockEntity(owner.worldPosition.offset(x, y, z)) instanceof LiddedVesselBlockEntity part && part.hasNetwork()) part.getOrCreateNetwork().updateStressFor(part, part.calculateStressApplied());
        } finally { owner.selectingPower = false; }
    }
    @Override public void validateStructure() {
        super.validateStructure();
        selectPowerInput();
        if (isController() && level != null && !level.isClientSide)
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                if (level.hasChunkAt(worldPosition.offset(x, y, z)) && level.getBlockEntity(worldPosition.offset(x, y, z)) instanceof LiddedVesselBlockEntity part && part.hasNetwork()) part.getOrCreateNetwork().updateStressFor(part, part.calculateStressApplied());
    }

    @Override public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) { connectionsPending = true;refreshConnections(); }
    }

    @Override public void lazyTick() { super.lazyTick();refreshConnections(); }

    private void refreshConnections() {
        if (!connectionsPending || level == null || level.isClientSide || isRemoved() || !level.hasChunkAt(getController())) return;
        var owner = getControllerBE();if (owner == null || !owner.isController()) return;
        int x = worldPosition.getX() - owner.worldPosition.getX(), z = worldPosition.getZ() - owner.worldPosition.getZ();
        if (x < 0 || x >= owner.width || z < 0 || z >= owner.width || worldPosition.getY() < owner.worldPosition.getY()
            || worldPosition.getY() >= owner.worldPosition.getY() + owner.height) return;
        var current = state();if (!current.is(CETransport.LIDDED_VESSEL.get())) return;
        connectionsPending = false;
        boolean expected = ladder(x, z, owner.width, owner.hinge());
        var next=current.setValue(LiddedVesselBlock.LADDER, expected).setValue(LiddedVesselBlock.DRIVE, owner.width == 3).setValue(LiddedVesselBlock.AXIS, Direction.Axis.Y);
        if(owner.width==1)next=next.setValue(LiddedVesselBlock.HATCH,LiddedVesselBlock.Hatch.NONE).setValue(LiddedVesselBlock.OPEN,false);
        setState(next);
        if (!acceptsRotation() && (hasSource() || getTheoreticalSpeed() != 0)) {
            detachKinetics();removeSource();sendData();
        }
        owner.selectPowerInput();
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this));
    }

    @Override public int getMaxLength(Direction.Axis axis, int width) { return axis == Direction.Axis.Y ? ReactorStructure.MAX_SECTIONS : getMaxWidth(); }

    @Override public void tick() {
        if (level != null && !level.isClientSide && updateConnectivity && isController()) {
            updateConnectivity = false;ConnectivityHandler.formMulti(this);
        }
        if (level!=null && !level.isClientSide && isController()) { fouling().tick(); }
        super.tick();
        if (level == null || !isController()) return;
        if (!level.isClientSide && !isRemoved() && module()==ReactorModule.NONE && ReactorStructure.loaded(level,worldPosition))
            tickHeatOnlyReactions(height,!tankInventory.isEmpty());
        if (!level.isClientSide && Math.floorMod(level.getGameTime()+worldPosition.asLong(),20)==0) heatContents();
        if (!level.isClientSide && Math.floorMod(level.getGameTime()+worldPosition.asLong(),5)==0) updateDisplayedHeat();
        if (lastKnownPos == null) lastKnownPos = worldPosition;
        if (!lastKnownPos.equals(worldPosition)) { removeController(true);lastKnownPos = worldPosition; }
        fluidLevel.tickChaser();
        if (level.isClientSide) {
            lid.chase(open() ? 1 : 0, .12, LerpedFloat.Chaser.LINEAR);
            lid.tickChaser();
            return;
        }
    }

    private void heatContents() {
        var fluids=new java.util.ArrayList<FluidStack>();fluids.add(tankInventory.getFluid());
        for(int i=0;i<PROCESS_TANKS;i++)fluids.add(climateFluid(i));
        var base=new java.util.ArrayList<BlockPos>();
        for(int x=0;x<width;x++)for(int z=0;z<width;z++)base.add(worldPosition.offset(x,0,z));
        int previous=heating.progress();
        if(heating.tick(fluids,net.createplant.thermal.TankHeating.burnerBelow(level,base),net.createplant.thermal.SeasonalThermal.heating(level))) {
            onFluidStackChanged(tankInventory.getFluid());changed();
        } else if(previous!=heating.progress())setChanged();
    }

    /** An internal off its layer's slot moves there when joining, or drops if that is taken. */
    @Override public void notifyMultiUpdated() {
        onFluidStackChanged(tankInventory.getFluid());
        invalidateStructure();
        // Create sizes and notifies every part before the controller, so each part uses its own width and height.
        var owner = getControllerBE();
        if (level == null || level.isClientSide || owner == null) return;
        windows=owner.windows;
        BlockPos origin = owner.getBlockPos();
        int x = worldPosition.getX() - origin.getX(), z = worldPosition.getZ() - origin.getZ(), w = getWidth();
        boolean top = worldPosition.getY() == origin.getY() + getHeight() - 1;
        BlockState lead = owner.state(), state = state();
        setState(state.setValue(LiddedVesselBlock.NORTH, z == 0).setValue(LiddedVesselBlock.SOUTH, z == w - 1).setValue(LiddedVesselBlock.WEST, x == 0)
            .setValue(LiddedVesselBlock.EAST, x == w - 1).setValue(LiddedVesselBlock.BOTTOM, worldPosition.getY() == origin.getY()).setValue(LiddedVesselBlock.DRIVE, w == 3)
            .setValue(LiddedVesselBlock.HATCH, top ? hatch(x, z, w) : LiddedVesselBlock.Hatch.NONE).setValue(LiddedVesselBlock.LADDER, ladder(x, z, w, lead.getValue(LiddedVesselBlock.HINGE)))
            .setValue(LiddedVesselBlock.OPEN, lead.getValue(LiddedVesselBlock.OPEN)).setValue(LiddedVesselBlock.HINGE, lead.getValue(LiddedVesselBlock.HINGE)));
        BlockPos slot = new BlockPos(origin.getX(), worldPosition.getY(), origin.getZ());
        if (!internal.isEmpty() && !slot.equals(worldPosition)) {
            if (level.getBlockEntity(slot) instanceof LiddedVesselBlockEntity holder && holder.internal.isEmpty()) { holder.setInternal(internal);internal = ItemStack.EMPTY;setChanged();sendData(); }
            else dropInternal();
        }
    }

    /** Wide vessels have a central hatch; a one-block column has a fixed lid. */
    private static LiddedVesselBlock.Hatch hatch(int x, int z, int width) {
        if (width == 1) return LiddedVesselBlock.Hatch.NONE;
        if (width == 3) return x == 1 && z == 1 ? LiddedVesselBlock.Hatch.FULL : LiddedVesselBlock.Hatch.NONE;
        return LiddedVesselBlock.Hatch.quarter(z == 0 ? Direction.SOUTH : Direction.NORTH, x == 0 ? Direction.EAST : Direction.WEST);
    }

    /** The left wall's left edge is its corner along the hinge side. */
    private static boolean ladder(int x, int z, int width, Direction hinge) {
        return switch (hinge) {
            case NORTH -> x == 0 && z == 0;
            case SOUTH -> x == width - 1 && z == width - 1;
            case WEST -> x == 0 && z == width - 1;
            case EAST -> x == width - 1 && z == 0;
            default -> false;
        };
    }

    /** A separated block retains its internal for recovery with a wrench. */
    @Override public void removeController(boolean keepFluids) {
        if (level == null || level.isClientSide) return;
        controller = null;updateConnectivity = true;
        width = 1;height = 1;powerInput = null;inputSpeed = 0;
        if (!keepFluids) setTankSize(0, 1);
        invalidateStructure();invalidateCapabilities();setChanged();sendData();
        setState(state().setValue(LiddedVesselBlock.NORTH, true).setValue(LiddedVesselBlock.EAST, true).setValue(LiddedVesselBlock.SOUTH, true).setValue(LiddedVesselBlock.WEST, true)
            .setValue(LiddedVesselBlock.BOTTOM, true).setValue(LiddedVesselBlock.DRIVE, false).setValue(LiddedVesselBlock.HATCH, LiddedVesselBlock.Hatch.NONE).setValue(LiddedVesselBlock.OPEN,false).setValue(LiddedVesselBlock.LADDER, true));
    }

    /** Neighbours are updated too, so a pipe on the top joins or leaves with the lid. */
    public void setState(BlockState next) {
        if (next == state()) return;
        level.setBlock(worldPosition, next, Block.UPDATE_ALL);
        level.invalidateCapabilities(worldPosition);
    }

    public void toggleLid() { var owner = getControllerBE();if (owner != null) owner.setLid(!owner.open()); }

    private void setLid(boolean open) {
        if(width==1)return;
        if (!ReactorStructure.loaded(level, worldPosition)) return;

        applyLid(open, hinge());
        level.playSound(null, top(), open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1, .8f);
        if (!open) return;
        vent();
    }

    private void applyLid(boolean open, Direction hinge) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            BlockPos pos = worldPosition.offset(x, y, z);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity part && part.getController().equals(worldPosition))
                part.setState(part.state().setValue(LiddedVesselBlock.OPEN, open).setValue(LiddedVesselBlock.HINGE, hinge));
        }
    }

    /** The middle of the top layer, where the hatch opens. */
    private BlockPos top() { return worldPosition.offset(width / 2, height - 1, width / 2); }

    /** An open vessel holds no gas and no vapour above atmospheric pressure: it leaves through the top, toxic ones as a cloud. */
    void vent() {
        if(level==null || level.isClientSide)return;
        for(var released:removeProcessGases())ToxicGas.release(level,top(),released);
        if(!tankInventory.isEmpty() && net.createplant.transport.FluidHazards.of(tankInventory.getFluid()).gas()) {
            var released=tankInventory.drain(tankInventory.getFluidAmount(),FluidAction.EXECUTE);ToxicGas.release(level,top(),released);
            ((ServerLevel)level).sendParticles(ParticleTypes.CLOUD,top().getX()+.5,top().getY()+.5,top().getZ()+.5,8,.3,.3,.3,.05);
        }
    }

    /** The lid is thrown back with a bang and the jet lifts whatever stands over the opening. */


    /** The block of a layer that keeps its internal: the one over the controller. Called on the controller. */
    public LiddedVesselBlockEntity slot(int layer) {
        return level != null && level.hasChunkAt(worldPosition.above(layer)) && level.getBlockEntity(worldPosition.above(layer)) instanceof LiddedVesselBlockEntity slot ? slot : null;
    }

    public boolean canTakeInternal() {
        if (width != 3) return false;
        for (int y = 0; y < height; y++) if (slot(y) != null && slot(y).internal.isEmpty()) return true;
        return false;
    }

    public boolean canTakeInternal(ItemStack stack) { return width == 3 && CEReactor.module(stack) != null && canTakeInternal(); }

    public boolean hasInternals() {
        for (int y = 0; y < height; y++) if (slot(y) != null && !slot(y).internal.isEmpty()) return true;
        return false;
    }

    /** Puts an internal into the lowest empty layer and returns where it went. */
    public BlockPos putInternal(ItemStack stack) {
        if (!canTakeInternal(stack)) throw new IllegalStateException("No available vessel layer for this internal");
        for (int y = 0; y < height; y++) {
            var slot = slot(y);
            if (slot != null && slot.internal.isEmpty()) { slot.setInternal(stack);return slot.getBlockPos(); }
        }
        throw new IllegalStateException("No empty layer");
    }

    /** Takes the internal out of the highest layer that has one. */
    public ItemStack takeTopInternal() {
        for (int y = height - 1; y >= 0; y--) {
            var slot = slot(y);
            if (slot != null && !slot.internal.isEmpty()) { ItemStack item = slot.internal;slot.setInternal(ItemStack.EMPTY);return item; }
        }
        return ItemStack.EMPTY;
    }

    public void setInternal(ItemStack stack) {
        internal = stack;
        setChanged();
        sendData();
        ReactorStructure.changed(level, worldPosition);
        var owner=getControllerBE();
        if(owner!=null && level!=null && !level.isClientSide)for(int y=0;y<owner.height;y++)for(int x=0;x<owner.width;x++)for(int z=0;z<owner.width;z++) {
            if(level.getBlockEntity(owner.worldPosition.offset(x,y,z)) instanceof LiddedVesselBlockEntity part){part.connectionsPending=true;part.refreshConnections();}
        }
    }

    public void dropInternal() {
        if (internal.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, internal);
        internal = ItemStack.EMPTY;
    }

    /** The connected vessel itself runs the process selected by its internals. */
    public ReactorMachineBlockEntity reactor() {
        var owner = getControllerBE();
        return owner != null && owner.formed() ? owner : null;
    }

    /** Whether this block lies under the open hatch. */
    public boolean underOpenHatch() { return open() && state().getValue(LiddedVesselBlock.HATCH) != LiddedVesselBlock.Hatch.NONE; }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        var owner=getControllerBE();if(owner==null)return false;
        if (net.createplant.mixture.MixtureHelper.isMixture(owner.tankInventory.getFluid())) {
            net.createplant.mixture.MixtureGoggleHelper.appendSummary(tooltip, owner.tankInventory.getFluid());return true;
        }
        String key="lidded_vessel.create_refinery_n_reactors.";
        var status=Component.translatable(owner.module()==ReactorModule.NONE && !owner.running() && !owner.status().equals("heat") ? key+"storage" : "reactor.create_refinery_n_reactors.status."+owner.status());
        tooltip.add(Component.translatable(key+"summary",status,Component.translatable(key+"lid."+(owner.width==1 ? "fixed" : owner.open() ? "open" : "closed")),owner.width,owner.height));
        var heat=owner.currentBurnerHeat();
        var condition=heat==com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING ? com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED : heat.isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED) ? com.simibubi.create.content.processing.recipe.HeatCondition.HEATED : com.simibubi.create.content.processing.recipe.HeatCondition.NONE;
        tooltip.add(Component.translatable("create."+condition.getTranslationKey()));
        long amount=owner.tankInventory.getFluidAmount();for(int i=0;i<PROCESS_TANKS;i++)amount+=owner.climateFluid(i).getAmount();
        tooltip.add(Component.translatable(key+"operation",Math.round(Math.abs(owner.rotationSpeed())),Math.round(owner.progress()*100/Math.max(1,owner.cycleTicks())),amount));
        // The overview keeps to four lines: the contents, or with Shift the maintenance state.
        var stock=isPlayerSneaking ? null : owner.stockLine(owner.tankInventory.getFluid());
        tooltip.add(stock!=null ? stock : Component.translatable(key+"maintenance",Math.round(owner.fouling().foulingFraction()*100),Component.translatable(key+"drain."+(owner.fouling().drainOpen() ? "open" : "closed"))));return true;
    }

    /** The open hatch stands up one block over the top. */
    @Override protected AABB createRenderBoundingBox() { return isController() ? new AABB(worldPosition).expandTowards(width - 1, height, width - 1).expandTowards(0,-.125,0) : super.createRenderBoundingBox(); }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        if (controller != null && !isController()) tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putInt("Size", width);tag.putInt("Height", height);
        if (lastKnownPos != null) tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (isController()) tag.put("TankContent", tankInventory.writeToNBT(lookup, new CompoundTag()));
        if(!clientPacket)heating.write(tag,lookup);
        if (updateConnectivity) tag.putBoolean("Uninitialized", true);
        if (powerInput != null) tag.putLong("PowerInput", powerInput.asLong());
        tag.putFloat("InputSpeed", inputSpeed);
        if (!internal.isEmpty()) tag.put("Internal", internal.save(lookup));
        tag.putBoolean("Windows",windows);
        var foulingTag=new CompoundTag();fouling().write(foulingTag);tag.put("Fouling",foulingTag);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        int oldWidth=width,oldHeight=height;boolean oldWindows=windows;
        controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag, "Controller").orElse(null) : null;
        width = Math.clamp(tag.getInt("Size"), 1, 3);height = Math.clamp(tag.getInt("Height"), 1, ReactorStructure.MAX_SECTIONS);
        lastKnownPos = tag.contains("LastKnownPos") ? NbtUtils.readBlockPos(tag, "LastKnownPos").orElse(null) : null;
        updateConnectivity = tag.getBoolean("Uninitialized");
        tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());
        if (isController()) {
            tankInventory.readFromNBT(lookup, tag.getCompound("TankContent"));
            if (tankInventory.getSpace() < 0) tankInventory.drain(-tankInventory.getSpace(), FluidAction.EXECUTE);
        }
        powerInput = tag.contains("PowerInput") ? BlockPos.of(tag.getLong("PowerInput")) : null;inputSpeed = tag.getFloat("InputSpeed");
        super.read(tag, lookup, clientPacket);
        if(!clientPacket)heating.read(tag,lookup);
        if (clientPacket) fluidLevel.chase(getFillState(), .5, LerpedFloat.Chaser.EXP);
        else fluidLevel = LerpedFloat.linear().startWithValue(getFillState());
        internal = tag.contains("Internal") ? ItemStack.parseOptional(lookup, tag.getCompound("Internal")) : ItemStack.EMPTY;
        if (CEReactor.module(internal) == null) internal = ItemStack.EMPTY;
        windows=tag.getBoolean("Windows");
        if (!clientPacket) connectionsPending = true;
        fouling().read(tag.getCompound("Fouling"));
        if (clientPacket && (oldWindows!=windows || oldWidth!=width || oldHeight!=height)) updateWindowModel(oldWidth,oldHeight);
    }

    /** Once internals form a process, pipes and funnels on any face reach its buffers instead of the vessel's storage tank. */
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CETransport.LIDDED_VESSEL_ENTITY.get(), (be, side) -> {
            if (side==Direction.UP && be.underOpenHatch()) return null;
            var owner=be.getControllerBE();if (owner==null) return null;
            var reactor = be.reactor();
            IFluidHandler handler;
            if (reactor!=null && reactor.module()!=ReactorModule.STIRRED) handler=reactor.port(side);
            else if (reactor!=null) {
                int generation=owner.portGeneration();
                var storage=be.storagePhasePort(side,()->be.getControllerBE()==owner && owner.portGeneration()==generation && owner.formed() && owner.module()==ReactorModule.STIRRED && !(side==Direction.UP && be.underOpenHatch()));
                handler=combined(reactor.port(side),storage);
            } else {
                var storage=GradeFluidTank.port(be, side, new FluidTank(0),
                () -> be.getControllerBE() != null && be.reactor() == null && !(side == Direction.UP && be.underOpenHatch()),
                () -> be.getControllerBE() != null && !be.getControllerBE().hasInternals());
                var recovery=guarded(owner.processRecoveryPort(),()->be.getControllerBE()==owner && be.reactor()==null);
                handler=combined(storage,recovery);
            }
            if (reactor!=null) handler=net.createplant.mixture.GasMixturePort.both(handler,owner);
            if (side==Direction.DOWN) handler=combined(handler,owner.fouling().drainPort());
            if (handler==null) return null;
            var delegate=handler;int lease=owner.portGeneration();
            return new IFluidHandler() {
                private boolean live() {
                    if (be.isRemoved() || be.getLevel()==null || !be.getLevel().hasChunkAt(be.getBlockPos()) || !be.getLevel().getBlockState(be.getBlockPos()).is(CETransport.LIDDED_VESSEL.get()) || be.getControllerBE()!=owner || side==Direction.UP && be.underOpenHatch()) return false;
                    return reactor==null ? be.reactor()==null : owner.portGeneration()==lease && owner.formed() && ReactorStructure.loaded(be.getLevel(),owner.getBlockPos());
                }
                private boolean gasAllowed(FluidStack stack) { var current=be.getControllerBE();return current!=null && (!current.open() || stack.isEmpty() || !FluidMaterials.gas(stack.getFluid())); }
                private boolean wasteOnly() { return side==Direction.DOWN && owner.fouling().drainOpen(); }
                @Override public int getTanks() { return wasteOnly() ? 1 : delegate.getTanks(); }
                @Override public FluidStack getFluidInTank(int tank) {
                    if (!live()) return FluidStack.EMPTY;
                    if (wasteOnly()) return owner.fouling().drainPort().getFluidInTank(tank);
                    var stack=delegate.getFluidInTank(tank);
                    return side!=null && !stack.isEmpty() && FluidMaterials.gas(stack.getFluid())!=(side==Direction.UP) ? FluidStack.EMPTY : stack;
                }
                @Override public int getTankCapacity(int tank) { return !live() ? 0 : wasteOnly() ? owner.fouling().drainPort().getTankCapacity(tank) : delegate.getTankCapacity(tank); }
                @Override public boolean isFluidValid(int tank,FluidStack stack) { return live() && !wasteOnly() && gasAllowed(stack) && delegate.isFluidValid(tank,stack); }
                @Override public int fill(FluidStack stack,FluidAction action) { return live() && !wasteOnly() && gasAllowed(stack) ? delegate.fill(stack,action) : 0; }
                @Override public FluidStack drain(int amount,FluidAction action) {
                    if (!live()) return FluidStack.EMPTY;
                    if (wasteOnly()) return owner.fouling().drainPort().drain(amount,action);
                    if (side==Direction.UP) return owner.extractVesselGas(FluidStack.EMPTY,amount,action);
                    if (side==null) return delegate.drain(amount,action);
                    var phased=owner.extractVesselLiquid(FluidStack.EMPTY,amount,action);if (!phased.isEmpty()) return phased;
                    for (int i=0;i<delegate.getTanks();i++) { var stack=getFluidInTank(i);if (!stack.isEmpty() && !FluidMaterials.gas(stack.getFluid())) { var out=delegate.drain(stack.copyWithAmount(Math.min(amount,stack.getAmount())),action);if (!out.isEmpty()) return out; } }
                    return FluidStack.EMPTY;
                }
                @Override public FluidStack drain(FluidStack stack,FluidAction action) {
                    if (!live()) return FluidStack.EMPTY;
                    if (wasteOnly()) return owner.fouling().drainPort().drain(stack,action);
                    if (side==Direction.UP) return owner.extractVesselGas(stack,stack.getAmount(),action);
                    if (side!=null && FluidMaterials.gas(stack.getFluid())) return FluidStack.EMPTY;
                    if (side!=null) { var phased=owner.extractVesselLiquid(stack,stack.getAmount(),action);if (!phased.isEmpty()) return phased; }
                    return delegate.drain(stack,action);
                }
            };
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CETransport.LIDDED_VESSEL_ENTITY.get(), (be, side) -> {
            var owner=be.getControllerBE();return owner==null ? null : owner.itemPort();
        });
    }
    private FluidStack extractVesselGas(FluidStack requested,int amount,FluidAction action) {
        if (amount<=0 || isRemoved() || !isController() || open()) return FluidStack.EMPTY;
        for (int i=2;i<PROCESS_TANKS+2;i++) {
            var port=processGasPort(i%PROCESS_TANKS);if (port==null) continue;
            var content=port.getFluidInTank(0);if (!content.isEmpty() && (requested.isEmpty() || FluidThermal.compatible(content,requested))) { var out=port.drain(amount,action);if (!out.isEmpty()) return out; }
        }
        var storage=storageGasPort();var content=storage.getFluidInTank(0);
        return !content.isEmpty() && (requested.isEmpty() || FluidThermal.compatible(content,requested)) ? storage.drain(amount,action) : FluidStack.EMPTY;
    }
    private FluidStack extractVesselLiquid(FluidStack requested,int amount,FluidAction action) {
        if (isRemoved() || !isController() || amount<=0) return FluidStack.EMPTY;
        for (int i=reactor()==null || module()==ReactorModule.STIRRED ? 0 : 2;i<PROCESS_TANKS;i++) {
            var liquid=processLiquidContent(i);
            if (!liquid.isEmpty() && (requested.isEmpty() || FluidThermal.compatible(liquid,requested))) return drainProcessLiquid(i,amount,action);
        }
        return FluidStack.EMPTY;
    }
    private static IFluidHandler guarded(IFluidHandler handler,java.util.function.BooleanSupplier available) {
        return new IFluidHandler() {
            @Override public int getTanks() { return handler.getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) { return available.getAsBoolean() ? handler.getFluidInTank(tank) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return available.getAsBoolean() ? handler.getTankCapacity(tank) : 0; }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { return available.getAsBoolean() && handler.isFluidValid(tank,stack); }
            @Override public int fill(FluidStack stack,FluidAction action) { return available.getAsBoolean() ? handler.fill(stack,action) : 0; }
            @Override public FluidStack drain(int amount,FluidAction action) { return available.getAsBoolean() ? handler.drain(amount,action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return available.getAsBoolean() ? handler.drain(stack,action) : FluidStack.EMPTY; }
        };
    }
    private IFluidHandler storagePhasePort(Direction side,java.util.function.BooleanSupplier available) {
        var delegate=GradeFluidTank.port(this,side,new FluidTank(0),available);
        return new IFluidHandler() {
            @Override public int getTanks() { return 2; }
            @Override public FluidStack getFluidInTank(int tank) {
                var owner=getControllerBE();if (!available.getAsBoolean() || owner==null || tank<0 || tank>1) return FluidStack.EMPTY;
                var stored=owner.tankInventory;
                return owner.physicalPhase(tank==1);
            }
            @Override public int getTankCapacity(int tank) { var owner=getControllerBE();return owner!=null && tank>=0 && tank<2 ? owner.tankInventory.volumeCapacity() : 0; }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { return tank>=0 && tank<2 && delegate.isFluidValid(0,stack); }
            @Override public int fill(FluidStack stack,FluidAction action) { return delegate.fill(stack,action); }
            @Override public FluidStack drain(int amount,FluidAction action) { return delegate.drain(amount,action); }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return delegate.drain(stack,action); }
        };
    }
    static IFluidHandler combined(IFluidHandler first,IFluidHandler second) {
        return new IFluidHandler() {
            @Override public int getTanks() { return first.getTanks()+second.getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) { int n=first.getTanks();return tank<n ? first.getFluidInTank(tank) : second.getFluidInTank(tank-n); }
            @Override public int getTankCapacity(int tank) { int n=first.getTanks();return tank<n ? first.getTankCapacity(tank) : second.getTankCapacity(tank-n); }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { int n=first.getTanks();return tank<n ? first.isFluidValid(tank,stack) : second.isFluidValid(tank-n,stack); }
            @Override public int fill(FluidStack stack,FluidAction action) { int n=first.fill(stack,action);return n+(n<stack.getAmount() ? second.fill(net.createplant.mixture.MixtureHelper.remainder(stack,n),action) : 0); }
            @Override public FluidStack drain(int amount,FluidAction action) { var out=first.drain(amount,action);return out.isEmpty() ? second.drain(amount,action) : out; }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { var out=first.drain(stack,action);return out.isEmpty() ? second.drain(stack,action) : out; }
        };
    }
    /** The sealed physical tank's gas space, independently from the seven process buffers. */
    public IFluidHandler storageGasPort() {
        FluidStack initial=tankInventory.getFluid().copy();
        return new IFluidHandler() {
            private boolean live() { return !isRemoved() && isController() && !open(); }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return !live() || tank!=0 ? FluidStack.EMPTY : physicalPhase(true); }
            @Override public int getTankCapacity(int tank) { return tank==0 ? tankInventory.volumeCapacity() : 0; }
            @Override public boolean isFluidValid(int tank,FluidStack stack) {
                boolean accepts=module()==ReactorModule.NONE || module()==ReactorModule.STIRRED || !initial.isEmpty() && (FluidThermal.compatible(initial,stack));
                return tank==0 && live() && accepts && !stack.isEmpty() && FluidMaterials.gas(stack.getFluid()) && tankInventory.fill(stack,FluidAction.SIMULATE)>0;
            }
            @Override public int fill(FluidStack stack,FluidAction action) { return isFluidValid(0,stack) ? tankInventory.fill(stack,action) : 0; }
            @Override public FluidStack drain(int amount,FluidAction action) { return !live() ? FluidStack.EMPTY : tankInventory.gasStored() ? tankInventory.drain(amount,action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return FluidThermal.compatible(getFluidInTank(0),stack) ? drain(stack.getAmount(),action) : FluidStack.EMPTY; }
        };
    }
}
