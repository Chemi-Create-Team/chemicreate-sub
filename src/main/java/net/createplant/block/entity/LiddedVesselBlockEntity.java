package net.createplant.block.entity;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.createplant.chemistry.ReactorModule;
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
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.GradeFluidTank;
import net.createplant.thermal.TankLayers;
import net.createplant.mixture.MixtureHelper;
import net.createplant.mixture.MixtureTransfer;
import net.createplant.mixture.GasPassThrough;
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
 * A 3x3 vessel two or more layers high runs the stirred process when a stirrer is mounted on the roof centre.
 */
public class LiddedVesselBlockEntity extends ReactorMachineBlockEntity implements IMultiBlockEntityContainer.Fluid {
    /** Reinforced lidded vessels share Create's 16 bar gameplay pressure rating. */
    private final GradeFluidTank tankInventory;
    /** The minimum stirred height and number of parallel batches. */
    public static final int IMPELLER_LAYERS = 2;
    private BlockPos controller, lastKnownPos;
    private int width = 1, height = 1;
    private boolean updateConnectivity, refreshing, connectionsPending;
    /** Whether a stirrer is mounted on the roof centre. Kept by the controller. */
    private boolean stirrer;
    private BlockPos checkedStirrer;
    private LerpedFloat fluidLevel = LerpedFloat.linear().startWithValue(0);
    private final LerpedFloat lid;
    /** An impeller or reaction tray fitted before stirrers replaced them; it is dropped once the vessel loads. */
    private ItemStack internal = ItemStack.EMPTY;
    private boolean windows;
    private final FluidStack[] storageDisplay = {FluidStack.EMPTY,FluidStack.EMPTY};
    private final FluidStack[] liquidDisplay=new FluidStack[12],gasDisplay=new FluidStack[12];
    private List<FluidStack> liquidLayers = List.of(), gasLayers = List.of();
    private List<GasLayer> gasDraws = List.of();
    private static final int STORAGE_GAS = -1, PASS_THROUGH_GAS = -2, ATMOSPHERIC = net.createplant.thermal.FluidState.pressureLevel(1);
    private record GasLayer(int bank, FluidStack fluid) { }
    private final net.createplant.thermal.TankHeating heating=new net.createplant.thermal.TankHeating();
    /** The game time of the last heating pass, so a pass made between the regular ones is charged only the ticks since. */
    private long lastHeated=Long.MIN_VALUE;

    public LiddedVesselBlockEntity(BlockPos pos, BlockState state) { this(CETransport.LIDDED_VESSEL_ENTITY.get(), pos, state); }

    public LiddedVesselBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = new GradeFluidTank(getCapacityMultiplier(), this::onFluidStackChanged, this);
        lid = LerpedFloat.linear().startWithValue(open(state) ? 1 : 0);
    }

    private static boolean open(BlockState state) { return state.hasProperty(LiddedVesselBlock.OPEN) && state.getValue(LiddedVesselBlock.OPEN); }

    /** The hinged storage hatch, used by the storage pressure and pipe rules. */
    public boolean open() { return !bored() && open(state()); }

    /** The round bore is always open for manual loading. */
    public boolean openTop() { return bored() || open(state()); }

    /** Only a 3x3 stirred tank has the round open bore, with or without its stirrer; smaller ones are plain tanks. */
    public boolean bored() {
        var owner = level == null ? this : getControllerBE();
        return owner != null && owner.getType() == CETransport.STIRRED_TANK_ENTITY.get() && owner.width == 3;
    }

    /** Like a mechanical mixer over a basin: bits of the charge and splashes of the liquid swirl at the surface while a batch runs. */
    private void mixingParticles() {
        var random = level.random;
        if (random.nextInt(3) != 0) return;
        var liquids = liquidLayers();
        long amount = liquids.stream().mapToLong(FluidStack::getAmount).sum();
        float inset = 2 / 16f, surface = inset + Math.min(1, (float) amount / getDisplayedCapacity()) * displayedFillScale() * (height - 2 * inset);
        var items = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < displayedItemSlots(); slot++) if (!displayedItem(slot).isEmpty()) items.add(displayedItem(slot));
        net.minecraft.core.particles.ParticleOptions particle;
        if (!items.isEmpty() && (liquids.isEmpty() || random.nextBoolean()))
            particle = new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, items.get(random.nextInt(items.size())));
        else if (!liquids.isEmpty()) particle = com.simibubi.create.content.fluids.FluidFX.getFluidParticle(liquids.getLast());
        else return;
        double angle = random.nextDouble() * Math.PI * 2, radius = .25 + random.nextDouble() * .7, spin = Math.signum(rotationSpeed()) * .08;
        level.addParticle(particle, worldPosition.getX() + 1.5 + Math.cos(angle) * radius, worldPosition.getY() + surface + .05, worldPosition.getZ() + 1.5 + Math.sin(angle) * radius,
            -Math.sin(angle) * spin, .06, Math.cos(angle) * spin);
    }

    /** A stirred tank's feed and product buffers each match the tank's own volume. */
    @Override public int liquidBufferCapacity() {
        var owner = level == null ? this : getControllerBE();
        return owner != null && owner.bored() ? owner.tankInventory.getCapacity() : super.liquidBufferCapacity();
    }

    /** In a stirred tank the storage and every buffer share the bore's one volume. */
    @Override protected int sharedLiquidLimit() {
        var owner = level == null ? this : getControllerBE();
        return owner != null && owner.bored() ? owner.tankInventory.getCapacity() : super.sharedLiquidLimit();
    }

    /** A stirred tank holds up to four stacks of feed, like a basin. */
    @Override protected int feedSlots() { return getType() == CETransport.STIRRED_TANK_ENTITY.get() ? 4 : super.feedSlots(); }

    /** The drawn liquid in a bored tank tops out at 80 % of the bore, leaving headspace. */
    public float displayedFillScale() { return bored() ? .8f : 1; }

    /** Capacity of a 3x3 bore {@code height} blocks high: its round volume, dished floor included, at one full block per tank block. */
    static int boreCapacity(int height) {
        double pixels = Math.PI * LiddedVesselBlock.BORE_RADIUS * LiddedVesselBlock.BORE_RADIUS * (16 * height - LiddedVesselBlock.DISH_RIM_HEIGHT);
        for (int y = LiddedVesselBlock.DISH_FLOOR_HEIGHT; y < LiddedVesselBlock.DISH_RIM_HEIGHT; y++) pixels += Math.PI * LiddedVesselBlock.boreRadius(y) * LiddedVesselBlock.boreRadius(y);
        return (int) (pixels * getCapacityMultiplier() / 4096 / 1000) * 1000;
    }

    private int capacity(int blocks) {
        blocks = Math.max(1, blocks);
        return getType() == CETransport.STIRRED_TANK_ENTITY.get() && blocks % 9 == 0 ? boreCapacity(blocks / 9) : blocks * getCapacityMultiplier();
    }

    public Direction hinge() { return state().hasProperty(LiddedVesselBlock.HINGE) ? state().getValue(LiddedVesselBlock.HINGE) : Direction.NORTH; }

    /** The block state in the world: while a block is being placed, the cached one is reset after joining has already updated it. */
    private BlockState state() { return level == null ? getBlockState() : level.getBlockState(worldPosition); }

    /** 0 closed to 1 fully open, eased on the client. */
    public float lidOpening(float partialTicks) { return lid.getValue(partialTicks); }








    public float frostLevel() {
        var owner=getControllerBE();if(owner==null)return 0;
        if(!owner.tankInventory.isEmpty() && net.createplant.transport.FluidHazards.of(owner.tankInventory.getFluid()).heat().ordinal()<=1)return 1;
        for(int i=0;i<PROCESS_TANKS;i++)if(!owner.climateFluid(i).isEmpty() && net.createplant.transport.FluidHazards.of(owner.climateFluid(i)).heat().ordinal()<=1)return 1;
        return 0;
    }
    public boolean maintenanceSafe() {
        return rotationSpeed()==0 && !currentBurnerHeat().isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED);
    }
    public IFluidHandler processGasPort(Direction side,int bank) { return processGasPort(bank); }


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
        if (sharedLiquidLimit()!=Integer.MAX_VALUE) return Math.max(1,capacity);
        for (int i=0;i<PROCESS_TANKS;i++) if (!liquidDisplay[i==THIRD_FEED ? 10 : i].isEmpty()) capacity+=liquidBufferCapacity();
        return Math.max(1,capacity);
    }
    @Override public int getDisplayedCapacity(int slot) { return slot==11 || slot==9 ? 0 : slot>=7 && slot<9 ? tankInventory.volumeCapacity() : super.getDisplayedCapacity(slot==10 ? THIRD_FEED : slot); }
    @Override protected void refreshDisplay() {
        super.refreshDisplay();
        storageDisplay[0]=physicalPhase(false);storageDisplay[1]=physicalPhase(true);
        for (int i=0;i<PROCESS_TANKS;i++) { int display=i==THIRD_FEED ? 10 : i;liquidDisplay[display]=processLiquidContent(i);gasDisplay[display]=processGasContent(i); }
        liquidDisplay[11]=FluidStack.EMPTY;gasDisplay[11]=FluidStack.EMPTY;
        liquidDisplay[7]=storageDisplay[0];liquidDisplay[8]=FluidStack.EMPTY;gasDisplay[7]=FluidStack.EMPTY;gasDisplay[8]=storageDisplay[1];
        liquidLayers = TankLayers.ordered(java.util.Arrays.asList(liquidDisplay));
        var draws = new ArrayList<GasLayer>();
        var vapour = new ArrayList<FluidStack>();
        for (int i = 0; i < PROCESS_TANKS; i++) {
            var gas = gasDisplay[i == THIRD_FEED ? 10 : i];
            if (!gas.isEmpty()) {
                // Reactant gases wait for their batch; only products and stored gas leave through the outlets.
                if (!inputTank(i)) draws.add(new GasLayer(i, gas));
                vapour.add(gas);
            }
        }
        if (!storageDisplay[1].isEmpty()) {
            draws.add(new GasLayer(STORAGE_GAS, storageDisplay[1]));
            vapour.add(storageDisplay[1]);
        }
        var pass = GasPassThrough.of(this);
        if (pass != null && !pass.gas().isEmpty()) {
            draws.add(new GasLayer(PASS_THROUGH_GAS, pass.gas()));
            vapour.add(pass.gas());
        }
        draws.sort(Comparator.comparingDouble((GasLayer layer) -> TankLayers.density(layer.fluid())).reversed());
        gasDraws = List.copyOf(draws);
        gasLayers = TankLayers.ordered(vapour);
    }
    /** The gas in the process buffers and waiting to pass through, which shares the vessel's volume with its stored gas. */
    public long gasOutsideStorage() {
        var pass=GasPassThrough.of(this);
        return processGas()+(pass==null ? 0 : pass.gas().getAmount());
    }
    /** The process gas fits in the vessel's volume beside the stored gas and the gas waiting to pass through. */
    @Override protected int processGasLimit() {
        return Math.max(0,batchGasLimit()-(tankInventory.gasStored() ? tankInventory.getFluidAmount() : 0));
    }
    @Override protected int batchGasLimit() {
        var pass=GasPassThrough.of(this);
        return Math.max(0,tankInventory.gasCapacity()-(pass==null ? 0 : pass.gas().getAmount()));
    }
    public List<FluidStack> liquidLayers() { ensureDisplay();return liquidLayers; }
    public List<FluidStack> gasLayers() { ensureDisplay();return gasLayers; }
    public boolean showGasComposition() { return hasStoredGas() || liquidLayers().isEmpty(); }

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
        super.commitMixingFluids(fluids);
        if (!FluidStack.matches(tankInventory.getFluid(),fluids[8])) tankInventory.setFluid(fluids[8]);
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
        tankInventory.setCapacity(capacity(blocks));
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
        var owner = level == null ? null : getControllerBE();
        return owner != null && owner.width == 3 && owner.height >= IMPELLER_LAYERS && owner.stirrer ? ReactorModule.STIRRED : ReactorModule.NONE;
    }
    public boolean stirred() { return module() == ReactorModule.STIRRED; }
    /** The stirrer sits directly on the centre of the top layer. Called on the controller. */
    public BlockPos stirrerPos() { return top().above(); }

    private void refreshStirrer() {
        if (level == null || level.isClientSide) return;
        var previous = checkedStirrer;
        var at = stirrerPos();
        // Only a stirred tank stirs; a stirrer on a stainless tank stays idle.
        boolean found = getType() == CETransport.STIRRED_TANK_ENTITY.get() && isController() && width == 3 && height >= IMPELLER_LAYERS && level.hasChunkAt(at) && level.getBlockState(at).is(CEReactor.STIRRER.get());
        if (found != stirrer) {
            stirrer = found;
            sendData();
        }
        checkedStirrer = found ? at : null;
        if (previous != null && !previous.equals(checkedStirrer)) refreshStirrerStress(previous);
        if (found) refreshStirrerStress(at);
    }

    private void refreshStirrerStress(BlockPos pos) {
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof VesselStirrerBlockEntity drive) drive.refreshStress();
    }
    @Override protected boolean processController() { return isController() && module() != ReactorModule.NONE; }
    /** The stirrer's speed; the vessel itself takes no rotation. */
    @Override public float rotationSpeed() {
        var owner = level == null ? null : getControllerBE();
        if (owner == null || !owner.stirred()) return 0;
        var at = owner.stirrerPos();
        return level.hasChunkAt(at) && level.getBlockEntity(at) instanceof VesselStirrerBlockEntity drive ? drive.getSpeed() : 0;
    }
    @Override public float calculateStressApplied() { return lastStressApplied = 0; }
    /** Called when the vessel is resized or the block over its top centre changes. */
    @Override public void invalidateStructure() {
        super.invalidateStructure();
        if (refreshing) return;
        refreshing = true;
        try { refreshStirrer(); } finally { refreshing = false; }
    }

    @Override public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) { connectionsPending = true;refreshConnections();refreshStirrer(); }
    }

    @Override public void lazyTick() { super.lazyTick();refreshConnections(); }

    private void refreshConnections() {
        if (!connectionsPending || level == null || level.isClientSide || isRemoved() || !level.hasChunkAt(getController())) return;
        var owner = getControllerBE();if (owner == null || !owner.isController()) return;
        int x = worldPosition.getX() - owner.worldPosition.getX(), z = worldPosition.getZ() - owner.worldPosition.getZ();
        if (x < 0 || x >= owner.width || z < 0 || z >= owner.width || worldPosition.getY() < owner.worldPosition.getY()
            || worldPosition.getY() >= owner.worldPosition.getY() + owner.height) return;
        var current = state();if (!(current.getBlock() instanceof LiddedVesselBlock)) return;
        connectionsPending = false;
        boolean expected = ladder(x, z, owner.width, owner.hinge());
        var next=current.setValue(LiddedVesselBlock.LADDER, expected).setValue(LiddedVesselBlock.DRIVE, owner.width == 3).setValue(LiddedVesselBlock.AXIS, Direction.Axis.Y);
        if(owner.width==1)next=next.setValue(LiddedVesselBlock.HATCH,LiddedVesselBlock.Hatch.NONE).setValue(LiddedVesselBlock.OPEN,false);
        setState(next);
        if (hasSource() || getTheoreticalSpeed() != 0) { detachKinetics();removeSource();sendData(); }
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this));
    }

    /** A stirred tank stands at most three blocks high. */
    public static final int STIRRED_MAX_HEIGHT = 3;
    @Override public int getMaxLength(Direction.Axis axis, int width) { return axis == Direction.Axis.Y ? getType() == CETransport.STIRRED_TANK_ENTITY.get() ? STIRRED_MAX_HEIGHT : ReactorStructure.MAX_SECTIONS : getMaxWidth(); }

    @Override public void tick() {
        if (level != null && !level.isClientSide && updateConnectivity && isController()) {
            updateConnectivity = false;ConnectivityHandler.formMulti(this);
        }
        if (level!=null && !level.isClientSide && !internal.isEmpty()) dropInternal();
        super.tick();
        if (level == null || !isController()) return;
        if (!level.isClientSide && !isRemoved() && module()==ReactorModule.NONE && ReactorStructure.loaded(level,worldPosition))
            tickHeatOnlyReactions(height,!tankInventory.isEmpty());
        if (!level.isClientSide && isController() && (heating.boiling() || Math.floorMod(level.getGameTime()+worldPosition.asLong(),net.createplant.thermal.TankHeating.INTERVAL)==0)) heatContents();
        if (!level.isClientSide && Math.floorMod(level.getGameTime()+worldPosition.asLong(),5)==0) updateDisplayedHeat();
        if (lastKnownPos == null) lastKnownPos = worldPosition;
        if (!lastKnownPos.equals(worldPosition)) { removeController(true);lastKnownPos = worldPosition; }
        fluidLevel.tickChaser();
        if (level.isClientSide) {
            lid.chase(open() ? 1 : 0, .12, LerpedFloat.Chaser.LINEAR);
            lid.tickChaser();
            if (mixing() && bored()) mixingParticles();
            return;
        }
    }

    private java.util.List<FluidStack> heatFluids() {
        var fluids=new java.util.ArrayList<FluidStack>();fluids.add(tankInventory.getFluid());
        for(int i=0;i<PROCESS_TANKS;i++)fluids.add(climateFluid(i));
        return fluids;
    }

    @Override protected double contentsKelvin() {
        var heat=new java.util.ArrayList<net.createplant.thermal.HeatContent>();
        heat.add(tankInventory.heat());
        return heating.meanKelvin(heatFluids(),heat);
    }

    @Override protected void releaseHeat(double joules) {
        if(joules==0 || level==null)return;
        var heat=new java.util.ArrayList<net.createplant.thermal.HeatContent>();
        heat.add(tankInventory.heat());
        heating.boilSolutions(canBoilSolutions());heating.boilWater(canBoilWater());
        int result=heating.react(heatFluids(),joules,ignored->Integer.MAX_VALUE,heat);
        if(result==net.createplant.thermal.TankHeating.CHANGED) { onFluidStackChanged(tankInventory.getFluid());changed(); }
        else if(result==net.createplant.thermal.TankHeating.KELVIN)setChanged();
    }

    private void heatContents() {
        var fluids=heatFluids();
        var base=new java.util.ArrayList<BlockPos>();
        for(int x=0;x<width;x++)for(int z=0;z<width;z++)base.add(worldPosition.offset(x,0,z));
        // The main inventory keeps its own heat state; the process buffers are tracked by the heater.
        var heat=new java.util.ArrayList<net.createplant.thermal.HeatContent>();
        heat.add(tankInventory instanceof net.createplant.thermal.ThermalFluidTank thermal ? thermal.heat() : null);
        // Storage tanks can concentrate a solution. Stirred vessels use their recipes to process it.
        if(open())vent();
        heating.boilSolutions(canBoilSolutions());heating.boilWater(canBoilWater());
        heating.steamRoom(open() ? Integer.MAX_VALUE : steamRoom());
        long now=level.getGameTime();
        int ticks=lastHeated==Long.MIN_VALUE ? net.createplant.thermal.TankHeating.INTERVAL : (int)Math.clamp(now-lastHeated,1,net.createplant.thermal.TankHeating.INTERVAL);
        lastHeated=now;
        int result=heating.tick(fluids,net.createplant.thermal.TankHeating.burnerWatts(level,base),ignored->Integer.MAX_VALUE,heat,ticks);
        int steam=heating.takeSteam();
        if(steam>0) {
            if(open()) {
                var top=top();
                ((ServerLevel)level).sendParticles(ParticleTypes.CLOUD,top.getX()+.5,top.getY()+1,top.getZ()+.5,Math.min(12,1+steam/20),.3,.2,.3,.04);
                if(now%10==0)level.playSound(null,top,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.3f,1.4f);
            } else storeSteam(steam);
        }
        if(result==net.createplant.thermal.TankHeating.CHANGED) {
            onFluidStackChanged(tankInventory.getFluid());changed();
        } else if(result==net.createplant.thermal.TankHeating.KELVIN)setChanged();
    }

    /** Water boils off a mB at a time unless a batch is running or another feed is waiting; a stirred vessel with only water in it is just a kettle. */
    private boolean canBoilWater() { return !processingStirredBatch() && feedsWaterOnly(); }
    private boolean canBoilSolutions() { return !stirred() && !processingStirredBatch() && feedsEmpty(); }

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

    @Override public void removeController(boolean keepFluids) {
        if (level == null || level.isClientSide) return;
        controller = null;updateConnectivity = true;
        width = 1;height = 1;stirrer = false;
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

    public void toggleLid() { var owner = getControllerBE();if (owner != null && !owner.bored()) owner.setLid(!owner.open()); }

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

    /**
     * An open vessel holds nothing above atmospheric pressure, and no gas lighter than air: it leaves through the top, toxic ones as a cloud.
     * A gas heavier than air settles and stays, down to what fills the vessel at atmospheric pressure.
     */
    void vent() {
        if(level==null || level.isClientSide)return;
        var pass = GasPassThrough.of(this);
        if (pass != null) pass.vent();
        boolean letOut=false;
        for(var released:removeProcessGases(net.createplant.thermal.FluidMaterials::heavierThanAir)) {
            ToxicGas.release(level,top(),released);letOut=true;
        }
        if(letOut)((ServerLevel)level).sendParticles(ParticleTypes.CLOUD,top().getX()+.5,top().getY()+1,top().getZ()+.5,8,.3,.2,.3,.04);
        var fluid=tankInventory.getFluid();
        if(fluid.isEmpty() || !net.createplant.transport.FluidHazards.of(fluid).gas())return;
        int kept=net.createplant.thermal.FluidMaterials.heavierThanAir(fluid) ? Math.min(fluid.getAmount(),tankInventory.volumeCapacity()) : 0;
        if(kept==fluid.getAmount() && net.createplant.thermal.FluidState.pressure(fluid)<=ATMOSPHERIC)return;
        var released=tankInventory.drain(fluid.getAmount()-kept,FluidAction.EXECUTE);
        if(kept>0){var settled=tankInventory.getFluid().copy();net.createplant.thermal.FluidState.setPressure(settled,ATMOSPHERIC);tankInventory.setFluid(settled);}
        if(released.isEmpty())return;
        ToxicGas.release(level,top(),released);
        ((ServerLevel)level).sendParticles(ParticleTypes.CLOUD,top().getX()+.5,top().getY()+.5,top().getZ()+.5,8,.3,.3,.3,.05);
    }

    /** The lid is thrown back with a bang and the jet lifts whatever stands over the opening. */


    public void dropInternal() {
        if (internal.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, internal);
        internal = ItemStack.EMPTY;setChanged();
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
        if (owner.showGasComposition() && net.createplant.mixture.MixtureGoggleHelper.appendGasLayers(tooltip, owner.gasLayers(), owner.tankInventory.gasCapacity())) return true;
        if (net.createplant.mixture.MixtureHelper.isMixture(owner.tankInventory.getFluid())) {
            net.createplant.mixture.MixtureGoggleHelper.appendSummary(tooltip, owner.tankInventory.getFluid());return true;
        }
        String key="lidded_vessel.create_refinery_n_reactors.";
        var status=Component.translatable(owner.module()==ReactorModule.NONE && !owner.running() && !owner.status().equals("heat") ? key+"storage" : "reactor.create_refinery_n_reactors.status."+owner.status());
        tooltip.add(Component.translatable(key+"summary",status,Component.translatable(key+"lid."+(owner.width==1 ? "fixed" : owner.openTop() ? "open" : "closed")),owner.width,owner.height));
        var heat=owner.currentBurnerHeat();
        var condition=heat==com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING ? com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED : heat.isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED) ? com.simibubi.create.content.processing.recipe.HeatCondition.HEATED : com.simibubi.create.content.processing.recipe.HeatCondition.NONE;
        tooltip.add(Component.translatable("create."+condition.getTranslationKey()));
        long amount=owner.tankInventory.getFluidAmount();for(int i=0;i<PROCESS_TANKS;i++)amount+=owner.climateFluid(i).getAmount();
        tooltip.add(Component.translatable(key+"operation",Math.round(Math.abs(owner.rotationSpeed())),Math.round(owner.progress()*100/Math.max(1,owner.cycleTicks())),amount));
        var stock=owner.stockLine(owner.tankInventory.getFluid());
        if (stock!=null) tooltip.add(stock);
        return true;
    }

    /** The open hatch stands up one block over the top. */
    @Override protected AABB createRenderBoundingBox() { return isController() ? new AABB(worldPosition).expandTowards(width - 1, height, width - 1).expandTowards(0,-.125,0) : super.createRenderBoundingBox(); }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        if (controller != null && !isController()) tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putInt("Size", width);tag.putInt("Height", height);
        if (lastKnownPos != null) tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (isController()) {
            tag.put("TankContent", tankInventory.writeToNBT(lookup, new CompoundTag()));
        }
        if(!clientPacket)heating.write(tag,lookup);
        if (updateConnectivity) tag.putBoolean("Uninitialized", true);
        if (stirrer) tag.putBoolean("Stirrer", true);
        if (!internal.isEmpty()) tag.put("Internal", internal.save(lookup));
        tag.putBoolean("Windows",windows);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        int oldWidth=width,oldHeight=height;boolean oldWindows=windows;
        controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag, "Controller").orElse(null) : null;
        width = Math.clamp(tag.getInt("Size"), 1, 3);height = Math.clamp(tag.getInt("Height"), 1, ReactorStructure.MAX_SECTIONS);
        lastKnownPos = tag.contains("LastKnownPos") ? NbtUtils.readBlockPos(tag, "LastKnownPos").orElse(null) : null;
        updateConnectivity = tag.getBoolean("Uninitialized");
        tankInventory.setCapacity(capacity(getTotalTankSize()));
        if (isController()) {
            tankInventory.readFromNBT(lookup, tag.getCompound("TankContent"));
            if (tankInventory.getSpace() < 0) tankInventory.drain(-tankInventory.getSpace(), FluidAction.EXECUTE);
        }
        stirrer = getType() == CETransport.STIRRED_TANK_ENTITY.get() && tag.getBoolean("Stirrer");
        super.read(tag, lookup, clientPacket);
        if(!clientPacket)heating.read(tag,lookup);
        if (clientPacket) fluidLevel.chase(getFillState(), .5, LerpedFloat.Chaser.EXP);
        else fluidLevel = LerpedFloat.linear().startWithValue(getFillState());
        internal = tag.contains("Internal") ? ItemStack.parseOptional(lookup, tag.getCompound("Internal")) : ItemStack.EMPTY;
        windows=tag.getBoolean("Windows");
        if (!clientPacket) connectionsPending = true;
        if (clientPacket && (oldWindows!=windows || oldWidth!=width || oldHeight!=height)) updateWindowModel(oldWidth,oldHeight);
    }

    /** Once a stirred process forms, pipes and funnels on any face reach its buffers as well as the vessel's storage tank. */
    public static void register(RegisterCapabilitiesEvent event) {
        for (var type : java.util.List.of(CETransport.LIDDED_VESSEL_ENTITY.get(), CETransport.STIRRED_TANK_ENTITY.get())) register(event, type);
    }

    private static void register(RegisterCapabilitiesEvent event, net.minecraft.world.level.block.entity.BlockEntityType<LiddedVesselBlockEntity> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> {
            if (side==Direction.UP && be.underOpenHatch()) return null;
            var owner=be.getControllerBE();if (owner==null) return null;
            var reactor = be.reactor();
            IFluidHandler handler;
            if (reactor!=null) {
                int generation=owner.portGeneration();
                var storage=be.storagePhasePort(side,()->be.getControllerBE()==owner && owner.portGeneration()==generation && owner.formed() && owner.module()==ReactorModule.STIRRED && !(side==Direction.UP && be.underOpenHatch()));
                handler=combined(reactor.port(side),storage);
            } else {
                var storage=GradeFluidTank.port(be, side, new FluidTank(0),
                () -> be.getControllerBE() != null && be.reactor() == null && !(side == Direction.UP && be.underOpenHatch()),
                () -> be.getControllerBE() != null && !be.getControllerBE().stirred());
                var recovery=guarded(owner.processRecoveryPort(),()->be.getControllerBE()==owner && be.reactor()==null);
                handler=combined(storage,recovery);
            }
            if (reactor!=null) handler=net.createplant.mixture.GasMixturePort.outlet(handler,owner);
            if (handler==null) return null;
            var delegate=handler;int lease=owner.portGeneration();
            return new IFluidHandler() {
                private boolean live() {
                    if (be.isRemoved() || be.getLevel()==null || !be.getLevel().hasChunkAt(be.getBlockPos()) || !(be.getLevel().getBlockState(be.getBlockPos()).getBlock() instanceof LiddedVesselBlock) || be.getControllerBE()!=owner || side==Direction.UP && be.underOpenHatch()) return false;
                    return reactor==null ? be.reactor()==null : owner.portGeneration()==lease && owner.formed() && ReactorStructure.loaded(be.getLevel(),owner.getBlockPos());
                }
                private boolean gasAllowed(FluidStack stack) { var current=be.getControllerBE();return current!=null && (!current.open() || stack.isEmpty() || !net.createplant.thermal.FluidState.gas(stack)); }
                private boolean stratified() { return !owner.open() && (owner.module() == ReactorModule.NONE || owner.module() == ReactorModule.STIRRED); }
                private boolean lowGasOutlet() {
                    return side != null && side != Direction.UP && stratified() && owner.hasStoredGas()
                        && (owner.liquidLayers().isEmpty() || side != Direction.DOWN && upperGasOutlet() && owner.getHeight() > 1);
                }
                private boolean upperGasOutlet() { return side == Direction.UP || side != Direction.DOWN && be.getBlockPos().getY() - owner.getBlockPos().getY() >= Math.max(1, owner.getHeight() / 2); }
                private FluidStack upperLiquid() {
                    return MixtureHelper.isMixture(owner.tankInventory.getFluid()) ? owner.tankInventory.layer(Direction.UP) : FluidStack.EMPTY;
                }
                @Override public int getTanks() { return delegate.getTanks(); }
                @Override public FluidStack getFluidInTank(int tank) {
                    if (!live()) return FluidStack.EMPTY;
                    if (stratified() && side == Direction.UP) {
                        if (tank != 0) return FluidStack.EMPTY;
                        var gas = owner.previewVesselGas(true);
                        var liquid = upperLiquid();
                        return !liquid.isEmpty() && !owner.hasStoredGas() ? liquid : gas;
                    }
                    if (lowGasOutlet()) return tank == 0 ? owner.previewVesselGas(upperGasOutlet()) : FluidStack.EMPTY;
                    var stack=delegate.getFluidInTank(tank);
                    return side!=null && !stack.isEmpty() && net.createplant.thermal.FluidState.gas(stack)!=(side==Direction.UP) ? FluidStack.EMPTY : stack;
                }
                @Override public int getTankCapacity(int tank) { return !live() ? 0 : delegate.getTankCapacity(tank); }
                @Override public boolean isFluidValid(int tank,FluidStack stack) { return live() && gasAllowed(stack) && delegate.isFluidValid(tank,stack); }
                @Override public int fill(FluidStack stack,FluidAction action) { return live() && gasAllowed(stack) ? delegate.fill(stack,action) : 0; }
                @Override public FluidStack drain(int amount,FluidAction action) {
                    if (!live()) return FluidStack.EMPTY;
                    if (stratified() && (side == Direction.UP || lowGasOutlet())) {
                        if (side == Direction.UP && !upperLiquid().isEmpty() && !owner.hasStoredGas())
                            return owner.tankInventory.drainLayer(Direction.UP, FluidStack.EMPTY, amount, action);
                        return owner.extractLayerGas(upperGasOutlet(), FluidStack.EMPTY, amount, action);
                    }
                    if (side==Direction.UP) return owner.extractVesselGas(FluidStack.EMPTY,amount,action);
                    if (side==null) return delegate.drain(amount,action);
                    var phased=owner.extractVesselLiquid(FluidStack.EMPTY,amount,action);if (!phased.isEmpty()) return phased;
                    for (int i=0;i<delegate.getTanks();i++) { var stack=getFluidInTank(i);if (!stack.isEmpty() && !net.createplant.thermal.FluidState.gas(stack)) { var out=delegate.drain(stack.copyWithAmount(Math.min(amount,stack.getAmount())),action);if (!out.isEmpty()) return out; } }
                    return FluidStack.EMPTY;
                }
                @Override public FluidStack drain(FluidStack stack,FluidAction action) {
                    if (!live()) return FluidStack.EMPTY;
                    if (stratified() && (side == Direction.UP || lowGasOutlet())) {
                        if (side == Direction.UP && !upperLiquid().isEmpty() && !owner.hasStoredGas())
                            return owner.tankInventory.drainLayer(Direction.UP, stack, stack.getAmount(), action);
                        return owner.extractLayerGas(upperGasOutlet(), stack, stack.getAmount(), action);
                    }
                    if (side==Direction.UP) return owner.extractVesselGas(stack,stack.getAmount(),action);
                    if (side!=null && net.createplant.thermal.FluidState.gas(stack)) return FluidStack.EMPTY;
                    if (side!=null) { var phased=owner.extractVesselLiquid(stack,stack.getAmount(),action);if (!phased.isEmpty()) return phased; }
                    return delegate.drain(stack,action);
                }
            };
        });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> {
            var owner=be.getControllerBE();return owner==null ? null : owner.itemPort();
        });
    }
    private boolean hasStoredGas() { ensureDisplay();return !gasDraws.isEmpty(); }
    private GasLayer gasDraw(boolean upper) {
        ensureDisplay();
        return gasDraws.isEmpty() ? null : upper ? gasDraws.getLast() : gasDraws.getFirst();
    }
    private FluidStack previewVesselGas(boolean upper) {
        var layer = gasDraw(upper);
        return layer == null ? FluidStack.EMPTY : layer.fluid().copy();
    }
    private FluidStack extractLayerGas(boolean upper, FluidStack request, int amount, FluidAction action) {
        if (amount <= 0) return FluidStack.EMPTY;
        var layer = gasDraw(upper);
        if (layer == null) return FluidStack.EMPTY;
        var sampled = layer.fluid();
        if (!request.isEmpty() && !MixtureTransfer.matches(sampled, request)) return FluidStack.EMPTY;
        if (layer.bank() == PASS_THROUGH_GAS) {
            var pass = GasPassThrough.of(this);
            return pass == null ? FluidStack.EMPTY : pass.take(amount, action);
        }
        var port = layer.bank() == STORAGE_GAS ? storageGasPort() : processGasPort(layer.bank());
        return port == null ? FluidStack.EMPTY : port.drain(amount, action);
    }
    private FluidStack extractVesselGas(FluidStack requested,int amount,FluidAction action) {
        if (amount<=0 || isRemoved() || !isController() || open()) return FluidStack.EMPTY;
        for (int i=2;i<PROCESS_TANKS+2;i++) {
            var port=processGasPort(i%PROCESS_TANKS);if (port==null) continue;
            var content=port.getFluidInTank(0);if (!content.isEmpty() && (requested.isEmpty() || MixtureTransfer.matches(content,requested))) { var out=port.drain(amount,action);if (!out.isEmpty()) return out; }
        }
        var storage=storageGasPort();var content=storage.getFluidInTank(0);
        return !content.isEmpty() && (requested.isEmpty() || MixtureTransfer.matches(content,requested)) ? storage.drain(amount,action) : FluidStack.EMPTY;
    }
    private FluidStack extractVesselLiquid(FluidStack requested,int amount,FluidAction action) {
        if (isRemoved() || !isController() || amount<=0) return FluidStack.EMPTY;
        for (int i=reactor()==null || module()==ReactorModule.STIRRED ? 0 : 2;i<PROCESS_TANKS;i++) {
            // A running process keeps its reactant liquids for the batch; only its products leave.
            if (reactor()!=null && inputTank(i)) continue;
            var liquid=processLiquidContent(i);
            if (!liquid.isEmpty() && (requested.isEmpty() || FluidThermal.compatible(liquid,requested))) {
                var drained=drainProcessLiquid(i,amount,action);if (!drained.isEmpty()) return drained;
            }
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
                var phase=owner.physicalPhase(tank==1);
                return tank==0 && !phase.isEmpty() ? delegate.getFluidInTank(0) : phase;
            }
            @Override public int getTankCapacity(int tank) {
                var owner=getControllerBE();if (owner==null || tank<0 || tank>1) return 0;
                return tank==1 ? owner.tankInventory.volumeCapacity() : (int)Math.min(owner.tankInventory.volumeCapacity(),(long)owner.physicalPhase(false).getAmount()+owner.liquidRoom());
            }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { return tank>=0 && tank<2 && delegate.isFluidValid(0,stack); }
            @Override public int fill(FluidStack stack,FluidAction action) {
                var owner=getControllerBE();
                // Liquid only fills what the process buffers leave free of the shared volume.
                if (owner!=null && !stack.isEmpty() && !net.createplant.thermal.FluidState.gas(stack)) stack=stack.copyWithAmount(Math.min(stack.getAmount(),owner.liquidRoom()));
                return stack.isEmpty() ? 0 : delegate.fill(stack,action);
            }
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
                return tank==0 && live() && accepts && !stack.isEmpty() && net.createplant.thermal.FluidState.gas(stack) && tankInventory.fill(stack,FluidAction.SIMULATE)>0;
            }
            @Override public int fill(FluidStack stack,FluidAction action) { return isFluidValid(0,stack) ? tankInventory.fill(stack,action) : 0; }
            @Override public FluidStack drain(int amount,FluidAction action) { return !live() ? FluidStack.EMPTY : tankInventory.gasStored() ? tankInventory.drain(amount,action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return MixtureTransfer.matches(getFluidInTank(0),stack) ? drain(stack.getAmount(),action) : FluidStack.EMPTY; }
        };
    }
}
