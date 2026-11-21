package net.createplant.gametest;

import java.util.ArrayList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class DriveLessVesselGameTests {
    private static final BlockPos BASE = new BlockPos(4, 3, 4);

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void closedStainlessTankBurnsSulfurWithOxygenOnAHeatedBurner(GameTestHelper h) {
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
                && !vessel.status().equals("heat"), "A heated burner is enough to burn sulfur in oxygen: " + vessel.status());
            h.setBlock(burnerPos,Blocks.AIR);
            h.setBlock(burnerPos,idleBurner);
            var cake = com.simibubi.create.AllItems.BLAZE_CAKE.asStack();
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(h.getBlockState(burnerPos), level, h.absolutePos(burnerPos), cake, false, false, false);
            h.assertTrue(h.getBlockState(burnerPos).getValue(heat) == com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING,
                "A finite Blaze Cake supplies real superheat");
        });
        h.runAfterDelay(150, () -> {
            var sulfurDioxide = net.createplant.registry.CEUranium.SULFUR_DIOXIDE.get();
            var product = tank.drain(new FluidStack(sulfurDioxide, 500), EXECUTE);
            h.assertTrue(product.getAmount() == 500 && product.is(sulfurDioxide) && vessel.getTankInventory().isEmpty()
                && vessel.itemPort().getStackInSlot(0).isEmpty() && vessel.internal().isEmpty() && vessel.rotationSpeed() == 0,
                "The stock roof port gives one batch of SO2 without an agitator or a rotation source");
            h.succeed();
        });
    }

    private static LiddedVesselBlockEntity vessel(GameTestHelper h) {
        var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 1);
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

    @GameTest(template = "test_reactor", timeoutTicks = 160)
    public static void bottomShaftRunsTheVesselAndCarriesItsStress(GameTestHelper h) {
        var owner = vessel(h);
        var receiver = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1));
        owner.port(Direction.NORTH).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        motor(h, BASE.offset(1, -1, 1), Direction.UP);
        h.runAfterDelay(90, () -> {
            h.assertTrue(owner.formed() && Math.abs(owner.rotationSpeed()) == 128, "A motor below the vessel drives it through Create's kinetic network");
            h.assertTrue(receiver.calculateStressApplied() == 16 && owner.calculateStressApplied() == 0, "Only the central receiver carries the one-bed process load");
            var port = owner.port(Direction.NORTH);
            h.assertTrue(port.getFluidInTank(0).isEmpty() && port.getFluidInTank(2).getAmount() == 1000, "One powered vessel consumes one feed batch and produces exactly one output batch");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void nineBottomMotorsOnlyConnectToTheCentralReceiver(GameTestHelper h) {
        var owner = vessel(h);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) motor(h, BASE.offset(x, -1, z), Direction.UP);
        var block = CETransport.LIDDED_VESSEL.get();
        h.runAfterDelay(45, () -> {
            float stress = 0;
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
                BlockPos pos = BASE.offset(x, 0, z);var part = (LiddedVesselBlockEntity) h.getBlockEntity(pos);boolean centre = x == 1 && z == 1;
                h.assertTrue(block.hasShaftTowards(h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), Direction.DOWN) == centre,
                    "Only the centre of the nine bottom faces exposes a shaft");
                h.assertTrue(Math.abs(part.getSpeed()) == (centre ? 128 : 0), "An actual bottom motor cannot rotate any off-centre block");
                stress += part.calculateStressApplied();
            }
            h.assertTrue(owner.rotationSpeed() == 128 && stress == 16 && owner.calculateStressApplied() == 0, "The central input alone runs and carries the process load");
            h.setBlock(BASE.offset(1, -1, 1), Blocks.AIR);
            owner.port(Direction.NORTH).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        });
        h.runAfterDelay(100, () -> {
            h.assertTrue(owner.rotationSpeed() == 0 && owner.status().equals("speed"), "The eight remaining motors cannot substitute for the disconnected central input");
            h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000 && owner.progress() == 0, "Off-centre motors cannot consume the waiting charge");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void resizingAStorageTankRebuildsTheCentralDriveConnection(GameTestHelper h) {
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) h.setBlock(BASE.offset(x, 0, z), CETransport.LIDDED_VESSEL.get());
        var owner = ((LiddedVesselBlockEntity) h.getBlockEntity(BASE)).getControllerBE();
        owner.getTankInventory().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        owner.itemPort().insertItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 7), false);
        motor(h, BASE.offset(1, -1, 1), Direction.UP);
        h.runAfterDelay(30, () -> {
            h.assertTrue(owner.getWidth() == 2 && ((LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1))).getSpeed() == 0,
                "A motor underneath a 2x2 tank has no rotational connection");
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if (x == 2 || z == 2) h.setBlock(BASE.offset(x, 0, z), CETransport.LIDDED_VESSEL.get());
        });
        h.runAfterDelay(65, () -> {
            h.assertTrue(owner.getWidth() == 3 && ((LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1))).getSpeed() == 128,
                "Expanding to 3x3 reconnects the same motor at the new bottom centre");
            for (int x = 2; x >= 0; x--) for (int z = 2; z >= 0; z--) if (x == 2 || z == 2) h.setBlock(BASE.offset(x, 0, z), Blocks.AIR);
        });
        h.runAfterDelay(110, () -> {
            h.assertTrue(owner.getWidth() == 2 && !owner.canTakeInternal() && !owner.formed() && owner.rotationSpeed() == 0
                && ((LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1))).getSpeed() == 0, "Shrinking to storage disconnects the old central drive");
            h.assertTrue(owner.getTankInventory().getFluidAmount() == 1000 && owner.itemPort().getStackInSlot(0).getCount() == 7,
                "Resizing retains the complete stored water and items");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void savedOffCentreRotationCannotRunAProcessAfterLoading(GameTestHelper h) {
        var owner = vessel(h);var level = h.getLevel();
        owner.port(Direction.NORTH).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        var saved = owner.saveWithoutMetadata(level.registryAccess());
        saved.putLong("PowerInput", owner.getBlockPos().asLong());saved.putFloat("InputSpeed", 128);
        owner.loadWithComponents(saved, level.registryAccess());
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos pos = BASE.offset(x, 0, z);
            h.setBlock(pos, h.getBlockState(pos).setValue(LiddedVesselBlock.DRIVE, false).setValue(LiddedVesselBlock.AXIS, Direction.Axis.X));
            var part = (LiddedVesselBlockEntity) h.getBlockEntity(pos);part.setSpeed(x == 0 && z == 0 ? 128 : 0);part.initialize();
        }
        owner.validateStructure();owner.tickProcess();
        h.assertTrue(owner.formed() && owner.rotationSpeed() == 0 && owner.calculateStressApplied() == 0 && owner.progress() == 0,
            "Loading the old controller's saved speed cannot bypass the input location check");
        h.assertTrue(owner.getTheoreticalSpeed() == 0 && !owner.hasSource() && !owner.hasNetwork(), "The saved off-centre kinetic connection is removed during loading");
        h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "The waiting process charge survives the connection migration");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos pos = BASE.offset(x, 0, z);var state = h.getBlockState(pos);
            h.assertTrue(state.getValue(LiddedVesselBlock.AXIS) == Direction.Axis.Y && state.getValue(LiddedVesselBlock.DRIVE)
                && CETransport.LIDDED_VESSEL.get().hasShaftTowards(level, h.absolutePos(pos), state, Direction.DOWN) == (x == 1 && z == 1),
                "Loading refreshes every saved member and leaves just the central bottom shaft");
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void sideMotorsCannotDriveTheVesselButTheBottomCentreCan(GameTestHelper h) {
        var owner = vessel(h);
        owner.port(Direction.NORTH).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos input = BASE.offset(1, 0, 1).relative(side);
            h.setBlock(input, h.getBlockState(input).setValue(LiddedVesselBlock.AXIS, side.getAxis()));
            motor(h, input.relative(side), side.getOpposite());
        }
        h.runAfterDelay(50, () -> {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                var receiver = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1).relative(side));
                h.assertTrue(receiver.getSpeed() == 0 && receiver.calculateStressApplied() == 0, "A real motor cannot connect to any horizontal wall");
            }
            h.assertTrue(owner.rotationSpeed() == 0 && owner.status().equals("speed") && owner.progress() == 0
                && owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "Side motors cannot run the process or consume its charge");
            motor(h, BASE.offset(1, -1, 1), Direction.UP);
        });
        h.runAfterDelay(110, () -> {
            var receiver = (LiddedVesselBlockEntity) h.getBlockEntity(BASE.offset(1, 0, 1));
            h.assertTrue(Math.abs(owner.rotationSpeed()) == 128 && receiver.calculateStressApplied() == 16, "The bottom centre still powers the vessel and owns its stress");
            h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).isEmpty() && owner.port(Direction.NORTH).getFluidInTank(2).getAmount() == 1000,
                "Only the bottom motor can complete the waiting batch");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void aFilledProcessCannotBeRehomedAndItsGogglesDoNotRecurse(GameTestHelper h) {
        var owner = vessel(h);
        var port = owner.port(Direction.NORTH);
        port.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(BASE.offset(x, -1, z), CETransport.LIDDED_VESSEL.get());
        owner.validateStructure();
        h.assertTrue(owner.isController() && owner.getBlockPos().equals(h.absolutePos(BASE)), "Adding a floor cannot strand a filled process in a non-controller member");
        h.assertTrue(owner.port(Direction.NORTH).getFluidInTank(0).getAmount() == 1000, "The feed remains reachable at its original controller");
        var text = new ArrayList<Component>();
        h.assertTrue(owner.addToGoggleTooltip(text, false) && !text.isEmpty() && text.size() <= 4, "A formed vessel's goggle summary is finite and four lines or fewer");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void anOverstressedCentralReceiverKeepsItsLoad(GameTestHelper h) {
        var owner = vessel(h);
        BlockPos input = BASE.offset(1, 0, 1);
        var receiver = (LiddedVesselBlockEntity) h.getBlockEntity(input);
        receiver.setSpeed(128);
        owner.validateStructure();
        receiver.updateFromNetwork(0, 2048, 1);
        h.assertTrue(receiver.getSpeed() == 0 && owner.rotationSpeed() == 0, "An overloaded network stops both the receiver and the process");
        h.assertTrue(receiver.calculateStressApplied() == 16 && owner.calculateStressApplied() == 0, "A stopped overloaded bottom network retains the process load");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void removingInternalsAndSavingKeepsTheReactionCharge(GameTestHelper h) {
        var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 1);
        owner.itemPort().insertItem(0, net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.toStack(), false);
        var internal = owner.takeTopInternal();
        h.assertTrue(!owner.formed() && owner.getWidth() == 3, "Removing the last internal stops processing while retaining the square storage vessel");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        owner.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(owner.module() == ReactorModule.NONE && owner.hasProcessContents(), "An unconfigured saved vessel still owns its reaction charge");
        owner.putInternal(internal);
        owner.validateStructure();
        h.assertTrue(owner.formed() && owner.itemPort().getStackInSlot(0).is(net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.get()), "Reinstalling the internal makes the saved ingredient reachable again");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void cachedStoragePortsRespectProcessingAndTheOpenHatch(GameTestHelper h) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) h.setBlock(BASE.offset(x, 0, z), CETransport.LIDDED_VESSEL.get());
        var owner = (LiddedVesselBlockEntity) h.getBlockEntity(BASE);
        var port = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.offset(1, 0, 1)), Direction.UP);
        var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        owner.putInternal(net.createplant.registry.CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());
        h.assertTrue(port != null && port.fill(water, EXECUTE) == 0, "Installing an internal immediately closes the storage-fill path before the next structure scan");
        owner.validateStructure();
        h.assertTrue(port != null && owner.formed() && port.fill(water, EXECUTE) == 0 && owner.getTankInventory().isEmpty(), "A cached storage port cannot insert hidden water into an active process");
        owner.takeTopInternal();
        h.assertTrue(port.fill(water, EXECUTE) == 1000, "The storage port works again once the internals are removed");
        owner.toggleLid();
        h.assertTrue(port.fill(water, EXECUTE) == 0 && port.drain(1000, EXECUTE).isEmpty(), "Opening the hatch retires a previously cached top pipe connection");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void everyModuleKeepsSquareCornersWithoutHorizontalShafts(GameTestHelper h) {
        var block = CETransport.LIDDED_VESSEL.get();
        int index = 0;
        for (var module : new ReactorModule[] {ReactorModule.STIRRED}) {
            BlockPos base = BASE.offset((index % 2) * 9, 0, (index / 2) * 9);index++;
            var owner = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(base), module, module.minSections);
            h.assertTrue(owner.formed() && owner.getWidth() == 3, "The internal forms its process at its supported width: " + module);
            int[] corners=new int[]{0,2};
            for (int x : corners) for (int z : corners) {
                BlockPos pos = base.offset(x, 0, z);var state = h.getBlockState(pos);var absolute = h.absolutePos(pos);
                h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(state.getShape(h.getLevel(), absolute),
                    net.minecraft.world.phys.shapes.Shapes.block(), net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME), "Each closed corner occupies the complete square footprint");
                for (Direction face : new Direction[] {x == 0 ? Direction.WEST : Direction.EAST, z == 0 ? Direction.NORTH : Direction.SOUTH}) {
                    h.assertTrue(!block.hasShaftTowards(h.getLevel(), absolute, state.setValue(LiddedVesselBlock.AXIS, face.getAxis()), face),
                        "No outer wall exposes a horizontal shaft, including saved horizontal-axis states");
                }
                h.assertTrue(block.hasShaftTowards(h.getLevel(), absolute, state, Direction.DOWN)==false, "Only the central bottom face accepts a shaft");
            }
        }
        h.succeed();
    }
}
