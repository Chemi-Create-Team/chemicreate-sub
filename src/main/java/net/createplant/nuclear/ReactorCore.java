package net.createplant.nuclear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.block.entity.ShieldDriveBlockEntity;
import net.createplant.block.entity.ShieldPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The state and tick of one formed RBMK core, kept by its controller: the first fuel column's bottom channel. Fuel and
 * coolant stay in the channels; the controller keeps the kinetics, the rod links and the readings it synchronises.
 */
public final class ReactorCore {
    private final FuelChannelBlockEntity owner;
    private final ReactorPhysics.Kinetics kinetics = new ReactorPhysics.Kinetics();
    private ReactorCoreStructure.Result structure;
    private boolean dirty = true, fuelDirty = true, scram, consoleScram, jammed;
    /** Per-rod desk commands, keyed by the bottom channel and its fine 2 x 2 slot. Cap commands remain the fallback. */
    private final java.util.Map<RodKey, Integer> individualTargets = new HashMap<>();
    /** A nearby automatic-control cabinet may command rods that do not have an individual desk command. */
    private BlockPos automaticController;
    private double automaticTarget = -1;
    private int automaticSeen = Integer.MIN_VALUE;
    /** The Chernobyl hazard taken when AZ-5 was pressed, the power then, and the core's mean coolant temperature now. */
    private double hazard, scramPower, averageCoolantKelvin = 293.15;
    /** Graphite in each layer of each rod column when AZ-5 was pressed; what AZ-5 adds below it pushes out water. */
    private double[][] tipBase;
    /** With the rods out: the wandering reactivity, the radial tilt of the power, and each fuel column's place across the core. */
    private double wander, tiltX, tiltZ, instability;
    private double[] columnX = new double[0], columnZ = new double[0];
    private int clock;
    private double[] axialFlux = new double[0], axialImportance = new double[0], fuelRadial = new double[0], fuelWeights = new double[0], rodRadial = new double[0], load = new double[0];
    private double[][] cellShare = new double[0][], pendingBurn = new double[0][];
    private double radialSum, fuelRadialSum, fuelWeightSum, leakage;
    private double[] reactivityBonus = new double[0];
    private int fuelChannels, controlChannels;
    private BlockPos[] rodBottoms = new BlockPos[0];
    private byte[] rodSlots = new byte[0];
    private float driveSpeed;
    /** The rotation inputs set into the vault's shield, found when the shield is looked over. */
    private List<BlockPos> inputs = List.of();
    private List<BlockPos> shieldPorts = List.of();
    private FuelChannelBlockEntity[] channels = new FuelChannelBlockEntity[0];
    // Readings, also what clients receive.
    private String status = "structure";
    private BlockPos fault;
    private double power, reactivity, averageVoid, averageFuelKelvin, hottestKelvin, averageSurfaceKelvin, hottestSurfaceKelvin,
        graphiteKelvin = 293.15, rodInsertion;
    private float[] columnVoid = new float[0], columnKelvin = new float[0], columnSurfaceKelvin = new float[0], columnFlow = new float[0],
        rodColumnInsertion = new float[0], rodWear = new float[0], rodDepth = new float[0];
    private boolean[] rodInCore = new boolean[0];
    private int[] columnCoolant = new int[0];
    private long lastPowerTick = Long.MIN_VALUE;

    public ReactorCore(FuelChannelBlockEntity owner) {
        this.owner = owner;
        this.fault = owner.getBlockPos();
    }

    public boolean formed() { return structure != null; }
    /** Irradiation may only use a recent physical solution of a fully loaded, unchanged core. */
    public double irradiationPower() {
        Level level = owner.getLevel();
        if (level == null || structure == null || dirty || lastPowerTick < level.getGameTime() - 1) return 0;
        BoundingBox box = structure.box();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++)
            if (!level.hasChunkAt(new BlockPos(x << 4, box.minY(), z << 4))) return 0;
        return power;
    }
    public BoundingBox box() { return structure == null ? null : structure.box(); }
    public String status() { return status; }
    public BlockPos fault() { return fault; }
    public double power() { return power; }
    public double reactivity() { return reactivity; }
    public double averageVoid() { return averageVoid; }
    public double hottestKelvin() { return hottestKelvin; }
    public double averageFuelKelvin() { return averageFuelKelvin; }
    public double hottestSurfaceKelvin() { return hottestSurfaceKelvin; }
    public double averageSurfaceKelvin() { return averageSurfaceKelvin; }
    public double xenon() { return kinetics.xenon; }
    public double rodInsertion() { return rodInsertion; }
    public boolean scram() { return scram; }
    public boolean consoleScram() { return consoleScram; }
    public boolean jammed() { return jammed; }
    public double hazard() { return hazard; }
    public double averageCoolantKelvin() { return averageCoolantKelvin; }
    public double graphiteKelvin() { return graphiteKelvin; }
    public double instability() { return instability; }
    public int fuelChannels() { return fuelChannels; }
    public int controlChannels() { return controlChannels; }
    public int installedControlRods() { return rodBottoms.length; }
    /** The bottom cell of every fuel column, where its water comes in. */
    public List<BlockPos> fuelBottoms() { return structure == null ? List.of() : structure.fuel(); }
    /** The Chernobyl hazard AZ-5 would meet if pressed now. */
    public double hazardNow() { return ReactorPhysics.scramHazard(rodInsertion, kinetics.xenon, averageCoolantKelvin); }

    /** Developer playtests only: the iodine and xenon of a core that has run long at power and then been turned down. */
    public void poison(double iodine, double xenon) {
        kinetics.iodine = iodine;
        kinetics.xenon = xenon;
    }
    public int height() { return structure == null ? 0 : structure.height(); }
    public ReactorPhysics.Layout layout() { return ReactorPhysics.layout(height()); }
    public double nominalWatts() { return fuelChannels * ReactorPhysics.nominalChannelWatts(height()); }
    /** Current auxiliary equipment envelope; saved links outside it do not belong to this reactor. */
    public boolean vaultContains(BlockPos pos) {
        if (structure == null) return false;
        BoundingBox box = structure.box();int shell = ReactorCoreStructure.SHIELD_THICKNESS;
        return pos.getX() >= box.minX() - shell && pos.getX() <= box.maxX() + shell
            && pos.getZ() >= box.minZ() - shell && pos.getZ() <= box.maxZ() + shell
            && pos.getY() >= box.minY() - layout().lowerStructureHeight() - shell
            && pos.getY() <= box.maxY() + layout().capOffset() + shell;
    }

    public float columnVoid(BlockPos bottom) { int i = fuelIndex(bottom); return i < 0 || i >= columnVoid.length ? 0 : columnVoid[i]; }
    public float columnKelvin(BlockPos bottom) { int i = fuelIndex(bottom); return i < 0 || i >= columnKelvin.length ? 0 : columnKelvin[i]; }
    public float columnSurfaceKelvin(BlockPos bottom) { int i = fuelIndex(bottom); return i < 0 || i >= columnSurfaceKelvin.length ? 0 : columnSurfaceKelvin[i]; }
    public float columnFlow(BlockPos bottom) { int i = fuelIndex(bottom); return i < 0 || i >= columnFlow.length ? 0 : columnFlow[i]; }
    public int columnCoolant(BlockPos bottom) { int i = fuelIndex(bottom); return i < 0 || i >= columnCoolant.length ? 0 : columnCoolant[i]; }
    public float rodColumnInsertion(BlockPos bottom) {
        if (structure == null) return 0;
        int i = rodIndex(bottom, -1);
        return i < 0 || i >= rodColumnInsertion.length ? 0 : rodColumnInsertion[i];
    }
    public float controlDepth(BlockPos bottom, int slot) { int i = rodIndex(bottom, slot); return i < 0 ? 0 : rodDepth[i]; }
    public float controlWear(BlockPos bottom, int slot) { int i = rodIndex(bottom, slot); return i < 0 ? 0 : rodWear[i]; }
    public int controlRodCount() { return rodBottoms.length; }
    public BlockPos controlBottom(int index) { return index >= 0 && index < rodBottoms.length ? rodBottoms[index] : null; }
    public int controlSlot(int index) { return index >= 0 && index < rodSlots.length ? Byte.toUnsignedInt(rodSlots[index]) : -1; }
    public float controlInsertion(int index) { return index >= 0 && index < rodDepth.length ? rodDepth[index] / layout().coreHeight() : 0; }
    public boolean hasIndividualTarget(BlockPos bottom, int slot) { return individualTargets.containsKey(new RodKey(bottom, slot)); }
    public int individualTarget(BlockPos bottom, int slot) { return individualTargets.getOrDefault(new RodKey(bottom, slot), -1); }

    /** Gives one installed rod a desk target. AZ-5 still overrides every individual command. */
    public boolean setIndividualTarget(BlockPos bottom, int slot, int percent) {
        if (rodIndex(bottom, slot) < 0) return false;
        individualTargets.put(new RodKey(bottom.immutable(), slot), Math.clamp(percent, 0, 100));
        owner.coreSync();
        return true;
    }

    /** Returns one rod to its cap/link command. */
    public boolean clearIndividualTarget(BlockPos bottom, int slot) {
        boolean changed = individualTargets.remove(new RodKey(bottom, slot)) != null;
        if (changed) owner.coreSync();
        return changed;
    }

    /** Refreshes the room-adjacent automatic controller lease and its insertion target. */
    public boolean setAutomaticTarget(BlockPos controller, double percent) {
        if (controller == null || automaticController != null && !automaticController.equals(controller) && clock - automaticSeen <= 40) return false;
        double next = Double.isFinite(percent) ? Math.clamp(percent, 0, 100) : 100;
        boolean changed = !controller.equals(automaticController) || automaticTarget != next;
        automaticController = controller.immutable();
        automaticTarget = next;
        automaticSeen = clock;
        if (changed) owner.coreSync();
        return true;
    }

    public void clearAutomaticTarget(BlockPos controller) {
        if (controller == null || !controller.equals(automaticController)) return;
        automaticController = null;
        automaticTarget = -1;
        automaticSeen = Integer.MIN_VALUE;
        owner.coreSync();
    }

    public double automaticTarget() { return automaticController != null && clock - automaticSeen <= 40 ? automaticTarget : -1; }
    private int rodIndex(BlockPos bottom, int slot) {
        for (int i = 0; i < rodBottoms.length; i++) if (rodBottoms[i].equals(bottom) && (slot < 0 || rodSlots[i] == slot)) return i;
        return -1;
    }
    private int fuelIndex(BlockPos bottom) { return structure == null ? -1 : structure.fuel().indexOf(bottom); }

    public void changed() { dirty = true; }

    public net.neoforged.neoforge.fluids.capability.IFluidHandler outlet() { return outlet; }

    private final net.neoforged.neoforge.fluids.capability.IFluidHandler outlet=new net.neoforged.neoforge.fluids.capability.IFluidHandler(){
        public int getTanks(){return 4;}
        public FluidStack getFluidInTank(int tank){return tank>=0 && tank<4 ? takePhase(tank,Integer.MAX_VALUE,false) : FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return structure==null || tank<0 || tank>=4 ? 0 : (fuelChannels+controlChannels)*structure.height()*FuelChannelBlockEntity.COOLANT_PER_CHANNEL;}
        public boolean isFluidValid(int tank,FluidStack stack){return false;}
        public int fill(FluidStack stack,FluidAction action){return 0;}
        // A request names the substance and its state, so it is matched against each phase's entry rather than by fluid id alone.
        public FluidStack drain(FluidStack stack,FluidAction action){
            if(stack.isEmpty())return FluidStack.EMPTY;
            for(int slot=0;slot<4;slot++)if(FluidStack.isSameFluidSameComponents(FuelChannelBlockEntity.outletStack(slot,1),stack))return takePhase(slot,stack.getAmount(),action.execute());
            return FluidStack.EMPTY;
        }
        public FluidStack drain(int amount,FluidAction action){for(int slot:new int[]{1,0,2,3}){var result=takePhase(slot,amount,action.execute());if(!result.isEmpty())return result;}return FluidStack.EMPTY;}
    };
    private int outletCursor;
    private FluidStack takePhase(int slot,int requested,boolean execute){
        if(structure==null || owner.getLevel()==null || requested<=0)return FluidStack.EMPTY;
        int columns=structure.fuel().size();var channels=new FuelChannelBlockEntity[columns];int[] room=new int[columns];
        for(int i=0;i<columns;i++)if(owner.getLevel().getBlockEntity(structure.fuel().get(i)) instanceof FuelChannelBlockEntity channel){channels[i]=channel;room[i]=channel.phaseAvailable(slot);}
        int[] share=EvenShare.split(requested,room,outletCursor%Math.max(1,columns));int taken=0;
        for(int i=0;i<columns;i++)if(share[i]>0)taken+=channels[i].takePhase(slot,share[i],execute).getAmount();
        if(execute && columns>0)outletCursor=Math.floorMod(outletCursor+taken,columns);
        return taken==0 ? FluidStack.EMPTY : FuelChannelBlockEntity.outletStack(slot,taken);
    }
    public void fuelChanged() { fuelDirty = true; }

    public void signalChanged() {
        if (structure == null) return;
        boolean next = consoleScram;
        Level level = owner.getLevel();
        for (BlockPos bottom : columns()) for (int y = 0; y < structure.height() && !next; y++) next = level.hasNeighborSignal(bottom.above(y));
        setScram(next);
    }

    /** Latches or resets the physical AZ-5 desk button; channel redstone remains an independent emergency input. */
    public void setConsoleScram(boolean active) {
        if (consoleScram == active) return;
        consoleScram = active;
        signalChanged();
        owner.coreSync();
    }

    private void setScram(boolean next) {
        if (next == scram) return;
        scram = next;
        jammed &= scram;
        tipBase = null;
        hazard = scram ? ReactorPhysics.scramHazard(rodInsertion, kinetics.xenon, averageCoolantKelvin) : 0;
        scramPower = power;
        owner.coreSync();
    }

    private List<BlockPos> columns() {
        return structure.fuel();
    }

    public void tick() {
        Level level = owner.getLevel();
        clock++;
        if (dirty) rescan(level);
        if (structure == null) return;
        BoundingBox box = structure.box();
        if (!level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))
            || !level.isLoaded(new BlockPos(box.minX(), box.minY(), box.maxZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.minY(), box.minZ()))) {
            setStatus("unloaded");
            return;
        }
        if (fuelDirty) loadFuel(level);
        if (clock % 100 == 1) shield(level);
        moveRods(level);
        double rods = rodReactivity(level);
        unsteady(level);
        int height = structure.height();
        double fuelSum = 0, fuelBonusSum = 0, voidSum = 0, kelvinSum = 0, surfaceSum = 0, coolantSum = 0, hottest = 0;
        for (int c = 0; c < load.length; c++) {
            if (!(level.getBlockEntity(structure.fuel().get(c)) instanceof FuelChannelBlockEntity channel)) {
                dirty = true;
                return;
            }
            channels[c] = channel;
            double r = fuelRadial[c], weight = fuelWeights[c];
            fuelSum += r * load[c] / ReactorPhysics.RODS_PER_BLOCK;
            fuelBonusSum += r * reactivityBonus[c] / ReactorPhysics.RODS_PER_BLOCK;
            voidSum += weight * channel.voidFraction(height);
            kelvinSum += weight * channel.fuelKelvin();
            surfaceSum += weight * channel.fuelSurfaceKelvin();
            coolantSum += weight * channel.coolantKelvin();
            hottest = Math.max(hottest, channel.fuelKelvin());
        }
        averageCoolantKelvin = coolantSum / Math.max(1e-9, fuelWeightSum);
        averageVoid = voidSum / Math.max(1e-9, fuelWeightSum);
        averageFuelKelvin = kelvinSum / Math.max(1e-9, fuelWeightSum);
        averageSurfaceKelvin = surfaceSum / Math.max(1e-9, fuelWeightSum);
        // Rods, void and the wandering change how the fuel's neutrons multiply; with no fuel in the core they change nothing.
        double fuelled = Math.clamp(fuelSum / Math.max(1e-9, fuelWeightSum), 0, 1);
        double voidWorth = ReactorPhysics.VOID_WORTH * (1 + (ReactorPhysics.UNSTABLE_VOID - 1) * instability);
        reactivity = (ReactorPhysics.FUEL_WORTH * fuelSum + fuelBonusSum) / radialSum - leakage - ReactorPhysics.STRUCTURE_ABSORPTION + fuelled * (rods + wander + voidWorth * averageVoid)
            + ReactorPhysics.DOPPLER_PER_KELVIN * (averageFuelKelvin - 293.15) - ReactorPhysics.XENON_WORTH * kinetics.xenon;
        kinetics.step(reactivity);
        double meanRadial = fuelChannels == 0 ? 1 : fuelRadialSum / fuelChannels, total = 0;
        double[] localRodPower = localRodPower();
        // The references live for this tick only.
        hottest = hottestSurfaceKelvin = 0;
        for (int c = 0; c < load.length; c++) {
            double watts = kinetics.level * ReactorPhysics.nominalChannelWatts(height) * Math.max(0, load[c]) * fuelRadial[c] / meanRadial
                * Math.max(0, 1 + tiltX * columnX[c] + tiltZ * columnZ[c]) * localRodPower[c];
            total += watts;
            channels[c].heat(watts, height);
            for (int k = 0; k < height; k++) pendingBurn[c][k] += watts * cellShare[c][k] * ReactorPhysics.TICK_SECONDS;
            columnVoid[c] = (float) channels[c].voidFraction(height);
            columnKelvin[c] = (float) channels[c].fuelKelvin();
            columnSurfaceKelvin[c] = (float) channels[c].fuelSurfaceKelvin();
            columnFlow[c] = channels[c].flowRate();
            columnCoolant[c] = channels[c].coolantAmount();
            hottest = Math.max(hottest, channels[c].fuelKelvin());
            hottestSurfaceKelvin = Math.max(hottestSurfaceKelvin, channels[c].fuelSurfaceKelvin());
        }
        java.util.Arrays.fill(channels, null);
        power = total;
        lastPowerTick = level.getGameTime();
        graphiteKelvin = ReactorPhysics.graphiteStep(graphiteKelvin, averageCoolantKelvin,
            total / Math.max(1, fuelChannels) / ReactorPhysics.nominalChannelWatts(height));
        // The surge of a Chernobyl AZ-5 bends the channels and the rods stick where they are, tips in the core.
        if (scram && !jammed && hazard > ReactorPhysics.JAM_HAZARD && power > ReactorPhysics.JAM_SURGE * Math.max(scramPower, 0.01 * nominalWatts())) {
            jammed = true;
        }
        hottestKelvin = hottest;
        if (hottest > ReactorPhysics.CHANNEL_RUPTURE_KELVIN) {
            rupture(level);
            return;
        }
        if (clock % 20 == 0) {
            burn(level);
            wearRods(level);
            RadiationField.core(level, owner.getBlockPos(), structure.box(), power, scram);
        }
        setStatus(jammed ? "rods_jammed" : scram ? "scram" : hottest > ReactorPhysics.ALARM_KELVIN ? "overheat" : reactivity >= ReactorPhysics.BETA ? "prompt_critical"
            : ReactorPhysics.scramHazard(rodInsertion, kinetics.xenon, averageCoolantKelvin) > 0.5 ? "az5_danger" : instability > 0.5 ? "unstable"
            : kinetics.level < 1e-4 && reactivity < 0 ? "shutdown" : Math.abs(reactivity) < 5e-4 ? "critical" : reactivity > 0 ? "supercritical" : "subcritical");
        if (clock % 10 == 0) owner.coreSync();
    }

    private double sumLoad() {
        double sum = 0;
        for (double value : load) sum += Math.max(0, value);
        return sum;
    }

    /** Redistributes flux toward fuel bundles beside withdrawn rods; global rod worth remains in {@link #rodReactivity}. */
    private double[] localRodPower() {
        double[] result = new double[load.length];
        java.util.Arrays.fill(result, 1);
        if (structure == null || rodBottoms.length == 0) return result;
        double weighted = 0, weights = 0;
        for (int c = 0; c < result.length; c++) {
            BlockPos fuel = structure.fuel().get(c);
            double factor = 1;
            for (int j = 0; j < rodBottoms.length; j++) {
                double withdrawn = 1 - controlInsertion(j);
                if (withdrawn <= 0) continue;
                float[] hole = FuelChannelBlockEntity.HOLES[Byte.toUnsignedInt(rodSlots[j])];
                double dx = rodBottoms[j].getX() + .5 + hole[0] - (fuel.getX() + .5);
                double dz = rodBottoms[j].getZ() + .5 + hole[1] - (fuel.getZ() + .5);
                factor += ReactorPhysics.localRodHeating(dx * dx + dz * dz, withdrawn);
            }
            result[c] = factor;
            double weight = Math.max(0, load[c]) * Math.max(0, fuelRadial[c]);
            weighted += factor * weight;
            weights += weight;
        }
        double mean = weights > 0 ? weighted / weights : 1;
        if (mean > 0) for (int c = 0; c < result.length; c++) result[c] /= mean;
        return result;
    }

    /**
     * With the absorber out the flux loses its shape: the reactivity wanders and the power tilts across the core, each a random
     * walk that grows with the instability.
     */
    private void unsteady(Level level) {
        instability = ReactorPhysics.instability(rodInsertion);
        var random = level.getRandom();
        wander = ReactorPhysics.wander(wander, ReactorPhysics.WANDER_REACTIVITY * instability, random.nextGaussian());
        tiltX = ReactorPhysics.wander(tiltX, ReactorPhysics.WANDER_TILT * instability, random.nextGaussian());
        tiltZ = ReactorPhysics.wander(tiltZ, ReactorPhysics.WANDER_TILT * instability, random.nextGaussian());
    }

    /**
     * Finds rotation inputs and coolant ports in the vault. Radiation shielding is evaluated by the shared path tracer.
     */
    private void shield(Level level) {
        var found = new java.util.ArrayList<BlockPos>();
        var ports = new java.util.ArrayList<BlockPos>();
        BoundingBox box = structure.box();
        int shell = ReactorCoreStructure.SHIELD_THICKNESS;
        int low = box.minY() - layout().lowerStructureHeight() - shell;
        int high = box.maxY() + layout().capOffset() + shell;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX() - shell, low, box.minZ() - shell, box.maxX() + shell, high, box.maxZ() + shell)) {
            if (!level.hasChunkAt(pos)) continue;
            var be = level.getBlockEntity(pos);
            if (be instanceof ShieldDriveBlockEntity) found.add(pos.immutable());
            if (be instanceof ShieldPortBlockEntity port) { ports.add(pos.immutable());port.link(owner.getBlockPos()); }
        }
        for (BlockPos pos : inputs) if (!found.contains(pos) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShieldDriveBlockEntity input) input.load(0);
        for (BlockPos pos : shieldPorts) if (!ports.contains(pos) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShieldPortBlockEntity port) port.unlink(owner.getBlockPos());
        inputs = found;
        shieldPorts = ports;
        driveSpeed = 0;
        for (BlockPos pos : found) if (level.getBlockEntity(pos) instanceof ShieldDriveBlockEntity input) driveSpeed = Math.max(driveSpeed, Math.abs(input.getSpeed()));
        int share = found.isEmpty() ? 0 : (controlChannels + found.size() - 1) / found.size();
        for (BlockPos pos : found) ((ShieldDriveBlockEntity) level.getBlockEntity(pos)).load(share);
    }

    private void setStatus(String next) {
        if (status.equals(next)) return;
        status = next;
        owner.coreSync();
    }

    private void rescan(Level level) {
        dirty = false;
        var result = ReactorCoreStructure.scan(level, owner.getBlockPos());
        if (result.formed() && result.controller().equals(owner.getBlockPos())) form(level, result);
        else {
            var carried = kinetics.copy();
            release(level);
            fault = result.fault();
            status = result.reason();
            if (result.formed() && level.getBlockEntity(result.controller()) instanceof FuelChannelBlockEntity other) other.adopt(result, carried);
            owner.coreSync();
        }
    }

    private static boolean matchesEmptyLegacyLayout(Level level, ReactorCoreStructure.Result result, boolean previousMinimum) {
        return result.fuel().stream().allMatch(pos -> level.getBlockEntity(pos) instanceof FuelChannelBlockEntity channel
            && channel.matchesEmptyLayout(ReactorCoreStructure.channelMask(result, pos), ReactorCoreStructure.legacyControlMask(result, pos, previousMinimum)));
    }

    public void form(Level level, ReactorCoreStructure.Result result) {
        lastPowerTick = Long.MIN_VALUE;
        for (BlockPos pos : result.fuel()) if (level.getBlockEntity(pos) instanceof FuelChannelBlockEntity channel && !channel.acceptsAssemblyHeight(result.height())) {
            release(level);status = "fuel_height";fault = pos;owner.coreSync();return;
        }
        Set<BlockPos> previous = structure == null ? new HashSet<>() : new HashSet<>(cells(structure));
        structure = result;
        dirty = false;
        fuelDirty = true;
        BoundingBox box = result.box();
        int width = result.width(), depth = result.depth(), height = result.height();
        axialFlux = ReactorPhysics.axialFlux(height, false);
        axialImportance = ReactorPhysics.axialFlux(height, true);
        leakage = ReactorPhysics.leakage(width, depth, height, result.circular());
        fuelRadial = radial(result.fuel(), box, result.circular());
        int fuel = result.fuel().size();
        load = new double[fuel];
        reactivityBonus = new double[fuel];
        channels = new FuelChannelBlockEntity[fuel];
        cellShare = new double[fuel][height];
        pendingBurn = new double[fuel][height];
        columnVoid = new float[fuel];
        columnKelvin = new float[fuel];
        columnSurfaceKelvin = new float[fuel];
        columnFlow = new float[fuel];
        columnCoolant = new int[fuel];
        fuelWeights = new double[fuel];
        columnX = new double[fuel];
        columnZ = new double[fuel];
        fuelChannels = controlChannels = 0;
        fuelRadialSum = fuelWeightSum = 0;
        // Plain graphite is one whole moderator block; a channel bundle contributes only its fine positions inside the circle.
        radialSum = sum(radial(result.graphite(), box, result.circular()));
        var bottoms = new java.util.ArrayList<BlockPos>();
        var slots = new java.util.ArrayList<Byte>();
        var rodWeights = new java.util.ArrayList<Double>();
        var depths = new java.util.ArrayList<Float>();
        var wear = new java.util.ArrayList<Float>();
        boolean refreshEmptyLayout = result.circular() && width == ReactorCoreStructure.MIN_WIDTH
            && (matchesEmptyLegacyLayout(level, result, false) || matchesEmptyLegacyLayout(level, result, true));
        for (int c = 0; c < fuel; c++) {
            BlockPos pos = result.fuel().get(c);
            if (level.getBlockEntity(pos) instanceof FuelChannelBlockEntity channel) {
                channel.joinCore(owner.getBlockPos(), height);
                channel.initializeLayout(ReactorCoreStructure.channelMask(result, pos), ReactorCoreStructure.defaultControlMask(result, pos), refreshEmptyLayout);
            }
            columnX[c] = (pos.getX() + 0.5 - (box.minX() + box.maxX() + 1) / 2d) / (width / 2d);
            columnZ[c] = (pos.getZ() + 0.5 - (box.minZ() + box.maxZ() + 1) / 2d) / (depth / 2d);
            if (!(level.getBlockEntity(pos) instanceof FuelChannelBlockEntity channel)) continue;
            int active = channel.activeChannels(), fuels = channel.fuelChannels();
            fuelChannels += fuels;
            controlChannels += channel.controlChannels();
            radialSum += fuelRadial[c] * active / ReactorPhysics.RODS_PER_BLOCK;
            fuelWeights[c] = fuelRadial[c] * fuels / ReactorPhysics.RODS_PER_BLOCK;
            fuelWeightSum += fuelWeights[c];
            fuelRadialSum += fuelRadial[c] * fuels;
            for (int slot = 0; slot < ReactorPhysics.RODS_PER_BLOCK; slot++) if (channel.controlInstalled(slot)) {
                bottoms.add(pos);
                slots.add((byte) slot);
                rodWeights.add(fuelRadial[c] / ReactorPhysics.RODS_PER_BLOCK);
                depths.add(channel.controlDepth(slot));
                wear.add(channel.controlWear(slot));
            }
        }
        for (BlockPos cell : cells(result)) {
            previous.remove(cell);
            var be = level.getBlockEntity(cell);
            if (be instanceof FuelChannelBlockEntity channel) channel.joinCore(owner.getBlockPos(), height);
        }
        rodBottoms = bottoms.toArray(BlockPos[]::new);
        rodSlots = new byte[slots.size()];
        rodRadial = new double[slots.size()];
        rodDepth = new float[slots.size()];
        rodWear = new float[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            rodSlots[i] = slots.get(i);
            rodRadial[i] = rodWeights.get(i);
            rodDepth[i] = depths.get(i);
            rodWear[i] = wear.get(i);
        }
        rodColumnInsertion = new float[slots.size()];
        rodInCore = new boolean[slots.size()];
        individualTargets.keySet().removeIf(key -> rodIndex(key.bottom(), key.slot()) < 0);
        tipBase = null;
        leave(level, previous);
        ReactorCores.formed(level, owner.getBlockPos(), box);
        shield(level);
        signalChanged();
        status = "subcritical";
        fault = owner.getBlockPos();
        owner.coreSync();
    }

    public void release(Level level) {
        for (BlockPos pos : inputs) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShieldDriveBlockEntity input) input.load(0);
        for (BlockPos pos : shieldPorts) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShieldPortBlockEntity port) port.unlink(owner.getBlockPos());
        inputs = List.of();shieldPorts = List.of();driveSpeed = 0;
        if (structure != null) {
            ReactorCores.released(level, owner.getBlockPos());
            leave(level, cells(structure));
        }
        structure = null;
        kinetics.shutdown();
        power = 0;
        scram = false;
        consoleScram = false;
        automaticController = null;
        automaticTarget = -1;
        automaticSeen = Integer.MIN_VALUE;
    }

    private void leave(Level level, Iterable<BlockPos> cells) {
        for (BlockPos cell : cells) {
            if (!level.isLoaded(cell)) continue;
            var be = level.getBlockEntity(cell);
            if (be instanceof FuelChannelBlockEntity channel && owner.getBlockPos().equals(channel.controllerPos())) channel.joinCore(null, 0);
        }
    }

    /** Takes over the kinetics of a controller that no longer leads this core, so reshaping a running core is not a restart. */
    public void inherit(ReactorPhysics.Kinetics from) {
        kinetics.level = from.level;
        kinetics.precursors = from.precursors;
        kinetics.iodine = from.iodine;
        kinetics.xenon = from.xenon;
    }

    private static List<BlockPos> cells(ReactorCoreStructure.Result result) {
        var cells = new java.util.ArrayList<BlockPos>();
        for (BlockPos bottom : result.fuel()) for (int y = 0; y < result.height(); y++) cells.add(bottom.above(y));
        return cells;
    }

    private static double[] radial(List<BlockPos> columns, BoundingBox box, boolean circular) {
        double[] values = new double[columns.size()];
        for (int i = 0; i < values.length; i++) {
            BlockPos pos = columns.get(i);
            int x = pos.getX() - box.minX(), z = pos.getZ() - box.minZ();
            values[i] = circular ? ReactorPhysics.radialDisc(x, z, box.getXSpan()) : ReactorPhysics.radial(x, box.getXSpan()) * ReactorPhysics.radial(z, box.getZSpan());
        }
        return values;
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum;
    }

    private void loadFuel(Level level) {
        fuelDirty = false;
        int height = structure.height();
        for (int c = 0; c < load.length; c++) {
            double column = 0, shares = 0;
            reactivityBonus[c] = 0;
            for (int k = 0; k < height; k++) {
                double cell = 0;
                if (level.getBlockEntity(structure.fuel().get(c).above(k)) instanceof FuelChannelBlockEntity channel) {
                    cell = channel.cellLoad();
                    reactivityBonus[c] += axialFlux[k] * channel.cellReactivityBonus();
                }
                column += axialFlux[k] * cell;
                cellShare[c][k] = axialFlux[k] * Math.max(0, cell);
                shares += cellShare[c][k];
            }
            load[c] = column;
            for (int k = 0; k < height; k++) cellShare[c][k] = shares > 0 ? cellShare[c][k] / shares : 0;
        }
    }

    /** The rods of a column run its whole height, so each column's energy goes to its bottom cell, which keeps the rods. */
    private void burn(Level level) {
        for (int c = 0; c < load.length; c++) {
            double joules = 0;
            for (int k = 0; k < structure.height(); k++) {
                joules += pendingBurn[c][k];
                pendingBurn[c][k] = 0;
            }
            if (joules > 0 && level.getBlockEntity(structure.fuel().get(c)) instanceof FuelChannelBlockEntity channel) channel.burn(joules);
        }
    }

    /** Moves the cap-driven rods at the shield input's speed; AZ-5 drops them through the full travel in five seconds. */
    private void moveRods(Level level) {
        driveSpeed = 0;
        for (BlockPos pos : inputs) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShieldDriveBlockEntity input) driveSpeed = Math.max(driveSpeed, Math.abs(input.getSpeed()));
        float travel = layout().coreHeight();
        float driven = Math.min(.49f, driveSpeed / 512f) * (float) layout().scale(), drop = travel / 100f;
        BlockPos last = null;
        double setting = 0;
        for (int j = 0; j < rodBottoms.length; j++) {
            BlockPos bottom = rodBottoms[j];
            if (!bottom.equals(last)) {
                last = bottom;
                BlockPos cap = bottom.above(structure.height() - 1 + layout().capOffset());
                setting = level.getBlockEntity(cap) instanceof RodCapBlockEntity top ? top.target() : 0;
            }
            if (!(level.getBlockEntity(bottom) instanceof FuelChannelBlockEntity channel)) continue;
            int slot = Byte.toUnsignedInt(rodSlots[j]);
            float now = channel.controlDepth(slot);
            int individual = individualTargets.getOrDefault(new RodKey(bottom, slot), -1);
            double automatic = automaticTarget();
            double target = individual >= 0 ? individual / 100d : automatic >= 0 ? automatic / 100d : setting;
            float wanted = (float) (Math.round((scram ? 1 : target) * travel * 512) / 512d);
            float capacity = jammed ? 0 : scram ? Math.max(driven, drop) : driven;
            float moved = (float) Math.copySign(Math.min(Math.abs(wanted - now), capacity), wanted - now);
            if (capacity > 0 && moved != 0) channel.controlDepth(slot, now + moved);
            rodDepth[j] = channel.controlDepth(slot);
            rodWear[j] = channel.controlWear(slot);
        }
    }

    /**
     * Reactivity of every integrated rod at its present height. During AZ-5 under the
     * Chernobyl hazard, graphite entering a layer it was not in when AZ-5 was pressed pushes out water where the flux is.
     */
    private double rodReactivity(Level level) {
        int height = structure.height();
        double total = 0, insertion = 0;
        boolean surge = scram && hazard > 0;
        boolean snapshot = surge && tipBase == null;
        if (snapshot) tipBase = new double[rodBottoms.length][height];
        for (int j = 0; j < rodBottoms.length; j++) {
            rodInCore[j] = false;
            rodColumnInsertion[j] = 0;
            double effect = 0, absorbed = 0, touching = 0;
            double displacerBottom = (height - layout().displacerLength()) / 2 - rodDepth[j];
            double absorberBottom = height - rodDepth[j];
            for (int k = 0; k < height; k++) {
                double absorber = ReactorPhysics.overlap(absorberBottom, layout().absorberLength(), k);
                double displacer = ReactorPhysics.overlap(displacerBottom, layout().displacerLength(), k);
                double tip = 0;
                if (snapshot) tipBase[j][k] = displacer;
                else if (surge) tip = ReactorPhysics.TIP_SURGE * hazard * Math.max(0, displacer - tipBase[j][k]);
                effect += axialImportance[k] * (-ReactorPhysics.ABSORBER_WORTH * (1 - rodWear[j]) * absorber + ReactorPhysics.DISPLACER_WORTH * (displacer + tip));
                absorbed += absorber;
                touching += absorber + displacer;
            }
            rodInCore[j] = touching > 0;
            total += rodRadial[j] * effect;
            rodColumnInsertion[j] = (float) (absorbed / height);
            insertion += rodColumnInsertion[j];
        }
        rodInsertion = rodBottoms.length == 0 ? 0 : insertion / rodBottoms.length;
        return total / Math.max(1e-9, radialSum);
    }

    /** Absorber in the core burns up with the flux it takes; the wear lives in the rod channel's bottom cell. */
    private void wearRods(Level level) {
        for (int j = 0; j < rodWear.length; j++) {
            double added = ReactorPhysics.ABSORBER_BURNUP * Math.min(100, kinetics.level) * rodColumnInsertion[j] * 20 * ReactorPhysics.TICK_SECONDS;
            if (level.getBlockEntity(rodBottoms[j]) instanceof FuelChannelBlockEntity channel)
                rodWear[j] = added > 0 ? channel.wearControl(Byte.toUnsignedInt(rodSlots[j]), added) : channel.controlWear(Byte.toUnsignedInt(rodSlots[j]));
        }
    }

    // What the gantry crane needs to service the core.

    /** The operating-floor cap of every channel bundle, where fuel rods are loaded and spent ones taken out. */
    public List<BlockPos> fuelTops() {
        if (structure == null) return List.of();
        return structure.fuel().stream().map(bottom -> bottom.above(structure.height() - 1 + layout().capOffset())).toList();
    }

    public int rodColumns() { return structure == null ? 0 : rodBottoms.length; }
    public BlockPos rodTop(int j) { return rodBottoms[j].above(structure.height() - 1 + layout().capOffset()); }
    public int rodSlot(int j) { return Byte.toUnsignedInt(rodSlots[j]); }
    public float rodWear(int j) { return rodWear[j]; }

    /** Whether rod {@code j} is fully withdrawn, so the crane can lift it through the operating-floor cap. */
    public boolean rodWithdrawn(int j) { return j >= 0 && j < rodDepth.length && rodDepth[j] <= 1f / 512; }

    /** Replaces rod {@code j} with a fresh absorber; false unless it is fully withdrawn. */
    public boolean renewRod(Level level, int j) {
        if (!rodWithdrawn(j) || !(level.getBlockEntity(rodBottoms[j]) instanceof FuelChannelBlockEntity channel)
            || !channel.renewControl(Byte.toUnsignedInt(rodSlots[j]))) return false;
        rodWear[j] = 0;
        return true;
    }

    /**
     * The fuel has burst its channels: all the channel water flashes and blows the top off the core, and the core explodes a
     * moment later and leaves its graphite burning.
     */
    private void rupture(Level level) {
        BoundingBox box = structure.box();
        var centre = box.getCenter();
        double kilograms = 0;
        for (BlockPos bottom : structure.fuel())
            if (level.getBlockEntity(bottom) instanceof FuelChannelBlockEntity channel) kilograms += channel.flash() / 1000d;
        boolean chernobyl = ReactorPhysics.chernobylExplosion(scram, jammed, hazard);
        float steam = chernobyl ? ReactorPhysics.chernobylSteamExplosion(kilograms, hazard) : ReactorPhysics.steamExplosion(kilograms);
        float core = chernobyl ? ReactorPhysics.chernobylCoreExplosion(fuelChannels, power, hazard)
            : ReactorPhysics.coreExplosion(structure.fuel().size(), power);
        release(level);
        status = chernobyl ? "chernobyl_explosion" : "steam_explosion";
        owner.coreSync();
        if (chernobyl) {
            ReactorCores.breachUpperStructure(level, box);
            ReactorCores.scheduleChernobylExplosion(level, box, core);
        }
        else ReactorCores.scheduleCoreExplosion(level, box, core);
        level.explode(null, centre.getX() + 0.5, box.maxY() + 1.5, centre.getZ() + 0.5, steam, Level.ExplosionInteraction.BLOCK);
    }

    public void write(CompoundTag tag, boolean clientPacket) {
        tag.putString("Status", status);
        tag.putLong("Fault", fault.asLong());
        tag.putBoolean("Scram", scram);
        tag.putBoolean("ConsoleScram", consoleScram);
        tag.putBoolean("Jammed", jammed);
        tag.putDouble("Hazard", hazard);
        tag.putDouble("ScramPower", scramPower);
        tag.putDouble("Level", kinetics.level);
        tag.putDouble("Precursors", kinetics.precursors);
        tag.putDouble("Iodine", kinetics.iodine);
        tag.putDouble("Xenon", kinetics.xenon);
        if (!clientPacket) {
            var targets = new ListTag();
            for (var entry : individualTargets.entrySet()) {
                var target = new CompoundTag();
                target.putLong("Bottom", entry.getKey().bottom().asLong());
                target.putByte("Slot", (byte) entry.getKey().slot());
                target.putByte("Target", entry.getValue().byteValue());
                targets.add(target);
            }
            tag.put("IndividualTargets", targets);
        }
        if (!clientPacket) return;
        tag.putBoolean("Formed", structure != null);
        tag.putDouble("Power", power);
        tag.putDouble("Reactivity", reactivity);
        tag.putDouble("Void", averageVoid);
        tag.putDouble("FuelKelvin", averageFuelKelvin);
        tag.putDouble("Hottest", hottestKelvin);
        tag.putDouble("FuelSurfaceKelvin", averageSurfaceKelvin);
        tag.putDouble("HottestFuelSurface", hottestSurfaceKelvin);
        tag.putDouble("Rods", rodInsertion);
        tag.putDouble("GraphiteKelvin", graphiteKelvin);
        if (structure == null) return;
        tag.put("Fuel", positions(structure.fuel()));
        tag.put("RodBottoms", positions(java.util.Arrays.asList(rodBottoms)));
        tag.putByteArray("RodSlots", rodSlots);
        tag.put("RodDepth", floats(rodDepth));
        tag.put("RodWear", floats(rodWear));
        tag.putInt("FuelChannels", fuelChannels);
        tag.putInt("ControlChannels", controlChannels);
        tag.putInt("Height", structure.height());
        tag.put("ColumnVoid", floats(columnVoid));
        tag.put("ColumnKelvin", floats(columnKelvin));
        tag.put("ColumnSurfaceKelvin", floats(columnSurfaceKelvin));
        tag.put("ColumnFlow", floats(columnFlow));
        tag.putIntArray("ColumnCoolant", columnCoolant);
        tag.put("RodInsertion", floats(rodColumnInsertion));
    }

    public void read(CompoundTag tag, boolean clientPacket) {
        lastPowerTick = Long.MIN_VALUE;
        status = tag.getString("Status");
        fault = BlockPos.of(tag.getLong("Fault"));
        scram = tag.getBoolean("Scram");
        consoleScram = tag.getBoolean("ConsoleScram");
        jammed = tag.getBoolean("Jammed");
        // A broken save (NaN or infinity) must not reach the physics: such a value restarts from rest, and the core is scrammed.
        double savedHazard = tag.getDouble("Hazard"), savedScram = tag.getDouble("ScramPower"), savedLevel = tag.getDouble("Level");
        double savedPrecursors = tag.getDouble("Precursors"), savedIodine = tag.getDouble("Iodine"), savedXenon = tag.getDouble("Xenon");
        boolean broken = !(Double.isFinite(savedHazard) && Double.isFinite(savedScram) && Double.isFinite(savedLevel)
            && Double.isFinite(savedPrecursors) && Double.isFinite(savedIodine) && Double.isFinite(savedXenon));
        hazard = Finite.of(savedHazard, 0, 0, Double.MAX_VALUE);
        scramPower = Finite.of(savedScram, 0, 0, Double.MAX_VALUE);
        kinetics.level = Finite.of(savedLevel, ReactorPhysics.SOURCE_LEVEL, ReactorPhysics.SOURCE_LEVEL, Double.MAX_VALUE);
        kinetics.precursors = Finite.of(savedPrecursors, 0, 0, Double.MAX_VALUE);
        kinetics.iodine = Finite.of(savedIodine, 0, 0, Double.MAX_VALUE);
        kinetics.xenon = Finite.of(savedXenon, 0, 0, Double.MAX_VALUE);
        if (broken && !clientPacket) scram = true;
        if (!clientPacket) {
            individualTargets.clear();
            var targets = tag.getList("IndividualTargets", Tag.TAG_COMPOUND);
            int maximumRods = ReactorCoreStructure.MAX_WIDTH * ReactorCoreStructure.MAX_WIDTH * ReactorPhysics.RODS_PER_BLOCK;
            for (int i = 0; i < Math.min(maximumRods, targets.size()); i++) {
                var target = targets.getCompound(i);
                int slot = Byte.toUnsignedInt(target.getByte("Slot"));
                if (slot < ReactorPhysics.RODS_PER_BLOCK)
                    individualTargets.put(new RodKey(BlockPos.of(target.getLong("Bottom")), slot), Math.clamp(Byte.toUnsignedInt(target.getByte("Target")), 0, 100));
            }
            return;
        }
        power = Finite.of(tag.getDouble("Power"), 0, 0, Double.MAX_VALUE);
        reactivity = Finite.of(tag.getDouble("Reactivity"), 0, -1, 1);
        averageVoid = Finite.of(tag.getDouble("Void"), 0, 0, 1);
        averageFuelKelvin = Finite.of(tag.getDouble("FuelKelvin"), 293.15, 1, 10000);
        hottestKelvin = Finite.of(tag.getDouble("Hottest"), 293.15, 1, 10000);
        averageSurfaceKelvin = tag.contains("FuelSurfaceKelvin") ? Finite.of(tag.getDouble("FuelSurfaceKelvin"), averageFuelKelvin, 1, 10000) : averageFuelKelvin;
        hottestSurfaceKelvin = tag.contains("HottestFuelSurface") ? Finite.of(tag.getDouble("HottestFuelSurface"), hottestKelvin, 1, 10000) : hottestKelvin;
        rodInsertion = Finite.of(tag.getDouble("Rods"), 0, 0, 1);
        graphiteKelvin = tag.contains("GraphiteKelvin") ? Finite.of(tag.getDouble("GraphiteKelvin"), 293.15, 1, 10000) : 293.15;
        if (!tag.getBoolean("Formed")) {
            structure = null;
            return;
        }
        // Clients only need the column positions to look up their own readings; the box is rebuilt from them.
        var fuel = positions(tag.getList("Fuel", Tag.TAG_LONG));
        int height = tag.getInt("Height");
        var box = BoundingBox.encapsulatingPositions(fuel).map(b -> new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY() + height - 1, b.maxZ())).orElse(null);
        structure = new ReactorCoreStructure.Result(box, false, fuel, List.of(), owner.getBlockPos(), fault, status);
        rodBottoms = positions(tag.getList("RodBottoms", Tag.TAG_LONG)).toArray(BlockPos[]::new);
        // Every rod and column list is as long as the rods and columns it describes, however the packet came.
        int rods = rodBottoms.length, columns = fuel.size();
        rodSlots = java.util.Arrays.copyOf(tag.getByteArray("RodSlots"), rods);
        rodDepth = Finite.all(floats(tag.getList("RodDepth", Tag.TAG_FLOAT)), rods, 0, 0, (float) layout().coreHeight());
        rodWear = Finite.all(floats(tag.getList("RodWear", Tag.TAG_FLOAT)), rods, 0, 0, (float) ReactorPhysics.MAX_ABSORBER_WEAR);
        fuelChannels = tag.getInt("FuelChannels");
        controlChannels = tag.getInt("ControlChannels");
        columnVoid = Finite.all(floats(tag.getList("ColumnVoid", Tag.TAG_FLOAT)), columns, 0, 0, 1);
        columnKelvin = Finite.all(floats(tag.getList("ColumnKelvin", Tag.TAG_FLOAT)), columns, 293.15f, 1, 10000);
        columnSurfaceKelvin = tag.contains("ColumnSurfaceKelvin") ? Finite.all(floats(tag.getList("ColumnSurfaceKelvin", Tag.TAG_FLOAT)), columns, 293.15f, 1, 10000) : columnKelvin.clone();
        columnFlow = Finite.all(floats(tag.getList("ColumnFlow", Tag.TAG_FLOAT)), columns, 0, 0, Float.MAX_VALUE);
        columnCoolant = java.util.Arrays.copyOf(tag.getIntArray("ColumnCoolant"), columns);
        rodColumnInsertion = Finite.all(floats(tag.getList("RodInsertion", Tag.TAG_FLOAT)), columns, 0, 0, 1);
        rodInCore = new boolean[rodBottoms.length];
    }

    private static ListTag positions(List<BlockPos> positions) {
        var list = new ListTag();
        for (BlockPos pos : positions) list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        return list;
    }

    private static List<BlockPos> positions(ListTag list) {
        var positions = new java.util.ArrayList<BlockPos>();
        for (Tag tag : list) positions.add(BlockPos.of(((net.minecraft.nbt.LongTag) tag).getAsLong()));
        return positions;
    }

    private static ListTag floats(float[] values) {
        var list = new ListTag();
        for (float value : values) list.add(net.minecraft.nbt.FloatTag.valueOf(value));
        return list;
    }

    private static float[] floats(ListTag list) {
        float[] values = new float[list.size()];
        for (int i = 0; i < values.length; i++) values[i] = list.getFloat(i);
        return values;
    }

    private record RodKey(BlockPos bottom, int slot) { }
}
