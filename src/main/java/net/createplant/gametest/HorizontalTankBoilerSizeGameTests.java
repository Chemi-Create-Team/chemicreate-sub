package net.createplant.gametest;

import java.util.HashSet;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.registry.CEFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.SteamGrades;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HorizontalTankBoilerSizeGameTests {
    private static final BlockPos BASE = new BlockPos(6, 3, 6);
    private static final int LENGTH = 5, FEED = 7000;
    private HorizontalTankBoilerSizeGameTests() { }

    private static BlockPos cell(Direction.Axis axis, int along, int up, int across) {
        return axis == Direction.Axis.X ? BASE.offset(along, up, across) : BASE.offset(across, up, along);
    }

    private static HorizontalTankBlockEntity part(GameTestHelper h, BlockPos pos) {
        return (HorizontalTankBlockEntity) h.getBlockEntity(pos);
    }

    private static IFluidHandler port(GameTestHelper h, BlockPos pos, Direction side) {
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
        h.assertTrue(port != null, "A formed boiler exposes its real fluid capability on " + side);return port;
    }

    private static void firedBurner(GameTestHelper h, BlockPos under) {
        h.setBlock(under, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var cake = AllItems.BLAZE_CAKE.asStack();
        var result = BlazeBurnerBlock.tryInsert(h.getBlockState(under), h.getLevel(), h.absolutePos(under), cake, false, false, false);
        h.assertTrue(result.getResult().consumesAction() && cake.isEmpty(), "One finite Blaze Cake fuels this real burner");
    }

    private static void near(GameTestHelper h, double actual, double expected, String description) {
        h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) <= Math.max(.01, Math.abs(expected) * 1e-8),
            description + ": " + actual + " / " + expected);
    }

    private static int storedMass(GameTestHelper h, Direction.Axis axis, int width) {
        int stored = 0;var owners = new HashSet<BlockPos>();
        for (int along = 0; along < LENGTH; along++) for (int up = 0; up < width; up++) for (int across = 0; across < width; across++) {
            var be = h.getLevel().getBlockEntity(h.absolutePos(cell(axis, along, up, across)));
            if (!(be instanceof HorizontalTankBlockEntity section)) continue;
            stored += section.steam().getFluidAmount();
            var owner = section.getControllerBE();
            if (owner != null && owners.add(owner.getBlockPos())) stored += owner.getTankInventory().getFluidAmount();
        }
        return stored;
    }

    private static void verify(GameTestHelper h, Direction.Axis axis, int width) {
        var state = CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, axis);
        for (int along = 0; along < LENGTH; along++) for (int up = 0; up < width; up++) for (int across = 0; across < width; across++)
            h.setBlock(cell(axis, along, up, across), state);
        var root = (HorizontalTankBlockEntity) part(h, BASE).getControllerBE();
        int volume = width * width * LENGTH;
        h.assertTrue(root != null && root.getWidth() == width && root.getHeight() == LENGTH && root.getTotalTankSize() == volume,
            "Real Create connectivity forms a " + width + " by " + width + " section of length " + LENGTH + " along " + axis);
        h.assertTrue(root.getTankInventory().getCapacity() == volume * FluidTankBlockEntity.getCapacityMultiplier(), "All cells contribute once to the shared feed capacity");
        for (int along = 0; along < LENGTH; along++) for (int up = 0; up < width; up++) for (int across = 0; across < width; across++)
            h.assertTrue(part(h, cell(axis, along, up, across)).getControllerBE() == root, "Every cell belongs to the single formed controller");

        Direction endFace = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction feedFace = axis == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
        BlockPos end = cell(axis, LENGTH - 1, 0, 0);
        var inlet = port(h, end, feedFace);var output = port(h, end, endFace);var hot = part(h, end);
        var roof = port(h, cell(axis, 1, width - 1, 0), Direction.UP);
        var oppositeEnd = port(h, cell(axis, 0, width - 1, 0), endFace.getOpposite());
        var feed = FluidThermal.at(new FluidStack(Fluids.WATER, FEED), 293.15);
        h.assertTrue(inlet.fill(feed, SIMULATE) == FEED && root.getTankInventory().isEmpty(), "The lower end side previews a shared feed fill without consuming water");
        h.assertTrue(inlet.fill(feed, EXECUTE) == FEED && root.getTankInventory().getFluidAmount() == FEED,
            "Water inserted through the remote lower end reaches the real shared controller");
        h.assertTrue(roof.drain(1, SIMULATE).is(Fluids.WATER) && oppositeEnd.drain(1, SIMULATE).is(Fluids.WATER),
            "The same roof and end connections work as standard storage while cold");
        h.assertTrue(inlet.fill(new FluidStack(Fluids.LAVA, 1000), EXECUTE) == 0, "The water already inside keeps lava out");
        var level = h.getLevel();var absolute = h.absolutePos(end);
        h.assertTrue(FluidPipeBlock.canConnectTo(level, absolute, level.getBlockState(absolute), endFace.getOpposite())
            && FluidPipeBlock.canConnectTo(level, absolute, level.getBlockState(absolute), feedFace.getOpposite()),
            "Stock Create pipes recognize both accessible end connections");
        for (int along = 0; along < LENGTH; along++) for (int across = 0; across < width; across++) firedBurner(h, cell(axis, along, 0, across).below());
        h.assertTrue(roof.getFluidInTank(0).isEmpty() && roof.drain(1, SIMULATE).isEmpty()
            && oppositeEnd.drain(feed.copyWithAmount(1), SIMULATE).isEmpty()
            && output.drain(1, SIMULATE).isEmpty() && inlet.drain(1, SIMULATE).isEmpty(),
            "After ignition, cached steam outlets and the feed face cannot export water before the first steam tick");
        part(h, cell(axis, LENGTH - 1, 1, 0)).generateSteam();
        h.assertTrue(root.getTankInventory().getFluidAmount() == FEED && storedMass(h, axis, width) == FEED,
            "An upper wall cell does not duplicate the heated lower row's production");

        double work = 0, steamEnergy = 0, credit = 0;
        int steamMass = 0;
        for (int across = 0; across < width; across++) {
            var terminal = part(h, cell(axis, LENGTH - 1, 0, across));terminal.generateSteam();
            h.assertTrue(terminal.steam().getFluidAmount() > 0, "Every independently heated lower-end tube row produces finite steam");
            h.assertTrue(terminal.steam().getFluid().is(CEProcessFluids.SUPERHEATED_STEAM.get()), "Superheated burner creates the superheated grade");
            steamMass += terminal.steam().getFluidAmount();steamEnergy += terminal.steam().getFluidAmount()*SteamGrades.SUPERHEATED.joulesPerMb();work += terminal.heatWatts()/20;
            credit += terminal.saveWithoutMetadata(level.registryAccess()).getDouble("TubeHeat");
        }
        h.assertTrue(root.getTankInventory().getFluidAmount() + steamMass == FEED && storedMass(h, axis, width) == FEED,
            "All parallel tube rows share finite water without duplicating or losing mass");
        h.assertTrue(work > 0 && work <= width * LENGTH * 8_000, "The first production step stays within the finite heat supplied by its individual burners");
        near(h, steamEnergy+credit, work, "Fixed steam cost plus saved rounding credit equals burner heat");

        h.assertTrue(output.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 0, "A boiling end outlet gives steam and takes no water");
        int held = hot.steam().getFluidAmount();var preview = output.drain(held, SIMULATE);
        h.assertTrue(preview.is(CEProcessFluids.SUPERHEATED_STEAM.get()) && preview.getAmount() == held && hot.steam().getFluidAmount() == held,
            "An end outlet previews the lower row's retained steam without draining it");
        h.assertTrue(roof.getFluidInTank(0).getAmount() == held && roof.drain(held, SIMULATE).is(preview.getFluid())
            && oppositeEnd.drain(preview, SIMULATE).getAmount() == held,
            "A remote roof inlet and the opposite end look up the same lower row's buffer");
        int exported = output.drain(preview, EXECUTE).getAmount();
        h.assertTrue(exported == held && output.drain(held, EXECUTE).isEmpty() && roof.drain(1, EXECUTE).isEmpty()
            && oppositeEnd.drain(1, EXECUTE).isEmpty() && storedMass(h, axis, width) + exported == FEED,
            "Draining one outlet empties the same row at all three ports and never falls through to water");
        var drain = port(h, end, Direction.DOWN);int remaining = root.getTankInventory().getFluidAmount();
        var drainPreview = drain.drain(1, SIMULATE);
        h.assertTrue(drainPreview.is(Fluids.WATER) && drainPreview.getAmount() == 1 && root.getTankInventory().getFluidAmount() == remaining,
            "The same lower end exposes the shared water through its bottom drain");
        var returned = drain.drain(1, EXECUTE);h.assertTrue(inlet.fill(returned, EXECUTE) == 1, "A finite bottom drain can be returned through the feed side");

        for (int along = 0; along < LENGTH; along++) for (int up = 0; up < width; up++) for (int across = 0; across < width; across++) {
            var section = part(h, cell(axis, along, up, across));var saved = section.saveWithoutMetadata(level.registryAccess());
            section.loadWithComponents(saved, level.registryAccess());
        }
        h.assertTrue(root.getWidth() == width && root.getHeight() == LENGTH && storedMass(h, axis, width) + exported == FEED,
            "Reload retains all cell owners, dimensions, shared water and separate tube steam");
        double savedEnergy = 0;
        for (int across = 0; across < width; across++) savedEnergy += part(h, cell(axis, LENGTH - 1, 0, across)).steam().getFluidAmount()*SteamGrades.SUPERHEATED.joulesPerMb();
        near(h, savedEnergy, steamEnergy - preview.getAmount()*SteamGrades.SUPERHEATED.joulesPerMb(), "Reload also retains the exact unexported steam energy");

        var fullCharge = new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(),Integer.MAX_VALUE);
        int extra = hot.steam().fill(fullCharge, EXECUTE);remaining = root.getTankInventory().getFluidAmount();hot.generateSteam();
        h.assertTrue(hot.status().equals("output") && root.getTankInventory().getFluidAmount() == remaining && hot.heatWatts() == 0,
            "A full lower-end steam row retains the entire shared feed and consumes no more burner work");
        exported += output.drain(Integer.MAX_VALUE, EXECUTE).getAmount();
        h.setBlock(end.below(), Blocks.AIR);hot.generateSteam();
        h.assertTrue(hot.status().equals("heat") && root.getTankInventory().getFluidAmount() == remaining && hot.steam().isEmpty(),
            "Removing the end superheater burner stops this row before consuming any feed");
        firedBurner(h, end.below());hot.generateSteam();
        h.assertTrue(hot.steam().getFluidAmount() > 0 && storedMass(h, axis, width) + exported == FEED + extra,
            "Refuelling resumes the same formed row without duplicating the finite feed or retained charge");

        for (int up = 0; up < width; up++) for (int across = 0; across < width; across++) h.setBlock(cell(axis, 2, up, across), Blocks.AIR);
        int splitMass = storedMass(h, axis, width);
        for (int across = 0; across < width; across++) {
            var terminal = part(h, cell(axis, LENGTH - 1, 0, across));int retained = terminal.steam().getFluidAmount();terminal.generateSteam();
            h.assertTrue(terminal.steam().getFluidAmount() >= retained,
                "Removing a complete middle section splits the tank; each remainder keeps its steam");
        }
        h.assertTrue(storedMass(h, axis, width) == splitMass && splitMass + exported == FEED + extra,
            "Splitting and stopping preserve the surviving water and steam inventories");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void twoByTwoBoilerAlongXKeepsItsSharedWaterAndSeparateSteam(GameTestHelper h) { verify(h, Direction.Axis.X, 2); }

    @GameTest(template = "test_fractionation")
    public static void twoByTwoBoilerAlongZKeepsItsSharedWaterAndSeparateSteam(GameTestHelper h) { verify(h, Direction.Axis.Z, 2); }

    @GameTest(template = "test_fractionation")
    public static void threeByThreeBoilerAlongXKeepsItsSharedWaterAndSeparateSteam(GameTestHelper h) { verify(h, Direction.Axis.X, 3); }

    @GameTest(template = "test_fractionation")
    public static void threeByThreeBoilerAlongZKeepsItsSharedWaterAndSeparateSteam(GameTestHelper h) { verify(h, Direction.Axis.Z, 3); }
}
