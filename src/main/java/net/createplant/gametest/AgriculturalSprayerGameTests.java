package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.createplant.CreatePlant;
import net.createplant.agriculture.FertilizedCrops;
import net.createplant.agriculture.FertilizerProfile;
import net.createplant.block.AgriculturalSprayerBlock;
import net.createplant.registry.CEAgriculture;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AgriculturalSprayerGameTests {
    private static final BlockPos SPRAYER = new BlockPos(6, 3, 6);

    @GameTest(template = "test_fractionation")
    public static void stationarySprayerNeedsRedstoneAndStopsAtAWall(GameTestHelper h) {
        place(h, SPRAYER, Direction.EAST);
        for (int i = 1; i <= 4; i++) crop(h, SPRAYER.east(i));
        var wall = SPRAYER.east(2);
        h.setBlock(wall, Blocks.STONE);
        var back = inlet(h, SPRAYER, Direction.WEST);
        var supply = new FluidStack(CEProcessFluids.LIQUID_FERTILIZER.get(), 60);
        h.assertTrue(inlet(h, SPRAYER, Direction.EAST) == null && inlet(h, SPRAYER, Direction.UP) == null
            && FluidPipeBlock.canConnectTo(h.getLevel(), h.absolutePos(SPRAYER.west()), AllBlocks.FLUID_PIPE.getDefaultState(), Direction.EAST),
            "Only the rear connects to a Create fluid pipe");
        h.assertTrue(back.fill(supply, SIMULATE) == 60 && back.getFluidInTank(0).isEmpty()
            && back.fill(supply, EXECUTE) == 60 && back.drain(1, SIMULATE).getAmount() == 1 && amount(back) == 60,
            "The rear simulates both filling and draining cleanly");
        h.runAfterDelay(12, () -> {
            h.assertTrue(age(h, SPRAYER.east()) == 0 && amount(back) == 60, "Unpowered sprayer is idle");
            h.setBlock(SPRAYER.south(), Blocks.REDSTONE_BLOCK);
            h.runAfterDelay(12, () -> {
                h.assertTrue(age(h, SPRAYER.east()) == 1 && age(h, SPRAYER.east(3)) == 0 && amount(back) == 40,
                    "A wall stops the nozzle; only one plant consumes 20 mB");
                crop(h, wall);
                h.runAfterDelay(12, () -> {
                    h.assertTrue(age(h, wall) == 1 && age(h, SPRAYER.east(3)) == 1
                        && age(h, SPRAYER.east(4)) == 0 && amount(back) == 0,
                        "The first three positions qualify, with no double charge or reach past the third");
                    place(h, SPRAYER, Direction.NORTH);
                    h.assertTrue(back.fill(supply, EXECUTE) == 0 && inlet(h, SPRAYER, Direction.SOUTH) != null,
                        "Changing facing closes the old rear connection");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "test_fractionation")
    public static void dilutedProtectantAddsOneHarvestAndKeepsFertilizerIndependent(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos basinPos = new BlockPos(3, 3, 3);
        h.setBlock(basinPos, AllBlocks.BASIN.get());
        var basin = (BasinBlockEntity) h.getBlockEntity(basinPos);
        basin.tick();
        basin.getInputInventory().insertItem(0, new ItemStack(Items.GLOW_BERRIES), false);
        basin.getInputInventory().insertItem(1, new ItemStack(Items.SLIME_BALL), false);
        var input = basin.inputTank.getCapability();
        var output = basin.getTanks().getSecond().getCapability();
        input.fill(new FluidStack(Fluids.WATER, 250), EXECUTE);
        mix(h, basin, "crop_protectant");
        var concentrate = output.drain(250, EXECUTE);
        h.assertTrue(concentrate.is(CEProcessFluids.CROP_PROTECTANT.get()), "The mixer makes the fictional concentrate");
        place(h, SPRAYER, Direction.EAST);
        var rear = inlet(h, SPRAYER, Direction.WEST);
        h.assertTrue(rear.fill(concentrate, EXECUTE) == 0, "The sprayer accepts diluted liquid");
        input.fill(concentrate, EXECUTE);
        input.fill(new FluidStack(Fluids.WATER, 750), EXECUTE);
        mix(h, basin, "diluted_pesticide");
        var liquid = output.drain(1000, EXECUTE);
        h.assertTrue(liquid.is(CEProcessFluids.DILUTED_PESTICIDE.get()) && liquid.getAmount() == 1000,
            "Native Create mixing outputs the diluted formulation");

        var crop = SPRAYER.east();
        crop(h, crop);
        var other = crop.east();
        crop(h, other);
        var absolute = h.absolutePos(crop);
        h.assertTrue(FertilizedCrops.apply(level, absolute, FertilizerProfile.NPK),
            "The crop already has its existing fertilizer treatment");
        rear.fill(liquid.copyWithAmount(29), EXECUTE);
        h.setBlock(SPRAYER.south(), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(22, () -> {
            h.assertTrue(amount(rear) == 14 && age(h, crop) == 1 && age(h, other) == 0,
                "One full dose protects without growth; repeat or partial doses cost nothing");
            var mature = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
            h.setBlock(crop, mature);
            var normal = List.of(new ItemStack(Items.WHEAT));
            int bonus = FertilizedCrops.harvest(level, absolute, mature, normal).getCount();
            h.assertTrue(bonus >= 3 && bonus <= 4 && FertilizedCrops.harvest(level, absolute, mature, normal).isEmpty(),
                "Protection adds one to the NPK bonus exactly once");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void aNativeBearingRotatesTheSprayAndReturnsTheRemainingTank(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bearingPos = new BlockPos(8, 3, 8);
        var pivot = bearingPos.above();
        var tank = pivot.east();
        var sprayer = tank.east();
        h.setBlock(bearingPos, AllBlocks.MECHANICAL_BEARING.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        h.setBlock(pivot, AllBlocks.ANDESITE_CASING.get());
        mountTank(h, tank, CEProcessFluids.LIQUID_FERTILIZER.get(), 100);
        place(h, sprayer, Direction.EAST);
        glue(h, pivot, sprayer);
        var before = sprayer.east();
        crop(h, before);
        var north = pivot.north(3);
        var south = pivot.south(3);
        crop(h, north);
        crop(h, south);
        var bearing = (MechanicalBearingBlockEntity) h.getBlockEntity(bearingPos);
        bearing.assemble();
        var entity = bearing.getMovedContraption();
        h.assertTrue(entity != null, "The actual mechanical bearing assembles the glued blocks");
        var mounted = entity.getContraption().getStorage().getFluids();
        h.assertTrue(mounted.getTanks() == 1 && amount(mounted) == 100
            && entity.getContraption().getActorAt(new BlockPos(2, 0, 0)) != null,
            "Create mounted the fluid tank and discovered the sprayer actor");
        entity.tickActors();
        h.assertTrue(amount(mounted) == 100 && age(h, before) == 0, "Initial stationary actor ticks do not spray");

        bearing.setAngle(90);
        entity.setAngle(90);
        entity.tickActors();
        h.assertTrue(amount(mounted) == 80 && age(h, north) + age(h, south) == 1 && age(h, before) == 0,
            "The actual bearing rotation transforms the nozzle from east to the field's north/south axis");
        entity.setAngle(0);
        bearing.setAngle(0);
        bearing.disassemble();
        var returned = (FluidTankBlockEntity) h.getBlockEntity(tank);
        h.assertTrue(returned != null && returned.getTankInventory().getFluidAmount() == 80,
            "Create returns the depleted mounted tank when disassembled");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aGantrySpraysAcrossTheRowsWithoutTreatingItsTravelDirection(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        BlockPos carriage = new BlockPos(7, 3, 8);
        var base = h.absolutePos(carriage);
        h.setBlock(carriage, AllBlocks.GANTRY_CARRIAGE.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        var tank = carriage.north();
        mountTank(h, tank, CEProcessFluids.LIQUID_FERTILIZER.get(), 100);
        place(h, carriage.north(2), Direction.NORTH);
        glue(h, carriage, carriage.north(2));
        var target = carriage.east().north(3);
        crop(h, target);
        var wrong = carriage.east(2).north(2);
        crop(h, wrong);
        var moving = new GantryContraption(Direction.UP);
        h.assertTrue(moving.assemble(level, base) && moving.getActorAt(new BlockPos(0, 0, -2)) != null,
            "The native Gantry Contraption finds the sprayer actor");
        moving.removeBlocksFromWorld(level, BlockPos.ZERO);
        var entity = GantryContraptionEntity.create(level, moving, Direction.EAST);
        entity.setPos(Vec3.atLowerCornerOf(base));
        entity.tickActors();
        entity.setPos(Vec3.atLowerCornerOf(base.east()));
        entity.tickActors();
        h.assertTrue(age(h, target) == 1 && age(h, wrong) == 0 && amount(moving.getStorage().getFluids()) == 80,
            "The nozzle points across the rows, using the tank attached to the carriage");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void anAssembledMinecartUsesItsOwnMountedTank(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        BlockPos assembler = new BlockPos(8, 2, 8);
        var base = h.absolutePos(assembler);
        h.setBlock(assembler.below(), Blocks.STONE);
        h.setBlock(assembler, AllBlocks.CART_ASSEMBLER.getDefaultState().setValue(CartAssemblerBlock.RAIL_SHAPE, RailShape.EAST_WEST));
        h.setBlock(assembler.above(), AllBlocks.ANDESITE_CASING.get());
        mountTank(h, assembler.above().north(), CEProcessFluids.LIQUID_FERTILIZER.get(), 100);
        place(h, assembler.above().north(2), Direction.NORTH);
        glue(h, assembler.above(), assembler.above().north(2));
        var moving = new MountedContraption();
        h.assertTrue(moving.assemble(level, base) && moving.getActorAt(new BlockPos(0, 1, -2)) != null,
            "The native cart assembler structure includes the spray nozzle");
        moving.removeBlocksFromWorld(level, BlockPos.ZERO);
        moving.startMoving(level);
        var cart = new Minecart(level, base.getX() + .5, base.getY(), base.getZ() + .5);
        var entity = OrientedContraptionEntity.create(level, moving, Direction.EAST);
        level.addFreshEntity(cart);
        entity.setPos(cart.position());
        h.assertTrue(entity.startRiding(cart, true), "The contraption rides a vanilla minecart");
        entity.tickActors();
        cart.setPos(cart.position().add(1, 0, 0));
        entity.setPos(cart.position());
        var target = assembler.above().east().north(3);
        crop(h, target);
        entity.tickActors();
        h.assertTrue(age(h, target) == 1 && amount(moving.getStorage().getFluids()) == 80,
            "A real minecart carries the actor and its own fluid supply; age " + age(h, target)
                + ", mounted " + amount(moving.getStorage().getFluids()));
        cart.discard();
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aStandardCreatePumpSuppliesTheStationaryNozzle(GameTestHelper h) {
        place(h, SPRAYER, Direction.EAST);
        var pump = SPRAYER.west();
        var tank = SPRAYER.west(2);
        var cog = pump.north();
        var motor = cog.east();
        var target = SPRAYER.east();
        mountTank(h, tank, CEProcessFluids.LIQUID_FERTILIZER.get(), 100);
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        h.setBlock(SPRAYER.south(), Blocks.REDSTONE_BLOCK);
        crop(h, target);
        h.runAfterDelay(4, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.succeedWhen(() -> {
            h.assertTrue(age(h, target) == 1, "A live Create pump supplies enough fertilizer for the sprayer");
            int held = amount(inlet(h, SPRAYER, Direction.WEST));
            int stored = ((FluidTankBlockEntity) h.getBlockEntity(tank)).getTankInventory().getFluidAmount();
            h.assertTrue(held + stored == 80, "Native piping and application debit exactly one dose");
        });
    }

    private static void place(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, CEAgriculture.SPRAYER.get().defaultBlockState().setValue(AgriculturalSprayerBlock.FACING, facing));
    }

    private static IFluidHandler inlet(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static int amount(IFluidHandler supply) {
        return supply.getFluidInTank(0).getAmount();
    }

    private static void crop(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(pos, Blocks.WHEAT);
    }

    private static int age(GameTestHelper h, BlockPos pos) {
        return h.getBlockState(pos).getValue(CropBlock.AGE);
    }

    private static void mountTank(GameTestHelper h, BlockPos pos, Fluid fluid, int amount) {
        h.setBlock(pos, AllBlocks.FLUID_TANK.get());
        var tank = (FluidTankBlockEntity) h.getBlockEntity(pos);
        tank.getTankInventory().setFluid(new FluidStack(fluid, amount));
    }

    private static void glue(GameTestHelper h, BlockPos first, BlockPos last) {
        var volume = new AABB(h.absolutePos(first)).minmax(new AABB(h.absolutePos(last)));
        var glue = new SuperGlueEntity(h.getLevel(), volume);
        h.assertTrue(h.getLevel().addFreshEntity(glue), "The game accepts the fixture's Create glue");
        h.assertTrue(glue.contains(h.absolutePos(first)) && glue.contains(h.absolutePos(last)), "Glue covers the fixture");
        var adjacent = last.subtract(first);
        var direction = Direction.getNearest(adjacent.getX(), adjacent.getY(), adjacent.getZ());
        h.assertTrue(SuperGlueEntity.isGlued(h.getLevel(), h.absolutePos(first), direction, null),
            "Create discovers its glue between the first two blocks");
    }

    private static void mix(GameTestHelper h, BasinBlockEntity basin, String recipe) {
        var holder = h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/" + recipe)).orElseThrow();
        h.assertTrue(BasinRecipe.apply(basin, holder.value()), "Native mixer runs " + recipe);
    }
}
