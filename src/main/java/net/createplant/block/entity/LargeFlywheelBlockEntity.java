package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.block.LargeFlywheelBlock;
import net.createplant.registry.CEBlockEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public class LargeFlywheelBlockEntity extends GeneratingKineticBlockEntity {
    private static final double STEEL_DENSITY_KG_PER_M3 = 7_850.0;
    private static final double OUTER_RADIUS_M = 2.74617;
    private static final double INNER_RADIUS_M = 0.5;
    private static final double THICKNESS_M = 0.375;
    private static final double MATERIAL_FILL_FACTOR = 0.35;
    private static final double MASS_KG = Math.PI
        * (OUTER_RADIUS_M * OUTER_RADIUS_M - INNER_RADIUS_M * INNER_RADIUS_M)
        * THICKNESS_M * MATERIAL_FILL_FACTOR * STEEL_DENSITY_KG_PER_M3;
    private static final double MOMENT_OF_INERTIA_KG_M2 = 0.5 * MASS_KG
        * (OUTER_RADIUS_M * OUTER_RADIUS_M + INNER_RADIUS_M * INNER_RADIUS_M);

    private static final double TICKS_PER_SECOND = 20.0;
    // Create: New Age default: 1 SU used for 1 tick = 0.029296875 FE.
    // Treating 1 FE as 1 joule gives 0.5859375 watts per continuously used SU.
    private static final double NEW_AGE_ENERGY_PER_SU_TICK = 0.029296875;
    private static final double WATTS_PER_SU = NEW_AGE_ENERGY_PER_SU_TICK * TICKS_PER_SECOND;
    private static final double CHARGE_EFFICIENCY = 0.95;
    private static final double DISCHARGE_EFFICIENCY = 0.95;
    private static final double BEARING_DRAG_COEFFICIENT_NM_S = 18.0;
    // A 5.5 m steel rotor at 256 RPM stores roughly 35 MJ.  The old 262 kSU ceiling let it exchange only
    // 154 kW, so it could not bridge even a 2 MW source-transfer transient despite having ample stored energy.
    // Ten megawatts is below the rotor's energy-limited short-duration output and lets it perform its intended
    // station flywheel duty without becoming a sustained power source.
    private static final double MAX_TRANSFER_SU = 16_777_216.0;
    private static final double MAX_TRANSFER_POWER_W = MAX_TRANSFER_SU * WATTS_PER_SU;
    private static final double MIN_OPERATING_RPM = 1.0;
    private static final double ENERGY_TARGET_TOLERANCE_J = 1.0;
    private static final int MAX_ASSEMBLY_LENGTH = 64;

    private final LerpedFloat visualSpeed = LerpedFloat.linear()
        .chase(0, 0.015625, LerpedFloat.Chaser.EXP);

    private double storedEnergyJ;
    private double rotorRpm;
    private int rotorDirection = 1;
    private double chargePowerW;
    private double dischargePowerW;
    private double accelerationPowerW;
    private double maintenancePowerW;
    private float suppliedCapacityPerRpm;
    private float chargingStressPerRpm;
    private float lastGeneratedSpeed;
    private float clientAngle;
    private float lastPublishedCapacitySu;
    private double lastSyncedRotorRpm = Double.NaN;
    private long lastRotationUpdateTick = Long.MIN_VALUE;
    private double displayedEnergyJ;
    private double displayedRotorRpm;
    private double displayedChargePowerW;
    private double displayedDischargePowerW;
    private double displayedAccelerationPowerW;
    private double displayedMaintenancePowerW;
    private double displayedInertiaKgM2 = MOMENT_OF_INERTIA_KG_M2;
    private int displayedFlywheelCount = 1;

    public LargeFlywheelBlockEntity(BlockPos pos, BlockState state) {
        super(CEBlockEntityTypes.LARGE_FLYWHEEL.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        if (level.isClientSide()) {
            visualSpeed.updateChaseTarget((float) rotorRpm * rotorDirection);
            visualSpeed.tickChaser();
            LargeFlywheelBlockEntity visualAnchor = visualAssemblyAnchor();
            if (visualAnchor == this) {
                clientAngle = Mth.positiveModulo(clientAngle + visualSpeed.getValue() * 3 / 10, 360f);
            } else {
                clientAngle = visualAnchor.clientAngle;
            }
            return;
        }

        Assembly assembly = findAssembly();
        if (!assembly.isCoordinator(worldPosition, level.getGameTime())) {
            return;
        }
        recoverStaleOpposingNetwork(assembly);
        if (breakOpposingRotationBoundary(assembly)) {
            return;
        }
        tickPhysics(assembly);
    }

    /**
     * A loaded kinetic network can briefly retain its old signed speed after its actual source disappears.
     * That metadata is not an external reverse drive and must never make an interior flywheel destroy itself.
     * Detach the whole coupled set first so its stored rotor can publish one coherent direction again.
     */
    private void recoverStaleOpposingNetwork(Assembly assembly) {
        double networkSpeed = getTheoreticalSpeed();
        if (Math.abs(networkSpeed) <= MIN_OPERATING_RPM
            || assemblyEnergy(assembly) <= energyForRpm(MIN_OPERATING_RPM, assembly.members().size())
            || directionSign(networkSpeed) == rotorDirection) {
            return;
        }

        Set<BlockPos> memberPositions = new HashSet<>();
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            memberPositions.add(member.getBlockPos());
        }
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            if (member.source != null && !memberPositions.contains(member.source)
                && level.getBlockEntity(member.source) instanceof KineticBlockEntity) {
                return;
            }
        }

        for (LargeFlywheelBlockEntity member : assembly.members()) {
            member.applyNewSpeed(member.getTheoreticalSpeed(), 0);
            member.lastGeneratedSpeed = 0;
        }
    }

    private boolean breakOpposingRotationBoundary(Assembly assembly) {
        List<LargeFlywheelBlockEntity> members = assembly.members();
        int previousActiveIndex = -1;
        for (int i = 0; i < members.size(); i++) {
            LargeFlywheelBlockEntity current = members.get(i);
            if (!isActivelyRotating(current)) {
                continue;
            }
            if (previousActiveIndex >= 0) {
                LargeFlywheelBlockEntity previous = members.get(previousActiveIndex);
                if (previous.rotorDirection != current.rotorDirection) {
                    BlockPos impactPos = members.get((previousActiveIndex + i) / 2).getBlockPos();
                    level.playSound(null, impactPos, SoundEvents.ANVIL_LAND,
                        SoundSource.BLOCKS, 1.2f, 0.65f);
                    for (int broken = previousActiveIndex; broken <= i; broken++) {
                        LargeFlywheelBlockEntity flywheel = members.get(broken);
                        BlockPos brokenPos = flywheel.getBlockPos();
                        flywheel.discardStoredEnergy();
                        level.destroyBlock(brokenPos, true);
                    }
                    return true;
                }
            }
            previousActiveIndex = i;
        }
        return false;
    }

    private static boolean isActivelyRotating(LargeFlywheelBlockEntity flywheel) {
        return flywheel.rotorRpm > MIN_OPERATING_RPM
            && flywheel.storedEnergyJ > energyForRpm(MIN_OPERATING_RPM, 1);
    }

    private void tickPhysics(Assembly assembly) {
        int flywheelCount = assembly.members().size();
        double balanceW = externalPowerBalance(assembly);
        synchronizeCoupledRotors(assembly);

        double networkRpm = Math.abs(getTheoreticalSpeed());
        int networkDirection = directionSign(getTheoreticalSpeed());

        chargePowerW = 0;
        dischargePowerW = 0;
        accelerationPowerW = 0;
        maintenancePowerW = bearingLossPowerW(rotorRpm, flywheelCount);
        suppliedCapacityPerRpm = 0;
        chargingStressPerRpm = 0;

        if (balanceW > 0 && networkRpm > 0) {
            if (rotorRpm <= MIN_OPERATING_RPM || rotorDirection == networkDirection) {
                rotorDirection = networkDirection;
            } else {
                dissipateOpposingRotation(networkRpm, flywheelCount);
            }
            double targetEnergy = energyForRpm(networkRpm, flywheelCount);
            double energyRoom = Math.max(0, targetEnergy - storedEnergyJ);
            double requestedAccelerationPower = energyRoom > ENERGY_TARGET_TOLERANCE_J
                ? energyRoom * TICKS_PER_SECOND / CHARGE_EFFICIENCY
                : 0;
            double requestedMaintenancePower = maintenancePowerW / CHARGE_EFFICIENCY;
            chargePowerW = Math.min(Math.min(balanceW, maxTransferPowerW(flywheelCount)),
                requestedAccelerationPower + requestedMaintenancePower);
            maintenancePowerW = Math.min(chargePowerW, requestedMaintenancePower);
            accelerationPowerW = Math.max(0, chargePowerW - maintenancePowerW);
            storedEnergyJ += chargePowerW * CHARGE_EFFICIENCY / TICKS_PER_SECOND;
            chargingStressPerRpm = powerToStress(chargePowerW, networkRpm);
            // Publish the charged rotor as spinning reserve even while another source is driving the network.
            // A clutch split rebuilds the downstream network before this BE gets another tick; publishing zero
            // reserve here produced one overloaded tick, enough to extinguish a self-powered fusion plant.
            double reservePowerW = Math.min(maxTransferPowerW(flywheelCount),
                storedEnergyJ * TICKS_PER_SECOND * DISCHARGE_EFFICIENCY);
            suppliedCapacityPerRpm = powerToStress(reservePowerW, networkRpm);
        } else if (storedEnergyJ > energyForRpm(MIN_OPERATING_RPM, flywheelCount)) {
            double deficitW = Math.max(0, -balanceW);
            double availablePower = storedEnergyJ * TICKS_PER_SECOND * DISCHARGE_EFFICIENCY;
            double offeredPowerW = Math.min(maxTransferPowerW(flywheelCount), availablePower);
            suppliedCapacityPerRpm = powerToStress(offeredPowerW, Math.max(networkRpm, rotorRpm));
            dischargePowerW = Math.min(deficitW, offeredPowerW);
            storedEnergyJ -= dischargePowerW / (DISCHARGE_EFFICIENCY * TICKS_PER_SECOND);
        }

        applyBearingLoss(flywheelCount);
        rotorRpm = rpmForEnergy(storedEnergyJ, flywheelCount);
        float totalCapacityPerRpm = suppliedCapacityPerRpm;
        float totalStressPerRpm = chargingStressPerRpm;
        distributeAssemblyState(assembly, totalCapacityPerRpm / flywheelCount,
            totalStressPerRpm / flywheelCount);
        publishDistributedNetworkState(assembly);
        updateDistributedRotation(assembly);
        updateOverlaySnapshot(assembly);

        setChanged();
        boolean lowSpeed = rotorRpm < 16;
        boolean syncDue = lowSpeed || level.getGameTime() % 5 == 0;
        double syncThreshold = lowSpeed ? .005 : .25;
        boolean rotorSyncNeeded = Double.isNaN(lastSyncedRotorRpm)
            || Math.abs(rotorRpm - lastSyncedRotorRpm) >= syncThreshold;
        if (syncDue && rotorSyncNeeded) {
            lastSyncedRotorRpm = rotorRpm;
            sendAssemblyData(assembly);
        }
    }

    private double externalPowerBalance(Assembly assembly) {
        if (!hasNetwork()) {
            return 0;
        }

        KineticNetwork network = getOrCreateNetwork();
        double ownCapacitySu = 0;
        double ownStressSu = 0;
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            if (network.sources.getOrDefault(member, 0f) != 0) {
                ownCapacitySu += network.getActualCapacityOf(member);
            }
            if (network.members.getOrDefault(member, 0f) != 0) {
                ownStressSu += network.getActualStressOf(member);
            }
        }
        return suToWatts(Math.max(0, capacity - ownCapacitySu) - Math.max(0, stress - ownStressSu));
    }

    private void applyBearingLoss(int flywheelCount) {
        if (storedEnergyJ <= 0) {
            storedEnergyJ = 0;
            return;
        }
        storedEnergyJ = Math.max(0,
            storedEnergyJ - bearingLossPowerW(rotorRpm, flywheelCount) / TICKS_PER_SECOND);
    }

    private static double bearingLossPowerW(double rpm, int flywheelCount) {
        double omega = rpmToRadiansPerSecond(rpm);
        double dragTorque = BEARING_DRAG_COEFFICIENT_NM_S * omega;
        return dragTorque * omega * flywheelCount;
    }

    @Override
    public float getGeneratedSpeed() {
        Assembly assembly = findAssembly();
        int flywheelCount = assembly.members().size();
        if (assemblyEnergy(assembly) <= energyForRpm(MIN_OPERATING_RPM, flywheelCount)) {
            return 0;
        }
        if (hasSource() && suppliedCapacityPerRpm <= 0) {
            return 0;
        }
        return (float) rotorRpm * rotorDirection;
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = suppliedCapacityPerRpm;
        return suppliedCapacityPerRpm;
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = chargingStressPerRpm;
        return chargingStressPerRpm;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        try {
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.energy",
                formatEnergy(displayedEnergyJ), ChatFormatting.AQUA));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.rotor_speed",
                format(displayedRotorRpm) + " RPM", ChatFormatting.AQUA));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.power",
                displayedSignedPower(), displayedChargePowerW > 0 ? ChatFormatting.YELLOW
                    : displayedDischargePowerW > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.acceleration_power",
                formatPower(displayedAccelerationPowerW), displayedAccelerationPowerW > 0
                    ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.maintenance_power",
                formatPower(displayedMaintenancePowerW), displayedMaintenancePowerW > 0
                    ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.inertia",
                format(displayedInertiaKgM2) + " kg m^2",
                ChatFormatting.GRAY));
            tooltip.add(line("goggle.create_refinery_n_reactors.flywheel.count",
                Integer.toString(displayedFlywheelCount), ChatFormatting.GRAY));
            return true;
        } finally { net.createplant.compat.display.MachineReadings.compact(tooltip); }
    }

    public float visualAngle(float partialTicks) {
        LargeFlywheelBlockEntity visualAnchor = visualAssemblyAnchor();
        if (visualAnchor != this) {
            return visualAnchor.visualAngle(partialTicks);
        }
        float partialAngle = visualSpeed.getValue(partialTicks) * 3 / 10;
        return clientAngle + partialAngle * partialTicks;
    }

    public double displayedEnergyJ() { return displayedEnergyJ; }
    public double displayedRotorRpm() { return displayedRotorRpm; }
    public double displayedChargePowerW() { return displayedChargePowerW; }
    public double displayedDischargePowerW() { return displayedDischargePowerW; }
    public double displayedAccelerationPowerW() { return displayedAccelerationPowerW; }
    public double displayedMaintenancePowerW() { return displayedMaintenancePowerW; }
    public double displayedInertiaKgM2() { return displayedInertiaKgM2; }
    public int displayedFlywheelCount() { return displayedFlywheelCount; }

    public double storedEnergyJ() {
        return storedEnergyJ;
    }

    public double rotorRpm() {
        return rotorRpm;
    }

    public void discardStoredEnergy() {
        storedEnergyJ = 0;
        rotorRpm = 0;
        chargePowerW = 0;
        dischargePowerW = 0;
        accelerationPowerW = 0;
        maintenancePowerW = 0;
        suppliedCapacityPerRpm = 0;
        chargingStressPerRpm = 0;
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("StoredEnergyJ", storedEnergyJ);
        tag.putDouble("RotorRpm", rotorRpm);
        tag.putInt("RotorDirection", rotorDirection);
        tag.putDouble("ChargePowerW", chargePowerW);
        tag.putDouble("DischargePowerW", dischargePowerW);
        tag.putDouble("AccelerationPowerW", accelerationPowerW);
        tag.putDouble("MaintenancePowerW", maintenancePowerW);
        tag.putFloat("SuppliedCapacityPerRpm", suppliedCapacityPerRpm);
        tag.putFloat("ChargingStressPerRpm", chargingStressPerRpm);
        tag.putDouble("DisplayedEnergyJ", displayedEnergyJ);
        tag.putDouble("DisplayedRotorRpm", displayedRotorRpm);
        tag.putDouble("DisplayedChargePowerW", displayedChargePowerW);
        tag.putDouble("DisplayedDischargePowerW", displayedDischargePowerW);
        tag.putDouble("DisplayedAccelerationPowerW", displayedAccelerationPowerW);
        tag.putDouble("DisplayedMaintenancePowerW", displayedMaintenancePowerW);
        tag.putDouble("DisplayedInertiaKgM2", displayedInertiaKgM2);
        tag.putInt("DisplayedFlywheelCount", displayedFlywheelCount);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        storedEnergyJ = nonNegative(tag.getDouble("StoredEnergyJ"));
        rotorRpm = tag.contains("RotorRpm") ? nonNegative(tag.getDouble("RotorRpm"))
            : rpmForEnergy(storedEnergyJ, 1);
        rotorDirection = tag.contains("RotorDirection")
            ? directionSign(tag.getInt("RotorDirection"))
            : directionSign(lastGeneratedSpeed);
        chargePowerW = nonNegative(tag.getDouble("ChargePowerW"));
        dischargePowerW = nonNegative(tag.getDouble("DischargePowerW"));
        accelerationPowerW = nonNegative(tag.getDouble("AccelerationPowerW"));
        maintenancePowerW = nonNegative(tag.getDouble("MaintenancePowerW"));
        suppliedCapacityPerRpm = nonNegative(tag.getFloat("SuppliedCapacityPerRpm"));
        chargingStressPerRpm = nonNegative(tag.getFloat("ChargingStressPerRpm"));
        displayedEnergyJ = nonNegative(tag.getDouble("DisplayedEnergyJ"));
        displayedRotorRpm = nonNegative(tag.getDouble("DisplayedRotorRpm"));
        displayedChargePowerW = nonNegative(tag.getDouble("DisplayedChargePowerW"));
        displayedDischargePowerW = nonNegative(tag.getDouble("DisplayedDischargePowerW"));
        displayedAccelerationPowerW = nonNegative(tag.getDouble("DisplayedAccelerationPowerW"));
        displayedMaintenancePowerW = nonNegative(tag.getDouble("DisplayedMaintenancePowerW"));
        displayedInertiaKgM2 = tag.contains("DisplayedInertiaKgM2")
            ? positiveOr(tag.getDouble("DisplayedInertiaKgM2"), MOMENT_OF_INERTIA_KG_M2)
            : MOMENT_OF_INERTIA_KG_M2;
        displayedFlywheelCount = tag.contains("DisplayedFlywheelCount")
            ? Math.max(1, tag.getInt("DisplayedFlywheelCount"))
            : 1;
        if (clientPacket) {
            visualSpeed.updateChaseTarget((float) rotorRpm * rotorDirection);
        }
    }

    private static double nonNegative(double value) { return Double.isFinite(value) ? Math.max(0,value) : 0; }
    private static float nonNegative(float value) { return Float.isFinite(value) ? Math.max(0,value) : 0; }
    private static double positiveOr(double value,double fallback) { return Double.isFinite(value) && value>0 ? value : fallback; }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(4);
    }

    private void distributeAssemblyState(Assembly assembly, float capacitySharePerRpm,
        float stressSharePerRpm) {
        double energyPerFlywheel = storedEnergyJ / assembly.members().size();
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            member.storedEnergyJ = energyPerFlywheel;
            member.rotorRpm = rotorRpm;
            member.rotorDirection = rotorDirection;
            member.chargePowerW = chargePowerW;
            member.dischargePowerW = dischargePowerW;
            member.accelerationPowerW = accelerationPowerW;
            member.maintenancePowerW = maintenancePowerW;
            member.suppliedCapacityPerRpm = capacitySharePerRpm;
            member.chargingStressPerRpm = stressSharePerRpm;
            member.setChanged();
        }
    }

    private void publishDistributedNetworkState(Assembly assembly) {
        Set<KineticNetwork> changedNetworks = new HashSet<>();
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            if (!member.hasNetwork()) {
                continue;
            }
            KineticNetwork network = member.getOrCreateNetwork();
            float registeredCapacity = network.sources.getOrDefault(member, 0f);
            float registeredStress = network.members.getOrDefault(member, 0f);
            boolean capacityChanged = networkValueChanged(registeredCapacity, member.suppliedCapacityPerRpm);
            boolean stressChanged = networkValueChanged(registeredStress, member.chargingStressPerRpm);
            if (capacityChanged) {
                registeredCapacity = member.calculateAddedStressCapacity();
                network.sources.put(member, registeredCapacity);
            }
            if (stressChanged) {
                network.members.put(member, member.calculateStressApplied());
            }

            // The registered capacity is per RPM, and Create totals it using the rotor's generated speed.
            float actualCapacitySu = registeredCapacity == 0 ? 0 : network.getActualCapacityOf(member);
            if (capacityChanged || stressChanged || actualCapacitySu != member.lastPublishedCapacitySu) {
                changedNetworks.add(network);
                member.lastPublishedCapacitySu = actualCapacitySu;
            }
        }
        changedNetworks.forEach(KineticNetwork::updateNetwork);
    }

    private static boolean networkValueChanged(float previous, float current) {
        return (previous == 0) != (current == 0) || Math.abs(previous - current) >= 0.01f;
    }

    private void updateDistributedRotation(Assembly assembly) {
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            float generatedSpeed = member.getGeneratedSpeed();
            boolean sourceStateChanged = (generatedSpeed == 0) != (member.lastGeneratedSpeed == 0);
            float speedThreshold = Math.max(1f, Math.abs(member.lastGeneratedSpeed) * .005f);
            boolean speedChanged = Math.abs(generatedSpeed - member.lastGeneratedSpeed) >= speedThreshold;
            boolean intervalElapsed = level.getGameTime() - member.lastRotationUpdateTick >= 5;
            if (sourceStateChanged || speedChanged && intervalElapsed) {
                member.lastGeneratedSpeed = generatedSpeed;
                member.lastRotationUpdateTick = level.getGameTime();
                member.updateGeneratedRotation();
            }
        }
    }

    private void updateOverlaySnapshot(Assembly assembly) {
        long gameTime = level.getGameTime();
        if (gameTime % 20 != 0) {
            return;
        }
        double totalEnergy = assemblyEnergy(assembly);
        double totalInertia = MOMENT_OF_INERTIA_KG_M2 * assembly.members().size();
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            member.displayedEnergyJ = totalEnergy;
            member.displayedRotorRpm = rotorRpm;
            member.displayedChargePowerW = chargePowerW;
            member.displayedDischargePowerW = dischargePowerW;
            member.displayedAccelerationPowerW = accelerationPowerW;
            member.displayedMaintenancePowerW = maintenancePowerW;
            member.displayedInertiaKgM2 = totalInertia;
            member.displayedFlywheelCount = assembly.members().size();
            member.sendData();
        }
    }

    private void sendAssemblyData(Assembly assembly) {
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            member.lastSyncedRotorRpm = rotorRpm;
            member.sendData();
        }
    }

    private double assemblyEnergy(Assembly assembly) {
        double total = 0;
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            total += Math.max(0, member.storedEnergyJ);
        }
        return total;
    }

    private void synchronizeCoupledRotors(Assembly assembly) {
        int count = assembly.members().size();
        double signedRpmSum = 0;
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            double memberRpm = member.rotorRpm > 0
                ? member.rotorRpm
                : rpmForEnergy(Math.max(0, member.storedEnergyJ), 1);
            signedRpmSum += memberRpm * member.rotorDirection;
        }

        double coupledSignedRpm = signedRpmSum / count;
        rotorDirection = coupledSignedRpm == 0
            ? stableDirection(assembly)
            : directionSign(coupledSignedRpm);
        rotorRpm = Math.abs(coupledSignedRpm);
        storedEnergyJ = energyForRpm(rotorRpm, count);
    }

    private void dissipateOpposingRotation(double networkRpm, int flywheelCount) {
        double rotorSignedRpm = rotorRpm * rotorDirection;
        double networkSignedRpm = networkRpm * directionSign(getTheoreticalSpeed());
        double coupledSignedRpm = (rotorSignedRpm + networkSignedRpm) * 0.5;
        rotorDirection = coupledSignedRpm == 0
            ? directionSign(networkSignedRpm)
            : directionSign(coupledSignedRpm);
        rotorRpm = Math.abs(coupledSignedRpm);
        storedEnergyJ = energyForRpm(rotorRpm, flywheelCount);
    }

    private static int stableDirection(Assembly assembly) {
        for (LargeFlywheelBlockEntity member : assembly.members()) {
            if (member.rotorRpm > MIN_OPERATING_RPM) {
                return member.rotorDirection;
            }
        }
        return 1;
    }

    private static int directionSign(double speed) {
        return speed < 0 ? -1 : 1;
    }

    private Assembly findAssembly() {
        if (level == null || !getBlockState().hasProperty(BlockStateProperties.AXIS)) {
            return new Assembly(List.of(this));
        }
        Direction.Axis axis = getBlockState().getValue(BlockStateProperties.AXIS);
        Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction positive = negative.getOpposite();
        BlockPos start = worldPosition;
        for (int i = 1; i < MAX_ASSEMBLY_LENGTH; i++) {
            BlockPos candidate = start.relative(negative);
            if (!isMatchingFlywheel(candidate, axis)) {
                break;
            }
            start = candidate;
        }

        List<LargeFlywheelBlockEntity> members = new ArrayList<>();
        BlockPos cursor = start;
        for (int i = 0; i < MAX_ASSEMBLY_LENGTH; i++) {
            if (!isMatchingFlywheel(cursor, axis)) {
                break;
            }
            if (level.getBlockEntity(cursor) instanceof LargeFlywheelBlockEntity flywheel) {
                members.add(flywheel);
            }
            cursor = cursor.relative(positive);
        }
        return members.isEmpty() ? new Assembly(List.of(this)) : new Assembly(List.copyOf(members));
    }

    private boolean isMatchingFlywheel(BlockPos pos, Direction.Axis axis) {
        if (level == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LargeFlywheelBlock
            && state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) == axis;
    }

    private LargeFlywheelBlockEntity visualAssemblyAnchor() {
        if (level == null || !level.isClientSide()
            || !getBlockState().hasProperty(BlockStateProperties.AXIS)) {
            return this;
        }
        Direction.Axis axis = getBlockState().getValue(BlockStateProperties.AXIS);
        Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        BlockPos anchorPos = worldPosition;
        for (int i = 1; i < MAX_ASSEMBLY_LENGTH; i++) {
            BlockPos candidate = anchorPos.relative(negative);
            if (!isMatchingFlywheel(candidate, axis)) {
                break;
            }
            anchorPos = candidate;
        }
        return level.getBlockEntity(anchorPos) instanceof LargeFlywheelBlockEntity anchor
            ? anchor
            : this;
    }

    private static double energyForRpm(double rpm, int flywheelCount) {
        double omega = rpmToRadiansPerSecond(rpm);
        return 0.5 * MOMENT_OF_INERTIA_KG_M2 * flywheelCount * omega * omega;
    }

    private static double rpmForEnergy(double energyJ, int flywheelCount) {
        if (energyJ <= 0) {
            return 0;
        }
        double omega = Math.sqrt(2 * energyJ / (MOMENT_OF_INERTIA_KG_M2 * flywheelCount));
        return omega * 60 / (2 * Math.PI);
    }

    private static double rpmToRadiansPerSecond(double rpm) {
        return rpm * 2 * Math.PI / 60;
    }

    private static double maxTransferPowerW(int flywheelCount) {
        return MAX_TRANSFER_POWER_W * flywheelCount;
    }

    private static float powerToStress(double powerW, double rpm) {
        return rpm <= 0.001 ? 0 : (float) (wattsToSu(powerW) / rpm);
    }

    private static double suToWatts(double su) {
        return su * WATTS_PER_SU;
    }

    private static double wattsToSu(double watts) {
        return watts / WATTS_PER_SU;
    }

    private String displayedSignedPower() {
        if (displayedChargePowerW > 0) {
            return "-" + formatPower(displayedChargePowerW);
        }
        if (displayedDischargePowerW > 0) {
            return "+" + formatPower(displayedDischargePowerW);
        }
        return "0 W";
    }

    private static Component line(String key, String value, ChatFormatting color) {
        return Component.literal("  ")
            .append(Component.translatable(key).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": "))
            .append(Component.literal(value).withStyle(color));
    }

    private static String formatEnergy(double joules) {
        if (joules >= 1_000_000) {
            return format(joules / 1_000_000) + " MJ";
        }
        if (joules >= 1_000) {
            return format(joules / 1_000) + " kJ";
        }
        return format(joules) + " J";
    }

    private static String formatPower(double watts) {
        if (watts >= 1_000_000) {
            return format(watts / 1_000_000) + " MW";
        }
        if (watts >= 1_000) {
            return format(watts / 1_000) + " kW";
        }
        return format(watts) + " W";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record Assembly(List<LargeFlywheelBlockEntity> members) {
        boolean isCoordinator(BlockPos pos, long gameTime) {
            if (members.isEmpty()) {
                return false;
            }
            int index = Math.floorMod(gameTime, members.size());
            return members.get(index).getBlockPos().equals(pos);
        }
    }
}
