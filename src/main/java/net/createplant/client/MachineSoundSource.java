package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.MaterialPumpBlock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.entity.*;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEMachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** A controller owns one voice, including every rotor in one turbine housing. */
public record MachineSoundSource(BlockEntity owner, Kind kind, Vec3 position, List<SteamTurbineBlockEntity> rotors) {
    public static final class Kind {
        private static final List<Kind> ALL = new ArrayList<>();
        public static List<Kind> values() { return List.copyOf(ALL); }

        public static final Kind REACTOR = new Kind(CEMachineSounds.REACTOR_CIRCULATION, .26f); 
        public static final Kind TURBINE_HIGH = new Kind(CEMachineSounds.TURBINE_HIGH, .28f); public static final Kind TURBINE_LOW = new Kind(CEMachineSounds.TURBINE_LOW, .30f);
        public static final Kind COMPRESSOR = new Kind(CEMachineSounds.COMPRESSOR, .24f); public static final Kind AGITATOR = new Kind(CEMachineSounds.AGITATOR, .20f);
        public static final Kind HOT_PROCESS = new Kind(CEMachineSounds.HOT_PROCESS, .22f); public static final Kind FLUID_PROCESS = new Kind(CEMachineSounds.FLUID_PROCESS, .17f);
        public static final Kind BLOWER = new Kind(CEMachineSounds.BLOWER, .22f); public static final Kind FLARE = new Kind(CEMachineSounds.FLARE_ROAR, .65f, 96);
        
        public static final Kind BOILER = new Kind(CEMachineSounds.BOILER, .22f); public static final Kind COLDBOX = new Kind(CEMachineSounds.COLDBOX, .22f);
        public static final Kind SHELL_EXCHANGER = new Kind(CEMachineSounds.SHELL_EXCHANGER, .16f); public static final Kind LARGE_EXCHANGER = new Kind(CEMachineSounds.LARGE_EXCHANGER, .19f);
        public static final Kind JACKET = new Kind(CEMachineSounds.JACKET, .15f); public static final Kind ABSORBER = new Kind(CEMachineSounds.ABSORBER, .18f);
        public static final Kind PACKED_TOWER = new Kind(CEMachineSounds.PACKED_TOWER, .19f); public static final Kind REACTION_TOWER = new Kind(CEMachineSounds.REACTION_TOWER, .20f);
        public static final Kind VERTICAL_SEPARATOR = new Kind(CEMachineSounds.VERTICAL_SEPARATOR, .17f); public static final Kind SEPARATOR_DRUM = new Kind(CEMachineSounds.SEPARATOR_DRUM, .17f);
        public static final Kind DISTILLATION = new Kind(CEMachineSounds.DISTILLATION, .20f); public static final Kind ELECTRIC_FURNACE = new Kind(CEMachineSounds.ELECTRIC_FURNACE, .25f);
        public static final Kind ELECTROLYSIS = new Kind(CEMachineSounds.ELECTROLYSIS, .18f); public static final Kind COOLING_TOWER = new Kind(CEMachineSounds.COOLING_TOWER, .29f);
        public static final Kind CONDENSER = new Kind(CEMachineSounds.CONDENSER, .19f); 
        
        public static final Kind FUEL_CELL = new Kind(CEMachineSounds.FUEL_CELL, .14f); 
        public static final Kind GASIFIER = new Kind(CEMachineSounds.GASIFIER, .23f);
        public static final Kind MOTOR_GENERATOR = new Kind(CEMachineSounds.MOTOR_GENERATOR, .23f); public static final Kind FLYWHEEL = new Kind(CEMachineSounds.FLYWHEEL, .20f);
        public static final Kind VACUUM_PUMP = new Kind(CEMachineSounds.VACUUM_PUMP, .17f); public static final Kind ELECTRIC_PUMP = new Kind(CEMachineSounds.ELECTRIC_PUMP, .18f);
        public static final Kind MATERIAL_PUMP = new Kind(CEMachineSounds.MATERIAL_PUMP, .14f, 20); public static final Kind RELIEF_SCRUBBER = new Kind(CEMachineSounds.RELIEF_SCRUBBER, .22f);
        public static final Kind GAS_HOLDER = new Kind(CEMachineSounds.GAS_HOLDER, .16f); public static final Kind CONSTRUCTOR = new Kind(CEMachineSounds.CONSTRUCTOR, .19f);
        
        public static final Kind PRESSURISED_GAS = new Kind(CEMachineSounds.PRESSURISED_GAS, .19f, 16); public static final Kind HIGH_PRESSURE_GAS = new Kind(CEMachineSounds.HIGH_PRESSURE_GAS, .28f, 20);

        final Supplier<SoundEvent> event;
        final float volume;
        final int range;
        public Kind(Supplier<SoundEvent> event, float volume) { this(event, volume, MachineSounds.RANGE); }
        public Kind(Supplier<SoundEvent> event, float volume, int range) { this.event = event;this.volume = volume;this.range = range;ALL.add(this); }
    }

    private record Rule<T extends BlockEntity>(Class<T> type, java.util.function.Function<T, Kind> kind) {
        Kind apply(BlockEntity be) { return kind.apply(type.cast(be)); }
    }

    private record Place<T extends BlockEntity>(Class<T> type, java.util.function.Function<T, Vec3> position) {
        Vec3 apply(BlockEntity be) { return position.apply(type.cast(be)); }
    }

    private static final List<Rule<?>> RULES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<Place<?>> PLACES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The voice an addon's machine plays; null while it is idle. */
    public static <T extends BlockEntity> void register(Class<T> type, java.util.function.Function<T, Kind> kind) { RULES.add(new Rule<>(type, kind)); }

    /** Where an addon's machine is heard from when that is not the middle of its block; null keeps the middle. */
    public static <T extends BlockEntity> void registerPosition(Class<T> type, java.util.function.Function<T, Vec3> position) { PLACES.add(new Place<>(type, position)); }

    private static Vec3 place(BlockEntity be) {
        for (var place : PLACES) if (place.type().isInstance(be)) return place.apply(be);
        return null;
    }

    boolean pipe() { return kind == Kind.PRESSURISED_GAS || kind == Kind.HIGH_PRESSURE_GAS; }

    private static PipeGasFlow gasFlow(BlockEntity be) {
        return com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(be, PipeGasFlow.TYPE);
    }

    float volume() {
        if (!pipe()) return kind.volume;
        var flow = gasFlow(owner);
        return kind.volume * (flow == null ? 0 : .34f + .16f * flow.flowBand());
    }

    float pitch() {
        if (kind == Kind.MATERIAL_PUMP && owner instanceof PumpBlockEntity pump && owner.getBlockState().getBlock() instanceof MaterialPumpBlock block)
            return block.soundPitch() * (.8f + .2f * Math.min(128, Math.abs(pump.getSpeed())) / 64);
        if (!pipe()) return 1;
        var flow = gasFlow(owner);
        return flow == null ? 1 : .84f + .08f * flow.flowBand();
    }

    static MachineSoundSource find(BlockEntity be) {
        if (be instanceof SteamTurbineBlockEntity turbine) return turbine(turbine);
        Kind kind = kind(be);
        if (kind == null) return null;
        Vec3 position = Vec3.atCenterOf(be.getBlockPos());
        var placed = place(be);
        if (placed != null) position = placed;
        else if (be instanceof LiddedVesselBlockEntity vessel)
            position = Vec3.atLowerCornerOf(be.getBlockPos()).add(vessel.getWidth() / 2d, vessel.getHeight() / 2d, vessel.getWidth() / 2d);
        else if (be instanceof WetProcessBlockEntity clarifier) position = position.add(0, (1 - clarifier.height()) * .5, 0);
        else if (be instanceof ReactionTowerBlockEntity tower) position = position.add(0, (tower.layers() - 1) * .5, 0);
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
    int range() { return kind.range; }

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
        for (var rule : RULES) if (rule.type().isInstance(be)) return rule.apply(be);
        if (be instanceof WetProcessBlockEntity wet) return wet.formed() && wet.getSpeed() != 0 ? Kind.FLUID_PROCESS : null;
        if (be instanceof FlareTipBlockEntity flare) return flare.burning() ? Kind.FLARE : null;
        if (be instanceof HorizontalTankBlockEntity boiler) return boiler.status().equals("running") ? Kind.BOILER : null;
        if (be instanceof CryogenicColdBoxBlockEntity box) return box.formed() && box.watts() > 0 ? Kind.COLDBOX : null;
        if (be instanceof LiddedVesselBlockEntity vessel && !vessel.isController()) return null;
        if (be instanceof ReactorMachineBlockEntity reactor) {
            if (!reactor.formed() || !reactor.running()) return null;
            var exchanger = reactor.exchanger();
            if (exchanger != null) return switch (exchanger) {
                case SHELL -> Kind.SHELL_EXCHANGER;
                case LARGE -> Kind.LARGE_EXCHANGER;
                case JACKET -> Kind.JACKET;
            };
            return switch (reactor.module()) {
                case PREHEATER -> Kind.HOT_PROCESS;
                case STIRRED -> Kind.AGITATOR;
                case ABSORBER -> Kind.ABSORBER;
                case TOWER -> Kind.PACKED_TOWER;
                case NONE -> null;
            };
        }
        if (be instanceof ReactionTowerBlockEntity tower)
            return tower.status().equals("running") && tower.owner() == tower ? (tower.glazed() ? Kind.VERTICAL_SEPARATOR : Kind.REACTION_TOWER) : null;
        if (be instanceof AmmoniaSeparatorBlockEntity drum) return drum.separating() ? Kind.SEPARATOR_DRUM : null;
        if (be instanceof ReciprocatingCompressorBlockEntity compressor)
            return compressor.flow() > 0 ? Kind.COMPRESSOR : null;
        if (be instanceof RadiatorBlockEntity radiator)
            return radiator.owner() == radiator && radiator.fanCount() > 0 && radiator.fanRpm() > 0 ? Kind.BLOWER : null;
        if (be instanceof FractionatingColumnBlockEntity column)
            return column.isController() && column.running() ? Kind.DISTILLATION : null;
        if (be instanceof ElectrolysisFurnaceBlockEntity furnace)
            return furnace.formed() && furnace.watts() > 0 ? Kind.ELECTRIC_FURNACE : null;
        if (be instanceof ElectrolysisControllerBlockEntity electrolysis) return electrolysis.isMaster() && electrolysis.running() > 0 ? Kind.ELECTROLYSIS : null;
        if (be instanceof CoolingTowerBlockEntity tower)
            return tower.formed() && tower.watts() > 0 ? Kind.COOLING_TOWER : null;
        if (be instanceof CondenserWaterboxBlockEntity condenser)
            return condenser.formed() && condenser.watts() > 0 ? Kind.CONDENSER : null;
        if (be instanceof FuelCellBlockEntity cell)
            return cell.watts() > 0 ? Kind.FUEL_CELL : null;
        if (be instanceof GasifierBlockEntity gasifier)
            return gasifier.formed() && (gasifier.status().equals("running") || gasifier.status().equals("preheat")) ? Kind.GASIFIER : null;
        if (be instanceof LargeFlywheelBlockEntity flywheel) return flywheel.displayedRotorRpm() >= 1 ? Kind.FLYWHEEL : null;
        if (be instanceof ElectricPumpBlockEntity pump) return pump.getSpeed() != 0 ? Kind.ELECTRIC_PUMP : null;
        if (be instanceof PumpBlockEntity pump && be.getBlockState().getBlock() instanceof MaterialPumpBlock)
            return pump.getSpeed() != 0 ? Kind.MATERIAL_PUMP : null;
        if (be instanceof ReliefScrubberBlockEntity scrubber) return scrubber.status().equals("scrubbing") ? Kind.RELIEF_SCRUBBER : null;
        if (be instanceof GasHolderBlockEntity holder)
            return holder.formed() && Math.abs(holder.renderedLift(1) - holder.renderedLift(0)) > .001 ? Kind.GAS_HOLDER : null;
        if (be instanceof FacilityConstructorBlockEntity constructor)
            return constructor.state == SchematicannonBlockEntity.State.RUNNING ? Kind.CONSTRUCTOR : null;
        var flow = gasFlow(be);
        if (flow == null || flow.flowBand() == 0) return null;
        return flow.pressureBand() >= net.createplant.thermal.Grade.Pressure.HIGH.ordinal() ? Kind.HIGH_PRESSURE_GAS : Kind.PRESSURISED_GAS;
    }
}
