package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.TurbineFacility;
import net.createplant.block.TurbineFacility.FacilityLink;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SteamTurbineBlockEntity extends GeneratingKineticBlockEntity {
    private static final float OUTPUT_SPEED_DIVISOR = 10f;
    private static final float MAX_OUTPUT_SPEED = BoilerSteamOutput.MAX_TURBINE_RPM / OUTPUT_SPEED_DIVISOR;

    private final IFluidHandler steamCapability = LiveMachinePorts.fluid(this, new SteamHandler());
    private final IFluidHandler liquidPort = new LiquidHandler();
    private final IFluidHandler unitSteamPort = new TurbineUnitPort(this,false);
    private final IFluidHandler unitLiquidPort = new TurbineUnitPort(this,true);
    IFluidHandler localSteamPort(){return steamCapability;}
    IFluidHandler localLiquidPort(){return liquidPort;}
    public static final int EXHAUST_CAPACITY=net.createplant.thermal.TurbineThermal.CAPACITY;
    private net.createplant.thermal.TurbineThermal thermal;
    public net.createplant.thermal.TurbineThermal thermodynamics(){if(thermal==null)thermal=new net.createplant.thermal.TurbineThermal(this);return thermal;}
    private net.createplant.block.TurbineUnit.Layout unit;
    public net.createplant.block.TurbineUnit.Layout unitLayout(){return unit;}
    public BlockPos unitOrigin(){return unit==null ? null : unit.origin();}
    public void setUnitLayout(net.createplant.block.TurbineUnit.Layout layout){unit=layout;sectionTick=Long.MIN_VALUE;setChanged();sendData();}
    private List<net.createplant.block.TurbineUnit.Layout> sectionAssembly=List.of();
    private long sectionTick=Long.MIN_VALUE, sectionStamp=Long.MIN_VALUE;
    public List<net.createplant.block.TurbineUnit.Layout> unitAssembly() {
        if (level==null || unit==null) return List.of();
        long tick=level.getGameTime(), stamp=net.createplant.thermal.FluidRoutes.stamp(level);
        if (tick!=sectionTick || stamp!=sectionStamp) {
            sectionAssembly=net.createplant.block.TurbineUnit.assembly(level,unit);
            sectionTick=tick;sectionStamp=stamp;
        }
        return sectionAssembly;
    }
    private FacilityLink cachedFacility;
    private long facilityStamp=-1,facilityTick=-100;
    private long lastSync;
    private boolean thermalDirty;
    public void thermalChanged(){thermalDirty=true;setChanged();}
    private boolean tarbinEndPositive;
    private boolean tarbinEndNegative;
    private int lastSteamInput;
    private float steamGeneratedSpeed;
    private float steamVisualSpeed;
    private float lastSyncedGeneratedSpeed;
    private float lastSyncedStressCapacity = -1;
    private KineticNetwork lastCapacityNetwork;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        this(CEBlockEntityTypes.STEAM_TURBINE.get(), pos, state);
    }

    public SteamTurbineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    public List<BlockPos> safetyParts(){
        if(unit!=null)return unitAssembly().stream().flatMap(section->section.volume().stream()).toList();
        var assembly=HighPressureTurbineMultiblock.assemblyInfo(level,worldPosition,getBlockState());if(!assembly.formed())return List.of();
        var result=new java.util.ArrayList<BlockPos>();var along=Direction.fromAxisAndDirection(assembly.axis(),Direction.AxisDirection.POSITIVE);
        Direction u=assembly.axis()==Direction.Axis.Y ? Direction.EAST : Direction.UP;Direction v=assembly.axis()==Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
        int radius=assembly.lowPressure() ? 2 : 1;
        for(int i=-1;i<=assembly.shaftCount();i++)for(int j=-radius;j<=radius;j++)for(int k=-radius;k<=radius;k++){
            var pos=assembly.start().relative(along,i).relative(u,j).relative(v,k);if(!level.hasChunkAt(pos))continue;
            var block=level.getBlockState(pos).getBlock();if(block instanceof SteamTurbineBlock || com.simibubi.create.AllBlocks.SHAFT.has(level.getBlockState(pos)) || block instanceof net.createplant.block.HighPressureTurbinePartBlock || block instanceof net.createplant.block.HighPressureTurbineEndBlock)result.add(pos);
        }return result;
    }
    @Override public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> list){super.addBehaviours(list);}
    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        if (level.isClientSide()) {
            spawnSteamParticles();
        }

        if (!level.isClientSide()) {
            if(isRemoved())return;
            int previousBuffer = localSteamBuffer();
            String previousStatus=thermodynamics().status();
            float previousGeneratedSpeed = steamGeneratedSpeed;
            float previousVisualSpeed = steamVisualSpeed;
            float previousSyncedGeneratedSpeed = lastSyncedGeneratedSpeed;
            lastSteamInput = consumeSteamForRotation();
            steamGeneratedSpeed=getGeneratedSpeed();steamVisualSpeed=steamGeneratedSpeed*10;
            lastSyncedGeneratedSpeed = getGeneratedSpeed();
            boolean speedChanged = previousGeneratedSpeed != steamGeneratedSpeed
                || previousSyncedGeneratedSpeed != lastSyncedGeneratedSpeed;
            if (speedChanged) {
                updateGeneratedRotation();
            }
            refreshStressCapacity(speedChanged);
            if (previousBuffer != localSteamBuffer() || previousGeneratedSpeed != steamGeneratedSpeed
                || previousVisualSpeed != steamVisualSpeed || previousSyncedGeneratedSpeed != lastSyncedGeneratedSpeed
                || lastSteamInput > 0 || thermalDirty || !previousStatus.equals(thermodynamics().status())) {
                setChanged();
                thermalDirty=true;
                if(level.getGameTime()-lastSync>=20){sendData();lastSync=level.getGameTime();thermalDirty=false;}
            }
        }

    }

    @Override
    public float getGeneratedSpeed() {
        // The governor holds speed while the set is whole: dropping to zero between steam pulses makes Create re-propagate the shaft and break its ends.
        return (unit!=null ? !unitAssembly().isEmpty() : facilityLink().operational()) ? MAX_OUTPUT_SPEED : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (getGeneratedSpeed() == 0) {
            lastCapacityProvided = 0;
            return 0;
        }
        if (unit!=null) return (float)(thermodynamics().capacityWork()*20/Math.max(1,Math.abs(getGeneratedSpeed())));
        FacilityLink facility = facilityLink();
        if (!facility.operational() || !isFacilityCapacityController(facility)) {
            lastCapacityProvided = 0;
            return 0;
        }
        float capacity = TurbineFacility.totalSU(level, facility)
            / Math.max(1f, Math.abs(getGeneratedSpeed()));
        lastCapacityProvided = capacity;
        return capacity;
    }

    private boolean isFacilityCapacityController(FacilityLink facility) {
        return facility.highPressure() != null
            && worldPosition.equals(facility.highPressure().start());
    }

    private void refreshStressCapacity(boolean speedChanged) {
        float capacity = calculateAddedStressCapacity();
        KineticNetwork network = hasNetwork() ? getOrCreateNetwork() : null;
        boolean networkChanged = network != lastCapacityNetwork;
        boolean registrationMissing = network != null && capacity > 0
            && (!network.sources.containsKey(this) || Math.abs(network.sources.get(this) - capacity) >= .001f);
        lastCapacityNetwork = network;
        if (speedChanged || !networkChanged && !registrationMissing && Math.abs(capacity - lastSyncedStressCapacity) < .001f) {
            lastSyncedStressCapacity = capacity;
            return;
        }
        lastSyncedStressCapacity = capacity;
        if (network != null) {
            // A clutch split rebuilds its two networks without necessarily changing this rotor's thermodynamic
            // capacity. Re-register it even when the numeric value is unchanged; otherwise the new network keeps
            // the generator load but has zero source capacity and can never restart itself.
            notifyStressCapacityChange(capacity);
            network.updateStressFor(this, calculateStressApplied());
            network.updateStress();
            if (registrationMissing && capacity > 0 && Math.abs(getSpeed()) < 1) updateGeneratedRotation();
        } else if (capacity > 0 && getGeneratedSpeed() != 0) {
            updateGeneratedRotation();
        }
    }

    public float visualSpeed() {
        return getGeneratedSpeed()*10;
    }

    public float localSteamOutputSpeed() {
        return steamGeneratedSpeed;
    }

    public int localSteamBuffer() {
        return thermodynamics().input().getFluidAmount();
    }

    public int localSteamInput() {
        return lastSteamInput;
    }

    /** A shaft already forced beyond its safe speed must shed generation before capacity is advertised. */
    public boolean canPublishStressCapacity() {
        return Float.isFinite(getSpeed()) && Math.abs(getSpeed()) <= MAX_OUTPUT_SPEED * 1.1f;
    }

    public boolean installTarbinEnd(Direction direction) {
        Direction.AxisDirection axisDirection = direction.getAxisDirection();
        if (axisDirection == Direction.AxisDirection.POSITIVE) {
            if (tarbinEndPositive) {
                return false;
            }
            tarbinEndPositive = true;
        } else {
            if (tarbinEndNegative) {
                return false;
            }
            tarbinEndNegative = true;
        }
        setChanged();
        sendData();
        return true;
    }

    public void setTarbinEnds(boolean positive, boolean negative) {
        tarbinEndPositive = positive;
        tarbinEndNegative = negative;
        setChanged();
        sendData();
    }

    public boolean hasTarbinEnd(Direction.AxisDirection direction) {
        return direction == Direction.AxisDirection.POSITIVE ? tarbinEndPositive : tarbinEndNegative;
    }

    public boolean hasAnyTarbinEnd() {
        return tarbinEndPositive || tarbinEndNegative;
    }

    public int tarbinEndCount() {
        return (tarbinEndPositive ? 1 : 0) + (tarbinEndNegative ? 1 : 0);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if(unit!=null && unitAssembly().isEmpty()) {
            tooltip.add(Component.translatable("turbine.create_refinery_n_reactors.sections",net.createplant.block.TurbineUnit.connectedSections(level,unit).size(),unit.stage().sections()));
            return true;
        }
        tooltip.add(Component.translatable("turbine.create_refinery_n_reactors.state",Component.translatable("turbine.create_refinery_n_reactors."+thermodynamics().status())));
        tooltip.add(Component.translatable("turbine.create_refinery_n_reactors.power",String.format(Locale.ROOT,"%.0f",thermodynamics().work()*20),formatSpeed(getGeneratedSpeed())));
        if(!thermodynamics().input().isEmpty())tooltip.add(net.createplant.thermal.FluidThermal.readout(thermodynamics().input().getFluid()));
        tooltip.add(Component.translatable("turbine.create_refinery_n_reactors.flow",lastSteamInput,thermodynamics().exhaust().getFluidAmount()).append(Component.literal(" | ")).append(Component.translatable("grade.create_refinery_n_reactors.turbine.wear",Math.round(thermodynamics().wear()*100/net.createplant.thermal.TurbineThermal.WEAR_LIMIT))));return true;
    }

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            CEBlockEntityTypes.STEAM_TURBINE.get(), SteamTurbineBlockEntity::fluidPort);
    }

    /** Axial faces are mechanical shaft connections; process fluids use radial casing ports. */
    public IFluidHandler fluidPort(Direction side) {
        if (side == null) {
            return getSteamCapability();
        }
        Direction.Axis axis = getBlockState().getValue(SteamTurbineBlock.AXIS);
        if (side.getAxis() == axis) {
            return null;
        }
        Direction drainSide = axis == Direction.Axis.Y ? Direction.NORTH : Direction.DOWN;
        return side == drainSide ? liquidPort() : getSteamCapability();
    }

    public int getRecoverableExhaust() { return thermodynamics().exhaust().getFluidAmount(); }
    public IFluidHandler getSteamCapability() {
        return unitPort(false);
    }
    private IFluidHandler unitPort(boolean liquid) {
        if(unit==null)return liquid ? liquidPort : steamCapability;
        var sections=unitAssembly();
        if(!sections.isEmpty() && level.getBlockEntity(sections.getFirst().origin()) instanceof SteamTurbineBlockEntity root)
            return liquid ? root.unitLiquidPort : root.unitSteamPort;
        return liquid ? unitLiquidPort : unitSteamPort;
    }

    public net.createplant.thermal.TurbineThermal.Vent ventContents(){var result=thermodynamics().release();if(result.mass()>0 && level instanceof net.minecraft.server.level.ServerLevel server)server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,12,.5,.5,.5,.02);return result;}
    public IFluidHandler liquidPort(){return unitPort(true);}

    private class LiquidHandler implements IFluidHandler {
        private net.createplant.thermal.ThermalFluidTank source(){return !isSteamOperational() || thermodynamics().wear()>=net.createplant.thermal.TurbineThermal.WEAR_LIMIT ? thermodynamics().input() : CEBlocks.isLowPressureSteamTurbine(getBlockState()) ? thermodynamics().exhaust() : null;}
        public int getTanks(){return 1;}
        public int getTankCapacity(int slot){var tank=source();return slot!=0 || tank==null ? 0 : tank.getCapacity();}
        public FluidStack getFluidInTank(int slot){var tank=source();return slot!=0 || isRemoved() || tank==null ? FluidStack.EMPTY : tank.getFluid().copy();}
        public boolean isFluidValid(int slot,FluidStack stack){return false;}
        public int fill(FluidStack stack,FluidAction action){return 0;}
        public FluidStack drain(int amount,FluidAction action){var tank=source();return isRemoved() || tank==null ? FluidStack.EMPTY : tank.drain(amount,action);}
        public FluidStack drain(FluidStack stack,FluidAction action){return net.createplant.thermal.FluidThermal.compatible(stack,getFluidInTank(0)) ? drain(stack.getAmount(),action) : FluidStack.EMPTY;}
    }
    public int steamCapacity() {return isSteamOperational() ? thermodynamics().input().getSpace() : 0;}
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(unit==null ? 2 : unit.radius()+.25);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        net.createplant.block.TurbineUnit.writeLayout(tag,unit);
        thermodynamics().write(tag,registries);
        tag.putBoolean("TarbinEndPositive", tarbinEndPositive);
        tag.putBoolean("TarbinEndNegative", tarbinEndNegative);
        tag.putInt("LastSteamInput", lastSteamInput);
        tag.putFloat("SteamGeneratedSpeed", steamGeneratedSpeed);
        tag.putFloat("SteamVisualSpeed", steamVisualSpeed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        unit=net.createplant.block.TurbineUnit.readLayout(tag);
        thermodynamics().read(tag,registries);if(clientPacket)thermodynamics().readClient(tag);
        tarbinEndPositive = tag.getBoolean("TarbinEndPositive");
        tarbinEndNegative = tag.getBoolean("TarbinEndNegative");
        lastSteamInput = tag.getInt("LastSteamInput");
        steamGeneratedSpeed = tag.getFloat("SteamGeneratedSpeed");
        steamVisualSpeed = tag.contains("SteamVisualSpeed") ? tag.getFloat("SteamVisualSpeed")
            : steamGeneratedSpeed * OUTPUT_SPEED_DIVISOR;
    }

    private boolean isSteamOperational() {
        return level != null && (unit!=null ? !unitAssembly().isEmpty() : facilityLink().operational());
    }





    private int consumeSteamForRotation() {
        if(Math.abs(getSpeed())>MAX_OUTPUT_SPEED*1.1){thermodynamics().stop("overspeed");return 0;}
        // Steam still applies torque to a stalled shaft. Refusing to expand it while Create reports overload fills every
        // HP/LP buffer, publishes zero capacity, and makes a generator set impossible to restart after a source transfer.
        // Running the thermodynamic step lets the set republish its available capacity and clear that transient itself.
        return thermodynamics().step(isSteamOperational(),net.createplant.thermal.TurbineThermal.MAX_FLOW);
    }

    private FacilityLink facilityLink() {
        if(level==null)return new FacilityLink(false,false,0,null,null);
        if(cachedFacility!=null && cachedFacility.operational())for(var assembly:List.of(cachedFacility.highPressure(),cachedFacility.lowPressure()))for(var end:List.of(assembly.start(),assembly.end()))for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})if(!level.hasChunkAt(end.offset(x,0,z)))return new FacilityLink(false,false,0,null,null);
        long stamp=net.createplant.thermal.FluidRoutes.stamp(level);
        if(cachedFacility==null || stamp!=facilityStamp || level.getGameTime()-facilityTick>=20){cachedFacility=TurbineFacility.find(level,worldPosition,getBlockState());facilityStamp=stamp;facilityTick=level.getGameTime();}
        return cachedFacility;
    }

    private void spawnSteamParticles(){
        if(level!=null && level.isClientSide && thermodynamics().wear()>=net.createplant.thermal.TurbineThermal.WEAR_LIMIT*.75 && level.getGameTime()%20==0)level.addParticle(ParticleTypes.SMOKE,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,0,.01,0);
    }
    private static String formatSpeed(float speed) {
        return String.format(Locale.ROOT, "%.1f", speed);
    }

    private class SteamHandler implements IFluidHandler {
        @Override public int getTanks(){return 2;}
        @Override public FluidStack getFluidInTank(int slot){return slot==0 ? FluidStack.EMPTY : slot==1 ? exhaustStream(thermodynamics().exhaust().getFluid()) : FluidStack.EMPTY;}
        @Override public int getTankCapacity(int slot){return net.createplant.thermal.TurbineThermal.CAPACITY;}
        @Override public boolean isFluidValid(int slot,FluidStack stack){return slot==0 && net.createplant.thermal.SteamGrades.of(stack)!=null;}
        @Override public int fill(FluidStack stack,FluidAction action){return isSteamOperational() && isFluidValid(0,stack) ? thermodynamics().input().fill(stack,action) : 0;}
        @Override public FluidStack drain(FluidStack request,FluidAction action){return request.isEmpty() || !net.createplant.thermal.FluidThermal.compatible(request,getFluidInTank(1)) ? FluidStack.EMPTY : drain(request.getAmount(),action);}
        @Override public FluidStack drain(int amount,FluidAction action){return exhaustStream(thermodynamics().exhaust().drain(amount,action));}
        private FluidStack exhaustStream(FluidStack stored){
        return stored.copy();
    }
    }
}
