package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.FusionConsolePanelBlock;
import net.createplant.block.FusionControlConsoleBlock;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.FusionMotorGeneratorBlock;
import net.createplant.block.TurbineFacility;
import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.createplant.block.entity.FusionMotorGeneratorBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.FusionVacuumPumpBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CEDisplays;
import net.createplant.registry.CEBlocks;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.PhaseChange;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TurbineThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class FusionGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos CONTROLLER = new BlockPos(20, 10, 40);

    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void ratedPhysicsUsesExplicitMassAndEnergyBalances(GameTestHelper helper) {
        double fuel = FusionPhysics.fuelMbPerTick(FusionPhysics.targetWatts(80));
        double coolant = FusionPhysics.coolantMbPerTick(FusionPhysics.targetWatts(80) + 40_000_000,
            FusionPhysics.COOLANT_INLET_KELVIN, FusionPhysics.COOLANT_OUTLET_KELVIN);
        helper.assertTrue(fuel > .1 && fuel < .2, "400 MW consumes a finite ideal-gas D2/T2 inventory: " + fuel + " mB/t each");
        helper.assertTrue(coolant < FusionPhysics.RATED_COOLANT_MB_PER_TICK,
            "The 200-250 C coolant rating removes the default 400 MW setpoint and 40 MW house load: " + coolant + " mB/t");
        helper.assertTrue(PhaseChange.ofLiquid(FluidForm.LIQUID_HELIUM.stack(1)).ratio()
            == FusionReactorControllerBlockEntity.HELIUM_VOLUME_RATIO, "One liquid-helium mB expands to 753 gas mB");
        helper.assertTrue(FusionStructure.count(FusionStructure.Role.CASING) > 20
            && FusionStructure.count(FusionStructure.Role.COIL) == 48
            && FusionStructure.count(FusionStructure.Role.BLANKET) > 20,
            "The compact torus retains annular footings, four twelve-block coils and a blanket");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void plasmaChannelHasTwoByTwoCrossSectionsWithoutNarrowCornersAndIsSealed(GameTestHelper helper) {
        var path = FusionStructure.plasmaPath();
        var innerLane = new java.util.HashSet<>(path);
        var channel = new java.util.HashSet<>(FusionStructure.plasmaChannel());
        var upperLayer = new java.util.HashSet<BlockPos>();
        var lowerLayer = new java.util.HashSet<BlockPos>();
        for (BlockPos at : channel) {
            helper.assertTrue(at.getY() == -1 || at.getY() == 0, "The channel occupies exactly two consecutive height layers");
            if (at.getY() == 0) upperLayer.add(at);
            else lowerLayer.add(at.above());
        }
        helper.assertTrue(channel.size() == 112 && upperLayer.size() == 56 && upperLayer.equals(lowerLayer),
            "Both height layers have the same complete 56-cell toroidal cross section");
        helper.assertTrue(path.size() == 24 && innerLane.size() == path.size(), "The inner horizontal lane is one closed 24-cell loop");
        for (int i = 0; i < path.size(); i++) {
            BlockPos at = path.get(i), next = path.get((i + 1) % path.size());
            helper.assertTrue(at.getY() == 0 && at.distManhattan(next) == 1,
                "The ordered inner lane closes through horizontal face neighbours only");
        }
        var outerLane = new java.util.HashSet<>(upperLayer);
        outerLane.removeAll(innerLane);
        helper.assertTrue(outerLane.size() == 32, "A separate 32-cell outer lane provides the second route around every bend");
        for (var lane : java.util.List.of(innerLane, outerLane)) {
            var remaining = new java.util.HashSet<>(lane);
            var pending = new java.util.ArrayDeque<BlockPos>();
            BlockPos first = remaining.iterator().next();
            remaining.remove(first); pending.add(first);
            while (!pending.isEmpty()) {
                BlockPos at = pending.removeFirst();
                int neighbours = 0;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos next = at.relative(direction);
                    if (lane.contains(next)) neighbours++;
                    if (remaining.remove(next)) pending.addLast(next);
                }
                helper.assertTrue(neighbours == 2, "Each of the two disjoint lanes remains a face-connected cycle through its corners");
            }
            helper.assertTrue(remaining.isEmpty(), "Each lane remains a single loop without a detached pocket");
        }
        for (BlockPos at : upperLayer) {
            boolean fullTile = false;
            for (int dx = -1; dx <= 0; dx++) for (int dz = -1; dz <= 0; dz++) {
                BlockPos origin = at.offset(dx, 0, dz);
                if (upperLayer.contains(origin) && upperLayer.contains(origin.east())
                    && upperLayer.contains(origin.south()) && upperLayer.contains(origin.east().south())) fullTile = true;
            }
            helper.assertTrue(fullTile, "Every bend cell belongs to a full two-by-two footprint, so no one-cell pinch remains");
            boolean threeByThree = true;
            for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++)
                if (!upperLayer.contains(at.offset(dx, 0, dz))) threeByThree = false;
            helper.assertTrue(!threeByThree, "No corner expands into a three-by-three chamber");
        }
        for (Direction radial : Direction.Plane.HORIZONTAL) {
            var straightSection = upperLayer.stream().filter(at -> radial.getAxis() == Direction.Axis.X
                ? at.getZ() == 0 && Integer.signum(at.getX()) == radial.getStepX()
                : at.getX() == 0 && Integer.signum(at.getZ()) == radial.getStepZ()).toList();
            helper.assertTrue(straightSection.size() == 2, "Each straight radial cross section is exactly two cells wide");
        }
        var centreline = FusionStructure.plasmaCentreline();
        helper.assertTrue(centreline.size() == 28, "The visible centreline lies halfway between the two channel lanes");
        for (int i = 0; i < centreline.size(); i++) {
            var at = centreline.get(i);
            helper.assertTrue(at.y == -.5 && Math.abs(at.distanceTo(centreline.get((i + 1) % centreline.size())) - 1) < 1e-9,
                "The renderer follows closed unit segments halfway between both height layers");
        }
        for (Direction inward : Direction.Plane.HORIZONTAL) {
            BlockPos controller = BlockPos.ZERO.relative(inward.getOpposite(), FusionPhysics.HALF_WIDTH);
            var layout = new java.util.HashMap<BlockPos, FusionStructure.Cell>();
            for (var cell : FusionStructure.cells(controller, inward)) layout.put(cell.pos(), cell);
            for (BlockPos at : channel) {
                helper.assertTrue(layout.get(at).role() == FusionStructure.Role.CLEAR, "All 112 channel cells remain clear");
                for (Direction face : Direction.values()) {
                    BlockPos neighbour = at.relative(face);
                    if (channel.contains(neighbour)) continue;
                    var wall = layout.get(neighbour);
                    helper.assertTrue(wall != null && wall.role() != FusionStructure.Role.CLEAR,
                        "A pressure wall closes every face outside the two-by-two channel, facing " + inward);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void openStructureKeepsItsPortsAndRejectsBlockedViewsOrVesselDamageInEveryOrientation(GameTestHelper helper) {
        BlockPos centre = helper.absolutePos(new BlockPos(20, 10, 20));
        helper.assertTrue(FusionPhysics.WIDTH == 11 && FusionPhysics.HEIGHT == 8 && FusionPhysics.MIDPLANE == 4,
            "A three-block central hole, two two-block channels and four one-block walls fit the eleven-block diameter");
        for (Direction inward : Direction.Plane.HORIZONTAL) {
            BlockPos control = centre.relative(inward.getOpposite(), FusionPhysics.HALF_WIDTH);
            var cells = FusionStructure.cells(control, inward);
            for (var cell : cells) helper.getLevel().setBlock(cell.pos(), cell.expected(), 3);
            var reactor = (FusionReactorControllerBlockEntity) helper.getLevel().getBlockEntity(control);
            reactor.tick();
            helper.assertTrue(reactor.formed(), "The toroidal reactor forms facing " + inward + ": " + reactor.status());
            int ports = 0;
            for (var cell : cells) {
                helper.assertTrue(Math.abs(cell.pos().getX() - centre.getX()) <= FusionPhysics.HALF_WIDTH
                    && Math.abs(cell.pos().getZ() - centre.getZ()) <= FusionPhysics.HALF_WIDTH
                    && cell.pos().getY() - centre.getY() >= -FusionPhysics.MIDPLANE
                    && cell.pos().getY() - centre.getY() <= FusionPhysics.ROOF_OFFSET,
                    "Every required cell and port stays inside the eleven-by-eleven toroidal footprint");
                if (!(cell.expected().getBlock() instanceof FusionFluidPortBlock)) continue;
                Direction outward = cell.expected().getValue(FusionFluidPortBlock.FACING);
                helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, cell.pos(), outward) != null,
                    "Each compact port exposes a standard outward fluid handler: " + cell.role());
                helper.assertTrue(helper.getLevel().isEmptyBlock(cell.pos().relative(outward)), "An external pipe can reach " + cell.role());
                ports++;
            }
            helper.assertTrue(ports == 7, "All seven independent fluid connections remain usable");
            var unsupported = new java.util.HashSet<BlockPos>();
            for (var cell : cells) if (cell.role() != FusionStructure.Role.CLEAR) unsupported.add(cell.pos());
            var connected = new java.util.ArrayDeque<BlockPos>();
            BlockPos footing = centre.below(FusionPhysics.MIDPLANE);
            unsupported.remove(footing);connected.add(footing);
            while (!connected.isEmpty()) {
                BlockPos at = connected.removeFirst();
                for (Direction connection : Direction.values()) {
                    BlockPos next = at.relative(connection);
                    if (unsupported.remove(next)) connected.addLast(next);
                }
            }
            helper.assertTrue(unsupported.isEmpty(), "Every exposed core and coil block is connected to its footing");
            helper.assertTrue(FusionStructure.serviceTops(control, inward).stream().allMatch(top -> helper.getLevel().isEmptyBlock(top.above())),
                "Nothing stands above the top blanket, so the crane can reach every block of it in every orientation");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                for (int y = 1; y < FusionPhysics.HEIGHT; y++)
                    helper.assertTrue(helper.getLevel().isEmptyBlock(centre.offset(x, y - FusionPhysics.MIDPLANE, z)),
                        "The full three-by-three central hole is clear above the foundation, including below the plasma midplane");
            BlockPos sideOpening = centre.offset(FusionPhysics.HALF_WIDTH, 1, FusionPhysics.HALF_WIDTH);
            BlockPos topOpening = centre.above(FusionPhysics.ROOF_OFFSET);
            helper.assertTrue(helper.getLevel().isEmptyBlock(sideOpening) && helper.getLevel().isEmptyBlock(topOpening),
                "The torus is visible through the removed cylindrical sidewall and roof");
            helper.getLevel().setBlock(sideOpening, CEFusion.REINFORCED_CASING.get().defaultBlockState(), 3);
            var covered = FusionStructure.scan(helper.getLevel(), control, inward);
            helper.assertTrue(!covered.formed() && covered.reason().equals("clearance"), "The old cylindrical casing cannot cover the exposed torus");
            helper.getLevel().setBlock(sideOpening, Blocks.AIR.defaultBlockState(), 3);
            var vessel = cells.stream().filter(cell -> cell.role() == FusionStructure.Role.VESSEL).findFirst().orElseThrow();
            helper.getLevel().setBlock(vessel.pos(), Blocks.AIR.defaultBlockState(), 3);
            var damaged = FusionStructure.scan(helper.getLevel(), control, inward);
            helper.assertTrue(!damaged.formed() && damaged.reason().equals("vessel"), "Removing the actual toroidal vacuum vessel still prevents operation");
            helper.getLevel().setBlock(vessel.pos(), CEFusion.VIEWPORT.get().defaultBlockState(), 3);
            helper.assertTrue(FusionStructure.scan(helper.getLevel(), control, inward).formed(), "A viewport may stand in for a vacuum vessel wall");
            helper.getLevel().setBlock(vessel.pos(), vessel.expected(), 3);
            BlockPos chamber = centre.offset(FusionStructure.plasmaPath().getFirst());
            helper.getLevel().setBlock(chamber, Blocks.OBSIDIAN.defaultBlockState(), 3);
            var blocked = FusionStructure.scan(helper.getLevel(), control, inward);
            helper.assertTrue(!blocked.formed() && blocked.reason().equals("clearance"), "The compact plasma chamber cannot be filled with an arbitrary solid");
            helper.getLevel().setBlock(chamber, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 200)
    public static void eightyPercentRunStaysCooledAndFailsSafe(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        for (FusionStructure.Cell cell : FusionStructure.cells(controllerPos, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) helper.getLevel().setBlock(cell.pos(), cell.expected(), 3);
        var controller = (FusionReactorControllerBlockEntity) helper.getLevel().getBlockEntity(controllerPos);
        helper.assertTrue(controller != null, "The feeder owns the complete tokamak");
        controller.tick();
        helper.assertTrue(controller.formed(), "The broad toroidal structure forms: " + controller.status());
        helper.assertTrue(Math.abs(controller.blanketKelvin() - FusionPhysics.AMBIENT_KELVIN) < 1e-6,
            "A new blanket begins at ambient temperature without stored heat");

        BlockPos pumpPos = controller.centre().relative(Direction.WEST, FusionPhysics.HALF_WIDTH);
        var pump = (FusionVacuumPumpBlockEntity) helper.getLevel().getBlockEntity(pumpPos);
        helper.assertTrue(pump != null, "The west penetration is the mechanical vacuum pump");
        pump.setSpeed(64);
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.DEUTERIUM,
            new FluidStack(CEFusion.DEUTERIUM_GAS.get(), FusionReactorControllerBlockEntity.FUEL_CAPACITY));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.TRITIUM,
            new FluidStack(CEFusion.TRITIUM_GAS.get(), FusionReactorControllerBlockEntity.FUEL_CAPACITY));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.HELIUM_IN,
            FluidThermal.at(FluidForm.LIQUID_HELIUM.stack(FusionReactorControllerBlockEntity.LIQUID_HELIUM_CAPACITY), 4.2));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN,
            FluidForm.PRESSURISED_WATER.stack(FusionReactorControllerBlockEntity.COOLANT_CAPACITY));
        controller.tick();
        helper.assertTrue(controller.coolantOut().isEmpty()
                && FluidForm.PRESSURISED_WATER.matches(controller.coolantIn()),
            "An unlit ambient blanket cannot heat 200 C coolant from nothing");
        controller.blanketPort().insertItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        controller.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        controller.commissionForTest();

        for (int tick = 0; tick < 12_000; tick++) {
            pump.setSpeed(64);
            controller.supplyElectrical(50_000_000, 400);
            controller.tick();
            var outlet = controller.port(net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_OUT);
            outlet.drain(FusionReactorControllerBlockEntity.COOLANT_CAPACITY, FluidAction.EXECUTE);
            fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN,
                FluidForm.PRESSURISED_WATER.stack(FusionPhysics.RATED_COOLANT_MB_PER_TICK));
        }
        helper.assertTrue(Math.abs(controller.fusionWatts() - 400_000_000) < 1,
            "The default physical desk setpoint is a steady 400 MW");
        helper.assertTrue(controller.status().equals("burning"),
            "A burning reactor reports its burn instead of an empty per-tick coolant input buffer");
        helper.assertTrue(controller.blanketKelvin() < FusionPhysics.COOLANT_OUTLET_KELVIN + 1
            && controller.coilKelvin() <= 4.21 && !controller.emergency(),
            "Ten simulated minutes stay inside blanket and superconducting-coil limits");
        helper.assertTrue(controller.deuterium().getAmount() < FusionReactorControllerBlockEntity.FUEL_CAPACITY
            && controller.ash().getAmount() > 0 && controller.blanketRemaining() < 24_000,
            "Steady burn consumes D-T, makes helium ash and wears the breeding blanket");

        CompoundTag coolantLoss = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
        coolantLoss.remove("CoolantIn");
        controller.loadWithComponents(coolantLoss, helper.getLevel().registryAccess());
        for (int tick = 0; tick < 10; tick++) {
            controller.supplyElectrical(50_000_000, 400);
            controller.tick();
        }
        helper.assertTrue(controller.fusionWatts() == 400_000_000 && controller.status().equals("burning"),
            "A sub-second Create pipe scheduling gap neither lies about coolant nor extinguishes the plasma");
        for (int tick = 0; tick < 11; tick++) {
            controller.supplyElectrical(50_000_000, 400);
            controller.tick();
        }
        helper.assertTrue(controller.fusionWatts() == 0 && controller.status().equals("coolant"),
            "A genuine one-second coolant loss still opens the plasma interlock");

        CompoundTag saved = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
        int deuterium = controller.deuterium().getAmount(), blanket = controller.blanketRemaining();
        controller.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(controller.deuterium().getAmount() == deuterium && controller.blanketRemaining() == blanket,
            "Reload preserves finite fuel and blanket life exactly");

        saved.putDouble("CoilKelvin", 7);
        controller.loadWithComponents(saved, helper.getLevel().registryAccess());
        controller.supplyElectrical(50_000_000, 400);
        controller.tick();
        helper.assertTrue(controller.quench() && controller.emergency() && controller.fusionWatts() == 0
            && helper.getLevel().getBlockEntity(controllerPos) == controller,
            "A warm charged coil quenches into a controlled dump without a reactor explosion");

        FusionStructure.Cell coil = FusionStructure.cells(controllerPos, Direction.SOUTH).stream()
            .filter(cell -> cell.role() == FusionStructure.Role.COIL).findFirst().orElseThrow();
        helper.getLevel().setBlock(coil.pos(), Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < 41; i++) controller.tick();
        helper.assertTrue(!controller.formed() && controller.fusionWatts() == 0 && controller.status().equals("coil"),
            "Removing one coil invalidates the indexed structure and leaves it shut down");
        helper.getLevel().destroyBlock(controllerPos, false);
        helper.assertTrue(controller.deuterium().isEmpty() && controller.tritium().isEmpty()
            && controller.liquidHelium().isEmpty() && controller.coolantIn().isEmpty()
            && controller.blanketRemaining() == 0 && controller.magnetJoules() == 0,
            "Removing the feeder vents finite inventories, dumps the magnet and recovers the blanket instead of deleting hidden contents");
        helper.succeed();
    }

    @GameTest(template = "test_fusion", timeoutTicks = 100)
    public static void shortElectricalTransferKeepsPlasmaHeatAndRestartsImmediately(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        for (FusionStructure.Cell cell : FusionStructure.cells(controllerPos, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) helper.getLevel().setBlock(cell.pos(), cell.expected(), 3);
        var controller = (FusionReactorControllerBlockEntity) helper.getLevel().getBlockEntity(controllerPos);
        controller.tick();
        BlockPos pumpPos = controller.centre().relative(Direction.WEST, FusionPhysics.HALF_WIDTH);
        var pump = (FusionVacuumPumpBlockEntity) helper.getLevel().getBlockEntity(pumpPos);
        BlockPos motorPos = pumpPos.west();
        helper.getLevel().setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(motorPos))
            .generatedSpeed.setValue(64);
        pump.setSpeed(64);
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.DEUTERIUM,
            new FluidStack(CEFusion.DEUTERIUM_GAS.get(), 8_000));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.TRITIUM,
            new FluidStack(CEFusion.TRITIUM_GAS.get(), 8_000));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.HELIUM_IN,
            FluidThermal.at(FluidForm.LIQUID_HELIUM.stack(60_000), 4.2));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN,
            FluidForm.PRESSURISED_WATER.stack(FusionReactorControllerBlockEntity.COOLANT_CAPACITY));
        controller.blanketPort().insertItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        controller.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        controller.commissionForTest();

        int[] elapsed = {0};
        helper.onEachTick(() -> {
            int tick = elapsed[0]++;
            pump.setSpeed(64);
            controller.port(net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_OUT)
                .drain(FusionReactorControllerBlockEntity.COOLANT_CAPACITY, FluidAction.EXECUTE);
            fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN,
                FluidForm.PRESSURISED_WATER.stack(FusionPhysics.RATED_COOLANT_MB_PER_TICK));
            if (tick < 20 || tick >= 30) controller.supplyElectrical(50_000_000, 20_000);
        });
        helper.runAfterDelay(29, () -> helper.assertTrue(controller.fusionWatts() == 0
                && controller.plasmaKelvin() >= FusionPhysics.PLASMA_KELVIN * .99,
            "A sub-second source transfer stops fusion but retains the already heated plasma-facing assembly: fusion="
                + controller.fusionWatts() + " W, plasma=" + controller.plasmaKelvin() + " K, electrical="
                + controller.electricalWatts() + " W, status=" + controller.status()));
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(controller.fusionWatts() == 400_000_000 && controller.status().equals("burning"),
                "Restored shaft power resumes the 400 MW burn without repeating the 1 GJ startup heat cycle");
            helper.succeed();
        });
    }

    @GameTest(template = "test_fusion", timeoutTicks = 240)
    public static void flywheelMotorGeneratorPowersTheTokamakAsARealKineticLoad(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        for (FusionStructure.Cell cell : FusionStructure.cells(controllerPos, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) helper.getLevel().setBlock(cell.pos(), cell.expected(), 3);
        var controller = (FusionReactorControllerBlockEntity) helper.getLevel().getBlockEntity(controllerPos);
        controller.tick();
        var pump = (FusionVacuumPumpBlockEntity) helper.getLevel().getBlockEntity(controller.centre().relative(Direction.WEST, FusionPhysics.HALF_WIDTH));
        pump.setSpeed(64);
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.DEUTERIUM,
            new FluidStack(CEFusion.DEUTERIUM_GAS.get(), 8_000));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.TRITIUM,
            new FluidStack(CEFusion.TRITIUM_GAS.get(), 8_000));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.HELIUM_IN,
            FluidThermal.at(FluidForm.LIQUID_HELIUM.stack(60_000), 4.2));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN,
            FluidForm.PRESSURISED_WATER.stack(FusionReactorControllerBlockEntity.COOLANT_CAPACITY));
        controller.blanketPort().insertItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        controller.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        controller.commissionForTest();

        BlockPos generatorPos = controllerPos.north(), flywheelPos = generatorPos.north(), motorPos = flywheelPos.north();
        helper.getLevel().setBlock(generatorPos, CEFusion.MOTOR_GENERATOR.get().defaultBlockState()
            .setValue(FusionMotorGeneratorBlock.AXIS, Direction.Axis.Z), 3);
        helper.getLevel().setBlock(flywheelPos, CEBlocks.LARGE_FLYWHEEL.get().defaultBlockState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        helper.getLevel().setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
        var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(motorPos);
        motor.generatedSpeed.setValue(256);
        // The creative motor represents an off-site starting grid. Give that source the 120 MW capacity a real
        // RBMK/turbine station would need; the M-G still publishes and is checked as the full kinetic load.
        helper.onEachTick(() -> {
            if (motor.hasNetwork()) motor.getOrCreateNetwork().updateCapacityFor(motor, 550_000);
        });

        helper.runAfterDelay(80, () -> {
            var generator = (FusionMotorGeneratorBlockEntity) helper.getLevel().getBlockEntity(generatorPos);
            helper.assertTrue(generator.status().equals("running") && generator.electricalWatts() >= 39_900_000
                    && generator.calculateStressApplied() > 150_000 && controller.electricalWatts() >= 39_900_000,
                "The flywheel M-G converts a 256 RPM shaft into the tokamak's 40 MW kinetic load: "
                    + generator.electricalWatts() + " W / " + generator.calculateStressApplied() + " SU/RPM");
            helper.getLevel().destroyBlock(flywheelPos, false);
        });
        helper.runAfterDelay(140, () -> {
            var generator = (FusionMotorGeneratorBlockEntity) helper.getLevel().getBlockEntity(generatorPos);
            helper.assertTrue(generator.status().equals("flywheel") && generator.electricalWatts() == 0
                    && controller.electricalWatts() == 0,
                "Removing the required flywheel opens the M-G feed without inventing power");
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void physicalConsoleRequiresExactlyThreeByTwo(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        Direction facing = Direction.NORTH, right = facing.getCounterClockWise();
        BlockPos master = origin.relative(right, FusionControlConsoleBlockEntity.MASTER_COLUMN);
        for (int depth = 0; depth < FusionControlConsoleBlockEntity.DEPTH; depth++)
            for (int column = 0; column < FusionControlConsoleBlockEntity.WIDTH; column++) {
                BlockPos pos = origin.relative(right, column).relative(facing, depth);
                if (pos.equals(master)) helper.getLevel().setBlock(pos, CEFusion.CONSOLE.get().defaultBlockState()
                    .setValue(FusionControlConsoleBlock.FACING, facing), 3);
                else helper.getLevel().setBlock(pos, CEFusion.CONSOLE_PANEL.get().defaultBlockState()
                    .setValue(FusionConsolePanelBlock.FACING, facing), 3);
            }
        var console = (FusionControlConsoleBlockEntity) helper.getLevel().getBlockEntity(master);
        helper.assertTrue(console != null && console.stationComplete(), "A 3 x 2 electromechanical desk assembles without a GUI");
        helper.getLevel().setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!console.stationComplete(), "Every physical switch and indicator section is required");
        helper.succeed();
    }


    @GameTest(template="test_fusion",timeoutTicks=200)
    public static void controlDeskRunsGradedCoolantThroughSeparatorAndBothTurbines(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(CONTROLLER);
        for(var cell:FusionStructure.cells(pos,Direction.SOUTH))if(cell.role()!=FusionStructure.Role.CLEAR)level.setBlock(cell.pos(),cell.expected(),3);
        var controller=(FusionReactorControllerBlockEntity)level.getBlockEntity(pos);controller.tick();
        var pump=(FusionVacuumPumpBlockEntity)level.getBlockEntity(controller.centre().relative(Direction.WEST,FusionPhysics.HALF_WIDTH));pump.setSpeed(64);
        var console=buildConsole(helper,helper.absolutePos(new BlockPos(4,2,43)),Direction.NORTH);
        linkConsole(helper,pos.north(),Direction.NORTH,console.getBlockPos());console.lazyTick();
        var player=FakePlayerFactory.getMinecraft(level);
        helper.assertTrue(console.stationComplete() && console.linked() && console.target()==80,"The linked physical desk reads the reactor's 80 percent target");
        fill(controller,FusionFluidPortBlock.Kind.DEUTERIUM,new FluidStack(CEFusion.DEUTERIUM_GAS.get(),60000));
        fill(controller,FusionFluidPortBlock.Kind.TRITIUM,new FluidStack(CEFusion.TRITIUM_GAS.get(),60000));
        fill(controller,FusionFluidPortBlock.Kind.HELIUM_IN,FluidForm.LIQUID_HELIUM.stack(60000));
        controller.blanketPort().insertItem(0,new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()),false);controller.blanketPort().insertItem(1,new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()),false);
        controller.commissionForTest();controller.preheatBlanketForTest(523.15);
        var pair=TurbineThermalGameTests.build(level,helper.absolutePos(BlockPos.ZERO));
        var at=helper.absolutePos(new BlockPos(4,2,26));
        for(int y=0;y<6;y++)level.setBlock(at.above(y),net.createplant.registry.CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);
        var separator=(net.createplant.block.entity.AmmoniaSeparatorBlockEntity)level.getBlockEntity(at);com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(separator);separator=(net.createplant.block.entity.AmmoniaSeparatorBlockEntity)separator.getControllerBE();
        var inlet=level.getCapability(Capabilities.FluidHandler.BLOCK,at.above(5),Direction.NORTH);var gas=level.getCapability(Capabilities.FluidHandler.BLOCK,at.above(5),Direction.UP);var liquid=level.getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.DOWN);
        long supplied=0,returned=0,condensate=0,hot=0;double work=0,rejected=0;
        for(int tick=0;tick<4000;tick++){
            supplied+=controller.port(FusionFluidPortBlock.Kind.COOLANT_IN).fill(FluidForm.PRESSURISED_WATER.stack(200000),FluidAction.EXECUTE);
            controller.supplyElectrical(50000000,400);pump.setSpeed(64);controller.tick();
            var out=controller.port(FusionFluidPortBlock.Kind.COOLANT_OUT);var preview=out.drain(200000,FluidAction.SIMULATE);int n=inlet.fill(preview,FluidAction.SIMULATE);inlet.fill(out.drain(n,FluidAction.EXECUTE),FluidAction.EXECUTE);hot+=n;
            separator.process(.25);returned+=liquid.drain(Integer.MAX_VALUE,FluidAction.EXECUTE).getAmount();
            for(int rotor=0;rotor<3;rotor++){
                var high=((SteamTurbineBlockEntity)level.getBlockEntity(helper.absolutePos(new BlockPos(13+rotor,4,10)))).thermodynamics();
                var low=((SteamTurbineBlockEntity)level.getBlockEntity(helper.absolutePos(new BlockPos(5+rotor,4,10)))).thermodynamics();
                var steam=gas.drain(20000,FluidAction.SIMULATE);n=high.input().fill(steam,FluidAction.SIMULATE);high.input().fill(gas.drain(n,FluidAction.EXECUTE),FluidAction.EXECUTE);
                high.step(true,TurbineThermal.MAX_FLOW);var exhaust=high.exhaust().drain(TurbineThermal.MAX_FLOW,FluidAction.SIMULATE);n=low.input().fill(exhaust,FluidAction.SIMULATE);low.input().fill(high.exhaust().drain(n,FluidAction.EXECUTE),FluidAction.EXECUTE);
                low.step(true,TurbineThermal.MAX_FLOW);work+=high.work()+low.work();rejected+=low.removeCondenserHeat(Double.MAX_VALUE);condensate+=low.exhaust().drain(Integer.MAX_VALUE,FluidAction.EXECUTE).getAmount();
            }
        }
        long stock=controller.coolantIn().getAmount()+controller.coolantOut().getAmount()+separator.feed().getAmount()+separator.liquid().getAmount()+separator.recycle().getAmount();
        for(int x:new int[]{5,6,7,8,9,13,14,15}){var rotor=(SteamTurbineBlockEntity)level.getBlockEntity(helper.absolutePos(new BlockPos(x,4,10)));stock+=rotor.thermodynamics().input().getFluidAmount()+rotor.thermodynamics().exhaust().getFluidAmount();}
        helper.assertTrue(supplied==returned+condensate+stock,"All finite coolant, separated water, steam and condensate balance: "+supplied+" / "+(returned+condensate+stock));
        helper.assertTrue(controller.fusionWatts()==400000000 && !controller.emergency() && hot>0 && condensate>0 && work>0 && rejected>0,"The 80 percent furnace supplies both stages without a pressure stall");
        console.emergency(player,false);helper.assertTrue(controller.emergency(),"Physical desk emergency command reaches the operating reactor");
        LOGGER.info("THERMAL C fusion boundary: supplied={}, hot={}, returned={}, condensate={}, shaftJ={}, rejectedJ={}, blanketK={}",supplied,hot,returned,condensate,work,rejected,controller.blanketKelvin());helper.succeed();
    }

    private static FusionControlConsoleBlockEntity buildConsole(GameTestHelper helper, BlockPos master, Direction facing) {
        Direction right = facing.getCounterClockWise();
        BlockPos origin = master.relative(right, -FusionControlConsoleBlockEntity.MASTER_COLUMN);
        for (int depth = 0; depth < FusionControlConsoleBlockEntity.DEPTH; depth++)
            for (int column = 0; column < FusionControlConsoleBlockEntity.WIDTH; column++) {
                BlockPos pos = origin.relative(right, column).relative(facing, depth);
                if (pos.equals(master)) helper.getLevel().setBlock(pos, CEFusion.CONSOLE.get().defaultBlockState()
                    .setValue(FusionControlConsoleBlock.FACING, facing), 3);
                else helper.getLevel().setBlock(pos, CEFusion.CONSOLE_PANEL.get().defaultBlockState()
                    .setValue(FusionConsolePanelBlock.FACING, facing), 3);
            }
        return (FusionControlConsoleBlockEntity) helper.getLevel().getBlockEntity(master);
    }

    private static DisplayLinkBlockEntity linkConsole(GameTestHelper helper, BlockPos pos, Direction facing, BlockPos target) {
        helper.getLevel().setBlock(pos, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, facing), 3);
        var link = (DisplayLinkBlockEntity) helper.getLevel().getBlockEntity(pos);
        link.activeSource = CEDisplays.MACHINE.get();
        link.activeTarget = CEDisplays.FUSION_CONSOLE.get();
        link.getSourceConfig().putInt("Metric", MachineReadings.KEYS.indexOf("fusion_power"));
        link.target(target);
        link.updateGatheredData();
        return link;
    }

    private static int turbineMass(SteamTurbineBlockEntity high, SteamTurbineBlockEntity low) {
        return high.thermodynamics().input().getFluidAmount() + high.thermodynamics().exhaust().getFluidAmount()
            + low.thermodynamics().input().getFluidAmount() + low.thermodynamics().exhaust().getFluidAmount();
    }

    private static void transferTurbineExhaust(SteamTurbineBlockEntity high, SteamTurbineBlockEntity low) {
        FluidStack preview = high.thermodynamics().exhaust().drain(64, FluidAction.SIMULATE);
        int accepted = low.thermodynamics().input().fill(preview, FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack parcel = high.thermodynamics().exhaust().drain(accepted, FluidAction.EXECUTE);
        if (low.thermodynamics().input().fill(parcel, FluidAction.EXECUTE) != accepted)
            throw new IllegalStateException("Turbine transfer promise changed");
    }

    /** The relay computer's sequence brings a cold, unpumped tokamak to a burn at its own target without anyone at the desk. */
    @GameTest(template = "test_fusion", timeoutTicks = 200)
    public static void relayComputerStartsAColdReactorAndHoldsItsTarget(GameTestHelper helper) {
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        for (FusionStructure.Cell cell : FusionStructure.cells(controllerPos, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) helper.getLevel().setBlock(cell.pos(), cell.expected(), 3);
        var controller = (FusionReactorControllerBlockEntity) helper.getLevel().getBlockEntity(controllerPos);
        controller.tick();
        helper.assertTrue(controller.formed(), "The tokamak forms: " + controller.status());
        var pump = (FusionVacuumPumpBlockEntity) helper.getLevel().getBlockEntity(controller.centre().relative(Direction.WEST, FusionPhysics.HALF_WIDTH));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.DEUTERIUM, new FluidStack(CEFusion.DEUTERIUM_GAS.get(), FusionReactorControllerBlockEntity.FUEL_CAPACITY));
        fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.TRITIUM, new FluidStack(CEFusion.TRITIUM_GAS.get(), FusionReactorControllerBlockEntity.FUEL_CAPACITY));
        controller.blanketPort().insertItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        controller.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        String first = net.createplant.relay.RelayComputerBlockEntity.fusionStep(controller, 50);
        helper.assertTrue(first.equals("fusion_vacuum") && controller.vacuumCommand() && !controller.plasmaCommand(),
            "A cold reactor is pumped down first, with the plasma held off: " + first);
        String status = first;
        java.util.List<String> seen = new java.util.ArrayList<>(java.util.List.of(first));
        for (int tick = 0; tick < 200_000 && !(status.equals("fusion") && controller.fusionWatts() > 0 && controller.targetPercent() == 50); tick++) {
            pump.setSpeed(64);
            controller.supplyElectrical(50_000_000, 400);
            fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.HELIUM_IN,
                FluidThermal.at(FluidForm.LIQUID_HELIUM.stack(FusionReactorControllerBlockEntity.LIQUID_HELIUM_CAPACITY), 4.2));
            fill(controller, net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_IN, FluidForm.PRESSURISED_WATER.stack(FusionPhysics.RATED_COOLANT_MB_PER_TICK));
            controller.port(net.createplant.block.FusionFluidPortBlock.Kind.COOLANT_OUT).drain(FusionReactorControllerBlockEntity.COOLANT_CAPACITY, FluidAction.EXECUTE);
            controller.tick();
            if (tick % net.createplant.relay.RelayComputerBlockEntity.PERIOD == 0) {
                status = net.createplant.relay.RelayComputerBlockEntity.fusionStep(controller, 50);
                if (!seen.getLast().equals(status)) seen.add(status);
            }
        }
        helper.assertTrue(status.equals("fusion") && controller.fusionWatts() > 0 && controller.targetPercent() == 50 && !controller.emergency(),
            "The computer brings the reactor to a burn at 50%: " + seen + " status=" + controller.status() + " target=" + controller.targetPercent());
        helper.assertTrue(seen.indexOf("fusion_charging") > seen.indexOf("fusion_vacuum"), "Magnets charge only after the vacuum: " + seen);
        helper.succeed();
    }

    private static void fill(FusionReactorControllerBlockEntity controller,
                             net.createplant.block.FusionFluidPortBlock.Kind kind, FluidStack stack) {
        controller.port(kind).fill(stack, FluidAction.EXECUTE);
    }
}
