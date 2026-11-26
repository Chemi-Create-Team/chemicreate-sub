package net.createplant.gametest;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipes;
import net.createplant.arm.ArmTool;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagneticMemoryLineGameTests {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos ORIGIN = new BlockPos(8, 4, 8);
    private static final List<Station> STATIONS = List.of(
        new Station(new BlockPos(1, 0, -1), ArmTool.GRIPPER),
        new Station(new BlockPos(1, 0, 3), ArmTool.WIRING_HEAD),
        new Station(new BlockPos(-1, 0, 1), ArmTool.WELDING_TORCH),
        new Station(new BlockPos(3, 0, 1), ArmTool.WELDING_TORCH));

    /** The parts chest beside the wiring arm (an arm only takes material it can reach): 144 ferrite cores and 25 enamelled wires a plane. */
    private static final BlockPos PARTS = new BlockPos(2, 0, 4);

    private MagneticMemoryLineGameTests() { }

    @GameTest(template = "test_forging", timeoutTicks = 1600)
    public static void poweredArmsProduceTwoMemoriesAndTheFanDeliversThemToStorage(GameTestHelper helper) {
        var line = build(helper.getLevel(), helper.absolutePos(ORIGIN));
        helper.assertTrue(!ChestBlock.isChestBlockedAt(line.level(), line.output().getBlockPos()),
            "Players can open the output chest beneath the collector's stop block");
        line.input().setItem(0, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get(), 2));
        line.supplyParts();
        helper.runAfterDelay(10, line::start);
        var completedAt = new long[1];
        helper.onEachTick(() -> {
            if (completedAt[0] == 0 && line.products() == 1 && line.jig().currentStage() == 1) {
                completedAt[0] = helper.getTick();
                helper.assertTrue(line.suppliesEmpty(), "Two sheets were laid and the first plane used all its cores and wires");
                helper.assertTrue(line.jig().status[0] == AssemblyJigBlockEntity.DONE,
                        "The second sheet is laid while the line waits for parts");
                helper.runAfterDelay(65, () -> {
                    helper.assertTrue(line.products() == 1 && line.jig().currentStage() == 1,
                        "Missing cores hold the second batch at core setting");
                    line.supplyParts();
                });
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(completedAt[0] != 0 && line.products() == 2,
                "Both assemblies must reach the real output chest through the fan and hopper");
            helper.assertTrue(line.suppliesEmpty(), "The full run consumes exactly two sheets and two planes' cores and wires");
            helper.assertTrue(line.jig().status.length == 0 || line.jig().status[0] == AssemblyJigBlockEntity.PENDING, "The jig is ready for the next sheet");
            LOGGER.info("Magnetic memory line recovered {} products at tick {} after resupplying copper", line.products(), helper.getTick());
        });
    }

    @GameTest(template = "test_forging", timeoutTicks = 120)
    public static void anUnpoweredArmDoesNotTakeFrameMaterials(GameTestHelper helper) {
        var line = build(helper.getLevel(), helper.absolutePos(ORIGIN));
        for (var arm : line.arms()) line.level().removeBlock(arm.getBlockPos().below(), false);
        line.input().setItem(0, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        helper.runAfterDelay(85, () -> {
            helper.assertTrue(line.input().getItem(0).getCount() == 1
                    && line.arms().stream().allMatch(arm -> arm.getSpeed() == 0
                        && arm.phase == IndustrialArmBlockEntity.IDLE && arm.held.isEmpty()),
                "Without shaft power the arm must leave the new batch in the input chest");
            helper.succeed();
        });
    }

    @GameTest(template = "test_forging", timeoutTicks = 1000)
    public static void paidWiringSurvivesLoadingTheArmAndJig(GameTestHelper helper) {
        var line = build(helper.getLevel(), helper.absolutePos(ORIGIN));
        line.input().setItem(0, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        line.supplyParts();
        helper.runAfterDelay(10, line::start);
        var reloaded = new boolean[1];
        helper.onEachTick(() -> {
            if (reloaded[0]) return;
            var wire = line.arms().get(1);
            if (wire.phase != IndustrialArmBlockEntity.WORK || wire.progress <= .2f) return;
            helper.assertTrue(line.input().isEmpty() && line.parts().getItem(0).isEmpty() && line.parts().getItem(2).isEmpty() && wire.held.is(CEArm.FERRITE_CORE.get()) && wire.held.getCount() == 144, "Core setting already paid for its 144 cores");
            var registries = line.level().registryAccess();
            var jig = line.jig();
            var savedJig = jig.saveWithFullMetadata(registries);
            var savedArm = wire.saveWithFullMetadata(registries);
            line.level().setBlockEntity(BlockEntity.loadStatic(jig.getBlockPos(), jig.getBlockState(), savedJig, registries));
            line.level().setBlockEntity(BlockEntity.loadStatic(wire.getBlockPos(), wire.getBlockState(), savedArm, registries));
            reloaded[0] = true;
        });
        helper.succeedWhen(() -> helper.assertTrue(reloaded[0] && line.suppliesEmpty() && line.products() == 1,
            "The reloaded wiring arm must finish its paid operation without requiring more parts"));
    }

    public static Line build(ServerLevel level, BlockPos origin) {
        place(level, origin, CEArm.JIG.get().defaultBlockState());
        var jig = (AssemblyJigBlockEntity) level.getBlockEntity(origin);
        jig.setRecipe(ArmRecipes.get(CreatePlant.location("magnetic_core_memory")));
        var input = origin.offset(2, 0, -2);
        var output = origin.east(3);
        place(level, input, Blocks.CHEST.defaultBlockState());
        place(level, origin.offset(PARTS), Blocks.CHEST.defaultBlockState()); // within the wiring arm's reach
        place(level, output, Blocks.CHEST.defaultBlockState());
        place(level, origin.east(), Blocks.STONE.defaultBlockState());
        place(level, output.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        place(level, output.above(), Blocks.GLASS.defaultBlockState());
        place(level, output.west().north().above(), Blocks.GLASS.defaultBlockState());
        var fan = origin.west(2).above();
        place(level, fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        drive(level, fan.west(), Direction.EAST);
        for (var station : STATIONS) {
            var pos = origin.offset(station.offset());
            place(level, pos, CEArm.ARM.get().defaultBlockState());
            drive(level, pos.below(), Direction.UP);
            var arm = (IndustrialArmBlockEntity) level.getBlockEntity(pos);
            arm.toolSlot.insertItem(0, CEArm.TOOLS.get(station.tool()).toStack(), false);
        }
        return new Line(level, origin);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    private static void drive(ServerLevel level, BlockPos pos, Direction facing) {
        place(level, pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
    }

    private record Station(BlockPos offset, ArmTool tool) { }

    public record Line(ServerLevel level, BlockPos origin) {
        public AssemblyJigBlockEntity jig() { return (AssemblyJigBlockEntity) level.getBlockEntity(origin); }
        public ChestBlockEntity input() { return (ChestBlockEntity) level.getBlockEntity(origin.offset(2, 0, -2)); }
        public ChestBlockEntity parts() { return (ChestBlockEntity) level.getBlockEntity(origin.offset(PARTS)); }
        public boolean suppliesEmpty() { return input().isEmpty() && parts().isEmpty(); }
        public void supplyParts() {
            parts().setItem(0, new ItemStack(CEArm.FERRITE_CORE.get(), 64));
            parts().setItem(1, new ItemStack(CEArm.FERRITE_CORE.get(), 64));
            parts().setItem(2, new ItemStack(CEArm.FERRITE_CORE.get(), 16));
            parts().setItem(3, new ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25));
        }
        public ChestBlockEntity output() { return (ChestBlockEntity) level.getBlockEntity(origin.east(3)); }
        public EncasedFanBlockEntity fan() { return (EncasedFanBlockEntity) level.getBlockEntity(origin.west(2).above()); }
        public List<IndustrialArmBlockEntity> arms() {
            return STATIONS.stream().map(station -> (IndustrialArmBlockEntity) level.getBlockEntity(origin.offset(station.offset()))).toList();
        }

        public void start() {
            for (var station : STATIONS) setRpm(origin.offset(station.offset()).below(), 64);
            setRpm(origin.west(3).above(), 64);
            if (fan().getAirFlowDirection() != Direction.EAST) setRpm(origin.west(3).above(), -64);
        }

        private void setRpm(BlockPos pos, int speed) {
            ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(speed);
        }

        public int products() {
            int total = 0;
            var chest = output();
            for (int slot = 0; slot < chest.getContainerSize(); slot++)
                if (chest.getItem(slot).is(CEArm.MAGNETIC_CORE_MEMORY.get())) total += chest.getItem(slot).getCount();
            return total;
        }
    }
}
