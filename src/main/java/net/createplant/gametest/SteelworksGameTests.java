package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.TorpedoTankBlockEntity;
import net.createplant.recipe.ContinuousFactoryRecipe;
import net.createplant.registry.CEContinuousFactory;
import net.createplant.registry.CEConverter;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteelworksGameTests {
    @GameTest(template = "test_fractionation", timeoutTicks = 120)
    public static void basinCompactingMixingAndCastingUseRealRecipes(GameTestHelper helper) {
        var ordinary = basin(helper, new BlockPos(3, 4, 5), HeatLevel.KINDLED);
        insert(ordinary, new ItemStack(Items.SAND, 2));
        insert(ordinary, CEItems.QUICKLIME.toStack());
        var bricks = recipe(helper, "compacting/silica_refractory_bricks");
        helper.assertTrue(!BasinRecipe.apply(ordinary, bricks), "Ordinary heat cannot make refractory bricks");
        var brickBasin = basin(helper, new BlockPos(5, 4, 5), HeatLevel.SEETHING);
        insert(brickBasin, new ItemStack(Items.SAND, 2));
        insert(brickBasin, CEItems.QUICKLIME.toStack());
        helper.assertTrue(BasinRecipe.apply(brickBasin, bricks)
            && brickBasin.getOutputInventory().getStackInSlot(0).is(CEConverter.SILICA_BRICKS_ITEM.get())
            && brickBasin.getOutputInventory().getStackInSlot(0).getCount() == 4,
            "The superheated press recipe consumes sand and lime and yields four bricks");

        var meltBasin = basin(helper, new BlockPos(8, 4, 5), HeatLevel.SEETHING);
        insert(meltBasin, CEItems.PIG_IRON.toStack());
        helper.assertTrue(BasinRecipe.apply(meltBasin, recipe(helper, "mixing/melt_pig_iron"))
            && fluid(helper, meltBasin).drain(100, EXECUTE).is(CEProcessFluids.MOLTEN_PIG_IRON.get()),
            "A native basin melts a pig-iron charge into the fluid carried by the train");

        var treating = basin(helper, new BlockPos(11, 4, 5), HeatLevel.KINDLED);
        insert(treating, CEItems.QUICKLIME.toStack());
        fluid(helper, treating).fill(new FluidStack(CEProcessFluids.MOLTEN_PIG_IRON.get(), 800), EXECUTE);
        helper.assertTrue(BasinRecipe.apply(treating, recipe(helper, "mixing/desulfurize_hot_metal"))
            && fluid(helper, treating).drain(800, EXECUTE).is(CEProcessFluids.DESULFURIZED_HOT_METAL.get())
            && treating.getOutputInventory().getStackInSlot(0).is(CEItems.STEEL_SLAG.get()),
            "The heated mixer separates pretreatment slag from the converter feed");

        var casting = basin(helper, new BlockPos(14, 4, 5), HeatLevel.NONE);
        fluid(helper, casting).fill(new FluidStack(CEProcessFluids.MOLTEN_STEEL.get(), 800), EXECUTE);
        helper.assertTrue(BasinRecipe.apply(casting, recipe(helper, "compacting/cast_steel_slab")),
            "The existing mechanical press can run the casting recipe in a basin with no extra heat");
        var cast = casting.getOutputInventory().getStackInSlot(0);
        var rolling = (ContinuousFactoryRecipe) recipe(helper, "continuous_processing/thick_steel_plate");
        helper.assertTrue(cast.is(CEConverter.CAST_STEEL_SLAB.get())
            && rolling.matches(new SingleRecipeInput(cast), helper.getLevel())
            && rolling.result().is(CEContinuousFactory.THICK_STEEL_PLATE.get()), "The cast slab goes directly into the tandem mill");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 80)
    public static void undersizedTorpedoCannotLoadOrAssembleButCanDrain(GameTestHelper helper) {
        var bogey = new BlockPos(7, 3, 7);
        var cell = bogey.above();
        helper.setBlock(bogey, AllBlocks.SMALL_BOGEY.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
        helper.setBlock(cell, CEConverter.TORPEDO_TANK.get().defaultBlockState());
        glue(helper, bogey, cell);
        helper.runAfterDelay(6, () -> {
            var tank = (TorpedoTankBlockEntity) helper.getBlockEntity(cell);
            var molten = new FluidStack(CEProcessFluids.MOLTEN_PIG_IRON.get(), 400);
            var port = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(cell), Direction.UP);
            helper.assertTrue(port != null && !port.isFluidValid(0, molten) && port.fill(molten, SIMULATE) == 0
                && port.fill(molten, EXECUTE) == 0, "A 1×1 torpedo must not accept new cargo");
            tank.getTankInventory().setFluid(molten.copy());
            try {
                new TestCarriage().assemble(helper.getLevel(), helper.absolutePos(bogey));
                helper.fail("A torpedo with one tank cell must fail assembly");
            } catch (AssemblyException expected) {
                helper.assertTrue(port.drain(400, EXECUTE).getAmount() == 400 && tank.getTankInventory().isEmpty(),
                    "Rejecting a small torpedo still lets an existing save recover its old cargo");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 160)
    public static void createCarriageTransfersAndRestoresTheSameMoltenCargo(GameTestHelper helper) {
        var bogey = new BlockPos(7, 3, 7);
        var start = bogey.above().north();
        var end = start.east(4).above(2).south(2);
        helper.setBlock(bogey, AllBlocks.SMALL_BOGEY.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
        var state = CEConverter.TORPEDO_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        for (BlockPos part : BlockPos.betweenClosed(start, end)) helper.setBlock(part, state);
        helper.runAfterDelay(7, () -> {
            var root = (TorpedoTankBlockEntity) helper.getBlockEntity(start);
            var filled = new FluidStack(CEProcessFluids.MOLTEN_PIG_IRON.get(), root.getTankInventory().getCapacity());
            var initial = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(end), Direction.UP);
            helper.assertTrue(root.getWidth() == 3 && root.getHeight() == 5
                && filled.getAmount() == 45 * TorpedoTankBlockEntity.getCapacityMultiplier()
                && initial != null && initial.fill(filled, SIMULATE) == filled.getAmount()
                && root.getTankInventory().isEmpty() && root.steam().isEmpty(), "The formed 3×3×5 tank offers the capacity of all 45 blocks");
            helper.assertTrue(initial.fill(filled, EXECUTE) == filled.getAmount(), "The far tank member loads into the controller");
            var partial = glue(helper, bogey.north(), bogey.above());
            try {
                var attempt = new TestCarriage();
                if (attempt.assemble(helper.getLevel(), helper.absolutePos(bogey)))
                    helper.assertTrue(attempt.getBlocks().containsKey(end.subtract(bogey)), "Create must capture every tank member");
            } catch (AssemblyException expected) {
                helper.assertTrue(root.getTankInventory().getFluidAmount() == filled.getAmount(), "The assembly error leaves stationary cargo untouched");
            }
            partial.discard();
            glue(helper, bogey.north(), end);
            var carriage = new TestCarriage();
            try {
                helper.assertTrue(carriage.assemble(helper.getLevel(), helper.absolutePos(bogey)),
                    "Create assembles the train bogey and the fully glued refractory tank");
            } catch (AssemblyException failure) {
                helper.fail(failure.getMessage());
            }
            carriage.prepareCargo();
            carriage.entity = CarriageContraptionEntity.create(helper.getLevel(), carriage);
            carriage.removeBlocksFromWorld(helper.getLevel(), BlockPos.ZERO);
            var portPos = bogey.north(3);
            helper.setBlock(portPos, AllBlocks.PORTABLE_FLUID_INTERFACE.getDefaultState());
            var port = (PortableFluidInterfaceBlockEntity) helper.getBlockEntity(portPos);
            port.startTransferringTo(carriage, 1);
            helper.runAfterDelay(8, () -> {
                var connected = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(portPos), Direction.NORTH);
                helper.assertTrue(connected != null && connected.getTanks() == 1
                    && connected.drain(100, SIMULATE).getAmount() == 100
                    && connected.drain(100, EXECUTE).getAmount() == 100
                    && connected.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 0,
                    "The stock portable interface exposes one cargo handler and transfers only valid molten metal");

                var registries = helper.getLevel().registryAccess();
                var saved = new CompoundTag();
                carriage.getStorage().write(saved, registries, false);
                var restored = new MountedStorageManager();
                restored.read(saved, registries, false, carriage);
                carriage.returnStorageForDisassembly(restored);
                var translated = helper.absolutePos(bogey.east(7));
                var turn = new StructureTransform(translated, Direction.Axis.Y, Rotation.CLOCKWISE_90, Mirror.NONE);
                carriage.addBlocksToWorld(helper.getLevel(), turn);
                var corner = turn.apply(start.subtract(bogey));
                var farCorner = turn.apply(end.below(2).subtract(bogey));
                var newRootPos = new BlockPos(Math.min(corner.getX(), farCorner.getX()), corner.getY(),
                    Math.min(corner.getZ(), farCorner.getZ()));
                var newRoot = (TorpedoTankBlockEntity) helper.getLevel().getBlockEntity(newRootPos);
                helper.assertTrue(newRoot != null && newRoot.isController()
                    && newRoot.getMainConnectionAxis() == Direction.Axis.Z && newRoot.getWidth() == 3 && newRoot.getHeight() == 5
                    && newRoot.getTankInventory().getFluidAmount() == filled.getAmount() - 100,
                    "Rotating and restoring a serialized carriage keeps the drained amount at the relocated controller");
                helper.succeed();
            });
        });
    }

    private static BasinBlockEntity basin(GameTestHelper helper, BlockPos pos, HeatLevel heat) {
        if (heat != HeatLevel.NONE) burner(helper, pos.below(), heat);
        helper.setBlock(pos, AllBlocks.BASIN.getDefaultState());
        return (BasinBlockEntity) helper.getBlockEntity(pos);
    }

    private static void burner(GameTestHelper helper, BlockPos pos, HeatLevel heat) {
        helper.setBlock(pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
    }

    private static void insert(BasinBlockEntity basin, ItemStack stack) {
        ItemHandlerHelper.insertItemStacked(basin.getInputInventory(), stack, false);
    }

    private static net.neoforged.neoforge.fluids.capability.IFluidHandler fluid(GameTestHelper helper, BasinBlockEntity basin) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, basin.getBlockPos(), null);
    }

    private static net.minecraft.world.item.crafting.Recipe<?> recipe(GameTestHelper helper, String path) {
        return helper.getLevel().getRecipeManager().byKey(CreatePlant.location(path)).orElseThrow().value();
    }

    private static SuperGlueEntity glue(GameTestHelper helper, BlockPos a, BlockPos b) {
        var glue = new SuperGlueEntity(helper.getLevel(), SuperGlueEntity.span(helper.absolutePos(a), helper.absolutePos(b)));
        helper.getLevel().addFreshEntity(glue);
        return glue;
    }

    private static final class TestCarriage extends CarriageContraption {
        private TestCarriage() { super(Direction.EAST); }

        void prepareCargo() {
            storage.initialize();
            storageProxy = storage;
        }
    }
}
