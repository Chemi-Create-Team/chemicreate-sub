package net.createplant.block.entity;

import java.util.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.block.CoolerRadiatorBlock;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessBlocks;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** One controller per contiguous bank of complete 3x3x4 fin-fan cells. */
public class RadiatorBlockEntity extends KineticBlockEntity {
    /** Four eight-cell banks reject the latent-heat share of the 765 MW rating with headroom for control transients. */
    public static final int CONDENSER_FLOW=4000, CONDENSER_BUFFER=32000;
    private final ThermalFluidTank input=new ThermalFluidTank(CONDENSER_BUFFER,s->changed()),output=new ThermalFluidTank(CONDENSER_BUFFER,s->changed());
    private BlockPos controller;
    private List<BlockPos> fans=List.of();
    private int clock,flow,displayedCells,inletEnd=-1,portEpoch,portEnd=-1;
    private Direction pipeDirection;
    private float fanRpm;
    public RadiatorBlockEntity(BlockPos pos,BlockState state) { super(CEProcessBlocks.COOLER_RADIATOR_DRIVE.get(),pos,state);setLazyTickRate(40); }
    private void changed() { setChanged(); }
    public double gasPressure(boolean entering){
        var root=owner();return root==null ? 1e5 : net.createplant.transport.FluidHazards.of((entering ? root.input : root.output).getFluid()).bar()*1e5;
    }
    public boolean isInlet(){var master=owner();return master!=null && master.inletEnd==portEnd;}
    public List<BlockPos> ruptureParts(){var result=new ArrayList<BlockPos>();for(var fan:fans)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++){var p=fan.offset(x,y-3,z);if(level.hasChunkAt(p))result.add(p);}if(result.isEmpty())result.add(worldPosition);input.setFluid(FluidStack.EMPTY);output.setFluid(FluidStack.EMPTY);return result;}
    @Override public void addBehaviours(List<BlockEntityBehaviour> list) {

    }
    public RadiatorBlockEntity owner() {
        if (isRemoved() || controller==null || level==null || !level.hasChunkAt(controller)) return null;
        return level.getBlockEntity(controller) instanceof RadiatorBlockEntity be && !be.isRemoved() ? be : null;
    }
    public void partChanged(BlockPos pos) {
        var master=owner();if(master==null || !level.hasChunkAt(pos)) return;
        var state=level.getBlockState(pos);
        if(state.is(CEProcessBlocks.COOLER_RADIATOR.get()) || state.is(CEProcessBlocks.COOLING_FAN.get()) || state.is(CEProcessBlocks.COOLING_FAN_SHROUD.get())) return;
        for(var fan:master.fans) if(Math.abs(pos.getX()-fan.getX())<=1 && Math.abs(pos.getZ()-fan.getZ())<=1 && pos.getY()<=fan.getY() && pos.getY()>=fan.getY()-3) {
            master.fans=List.of();master.flow=0;master.fanRpm=0;master.setChanged();master.sendData();return;
        }
    }
    private boolean cell(BlockPos fan) {
        if (!level.hasChunkAt(fan) || !level.getBlockState(fan).is(CEProcessBlocks.COOLING_FAN.get())) return false;
        for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) for(int y=0;y<=3;y++) {
            var at=fan.offset(x,y-3,z);if (!level.hasChunkAt(at)) return false;
            if (!level.getBlockState(at).is(y<3 ? CEProcessBlocks.COOLER_RADIATOR.get() : x==0 && z==0 ? CEProcessBlocks.COOLING_FAN.get() : CEProcessBlocks.COOLING_FAN_SHROUD.get())) return false;
        }
        return true;
    }
    @Override public void lazyTick() {
        super.lazyTick();if(level.isClientSide) return;
        var owner=owner();if(owner!=null && owner!=this) return;
        BlockPos center=null;
        for(int x=-2;x<=2 && center==null;x++) for(int z=-2;z<=2 && center==null;z++) for(int y=1;y<=3;y++) {
            var candidate=worldPosition.offset(x,y,z);if(cell(candidate)) {center=candidate;break;}
        }
        if(center==null) {fans=List.of();controller=null;return;}
        Direction along=level.getBlockState(center.below(3)).getValue(CoolerRadiatorBlock.FACING);
        if(cell(center.east(3)) || cell(center.west(3))) along=Direction.WEST;
        else if(cell(center.north(3)) || cell(center.south(3))) along=Direction.NORTH;
        for(int n=0;n<7 && cell(center.relative(along.getOpposite(),3));n++) center=center.relative(along.getOpposite(),3);
        var bank=new ArrayList<BlockPos>();
        for(int n=0;n<8;n++) {var fan=center.relative(along,n*3);if(!cell(fan)) break;
            bank.add(fan);}
        if(bank.isEmpty()) return;
        BlockPos root=center.below(3);
        RadiatorBlockEntity occupied=null;
        for(var fan:bank) if(level.getBlockEntity(fan.below(3)) instanceof RadiatorBlockEntity previous
                && previous.owner()==previous && (!previous.input.isEmpty() || !previous.output.isEmpty())) {
            if(occupied!=null && occupied!=previous) return;
            occupied=previous;root=previous.getBlockPos();
        }
        if(!root.equals(worldPosition) && (!input.isEmpty() || !output.isEmpty())) return;
        if(level.getBlockEntity(root) instanceof RadiatorBlockEntity master) {
            boolean bankChanged=!bank.equals(master.fans) || master.pipeDirection!=along;
            master.pipeDirection=along;
            if (bankChanged) master.portEpoch++;
            controller=root;master.controller=root;master.fans=List.copyOf(bank);
            for(var fan:bank) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int y=0;y<3;y++) {
                var at=fan.offset(x,y-3,z);if(!(level.getBlockEntity(at) instanceof RadiatorBlockEntity part)) continue;
                boolean moved=!root.equals(part.controller);part.controller=root;
                var state=part.getBlockState();boolean first=fan.equals(bank.getFirst()) && x==-along.getStepX() && z==-along.getStepZ() && y==1;
                boolean last=fan.equals(bank.getLast()) && x==along.getStepX() && z==along.getStepZ() && y==1;
                part.portEnd=first ? 0 : last ? 1 : -1;
                var next=state.setValue(CoolerRadiatorBlock.PORT,first || last).setValue(CoolerRadiatorBlock.SECTION,CoolerRadiatorBlock.Section.values()[y+1]).setValue(CoolerRadiatorBlock.FACING,first ? along.getOpposite() : along).setValue(CoolerRadiatorBlock.DRIVE,x==0 && z==0).setValue(CoolerRadiatorBlock.OUTLET,y==0);
                if(next!=state) KineticBlockEntity.switchToBlockState(level,at,next);
                if(moved || next!=state || bankChanged) {part.setChanged();level.invalidateCapabilities(at);part.sendData();}
            }
            if(bankChanged) master.sendData();
        }
    }

    public int fanCount() { return level!=null && level.isClientSide ? displayedCells : fans.size(); }
    public int actualFlow() { return flow; }
    public float fanRpm() { return fanRpm; }




    @Override public void tick() {
        super.tick();if(level.isClientSide || ++clock%5!=0 || owner()!=this)return;
        int previousFlow=flow;float previousRpm=fanRpm;fanRpm=0;flow=0;
        for(var pos:fans)if(level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof CoolingFanBlockEntity fan)fanRpm+=Math.abs(fan.getSpeed());
        try{
            if(fans.isEmpty() || clock%40==0 && fans.stream().anyMatch(f->!cell(f))){
                fans=List.of();controller=null;fanRpm=0;ProductionMonitor.limitedBy(this, "structure");return;
            }
            var recipe=ThermalStreams.cooling(input.getFluid());
            if (recipe == null) { ProductionMonitor.limitedBy(this, "feed");return; }
            double season = SeasonalThermal.cooling(level);
            int maxRpm = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();
            int maximum = Math.max(1, (int) (100d * fans.size() * maxRpm / 64 * season));
            ProductionMonitor.plan(this, recipe.outputAmount() * maximum * 4d, 0, 0);
            if(fanRpm<=0){ProductionMonitor.limitedBy(this, "speed");return;}
            int capacity = Math.max(1,(int)(100*fanRpm/64*season));
            int feed = Math.min(input.getFluidAmount()/recipe.inputAmount(),capacity);
            int batches = Math.min(feed, output.fill(recipe.result(input.getFluid(),feed),IFluidHandler.FluidAction.SIMULATE)/recipe.outputAmount());
            ProductionMonitor.limitedBy(this, batches < feed ? "output" : feed < capacity ? "feed" : capacity < maximum ? "speed" : "rated");
            if(batches<=0)return;
            var result=recipe.result(input.getFluid(),batches);input.drain(recipe.inputAmount()*batches,IFluidHandler.FluidAction.EXECUTE);
            ProductionMonitor.produced(this, output.fill(result,IFluidHandler.FluidAction.EXECUTE), 0, 0);
            flow=recipe.inputAmount()*batches;setChanged();
        }finally{if(clock%20==0 && flow>0 || previousFlow>0 && flow==0 || previousRpm!=fanRpm)sendData();}
    }
    public IFluidHandler port(Direction side) {
        var master=owner();if(master==null) return null;
        if (master.pipeDirection==null || master.fanCount()==0) return null;
        int end=portEnd;
        Direction outward=getBlockState().getValue(CoolerRadiatorBlock.FACING);
        if (side!=null && (end<0 || !getBlockState().getValue(CoolerRadiatorBlock.PORT) || side!=outward)) return null;
        int epoch=master.portEpoch;
        return new IFluidHandler() {
            private boolean live() {return owner()==master && master.fanCount()>0 && master.portEpoch==epoch && (side==null || portEnd==end && getBlockState().getValue(CoolerRadiatorBlock.PORT) && getBlockState().getValue(CoolerRadiatorBlock.FACING)==outward);}
            @Override public int getTanks(){return 2;}
            @Override public FluidStack getFluidInTank(int i){return i<0 || i>1 || !live() ? FluidStack.EMPTY : (i==0 ? master.input : master.output).getFluid().copy();}
            @Override public int getTankCapacity(int i){return i<0 || i>1 ? 0 : (i==0 ? master.input : master.output).getCapacity();}
            private boolean accepts(){return side!=null && live() && (master.inletEnd==end || master.input.isEmpty() && master.output.isEmpty() || master.inletEnd<0);}
            @Override public boolean isFluidValid(int i,FluidStack s){return i==0 && !s.isEmpty() && ThermalStreams.cooling(s)!=null && accepts();}
            @Override public int fill(FluidStack s,FluidAction a){
                if(!isFluidValid(0,s)) return 0;
                int n=master.input.fill(s,a);
                if(n>0 && a.execute() && master.inletEnd!=end) {master.inletEnd=end;master.setChanged();}
                return n;
            }
            @Override public FluidStack drain(int n,FluidAction a){return side!=null && live() && master.inletEnd>=0 && end!=master.inletEnd ? master.output.drain(n,a) : FluidStack.EMPTY;}
            @Override public FluidStack drain(FluidStack s,FluidAction a){return side!=null && live() && master.inletEnd>=0 && end!=master.inletEnd ? master.output.drain(s,a) : FluidStack.EMPTY;}
        };
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CEProcessBlocks.COOLER_RADIATOR_DRIVE.get(),(be,side)->be.port(side));}
    @Override public boolean addToGoggleTooltip(List<Component> lines,boolean shift) {
        var root=owner();if(root==null || root.fanCount()==0){lines.add(Component.translatable("radiator.create_refinery_n_reactors.incomplete"));return true;}
        lines.add(Component.translatable("grade.create_refinery_n_reactors.radiator",root.fanCount(),root.flow/5d));
        if(!root.input.isEmpty())lines.add(FluidThermal.readout(root.input.getFluid()));if(!root.output.isEmpty())lines.add(FluidThermal.readout(root.output.getFluid()));return true;
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);if(controller!=null)tag.putLong("Controller",controller.asLong());
        tag.putInt("InletEnd",inletEnd);tag.putInt("PortEnd",portEnd);
        if(pipeDirection!=null) tag.putString("PipeDirection",pipeDirection.getName());
        tag.put("In",input.writeToNBT(lookup,new CompoundTag()));tag.put("Out",output.writeToNBT(lookup,new CompoundTag()));
        tag.putInt("FanCells",fans.size());tag.putInt("Flow",flow);
        if(packet) tag.putFloat("FanRpm",fanRpm);
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);if(!packet){portEpoch++;if(level!=null)for(var fan:fans)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)level.invalidateCapabilities(fan.offset(x,y-3,z));}controller=tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        pipeDirection=Direction.byName(tag.getString("PipeDirection"));portEnd=tag.contains("PortEnd") ? tag.getInt("PortEnd") : -1;
        inletEnd=tag.contains("InletEnd") ? tag.getInt("InletEnd") : -1;
        input.readFromNBT(lookup,tag.getCompound("In"));output.readFromNBT(lookup,tag.getCompound("Out"));
        displayedCells=Math.clamp(tag.getInt("FanCells"),0,8);flow=tag.getInt("Flow");
        float rpm=tag.getFloat("FanRpm");fanRpm=packet && Float.isFinite(rpm) ? Math.clamp(rpm,0,4096) : 0;
    }
}
