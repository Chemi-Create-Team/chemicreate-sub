package net.createplant.block.entity;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.block.*;
import net.createplant.chemistry.*;
import net.createplant.mixture.MixtureHelper;
import net.createplant.mixture.MixtureTransfer;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CERecipeTypes;
import net.createplant.thermal.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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
    private StirredBatch stirredBatch;
    private int[] feedCredit = new int[0];
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
    /** Set when a recipe had its ingredients but no room for its products, so the status names the full output instead of missing materials. */
    private boolean outputBlocked, selectionFull;
    private int sections, epoch, clock, scale, cycleTicks=100;
    private int reuseProductsAt;
    private float progress;
    /** The fraction already converted, used to apportion integral amounts across ticks. */
    private double flow;
    private String batch="", status="structure";
    private BlockPos fault;
    private long scans;

    /** A charge already paid for; keep its recipe quantities and rolled products through a save or reload. */
    private record StirredBatch(HeatCondition heat, boolean mixedFeed, boolean reuseLiquidProducts, double joules, double expectedItems, List<SizedFluidIngredient> inputs,
        List<FluidStack> fluids, List<ItemStack> items) {
        private static final Codec<StirredBatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            HeatCondition.CODEC.fieldOf("heat").forGetter(StirredBatch::heat),
            Codec.BOOL.fieldOf("mixed_gas").forGetter(StirredBatch::mixedFeed),
            Codec.BOOL.optionalFieldOf("reuse_liquid_products",false).forGetter(StirredBatch::reuseLiquidProducts),
            Codec.DOUBLE.fieldOf("joules").forGetter(StirredBatch::joules),
            Codec.DOUBLE.fieldOf("expected_items").forGetter(StirredBatch::expectedItems),
            SizedFluidIngredient.FLAT_CODEC.listOf().fieldOf("inputs").forGetter(StirredBatch::inputs),
            FluidStack.CODEC.listOf().fieldOf("fluids").forGetter(StirredBatch::fluids),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(StirredBatch::items)
        ).apply(instance, StirredBatch::new));
    }
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
    public int getDisplayedCapacity(int slot) { return slot>=0 && slot<tanks.length ? tankCapacity(tanks[slot]) : 0; }
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
        var liquid=processLiquidContent(bank);if (liquid.isEmpty() || heldForStirredBatch(liquid)) return FluidStack.EMPTY;
        var out=liquid.copyWithAmount(Math.min(amount,liquid.getAmount()));
        if (action.execute()) { tanks[bank].shrink(out.getAmount());if (!inputTank(bank)) giveLiquidOutletsTime();changed(); }
        return out;
    }
    public HeatLevel currentBurnerHeat() { return burnerHeat(); }
    protected String processInhibit() { return ""; }
    protected float processingSpeedFactor() { return 1; }

    protected void completedStirredBatch(StandardProcessingRecipe<RecipeInput> recipe,int count) { }

    private final net.createplant.thermal.TankHeating reactionHeating=new net.createplant.thermal.TankHeating();
    /** The heat of a reaction (docs/internal-temperature.md, 5): it goes into what is in the machine, and a vessel overrides this to include its tank. */
    protected void releaseHeat(double joules) {
        if (joules!=0 && reactionHeating.react(Arrays.asList(tanks),joules,ignored->Integer.MAX_VALUE,List.of())==net.createplant.thermal.TankHeating.CHANGED) changed();
    }
    /** The real temperature of what is in the machine; a vessel overrides this to include its tank. */
    protected double contentsKelvin() { return reactionHeating.meanKelvin(Arrays.asList(tanks),List.of()); }
    /**
     * While a reaction with a heat of reaction makes its products, they enter at the temperature of the contents and the heat of
     * reaction does the rest; otherwise they enter at their own level (docs/internal-temperature.md, 5). NaN for the latter.
     */
    private double productKelvin=Double.NaN;
    private void productsAtContents(boolean heatOfReaction) { productKelvin=heatOfReaction ? contentsKelvin() : Double.NaN; }
    private static boolean heatOfReaction(StandardProcessingRecipe<RecipeInput> recipe) {
        return recipe instanceof PressureReactionRecipe reaction && reaction.reactionHeat()!=0;
    }
    private void releaseHeat(StandardProcessingRecipe<RecipeInput> recipe,double batches) {
        if (recipe instanceof PressureReactionRecipe reaction && reaction.reactionHeat()!=0) releaseHeat(reaction.reactionHeat()*batches);
    }

    public IFluidHandler processRecoveryPort() {
        return new IFluidHandler() {
            private boolean live() { return !isRemoved() && (!(ReactorMachineBlockEntity.this instanceof LiddedVesselBlockEntity vessel) || vessel.isController()); }
            @Override public int getTanks() { return tanks.length; }
            @Override public FluidStack getFluidInTank(int slot) { return live() && slot>=0 && slot<tanks.length ? tanks[slot].copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int slot) { return slot>=0 && slot<tanks.length ? tankCapacity(tanks[slot]) : 0; }
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
    /** A gas network treats the separate process buffers as separate vessels, never as the plain tank's volume. */
    public IFluidHandler processGasPort(int bank) {
        if (bank<0 || bank>=tanks.length || isRemoved() || !tanks[bank].isEmpty() && processGasContent(bank).isEmpty() || tanks[bank].isEmpty() && (!inputTank(bank) || !ready())) return null;
        FluidStack initial=processGasContent(bank);int generation=epoch;
        return new IFluidHandler() {
            private boolean live() { return !isRemoved() && generation==epoch && (!(ReactorMachineBlockEntity.this instanceof LiddedVesselBlockEntity vessel) || vessel.isController() && !vessel.open()); }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int slot) { return slot==0 && live() ? processGasContent(bank) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int slot) { return slot==0 ? tankCapacity(tanks[bank]) : 0; }
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
                int amount=Math.min(gasRoomFor(stack),Math.min(stack.getAmount(),room(stack,tanks[bank])));
                if (amount>0 && action.execute()) {
                    tanks[bank]=tanks[bank].isEmpty() ? stack.copyWithAmount(amount) : FluidThermal.mix(tanks[bank],stack,amount);
                    changed();
                }
                return amount;
            }
            @Override public FluidStack drain(int amount,FluidAction action) {
                if (!live() || amount<=0 || tanks[bank].isEmpty()) return FluidStack.EMPTY;
                var gas=processGasContent(bank);if (gas.isEmpty()) return FluidStack.EMPTY;
                var out=MixtureHelper.portion(gas,Math.min(amount,gas.getAmount()));
                if (action.execute()) { tanks[bank]=MixtureHelper.remainder(gas,out.getAmount());changed(); }
                return out;
            }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return MixtureTransfer.matches(processGasContent(bank),stack) ? drain(stack.getAmount(),action) : FluidStack.EMPTY; }
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
    public IItemHandler itemPort() { return module()==ReactorModule.STIRRED || module()==ReactorModule.NONE && this instanceof LiddedVesselBlockEntity ? chargePort : null; }
    /** A vertical process vessel must be closed to run. */
    protected boolean vesselOpen() { return false; }
    /** Nothing but the main liquid is in the vessel: no other feed and no charge. */
    protected boolean feedsEmpty() {
        for(int slot=0;slot<CHARGE_SLOTS;slot++)if(!charge.getStackInSlot(slot).isEmpty())return false;
        return tanks[0].isEmpty() && tanks[1].isEmpty() && tanks[THIRD_FEED].isEmpty();
    }
    /** No feed but liquid water and no charge: heating it can only boil it, there is no recipe to wait for. */
    protected boolean feedsWaterOnly() {
        for(int slot=0;slot<CHARGE_SLOTS;slot++)if(!charge.getStackInSlot(slot).isEmpty())return false;
        for(int feed:new int[]{0,1,THIRD_FEED})if(!tanks[feed].isEmpty() && !net.createplant.thermal.FluidState.liquidWater(tanks[feed]))return false;
        return true;
    }
    /** How much steam the gas space can still take. */
    protected int steamRoom() {
        var steam=FluidForm.STEAM.stack(1);
        long room=0;
        for(int i=2;i<7;i++)if(tanks[i].isEmpty() || tanks[i].is(steam.getFluid()))room+=Math.max(0,bufferCapacity(steam)-tanks[i].getAmount());
        return (int)Math.min(room,gasRoom());
    }
    protected void storeSteam(int amount) {
        for(int i=2;i<7 && amount>0;i++) {
            if(!tanks[i].isEmpty() && !FluidForm.STEAM.matches(tanks[i]))continue;
            // An empty stack cannot be copied with an amount (it stays empty), so the first steam starts from a one mB sample.
            var steam=tanks[i].isEmpty() ? FluidForm.STEAM.stack(1) : tanks[i];
            int held=tanks[i].getAmount();
            int add=Math.min(amount,Math.max(0,bufferCapacity(steam)-held));
            if(add<=0)continue;
            tanks[i]=steam.copyWithAmount(held+add);amount-=add;
        }
        changed();
    }
    protected boolean processController() { return true; }
    public float rotationSpeed() { return getSpeed(); }
    public boolean hasProcessContents() { return Arrays.stream(tanks).anyMatch(stack->!stack.isEmpty()) || java.util.stream.IntStream.range(0,CHARGE_SLOTS).anyMatch(slot->!charge.getStackInSlot(slot).isEmpty()); }
    protected boolean processingStirredBatch() { return stirredBatch!=null; }
    public boolean blocksControllerMove() { return processingStirredBatch() || Arrays.stream(tanks).anyMatch(stack->!stack.isEmpty()) || module()!=ReactorModule.NONE && java.util.stream.IntStream.range(0,CHARGE_SLOTS).anyMatch(slot->!charge.getStackInSlot(slot).isEmpty()); }
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
        int accepted=Math.min(incoming.getAmount(),room(incoming,tanks[slot]));
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
        if (clock==reuseProductsAt) recipeDirty=true;
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
        if (module()==ReactorModule.STIRRED && react(rpm,sections)) {
            if (clock%10==0) syncNeeded=true;return;
        }
        if (recipeDirty) {
            recipe=null;idle="materials";
            for (ProcessRecipe candidate:ProcessRecipe.all()) if (candidate.module()==module().process) {
                if (supplyFraction(candidate,1)>0) { recipe=candidate;break; }
            }
            recipeDirty=false;
        }
        if (recipe==null) { setStatus(idle,false);return; }
        double ratedRate = ProductionMonitor.batchRate(1, sections, recipe.ticks(), module().stress == 0 ? 1 : 4);
        ProductionMonitor.plan(this, ProductionMonitor.fluids(recipe.outputs()) * ratedRate, 0, 0);
        if (!recipe.heatAllowed(currentBurnerHeat())) { setStatus("heat",false);return; }
        double speed=Math.min(4,rpm/32f), wanted=sections*speed/recipe.ticks(), moving=supplyFraction(recipe,wanted);
        if (moving<=0) { recipeDirty=true;setStatus(gasBlocked ? "gas_full" : "materials",false);return; }
        if (!batch.equals(recipe.id())) { batch=recipe.id();flow=0; }
        scale=Math.max(1,(int)Math.ceil(moving*recipe.ticks()/speed));
        cycleTicks=recipe.ticks();
        if(recipe.requiredHeat()!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE && takeBurnerHeat(recipe.requiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)<(recipe.requiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : 150)){setStatus("heat",false);return;}
        ProductionMonitor.limitedBy(this, moving < wanted*.999 ? "throughput" : module().stress != 0 && rpm < 128 ? "speed" : "rated");
        setStatus("running",true);
        convert(recipe,moving);
        progress=(float)(flow*cycleTicks);setChanged();
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
        recipes.sort(Comparator.comparingInt((RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> r)->r.value().getIngredients().size()+r.value().getFluidIngredients().size()).reversed()
            .thenComparing(r->r.value().getRequiredHeat(),Comparator.reverseOrder()));
        if(module()!=ReactorModule.STIRRED)return recipes;
        var mixing=new ArrayList<>(level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.MIXING.<RecipeInput,MixingRecipe>getType()));
        mixing.sort(Comparator.comparingInt((RecipeHolder<MixingRecipe> r)->r.value().getIngredients().size()).reversed());
        recipes.addAll(mixing);
        return recipes;
    }
    protected void tickHeatOnlyReactions(int parallel, boolean fed) {
        if (processingStirredBatch()) { setStatus("speed",false);return; }
        if (!fed || vesselOpen() || level.hasNeighborSignal(worldPosition) || !processInhibit().isEmpty()) {
            if (reaction!=null || running) {
                reaction=null;recipeDirty=true;progress=0;
                setStatus(!fed ? "materials" : vesselOpen() ? "lid" : level.hasNeighborSignal(worldPosition) ? "redstone" : processInhibit(),false);
            }
            return;
        }
        for(int slot=0;slot<FEED_SLOTS;slot++) if(!charge.getStackInSlot(slot).isEmpty()) {
            if(!react(32,parallel) && running)setStatus(selectionFull ? "output" : "materials",false);
            return;
        }
        if(running)setStatus("materials",false);
    }

    /** Shared item/fluid batches; heated vessels do not need mechanical stirring. */
    private boolean react(float rpm,int parallel) {
        var heat=burnerHeat();
        if (heat!=burnerHeat) { burnerHeat=heat;recipeDirty=true; }
        if (processingStirredBatch()) return advanceStirredBatch(stirredBatch,null,heat,rpm,parallel);
        if (recipeDirty) {
            reaction=null;
            RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>> blocked=null;
            var recipes=vesselRecipes();
            var reserved=reservedForSolids(recipes);
            selectionFull=false;
            for (var holder:recipes) {
                var candidate=holder.value();
                if (!reserved.isEmpty() && candidate.getIngredients().isEmpty() && candidate.getFluidIngredients().stream()
                    .anyMatch(ingredient->reserved.stream().anyMatch(ingredient.ingredient()))) continue;
                if (!available(candidate,parallel,holder.id().toString().equals(batch) ? flow : 0)) { selectionFull|=outputBlocked || gasBlocked;continue; }
                if (candidate.getRequiredHeat().testBlazeBurner(heat)) { reaction=holder;break; }
                if(blocked==null)blocked=holder;
            }
            if(reaction==null)reaction=blocked;
            recipeDirty=false;
        }
        if (reaction==null) {
            if(module()==ReactorModule.STIRRED)setStatus(selectionFull ? "output" : "materials",false);
            return module()==ReactorModule.STIRRED;
        }
        var recipe=reaction.value();
        int duration = Math.clamp(recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : 100, 1, 200);
        double ratedRate = ProductionMonitor.batchRate(1, parallel, duration, module().stress == 0 ? 1 : 4);
        double itemCount = recipe.getRollableResults().stream().mapToDouble(result -> result.getStack().getCount() * result.getChance()).sum();
        ProductionMonitor.plan(this, ProductionMonitor.fluids(recipe.getFluidResults()) * ratedRate, itemCount * ratedRate, 0);
        if (!recipe.getRequiredHeat().testBlazeBurner(heat)) { progress=0;setStatus("heat",false);return true; }
        if (recipe.getIngredients().isEmpty() && recipe.getRollableResults().isEmpty()) return reactFluids(recipe,duration,rpm,parallel);
        int available=batches(recipe,parallel);
        if (available==0) { recipeDirty=true;setStatus(gasBlocked ? "gas_full" : outputBlocked ? "output" : "materials",false);return true; }
        String id=reaction.id().toString();
        if (module()==ReactorModule.STIRRED) {
            ItemStack[] contents=items();
            var products=new ArrayList<ItemStack>();
            for (int i=0;i<available;i++) products.addAll(batchProducts(recipe,takeItems(recipe,contents),recipe.rollResults(level.random)));
            boolean pressureReaction=recipe instanceof PressureReactionRecipe;
            double joules=pressureReaction ? ((PressureReactionRecipe)recipe).reactionHeat() : 0;
            batch=id;scale=available;cycleTicks=duration;
            boolean reuseLiquidProducts=mayReuseProducts(recipe) && batches(recipe,parallel,false)<available;
            return advanceStirredBatch(new StirredBatch(recipe.getRequiredHeat(),pressureReaction,reuseLiquidProducts,joules,itemCount,
                List.copyOf(recipe.getFluidIngredients()),List.copyOf(recipe.getFluidResults()),products),contents,heat,rpm,parallel);
        }
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
            releaseHeat(recipe,1);
        }
        progress=0;changed();return true;
    }

    /** Move one slice of a paid charge. A blocked slice leaves both the buffers and its rounding credit untouched. */
    private boolean advanceStirredBatch(StirredBatch active,ItemStack[] newCharge,HeatLevel heat,float rpm,int parallel) {
        boolean starting=newCharge!=null;
        double ratedRate=ProductionMonitor.batchRate(1,parallel,cycleTicks,4);
        ProductionMonitor.plan(this,ProductionMonitor.fluids(active.fluids())*ratedRate,active.expectedItems()*ratedRate,0);
        if (!active.heat().testBlazeBurner(heat)) { setStatus("heat",false);return true; }
        float efficiency=processingSpeedFactor();
        double position=starting ? 0 : flow;
        double wanted=Math.min(4,rpm/32d)*efficiency/cycleTicks;
        boolean finished=position+wanted>=1-1e-9;
        double step=finished ? 1-position : wanted;
        if (!(step>0)) { setStatus("materials",false);return true; }
        var fluids=mixingFluids();
        var credit=starting ? new int[active.inputs().size()] : feedCredit.clone();
        for (int i=0;i<credit.length;i++) {
            var ingredient=active.inputs().get(i);
            int amount=ReactionFlow.owed(ingredient.amount(),position*scale,step*scale);
            long owed=withdrawFluid(active.mixedFeed(),active.reuseLiquidProducts(),ingredient,(long)amount*Concentration.NORMAL-credit[i],fluids);
            if (owed>0) { setStatus("materials",false);return true; }
            credit[i]=(int)-owed;
        }
        int produced=0;
        productsAtContents(active.joules()!=0);
        try {
            for (var product:active.fluids()) {
                int amount=ReactionFlow.made(product.getAmount(),position*scale,step*scale);
                if (amount==0) continue;
                if (!putFluid(fluids,product.copyWithAmount(amount))) { setStatus("output",false);return true; }
                produced+=amount;
            }
        } finally { productKelvin=Double.NaN; }
        if (gasIn(fluids)>batchGasLimit()) { setStatus("gas_full",false);return true; }
        var finalItems=finished ? starting ? newCharge : items() : null;
        if (finished && !putItems(finalItems,active.items())) { setStatus("output",false);return true; }
        int required=active.heat()==HeatCondition.SUPERHEATED ? 600 : active.heat()==HeatCondition.HEATED ? 150 : 0;
        if (required>0 && takeBurnerHeat(required)<required) { setStatus("heat",false);return true; }

        if (finished || starting) {
            var contents=finished ? finalItems : newCharge;
            for (int slot=0;slot<CHARGE_SLOTS;slot++) if (!ItemStack.matches(charge.getStackInSlot(slot),contents[slot])) charge.setStackInSlot(slot,contents[slot]);
        }
        if (produced>0 || !active.inputs().isEmpty()) commitMixingFluids(fluids);
        stirredBatch=finished ? null : active;
        feedCredit=credit;
        flow=finished ? 0 : position+step;
        progress=(float)(flow*cycleTicks);
        ProductionMonitor.limitedBy(this,scale<parallel ? "throughput" : efficiency<1 ? "fouled" : rpm<128 ? "speed" : "rated");
        ProductionMonitor.produced(this,produced,finished ? ProductionMonitor.items(active.items()) : 0,0);
        setStatus("running",true);
        releaseHeat(active.joules()*step*scale);
        if (finished) {
            if (active.fluids().stream().anyMatch(fluid->!FluidState.gas(fluid))) giveLiquidOutletsTime();
            changed();
        }
        else { displayDirty=true;syncNeeded=true;setChanged(); }
        return true;
    }

    private boolean mayReuseProducts(StandardProcessingRecipe<RecipeInput> recipe) {
        return module()==ReactorModule.STIRRED && clock>=reuseProductsAt
            && recipe instanceof PressureReactionRecipe pressure && pressure.reuseLiquidProducts();
    }

    /** Give a connected pump first use of new liquid products while it keeps drawing them. */
    private void giveLiquidOutletsTime() { reuseProductsAt=clock+20; }

    private boolean heldForStirredBatch(FluidStack stock) {
        return processingStirredBatch() && stirredBatch.reuseLiquidProducts() && !FluidState.gas(stock)
            && stirredBatch.inputs().stream().anyMatch(ingredient->ingredient.ingredient().test(stock));
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

    /** A solid charge only reserves liquids that can react with it. Product slots are never part of the charge. */
    private List<FluidStack> reservedForSolids(List<RecipeHolder<? extends StandardProcessingRecipe<RecipeInput>>> recipes) {
        var solids=java.util.stream.IntStream.range(0,FEED_SLOTS).mapToObj(charge::getStackInSlot).filter(stack->!stack.isEmpty()).toList();
        if (solids.isEmpty()) return List.of();
        var ingredients=recipes.stream().map(RecipeHolder::value)
            .filter(recipe->!recipe.getFluidIngredients().isEmpty() && recipe.getIngredients().stream().anyMatch(ingredient->solids.stream().anyMatch(ingredient)))
            .flatMap(recipe->recipe.getFluidIngredients().stream()).toList();
        if (ingredients.isEmpty()) return List.of();
        var fluids=mixingFluids();
        var reserved=new ArrayList<FluidStack>();
        for (int slot:mixingInputs()) {
            var feed=fluids[slot];
            if (feed.isEmpty()) continue;
            if (ingredients.stream().anyMatch(ingredient->ingredient.ingredient().test(feed))) reserved.add(feed);
            if (canDrawComponents(feed)) {
                for (var part:MixtureHelper.components(feed))
                    if (ingredients.stream().anyMatch(ingredient->ingredient.ingredient().test(part))) reserved.add(part);
            }
        }
        return reserved;
    }

    /** How many batches, one per section at most, the feed and the free product space allow; every product is counted as if it were rolled. */
    private int batches(StandardProcessingRecipe<RecipeInput> recipe,int parallel) {
        return batches(recipe,parallel,mayReuseProducts(recipe));
    }
    private int batches(StandardProcessingRecipe<RecipeInput> recipe,int parallel,boolean reuseLiquidProducts) {
        gasBlocked=false;outputBlocked=false;
        ItemStack[] items=items();FluidStack[] fluids=mixingFluids();
        int count=0;
        while (count<parallel && runBatch(recipe,items,fluids,recipe.getRollableResultsAsItemStacks(),reuseLiquidProducts)) count++;
        return count;
    }
    /** Takes one batch out of the given stacks and puts its products in; false when it does not fit, the stacks then being spoilt. */
    private boolean runBatch(StandardProcessingRecipe<RecipeInput> recipe,ItemStack[] items,FluidStack[] fluids,List<ItemStack> products) {
        return runBatch(recipe,items,fluids,products,false);
    }
    private boolean runBatch(StandardProcessingRecipe<RecipeInput> recipe,ItemStack[] items,FluidStack[] fluids,List<ItemStack> products,boolean reuseLiquidProducts) {
        var consumed=takeItems(recipe,items);
        if (consumed==null) return false;
        for (var ingredient:recipe.getFluidIngredients())
            if (withdrawFluid(recipe instanceof PressureReactionRecipe,reuseLiquidProducts,ingredient,(long)ingredient.amount()*Concentration.NORMAL,fluids)>0) return false;
        if (!putItems(items,batchProducts(recipe,consumed,products))) { outputBlocked=true;return false; }
        productsAtContents(heatOfReaction(recipe));
        try { for (FluidStack template:recipe.getFluidResults()) if (!putFluid(fluids,template)) { outputBlocked=true;return false; } }
        finally { productKelvin=Double.NaN; }
        if (gasIn(fluids)>batchGasLimit()) { gasBlocked=true;return false; }
        return true;
    }

    private static List<ItemStack> takeItems(StandardProcessingRecipe<RecipeInput> recipe,ItemStack[] items) {
        var consumed=new ArrayList<ItemStack>();
        for (var ingredient:recipe.getIngredients()) {
            int slot=0;
            while (slot<FEED_SLOTS && (items[slot].isEmpty() || !ingredient.test(items[slot]))) slot++;
            if (slot==FEED_SLOTS) return null;
            consumed.add(items[slot].copyWithCount(1));items[slot].shrink(1);
        }
        return consumed;
    }

    private static List<ItemStack> batchProducts(StandardProcessingRecipe<RecipeInput> recipe,List<ItemStack> consumed,List<ItemStack> products) {
        var result=new ArrayList<>(products);
        for (var remainder:recipe.getRemainingItems(CraftingInput.of(consumed.size(),1,consumed))) if (!remainder.isEmpty()) result.add(remainder);
        return result;
    }

    private static boolean putItems(ItemStack[] items,List<ItemStack> products) {
        for (ItemStack product:products) {
            ItemStack rest=product.copy();
            for (int slot=FEED_SLOTS;slot<CHARGE_SLOTS && !rest.isEmpty();slot++) {
                if (items[slot].isEmpty()) { items[slot]=rest;rest=ItemStack.EMPTY; }
                else if (ItemStack.isSameItemSameComponents(items[slot],rest)) { int moved=Math.min(rest.getCount(),items[slot].getMaxStackSize()-items[slot].getCount());items[slot].grow(moved);rest.shrink(moved); }
            }
            if (!rest.isEmpty()) return false;
        }
        return true;
    }


    /** Whether a recipe can run now: a fluid-only one as soon as any feed is there (it runs continuously), one with solids once a whole batch is. */
    private boolean available(StandardProcessingRecipe<RecipeInput> recipe,int parallel,double position) {
        if (recipe.getIngredients().isEmpty() && recipe.getRollableResults().isEmpty()) { gasBlocked=false;outputBlocked=false;return flowBatch(recipe,mixingFluids(),position,1e-3); }
        return batches(recipe,parallel)>0;
    }
    /** Takes {@code needed} mB of a fluid ingredient out of the feed buffers; false when the feed holds less. */
    private boolean takeFluid(StandardProcessingRecipe<RecipeInput> recipe,SizedFluidIngredient ingredient,int needed,FluidStack[] fluids) {
        return withdrawFluid(recipe instanceof PressureReactionRecipe,false,ingredient,(long)needed*Concentration.NORMAL,fluids)<=0;
    }

    private static boolean canDrawComponents(FluidStack feed) {
        return MixtureHelper.composite(feed) && (MixtureHelper.isMixableGas(feed) || MixtureHelper.isMixableLiquid(feed));
    }

    /** The balance is in thousandths of a normal-strength mB. Negative balances pay toward the next tick. */
    private long withdrawFluid(boolean mixedFeed,boolean reuseLiquidProducts,SizedFluidIngredient ingredient,long owed,FluidStack[] fluids) {
        for (int tank:mixingInputs()) {
            if (owed<=0) return owed;
            owed=withdrawFrom(tank,mixedFeed,ingredient,owed,fluids);
        }
        if (reuseLiquidProducts) for (int tank=2;tank<THIRD_FEED && owed>0;tank++) {
            if (!FluidState.gas(fluids[tank])) owed=withdrawFrom(tank,false,ingredient,owed,fluids);
        }
        return owed;
    }

    private static long withdrawFrom(int tank,boolean mixedFeed,SizedFluidIngredient ingredient,long owed,FluidStack[] fluids) {
        var feed=fluids[tank];
        if (feed.isEmpty()) return owed;
        if (ingredient.ingredient().test(feed)) {
            int strength=Concentration.permille(feed);
            int take=(int)Math.min((owed+strength-1)/strength,feed.getAmount());
            fluids[tank]=MixtureHelper.remainder(feed,take);
            return owed-(long)take*strength;
        }
        if (mixedFeed && canDrawComponents(feed)) for (var component:MixtureHelper.components(feed)) {
            if (!ingredient.ingredient().test(component)) continue;
            int strength=Concentration.permille(component);
            int take=(int)Math.min((owed+strength-1)/strength,component.getAmount());
            var held=fluids[tank];
            var removal=MixtureHelper.composite(held) ? MixtureHelper.removeComponent(held,component,take)
                : new MixtureHelper.Removal(held.copyWithAmount(take),MixtureHelper.remainder(held,take));
            fluids[tank]=removal.remaining();owed-=(long)removal.sample().getAmount()*strength;
            if (owed<=0) break;
        }
        return owed;
    }
    /** Puts a product beside the same material and phase, or uses an empty product buffer. */
    private boolean putFluid(FluidStack[] fluids,FluidStack product) {
        if (!Double.isNaN(productKelvin)) {
            product=product.copy();
            FluidState.setTemperature(product,FluidState.levelFor(product,productKelvin,FluidState.pressure(product),FluidState.gas(product)));
        }
        int empty=-1,tank=-1;
        boolean gas=FluidState.gas(product);
        for (int slot=2;slot<THIRD_FEED;slot++) {
            var held=fluids[slot];
            if (held.isEmpty()) { if (empty<0) empty=slot; }
            else if (FluidThermal.mixable(held,product) && FluidState.gas(held)==gas) { tank=slot;break; }
        }
        if (tank<0) tank=empty;
        if (tank<0 || fluids[tank].getAmount()+product.getAmount()>bufferCapacity(product)) return false;
        if (!FluidState.gas(product) && liquidRoom(fluids)<product.getAmount()) return false;
        fluids[tank]=FluidThermal.add(fluids[tank],product,product.getAmount());
        return true;
    }
    /** What a reaction taking {@code f} more batches takes from an amount per batch: ahead of the products, so nothing is made from nothing. */
    private int owed(int amount,double f) { return net.createplant.chemistry.ReactionFlow.owed(amount,flow,f); }
    /** What a reaction that has converted {@code f} more batches has made of an amount per batch. */
    private int made(int amount,double f) { return net.createplant.chemistry.ReactionFlow.made(amount,flow,f); }
    /** Moves a fluid-only reaction on by {@code f} batches out of the given buffers; false when the feed, the room for the products or the gas limit does not allow it. */
    private boolean flowBatch(StandardProcessingRecipe<RecipeInput> recipe,FluidStack[] fluids,double position,double f) {
        if (recipe instanceof PressureReactionRecipe && recipe.getIngredients().isEmpty() && recipe.getFluidIngredients().size()==1) {
            for (int slot:mixingInputs()) if (!fluids[slot].isEmpty() && !recipe.getFluidIngredients().getFirst().ingredient().test(fluids[slot])) return false;
        }
        for (var ingredient:recipe.getFluidIngredients()) if (!takeFluid(recipe,ingredient,ReactionFlow.owed(ingredient.amount(),position,f),fluids)) return false;
        productsAtContents(heatOfReaction(recipe));
        try {
            for (FluidStack template:recipe.getFluidResults()) {
                int amount=ReactionFlow.made(template.getAmount(),position,f);
                if (amount>0 && !putFluid(fluids,template.copyWithAmount(amount))) { outputBlocked=true;return false; }
            }
        } finally { productKelvin=Double.NaN; }
        if (gasIn(fluids)>batchGasLimit()) { gasBlocked=true;return false; }
        return true;
    }
    /** A stirred reaction of fluids alone runs continuously: each tick converts what the speed allows of the feed, halving the step until the buffers allow it. */
    private boolean reactFluids(StandardProcessingRecipe<RecipeInput> recipe,int duration,float rpm,int parallel) {
        double speed=Math.min(4,rpm/32f)*processingSpeedFactor(), wanted=parallel*speed/duration;
        String id=reaction.id().toString();
        double position=batch.equals(id) ? flow : 0;
        gasBlocked=false;outputBlocked=false;
        FluidStack[] result=null;
        double step=wanted;
        for (;step>1e-4;step/=2) { var trial=mixingFluids();if (flowBatch(recipe,trial,position,step)) { result=trial;break; } }
        if (result==null) { recipeDirty=true;setStatus(gasBlocked ? "gas_full" : outputBlocked ? "output" : "materials",false);return true; }
        var needed=recipe.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? 600 : recipe.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.HEATED ? 150 : 0;
        if (needed>0 && takeBurnerHeat(needed)<needed) { setStatus("heat",false);return true; }
        batch=id;
        cycleTicks=duration;scale=Math.max(1,(int)Math.ceil(step/Math.max(1e-9,speed/duration)));
        ProductionMonitor.limitedBy(this, step<wanted*.999 ? "throughput" : processingSpeedFactor()<1 ? "fouled" : module().stress!=0 && rpm<128 ? "speed" : "rated");
        setStatus("running",true);
        commitMixingFluids(result);
        ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.getFluidResults())*step, 0, 0);
        int done=(int)Math.floor(position+step+1e-9);flow=ReactionFlow.next(position,step);
        if (done>0) completedStirredBatch(recipe,done);
        releaseHeat(recipe,step);
        progress=(float)(flow*cycleTicks);displayDirty=true;setChanged();
        return true;
    }
    /** How many batches a machine of fixed recipes may convert this tick: what it wants, less what the feed, the product room and the gas room allow. */
    private double supplyFraction(ProcessRecipe r,double wanted) {
        gasBlocked=false;
        if (r.catalyst()!=Items.AIR) return 0;
        double f=wanted;
        for (int i=0;i<r.inputs().size();i++) {
            if (!r.accepts(i,tanks[i])) return 0;
            f=Math.min(f,tanks[i].getAmount()/(double)requiredAmount(r,i));
        }
        for (int i=0;i<r.outputs().size();i++) {
            FluidStack out=r.outputs().get(i),stored=tanks[i+2];
            if (!stored.isEmpty() && !FluidThermal.compatible(stored,out)) return 0;
            f=Math.min(f,(bufferCapacity(out)-stored.getAmount())/(double)out.getAmount());
        }
        long netGas=0;
        for (int i=0;i<r.outputs().size();i++) if (net.createplant.thermal.FluidState.gas(r.outputs().get(i))) netGas+=r.outputs().get(i).getAmount();
        for (int i=0;i<r.inputs().size();i++) if (net.createplant.thermal.FluidState.gas(r.inputs().get(i))) netGas-=requiredAmount(r,i);
        if (netGas>0 && f>0) {
            double fits=Math.max(0,gasRoom())/(double)netGas;
            if (fits<=0) gasBlocked=true;
            f=Math.min(f,fits);
        }
        return f>1e-6 ? f : 0;
    }
    /** Moves a fixed-recipe machine on by {@code f} batches: the feed goes out ahead of the products, a little every tick. */
    private void convert(ProcessRecipe r,double f) {
        int total=0;
        for (int i=0;i<r.inputs().size();i++) tanks[i].shrink(Math.min(tanks[i].getAmount(),owed(requiredAmount(r,i),f)));
        for (int i=0;i<r.outputs().size();i++) {
            FluidStack out=r.outputs().get(i);
            int give=made(out.getAmount(),f);
            if (give<=0) continue;
            var portion=out.copyWithAmount(give);
            tanks[i+2]=tanks[i+2].isEmpty() ? portion : FluidThermal.mix(tanks[i+2],portion,give);
            total+=give;
        }
        releaseHeat(r.reactionHeat()*f);
        double next=flow+f;flow=next-Math.floor(next);
        ProductionMonitor.produced(this, total, 0, 0);
        displayDirty=true;
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
    /** Whether the machine has picked no batch yet. */
    public boolean batchEmpty() { return batch.isEmpty(); }
    /** The batch as a reaction of formulas ("CuO + H2SO4 -> CuSO4 + H2O"); a recipe without chemistry shows its product's name. */
    public Component reactionLabel() {
        try {
            if (batch.contains(":") && level!=null && level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(batch)).map(holder->holder.value()).orElse(null) instanceof StandardProcessingRecipe<?> recipe) {
                var left=new ArrayList<String>();var right=new ArrayList<String>();
                for (var ingredient:recipe.getFluidIngredients()) if (ingredient.getFluids().length>0) left.add(formulaOf(ingredient.getFluids()[0]));
                for (var ingredient:recipe.getIngredients()) if (ingredient.getItems().length>0) left.add(ingredient.getItems()[0].getHoverName().getString());
                for (var fluid:recipe.getFluidResults()) right.add(formulaOf(fluid));
                for (var result:recipe.getRollableResults()) right.add(result.getStack().getHoverName().getString());
                if (!left.isEmpty() && !right.isEmpty()) return Component.literal(String.join(" + ",left)+" → "+String.join(" + ",right));
            }
            if (!batch.contains(":")) for (var recipe:ProcessRecipe.all()) if (recipe.id().equals(batch)) return net.createplant.client.GoggleSpec.equation(recipe.inputs(),recipe.outputs());
        } catch (RuntimeException ignored) { }
        return batchName();
    }
    private static String formulaOf(FluidStack fluid) {
        var info=net.createplant.chemistry.FluidChemicalRegistry.of(fluid);
        return info==null || info.formula().equals("…") ? fluid.getHoverName().getString() : info.formula();
    }
    private Component batchName() {
        if (batch.isEmpty()) return Component.translatable(KEY+"no_batch");
        if (!batch.contains(":")) return Component.translatableWithFallback("process.create_refinery_n_reactors.recipe."+batch,batch.replace('_',' '));
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
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { return slot>=0 && slot<CHARGE_SLOTS && (module()==ReactorModule.NONE || module()==ReactorModule.STIRRED || !formed || slot>=FEED_SLOTS) && available() ? charge.extractItem(slot,amount,simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return charge.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return slot>=0 && slot<feedSlots(); }
    }
    /** Feed slots that accept new items; recipes still read all of them. */
    protected int feedSlots() { return FEED_SLOTS; }
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
    protected int batchGasLimit() { return processGasLimit(); }
    private static long gasIn(FluidStack[] fluids) { long total=0;for (var fluid:fluids) if (net.createplant.thermal.FluidState.gas(fluid)) total+=fluid.getAmount();return total; }
    /** The gas that still fits beside what the process buffers hold. */
    protected long processGas() { return gasIn(tanks); }
    public int gasRoom() { return (int)Math.max(0,Math.min(Integer.MAX_VALUE,processGasLimit()-gasIn(tanks))); }
    private int gasRoomFor(FluidStack stack) { return net.createplant.thermal.FluidState.gas(stack) ? gasRoom() : Integer.MAX_VALUE; }
    private int bufferCapacity(FluidStack fluid) { return net.createplant.thermal.FluidState.gas(fluid) ? liquidBufferCapacity()*64 : liquidBufferCapacity(); }
    /** Liquid room of each feed and product buffer. */
    public int liquidBufferCapacity() { return CAPACITY; }
    /** Liquid all buffers of the vessel hold together, storage included; unlimited where each buffer is a tank of its own. */
    protected int sharedLiquidLimit() { return Integer.MAX_VALUE; }
    /** Liquid room left in the shared volume of the given buffers: a reaction turns one liquid into another, so it only needs room for what it adds. */
    protected int liquidRoom(FluidStack[] fluids) {
        int limit=sharedLiquidLimit();
        if (limit==Integer.MAX_VALUE) return limit;
        long used=0;
        for (var fluid:fluids) if (!fluid.isEmpty() && !FluidState.gas(fluid)) used+=fluid.getAmount();
        return (int)Math.max(0,limit-used);
    }
    public int liquidRoom() { return liquidRoom(mixingFluids()); }
    /** A buffer's capacity as seen from outside: a liquid one can grow only into the vessel's free volume. */
    private int tankCapacity(FluidStack held) {
        int own=bufferCapacity(held);
        return FluidState.gas(held) ? own : (int)Math.min(own,(long)held.getAmount()+liquidRoom());
    }
    /** How much of {@code incoming} a buffer holding {@code held} can still take. */
    private int room(FluidStack incoming,FluidStack held) {
        int room=Math.max(0,bufferCapacity(incoming)-held.getAmount());
        return FluidState.gas(incoming) ? room : Math.min(room,liquidRoom());
    }
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
        stirredBatch=null;
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
                tankCapacity(tanks[i])));
            for (int i=0;i<CHARGE_SLOTS;i++) if (!charge.getStackInSlot(i).isEmpty()) text.add(Component.translatable(KEY+(i<FEED_SLOTS ? "item_input" : "item_output"),charge.getStackInSlot(i).getHoverName(),charge.getStackInSlot(i).getCount()));
            return true;
        } finally { net.createplant.compat.display.MachineReadings.compact(text); }
    }
    /** The goggle lines on the size, drive and connections of a formed vertical machine. */
    protected void structureTooltip(List<Component> text) {
        text.add(Component.translatable(KEY+"structure",sections,maxSections(),liquidBufferCapacity()));
        text.add(Component.translatable(KEY+"performance",Math.min(4,Math.abs(rotationSpeed())/32f),sections,formed ? module().stress*sections : 0));
        text.add(Component.translatable(KEY+"ports"));
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);
        if (module()==ReactorModule.PREHEATER) { var state=new CompoundTag();thermal().write(state,lookup);tag.put("ThermalEquipment",state); }
        tag.put("Charge",charge.serializeNBT(lookup));tag.putFloat("Progress",progress);tag.putDouble("Flow",flow);tag.putString("Batch",batch);tag.putInt("Scale",scale);tag.putInt("CycleTicks",cycleTicks);
        if (!packet && processingStirredBatch()) {
            tag.put("StirredBatch",StirredBatch.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),stirredBatch).getOrThrow());
            tag.putIntArray("StirredCredit",feedCredit);
        }
        tag.putInt("Sections",sections);tag.putBoolean("Formed",formed);tag.putBoolean("Running",running);tag.putString("Status",status);tag.putLong("Fault",fault.asLong());
        tag.putInt("DisplayedHeat",displayedHeat.ordinal());
        for (int i=0;i<tanks.length;i++) if (!tanks[i].isEmpty()) tag.put("Tank"+i,tanks[i].save(lookup));
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        if(!packet)announcer.loaded();
        super.read(tag,lookup,packet);
        if (tag.contains("Charge")) charge.deserializeNBT(lookup,tag.getCompound("Charge"));float savedProgress=tag.getFloat("Progress");progress=Float.isFinite(savedProgress) ? Math.clamp(savedProgress,0,200) : 0;batch=tag.getString("Batch");flow=Math.clamp(tag.getDouble("Flow"),0,1);scale=Math.clamp(tag.getInt("Scale"),0,4);cycleTicks=Math.clamp(tag.getInt("CycleTicks"),1,200);
        if (!packet) {
            giveLiquidOutletsTime();
            stirredBatch=scale>0 && flow>0 && flow<1 && tag.contains("StirredBatch")
                ? StirredBatch.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE),tag.get("StirredBatch")).result().orElse(null) : null;
            feedCredit=stirredBatch==null ? new int[0] : Arrays.copyOf(tag.getIntArray("StirredCredit"),stirredBatch.inputs().size());
        }
        sections=Math.clamp(tag.getInt("Sections"),0,24);formed=packet && tag.getBoolean("Formed");running=packet && tag.getBoolean("Running");status=tag.getString("Status");fault=BlockPos.of(tag.getLong("Fault"));
        dirty=!packet;recipeDirty=true;
        displayDirty=true;displayedHeat=HeatLevel.values()[Math.clamp(tag.getInt("DisplayedHeat"),0,HeatLevel.values().length-1)];
        for (int i=0;i<tanks.length;i++) tanks[i]=tag.contains("Tank"+i) ? FluidStack.parseOptional(lookup,tag.getCompound("Tank"+i)) : FluidStack.EMPTY;
        if (module()==ReactorModule.PREHEATER) thermal().read(tag.getCompound("ThermalEquipment"),lookup);
    }
    protected boolean inputTank(int index) { return index>=0 && (index<2 || index==THIRD_FEED && module()==ReactorModule.STIRRED); }
    private class Port implements IFluidHandler {
        private final int first,count,generation;
        private final boolean input,output;
        Port(int first,int count,boolean input,boolean output,int generation) { this.first=first;this.count=count;this.input=input;this.output=output;this.generation=generation; }
        private boolean live() { return generation==epoch && ready(); }
        @Override public int getTanks() { return count; }
        @Override public int getTankCapacity(int tank) { return tank>=0 && tank<count ? tankCapacity(tanks[first+tank]) : 0; }
        @Override public FluidStack getFluidInTank(int tank) { return generation==epoch && !isRemoved() && tank>=0 && tank<count ? tanks[first+tank].copy() : FluidStack.EMPTY; }
        @Override public boolean isFluidValid(int tank,FluidStack stack) {
            if (!input || !live() || tank<0 || tank>=count || !inputTank(first+tank) || stack.isEmpty()) return false;
            if (module()==ReactorModule.STIRRED) return feedFits(stack);
            int index=first+tank;
            for (ProcessRecipe r:ProcessRecipe.all()) if (r.module()==module().process && index<r.inputs().size() && r.accepts(index,stack)) return true;
            return false;
        }
        @Override public int fill(FluidStack stack,FluidAction action) {
            if (!live() || !input || stack.isEmpty()) return 0;
            for (int i=0;i<count;i++) if (isFluidValid(i,stack) && (tanks[first+i].isEmpty() || FluidThermal.mixable(tanks[first+i],stack))) {
                int amount=Math.min(gasRoomFor(stack),Math.min(stack.getAmount(),room(stack,tanks[first+i])));
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
            if (live() && output && amount>0) for (int i=Math.max(first,2);i<first+count;i++) if (!inputTank(i) && !tanks[i].isEmpty()) {
                var drained=take(i,amount,action);if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }
        private FluidStack take(int index,int amount,FluidAction action) {
            int n=Math.min(Math.max(0,amount),tanks[index].getAmount());
            if (n==0 || heldForStirredBatch(tanks[index])) return FluidStack.EMPTY;
            FluidStack result=tanks[index].copyWithAmount(n);
            if (action.execute()) { tanks[index].shrink(n);if (!FluidState.gas(result)) giveLiquidOutletsTime();changed(); }
            return result;
        }
    }
}
