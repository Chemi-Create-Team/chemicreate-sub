package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.entity.*;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEMachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** A controller owns one voice, including every rotor in one turbine housing. */
record MachineSoundSource(BlockEntity owner, Kind kind, Vec3 position, List<SteamTurbineBlockEntity> rotors) {
    enum Kind {
        REACTOR(CEMachineSounds.REACTOR_CIRCULATION, .26f), FUSION(CEMachineSounds.FUSION_DRIVE, .27f),
        TURBINE_HIGH(CEMachineSounds.TURBINE_HIGH, .28f), TURBINE_LOW(CEMachineSounds.TURBINE_LOW, .30f),
        COMPRESSOR(CEMachineSounds.COMPRESSOR, .24f), AGITATOR(CEMachineSounds.AGITATOR, .20f),
        HOT_PROCESS(CEMachineSounds.HOT_PROCESS, .22f), FLUID_PROCESS(CEMachineSounds.FLUID_PROCESS, .17f),
        BLOWER(CEMachineSounds.BLOWER, .22f), CRANE(CEMachineSounds.CRANE_DRIVE, .23f), FLARE(CEMachineSounds.FLARE_ROAR,.65f);

        final Supplier<SoundEvent> event;
        final float volume;
        Kind(Supplier<SoundEvent> event, float volume) { this.event = event;this.volume = volume; }
    }

    static MachineSoundSource find(BlockEntity be) {
        if (be instanceof SteamTurbineBlockEntity turbine) return turbine(turbine);
        Kind kind = kind(be);
        if (kind == null) return null;
        Vec3 position = Vec3.atCenterOf(be.getBlockPos());
        if (be instanceof FuelChannelBlockEntity channel && channel.core().box() != null)
            position = Vec3.atCenterOf(channel.core().box().getCenter());
        else if (be instanceof LiddedVesselBlockEntity vessel)
            position = Vec3.atLowerCornerOf(be.getBlockPos()).add(vessel.getWidth() / 2d, vessel.getHeight() / 2d, vessel.getWidth() / 2d);
        return new MachineSoundSource(be, kind, position, List.of());
    }

    boolean active() {
        if (!present(owner)) return false;
        if (rotors.isEmpty()) return kind(owner) == kind;
        boolean work = false;
        for (var rotor : rotors) {
            if (!present(rotor)) return false;
            work |= rotor.thermodynamics().work() > 0;
        }
        return work;
    }
    int range() { return kind==Kind.FLARE ? 96 : MachineSounds.RANGE; }

    boolean intact() {
        return rotors.isEmpty() || HighPressureTurbineMultiblock.isFormed(owner.getLevel(), owner.getBlockPos(), owner.getBlockState());
    }

    private static boolean present(BlockEntity be) {
        var level = be.getLevel();
        return !be.isRemoved() && level != null && level.hasChunkAt(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }

    private static MachineSoundSource turbine(SteamTurbineBlockEntity be) {
        var level = be.getLevel();var pos = be.getBlockPos();var state = be.getBlockState();
        if (level == null) return null;
        var axis = state.getValue(SteamTurbineBlock.AXIS);
        var previous = pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
        if (level.hasChunkAt(previous) && level.getBlockEntity(previous) instanceof SteamTurbineBlockEntity before
            && before.getBlockState().getValue(SteamTurbineBlock.AXIS) == axis
            && CEBlocks.isHighPressureSteamTurbine(before.getBlockState()) == CEBlocks.isHighPressureSteamTurbine(state)) return null;
        var assembly = HighPressureTurbineMultiblock.assemblyInfo(level, pos, state);
        if (!assembly.formed()) return null;
        var rotors = new ArrayList<SteamTurbineBlockEntity>(assembly.shaftCount());
        var step = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        for (int i = 0; i < assembly.shaftCount(); i++) {
            var at = assembly.start().relative(step, i);
            if (!level.hasChunkAt(at) || !(level.getBlockEntity(at) instanceof SteamTurbineBlockEntity rotor)) return null;
            rotors.add(rotor);
        }
        var kind = CEBlocks.isHighPressureSteamTurbine(state) ? Kind.TURBINE_HIGH : Kind.TURBINE_LOW;
        var center = Vec3.atCenterOf(assembly.start()).add(Vec3.atCenterOf(assembly.end())).scale(.5);
        return new MachineSoundSource(be, kind, center, List.copyOf(rotors));
    }

    private static Kind kind(BlockEntity be) {
        if (be instanceof net.createplant.block.entity.WetProcessBlockEntity wet) return wet.formed() && Math.abs(wet.getSpeed()) > 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof net.createplant.block.entity.GasCentrifugeBlockEntity centrifuge) return Math.abs(centrifuge.getSpeed()) >= 32 ? Kind.AGITATOR : null;
        if (be instanceof net.createplant.block.entity.CubicPressBlockEntity press) return press.running() ? Kind.HOT_PROCESS : null;
        if (be instanceof FlareTipBlockEntity flare) return flare.burning() ? Kind.FLARE : null;
        if (be instanceof net.createplant.block.entity.HorizontalTankBlockEntity boiler && boiler.status().equals("running")) return Kind.HOT_PROCESS;
        if (be instanceof CryogenicColdBoxBlockEntity box) return box.formed() && box.watts() > 0 ? Kind.COMPRESSOR : null;
        if (be instanceof FuelChannelBlockEntity channel)
            return channel.core() != null && channel.core().formed() && channel.core().power() > 0 ? Kind.REACTOR : null;
        if (be instanceof FusionReactorControllerBlockEntity fusion)
            return fusion.formed() && (fusion.fusionWatts() > 0 || fusion.electricalWatts() > 0) ? Kind.FUSION : null;
        if (be instanceof LiddedVesselBlockEntity vessel && !vessel.isController()) return null;
        if (be instanceof ReactorMachineBlockEntity reactor) {
            if (!reactor.formed() || !reactor.running()) return null;
            return reactor.module() == ReactorModule.PREHEATER ? Kind.HOT_PROCESS
                : reactor.module() == ReactorModule.STIRRED ? Kind.AGITATOR : Kind.FLUID_PROCESS;
        }
        if (be instanceof ReciprocatingCompressorBlockEntity compressor)
            return compressor.flow() > 0 ? Kind.COMPRESSOR : null;
        if (be instanceof RadiatorBlockEntity radiator)
            return radiator.owner() == radiator && radiator.fanCount() > 0 && radiator.fanRpm() > 0 ? Kind.BLOWER : null;
        if (be instanceof FractionatingColumnBlockEntity column)
            return column.isController() && column.running() ? Kind.FLUID_PROCESS : null;
        if (be instanceof ElectrolysisFurnaceBlockEntity furnace)
            return furnace.formed() && furnace.watts() > 0 ? Kind.HOT_PROCESS : null;
        if (be instanceof ElectrolysisControllerBlockEntity electrolysis) return electrolysis.isMaster() && electrolysis.running() > 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof CoolingTowerBlockEntity tower)
            return tower.formed() && tower.watts() > 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof CondenserWaterboxBlockEntity condenser)
            return condenser.formed() && condenser.watts() > 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof FuelCycleMachineBlockEntity cycle)
            return !cycle.formed() ? null : cycle.melter() && (cycle.watts() > 0 || cycle.status().equals("vitrifying")) ? Kind.HOT_PROCESS
                : cycle.status().equals("extracting") ? Kind.FLUID_PROCESS : null;
        if (be instanceof ConverterTrunnionBearingBlockEntity converter)
            return converter.formed() && converter.status().equals("blowing") ? Kind.HOT_PROCESS : null;
        if (be instanceof FuelCellBlockEntity cell)
            return cell.watts() > 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof CraneDriveBlockEntity crane)
            return crane.structure() != null && crane.working() ? Kind.CRANE : null;
        return null;
    }
}
