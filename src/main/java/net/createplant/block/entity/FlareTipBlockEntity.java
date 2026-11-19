package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.block.FlareTipBlock;
import net.createplant.registry.CEFlare;
import net.createplant.safety.FlammableGas;
import net.createplant.safety.ProcessGasIncidents;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.SteamGrades;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** A terminal burner on a piped chimney, with a finite receiving volume and a manually lit pilot. */
public class FlareTipBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int VOLUME=4000,MIN_CHIMNEY=3,INTERVAL=5,MAX_FLOW=16_000,MAX_FLAME=16;
    private static final String KEY="flare.create_refinery_n_reactors.";
    private final ThermalFluidTank gas=new ThermalFluidTank(VOLUME,fluid->{setChanged();dirty=true;});
    private final LerpedFloat height=LerpedFloat.linear().startWithValue(0);
    private boolean formed,exhaust,dirty;
    private float flame;
    private double flow;
    private long burnt,vented;

    public FlareTipBlockEntity(BlockPos pos,BlockState state) { super(CEFlare.TIP_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }
    public ThermalFluidTank gas() { return gas; }

    public double flow() { return flow; }
    public long burnt() { return burnt; }
    public long vented() { return vented; }
    public boolean pilot() { return getBlockState().getValue(FlareTipBlock.PILOT); }
    public boolean burning() { return flame>0; }
    /** Ponder has no reacting inventory; its virtual world illustrates the running line. */
    public void preview(float value,double perTick) {
        if (!isVirtual()) return;
        flame=Math.clamp(value,0,MAX_FLAME);flow=Math.clamp(perTick,0,MAX_FLOW);formed=true;exhaust=true;height.startWithValue(flame);
    }
    public float flameHeight(float partialTicks) { return level!=null && level.isClientSide ? height.getValue(partialTicks) : flame; }
    public String status() { return !formed ? "chimney" : !exhaust ? "blocked" : !pilot() ? "unlit" : burning() ? "burning" : flow>0 ? "venting" : "ready"; }

    private boolean chimney() {
        if (level==null) return false;
        for (int y=1;y<=MIN_CHIMNEY;y++) {
            var at=worldPosition.below(y);if (!level.hasChunkAt(at)) return false;
            var pipe=FluidPropagator.getPipe(level,at);
            if (pipe==null || !pipe.canHaveFlowToward(level.getBlockState(at),Direction.UP) || !pipe.canHaveFlowToward(level.getBlockState(at),Direction.DOWN)) return false;
        }
        return true;
    }
    private boolean clearExhaust() {
        return level!=null && !getBlockState().getValue(FlareTipBlock.WATERLOGGED) && level.hasChunkAt(worldPosition.above())
            && level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,worldPosition.getX(),worldPosition.getZ())<=worldPosition.getY()+1
            && level.canSeeSky(worldPosition.above());
    }
    private boolean live() { return level!=null && !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition)==this; }

    private final IFluidHandler port=new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return live() && tank==0 ? gas.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank==0 ? gas.getCapacity() : 0; }
        @Override public boolean isFluidValid(int tank,FluidStack stack) { return tank==0 && !stack.isEmpty() && FluidMaterials.gas(stack.getFluid()); }
        @Override public int fill(FluidStack stack,FluidAction action) { return live() && isFluidValid(0,stack) && chimney() && clearExhaust() ? gas.fill(stack,action) : 0; }
        @Override public FluidStack drain(int amount,FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(FluidStack stack,FluidAction action) { return FluidStack.EMPTY; }
    };
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,CEFlare.TIP_ENTITY.get(),(be,side)->side==Direction.DOWN ? be.port : null);
    }
    public void releaseGas() {
        if (level instanceof ServerLevel server && !gas.isEmpty()) releaseExhaust(server, gas.drain(gas.getFluidAmount(), EXECUTE));
    }

    private void releaseExhaust(ServerLevel level, FluidStack emitted) {
        if (SteamGrades.of(emitted) != null) ProcessGasIncidents.record(level, worldPosition.above(), emitted.getAmount());
        else ToxicGas.release(level, worldPosition.above(), emitted);
    }

    @Override public void tick() {
        super.tick();
        if (level==null) return;
        if (level.isClientSide) { height.chase(flame,.25,LerpedFloat.Chaser.EXP);height.tickChaser();return; }
        if (!(level instanceof ServerLevel server) || Math.floorMod(level.getGameTime()+worldPosition.asLong(),INTERVAL)!=0) return;
        String before=status();float previous=flame;double previousFlow=flow;
        formed=chimney();exhaust=clearExhaust();
        if (pilot() && (getBlockState().getValue(FlareTipBlock.WATERLOGGED) || level.isRainingAt(worldPosition.above())))
            level.setBlock(worldPosition,getBlockState().setValue(FlareTipBlock.PILOT,false),Block.UPDATE_ALL);
        flow=0;flame=0;
        if (formed && exhaust && !gas.isEmpty()) {
            var emitted=gas.drain(Math.min(gas.getFluidAmount()/2+gas.getFluidAmount()%2,MAX_FLOW*INTERVAL),EXECUTE);
            flow=emitted.getAmount()/(double)INTERVAL;
            if (pilot() && (FlammableGas.fuel(emitted) || net.createplant.chemistry.WasteGas.contaminated(emitted))) {
                burnt+=Math.min(emitted.getAmount(),Long.MAX_VALUE-burnt);
                flame=FlammableGas.fuel(emitted) ? (float)Math.min(MAX_FLAME,1+15*Math.sqrt(flow/8000)) : 0;
                ToxicGas.releasePoison(level,worldPosition.above(),emitted);
            } else {
                vented+=Math.min(emitted.getAmount(),Long.MAX_VALUE-vented);
                releaseExhaust(server, emitted);
                server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+.5,worldPosition.getY()+1.3,worldPosition.getZ()+.5,3,.2,.1,.2,.08);
            }
        }
        if (flame>0) {
            var hot=new AABB(worldPosition).move(0,1,0).expandTowards(0,flame-1,0).inflate(.5,0,.5);
            for (var entity:level.getEntitiesOfClass(LivingEntity.class,hot)) { entity.igniteForSeconds(4);entity.hurt(level.damageSources().inFire(),2); }
            server.sendParticles(ParticleTypes.LARGE_SMOKE,worldPosition.getX()+.5,worldPosition.getY()+flame,worldPosition.getZ()+.5,2,.3,.2,.3,.04);
        }
        if (dirty || previous!=flame || previousFlow!=flow || !before.equals(status())) { dirty=false;setChanged();sendData(); }
    }
    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0,MAX_FLAME+1,0).inflate(2,0,2); }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.write(tag,lookup,packet);tag.put("FlareGas",gas.writeToNBT(lookup,new CompoundTag()));
        tag.putLong("Burnt",burnt);tag.putLong("Vented",vented);
        if (packet) { tag.putFloat("Flame",flame);tag.putDouble("Flow",flow);tag.putBoolean("Formed",formed);tag.putBoolean("Exhaust",exhaust); }
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider lookup,boolean packet) {
        super.read(tag,lookup,packet);gas.readFromNBT(lookup,tag.getCompound("FlareGas"));
        burnt=Math.max(0,tag.getLong("Burnt"));vented=Math.max(0,tag.getLong("Vented"));
        flame=packet ? net.createplant.nuclear.Finite.of(tag.getFloat("Flame"),0,0,MAX_FLAME) : 0;
        flow=packet ? net.createplant.nuclear.Finite.of(tag.getDouble("Flow"),0,0,MAX_FLOW) : 0;
        formed=packet && tag.getBoolean("Formed");exhaust=packet && tag.getBoolean("Exhaust");
    }
    @Override public boolean addToGoggleTooltip(List<Component> text,boolean sneaking) {
        text.add(Component.translatable(KEY+"state",Component.translatable(KEY+status())));
        if(!gas.isEmpty())text.add(net.createplant.thermal.FluidThermal.readout(gas.getFluid()));
        text.add(Component.translatable(KEY+"flow",String.format(Locale.ROOT,"%.0f",flow),String.format(Locale.ROOT,"%.1f",flame)));
        text.add(Component.translatable(KEY+"pilot_"+(pilot() ? "on" : "off")));
        return true;
    }
}
