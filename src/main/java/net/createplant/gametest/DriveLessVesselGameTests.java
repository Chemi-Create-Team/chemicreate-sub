package net.createplant.gametest;

import java.util.ArrayList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.VesselStirrerBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.mixture.MixtureContents;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class DriveLessVesselGameTests {
    private static final BlockPos BASE = new BlockPos(4, 3, 4), STIRRER = BASE.offset(1, 2, 1);

    /** What a Create pump asks for: the fluid the port holds, with its temperature, burning leaves the gas hot. */
    private static FluidStack held(net.neoforged.neoforge.fluids.capability.IFluidHandler port, net.minecraft.world.level.material.Fluid fluid, int amount) {
        for (int tank = 0; tank < port.getTanks(); tank++) if (port.getFluidInTank(tank).is(fluid)) return port.getFluidInTank(tank).copyWithAmount(amount);
        return new FluidStack(fluid, amount);
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void closedStainlessTankBurnsSulfurWithOxygenAtSuperheat(GameTestHelper h) {
        var burnerPos = BASE.below();
        var heat = com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL;
        var idleBurner = AllBlocks.BLAZE_BURNER.getDefaultState().setValue(heat,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING);
        h.setBlock(BASE, CETransport.LIDDED_VESSEL.get());
        h.setBlock(burnerPos,idleBurner);
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(BASE);
        var level = h.getLevel();
        var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE), Direction.UP);
        var oxygen = new FluidStack(net.createplant.registry.CEFluids.OXYGEN_GAS.get(), 500);
        var sulfur = net.createplant.registry.CEItems.SULFUR_DUST.toStack();
        h.assertTrue(tank != null && tank.fill(oxygen, EXECUTE) == 500 && vessel.itemPort().insertItem(0, sulfur, false).isEmpty(), "Closed tank accepts sulfur and oxygen on its normal ports");
        var coal = new ItemStack(net.minecraft.world.item.Items.COAL);
        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(h.getBlockState(burnerPos), level, h.absolutePos(burnerPos), coal, false, false, false);
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getBlockState(burnerPos).getValue(heat) == com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED
                && vessel.status().equals("heat") && vessel.progress() == 0 && vessel.getTankInventory().getFluidAmount() == 500,
                "Normal heat keeps the original sulfur and oxygen intact");
            h.setBlock(burnerPos,Blocks.AIR);
            h.setBlock(burnerPos,idleBurner);
            var cake = com.simibubi.create.AllItems.BLAZE_CAKE.asStack();
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(h.getBlockState(burnerPos), level, h.absolutePos(burnerPos), cake, false, false, false);
            h.assertTrue(h.getBlockState(burnerPos).getValue(heat) == com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING,
                "A finite Blaze Cake supplies real superheat");
        });
        h.runAfterDelay(150, () -> {
            var sulfurDioxide = net.createplant.registry.CEUranium.SULFUR_DIOXIDE.get();
            var product = tank.drain(held(tank, sulfurDioxide, 500), EXECUTE);
            h.assertTrue(product.getAmount() == 500 && product.is(sulfurDioxide) && vessel.getTankInventory().isEmpty()
                && vessel.itemPort().getStackInSlot(0).isEmpty() && vessel.rotationSpeed() == 0,
                "The stock roof port gives one batch of SO2 without an agitator or a rotation source: " + product.getAmount() + " " + vessel.status()
                    + " tank " + vessel.getTankInventory().getFluid().getAmount() + " " + vessel.getTankInventory().getFluid().getHoverName().getString()
                    + " lvl " + net.createplant.thermal.FluidState.temperature(vessel.getTankInventory().getFluid()) + " item " + vessel.itemPort().getStackInSlot(0));
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void airBurnsSulfurInASealedStainlessTank(GameTestHelper h) {
        var sulfurDioxide = net.createplant.registry.CEUranium.SULFUR_DIOXIDE.get();
        var oxygen = BuiltInRegistries.FLUID.getKey(CEFluids.OXYGEN_GAS.get());
        for (var pos : new BlockPos[] {BASE, BASE.east(4)}) {
            h.setBlock(pos, CETransport.LIDDED_VESSEL.get());
            var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(pos);
            var inlet = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.UP);
            var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.EAST);
            boolean nativeAir = pos.equals(BASE);
            int amount = nativeAir ? 3000 : ((net.createplant.thermal.GradeFluidTank) vessel.getTankInventory()).gasCapacity();
            var air = nativeAir ? new FluidStack(CEFluids.AIR_GAS.get(), amount)
                : MixtureHelper.createStack(MixtureHelper.airContents(amount), false);
            h.assertTrue(inlet.fill(air, EXECUTE) == amount && vessel.itemPort().insertItem(0, net.createplant.registry.CEItems.SULFUR_DUST.toStack(), false).isEmpty(),
                "The normal ports accept the complete air parcel and sulfur dust");
            var heat = com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL;
            var burner = AllBlocks.BLAZE_BURNER.getDefaultState().setValue(heat, com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING);
            h.setBlock(pos.below(), burner);
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(burner, h.getLevel(), h.absolutePos(pos.below()), com.simibubi.create.AllItems.BLAZE_CAKE.asStack(), false, false, false);
            h.runAfterDelay(150, () -> {
                var product = port.drain(held(port, sulfurDioxide, 500), EXECUTE);
                var expected = new java.util.TreeMap<>(MixtureHelper.airContents(amount).amounts());
                expected.compute(oxygen, (id, available) -> available - 500);
                var remaining = vessel.getTankInventory().getFluid();
                h.assertTrue(product.is(sulfurDioxide) && product.getAmount() == 500 && vessel.itemPort().getStackInSlot(0).isEmpty()
                    && MixtureHelper.extractContents(remaining).equals(new MixtureContents(expected)),
                    "The batch uses only the needed oxygen and leaves the other gases together, including at full rated capacity: " + vessel.status()
                        + " product " + product.getAmount() + " remaining " + MixtureHelper.extractContents(remaining) + " expected " + expected
                        + " lvl " + net.createplant.thermal.FluidState.temperature(remaining) + " item " + vessel.itemPort().getStackInSlot(0));
                var simulated = port.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                h.assertTrue(simulated.getAmount() == 1000 && MixtureHelper.extractContents(simulated).equals(new MixtureContents(expected).split(1000).extracted())
                    && port.drain(new FluidStack(CEFluids.OXYGEN_GAS.get(), 1), EXECUTE).isEmpty(),
                    "A normal outlet samples the residual mixture in proportion");
                if (!nativeAir) h.succeed();
            });
        }
    }

    /** A two-layer stirred tank over its stirrer, with a lit burner under one corner. */
    private static LiddedVesselBlockEntity vessel(GameTestHelper h) {
        var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 2);
        h.setBlock(BASE.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(
                com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
        var burner=(com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity)h.getBlockEntity(BASE.below());
        var tag=burner.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("fuelLevel",1);tag.putInt("burnTimeRemaining",4000);burner.loadWithComponents(tag,h.getLevel().registryAccess());burner.updateBlockState();
        return owner;
    }

    private static void motor(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(128));
    }

    private static void drive(GameTestHelper h) { ReactorGameTests.driveStirrer(h, BASE); }

    private static VesselStirrerBlockEntity stirrer(GameTestHelper h) { return (VesselStirrerBlockEntity) h.getBlockEntity(STIRRER); }

    @GameTest(template = "test_reactor", timeoutTicks = 400)
    public static void aCogwheelTurnsTheStirrerWhichRunsTheTankAndCarriesItsStress(GameTestHelper h) {
        var owner = vessel(h);
        owner.port(Direction.NORTH).fill(new FluidStack(net.createplant.registry.CEPetroFluids.PROPYLENE.get(),1000), EXECUTE);
        owner.port(Direction.NORTH).fill(new FluidStack(net.createplant.registry.CEProcessFluids.SYNTHESIS_GAS.get(),1000), EXECUTE);
        drive(h);
        h.runAfterDelay(330, () -> {
            h.assertTrue(owner.formed() && Math.abs(owner.rotationSpeed()) == 128 && Math.abs(stirrer(h).getSpeed()) == 128,
                "A cogwheel beside the stirrer turns it through Create's kinetic network, and the tank runs at its speed");
            h.assertTrue(stirrer(h).calculateStressApplied() == 32 && owner.calculateStressApplied() == 0, "The stirrer carries the two impellers' load; the tank none");
            var port = owner.port(Direction.NORTH);
            h.assertTrue(port.getFluidInTank(0).isEmpty() && port.getFluidInTank(2).getAmount() == 1000, "One feed batch makes exactly one output batch");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void theTankTakesNoRotationOfItsOwn(GameTestHelper h) {
        var owner = vessel(h);
        owner.port(Direction.NORTH).fill(new FluidStack(net.createplant.registry.CEPetroFluids.PROPYLENE.get(),1000), EXECUTE);
        owner.port(Direction.NORTH).fill(new FluidStack(net.createplant.registry.CEProcessFluids.SYNTHESIS_GAS.get(),1000), EXECUTE);
        // Motors under every block but the stirrer and the corner burner.
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if ((x != 1 || z != 1) && (x != 0 || z != 0)) motor(h, BASE.offset(x, -1, z), Direction.UP);
        for (Direction side : Direction.Plane.HORIZONTAL) motor(h, BASE.offset(1, 0, 1).relative(side, 2), side.getOpposite());
        h.runAfterDelay(50, () -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                var part = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(x, 0, z));
                h.assertTrue(part.getSpeed() == 0 && part.calculateStressApplied() == 0, "No motor under or beside the tank connects to it");
            }
            h.assertTrue(owner.rotationSpeed() == 0 && owner.status().equals("speed") && owner.progress() == 0
                && owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "Without its stirrer turning, the tank cannot run or consume its charge");
            drive(h);
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(Math.abs(owner.rotationSpeed()) == 128 && owner.port(Direction.NORTH).getFluidInTank(0).isEmpty()
                && owner.port(Direction.NORTH).getFluidInTank(2).getAmount() == 1000, "Driving the stirrer completes the waiting batch");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void resizingATankEngagesTheStirrerOnlyOnAThreeByThree(GameTestHelper h) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) h.setBlock(BASE.offset(x, y, z), CETransport.LIDDED_VESSEL.get());
        h.setBlock(STIRRER, net.createplant.registry.CEReactor.STIRRER.get());
        var owner = ((LiddedVesselBlockEntity) h.getBlockEntity(BASE)).getControllerBE();
        owner.getTankInventory().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        owner.itemPort().insertItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 7), false);
        // Left still, so that no stirred recipe takes the stored water while the tank is 3x3.
        h.runAfterDelay(30, () -> {
            h.assertTrue(owner.getWidth() == 2 && !owner.stirred() && stirrer(h).calculateStressApplied() == 0,
                "A stirrer on a 2x2 tank stirs nothing and carries no load");
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if (x == 2 || z == 2) h.setBlock(BASE.offset(x, y, z), CETransport.LIDDED_VESSEL.get());
        });
        h.runAfterDelay(65, () -> {
            h.assertTrue(owner.getWidth() == 3 && owner.stirred() && stirrer(h).vessel() == owner && stirrer(h).calculateStressApplied() == 32,
                "Expanding to 3x3 under the stirrer makes a stirred tank whose load the stirrer carries");
            for (int y = 1; y >= 0; y--) for (int x = 2; x >= 0; x--) for (int z = 2; z >= 0; z--) if (x == 2 || z == 2) h.setBlock(BASE.offset(x, y, z), Blocks.AIR);
        });
        h.runAfterDelay(110, () -> {
            h.assertTrue(owner.getWidth() == 2 && !owner.stirred() && !owner.formed() && stirrer(h).calculateStressApplied() == 0,
                "Shrinking back to storage lets the stirrer go");
            h.assertTrue(owner.getTankInventory().getFluidAmount() == 1000 && owner.itemPort().getStackInSlot(0).getCount() == 7,
                "Resizing retains the complete stored water and items");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void aSavedOldShaftConnectionCannotRunATank(GameTestHelper h) {
        var owner = vessel(h);var level = h.getLevel();
        owner.port(Direction.NORTH).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        // Before stirrers the tank's own bottom centre took the shaft and saved its speed.
        var saved = owner.saveWithoutMetadata(level.registryAccess());
        saved.putLong("PowerInput", owner.getBlockPos().asLong());saved.putFloat("InputSpeed", 128);
        owner.loadWithComponents(saved, level.registryAccess());
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            var part = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(x, 0, z));part.setSpeed(128);part.initialize();
        }
        owner.validateStructure();owner.tickProcess();
        h.assertTrue(owner.formed() && owner.rotationSpeed() == 0 && owner.progress() == 0, "A saved speed of the tank itself cannot turn its impellers");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            var part = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(x, 0, z));
            h.assertTrue(part.getTheoreticalSpeed() == 0 && !part.hasSource(), "The saved kinetic connection of every member is removed during loading");
        }
        h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "The waiting process charge survives the connection migration");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void anOverstressedStirrerKeepsItsLoad(GameTestHelper h) {
        var owner = vessel(h);
        var stirrer = stirrer(h);
        stirrer.setSpeed(128);
        owner.validateStructure();
        stirrer.updateFromNetwork(0, 2048, 1);
        h.assertTrue(stirrer.getSpeed() == 0 && owner.rotationSpeed() == 0, "An overloaded network stops both the stirrer and the process");
        h.assertTrue(stirrer.calculateStressApplied() == 32 && owner.calculateStressApplied() == 0, "A stopped overloaded stirrer retains the process load");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void removingTheStirrerAndSavingKeepsTheReactionCharge(GameTestHelper h) {
        var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 2);
        owner.itemPort().insertItem(0, net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.toStack(), false);
        h.setBlock(STIRRER, Blocks.AIR);owner.validateStructure();
        h.assertTrue(!owner.formed() && owner.getWidth() == 3, "Taking the stirrer away stops processing while retaining the square storage vessel");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        owner.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(owner.module() == ReactorModule.NONE && owner.hasProcessContents(), "An unstirred saved vessel still owns its reaction charge");
        h.setBlock(STIRRER, net.createplant.registry.CEReactor.STIRRER.get());owner.validateStructure();
        h.assertTrue(owner.formed() && owner.itemPort().getStackInSlot(0).is(net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.get()), "Putting the stirrer back makes the saved ingredient reachable again");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void cachedStoragePortsRespectProcessingAndTheOpenHatch(GameTestHelper h) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(BASE.offset(x, y, z), CETransport.LIDDED_VESSEL.get());
        var owner = (LiddedVesselBlockEntity) h.getBlockEntity(BASE);
        var port = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.offset(1, 1, 1)), Direction.UP);
        var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        h.setBlock(STIRRER, net.createplant.registry.CEReactor.STIRRER.get());
        h.assertTrue(port != null && port.fill(water, EXECUTE) == 0, "Placing the stirrer immediately closes the storage-fill path before the next structure scan");
        owner.validateStructure();
        h.assertTrue(owner.formed() && port.fill(water, EXECUTE) == 0 && owner.getTankInventory().isEmpty(), "A cached storage port cannot insert hidden water into an active process");
        h.setBlock(STIRRER, Blocks.AIR);
        h.assertTrue(port.fill(water, EXECUTE) == 1000, "The storage port works again once the stirrer is gone");
        owner.toggleLid();
        h.assertTrue(port.fill(water, EXECUTE) == 0 && port.drain(1000, EXECUTE).isEmpty(), "Opening the hatch retires a previously cached top pipe connection");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void aFilledProcessCannotBeRehomedAndItsGogglesDoNotRecurse(GameTestHelper h) {
        var owner = vessel(h);
        var port = owner.port(Direction.NORTH);
        port.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        // An incomplete third layer round the stirrer.
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if (x != 1 || z != 1) h.setBlock(BASE.offset(x, 2, z), CETransport.LIDDED_VESSEL.get());
        owner.validateStructure();
        h.assertTrue(owner.isController() && owner.getBlockPos().equals(h.absolutePos(BASE)) && owner.stirred(), "Tanks placed under it cannot strand a filled process in a non-controller member");
        h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "The feed remains reachable at its original controller");
        var text = new ArrayList<Component>();
        h.assertTrue(owner.addToGoggleTooltip(text, false) && !text.isEmpty(), "A formed vessel shows a goggle summary");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void everyModuleKeepsSquareCornersWithoutHorizontalShafts(GameTestHelper h) {
        var block = CETransport.LIDDED_VESSEL.get();
        int index = 0;
        for (var module : new ReactorModule[] {ReactorModule.STIRRED}) {
            BlockPos base = BASE.offset((index % 2) * 9, 0, (index / 2) * 9);index++;
            var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(base), module, 2);
            h.assertTrue(owner.formed() && owner.getWidth() == 3, "The stirrer forms its process at its supported width: " + module);
            int[] corners=new int[]{0,2};
            for (int x : corners) for (int z : corners) {
                BlockPos pos = base.offset(x, 0, z);var state = h.getBlockState(pos);var absolute = h.absolutePos(pos);
                h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(state.getShape(h.getLevel(), absolute),
                    net.minecraft.world.phys.shapes.Shapes.block(), net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME), "Each closed corner occupies the complete square footprint");
                for (Direction face : new Direction[] {x == 0 ? Direction.WEST : Direction.EAST, z == 0 ? Direction.NORTH : Direction.SOUTH}) {
                    h.assertTrue(!block.hasShaftTowards(h.getLevel(), absolute, state.setValue(LiddedVesselBlock.AXIS, face.getAxis()), face),
                        "No outer wall exposes a horizontal shaft, including saved horizontal-axis states");
                }
                h.assertTrue(block.hasShaftTowards(h.getLevel(), absolute, state, Direction.DOWN)==false, "No face of the tank takes a shaft; the stirrer does");
            }
        }
        h.succeed();
    }
}
