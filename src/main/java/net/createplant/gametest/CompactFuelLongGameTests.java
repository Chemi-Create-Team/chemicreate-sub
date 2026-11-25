package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.nuclear.ReactorAutomaticControl;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorFuel;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CENuclear;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Actual world ticks, finite fuel and real feed bundles; kept out of the ordinary GameTest server. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
@ExplicitGameTest
public class CompactFuelLongGameTests {
    static final BlockPos BASE = new BlockPos(3, 10, 3);
    private static final int SCRAM_TICK = 59_400, END_TICK = 60_000;

    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void circularFiveHoldsWithEnrichedFuel(GameTestHelper h) { start(h, 5, 3, true, ReactorFuel.COMPACT_ENRICHED, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void squareFiveHoldsWithEnrichedFuel(GameTestHelper h) { start(h, 5, 3, false, ReactorFuel.COMPACT_ENRICHED, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void circularSevenHoldsWithEnrichedFuel(GameTestHelper h) { start(h, 7, 3, true, ReactorFuel.COMPACT_ENRICHED, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void compactThirteenRetainsOrdinaryFuelRating(GameTestHelper h) { start(h, 13, 3, true, ReactorFuel.URANIUM, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void standardFiveRetainsOrdinaryFuelRating(GameTestHelper h) { start(h, 5, 7, true, ReactorFuel.URANIUM, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 61_000)
    public static void standardThirteenRetains765MW(GameTestHelper h) { start(h, 13, 7, true, ReactorFuel.URANIUM, false); }
    @GameTest(template = "test_rbmk", timeoutTicks = 11_000)
    public static void measureCircularFiveOrdinaryAutomaticStartup(GameTestHelper h) { start(h, 5, 3, true, ReactorFuel.URANIUM, true); }
    @GameTest(template = "test_rbmk", timeoutTicks = 11_000)
    public static void measureCircularFiveEnrichedAutomaticStartup(GameTestHelper h) { start(h, 5, 3, true, ReactorFuel.COMPACT_ENRICHED, true); }
    @GameTest(template = "test_rbmk", timeoutTicks = 11_000)
    public static void measureSquareFiveOrdinaryAutomaticStartup(GameTestHelper h) { start(h, 5, 3, false, ReactorFuel.URANIUM, true); }
    @GameTest(template = "test_rbmk", timeoutTicks = 11_000)
    public static void measureSquareFiveEnrichedAutomaticStartup(GameTestHelper h) { start(h, 5, 3, false, ReactorFuel.COMPACT_ENRICHED, true); }

    static Fixture fixture(GameTestHelper h, int size, int height, boolean circular, ReactorFuel fuel) {
        var layout = ReactorPhysics.layout(height);
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            if (circular && !ReactorCoreStructure.inCircle(x, z, size)) continue;
            BlockPos bottom = BASE.offset(x, 0, z);
            for (int y = 0; y < height; y++) h.setBlock(bottom.above(y), CENuclear.FUEL_CHANNEL.get());
            for (int y = 1; y <= layout.lowerStructureHeight(); y++) h.setBlock(bottom.below(y), CENuclear.FEED_BUNDLE.get());
            for (int y = height; y < layout.fuelAssemblyLength(); y++) h.setBlock(bottom.above(y), CENuclear.FUEL_CHANNEL_TUBE.get());
            h.setBlock(bottom.above(layout.fuelAssemblyLength()), CENuclear.TOP_CAP.get());
        }
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(BASE.offset(size / 2, 0, size / 2)));
        h.assertTrue(result.formed(), "The real operating fixture forms: " + result.reason());
        var lead = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller());lead.adopt(result, null);
        for (BlockPos pos : result.fuel()) {
            var channel = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(pos);
            for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) {
                if (!channel.active(slot)) continue;
                if (channel.control(slot)) {
                    h.assertTrue(channel.installControlRod(slot), "Install the real control rod");channel.controlDepth(slot, height);
                } else if (fuel != null)
                    h.assertTrue(channel.topItems().insertItem(slot, new ItemStack(fuel.item()), false).isEmpty(), "Install every real fuel rod");
            }
        }
        BlockPos drive = BASE.offset(-2, 1, size / 2), motor = drive.west();
        h.setBlock(drive, CENuclear.SHIELD_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        lead.adopt(ReactorCoreStructure.scan(h.getLevel(), result.controller()), null);
        var core = lead.core();
        BlockPos feed = result.fuel().getFirst().below(layout.lowerStructureHeight());
        var inlet = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, feed, Direction.DOWN);
        h.assertTrue(inlet != null, "A real bottom feed-bundle capability supplies the operating core");
        return new Fixture(lead, core, inlet, motor, result);
    }

    record Fixture(FuelChannelBlockEntity lead, ReactorCore core, IFluidHandler inlet, BlockPos motor, ReactorCoreStructure.Result structure) { }

    static void start(GameTestHelper h, int size, int height, boolean circular, ReactorFuel fuel, boolean automatic) {
        Fixture fixture = fixture(h, size, height, circular, fuel);
        new Run(h, fixture, size, height, circular, fuel, automatic).start();
    }

    private static final class Run {
        private final GameTestHelper h;
        private final Fixture fixture;
        private final ReactorCore core;
        private final ReactorFuel fuel;
        private final boolean automatic;
        private final String name;
        private final int flow;
        private final FluidStack feed = FluidForm.PRESSURISED_WATER.stack(Integer.MAX_VALUE);
        private final List<Map<String, Object>> samples = new ArrayList<>();
        private final Map<String, Object> report = new LinkedHashMap<>();
        private int elapsed;
        private double target = 100;
        private double maximumPower, maximumFuel, minimumHold = Double.POSITIVE_INFINITY, maximumHold, minimumInsertion = 1, minimumXenon = Double.POSITIVE_INFINITY;
        private double preScramPower, scramReactivity;
        private double holdPowerSum;
        private int holdTicks;
        private boolean done;

        Run(GameTestHelper h, Fixture fixture, int size, int height, boolean circular, ReactorFuel fuel, boolean automatic) {
            this.h = h;this.fixture = fixture;this.core = fixture.core();this.fuel = fuel;this.automatic = automatic;
            name = (circular ? "circle" : "square") + "-" + size + "-h" + height + "-" + fuel.name().toLowerCase(java.util.Locale.ROOT) + (automatic ? "-automatic-startup" : "-hold");
            flow = (int) Math.round(165_888d * core.fuelChannels() / 397 * height / 7);
            report.put("case", name);report.put("height", height);report.put("size", size);report.put("fuelRods", core.fuelChannels());
            report.put("controlRods", core.controlChannels());report.put("nominalMW", core.nominalWatts() / 1e6);report.put("circulationMbPerTick", flow);
            report.put("feedKelvin", 543.15);report.put("fuelWorth", fuel.worth());report.put("samples", samples);
            report.put("worldSeed", h.getLevel().getSeed());report.put("startWorldTick", h.getLevel().getGameTime());
            report.put("controller", fixture.lead().getBlockPos().toShortString());
        }

        void start() {
            core.setAutomaticTarget(fixture.lead().getBlockPos(), target);
            h.assertTrue(fixture.inlet().fill(feed,EXECUTE)>0,"Native feed bundle accepts graded operating feedwater");
            h.runAfterDelay(5, () -> {
                ((CreativeMotorBlockEntity) h.getBlockEntity(fixture.motor())).generatedSpeed.setValue(128);
                if (Boolean.getBoolean("createplant.compactFuelSprint")) h.getLevel().getServer().tickRateManager().requestGameToSprint(61_000);
            });
            h.onEachTick(this::tick);
        }

        void tick() {
            if (done) return;
            elapsed++;
            maximumPower = Math.max(maximumPower, core.power());maximumFuel = Math.max(maximumFuel, core.hottestKelvin());
            if (!core.formed()) {
                report.put("structureLostAtTick", elapsed);
                finish();
                h.assertTrue(false, "A controlled reactor must retain its intact structure");
                h.succeed();return;
            }
            fixture.inlet().fill(feed, EXECUTE);
            core.outlet().drain(flow,EXECUTE);
            if (elapsed % 10 == 0) {
                if (automatic) target = ReactorAutomaticControl.nextTarget(target, core.hottestKelvin(), core.averageCoolantKelvin(), flow, core.power(), core.nominalWatts(), core.reactivity(), core.scram());
                else if (elapsed < SCRAM_TICK) {
                    if (core.power() < core.nominalWatts() * .98 && core.reactivity() < .001) target = Math.max(0, target - 1);
                    else if (core.reactivity() > .002 || core.power() > core.nominalWatts() * 1.02) target = Math.min(100, target + 1);
                }
                core.setAutomaticTarget(fixture.lead().getBlockPos(), target);
            }
            if (!automatic && elapsed >= 50_000 && elapsed < SCRAM_TICK) {
                holdPowerSum += core.power();holdTicks++;
                minimumHold = Math.min(minimumHold, core.power());maximumHold = Math.max(maximumHold, core.power());
                minimumInsertion = Math.min(minimumInsertion, core.rodInsertion());minimumXenon = Math.min(minimumXenon, core.xenon());
            }
            if (elapsed % 1000 == 0 || elapsed == SCRAM_TICK || elapsed == SCRAM_TICK + 120 || elapsed == END_TICK) sample();
            if (!automatic && elapsed == SCRAM_TICK) { preScramPower = core.power();core.setConsoleScram(true); }
            if (!automatic && elapsed == SCRAM_TICK + 120) scramReactivity = core.reactivity();
            if (elapsed == (automatic ? 10_000 : END_TICK)) {
                finish();
                if (automatic) {
                    h.assertTrue(core.power()>=core.nominalWatts()*.7,"AUTO must reach and retain useful output at the end of cold start");
                    h.assertTrue(maximumPower<=core.nominalWatts()*1.5,"AUTO cold start stays below 150% nominal: "+maximumPower/core.nominalWatts());
                    h.assertTrue(maximumFuel<ReactorPhysics.ALARM_KELVIN && !core.scram(),"AUTO cold start remains below the fuel alarm without SCRAM");
                }
                if (!automatic) {
                    h.assertTrue(minimumXenon >= .95, "The hold interval reaches xenon equilibrium: " + minimumXenon);
                    double mean = holdPowerSum / holdTicks;
                    h.assertTrue(mean >= core.nominalWatts() * .9 && mean <= core.nominalWatts() * 1.1 && minimumHold >= core.nominalWatts() * .7,
                        "The discrete 1% controller sustains rated mean output without xenon shutdown: mean=" + mean / 1e6 + " MW, range=" + minimumHold / 1e6 + ".." + maximumHold / 1e6);
                    if (fuel == ReactorFuel.COMPACT_ENRICHED) h.assertTrue(minimumInsertion >= .15, "At least 15% absorber insertion remains at equilibrium: " + minimumInsertion);
                    h.assertTrue(maximumFuel < 2400, "No fuel reaches channel rupture: " + maximumFuel + " K");
                    double shutdownLimit = fuel == ReactorFuel.COMPACT_ENRICHED ? -.05 : -.04;
                    h.assertTrue(core.scram() && core.rodInsertion() >= .999 && core.reactivity() <= shutdownLimit
                        && core.power() < preScramPower * .05, "AZ-5 fully inserts the rods, makes reactivity negative and lowers power");
                    if (core.height() == 7 && core.fuelChannels() == 397) h.assertTrue(core.nominalWatts() == 765e6, "Maximum ordinary-fuel calibration remains exactly 765 MW");
                }
                h.succeed();
            }
        }

        private void sample() {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("tick", elapsed);sample.put("powerMW", core.power() / 1e6);sample.put("reactivity", core.reactivity());
            sample.put("xenon", core.xenon());sample.put("insertion", core.rodInsertion());sample.put("hottestFuelK", core.hottestKelvin());
            samples.add(sample);
            if (elapsed % 10_000 == 0) LoggerFactory.getLogger(CompactFuelLongGameTests.class).info("COMPACT FUEL {} tick={} MW={} xenon={} insertion={}", name, elapsed, core.power() / 1e6, core.xenon(), core.rodInsertion());
        }

        private void finish() {
            done = true;
            report.put("ticks", elapsed);report.put("maximumMW", maximumPower / 1e6);report.put("maximumFuelK", maximumFuel);
            report.put("equilibriumMinimumMW", Double.isFinite(minimumHold) ? minimumHold / 1e6 : null);report.put("equilibriumMaximumMW", maximumHold / 1e6);
            report.put("equilibriumMeanMW", holdTicks > 0 ? holdPowerSum / holdTicks / 1e6 : null);
            report.put("equilibriumMinimumInsertion", minimumInsertion);report.put("equilibriumMinimumXenon", Double.isFinite(minimumXenon) ? minimumXenon : null);
            report.put("preScramMW", preScramPower / 1e6);report.put("scram120Reactivity", scramReactivity);
            report.put("finalMW", core.power() / 1e6);report.put("finalReactivity", core.reactivity());report.put("finalInsertion", core.rodInsertion());
            Path path = Path.of(System.getProperty("createplant.compactFuelReportDir", "compact-fuel-results"), name + ".json");
            try { Files.createDirectories(path.getParent());Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (IOException exception) { throw new IllegalStateException("Cannot save compact fuel measurements", exception); }
            LoggerFactory.getLogger(CompactFuelLongGameTests.class).info("COMPACT FUEL RESULT {}", report);
        }
    }
}
