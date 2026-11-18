package net.createplant.block.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.HighPressureTurbinePartBlock;

import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.TurbineFacility;
import net.createplant.mixin.FluidTankBlockEntityAccessor;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class BoilerSteamOutput extends BlockEntityBehaviour {
    public static final com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<BoilerSteamOutput> TYPE=new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<>();
    @Override public com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType() {return TYPE;}
    public static final int MAX_TURBINE_RPM = 2560;
    public static final int MAX_BOILERS_FOR_FULL_SPEED = 4;
    public static final int MB_PER_RPM = 10;
    public static final int MAX_STEAM_PER_TICK = MAX_TURBINE_RPM * MB_PER_RPM;
    public static final int MAX_STEAM_PER_TICK_PER_BOILER = MAX_STEAM_PER_TICK / MAX_BOILERS_FOR_FULL_SPEED;
    private static final int STEAM_PER_WATER_MB = 1;
    private static final int MAX_SEARCHED_PIPES = 256;
    private static final java.util.Map<Level,OutletCache> OUTLETS=new java.util.WeakHashMap<>();
    public static long outletScans;
    private static final class OutletCache {long stamp=-1;final java.util.Map<BlockPos,CachedOutlets> values=new java.util.HashMap<>();}
    private record CachedOutlets(long tick,int count) { }
    private double creditJoules;
    private final net.createplant.thermal.ThermalFluidTank pending=new net.createplant.thermal.ThermalFluidTank(64000,s->blockEntity.setChanged());
    private final java.util.List<BlockPos> targets=new java.util.ArrayList<>();
    /** The pipes the steam passes on its way to the turbines; they see its temperature and pressure like any other flow. */
    private final java.util.List<BlockPos> route=new java.util.ArrayList<>();
    /** False while no boiler touches this pipe: it then looks again only once a second. */
    private boolean nearBoiler=true;
    private long targetEpoch=-1,targetTick=-100;
    private static final java.util.Map<Level,Budget> BUDGETS=new java.util.WeakHashMap<>();
    public int pendingAmount(){return pending.getFluidAmount();}
    private static final class Budget {long tick=-1;final java.util.Map<BlockPos,Double> spent=new java.util.HashMap<>();}
    @Override public void write(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider lookup,boolean packet){tag.put("BoilerPendingSteam",pending.writeToNBT(lookup,new net.minecraft.nbt.CompoundTag()));tag.putDouble("BoilerHeatCredit",creditJoules);}
    @Override public void read(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider lookup,boolean packet){pending.readFromNBT(lookup,tag.getCompound("BoilerPendingSteam"));double saved=tag.getDouble("BoilerHeatCredit");creditJoules=Double.isFinite(saved) ? Math.clamp(saved,0,400000) : 0;}


    public BoilerSteamOutput(SmartBlockEntity be) { super(be); }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() == null || getWorld().isClientSide()) {
            return;
        }
        if (!nearBoiler && Math.floorMod(getWorld().getGameTime() + getPos().asLong(), 20) != 0) {
            return;
        }

        nearBoiler = false;
        for (Direction direction : Direction.values()) {
            FluidTankBlockEntity controller = adjacentBoilerController(getPos().relative(direction));
            if (controller == null) {
                continue;
            }
            // Only native boilers use this water-supply capability; process vessels keep their stored inventory.
            if (controller instanceof PlantFluidTankBlockEntity) {
                if (refreshSteamBoiler(controller)) controller.sendData();
                continue;
            }
            nearBoiler = true;

            int outlets = countSteamPipeOutlets(controller);
            if (outlets <= 0) {
                continue;
            }
            if (refreshSteamBoiler(controller)) {
                controller.sendData();
            }
            if (!controller.boiler.isActive()) {
                continue;
            }

            int steam = Math.min(steamGeneratedBy(controller) / outlets, steamCapacityInNetwork());
            if(pending.isEmpty() && steam>0) {
                int heat=boilerHeatLimitedBySizeAndWater(controller);
                var stored=controller.getTankInventory().getFluid();
                if(heat<=0 || stored.isEmpty() || !FluidHelper.isWater(stored.getFluid()))continue;
                var budget=BUDGETS.computeIfAbsent(getWorld(),key->new Budget());
                if(budget.tick!=getWorld().getGameTime()){budget.tick=getWorld().getGameTime();budget.spent.clear();}
                double maximum=Math.max(0,heat*4000-budget.spent.getOrDefault(controller.getBlockPos(),0d));
                double provided=0;
                for(int x=0;x<controller.getWidth();x++)for(int z=0;z<controller.getWidth();z++){
                    var pos=controller.getBlockPos().offset(x,-1,z);if(!getWorld().hasChunkAt(pos))continue;
                    float heater=com.simibubi.create.api.boiler.BoilerHeater.findHeat(getWorld(),pos,getWorld().getBlockState(pos));if(heater<=0)continue;
                    double request=Math.max(0,Math.min(maximum-provided,400000-creditJoules-provided));
                    provided+=Math.min(request,heater*4000);
                }
                creditJoules+=provided;budget.spent.merge(controller.getBlockPos(),provided,Double::sum);
                double cost=net.createplant.thermal.SteamGrades.ORDINARY.joulesPerMb();
                int n=Math.min(Math.min(steam,stored.getAmount()),(int)Math.floor(creditJoules/cost));
                if(n>0){var drained=controller.getTankInventory().drain(n,FluidAction.EXECUTE);n=drained.getAmount();
                    var result=new FluidStack(net.createplant.registry.CEFluids.STEAM.get(),n);net.createplant.thermal.FluidThermal.processComponents(drained,result);pending.fill(result,FluidAction.EXECUTE);creditJoules-=n*cost;
                }
                if(provided>0 || n>0)blockEntity.setChanged();
            }
            if(!pending.isEmpty()){int sent=pushSteamThroughNetwork(pending.getFluid());
                pending.drain(sent,FluidAction.EXECUTE);}
        }
    }

    /** Create's plain, encased and glass pipes, and the stainless pipes built on them, carry boiler steam to a turbine. */
    public static void attachToCreate(com.simibubi.create.api.event.BlockEntityBehaviourEvent event) {
        for (var type:java.util.List.of(com.simibubi.create.AllBlockEntityTypes.FLUID_PIPE.get(),com.simibubi.create.AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(),
            com.simibubi.create.AllBlockEntityTypes.GLASS_FLUID_PIPE.get()))
            event.forType(type,be->{ if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace().equals("create")) event.attach(new BoilerSteamOutput(be)); });
        for (var type:java.util.List.of(net.createplant.registry.CETransport.STAINLESS_PIPE_ENTITY.get(),net.createplant.registry.CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get(),
            net.createplant.registry.CETransport.STAINLESS_GLASS_PIPE_ENTITY.get())) event.forType(type,be->event.attach(new BoilerSteamOutput(be)));
    }
    /** Whether this pipe's network reaches a high-pressure turbine. */
    public boolean leadsToTurbine() { return getWorld()!=null && !targets().isEmpty(); }
    /**
     * Whether boiler steam passes from a block into the next one along the direction: any pipe that carries this behaviour, a smart fluid pipe,
     * or a Create valve that is open and lies along that direction. A closed valve stops the steam.
     */
    private static boolean carriesSteam(Level level,BlockPos pos,Direction direction) {
        BlockState state=level.getBlockState(pos);
        if (state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock)
            return state.getValue(com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock.ENABLED) && com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock.getPipeAxis(state)==direction.getAxis();
        if (state.is(com.simibubi.create.AllBlocks.SMART_FLUID_PIPE.get())) return true;
        return level.getBlockEntity(pos) instanceof SmartBlockEntity pipe && pipe.getBehaviour(TYPE)!=null;
    }

    /** A tank only boils on a gas pipe while it holds water; a tank of gas is just a gas tank. */
    private static boolean holdsWater(FluidTankBlockEntity tank) {
        var stored=tank.getTankInventory().getFluid();
        return !stored.isEmpty() && FluidHelper.isWater(stored.getFluid());
    }

    /** The hotter the burners under a tank, the hotter its steam: 100 C with a cold fire, 25 K more for every level of heat. */


    public static void refreshBoilersAround(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }

        for (BlockPos scan : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (!level.hasChunkAt(scan)) continue;
            BlockEntity blockEntity = level.getBlockEntity(scan);
            if (blockEntity instanceof FluidTankBlockEntity tank) {
                FluidTankBlockEntity controller = tank.getControllerBE();
                if (controller != null && refreshSteamBoiler(controller)) {
                    controller.sendData();
                }
            }
        }
    }

    public static int steamGeneratedBy(FluidTankBlockEntity tank) {
        if (tank == null) {
            return 0;
        }

        BoilerData boiler = tank.boiler;
        if (!boiler.isActive()) {
            return 0;
        }
        int storedWater = storedWater(tank);
        float suppliedWater = tank.boiler.waterSupply;
        if (storedWater <= 0 && suppliedWater <= 0) {
            return 0;
        }

        int heat = boilerHeatLimitedBySizeAndWater(tank);
        if (heat <= 0 && boiler.isPassive(tank.getTotalTankSize())) {
            heat = 1;
        }
        int steamByHeat = Math.round(MAX_STEAM_PER_TICK_PER_BOILER * (heat / 18f));
        int steamByWater = waterLimitForSteam(tank, storedWater);
        return Math.min(steamByHeat, steamByWater);
    }

    public static int countSteamPipeOutlets(FluidTankBlockEntity controller) {
        if (controller == null || controller.getLevel() == null) {
            return 0;
        }
        if (controller instanceof PlantFluidTankBlockEntity) return 0;

        Level level = controller.getLevel();
        if (!holdsWater(controller)) return 0;
        OutletCache cache = OUTLETS.computeIfAbsent(level, ignored -> new OutletCache());
        long stamp = net.createplant.thermal.FluidRoutes.stamp(level);
        if (cache.stamp != stamp) { cache.stamp = stamp; cache.values.clear(); }
        BlockPos key = controller.getBlockPos().immutable();
        CachedOutlets cached = cache.values.get(key);
        if (cached != null && level.getGameTime() - cached.tick() < 20) return cached.count();
        int[] outlets = {0};
        outletScans++;
        net.createplant.block.TankCells.forEach(controller, tankPos -> {
            for (Direction direction : Direction.values()) {
                if (isSteamOutlet(level, tankPos.relative(direction))) {
                    outlets[0]++;
                }
            }
        });
        cache.values.put(key, new CachedOutlets(level.getGameTime(), outlets[0]));
        return outlets[0];
    }

    /** Any pipe on the boiler that leads to a turbine takes its steam; a gas pipe also stores it. A water feed pipe stays a feed pipe. */
    private static boolean isSteamOutlet(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SmartBlockEntity pipe) || !(pipe.getBehaviour(TYPE) instanceof BoilerSteamOutput output)) return false;
        return pipe instanceof net.createplant.block.entity.GasPipeBlockEntity || output.leadsToTurbine();
    }

    private static boolean refreshSteamBoiler(FluidTankBlockEntity controller) {
        BoilerData boiler = controller.boiler;
        boolean previousActive = boiler.isActive();
        int previousEngines = boiler.attachedEngines;
        int previousHeat = boiler.activeHeat;
        boolean previousPassiveHeat = boiler.passiveHeat;
        int outlets = countSteamPipeOutlets(controller);
        controller.updateBoilerState();
        boolean activeAfterCreateEvaluation = boiler.isActive();
        boiler.attachedEngines += outlets;
        boolean activeAfterSteamPipes = boiler.isActive();
        if (activeAfterCreateEvaluation != activeAfterSteamPipes) {
            refreshTankCapabilities(controller);
        }
        controller.updateBoilerTemperature();
        return previousActive != activeAfterSteamPipes
            || previousEngines != boiler.attachedEngines
            || previousHeat != boiler.activeHeat
            || previousPassiveHeat != boiler.passiveHeat;
    }

    private static int boilerHeatLimitedBySizeAndWater(FluidTankBlockEntity tank) {
        BoilerData boiler = tank.boiler;
        int heat = Math.min(boiler.activeHeat, boiler.getMaxHeatLevelForBoilerSize(tank.getTotalTankSize()));
        if (boiler.waterSupply > 0) {
            heat = Math.min(heat, boiler.getMaxHeatLevelForWaterSupply());
        }
        return heat;
    }

    private static int waterLimitForSteam(FluidTankBlockEntity tank, int storedWater) {
        int steamBySupply = Math.round(tank.boiler.waterSupply * STEAM_PER_WATER_MB);
        if (steamBySupply > 0) {
            return steamBySupply;
        }
        return storedWater * STEAM_PER_WATER_MB;
    }

    private static void refreshTankCapabilities(FluidTankBlockEntity controller) {
        if (controller.getLevel() == null) {
            return;
        }

        Level level = controller.getLevel();
        net.createplant.block.TankCells.forEach(controller, pos -> {
            if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
                ((FluidTankBlockEntityAccessor) tank).createPlant$refreshCapability();
            }
        });
    }

    private static int storedWater(FluidTankBlockEntity tank) {
        FluidStack stored = tank.getFluid(0);
        if (stored.isEmpty() || !FluidHelper.isWater(stored.getFluid())) {
            return 0;
        }
        return stored.getAmount();
    }

    private FluidTankBlockEntity adjacentBoilerController(BlockPos tankPos) {
        return !getWorld().hasChunkAt(tankPos) ? null : BoilerSteamOutput.getCreateBoilerController(getWorld(), tankPos, getWorld().getBlockState(tankPos));
    }

    private java.util.List<BlockPos> targets() {
        long stamp=net.createplant.thermal.FluidRoutes.stamp(getWorld());
        if(targetEpoch==stamp && getWorld().getGameTime()-targetTick<20)return targets;
        targetEpoch=stamp;targetTick=getWorld().getGameTime();targets.clear();route.clear();
        Queue<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> visited=new HashSet<>();Set<BlockPos> rotors=new HashSet<>();
        queue.add(getPos());visited.add(getPos());
        while(!queue.isEmpty() && visited.size()<=MAX_SEARCHED_PIPES){
            BlockPos current=queue.poll();
            for(Direction direction:Direction.values()){
                if(!net.createplant.thermal.FluidPipeTopology.opens(getWorld(),current,direction))continue;
                BlockPos target=current.relative(direction);if(!getWorld().hasChunkAt(target))continue;
                BlockState state=getWorld().getBlockState(target);
                if(carriesSteam(getWorld(),target,direction)
                    && net.createplant.thermal.FluidPipeTopology.opens(getWorld(),target,direction.getOpposite())){
                    if(visited.add(target))queue.add(target);continue;
                }
                var rotor=turbineAtTarget(target,state);
                if(rotor!=null && CEBlocks.isHighPressureSteamTurbine(rotor.getBlockState()))rotors.add(rotor.getBlockPos().immutable());
            }
        }
        if(queue.isEmpty() && !rotors.isEmpty()){targets.addAll(rotors);route.addAll(visited);}return targets;
    }

    private int pushSteamThroughNetwork(FluidStack steam) {
        int remaining=steam.getAmount();
        for(var pos:targets())if(getWorld().hasChunkAt(pos) && getWorld().getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine){
            remaining-=turbine.getSteamCapability().fill(steam.copyWithAmount(remaining),FluidAction.EXECUTE);if(remaining<=0)break;
        }
        return steam.getAmount()-remaining;
    }

    private int steamCapacityInNetwork() {
        int capacity=0;
        for(var pos:targets())if(getWorld().hasChunkAt(pos) && getWorld().getBlockEntity(pos) instanceof SteamTurbineBlockEntity turbine){
            capacity+=turbine.steamCapacity();if(capacity>=MAX_STEAM_PER_TICK)return MAX_STEAM_PER_TICK;
        }
        return capacity;
    }

    private SteamTurbineBlockEntity turbineAtTarget(BlockPos target, BlockState state) {
        BlockPos turbinePos = target;
        BlockState turbineState = state;
        if (state.is(CEBlocks.HIGH_PRESSURE_TURBINE_PART.get())) {
            if (!state.getValue(HighPressureTurbinePartBlock.ATTACHED)) {
                return null;
            }
            turbinePos = HighPressureTurbinePartBlock.centerPos(target, state);
            if(!getWorld().hasChunkAt(turbinePos))return null;
            turbineState = getWorld().getBlockState(turbinePos);
        }
        if (!SteamTurbineBlock.isTurbine(turbineState)) {
            return null;
        }
        BlockEntity blockEntity = getWorld().getBlockEntity(turbinePos);
        return blockEntity instanceof SteamTurbineBlockEntity turbine ? turbine : null;
    }

    public static FluidTankBlockEntity getCreateBoilerController(net.minecraft.world.level.BlockGetter level,BlockPos pos,BlockState state) {
        if(!com.simibubi.create.content.fluids.tank.FluidTankBlock.isTank(state)) return null;
        return level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank ? tank.getControllerBE() : null;
    }

}
