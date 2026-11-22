package net.createplant.block.entity;

import java.util.*;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.block.*;
import net.createplant.chemistry.*;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CERecipeTypes;
import net.createplant.thermal.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.*;

/** Each machine owns its buffers. Material moves between machines exclusively through standard fluid handlers. */
public class ReactorMachineBlockEntity extends KineticBlockEntity {
    public static final int CAPACITY=8000;
    /** Keep the original two feeds and five products at their saved indices; the third feed is appended. */
    public static final int THIRD_FEED=7,PROCESS_TANKS=8;
    private static final String KEY="reactor.create_refinery_n_reactors.";
    private final FluidStack[] tanks=new FluidStack[PROCESS_TANKS];
    /** Nine shared basin/feed slots and four product slots. Ordinary storage accepts every item. */
    private static final int FEED_SLOTS=9,CHARGE_SLOTS=13;
    private final ItemStackHandler charge=new ItemStackHandler(CHARGE_SLOTS) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final IItemHandler chargePort=LiveMachinePorts.items(this,new ChargePort());
    private RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> reaction;
    private com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel burnerHeat;
    private HeatLevel displayedHeat = HeatLevel.NONE;
    private boolean displayDirty = true;
    private final ItemStack[] itemDisplay = new ItemStack[CHARGE_SLOTS];
    private final FluidStack[] fluidDisplay = new FluidStack[PROCESS_TANKS];
    private final Object[] gasBanks = java.util.stream.IntStream.range(0,PROCESS_TANKS).mapToObj(i->new Object()).toArray();
    private List<BlockPos> members=List.of();
    private Set<BlockPos> memberSet=Set.of();
    private boolean validating;
    public boolean horizontal() { return module()==ReactorModule.PREHEATER; }
    public boolean owns(BlockPos pos) {
        if (!formed) return false;
        // A client knows its exchanger's casings by the controller they name, not the run it was scanned with.
        if (level!=null && level.isClientSide && exchanger()!=null) return true;
        return memberSet.contains(pos);
    }
    private final FormationEffects.Announcer announcer=new FormationEffects.Announcer();
    private ProcessRecipe recipe;
    private String idle="materials";
    private ThermalEquipment thermal;



    private boolean dirty=true, recipeDirty=true, formed, running, syncNeeded, gasBlocked;
    private int sections, epoch, clock, scale, cycleTicks=100;
    private float progress;
    private String batch="", status="structure";
    private BlockPos fault;
    private long scans;
    public ReactorMachineBlockEntity(BlockPos pos,BlockState state) {
        this(CEReactor.ENTITY.get(),pos,state);
    }
    protected ReactorMachineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state) {
        super(type,pos,state);Arrays.fill(tanks,FluidStack.EMPTY);fault=pos;setLazyTickRate(20);
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }
    /** The kind of fluid heat exchanger this controls, or null. */
    public net.createplant.block.ExchangerStructure.Kind exchanger() { return getBlockState().getBlock() instanceof net.createplant.block.ExchangerControllerBlock block ? block.kind : null; }
    public List<BlockPos> members() { return members; }
    public ThermalEquipment thermal() { if (thermal==null) thermal=new ThermalEquipment(this);return thermal; }
    public int portGeneration() { return epoch; }
    public boolean thermalReady() { return ready(); }
    public void thermalChanged() { syncNeeded=true;setChanged(); }
    public void thermalStatus(String value,boolean work) { setStatus(value,work,true); }
    public ReactorModule module() { return ((ReactorMachineBlock)getBlockState().getBlock()).module; }
    public boolean formed() { return formed; }
    public boolean running() { return running; }
    public int sections() { return sections; }
    public float progress() { return module()==ReactorModule.PREHEATER ? (float)Math.min(1,Math.max(thermal().coldFlow(),thermal().hotFlow())/(double)Math.max(1,thermal().flowPerStep()))*cycleTicks : progress; }
    public int cycleTicks() { return cycleTicks; }
    public String status() { return status; }
    public long scans() { return scans; }
    /** These cached display snapshots are read-only; renderers must never mutate the returned stacks. */
    public int displayedItemSlots() { return CHARGE_SLOTS; }
    public ItemStack displayedItem(int slot) { ensureDisplay();return slot>=0 && slot<itemDisplay.length ? itemDisplay[slot] : ItemStack.EMPTY; }
    public int displayedFluidSlots() { return tanks.length; }
    public FluidStack displayedFluid(int slot) { ensureDisplay();return slot>=0 && slot<fluidDisplay.length ? fluidDisplay[slot] : FluidStack.EMPTY; }
    public int getDisplayedCapacity() { return CAPACITY; }
    public int getDisplayedCapacity(int slot) { return slot>=0 && slot<tanks.length ? bufferCapacity(tanks[slot]) : 0; }
    public boolean mixing() { return module()==ReactorModule.STIRRED && running; }
    public HeatLevel heatLevel() { return displayedHeat; }
    protected final void ensureDisplay() { if (displayDirty) refreshDisplay(); }
    protected void refreshDisplay() {
        for (int i=0;i<CHARGE_SLOTS;i++) itemDisplay[i]=charge.getStackInSlot(i).copy();
        for (int i=0;i<tanks.length;i++) fluidDisplay[i]=tanks[i].copy();
        displayDirty=false;
    }
    protected void updateDisplayedHeat() {
        var heat=burnerHeat();
        if (heat!=displayedHeat) { displayedHeat=heat;changed(); }
    }
    public int inputItemSlots() { return FEED_SLOTS; }
    public ItemStack recoverInputItem(int slot,int amount) { return slot>=0 && slot<FEED_SLOTS && !isRemoved() ? charge.extractItem(slot,amount,false) : ItemStack.EMPTY; }
    public ItemStack recoverStoredItem(int slot,int amount) { return slot>=0 && slot<CHARGE_SLOTS && !isRemoved() ? charge.extractItem(slot,amount,false) : ItemStack.EMPTY; }
    FluidStack climateFluid(int slot) { return tanks[slot]; }
    /** What the process holds and has made, for the goggles: one line with the two largest stocks and how many more there are. */
    Component stockLine(FluidStack storage) {
        var entries=new ArrayList<Component>();var amounts=new ArrayList<Integer>();
        if (!storage.isEmpty()) { entries.add(Component.translatable(KEY+"stock_fluid",storage.getHoverName(),storage.getAmount()));amounts.add(storage.getAmount()); }
        for (var tank:tanks) if (!tank.isEmpty()) { entries.add(Component.translatable(KEY+"stock_fluid",tank.getHoverName(),tank.getAmount()));amounts.add(tank.getAmount()); }
        for (int i=0;i<CHARGE_SLOTS;i++) { var item=charge.getStackInSlot(i);if (!item.isEmpty()) { entries.add(Component.translatable(KEY+"stock_item",item.getHoverName(),item.getCount()));amounts.add(item.getCount()); } }
        if (entries.isEmpty()) return null;
        var order=new ArrayList<Integer>();for (int i=0;i<entries.size();i++) order.add(i);
        order.sort((a,b)->Integer.compare(amounts.get(b),amounts.get(a)));
        var line=Component.empty();
        for (int i=0;i<Math.min(2,order.size());i++) { if (i>0) line.append("  ");line.append(entries.get(order.get(i))); }
        if (order.size()>2) line.append("  ").append(Component.translatable(KEY+"stock_more",order.size()-2));
        return line;
    }
    void climateFluid(int slot,FluidStack stack) { tanks[slot]=stack;changed(); }
    public FluidStack processGasContent(int slot) { return !tanks[slot].isEmpty() && net.createplant.transport.FluidHazards.of(tanks[slot]).gas() ? tanks[slot].copy() : FluidStack.EMPTY; }
    public FluidStack processLiquidContent(int slot) { return !tanks[slot].isEmpty() && !net.createplant.transport.FluidHazards.of(tanks[slot]).gas() ? tanks[slot].copy() : FluidStack.EMPTY; }
    public FluidStack drainProcessLiquid(int bank,int amount,FluidAction action) {
        if (bank<0 || bank>=tanks.length || amount<=0 || isRemoved()) return FluidStack.EMPTY;
        var liquid=processLiquidContent(bank);if (liquid.isEmpty()) return FluidStack.EMPTY;
        var out=liquid.copyWithAmount(Math.min(amount,liquid.getAmount()));
        if (action.execute()) { tanks[bank].shrink(out.getAmount());changed(); }
        return out;
    }
    public HeatLevel currentBurnerHeat() { return burnerHeat(); }
    protected String processInhibit() { return ""; }
    protected float processingSpeedFactor() { return 1; }

    protected void completedStirredBatch(StandardProcessingRecipe<RecipeInput> recipe,int count) { }

    public IFluidHandler processRecoveryPort() {
        return new IFluidHandler() {
            private boolean live() { return !isRemoved() && (!(ReactorMachineBlockEntity.this instanceof LiddedVesselBlockEntity vessel) || vessel.isController()); }
            @Override public int getTanks() { return tanks.length; }
            @Override public FluidStack getFluidInTank(int slot) { return live() && slot>=0 && slot<tanks.length ? tanks[slot].copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int slot) { return slot>=0 && slot<tanks.length ? bufferCapacity(tanks[slot]) : 0; }
            @Override public boolean isFluidValid(int slot,FluidStack stack) { return false; }
            @Override public int fill(FluidStack stack,FluidAction action) { return 0; }
            @Override public FluidStack drain(int amount,FluidAction action) {
                if (live()) for (int i=2;i<tanks.length+2;i++) { int slot=i%tanks.length;if (!tanks[slot].isEmpty()) return take(slot,amount,action); }
                return FluidStack.EMPTY;
            }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) {
                if (live()) for (int i=0;i<tanks.length;i++) if (FluidThermal.compatible(tanks[i],stack)) return take(i,stack.getAmount(),action);
                return FluidStack.EMPTY;
            }
            private FluidStack take(int slot,int amount,FluidAction action) {
                int n=Math.min(Math.max(0,amount),tanks[slot].getAmount());if (n==0) return FluidStack.EMPTY;
                var out=tanks[slot].copyWithAmount(n);if (action.execute()) { tanks[slot].shrink(n);changed(); }return out;
            }
        };
    }
    public Object gasBankIdentity(int slot) { return gasBanks[slot]; }
    protected FluidStack drainProcessGasLayer(int bank, FluidStack layer, int amount, FluidAction action) {
        var removal = net.createplant.thermal.TankLayers.remove(tanks[bank], layer, amount);
        if (action.execute() && !removal.sample().isEmpty()) {
            tanks[bank] = removal.remaining();
            changed();
        }
        return removal.sample();
    }
    /** A gas network treats the separate process buffers as separate vessels, never as the plain tank's volume. */
    public IFluidHandler processGasPort(int bank) {
        if (bank<0 || bank>=tanks.length || isRemoved() || !tanks[bank].isEmpty() && processGasContent(bank).isEmpty() || tanks[bank].isEmpty() && (!inputTank(bank) || !ready())) return null;
        FluidStack initial=processGasContent(bank);int generation=epoch;
        return new IFluidHandler() {
            private boolean live() { return !isRemoved() && generation==epoch && (!(ReactorMachineBlockEntity.this instanceof LiddedVesselBlockEntity vessel) || vessel.isController() && !vessel.open()); }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int slot) { return slot==0 && live() ? processGasContent(bank) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int slot) { return slot==0 ? bufferCapacity(tanks[bank]) : 0; }
            @Override public boolean isFluidValid(int slot,FluidStack stack) {
                if (slot!=0 || !live() || stack.isEmpty() || !net.createplant.thermal.FluidState.gas(stack) || !tanks[bank].isEmpty() && !FluidThermal.mixable(tanks[bank],stack)) return false;
                // A giver must always accept its own rejected gas back, including when it was drained completely.
                if (!initial.isEmpty() && FluidThermal.mixable(initial,stack)) return true;
                if (!inputTank(bank) || !new Port(0,tanks.length,true,true,epoch).isFluidValid(bank,stack)) return false;
                for (int other=0;other<tanks.length;other++) if (inputTank(other) && other!=bank && FluidThermal.mixable(tanks[other],stack)) return false;
                return true;
            }
            @Override public int fill(FluidStack stack,FluidAction action) {
                if (!isFluidValid(0,stack)) return 0;
                int amount=Math.min(gasRoomFor(stack),Math.min(stack.getAmount(),Math.max(0,bufferCapacity(stack)-tanks[bank].getAmount())));
                if (amount>0 && action.execute()) {
                    tanks[bank]=tanks[bank].isEmpty() ? stack.copyWithAmount(amount) : FluidThermal.mix(tanks[bank],stack,amount);
                    changed();
                }
                return amount;
            }
            @Override public FluidStack drain(int amount,FluidAction action) {
                if (!live() || amount<=0 || tanks[bank].isEmpty()) return FluidStack.EMPTY;
                var gas=processGasContent(bank);if (gas.isEmpty()) return FluidStack.EMPTY;
                var out=gas.copyWithAmount(Math.min(amount,gas.getAmount()));
                if (action.execute()) { tanks[bank].shrink(out.getAmount());changed(); }
                return out;
            }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return FluidThermal.compatible(processGasContent(bank),stack) ? drain(stack.getAmount(),action) : FluidStack.EMPTY; }
        };
    }
    protected List<FluidStack> removeProcessGases(java.util.function.Predicate<FluidStack> stays) {
        var removed=new ArrayList<FluidStack>();
        for(int i=0;i<tanks.length;i++)if(!tanks[i].isEmpty() && net.createplant.transport.FluidHazards.of(tanks[i]).gas() && !stays.test(tanks[i])){removed.add(tanks[i].copy());tanks[i]=FluidStack.EMPTY;}
        if(!removed.isEmpty())changed();return removed;
    }

    public Direction inlet() { if (horizontal()) return getBlockState().getValue(ThermalMachineBlock.FACING).getCounterClockWise();return getBlockState().getValue(ReactorMachineBlock.AXIS)==Direction.Axis.Z ? Direction.WEST : Direction.NORTH; }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CEReactor.ENTITY.get(),(be,side)->
            be.horizontal() ? net.createplant.mixture.GasMixturePort.withLegacyOutlet(be.port(side),be) : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,net.createplant.registry.CEHeating.PART_ENTITY.get(),(proxy,side)-> {
            var owner=proxy.owner();return owner==null ? null : net.createplant.mixture.GasMixturePort.withLegacyOutlet(owner.horizontalPort(proxy.getBlockPos(),side),owner);
        });
    }
    /** Any part of a formed fluid heat exchanger gives out the ice it has frozen; the ice remains owned by its controller. */

    /** Every face of a vertical vessel takes feed and gives products; the reaction charge is reached the same way. */
    public IItemHandler itemPort() { return module()==ReactorModule.STIRRED || module()==ReactorModule.ABSORBER || module()==ReactorModule.NONE && this instanceof LiddedVesselBlockEntity ? chargePort : null; }
    /** A vertical process vessel must be closed to run. */
    protected boolean vesselOpen() { return false; }
    protected boolean processController() { return true; }
    public float rotationSpeed() { return getSpeed(); }
    public boolean hasProcessContents() { return Arrays.stream(tanks).anyMatch(stack->!stack.isEmpty()) || java.util.stream.IntStream.range(0,CHARGE_SLOTS).anyMatch(slot->!charge.getStackInSlot(slot).isEmpty()); }
    public boolean blocksControllerMove() { return Arrays.stream(tanks).anyMatch(stack->!stack.isEmpty()) || module()!=ReactorModule.NONE && java.util.stream.IntStream.range(0,CHARGE_SLOTS).anyMatch(slot->!charge.getStackInSlot(slot).isEmpty()); }
    protected void transferPlainItemsTo(ReactorMachineBlockEntity target) {
        if (target==this || blocksControllerMove()) return;
        for (int slot=0;slot<CHARGE_SLOTS;slot++) {
            ItemStack rest=charge.extractItem(slot,Integer.MAX_VALUE,false);
            for (int into=0;into<CHARGE_SLOTS && !rest.isEmpty();into++) rest=target.charge.insertItem(into,rest,false);
            if (!rest.isEmpty()) Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,rest);
        }
    }
    public IFluidHandler horizontalPort(BlockPos pos,Direction side) {
        if (!horizontal() || !owns(pos) || side==null) return null;
        return net.createplant.block.ExchangerStructure.port(this,members,sections,exchanger(),pos,side);
    }
    protected IFluidHandler processPort(int first,int count,boolean input,boolean output) { return new Port(first,count,input,output,epoch); }

    protected int storeProcessOutput(int slot, FluidStack incoming, IFluidHandler.FluidAction action) {
        if (incoming.isEmpty() || !ready() || inputTank(slot)) return 0;
        var stored=tanks[slot];
        boolean mixed=net.createplant.mixture.MixtureHelper.isMixture(incoming)
            && (stored.isEmpty() || net.createplant.mixture.MixtureHelper.isMixture(stored));
        if (!mixed && !stored.isEmpty() && !FluidThermal.mixable(stored,incoming)) return 0;
        int accepted=Math.min(incoming.getAmount(),Math.max(0,bufferCapacity(incoming)-tanks[slot].getAmount()));
        if (accepted<=0) return 0;
        var portion=net.createplant.mixture.MixtureHelper.portion(incoming,accepted);
        FluidStack result;
        try { result=mixed ? net.createplant.mixture.MixtureHelper.merge(stored,portion,true) : FluidThermal.add(stored,portion,accepted); }
        catch (IllegalArgumentException error) { return 0; }
        if (action.execute()) {
            tanks[slot]=result;
            changed();
        }
        return accepted;
    }
    public IFluidHandler port(Direction side) {
        // The controller is one of its machine's cells and hands out that cell's ports.
        if (module()==ReactorModule.PREHEATER) return horizontalPort(worldPosition,side);
        if (side==null) return new Port(0,tanks.length,false,false,epoch);
        return ready() ? new Port(0,tanks.length,true,true,epoch) : null;
    }
    protected boolean ready() {
        return formed && !dirty && !isRemoved() && level!=null && structureLoaded();
    }
    protected boolean structureLoaded() {
        if (!horizontal()) return ReactorStructure.loaded(level,worldPosition);
        return members.isEmpty() || level.hasChunkAt(members.getFirst()) && level.hasChunkAt(members.getLast());
    }
    public void invalidateStructure() {
        if (validating) return;
        if (!dirty) { epoch++;level.invalidateCapabilities(worldPosition); }
        dirty=true;formed=false;recipeDirty=true;
        // Removing the last internal (or splitting down to 1x1) stops process ticking, so release its old envelope here.
        if (level!=null && !level.isClientSide && !processController()) {
            var previous=members;ReactorStructure.release(level,previous);members=List.of();memberSet=Set.of();sections=0;
            for (BlockPos part:previous) if (level.hasChunkAt(part)) {
                level.invalidateCapabilities(part);level.getBlockState(part).updateNeighbourShapes(level,part,Block.UPDATE_ALL);
            }
            setStatus("vessel",false);
        }
    }
    @Override public void initialize() {
        super.initialize();dirty=true;formed=false;
    }
    public void validateStructure() {
        if (level==null || level.isClientSide() || isRemoved() || !processController()) return;
        validating=true;
        try {
            scans++;var previous=members;
            var result=scanStructure();
            ReactorStructure.release(level,members);
            sections=result.sections();formed=sections>0;fault=result.fault();members=formed ? result.members() : List.of();memberSet=Set.copyOf(members);announcer.update(level,formed,members);
            if (formed && horizontal()) net.createplant.block.ExchangerStructure.shape(this,members,sections,exchanger());
            dirty=false;epoch++;recipeDirty=true;level.invalidateCapabilities(worldPosition);
            // Every part hands out ports, so pipes beside any of them re-check their connection.
            var affected=new HashSet<BlockPos>(previous);affected.addAll(members);
            for (BlockPos part:affected) if (level.hasChunkAt(part)) { level.invalidateCapabilities(part);level.getBlockState(part).updateNeighbourShapes(level,part,Block.UPDATE_ALL); }
            getBlockState().updateNeighbourShapes(level,worldPosition,Block.UPDATE_ALL);
            if (module()==ReactorModule.PREHEATER) {
                level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
            }
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(this,calculateStressApplied());
            setStatus(formed ? "idle" : result.reason(),false);changed();
        } finally { validating=false; }
    }
    protected ReactorStructure.Result scanStructure() { return horizontal() ? net.createplant.block.ExchangerStructure.scan(this,exchanger()) : ReactorStructure.scan(level,worldPosition,module()); }
    /** Layers the goggles count against: four for a vessel. */
    protected int maxSections() { return ReactorStructure.MAX_SECTIONS; }
    @Override public float calculateStressApplied() { return lastStressApplied=formed && !horizontal() ? module().stress*sections : 0; }
    protected void changed() { recipeDirty=true;syncNeeded=true;displayDirty=true;setChanged(); }
    @Override public void tick() {
        super.tick();if (processController()) tickProcess();
        if (level!=null && !level.isClientSide && syncNeeded && Math.floorMod(level.getGameTime()+worldPosition.asLong(),5)==0) { sendData();syncNeeded=false; }
    }
    public void tickProcess() {
        if (level==null || level.isClientSide() || isRemoved()) return;
        clock++;
        if(isRemoved())return;
        if (!structureLoaded()) {
            if (formed) invalidateStructure();
            setStatus("unloaded",false);return;
        }
        if (dirty) validateStructure();
        if (!ready()) return;
        String inhibit=processInhibit();if (!inhibit.isEmpty()) { setStatus(inhibit,false);return; }
        if (!horizontal() && vesselOpen()) { setStatus("lid",false);return; }
        if (level.hasNeighborSignal(worldPosition)) { setStatus("redstone",false);return; }
        if (module()==ReactorModule.PREHEATER) { thermal().tick();return; }
        float rpm=module().stress==0 ? 32 : Math.abs(rotationSpeed());
        if (rpm<32) { setStatus("speed",false);return; }
        if ((module()==ReactorModule.STIRRED || module()==ReactorModule.ABSORBER) && react(rpm,sections)) {
            if (clock%10==0) syncNeeded=true;return;
        }
        if (recipeDirty) {
            recipe=null;idle="materials";
            for (ProcessRecipe candidate:ProcessRecipe.all()) if (candidate.module()==module().process) {
                if (supplied(candidate)>0) { recipe=candidate;break; }
            }
            recipeDirty=false;
        }
        if (recipe==null) { setStatus(idle,false);return; }
        double ratedRate = ProductionMonitor.batchRate(1, sections, recipe.ticks(), module().stress == 0 ? 1 : 4);
        ProductionMonitor.plan(this, ProductionMonitor.fluids(recipe.outputs()) * ratedRate, 0, 0);
        if (!recipe.heatAllowed(currentBurnerHeat())) { setStatus("heat",false);return; }
        int available=batches(recipe);
        if (available==0) { recipeDirty=true;setStatus(gasBlocked ? "gas_full" : "materials",false);return; }
        if (!batch.equals(recipe.id()) || scale<=0 || scale>available || progress==0 && scale!=available) { batch=recipe.id();scale=available;progress=0; }
        cycleTicks=recipe.ticks();
        if(recipe.requiredHeat()!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE && takeBurnerHeat(recipe.requiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)<(recipe.requiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)){setStatus("heat",false);return;}
        ProductionMonitor.limitedBy(this, available < sections ? "throughput" : module().stress != 0 && rpm < 128 ? "speed" : "rated");
        setStatus("running",true);progress+=Math.min(4,rpm/32f);setChanged();
        if (progress>=recipe.ticks()) {
            if (!ready() || batches(recipe)<scale) return;
            for (int i=0;i<recipe.inputs().size();i++) tanks[i].shrink(requiredAmount(recipe,i)*scale);
            for (int i=0;i<recipe.outputs().size();i++) {
                FluidStack out=recipe.outputs().get(i);
                var portion=out.copyWithAmount(out.getAmount()*scale);
                tanks[i+2]=tanks[i+2].isEmpty() ? portion : FluidThermal.mix(tanks[i+2],portion,portion.getAmount());
            }
            ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.outputs()) * scale, 0, 0);
            progress=0;changed();
        }
        if (clock%10==0) syncNeeded=true;
    }
    @Override public void lazyTick() {
        super.lazyTick();
        if (level!=null && !level.isClientSide() && syncNeeded) { sendData();syncNeeded=false; }
        // A vessel that still held something of its own is checked again once a second, since emptying it changes no block.
        if (level!=null && !level.isClientSide() && !formed && "contents".equals(status)) dirty=true;
    }
    private List<net.minecraft.world.item.crafting.RecipeHolder<PressureReactionRecipe>> reactions() {
        return level.getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<net.minecraft.world.item.crafting.RecipeInput,PressureReactionRecipe>getType());
    }
    private List<RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>>> vesselRecipes() {
        var recipes=new ArrayList<RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>>>();
        var installed=module();
        for(var holder:reactions())if(holder.value().module()==installed || installed==ReactorModule.STIRRED && holder.value().module()==ReactorModule.NONE)recipes.add(holder);
        recipes.sort(Comparator.comparingInt((RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> r)->r.value().getIngredients().size()+r.value().getFluidIngredients().size()).reversed());
        if(module()!=ReactorModule.STIRRED)return recipes;
        var mixing=new ArrayList<>(level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.MIXING.<RecipeInput,MixingRecipe>getType()));
        mixing.sort(Comparator.comparingInt((RecipeHolder<MixingRecipe> r)->r.value().getIngredients().size()).reversed());
        recipes.addAll(mixing);
        return recipes;
    }
    protected void tickHeatOnlyReactions(int parallel, boolean fed) {
        if (!fed || vesselOpen() || level.hasNeighborSignal(worldPosition) || !processInhibit().isEmpty()) {
            if (reaction!=null || running) {
                reaction=null;recipeDirty=true;progress=0;
                setStatus(!fed ? "materials" : vesselOpen() ? "lid" : level.hasNeighborSignal(worldPosition) ? "redstone" : processInhibit(),false);
            }
            return;
        }
        for(int slot=0;slot<FEED_SLOTS;slot++) if(!charge.getStackInSlot(slot).isEmpty()) {
            if(!react(32,parallel) && running)setStatus("materials",false);
            return;
        }
        if(running)setStatus("materials",false);
    }

    /** Shared item/fluid batches; heated and tray vessels do not need mechanical stirring. */
    private boolean react(float rpm,int parallel) {
        var heat=burnerHeat();
        if (heat!=burnerHeat) { burnerHeat=heat;recipeDirty=true; }
        if (recipeDirty) {
            reaction=null;
            RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> blocked=null;
            for (var holder:vesselRecipes()) if (batches(holder.value(),parallel)>0) {
                if (holder.value().getRequiredHeat().testBlazeBurner(heat)) { reaction=holder;break; }
                if(blocked==null)blocked=holder;
            }
            if(reaction==null)reaction=blocked;
            if(reaction!=null || module()!=ReactorModule.ABSORBER)recipeDirty=false;
        }
        if (reaction==null) {
            if(module()==ReactorModule.STIRRED)setStatus("materials",false);
            return module()==ReactorModule.STIRRED;
        }
        var recipe=reaction.value();
        int duration = Math.clamp(recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : 100, 1, 200);
        double ratedRate = ProductionMonitor.batchRate(1, parallel, duration, module().stress == 0 ? 1 : 4);
        double itemCount = recipe.getRollableResults().stream().mapToDouble(result -> result.getStack().getCount() * result.getChance()).sum();
        ProductionMonitor.plan(this, ProductionMonitor.fluids(recipe.getFluidResults()) * ratedRate, itemCount * ratedRate, 0);
        if (!recipe.getRequiredHeat().testBlazeBurner(heat)) { progress=0;setStatus("heat",false);return true; }
        int available=batches(recipe,parallel);
        if (available==0) { recipeDirty=true;setStatus(gasBlocked ? "gas_full" : "materials",false);return true; }
        String id=reaction.id().toString();
        if (!batch.equals(id) || scale<=0 || scale>available || progress==0 && scale!=available) { batch=id;scale=available;progress=0; }
        cycleTicks=duration;
        if(recipe.getRequiredHeat()!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE && takeBurnerHeat(recipe.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)<(recipe.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)){setStatus("heat",false);return true;}
        float efficiency = processingSpeedFactor();
        ProductionMonitor.limitedBy(this, available < parallel ? "throughput" : efficiency < 1 ? "fouled"
            : module().stress != 0 && rpm < 128 ? "speed" : "rated");
        setStatus("running",true);progress+=Math.min(4,rpm/32f)*efficiency;setChanged();
        if (progress<cycleTicks || batches(recipe,parallel)<scale) return true;
        for (int i=0;i<scale;i++) {
            ItemStack[] items=items();FluidStack[] fluids=mixingFluids();
            var products = recipe.rollResults(level.random);
            if (!runBatch(recipe,items,fluids,products)) break;
            for (int slot=0;slot<CHARGE_SLOTS;slot++) charge.setStackInSlot(slot,items[slot]);
            commitMixingFluids(fluids);
            ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.getFluidResults()), ProductionMonitor.items(products), 0);
            completedStirredBatch(recipe,1);
        }
        progress=0;changed();return true;
    }
    /** Electric replacements withdraw paid heat; ordinary burners keep their existing heat budget. */
    public double takeBurnerHeat(double requested) {
        if (!(requested>0)) return 0;
        int width=this instanceof LiddedVesselBlockEntity vessel ? vessel.getWidth() : 3;
        BlockPos floor=this instanceof LiddedVesselBlockEntity ? worldPosition.below() : worldPosition.offset(-1,0,-1);
        double supplied=0;
        for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            BlockPos at=floor.offset(x,0,z);
            if (!level.hasChunkAt(at)) continue;
            var heat=com.simibubi.create.content.processing.basin.BasinBlockEntity.getHeatLevelOf(level.getBlockState(at));
            supplied=Math.max(supplied,heat==HeatLevel.SEETHING ? 25000 : heat.isAtLeast(HeatLevel.KINDLED) ? 10000 : 0);
        }
        supplied=Math.min(supplied,requested);
        return supplied;
    }
    /** The hottest heater under the vessel footprint. */
    private com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel burnerHeat() {
        var heat=com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE;
        int width=this instanceof LiddedVesselBlockEntity vessel ? vessel.getWidth() : 3;
        BlockPos floor=this instanceof LiddedVesselBlockEntity ? worldPosition.below() : worldPosition.offset(-1,0,-1);
        for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            if (!this.level.hasChunkAt(floor.offset(x,0,z))) continue;
            var level=com.simibubi.create.content.processing.basin.BasinBlockEntity.getHeatLevelOf(this.level.getBlockState(floor.offset(x,0,z)));
            if (level.ordinal()>heat.ordinal()) heat=level;
        }
        return heat;
    }
    private ItemStack[] items() { ItemStack[] items=new ItemStack[CHARGE_SLOTS];for (int i=0;i<CHARGE_SLOTS;i++) items[i]=charge.getStackInSlot(i).copy();return items; }
    private FluidStack[] fluids() { return Arrays.stream(tanks).map(FluidStack::copy).toArray(FluidStack[]::new); }
    protected FluidStack[] mixingFluids() { return fluids(); }
    protected int[] mixingInputs() { return new int[]{0,1,THIRD_FEED}; }
    protected void commitMixingFluids(FluidStack[] fluids) { System.arraycopy(fluids,0,tanks,0,tanks.length); }
    /** How many batches, one per section at most, the feed and the free product space allow; every product is counted as if it were rolled. */
    private int batches(StandardProcessingRecipe<RecipeInput> recipe,int parallel) {
        gasBlocked=false;
        ItemStack[] items=items();FluidStack[] fluids=mixingFluids();
        if(recipe instanceof PressureReactionRecipe && recipe.getIngredients().isEmpty() && recipe.getFluidIngredients().size()==1){
            var ingredient=recipe.getFluidIngredients().getFirst();
            if(java.util.Arrays.stream(items).anyMatch(stack->!stack.isEmpty()))return 0;
            for(int slot:mixingInputs())if(!fluids[slot].isEmpty() && !ingredient.test(fluids[slot]))return 0;
        }
        int count=0;
        while (count<parallel && runBatch(recipe,items,fluids,recipe.getRollableResultsAsItemStacks())) count++;
        return count;
    }
    /** Takes one batch out of the given stacks and puts its products in; false when it does not fit, the stacks then being spoilt. */
    private boolean runBatch(StandardProcessingRecipe<RecipeInput> recipe,ItemStack[] items,FluidStack[] fluids,List<ItemStack> products) {
        var consumed=new ArrayList<ItemStack>();
        for (var ingredient:recipe.getIngredients()) {
            int slot=0;
            while (slot<FEED_SLOTS && (items[slot].isEmpty() || !ingredient.test(items[slot]))) slot++;
            if (slot==FEED_SLOTS) return false;
            consumed.add(items[slot].copyWithCount(1));items[slot].shrink(1);
        }
        for (var ingredient:recipe.getFluidIngredients()) {
            int needed=ingredient.amount();
            for (int tank:mixingInputs()) if (ingredient.ingredient().test(fluids[tank])) {
                int take=Math.min(needed,fluids[tank].getAmount());
                fluids[tank].shrink(take);needed-=take;
                if (needed==0) break;
            }
            if (needed>0) return false;
        }
        var allProducts=new ArrayList<>(products);
        for (var remainder:recipe.getRemainingItems(CraftingInput.of(consumed.size(),1,consumed))) if (!remainder.isEmpty()) allProducts.add(remainder);
        for (ItemStack product:allProducts) {
            ItemStack rest=product.copy();
            for (int slot=FEED_SLOTS;slot<CHARGE_SLOTS && !rest.isEmpty();slot++) {
                if (items[slot].isEmpty()) { items[slot]=rest;rest=ItemStack.EMPTY; }
                else if (ItemStack.isSameItemSameComponents(items[slot],rest)) { int moved=Math.min(rest.getCount(),items[slot].getMaxStackSize()-items[slot].getCount());items[slot].grow(moved);rest.shrink(moved); }
            }
            if (!rest.isEmpty()) return false;
        }
        for (FluidStack template:recipe.getFluidResults()) {
            FluidStack product=template;
            int tank=2;
            while (tank<7 && !fluids[tank].isEmpty() && !FluidThermal.compatible(fluids[tank],product)) tank++;
            if (tank==7 || fluids[tank].getAmount()+product.getAmount()>bufferCapacity(product)) return false;
            if (fluids[tank].isEmpty()) fluids[tank]=product.copy();else fluids[tank]=FluidThermal.mix(fluids[tank],product,product.getAmount());
        }
        if (gasIn(fluids)>processGasLimit()) { gasBlocked=true;return false; }
        return true;
    }

    /** The components of a mixed gas feed while it is offered, so the feed takes only the components a recipe can use here. */
    private List<FluidStack> offered=List.of();
    public void offer(List<FluidStack> components) { offered=components; }

    /**
     * A feed goes in only beside feeds that a recipe uses together with it, so a gas no recipe needs here passes on instead of taking a feed slot.
     * From a mixture, a component goes in only for a recipe whose other fluids are held or come with it, such as air's oxygen but not its nitrogen.
     */
    private boolean feedFits(FluidStack stack) {
        if (level==null) return false;
        for (var holder:vesselRecipes()) {
            var ingredients=holder.value().getFluidIngredients();
            if (ingredients.stream().noneMatch(ingredient->ingredient.ingredient().test(stack))) continue;
            boolean together=true;
            for (int slot:new int[]{0,1,THIRD_FEED}) {
                var held=tanks[slot];
                if (held.isEmpty() || FluidThermal.mixable(held,stack)) continue;
                if (ingredients.stream().noneMatch(ingredient->ingredient.ingredient().test(held))) { together=false;break; }
            }
            if (together && !offered.isEmpty()) together=ingredients.stream().allMatch(ingredient->ingredient.ingredient().test(stack)
                || offered.stream().anyMatch(ingredient.ingredient()) || java.util.stream.IntStream.of(0,1,THIRD_FEED).anyMatch(slot->ingredient.ingredient().test(tanks[slot])));
            if (together) return true;
        }
        return false;
    }
    /** Whether a recipe here takes this fluid as it is, such as compressed air for leaching, so a mixed feed goes in whole. */
    public boolean usesWhole(FluidStack stack) { return module()==ReactorModule.STIRRED ? feedFits(stack) : reactionFluid(stack); }
    private boolean reactionFluid(FluidStack stack) {
        if (level==null) return false;
        for (var holder:vesselRecipes()) for (var ingredient:holder.value().getFluidIngredients()) if (ingredient.ingredient().test(stack)) return true;
        return false;
    }
    private Component batchName() {
        if (batch.isEmpty()) return Component.translatable(KEY+"no_batch");
        if (!batch.contains(":")) return Component.translatable("process.create_refinery_n_reactors.recipe."+batch);
        var recipe=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(batch)).map(holder->holder.value()).orElse(null);
        if (!(recipe instanceof StandardProcessingRecipe<?> reactionRecipe)) return Component.translatable(KEY+"no_batch");
        return reactionRecipe.getRollableResults().isEmpty() ? reactionRecipe.getFluidResults().getFirst().getHoverName() : reactionRecipe.getRollableResults().getFirst().getStack().getHoverName();
    }
    /** Feed slots take only reaction ingredients, product slots only give. */
    private class ChargePort implements IItemHandler {
        @Override public int getSlots() { return CHARGE_SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return charge.getStackInSlot(slot); }
        private boolean available() { return !isRemoved() && level!=null && (!(ReactorMachineBlockEntity.this instanceof LiddedVesselBlockEntity vessel) || vessel.isController()) && (module()==ReactorModule.NONE || module()==ReactorModule.STIRRED || ready()); }
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return isItemValid(slot,stack) && available() ? charge.insertItem(slot,stack,simulate) : stack; }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { return slot>=0 && slot<CHARGE_SLOTS && (module()==ReactorModule.NONE || !formed || slot>=FEED_SLOTS) && available() ? charge.extractItem(slot,amount,simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return charge.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return slot>=0 && slot<FEED_SLOTS; }
    }
    private int requiredAmount(ProcessRecipe recipe,int index){
        var required=recipe.inputs().get(index);
        return required.getAmount();
    }
    private int batches(ProcessRecipe r) { return r.heatAllowed(currentBurnerHeat()) ? supplied(r) : 0; }
    private int supplied(ProcessRecipe r) {
        gasBlocked=false;
        // Catalytic recipes run in the reaction tower.
        if (r.catalyst()!=Items.AIR) return 0;
        int count=sections;
        for (int i=0;i<r.inputs().size();i++) {
            if (!r.accepts(i,tanks[i])) return 0;
            count=Math.min(count,tanks[i].getAmount()/requiredAmount(r,i));
        }
        for (int i=0;i<r.outputs().size();i++) {
            FluidStack out=r.outputs().get(i),stored=tanks[i+2];
            if (!stored.isEmpty() && !FluidThermal.compatible(stored,out)) return 0;
            count=Math.min(count,(bufferCapacity(out)-stored.getAmount())/out.getAmount());
        }
        long netGas=0;
        for (int i=0;i<r.outputs().size();i++) if (net.createplant.thermal.FluidState.gas(r.outputs().get(i))) netGas+=r.outputs().get(i).getAmount();
        for (int i=0;i<r.inputs().size();i++) if (net.createplant.thermal.FluidState.gas(r.inputs().get(i))) netGas-=requiredAmount(r,i);
        if (netGas>0 && count>0) {
            int fits=(int)Math.min(count,Math.max(0,gasRoom())/netGas);
            if (fits==0) gasBlocked=true;
            count=fits;
        }
        return count;
    }
    /** How much gas the process may hold in all its buffers together; a lidded vessel bounds it by its volume. */
    protected int processGasLimit() { return Integer.MAX_VALUE; }
    private static long gasIn(FluidStack[] fluids) { long total=0;for (var fluid:fluids) if (net.createplant.thermal.FluidState.gas(fluid)) total+=fluid.getAmount();return total; }
    /** The gas that still fits beside what the process buffers hold. */
    protected long processGas() { return gasIn(tanks); }
    public int gasRoom() { return (int)Math.max(0,Math.min(Integer.MAX_VALUE,processGasLimit()-gasIn(tanks))); }
    private int gasRoomFor(FluidStack stack) { return net.createplant.thermal.FluidState.gas(stack) ? gasRoom() : Integer.MAX_VALUE; }
    private static int bufferCapacity(FluidStack fluid) { return net.createplant.thermal.FluidState.gas(fluid) ? CAPACITY*64 : CAPACITY; }
    private void setStatus(String reason,boolean work) { setStatus(reason,work,false); }
    private void setStatus(String reason,boolean work,boolean measured) {
        if (!work) ProductionMonitor.limitedBy(this, switch (reason) {
            case "materials", "contents" -> "throughput";
            case "heat", "redstone", "speed", "lid", "output" -> reason;
            case "gas_full" -> "output";
            case "two_streams" -> "feed";
            default -> "structure";
        });
        if (!measured && module()==ReactorModule.PREHEATER && !work && thermal!=null) thermal.stop();
        if (running==work && status.equals(reason)) return;
        running=work;status=reason;syncNeeded=true;
        if (getBlockState().hasProperty(BlockStateProperties.LIT) && getBlockState().getValue(BlockStateProperties.LIT)!=work) level.setBlock(worldPosition,getBlockState().setValue(BlockStateProperties.LIT,work),Block.UPDATE_CLIENTS);
        setChanged();
    }
    public void dismantle() {
        if (level.isClientSide()) return;
        formed=false;dirty=true;epoch++;ReactorStructure.release(level,members);members=List.of();
        for (int slot=0;slot<CHARGE_SLOTS;slot++) Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),charge.extractItem(slot,64,false));
        boolean thermalVent=module()==ReactorModule.PREHEATER && thermal().hasFluid();
        if (module()==ReactorModule.PREHEATER) {
            for (int i=0;i<4;i++) net.createplant.safety.ToxicGas.release(level,worldPosition,thermal().tank(i).getFluid().copy());
            thermal().clear();
        }
        boolean vent=thermalVent || Arrays.stream(tanks).anyMatch(stack->!stack.isEmpty());
        for (var tank:tanks) net.createplant.safety.ToxicGas.release(level,worldPosition,tank.copy());
        Arrays.fill(tanks,FluidStack.EMPTY);
        if (vent && level instanceof ServerLevel server) server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,16,.4,.5,.4,.02);
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) {
        try {
            text.add(Component.translatable(KEY+"status."+status));
            if (horizontal()) {
                text.add(thermal().exchangeMode());
                for(int row=0;row<2;row++)text.add(thermal().outletReading(row));
                return true;
            }
            structureTooltip(text);
            if (!formed) text.add(Component.translatable(KEY+"fault",fault.getX(),fault.getY(),fault.getZ()));
            text.add(Component.translatable(KEY+"batch",batchName(),scale,Math.round(progress),cycleTicks()));
            for (int i=0;i<tanks.length;i++) if (!tanks[i].isEmpty()) text.add(Component.translatable(KEY+(inputTank(i) ? "input" : "output"),tanks[i].getHoverName(),tanks[i].getAmount(),
                net.createplant.thermal.FluidState.gas(tanks[i]) ? CAPACITY*64 : CAPACITY));
            for (int i=0;i<CHARGE_SLOTS;i++) if (!charge.getStackInSlot(i).isEmpty()) text.add(Component.translatable(KEY+(i<FEED_SLOTS ? "item_input" : "item_output"),charge.getStackInSlot(i).getHoverName(),charge.getStackInSlot(i).getCount()));
            return true;
        } finally { net.createplant.compat.display.MachineReadings.compact(text); }
    }
    /** The goggle lines on the size, drive and connections of a formed vertical machine. */
    protected void structureTooltip(List<Component> text) {
        text.add(Component.translatable(KEY+"structure",sections,maxSections(),CAPACITY));
        text.add(Component.translatable(KEY+"performance",Math.min(4,Math.abs(rotationSpeed())/32f),sections,formed ? module().stress*sections : 0));
        text.add(Component.translatable(KEY+"ports"));
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);
        if (module()==ReactorModule.PREHEATER) { var state=new CompoundTag();thermal().write(state,lookup);tag.put("ThermalEquipment",state); }
        tag.put("Charge",charge.serializeNBT(lookup));tag.putFloat("Progress",progress);tag.putString("Batch",batch);tag.putInt("Scale",scale);tag.putInt("CycleTicks",cycleTicks);
        tag.putInt("Sections",sections);tag.putBoolean("Formed",formed);tag.putBoolean("Running",running);tag.putString("Status",status);tag.putLong("Fault",fault.asLong());
        tag.putInt("DisplayedHeat",displayedHeat.ordinal());
        for (int i=0;i<tanks.length;i++) if (!tanks[i].isEmpty()) tag.put("Tank"+i,tanks[i].save(lookup));
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        if(!packet)announcer.loaded();
        super.read(tag,lookup,packet);
        if (tag.contains("Charge")) charge.deserializeNBT(lookup,tag.getCompound("Charge"));float savedProgress=tag.getFloat("Progress");progress=Float.isFinite(savedProgress) ? Math.clamp(savedProgress,0,200) : 0;batch=tag.getString("Batch");scale=Math.clamp(tag.getInt("Scale"),0,4);cycleTicks=Math.clamp(tag.getInt("CycleTicks"),1,200);
        sections=Math.clamp(tag.getInt("Sections"),0,24);formed=packet && tag.getBoolean("Formed");running=packet && tag.getBoolean("Running");status=tag.getString("Status");fault=BlockPos.of(tag.getLong("Fault"));
        dirty=!packet;recipeDirty=true;
        displayDirty=true;displayedHeat=HeatLevel.values()[Math.clamp(tag.getInt("DisplayedHeat"),0,HeatLevel.values().length-1)];
        for (int i=0;i<tanks.length;i++) tanks[i]=tag.contains("Tank"+i) ? FluidStack.parseOptional(lookup,tag.getCompound("Tank"+i)) : FluidStack.EMPTY;
        if (module()==ReactorModule.PREHEATER) thermal().read(tag.getCompound("ThermalEquipment"),lookup);
    }
    private boolean inputTank(int index) { return index>=0 && (index<2 || index==THIRD_FEED && module()==ReactorModule.STIRRED); }
    private class Port implements IFluidHandler {
        private final int first,count,generation;
        private final boolean input,output;
        Port(int first,int count,boolean input,boolean output,int generation) { this.first=first;this.count=count;this.input=input;this.output=output;this.generation=generation; }
        private boolean live() { return generation==epoch && ready(); }
        @Override public int getTanks() { return count; }
        @Override public int getTankCapacity(int tank) { return tank>=0 && tank<count ? net.createplant.thermal.FluidState.gas(tanks[first+tank]) ? CAPACITY*64 : CAPACITY : 0; }
        @Override public FluidStack getFluidInTank(int tank) { return generation==epoch && !isRemoved() && tank>=0 && tank<count ? tanks[first+tank].copy() : FluidStack.EMPTY; }
        @Override public boolean isFluidValid(int tank,FluidStack stack) {
            if (!input || !live() || tank<0 || tank>=count || !inputTank(first+tank) || stack.isEmpty()) return false;
            if (module()==ReactorModule.STIRRED) return feedFits(stack);
            if (module()==ReactorModule.ABSORBER && reactionFluid(stack)) return true;
            int index=first+tank;
            for (ProcessRecipe r:ProcessRecipe.all()) if (r.module()==module().process && index<r.inputs().size() && r.accepts(index,stack)) return true;
            return false;
        }
        @Override public int fill(FluidStack stack,FluidAction action) {
            if (!live() || !input || stack.isEmpty()) return 0;
            for (int i=0;i<count;i++) if (isFluidValid(i,stack) && (tanks[first+i].isEmpty() || FluidThermal.mixable(tanks[first+i],stack))) {
                int amount=Math.min(gasRoomFor(stack),Math.min(stack.getAmount(),Math.max(0,(net.createplant.thermal.FluidState.gas(stack) ? CAPACITY*64 : CAPACITY)-tanks[first+i].getAmount())));
                if (amount>0 && action.execute()) { tanks[first+i]=FluidThermal.add(tanks[first+i],stack,amount);changed(); }
                return amount;
            }
            return 0;
        }
        @Override public FluidStack drain(FluidStack request,FluidAction action) {
            if (!live() || !output || request.isEmpty()) return FluidStack.EMPTY;
            for (int i=Math.max(first,2);i<first+count;i++) if (!inputTank(i) && FluidThermal.compatible(tanks[i],request)) return take(i,request.getAmount(),action);
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount,FluidAction action) {
            if (live() && output && amount>0) for (int i=Math.max(first,2);i<first+count;i++) if (!inputTank(i) && !tanks[i].isEmpty()) return take(i,amount,action);
            return FluidStack.EMPTY;
        }
        private FluidStack take(int index,int amount,FluidAction action) {
            int n=Math.min(Math.max(0,amount),tanks[index].getAmount());
            if (n==0) return FluidStack.EMPTY;
            FluidStack result=tanks[index].copyWithAmount(n);
            if (action.execute()) { tanks[index].shrink(n);changed(); }
            return result;
        }
    }
}
