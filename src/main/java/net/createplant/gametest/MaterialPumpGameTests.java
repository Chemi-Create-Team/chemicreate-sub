package net.createplant.gametest;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createmod.catnip.math.BlockFace;
import net.createplant.CreatePlant;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.cloud.GasClouds;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEGasPipes;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialPumpGameTests {
    private record Line(FluidTankBlockEntity source, FluidTankBlockEntity destination,
                        PumpBlockEntity pump, CreativeMotorBlockEntity motor) { }

    private static Line line(GameTestHelper h, int z, Block pump) { return line(h, z, pump, false); }
    private static Line line(GameTestHelper h, int z, Block pump, boolean gas) {
        var source = tank(h, new BlockPos(1, 2, z));
        var destination = tank(h, new BlockPos(7, 2, z));
        if (gas) {
            h.setBlock(new BlockPos(7, 2, z), CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS, Direction.Axis.X));
            destination = (FluidTankBlockEntity) h.getBlockEntity(new BlockPos(7, 2, z));
        }
        String id = BuiltInRegistries.BLOCK.getKey(pump).getPath().replace("_pump", "_pipe");
        Block pipe = BuiltInRegistries.BLOCK.get(CreatePlant.location(id));
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 2, z), pipe.defaultBlockState()
            .setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true)
                .setValue(FluidPipeBlock.NORTH, false).setValue(FluidPipeBlock.SOUTH, false)
                .setValue(FluidPipeBlock.UP, false).setValue(FluidPipeBlock.DOWN, false));
        BlockPos at = new BlockPos(3, 2, z);
        h.setBlock(at, pump.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        h.setBlock(at.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(at.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        var motor = (CreativeMotorBlockEntity) h.getBlockEntity(at.south().west());
        motor.generatedSpeed.setValue(0);
        return new Line(source, destination, (PumpBlockEntity) h.getBlockEntity(at), motor);
    }

    private static FluidTankBlockEntity tank(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, CETransport.PRESSURE_TANK.get());
        return (FluidTankBlockEntity) h.getBlockEntity(pos);
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void copperPipeDischargesSteamFromAnExposedOutput(GameTestHelper h) {
        ventSteam(h, true);
    }

    @GameTest(template = "test_reactor", timeoutTicks = 180)
    public static void mechanicalPumpCanDischargeSteamDirectly(GameTestHelper h) {
        ventSteam(h, false);
    }

    private static void ventSteam(GameTestHelper h, boolean copperPipe) {
        var pump = new BlockPos(2, 2, 3);
        var outlet = pump.east(copperPipe ? 2 : 1);
        h.setBlock(outlet, Blocks.STONE);
        if (copperPipe) {
            for (var side : Direction.values())
                if (side.getAxis() != Direction.Axis.X) h.setBlock(pump.east().relative(side), Blocks.STONE);
        }
        h.setBlock(pump.west(), CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X));
        var source = (FluidTankBlockEntity) h.getBlockEntity(pump.west());
        h.assertTrue(source.getTankInventory().fill(new FluidStack(CEFluids.STEAM.get(), 500), EXECUTE) == 500, "The source receiver accepts its steam charge");
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(pump.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        var motor = (CreativeMotorBlockEntity) h.getBlockEntity(pump.south().west());
        motor.generatedSpeed.setValue(64);
        if (copperPipe) h.setBlock(pump.east(), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true));
        var worldOutlet = h.absolutePos(outlet);
        h.runAfterDelay(25, () -> {
            h.assertTrue(source.getTankInventory().getFluidAmount() == 500 && GasClouds.in(h.getLevel(), new AABB(worldOutlet)).isEmpty(),
                "A blocked outlet holds the charge, remaining=" + source.getTankInventory().getFluidAmount());
            h.setBlock(outlet, Blocks.AIR);
            var end = new OpenEndedPipe(new BlockFace(worldOutlet.west(), Direction.EAST));
            end.manageSource(h.getLevel(), null);
            var handler = end.provideHandler().getCapability();
            h.assertTrue(handler.fill(new FluidStack(CEFluids.STEAM.get(), 500), SIMULATE) == 500
                && handler.getFluidInTank(0).isEmpty() && GasClouds.in(h.getLevel(), new AABB(worldOutlet)).isEmpty(),
                "Simulating a steam outlet produces no clouds or inventory changes");
        });
        h.succeedWhen(() -> {
            var clouds = GasClouds.in(h.getLevel(), new AABB(worldOutlet));
            h.assertTrue(source.getTankInventory().isEmpty() && h.getLevel().getBlockState(worldOutlet).isAir()
                && clouds.stream().anyMatch(cloud -> cloud.blockPosition().equals(worldOutlet) && cloud.content().is(CEFluids.STEAM.get()) && cloud.amount() <= 500),
                "The native pump vents finite steam at the air block without placing a fluid source, remaining=" + source.getTankInventory().getFluidAmount());
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 300)
    public static void everyMaterialPumpUsesNativeGearsAndConservesWater(GameTestHelper h) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < CETransport.MATERIAL_PUMPS.size(); i++) {
            Line line = line(h, 2 + i * 3, CETransport.MATERIAL_PUMPS.get(i).get());
            line.source.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), EXECUTE);
            lines.add(line);
        }
        h.runAfterDelay(25, () -> {
            for (Line line : lines) {
                h.assertTrue(line.destination.getTankInventory().isEmpty(), "A stopped pump never transfers passively");
                line.motor.generatedSpeed.setValue(64);
            }
        });
        h.runAfterDelay(230, () -> {
            for (Line line : lines) {
                h.assertTrue(line.pump.getSpeed() != 0, "Adjacent gears drive each material pump");
                h.assertTrue(line.source.getTankInventory().isEmpty() && line.destination.getTankInventory().getFluidAmount() == 1000,
                    "Every pump moves the whole 1000 mB charge without loss: " + line.pump.getBlockState());
                h.assertTrue(line.pump.calculateStressApplied() > 0, "Material pumps retain native stress consumption");
            }
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 500)
    public static void wrenchReversesAndReconnectingResumesTheSameCharge(GameTestHelper h) {
        Line line = line(h, 3, CETransport.MATERIAL_PUMPS.get(4).get());
        line.source.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), EXECUTE);
        line.motor.generatedSpeed.setValue(64);
        h.runAfterDelay(180, () -> {
            h.assertTrue(line.destination.getTankInventory().getFluidAmount() == 1000, "Forward transfer completed");
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            var pos = line.pump.getBlockPos();
            ((PumpBlock) line.pump.getBlockState().getBlock()).onWrenched(line.pump.getBlockState(),
                new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            h.assertTrue(line.pump.getBlockState().getValue(PumpBlock.FACING) == Direction.WEST, "The native wrench flips the pump");
            h.setBlock(new BlockPos(5, 2, 3), Blocks.AIR);
        });
        h.runAfterDelay(230, () -> {
            h.assertTrue(line.destination.getTankInventory().getFluidAmount() == 1000, "A disconnected intake cannot drain its tank");
            h.setBlock(new BlockPos(5, 2, 3), CETransport.STAINLESS_PIPE.get().defaultBlockState()
                .setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true)
                .setValue(FluidPipeBlock.NORTH, false).setValue(FluidPipeBlock.SOUTH, false)
                .setValue(FluidPipeBlock.UP, false).setValue(FluidPipeBlock.DOWN, false));
        });
        h.runAfterDelay(450, () -> {
            h.assertTrue(line.source.getTankInventory().getFluidAmount() == 1000 && line.destination.getTankInventory().isEmpty(),
                "Reconnection resumes reverse transfer without duplicating contents: source=" + line.source.getTankInventory().getFluidAmount()
                        + " destination=" + line.destination.getTankInventory().getFluidAmount() + " speed=" + line.pump.getSpeed()
                        + " facing=" + line.pump.getBlockState().getValue(PumpBlock.FACING));
            var state = line.pump.getBlockState();
            var lookup = h.getLevel().registryAccess();
            var saved = line.pump.saveWithoutMetadata(lookup);
            BlockPos relative = h.relativePos(line.pump.getBlockPos());
            h.setBlock(relative, Blocks.AIR);
            h.setBlock(relative, state);
            ((PumpBlockEntity) h.getBlockEntity(relative)).loadWithComponents(saved, lookup);
        });
        h.runAfterDelay(480, () -> {
            var restored = (PumpBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 3));
            h.assertTrue(restored.getBlockState().getValue(PumpBlock.FACING) == Direction.WEST && restored.getSpeed() != 0,
                "A recreated block entity restores facing and rejoins its kinetic network");
            h.assertTrue(line.source.getTankInventory().getFluidAmount() + line.destination.getTankInventory().getFluidAmount() == 1000,
                "Reloading the pump creates no additional fluid inventory");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 250)
    public static void atmosphericAirNeedsAnExposedFilterAndAWorkingPump(GameTestHelper h) {
        Line line = line(h, 3, BuiltInRegistries.BLOCK.get(CreatePlant.location("gas_pump")), true);
        var filterPos = new BlockPos(1, 2, 3);
        h.setBlock(filterPos, Blocks.AIR);
        var open = new OpenEndedPipe(new BlockFace(h.absolutePos(filterPos.east()), Direction.WEST));
        open.manageSource(h.getLevel(), null);
        var outlet = open.provideHandler().getCapability();
        h.assertTrue(outlet.drain(125, SIMULATE).isEmpty() && outlet.drain(125, EXECUTE).isEmpty(), "An open gas pipe no longer draws atmospheric air");
        h.assertTrue(outlet.fill(new FluidStack(CEFluids.AIR_GAS.get(), 1000), EXECUTE) == 1000
            && h.getBlockState(filterPos).isAir(), "Exhausted air still disperses into the world");
        h.setBlock(filterPos, CEGasPipes.AIR_FILTER.get());
        var capability = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK;
        var filter = h.getLevel().getCapability(capability, h.absolutePos(filterPos), Direction.EAST);
        h.assertTrue(filter != null && filter.drain(125, SIMULATE).is(CEFluids.AIR_GAS.get())
            && filter.drain(2000, EXECUTE).getAmount() == 1000 && filter.drain(new FluidStack(Fluids.WATER, 100), EXECUTE).isEmpty(),
            "The filter exposes only ambient air and respects the per-draw cap");
        for (var side : Direction.values()) if (side != Direction.EAST) h.setBlock(filterPos.relative(side), Blocks.STONE);
        h.assertTrue(filter.drain(125, SIMULATE).isEmpty(), "A covered filter cannot collect atmosphere");
        h.setBlock(filterPos.above(), Blocks.AIR);
        h.runAfterDelay(25, () -> {
            h.assertTrue(line.destination.getTankInventory().isEmpty(), "A fitted filter needs a powered pump");
            line.motor.generatedSpeed.setValue(64);
        });
        int[] first = new int[1];
        h.runAfterDelay(115, () -> {
            h.assertTrue(line.destination.getTankInventory().getFluid().is(CEFluids.AIR_GAS.get()), "The real pump draws air through a pipe connected to the filter");
            first[0] = line.destination.getTankInventory().getFluidAmount();
            h.setBlock(filterPos, Blocks.AIR);
            h.assertTrue(filter.drain(1, EXECUTE).isEmpty(), "The cached atmospheric capability expires when the filter is removed");
            h.setBlock(filterPos.east(), CEGasPipes.AIR_FILTER.get());
        });
        h.runAfterDelay(220, () -> {
            h.assertTrue(line.destination.getTankInventory().getFluidAmount() > first[0], "The filter also supplies air when placed directly at the pump");
            h.succeed();
        });
    }
}
