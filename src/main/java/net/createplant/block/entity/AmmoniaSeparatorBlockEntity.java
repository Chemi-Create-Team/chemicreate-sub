package net.createplant.block.entity;

import java.util.List;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Fixed ammonia/coolant separation and independent graded water/steam pass-through buffers. */
public class AmmoniaSeparatorBlockEntity extends PlantFluidTankBlockEntity {
    private final ThermalFluidTank feed=tank(),liquid=tank(),recycle=tank(),water=tank(),steam=tank();
    private double progress;
    private int clock,portEpoch;
    private boolean syncNeeded, separating;
    private Direction front=Direction.NORTH;
    public AmmoniaSeparatorBlockEntity(BlockPos pos,BlockState state){super(pos,state);}
    private ThermalFluidTank tank(){return new ThermalFluidTank(16000,f->changed()){@Override protected int storageRatio(){return Grade.Pressure.HIGH.storageRatio();}};}
    @Override protected void addVesselBehaviour(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours){ }
    @Override public void updateBoilerState(){ }
    @Override public int getMaxWidth(){return 1;}
    @Override public int getMaxLength(Direction.Axis axis,int width){return axis==Direction.Axis.Y ? 6 : 1;}
    @Override public void toggleWindows(){ }
    @Override public void setWindows(boolean windows){super.setWindows(false);}
    public Direction front(){return getControllerBE() instanceof AmmoniaSeparatorBlockEntity root ? root.front : front;}
    public void face(Direction side){if(side.getAxis().isHorizontal()){front=side;changed();}}
    @Override public void notifyMultiUpdated(){
        super.notifyMultiUpdated();
        if(level!=null){front=front();var state=getBlockState().setValue(net.createplant.block.AmmoniaSeparatorBlock.FACING,front).setValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE,com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN);if(!state.equals(getBlockState()))level.setBlock(worldPosition,state,2);}
    }
    public float liquidFraction(){return Math.clamp((float)(liquid.getFluidAmount()+water.getFluidAmount())/volume(),0,1);}
    public FluidStack gaugeLiquid(){return liquid.isEmpty() ? water.getFluid().copy() : liquid.getFluid().copy();}
    public static void register(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CETransport.AMMONIA_SEPARATOR_ENTITY.get(),(be,side)->net.createplant.mixture.GasMixturePort.both(be.port(side),be));}
    private static final class Recipes { static final List<ProcessRecipe> ALL=ProcessRecipe.all().stream().filter(r->r.module()==ProcessModule.GAS_LIQUID_SEPARATOR).toList(); }
    public static List<ProcessRecipe> recipes(){return Recipes.ALL;}
    private static ProcessRecipe recipe(FluidStack input){return recipes().stream().filter(r->r.accepts(0,input)).findFirst().orElse(null);}
    private void changed(){syncNeeded=true;setChanged();}
    public FluidStack feed(){return feed.getFluid().copy();}
    public FluidStack liquid(){return liquid.getFluid().copy();}
    public FluidStack recycle(){return recycle.getFluid().copy();}
    public FluidStack water(){return water.getFluid().copy();}
    public FluidStack steam(){return steam.getFluid().copy();}
    public boolean ready(){return isController() && getHeight()>=2 && !isRemoved() && level!=null;}
    public boolean separating(){return ready() && separating;}
    private boolean chemicalContents(){return !feed.isEmpty() || !liquid.isEmpty() || !recycle.isEmpty() || progress>0;}
    public boolean hasProcessContents(){return chemicalContents() || !water.isEmpty() || !steam.isEmpty();}
    private AmmoniaSeparatorBlockEntity owner(){return getControllerBE() instanceof AmmoniaSeparatorBlockEntity be && be.ready() ? be : null;}
    private int volume(){return Math.max(1,getWidth()*getWidth()*getHeight())*16000;}
    private void resize(){for(var tank:List.of(feed,liquid,recycle,water,steam)){var kept=tank.getFluid().copy();tank.setCapacity(volume());tank.setFluid(kept);}}
    @Override public void setWidth(int width){if(width!=getWidth())portEpoch++;super.setWidth(width);resize();}
    @Override public void setHeight(int height){if(height!=getHeight())portEpoch++;super.setHeight(height);resize();}
    private IFluidHandler port(Direction side){
        var root=owner();if(root==null || side==null)return null;int layer=worldPosition.getY()-root.worldPosition.getY();
        if(side==Direction.UP)return layer==root.getHeight()-1 ? new Port(root,Mode.GAS) : null;
        if(side==Direction.DOWN)return layer==0 ? new Port(root,Mode.LIQUID) : null;
        return layer==root.getHeight()-1 ? new Port(root,Mode.INLET) : null;
    }
    @Override public void tick(){super.tick();if(level==null || level.isClientSide || !ready())return;if(++clock%5==0)process(.25);if(syncNeeded && clock%10==0){syncNeeded=false;sendData();}}
    private FluidStack product(ProcessRecipe recipe,int slot,int count){return product(recipe,slot,count,feed.getFluid());}
    private FluidStack product(ProcessRecipe recipe,int slot,int count,FluidStack source){var result=recipe.outputs().get(slot).copyWithAmount(recipe.outputs().get(slot).getAmount()*count);if(!result.isEmpty() && FluidForm.HOT_REACTOR_COOLANT.matches(source))FluidThermal.processComponents(source,result);return result;}
    public void process(double seconds){
        if(!ready() || !Double.isFinite(seconds) || seconds<=0)return;
        var recipe=water.isEmpty() && steam.isEmpty() ? recipe(feed.getFluid()) : null;
        if(recipe==null){ProductionMonitor.limitedBy(this,"feed");setSeparating(false);return;}
        int size=getWidth()*getWidth()*getHeight();
        ProductionMonitor.plan(this,ProductionMonitor.batchRate(ProductionMonitor.fluids(recipe.outputs()),size,((recipe.ticks()+4)/5)*5,1),0,0);
        int feedCount=feed.getFluidAmount()/recipe.inputs().getFirst().getAmount();
        int count=Math.min(size,feedCount);
        count=Math.min(count,liquid.fill(product(recipe,0,count),IFluidHandler.FluidAction.SIMULATE)/recipe.outputs().get(0).getAmount());
        count=Math.min(count,recycle.fill(product(recipe,1,count),IFluidHandler.FluidAction.SIMULATE)/recipe.outputs().get(1).getAmount());
        ProductionMonitor.limitedBy(this,count<Math.min(size,feedCount) ? "output" : feedCount<size ? "feed" : "rated");
        setSeparating(count>0);
        if(count<=0)return;progress+=Math.min(1,seconds)*20;
        if(progress>=recipe.ticks()){
            var madeLiquid=product(recipe,0,count);var madeGas=product(recipe,1,count);feed.drain(recipe.inputs().getFirst().getAmount()*count,IFluidHandler.FluidAction.EXECUTE);
            liquid.fill(madeLiquid,IFluidHandler.FluidAction.EXECUTE);recycle.fill(madeGas,IFluidHandler.FluidAction.EXECUTE);progress=0;
            ProductionMonitor.produced(this,madeLiquid.getAmount()+madeGas.getAmount(),0,0);
        }changed();
    }
    /** A stream that is both liquid and gas is split here: liquid to the bottom outlet, gas to the top. Returns the amount of the stream taken. */
    private int separateStream(FluidStack stream,IFluidHandler.FluidAction action){
        if(!ready() || !feed.isEmpty() || progress>0)return 0;
        boolean isWater=stream.is(Fluids.WATER);
        if(isWater ? chemicalContents() : !water.isEmpty() || !steam.isEmpty())return 0;
        var liquidTank=isWater ? water : liquid;var gasTank=isWater ? steam : recycle;
        int units=stream.getAmount();
        for(int attempt=0;attempt<4 && units>0;attempt++){
            var split=TwoPhase.split(stream,units);
            int fitLiquid=split.liquid().isEmpty() ? 0 : liquidTank.fillAt(split.liquid(),split.kelvin(),0,IFluidHandler.FluidAction.SIMULATE);
            int fitGas=split.gas().isEmpty() ? 0 : gasTank.fillAt(split.gas(),split.kelvin(),1,IFluidHandler.FluidAction.SIMULATE);
            if(fitLiquid==split.liquid().getAmount() && fitGas==split.gas().getAmount()){
                if(action.execute()){
                    if(!split.liquid().isEmpty())liquidTank.fillAt(split.liquid(),split.kelvin(),0,IFluidHandler.FluidAction.EXECUTE);
                    if(!split.gas().isEmpty())gasTank.fillAt(split.gas(),split.kelvin(),1,IFluidHandler.FluidAction.EXECUTE);
                    changed();
                }
                return units;
            }
            double scale=Math.min(split.liquid().isEmpty() ? 1 : (double)fitLiquid/split.liquid().getAmount(),split.gas().isEmpty() ? 1 : (double)fitGas/split.gas().getAmount());
            units=(int)(units*scale);
        }
        return 0;
    }
    private void setSeparating(boolean active){if(separating!=active){separating=active;syncNeeded=true;}}
    @Override public void removeController(boolean keepFluids){front=front();portEpoch++;super.removeController(keepFluids);}
    public void vent(){
        if(level==null || level.isClientSide)return;
        if(hasProcessContents() && level instanceof ServerLevel server)server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,24,.6,.8,.6,.03);
        for(var tank:List.of(feed,liquid,recycle,water,steam)){net.createplant.safety.ToxicGas.release(level,worldPosition,tank.getFluid().copy());tank.setFluid(FluidStack.EMPTY);}progress=0;changed();
    }
    private enum Mode{INLET,GAS,LIQUID}
    private final class Port implements IFluidHandler {
        private final AmmoniaSeparatorBlockEntity root;private final Mode mode;private final int epoch;
        Port(AmmoniaSeparatorBlockEntity root,Mode mode){this.root=root;this.mode=mode;epoch=root.portEpoch;}
        private boolean live(){return !isRemoved() && level!=null && level.getBlockEntity(worldPosition)==AmmoniaSeparatorBlockEntity.this && owner()==root && epoch==root.portEpoch;}
        private ThermalFluidTank output(){return mode==Mode.GAS ? root.recycle.isEmpty() ? root.steam : root.recycle : root.liquid.isEmpty() ? root.water : root.liquid;}
        private ThermalFluidTank input(int slot){return slot==0 ? root.feed : slot==1 ? root.water : root.steam;}
        public int getTanks(){return mode==Mode.INLET ? 3 : 1;}
        public FluidStack getFluidInTank(int slot){return !live() || slot<0 || slot>=getTanks() ? FluidStack.EMPTY : (mode==Mode.INLET ? input(slot) : output()).getFluidInTank(0).copy();}
        public int getTankCapacity(int slot){return !live() || slot<0 || slot>=getTanks() ? 0 : (mode==Mode.INLET ? input(slot) : output()).getCapacity();}
        public boolean isFluidValid(int slot,FluidStack stack){
            if(!live() || mode!=Mode.INLET || slot<0 || slot>2 || stack.isEmpty())return false;
            if(slot==0 && TwoPhase.is(stack))return root.separateStream(stack,IFluidHandler.FluidAction.SIMULATE)>0;
            if(slot!=0)return !root.chemicalContents() && (slot==1 ? net.createplant.thermal.FluidState.liquidWater(stack) : SteamGrades.of(stack)!=null);
            var recipe=recipe(stack);return recipe!=null && root.water.isEmpty() && root.steam.isEmpty() && (root.liquid.isEmpty() || FluidThermal.compatible(root.liquid.getFluid(),root.product(recipe,0,1,stack))) && (root.recycle.isEmpty() || FluidThermal.compatible(root.recycle.getFluid(),root.product(recipe,1,1,stack)));
        }
        public int fill(FluidStack stack,FluidAction action){if(TwoPhase.is(stack))return live() && mode==Mode.INLET ? root.separateStream(stack,action) : 0;for(int slot=0;slot<getTanks();slot++)if(isFluidValid(slot,stack))return input(slot).fill(stack,action);return 0;}
        public FluidStack drain(int amount,FluidAction action){return !live() || mode==Mode.INLET ? FluidStack.EMPTY : output().drain(amount,action);}
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidThermal.compatible(getFluidInTank(0),stack) ? drain(stack.getAmount(),action) : FluidStack.EMPTY;}
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking){
        var root=owner();text.add(getBlockState().getBlock().getName());if(root==null){text.add(Component.translatable("separator.create_refinery_n_reactors.short"));return true;}
        var source=root.feed.isEmpty() ? root.steam.getFluid() : root.feed.getFluid();if(!source.isEmpty())text.add(FluidThermal.readout(source));
        text.add(Component.translatable("grade.create_refinery_n_reactors.separator",root.feed.getFluidAmount(),root.liquid.getFluidAmount()+root.water.getFluidAmount(),root.recycle.getFluidAmount()+root.steam.getFluidAmount()));return true;
    }
    @Override public void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet){super.write(tag,lookup,packet);var tanks=List.of(feed,liquid,recycle,water,steam);var names=List.of("Feed","Liquid","Recycle","SeparatorWater","SeparatorSteam");for(int i=0;i<tanks.size();i++)tag.put(names.get(i),tanks.get(i).writeToNBT(lookup,new CompoundTag()));tag.putDouble("Progress",progress);tag.putString("Front",front.getSerializedName());if(packet)tag.putBoolean("Separating",separating);}
    @Override public void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet){super.read(tag,lookup,packet);resize();var tanks=List.of(feed,liquid,recycle,water,steam);var names=List.of("Feed","Liquid","Recycle","SeparatorWater","SeparatorSteam");for(int i=0;i<tanks.size();i++)tanks.get(i).readFromNBT(lookup,tag.getCompound(names.get(i)));var recipe=recipe(feed.getFluid());double saved=tag.getDouble("Progress");progress=recipe!=null && Double.isFinite(saved) ? Math.clamp(saved,0,recipe.ticks()-1) : 0;var side=Direction.byName(tag.getString("Front"));front=side!=null && side.getAxis().isHorizontal() ? side : Direction.NORTH;separating=packet && tag.getBoolean("Separating");portEpoch++;if(level!=null)invalidateCapabilities();}
}
