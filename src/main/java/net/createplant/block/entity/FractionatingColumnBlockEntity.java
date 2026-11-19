package net.createplant.block.entity;

import java.util.*;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.*;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Create owns structural grouping. Every cell retains its own finite process hold-up through merges and splits. */
public class FractionatingColumnBlockEntity extends SmartBlockEntity implements IMultiBlockEntityContainer, IHaveGoggleInformation {
    private static final String KEY="distillation.create_refinery_n_reactors.";
    private static final int SLOTS=DistillationProfile.SLOTS, INTERVAL=5;
    private final int[] held=new int[SLOTS];
    private final int[] stocks=new int[SLOTS];
    private float progress;
    private int scale;
    private String localRecipe="air",activeRecipe="air";
    private int compatibleCapacity;
    private List<FractionatingColumnBlockEntity> members=List.of();
    private BlockPos controller;
    private int width=1,height=1,epoch,clock;
    private boolean connectivityPending=true,geometryDirty=true,inventoryDirty=true,valid,syncNeeded=true,running;
    private String status="height";
    private long structureScans;
    public FractionatingColumnBlockEntity(BlockPos pos,BlockState state) {
        super(CEDistillation.COLUMN_ENTITY.get(),pos,state);setLazyTickRate(20);
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    @Override public boolean isController() { return controller==null || controller.equals(worldPosition); }
    @Override public BlockPos getController() { return isController() ? worldPosition : controller; }
    @Override @SuppressWarnings("unchecked") public FractionatingColumnBlockEntity getControllerBE() {
        if (isController()) return this;
        if (level==null || !level.hasChunkAt(controller)) return null;
        return level.getBlockEntity(controller) instanceof FractionatingColumnBlockEntity be && !be.isRemoved() ? be : null;
    }
    @Override public BlockPos getLastKnownPos() { return worldPosition; }
    @Override public Direction.Axis getMainConnectionAxis() { return Direction.Axis.Y; }
    @Override public int getMaxWidth() { return DistillationProfile.MAX_WIDTH; }
    @Override public int getMaxLength(Direction.Axis axis,int candidateWidth) { return axis==Direction.Axis.Y ? DistillationProfile.HEIGHT : 3; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public void setWidth(int value) { width=Math.clamp(value,1,3); }
    @Override public void setHeight(int value) { height=Math.clamp(value,1,DistillationProfile.HEIGHT); }
    @Override public void setController(BlockPos pos) {
        if (Objects.equals(controller,pos)) return;
        controller=pos.immutable();progress=0;members=List.of();invalidateLocal();
    }
    @Override public void removeController(boolean keepContents) {
        controller=null;width=height=1;progress=0;members=List.of();connectivityPending=true;invalidateLocal();
    }
    @Override public void preventConnectivityUpdate() { connectivityPending=false; }
    @Override public void notifyMultiUpdated() { invalidateLocal();shape(); }
    private void invalidateLocal() {
        valid=false;geometryDirty=true;inventoryDirty=true;epoch++;syncNeeded=true;setChanged();
        if (level!=null && !level.isClientSide()) level.invalidateCapabilities(worldPosition);
    }
    public void requestConnectivity() {
        if (level==null || level.isClientSide()) return;
        FractionatingColumnBlockEntity owner=getControllerBE();
        if (owner!=null) {
            owner.connectivityPending=true;owner.invalidateLocal();
            for (var member:owner.members) if (level.hasChunkAt(member.worldPosition)) level.invalidateCapabilities(member.worldPosition);
        } else { controller=null;width=height=1; }
        connectivityPending=true;invalidateLocal();
    }
    @Override public void initialize() {
        super.initialize();
        if (level!=null && !level.isClientSide()) requestConnectivity();
    }
    private boolean loaded(int margin) {
        for (int x=0;x<2;x++) for (int z=0;z<2;z++)
            if (!level.hasChunkAt(worldPosition.offset(x==0 ? -margin : width-1+margin,0,z==0 ? -margin : width-1+margin))) return false;
        return true;
    }
    @Override public void tick() {
        super.tick();if (level==null || level.isClientSide()) return;clock++;
        if (connectivityPending && (clock==1 || clock%5==0)) {
            // Create's public grouping API searches nearby candidate origins. Check that neighbourhood before invoking it.
            if (loaded(6)) { connectivityPending=false;ConnectivityHandler.formMulti(this);geometryDirty=true; }
            else { setStatus("unloaded",false);return; }
        }
        if (!isController()) return;
        if (valid && !loaded(1)) { invalidateLocal();setStatus("unloaded",false); }
        if (geometryDirty && !connectivityPending && (clock==1 || clock%5==0)) validateGeometry();
        if (ready() && clock%INTERVAL==0) process();
    }
    @Override public void lazyTick() {
        super.lazyTick();
        if (level!=null && !level.isClientSide() && syncNeeded) { sendData();syncNeeded=false; }
    }
    public void rebuild() {
        if (level==null || level.isClientSide() || !loaded(6)) return;
        connectivityPending=false;ConnectivityHandler.formMulti(this);
        FractionatingColumnBlockEntity owner=getControllerBE();if (owner!=null) owner.validateGeometry();
    }
    public void validateGeometry() {
        if (!isController() || level==null || level.isClientSide()) return;
        structureScans++;geometryDirty=false;valid=false;members=List.of();
        if (!loaded(1)) { geometryDirty=true;setStatus("unloaded",false);return; }
        if (width!=DistillationProfile.REQUIRED_WIDTH) { setStatus("width",false);shape();return; }
        if (height<2) { setStatus("height",false);shape();return; }
        List<FractionatingColumnBlockEntity> found=new ArrayList<>(width*width*height);
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            BlockPos pos=worldPosition.offset(x,y,z);
            if (!(level.getBlockEntity(pos) instanceof FractionatingColumnBlockEntity part) || !part.getController().equals(worldPosition)) { setStatus("structure",false);return; }
            found.add(part);
        }
        for (var part:found) for (Direction side:Direction.values()) {
            BlockPos pos=part.worldPosition.relative(side);
            if (inside(pos)) continue;
            if (level.getBlockState(pos).is(CEDistillation.COLUMN.get())) { setStatus("overhang",false);return; }
        }
        members=List.copyOf(found);inventoryDirty=true;valid=true;epoch++;
        for (var part:members) { part.connectivityPending=false;part.geometryDirty=false; }
        refreshPorts();
        setStatus("idle",false);
        refreshStocks();syncNeeded=true;setChanged();
    }
    private boolean inside(BlockPos pos) {
        return pos.getX()>=worldPosition.getX() && pos.getX()<worldPosition.getX()+width && pos.getZ()>=worldPosition.getZ() && pos.getZ()<worldPosition.getZ()+width && pos.getY()>=worldPosition.getY() && pos.getY()<worldPosition.getY()+height;
    }
    private void shape() {
        if (level==null || level.isClientSide() || isRemoved()) return;
        BlockPos origin=getController();int y=worldPosition.getY()-origin.getY();
        int x=worldPosition.getX()-origin.getX(),z=worldPosition.getZ()-origin.getZ();
        if (x<0 || x>=width || z<0 || z>=width || y<0 || y>=height) return;
        var section=height==1 ? FractionatingColumnBlock.Section.SINGLE : y==0 ? FractionatingColumnBlock.Section.BASE : y==height-1 ? FractionatingColumnBlock.Section.TOP : y==1 ? FractionatingColumnBlock.Section.SERVICE : FractionatingColumnBlock.Section.BODY;
        int cut=0,inlet=0;
        if (width==DistillationProfile.REQUIRED_WIDTH && height>=2) {
            if (z==0 && x==(width-1)/2) {
                var owner=getControllerBE();var fraction=owner==null ? null : DistillationProfile.fraction(owner.activeRecipe);
                int slot=fraction==null ? 0 : DistillationProfile.fractionSlot(y,height,fraction.outputs().size());
                cut=fraction==null ? height==DistillationProfile.HEIGHT ? DistillationProfile.outlet(y) : 0 : slot;
            }
            if (x==width-1 && z==(width-1)/2 && y==DistillationProfile.feedLayer(height)) inlet=1;
        }
        BlockState next=getBlockState().setValue(FractionatingColumnBlock.WIDTH,width).setValue(FractionatingColumnBlock.CELL,z*3+x).setValue(FractionatingColumnBlock.SECTION,section).setValue(FractionatingColumnBlock.CUT,cut).setValue(FractionatingColumnBlock.INLET,inlet);
        if (next!=getBlockState()) level.setBlock(worldPosition,next,Block.UPDATE_CLIENTS);
    }
    public boolean ready() { return valid && isController() && !geometryDirty && !connectivityPending && !isRemoved() && level!=null && loaded(1); }
    public long structureScans() { return structureScans; }
    public int batchCapacity() { return width*width; }
    public int capacity() { refreshStocks();return compatibleCapacity; }
    public boolean airMode() { return activeRecipe.equals("air"); }
    public String recipeId() { return activeRecipe; }
    public int requiredHeight() { return DistillationProfile.requiredHeight(activeRecipe); }
    public int cycleTicks() { var fraction=DistillationProfile.fraction(activeRecipe);return fraction==null ? DistillationProfile.CYCLE_TICKS : fraction.ticks(); }
    private static FluidStack template(String recipe,int slot) {
        if(recipe.equals("air"))return slot<=DistillationProfile.NITROGEN ? new FluidStack(DistillationProfile.fluid(slot),1) : FluidStack.EMPTY;
        var fraction=DistillationProfile.fraction(recipe);
        return fraction==null || slot<0 || slot>fraction.outputs().size() ? FluidStack.EMPTY : slot==0 ? fraction.input() : fraction.outputs().get(slot-1);
    }
    public FluidStack content(int slot) { var fluid=template(activeRecipe,slot);return fluid.isEmpty() || stock(slot)<=0 ? FluidStack.EMPTY : fluid.copyWithAmount(stock(slot)); }
    public float progress() { return progress; }
    public String status() { return status; }
    public boolean running() { return running; }
    private void refreshStocks() {
        if (!inventoryDirty) return;
        Arrays.fill(stocks,0);compatibleCapacity=0;
        if (members.isEmpty()) {if(localRecipe.equals(activeRecipe))System.arraycopy(held,0,stocks,0,SLOTS);compatibleCapacity=DistillationProfile.BUFFER_PER_BLOCK;}
        else for (var part:members) {
            boolean same=part.localRecipe.equals(activeRecipe);
            if(same || Arrays.stream(part.held).allMatch(n->n==0))compatibleCapacity+=DistillationProfile.BUFFER_PER_BLOCK;
            if(same)for(int i=0;i<SLOTS;i++)stocks[i]+=part.held[i];
        }
        inventoryDirty=false;
    }
    public int stock(int slot) { refreshStocks();return stocks[slot]; }
    private void changed(int slot,int delta) {
        setChanged();FractionatingColumnBlockEntity owner=getControllerBE();
        if (owner!=null) {
            if (owner.valid && !owner.inventoryDirty && localRecipe.equals(owner.activeRecipe)) owner.stocks[slot]+=delta;else owner.inventoryDirty=true;
            owner.syncNeeded=true;owner.setChanged();
        }
    }
    private void process() {
        selectExistingRecipe();
        var fraction = DistillationProfile.fraction(activeRecipe);
        if (airMode()) planProduction(Arrays.stream(DistillationProfile.PRODUCT_AMOUNT).sum(), DistillationProfile.CYCLE_TICKS);
        else if (fraction != null) planProduction(ProductionMonitor.fluids(fraction.outputs()), fraction.ticks());
        if(height<requiredHeight()) { setStatus(totalStored()==0 ? "feed" : "height",false);return; }
        if (level.hasNeighborSignal(worldPosition)) { setStatus("redstone",false);return; }
        boolean active=airMode() ? separate() : fractionate();
        int batch=airMode() ? DistillationProfile.AIR_BATCH : DistillationProfile.fraction(activeRecipe)==null ? Integer.MAX_VALUE : DistillationProfile.fraction(activeRecipe).input().getAmount();
        setStatus(active ? "running" : stock(0)>=batch ? "products_full" : "feed",active);
        if (active) { setChanged();syncNeeded=true; }
    }
    private int totalStored() {int total=0;for(var part:members)for(int n:part.held)total+=n;return total;}
    private void selectExistingRecipe() {
        refreshStocks();if(Arrays.stream(stocks).anyMatch(n->n>0))return;
        for(var part:members)if(Arrays.stream(part.held).anyMatch(n->n>0)){adoptRecipe(part.localRecipe);return;}
    }
    private void adoptRecipe(String recipe) {
        if(activeRecipe.equals(recipe))return;
        activeRecipe=recipe;progress=0;scale=0;inventoryDirty=true;epoch++;syncNeeded=true;setChanged();
        refreshPorts();
    }
    private void refreshPorts() {
        for(var part:members) {
            part.shape();level.invalidateCapabilities(part.worldPosition);
            part.getBlockState().updateNeighbourShapes(level,part.worldPosition,Block.UPDATE_ALL);
        }
        // A new process changes endpoints even though the neighbouring pipe blocks stay in place.
        for(var part:members)for(var side:List.of(Direction.NORTH,Direction.EAST)) {
            var at=part.worldPosition.relative(side);
            if(level.hasChunkAt(at))com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(level,at,level.getBlockState(at));
        }
    }
    private boolean fractionate() {
        var recipe=DistillationProfile.fraction(activeRecipe);if(recipe==null)return false;
        int feed=Math.min(batchCapacity(),stock(0)/recipe.input().getAmount());
        int batches=feed;
        for(int i=0;i<recipe.outputs().size();i++)batches=Math.min(batches,(capacity()-stock(i+1))/recipe.outputs().get(i).getAmount());
        reportBatch(feed, batches);
        if(batches<=0)return false;
        if(progress==0 || scale>batches){scale=batches;progress=0;}progress+=INTERVAL;
        if(progress>=recipe.ticks()) {
            move(0,-recipe.input().getAmount()*scale);
            for(int i=0;i<recipe.outputs().size();i++)move(i+1,recipe.outputs().get(i).getAmount()*scale);
            ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.outputs()) * scale, 0, 0);
            progress=0;
        }
        return true;
    }
    /** A batch is fixed when its progress starts and is consumed and produced together once the cycle ends. */
    private boolean separate() {
        int feed=Math.min(batchCapacity(),stock(DistillationProfile.AIR)/DistillationProfile.AIR_BATCH);
        int batches=feed;
        for (int i=0;i<DistillationProfile.PRODUCT.length;i++) batches=Math.min(batches,(capacity()-stock(DistillationProfile.PRODUCT[i]))/DistillationProfile.PRODUCT_AMOUNT[i]);
        reportBatch(feed, batches);
        if (batches==0) return false;
        if (progress==0 || scale>batches) { scale=batches;progress=0; }
        progress+=INTERVAL;
        if (progress>=DistillationProfile.CYCLE_TICKS) {
            move(DistillationProfile.AIR,-DistillationProfile.AIR_BATCH*scale);
            for (int i=0;i<DistillationProfile.PRODUCT.length;i++) move(DistillationProfile.PRODUCT[i],DistillationProfile.PRODUCT_AMOUNT[i]*scale);
            ProductionMonitor.produced(this, Arrays.stream(DistillationProfile.PRODUCT_AMOUNT).sum() * scale, 0, 0);
            progress=0;
        }
        return true;
    }
    private void planProduction(int amount, int ticks) {
        ProductionMonitor.plan(this, ProductionMonitor.batchRate(amount, batchCapacity(), ticks, INTERVAL) / INTERVAL, 0, 0);
    }
    private void reportBatch(int feed, int batches) {
        ProductionMonitor.limitedBy(this, batches < feed ? "output" : feed < batchCapacity() ? "feed" : "rated");
    }
    private void move(int slot,int amount) {
        int remaining=Math.abs(amount);
        for (var part:members) {
            if(!part.localRecipe.equals(activeRecipe) && Arrays.stream(part.held).anyMatch(n->n>0))continue;
            int n=Math.min(remaining,amount>0 ? DistillationProfile.BUFFER_PER_BLOCK-part.held[slot] : part.held[slot]);
            if (n==0) continue;
            part.localRecipe=activeRecipe;
            part.held[slot]+=amount>0 ? n : -n;
            part.changed(slot,amount>0 ? n : -n);remaining-=n;if (remaining==0) return;
        }
        if (remaining!=0) throw new IllegalStateException("Distillation hold-up accounting mismatch");
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CEDistillation.COLUMN_ENTITY.get(),(be,side)->be.port(side));
    }
    public IFluidHandler port(Direction side) {
        FractionatingColumnBlockEntity owner=getControllerBE();
        if (side==null) return new Port(owner==null ? this : owner,-1,false,false);
        if (owner==null || !owner.ready()) return null;
        int x=worldPosition.getX()-owner.worldPosition.getX(),z=worldPosition.getZ()-owner.worldPosition.getZ(),y=worldPosition.getY()-owner.worldPosition.getY();
        if (side==Direction.EAST && x==owner.width-1 && z==(owner.width-1)/2) {
            if(y==DistillationProfile.feedLayer(owner.height))return new Port(owner,0,true,false);
        }
        if (side==Direction.NORTH && z==0 && x==(owner.width-1)/2) {
            if(owner.airMode()){int outlet=owner.height==DistillationProfile.HEIGHT ? DistillationProfile.outlet(y) : 0;if(outlet>0)return new Port(owner,DistillationProfile.outletSlot(outlet),false,true);}
            else {var recipe=DistillationProfile.fraction(owner.activeRecipe);int slot=recipe==null ? 0 : DistillationProfile.fractionSlot(y,owner.height,recipe.outputs().size());if(slot>0)return new Port(owner,slot,false,true);}
        }
        return null;
    }
    private void setStatus(String value,boolean work) {
        if (!work) ProductionMonitor.limitedBy(this, switch (value) {
            case "products_full" -> "output";
            case "redstone", "feed" -> value;
            default -> "structure";
        });
        if (status.equals(value) && running==work) return;status=value;running=work;syncNeeded=true;setChanged();
    }
    public void vent() {
        if (level==null || level.isClientSide()) return;
        boolean released=Arrays.stream(held).anyMatch(amount->amount>0);
        for(int i=0;i<SLOTS;i++) {var fluid=template(localRecipe,i);if(!fluid.isEmpty() && held[i]>0)net.createplant.safety.ToxicGas.release(level,worldPosition,fluid.copyWithAmount(held[i]));}
        Arrays.fill(held,0);
        FractionatingColumnBlockEntity owner=getControllerBE();if (owner!=null) { owner.inventoryDirty=true;owner.syncNeeded=true; }
        if (released && level instanceof ServerLevel server) server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,12,.3,.3,.3,.02);
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) {
        var owner=getControllerBE();if(owner==null){text.add(Component.translatable(KEY+"status.unloaded"));return true;}
        text.add(Component.translatable(KEY+"status."+owner.status));
        text.add(Component.translatable(KEY+"size",owner.width,owner.width,owner.height,owner.requiredHeight()));
        text.add(Component.translatable(KEY+"progress",Math.round(owner.progress),owner.cycleTicks()));
        var fraction=DistillationProfile.fraction(owner.activeRecipe);
        int layer=worldPosition.getY()-owner.worldPosition.getY(),inlet=layer==DistillationProfile.feedLayer(owner.height) ? 1 : 0;
        int outlet=owner.airMode() ? owner.height==DistillationProfile.HEIGHT ? DistillationProfile.outlet(layer) : 0 : fraction==null ? 0 : DistillationProfile.fractionSlot(layer,owner.height,fraction.outputs().size());
        int slot=owner.airMode() && outlet>0 ? DistillationProfile.outletSlot(outlet) : outlet;
        var fluid=template(owner.activeRecipe,slot);
        if(inlet>0 && slot>0 && !fluid.isEmpty())text.add(Component.translatable(KEY+"inout",layer+1,template(owner.activeRecipe,0).getHoverName(),fluid.getHoverName()));
        else if(inlet>0)text.add(Component.translatable(KEY+"inlet",layer+1,template(owner.activeRecipe,0).getHoverName()));
        else if(slot>0 && !fluid.isEmpty())text.add(Component.translatable(KEY+"draw",layer+1,fluid.getHoverName()));
        else text.add(Component.translatable(KEY+"ports"));
        return true;
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);if (controller!=null) tag.putLong("Controller",controller.asLong());
        tag.putInt("Width",width);tag.putInt("Height",height);tag.putFloat("Progress",progress);tag.putInt("Scale",scale);
        tag.putIntArray("Held",held);
        tag.putString("LocalRecipe",localRecipe);tag.putString("ActiveRecipe",activeRecipe);
        if (packet) { refreshStocks();tag.putIntArray("Stocks",stocks);tag.putInt("Capacity",capacity());tag.putString("Status",status);tag.putBoolean("Valid",valid);tag.putBoolean("Running",running); }
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);controller=tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        width=Math.clamp(tag.getInt("Width"),1,3);height=Math.clamp(tag.getInt("Height"),1,DistillationProfile.HEIGHT);
        float savedProgress=tag.getFloat("Progress");progress=Float.isFinite(savedProgress) ? Math.clamp(savedProgress,0,DistillationProfile.CYCLE_TICKS) : 0;scale=Math.clamp(tag.getInt("Scale"),0,9);
        int[] saved=tag.getIntArray("Held");
        localRecipe=tag.contains("LocalRecipe") ? tag.getString("LocalRecipe") : "air";activeRecipe=tag.contains("ActiveRecipe") ? tag.getString("ActiveRecipe") : "air";
        for (int i=0;i<SLOTS;i++) held[i]=i<saved.length ? Math.clamp(saved[i],0,DistillationProfile.BUFFER_PER_BLOCK) : 0;
        if (packet) { int[] values=tag.getIntArray("Stocks");if (values.length==SLOTS) System.arraycopy(values,0,stocks,0,SLOTS);compatibleCapacity=tag.contains("Capacity") ? tag.getInt("Capacity") : width*width*height*DistillationProfile.BUFFER_PER_BLOCK;status=tag.getString("Status");valid=tag.getBoolean("Valid");running=tag.getBoolean("Running");inventoryDirty=false; }
        else { valid=false;geometryDirty=true;inventoryDirty=true;connectivityPending=true; }
    }
    /**
     * Air pumped in compressed and cooled is liquefied in the column's own cold box, as in a real air separation unit; 1000 mB of it
     * is one batch. The pressure is that of the vessel it is pumped from.
     */
    public static boolean compressedAir(FluidStack stack) { return stack.is(CEProcessFluids.COMPRESSED_AIR.get()); }
    public static final double COLD_BOX_KELVIN=313.15, COLD_BOX_PA=6e5;
    private class Port implements IFluidHandler {
        private final FractionatingColumnBlockEntity owner;
        private final int slot,generation;
        private final boolean input,output;
        Port(FractionatingColumnBlockEntity owner,int slot,boolean input,boolean output) { this.owner=owner;this.slot=slot;this.input=input;this.output=output;generation=owner.epoch; }
        private boolean live() { return !isRemoved() && getControllerBE()==owner && generation==owner.epoch && owner.ready(); }
        @Override public int getTanks() { return slot<0 ? SLOTS : output && owner.airMode() && slot==DistillationProfile.NITROGEN ? 2 : 1; }
        @Override public int getTankCapacity(int tank) { return tank<0 || tank>=getTanks() ? 0 : owner.airMode() && slot==DistillationProfile.NITROGEN && tank==1 ? owner.capacity()/nitrogenRatio() : owner.capacity(); }
        @Override public FluidStack getFluidInTank(int tank) {
            if (isRemoved() || generation!=owner.epoch || tank<0 || tank>=getTanks()) return FluidStack.EMPTY;
            if (owner.airMode() && slot==DistillationProfile.NITROGEN && tank==1) {
                int amount=owner.stock(slot)/nitrogenRatio();return amount==0 ? FluidStack.EMPTY : net.createplant.thermal.FluidThermal.at(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(),amount),77.36);
            }
            int index=slot<0 ? tank : slot,amount=owner.stock(index);
            // Oxygen is held as gas but leaves as liquid: only whole liquid mB show, the rest waits in the column.
            if (owner.airMode() && index==DistillationProfile.OXYGEN) amount/=DistillationProfile.gasPerLiquid(index);
            var template=template(owner.activeRecipe,index);
            return amount==0 || template.isEmpty() ? FluidStack.EMPTY : owner.airMode() && index==DistillationProfile.OXYGEN ? new FluidStack(DistillationProfile.liquid(index),amount) : template.copyWithAmount(amount);
        }
        private String recipeFor(FluidStack stack) { var fraction=DistillationProfile.fraction(stack);return compressedAir(stack) ? "air" : fraction==null ? "" : fraction.id(); }
        @Override public boolean isFluidValid(int tank,FluidStack stack) {
            if(!input || tank!=0 || !live() || slot!=0 || stack.isEmpty() || !net.createplant.thermal.FluidThermal.valid(stack))return false;
            String recipe=recipeFor(stack);return !recipe.isEmpty() && owner.height>=DistillationProfile.requiredHeight(recipe) && (recipe.equals(owner.activeRecipe) || owner.totalStored()==0);
        }
        @Override public int fill(FluidStack stack,FluidAction action) {
            if (!isFluidValid(0,stack)) return 0;
            int n=Math.min(stack.getAmount(),Math.max(0,owner.capacity()-owner.stock(slot)));
            if (n>0 && action.execute() && !level.isClientSide()) {owner.adoptRecipe(recipeFor(stack));owner.move(slot,n);}return n;
        }
        private net.minecraft.world.level.material.Fluid leaving() { return owner.airMode() && slot==DistillationProfile.OXYGEN ? DistillationProfile.liquid(slot) : template(owner.activeRecipe,slot).getFluid(); }
        @Override public FluidStack drain(FluidStack request,FluidAction action) {
            if (output && owner.airMode() && slot==DistillationProfile.NITROGEN && request.is(CEProcessFluids.LIQUID_NITROGEN.get())) {
                if (!live() || request.isEmpty()) return FluidStack.EMPTY;
                int amount=Math.min(request.getAmount(),owner.stock(slot)/nitrogenRatio());if (amount==0) return FluidStack.EMPTY;
                if (action.execute() && !level.isClientSide()) owner.move(slot,-amount*nitrogenRatio());
                return net.createplant.thermal.FluidThermal.at(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(),amount),77.36);
            }
            return output && request.is(leaving()) ? drain(request.getAmount(),action) : FluidStack.EMPTY;
        }
        private int nitrogenRatio() { return net.createplant.thermal.PhaseChange.ofLiquid(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(),1)).ratio(); }
        @Override public FluidStack drain(int amount,FluidAction action) {
            if (!output || !live() || amount<=0) return FluidStack.EMPTY;
            int per=owner.airMode() && slot==DistillationProfile.OXYGEN ? DistillationProfile.gasPerLiquid(slot) : 1;
            int n=Math.min(amount,owner.stock(slot)/per);if (n==0) return FluidStack.EMPTY;
            if (action.execute() && !level.isClientSide()) owner.move(slot,-n*per);return new FluidStack(leaving(),n);
        }
    }
}
