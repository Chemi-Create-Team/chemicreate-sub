package net.createplant.block.entity;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.createplant.block.GasHolderGeometry;
import net.createplant.chemistry.WasteGas;
import net.createplant.contraptions.GasHolderSleeveContraption;
import net.createplant.contraptions.GasHolderSleeveEntity;
import net.createplant.registry.CEGasBell;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.GasTankDrain;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.transport.TransportConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Native Create tank connectivity owns one inventory and three finite telescopic collision bodies. */
public class GasHolderBlockEntity extends FluidTankBlockEntity implements IControlContraption {
    public static final double SPEED=.125,PRESSURE=1.02e5;
    private static final String LANG="gas_bell.create_refinery_n_reactors.";
    private final ControlledContraptionEntity[] sleeves=new ControlledContraptionEntity[3];
    private final UUID[] sleeveIds=new UUID[3];
    private GasHolderGeometry geometry;
    private List<List<AABB>> collision;
    private double lift,previousLift,correction;
    private int missingTicks;
    private boolean dirty,pending=true,paused,blocked,failed;
    private String reason="";
    private final GasTankDrain sump = new GasTankDrain(() -> { dirty=true;setChanged(); });

    public GasHolderBlockEntity(BlockPos pos,BlockState state) { super(CEGasBell.TANK_ENTITY.get(),pos,state); }
    @Override protected SmartFluidTank createInventory() { return new net.createplant.thermal.GradeFluidTank(getCapacityMultiplier(),this::onFluidStackChanged,this); }
    @Override protected void onFluidStackChanged(FluidStack fluid) { dirty=true;setChanged(); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> list) { }
    @Override public void updateBoilerState() { }
    @Override public GasHolderBlockEntity getControllerBE() {
        if (isController() || level==null) return this;
        return level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof GasHolderBlockEntity owner ? owner : null;
    }
    public ThermalFluidTank gas() { return (ThermalFluidTank)tankInventory; }
    public int capacity() { return tankInventory.getCapacity(); }
    public double travel() { return height<2 ? 0 : 3*(height-.5); }
    public double target() { return capacity()<=0 ? 0 : travel()*gas().getFluidAmount()/capacity(); }
    public double offset() { return lift; }
    public double renderedLift(float partial) { return net.minecraft.util.Mth.lerp(partial,previousLift,lift); }
    public double pressure() { return !formed() || gas().isEmpty() ? 0 : PRESSURE; }
    public boolean formed() { return isController() && height>=2 && height<=GasHolderGeometry.MAX_HEIGHT && !failed; }
    public String status() { return paused ? "unloaded" : failed ? reason : height<2 ? "height" : blocked ? "blocked" : !reason.isEmpty() ? reason : WasteGas.contaminated(gas().getFluid()) ? "contaminated" : "ready"; }
    public ControlledContraptionEntity sleeve(int stage) { return sleeves[stage]; }
    public GasHolderGeometry geometry() { return geometry; }
    private boolean live() { return level!=null && !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition)==this; }
    private boolean loaded() { for (int x:new int[]{0,width-1}) for (int z:new int[]{0,width-1}) if (!level.hasChunkAt(worldPosition.offset(x,0,z))) return false;return true; }
    private boolean attached() { for (var sleeve:sleeves) if (sleeve==null || !sleeve.isAlive()) return false;return true; }
    private boolean usable() { return live() && formed() && attached() && loaded() && !paused && !pending; }
    @Override public int getMaxWidth() { return GasHolderGeometry.MAX_WIDTH; }
    @Override public int getMaxLength(Direction.Axis axis,int width) { return axis==Direction.Axis.Y ? GasHolderGeometry.MAX_HEIGHT : getMaxWidth(); }
    @Override public void setWindows(boolean enabled) {
        if (width<=3) {super.setWindows(enabled);return;}
        window=enabled;int low=(width-1)/2,high=width/2;
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            var at=worldPosition.offset(x,y,z);var state=level.getBlockState(at);
            if (!state.is(CEGasBell.TANK.get())) continue;
            boolean middle=(x==0 || x==width-1) && z>=low && z<=high || (z==0 || z==width-1) && x>=low && x<=high;
            var shape=enabled && middle ? FluidTankBlock.Shape.WINDOW : FluidTankBlock.Shape.PLAIN;
            var next=state.setValue(FluidTankBlock.SHAPE,shape);
            if (state!=next) {level.setBlock(at,next,Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);level.getChunkSource().getLightEngine().checkBlock(at);}
        }
    }
    @Override public void notifyMultiUpdated() { super.notifyMultiUpdated();if (gas().isEmpty() && reason.equals("removed")) {failed=false;reason="";}pending=true;invalidateRenderBoundingBox(); }
    @Override public void setController(BlockPos pos) { if (!pos.equals(worldPosition)) discardSleeves();super.setController(pos); }
    @Override public void removeController(boolean keepFluids) { discardSleeves();super.removeController(keepFluids);pending=true; }
    public void purge() {
        if (!live() || level.isClientSide) return;
        var out=gas().drain(gas().getFluidAmount(),IFluidHandler.FluidAction.EXECUTE);
        if (!out.isEmpty()) {ToxicGas.release(level,worldPosition,out);level.playSound(null,worldPosition,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.8f,.7f);}
        failed=false;reason="";pending=true;dirty=true;sendDataImmediately();
    }
    public void breakHolder() { var owner=getControllerBE();if (owner!=null) owner.fail("removed"); }
    private void discardSleeves() {
        if (sleeves==null) return;
        for (int i=0;i<3;i++) {if (sleeves[i]!=null && sleeves[i].isAlive()) sleeves[i].discard();sleeves[i]=null;sleeveIds[i]=null;}
    }
    private void fail(String failure) {
        if (level==null || level.isClientSide) return;
        failed=true;reason=failure;pending=false;blocked=false;paused=false;
        var lost=gas().drain(gas().getFluidAmount(),IFluidHandler.FluidAction.EXECUTE);discardSleeves();lift=0;
        if (!lost.isEmpty()) ToxicGas.release(level,worldPosition,lost);
        dirty=true;setChanged();sendDataImmediately();
    }
    private final IFluidHandler port=new IFluidHandler() {
        private GasHolderBlockEntity owner() { return live() ? getControllerBE() : null; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { var o=owner();return tank==0 && o!=null ? o.gas().getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { var o=owner();return tank==0 && o!=null ? o.capacity() : 0; }
        @Override public boolean isFluidValid(int tank,FluidStack fluid) { return tank==0 && !fluid.isEmpty() && FluidThermal.valid(fluid) && net.createplant.thermal.TankGrade.HOLDER.accepts(fluid); }
        @Override public int fill(FluidStack incoming,FluidAction action) {
            var o=owner();if (o==null || !o.usable() || o.blocked || action.execute() && level.isClientSide || !isFluidValid(0,incoming)) return 0;
            var stored=o.gas().getFluid();
            if (!stored.isEmpty() && !FluidThermal.mixable(stored,incoming) && !GasTankDrain.sameGasWithSteam(stored,incoming)) return 0;
            return o.gas().fill(incoming,action);
        }
        @Override public FluidStack drain(int amount,FluidAction action) {
            var o=owner();return o==null || !o.usable() || amount<=0 || action.execute() && level.isClientSide ? FluidStack.EMPTY : o.gas().drain(amount,action);
        }
        @Override public FluidStack drain(FluidStack request,FluidAction action) {
            var o=owner();return o==null || !o.usable() || action.execute() && level.isClientSide ? FluidStack.EMPTY : o.gas().drain(request,action);
        }
    };
    private final IFluidHandler upperPort=new IFluidHandler() {
        private boolean allowed() { return live() && !getBlockState().getValue(net.createplant.block.GasHolderBlock.TOP); }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return allowed() ? port.getFluidInTank(tank) : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return allowed() ? port.getTankCapacity(tank) : 0; }
        @Override public boolean isFluidValid(int tank,FluidStack fluid) { return allowed() && port.isFluidValid(tank,fluid); }
        @Override public int fill(FluidStack fluid,FluidAction action) { return allowed() ? port.fill(fluid,action) : 0; }
        @Override public FluidStack drain(int amount,FluidAction action) { return allowed() ? port.drain(amount,action) : FluidStack.EMPTY; }
        @Override public FluidStack drain(FluidStack fluid,FluidAction action) { return allowed() ? port.drain(fluid,action) : FluidStack.EMPTY; }
    };
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CEGasBell.TANK_ENTITY.get(),(be,side)-> {
            if (side==Direction.UP && be.getBlockState().getValue(net.createplant.block.GasHolderBlock.TOP)) return null;
            var root=be.getControllerBE();
            if (root==null) return null;
            boolean bottom=side==Direction.DOWN && be.worldPosition.getY()==root.worldPosition.getY();
            return GasTankDrain.port(side==Direction.UP ? be.upperPort : be.port,bottom,()-> {
                var owner=be.getControllerBE();
                return owner!=null && owner.usable() ? owner.sump : null;
            });
        });
    }
    private void configureGeometry() {
        if (geometry!=null && geometry.width()==width && geometry.height()==height) return;
        var next=new GasHolderGeometry(width,height);
        discardSleeves();geometry=next;collision=java.util.stream.IntStream.range(0,3).mapToObj(next::shell).toList();lift=Math.min(lift,travel());
    }
    private boolean blockedAt(double value) {
        int top=worldPosition.getY()+(int)Math.ceil(height+value+5/16d);
        if (top>=level.getMaxBuildHeight()) return true;
        var cursor=new BlockPos.MutableBlockPos();
        for (int y=worldPosition.getY()+height;y<=top;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            cursor.set(worldPosition.getX()+x,y,worldPosition.getZ()+z);var state=level.getBlockState(cursor);if (state.isAir()) continue;
            var shape=state.getCollisionShape(level,cursor);if (shape.isEmpty()) continue;
            for (var obstacle:shape.toAabbs()) for (int stage=0;stage<3;stage++) {
                double dy=geometry.offset(value,stage)+.125;
                for (var shell:collision.get(stage)) if (shell.move(worldPosition.getX(),worldPosition.getY()+dy,worldPosition.getZ()).intersects(obstacle.move(cursor))) return true;
            }
        }
        return false;
    }
    @Override public void tick() {
        super.tick();if (level==null || !isController()) return;previousLift=lift;
        if (height<2 || failed) return;
        if (level.isClientSide) {
            if (!paused && !blocked) {double delta=Math.clamp(target()-lift,-SPEED,SPEED)+correction*.25;correction*=.75;lift=Math.clamp(lift+delta,0,travel());}
            placeSleeves();return;
        }
        if (!loaded()) {if (!paused) {paused=true;sendDataImmediately();}for (var sleeve:sleeves) if (sleeve!=null) sleeve.setContraptionMotion(Vec3.ZERO);return;}
        if (paused) {paused=false;pending=true;}
        configureGeometry();
        if (!attached()) {
            boolean waiting=false;
            for (int i=0;i<3;i++) {
                if (sleeves[i]!=null && sleeves[i].isAlive()) continue;
                if (sleeveIds[i]!=null && level instanceof ServerLevel server && server.getEntity(sleeveIds[i]) instanceof ControlledContraptionEntity saved) attach(saved);
                if (sleeves[i]!=null && sleeves[i].isAlive()) continue;
                if (sleeveIds[i]!=null) {waiting=true;continue;}
                var moving=new GasHolderSleeveContraption(worldPosition,width,height,i);
                sleeves[i]=GasHolderSleeveEntity.create(level,this,moving);sleeveIds[i]=sleeves[i].getUUID();placeSleeve(i,0);
                if (!level.addFreshEntity(sleeves[i])) {fail("missing");return;}
            }
            if (waiting) {if (++missingTicks>100) fail("missing");return;}
            missingTicks=0;dirty=true;level.invalidateCapabilities(worldPosition);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
        }
        pending=false;
        if (Math.floorMod(level.getGameTime()+worldPosition.asLong(),20)==0) sump.collect(gas());
        double next=lift+Math.clamp(target()-lift,-SPEED,SPEED);
        if (next!=lift || blocked) {
            boolean obstruction=blockedAt(next==lift ? Math.min(travel(),lift+SPEED) : next);
            if (obstruction!=blocked) {blocked=obstruction;dirty=true;}
            if (!blocked) {lift=next;dirty=true;}
        }
        placeSleeves();
        if (dirty && Math.floorMod(level.getGameTime()+worldPosition.asLong(),8)==0) {dirty=false;setChanged();sendData();}
    }
    private void placeSleeves() { for (int i=0;i<3;i++) placeSleeve(i,geometry==null ? 0 : geometry.offset(lift,i)-geometry.offset(previousLift,i)); }
    private void placeSleeve(int stage,double motion) {
        var entity=sleeves[stage];if (entity==null || geometry==null) return;
        double x=worldPosition.getX(),y=worldPosition.getY()+.125+geometry.offset(lift,stage),z=worldPosition.getZ();
        entity.setContraptionMotion(motion==0 ? Vec3.ZERO : new Vec3(0,motion,0));
        if (entity.getX()!=x || entity.getY()!=y || entity.getZ()!=z) entity.setPos(x,y,z);
    }
    @Override public boolean isAttachedTo(AbstractContraptionEntity entity) { for (var sleeve:sleeves) if (sleeve==entity) return true;return false; }
    @Override public void attach(ControlledContraptionEntity entity) {
        if (!isController() || !(entity.getContraption() instanceof GasHolderSleeveContraption sleeve) || !sleeve.matches(width,height) || !entity.isAlive()) return;
        int i=sleeve.stage();if (sleeveIds[i]!=null && !sleeveIds[i].equals(entity.getUUID())) return;
        sleeves[i]=entity;sleeveIds[i]=entity.getUUID();
    }
    @Override public void onStall() { blocked=true;dirty=true; }
    @Override public boolean isValid() { return live(); }
    @Override public BlockPos getBlockPosition() { return worldPosition; }
    public void preview(int width,int height,FluidStack fluid,double lift) {
        if (!isVirtual()) return;
        this.width=width;this.height=height;geometry=new GasHolderGeometry(width,height);gas().setCapacity(width*width*height*getCapacityMultiplier());gas().setFluid(fluid.copy());
        this.lift=lift;previousLift=lift;failed=false;reason="";invalidateRenderBoundingBox();
    }
    @Override protected AABB createRenderBoundingBox() { return isController() ? new AABB(worldPosition).expandTowards(width-1,height+travel()+.5,width-1) : new AABB(worldPosition); }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) {
        var owner=getControllerBE();if (owner==null) return false;
        text.add(Component.translatable(LANG+"state",Component.translatable(LANG+owner.status())));
        text.add(Component.translatable(LANG+"contents",owner.gas().isEmpty() ? Component.translatable(LANG+"empty") : owner.gas().getFluid().getHoverName().copy().append(" · ").append(net.createplant.thermal.FluidThermal.readout(owner.gas().getFluid())),owner.gas().getFluidAmount(),owner.capacity()));
        net.createplant.thermal.TankGrade.HOLDER.describe(text,owner.gas().getFluid(),sneaking);addDrainReadout(text,false);return true;
    }
    public void addDrainReadout(List<Component> tooltip, boolean composition) {
        var owner=getControllerBE();
        if (owner!=null) owner.sump.addReadout(tooltip,composition);
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);sump.read(tag,lookup);double value=tag.getDouble("Lift");value=Double.isFinite(value) ? Math.clamp(value,0,travel()) : 0;
        if (packet && geometry!=null) correction=value-lift;else {lift=value;previousLift=value;}
        for (int i=0;i<3;i++) sleeveIds[i]=tag.hasUUID("Sleeve"+i) ? tag.getUUID("Sleeve"+i) : null;
        paused=tag.getBoolean("Paused");blocked=tag.getBoolean("Blocked");failed=tag.getBoolean("Failed");reason=tag.getString("Reason");pending=!packet;
        if (packet && (blocked || paused)) {lift=value;previousLift=value;correction=0;}
        geometry=new GasHolderGeometry(width,height);
        if (!packet) collision=java.util.stream.IntStream.range(0,3).mapToObj(geometry::shell).toList();
    }
    @Override public void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);sump.write(tag,lookup);tag.putDouble("Lift",lift);tag.putBoolean("Paused",paused);tag.putBoolean("Blocked",blocked);tag.putBoolean("Failed",failed);tag.putString("Reason",reason);
        for (int i=0;i<3;i++) if (sleeveIds[i]!=null) tag.putUUID("Sleeve"+i,sleeveIds[i]);
    }
}
