package net.createplant.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.createplant.CreatePlant;
import net.createplant.block.ContinuousFactoryBlock;
import net.createplant.block.FactoryPortBlock;
import net.createplant.block.FactoryStageBlock;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.client.PlaytestPlant;
import net.createplant.factory.FactoryLayout;
import net.createplant.registry.CEContinuousFactory;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StainlessCastingGameTests {
    @GameTest(template = "test_fractionation", timeoutTicks = 1100)
    public static void stainlessMixingCastingAndRollingRunEndToEnd(GameTestHelper helper) {
        var level = helper.getLevel();
        var line = build(level, helper.absolutePos(new BlockPos(5, 6, 9)), 4);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(Math.abs(line.motorSpeed(line.mixer().above(2))) >= 32 && line.motorSpeed(line.caster().above(2)) != 0
                && line.motorSpeed(line.pump()) != 0 && line.mill().formedLength() == 7
                && line.finished() == 0 && line.feed().countItem(Items.IRON_INGOT) == 24,
                "The connected machines are powered and the locked feed retains all four batches");
            line.start();
        });
        boolean[] sawLiquid = {false};
        helper.onEachTick(() -> sawLiquid[0] |= line.casterVolume() > 0);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(line.finished() == 24,
                "Wait for 24 ingots, currently " + line.finished() + " with " + line.casterVolume() + " mB at the caster and mill " + line.mill().status()))
            .thenExecute(() -> {
                helper.assertTrue(sawLiquid[0] && line.feed().isEmpty() && line.mixerVolume() == 0 && line.casterVolume() == 0,
                    "All four batches move through the caster as a liquid and spend exactly 2,400 mB");
                line.plant().requireNoWear();
            }).thenSucceed();
    }

    public static Line build(ServerLevel level, BlockPos mixer, int batches) {
        var plant = new PlaytestPlant(level, mixer.getY() - 3);
        BlockPos caster = mixer.east(6);
        BlockPos feeder = mixer.north();
        BlockPos input = feeder.above();
        BlockPos lever = feeder.west();
        var mill = new FactoryLayout(caster.below().east(), Direction.EAST, 7, List.of());
        BlockPos output = mill.outlet().below(2);
        var basin = AllBlocks.BASIN.getDefaultState().setValue(BasinBlock.FACING, Direction.DOWN);
        plant.put(mixer, basin);
        plant.put(caster, basin);
        plant.burner(mixer.below(), HeatLevel.SEETHING);
        plant.put(mixer.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        var mixerGear = mixer.above(2).south();
        plant.put(mixerGear, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        plant.motor(mixerGear.above(), Direction.DOWN, 128);
        plant.put(caster.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        plant.motor(caster.above(2).east(), Direction.WEST, 128);

        plant.put(caster.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        plant.put(mill.origin(), CEContinuousFactory.MILL.get().defaultBlockState().setValue(ContinuousFactoryBlock.FACING, mill.forward()));
        plant.put(mill.outlet(), CEContinuousFactory.OUTLET.get().defaultBlockState().setValue(FactoryPortBlock.FACING, mill.forward()));
        plant.burner(mill.origin().below(), HeatLevel.KINDLED);
        Direction drive = mill.forward().getCounterClockWise();
        for (int along : new int[] {2, 5}) {
            for (int across : new int[] {-2, 2}) for (int y = 0; y < 4; y++)
                if (across != 2 || y != 1) plant.put(mill.at(across, y, along), CEContinuousFactory.FRAME.get().defaultBlockState());
            for (int across = -1; across <= 1; across++)
                plant.put(mill.at(across, 3, along), CEContinuousFactory.FRAME.get().defaultBlockState());
            plant.put(mill.at(2, 1, along), CEContinuousFactory.STAND.get().defaultBlockState().setValue(FactoryStageBlock.FACING, drive));
            plant.motor(mill.at(3, 1, along), drive.getOpposite(), along == 2 ? 30 : 40);
        }

        plant.put(output, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        plant.put(mill.outlet().below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        plant.put(lever.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        plant.put(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
            .setValue(LeverBlock.POWERED, true));
        plant.put(feeder, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH).setValue(HopperBlock.ENABLED, false));
        plant.put(input, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));

        plant.route(Direction.EAST, mixer.east(), List.of(caster.west()), Direction.EAST, 2);
        plant.driveAll();
        var line = new Line(level, plant, mixer, caster, input, lever, output, plant.PUMPS.getFirst(), mill.origin());
        line.stock(batches);
        return line;
    }

    public record Line(ServerLevel level, PlaytestPlant plant, BlockPos mixer, BlockPos caster,
                       BlockPos input, BlockPos lever, BlockPos output, BlockPos pump, BlockPos rolling) {
        public void stock(int batches) {
            edit(input, inventory -> {
                inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 6 * batches));
                inventory.setItem(1, CEItems.FERROCHROME.toStack(2 * batches));
                inventory.setItem(2, CEItems.NICKEL_INGOT.toStack(batches));
            });
        }

        public void start() {
            level.setBlock(lever, level.getBlockState(lever).setValue(LeverBlock.POWERED, false), Block.UPDATE_ALL);
        }

        public Container feed() { return (Container) level.getBlockEntity(input); }
        public Container products() { return (Container) level.getBlockEntity(output); }
        public int finished() { return products().countItem(CEItems.STAINLESS_STEEL_INGOT.get()); }
        public int mixerVolume() { return plant.amount(mixer, Direction.EAST, CEProcessFluids.MOLTEN_STAINLESS_STEEL.get()); }
        public int casterVolume() { return plant.amount(caster, Direction.WEST, CEProcessFluids.MOLTEN_STAINLESS_STEEL.get()); }
        public ContinuousFactoryBlockEntity mill() { return (ContinuousFactoryBlockEntity) level.getBlockEntity(rolling); }

        float motorSpeed(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
        }

        private void edit(BlockPos pos, Consumer<Container> edit) {
            var inventory = (Container) level.getBlockEntity(pos);
            edit.accept(inventory);
            inventory.setChanged();
        }
    }
}
