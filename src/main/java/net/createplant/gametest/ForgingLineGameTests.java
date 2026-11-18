package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;

import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
@ExplicitGameTest
public class ForgingLineGameTests {
    @GameTest(template = "test_forging", timeoutTicks = 12000)
    public static void nativeBeltLineRunsAllPressesAndFansIntoAChest(GameTestHelper h) {
        var shaft =
                AllBlocks.SHAFT
                        .getDefaultState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties
                                        .BlockStateProperties.AXIS,
                                net.minecraft.core.Direction.Axis.Z);
        h.setBlock(new BlockPos(1, 1, 5), shaft);
        h.setBlock(new BlockPos(17, 1, 5), shaft);
        com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(
                h.getLevel(),
                h.absolutePos(new BlockPos(1, 1, 5)),
                h.absolutePos(new BlockPos(17, 1, 5)));
        var beltMotor = new BlockPos(1, 1, 6);
        h.setBlock(
                beltMotor,
                AllBlocks.CREATIVE_MOTOR
                        .getDefaultState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties
                                        .BlockStateProperties.FACING,
                                net.minecraft.core.Direction.NORTH));
        for (int x : new int[] {2, 6, 7, 11, 12, 16}) {
            h.setBlock(new BlockPos(x, 3, 5), AllBlocks.MECHANICAL_PRESS.get());
            h.setBlock(
                    new BlockPos(x, 3, 6),
                    AllBlocks.CREATIVE_MOTOR
                            .getDefaultState()
                            .setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.FACING,
                                    net.minecraft.core.Direction.NORTH));
        }
        for (int x : new int[] {4, 9, 14}) {
            h.setBlock(
                    new BlockPos(x, 2, 1),
                    AllBlocks.ENCASED_FAN
                            .getDefaultState()
                            .setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.FACING,
                                    net.minecraft.core.Direction.SOUTH));
            h.setBlock(
                    new BlockPos(x, 2, 0),
                    AllBlocks.CREATIVE_MOTOR
                            .getDefaultState()
                            .setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.FACING,
                                    net.minecraft.core.Direction.SOUTH));
            h.setBlock(new BlockPos(x, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(new BlockPos(x, 2, 2), net.minecraft.world.level.block.Blocks.LAVA);
            h.setBlock(new BlockPos(x, 2, 3), net.minecraft.world.level.block.Blocks.IRON_BARS);
            h.setBlock(new BlockPos(x - 1, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(new BlockPos(x + 1, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
        }
        h.setBlock(new BlockPos(1, 3, 5), net.minecraft.world.level.block.Blocks.CHEST);
        h.setBlock(
                new BlockPos(1, 2, 5),
                AllBlocks.ANDESITE_FUNNEL
                        .getDefaultState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties
                                        .BlockStateProperties.FACING,
                                net.minecraft.core.Direction.DOWN)
                        .setValue(
                                com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING,
                                true));
        h.setBlock(new BlockPos(18, 2, 5), net.minecraft.world.level.block.Blocks.CHEST);
        var outlet = new BlockPos(17, 2, 5);
        var mouth =
                AllBlocks.ANDESITE_FUNNEL
                        .getDefaultState()
                        .setValue(
                                net.minecraft.world.level.block.state.properties
                                        .BlockStateProperties.FACING,
                                net.minecraft.core.Direction.WEST)
                        .setValue(
                                com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING,
                                false);
        h.setBlock(
                outlet,
                ((com.simibubi.create.content.logistics.funnel.FunnelBlock) mouth.getBlock())
                        .getEquivalentBeltFunnel(h.getLevel(), h.absolutePos(outlet), mouth));
        h.runAfterDelay(
                5,
                () -> {
                    motor(h, beltMotor).generatedSpeed.setValue(1);
                    for (int x : new int[] {2, 6, 7, 11, 12, 16})
                        motor(h, new BlockPos(x, 3, 6)).generatedSpeed.setValue(128);
                    for (int x : new int[] {4, 9, 14})
                        motor(h, new BlockPos(x, 2, 0)).generatedSpeed.setValue(256);
                });
        h.runAfterDelay(
                20,
                () -> {
                    var belt =
                            (com.simibubi.create.content.kinetics.belt.BeltBlockEntity)
                                    h.getBlockEntity(new BlockPos(1, 1, 5));
                    if (belt.getMovementFacing() != net.minecraft.core.Direction.EAST)
                        motor(h, beltMotor).generatedSpeed.setValue(-1);
                    for (int x : new int[] {4, 9, 14}) {
                        var fan =
                                (com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity)
                                        h.getBlockEntity(new BlockPos(x, 2, 1));
                        if (fan.getAirFlowDirection() != net.minecraft.core.Direction.SOUTH)
                            motor(h, new BlockPos(x, 2, 0)).generatedSpeed.setValue(-256);
                    }
                });
        h.runAfterDelay(
                40,
                () ->
                        ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                                        h.getBlockEntity(new BlockPos(1, 3, 5)))
                                .setItem(0, CEItems.PIG_IRON.toStack()));
        h.runAfterDelay(
                11900,
                () -> {
                    var b =
                            (com.simibubi.create.content.kinetics.belt.BeltBlockEntity)
                                    h.getBlockEntity(new BlockPos(1, 1, 5));
                    var state =
                            new StringBuilder("belt=")
                                    .append(b.getSpeed())
                                    .append(" direction=")
                                    .append(b.getMovementFacing())
                                    .append(" items=");
                    for (var item : b.getInventory().getTransportedItems())
                        state.append(item.stack)
                                .append(" at ")
                                .append(item.beltPosition)
                                .append(" / ")
                                .append(item.stack.get(AllDataComponents.SEQUENCED_ASSEMBLY))
                                .append(';');
                    for (int x : new int[] {4, 9, 14}) {
                        var fan =
                                (com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity)
                                        h.getBlockEntity(new BlockPos(x, 2, 1));
                        state.append(" fan")
                                .append(x)
                                .append('=')
                                .append(fan.getSpeed())
                                .append('/')
                                .append(fan.getAirFlowDirection());
                    }
                    for (int x : new int[] {2, 6, 7, 11, 12, 16})
                        state.append(" press")
                                .append(x)
                                .append('=')
                                .append(
                                        ((MechanicalPressBlockEntity)
                                                        h.getBlockEntity(new BlockPos(x, 3, 5)))
                                                .getSpeed());
                    var area =
                            new net.minecraft.world.phys.AABB(
                                    net.minecraft.world.phys.Vec3.atLowerCornerOf(
                                            h.absolutePos(new BlockPos(0, 0, 0))),
                                    net.minecraft.world.phys.Vec3.atLowerCornerOf(
                                            h.absolutePos(new BlockPos(23, 7, 12))));
                    state.append(" input=")
                            .append(
                                    ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                                                    h.getBlockEntity(new BlockPos(1, 3, 5)))
                                            .getItem(0));
                    var out =
                            (net.minecraft.world.level.block.entity.ChestBlockEntity)
                                    h.getBlockEntity(new BlockPos(18, 2, 5));
                    for (int slot = 0; slot < out.getContainerSize(); slot++)
                        if (!out.getItem(slot).isEmpty())
                            state.append(" output=")
                                    .append(out.getItem(slot))
                                    .append('/')
                                    .append(
                                            out.getItem(slot)
                                                    .get(AllDataComponents.SEQUENCED_ASSEMBLY));
                    state.append(" fanTime=")
                            .append(
                                    com.simibubi.create.infrastructure.config.AllConfigs.server()
                                            .kinetics
                                            .fanProcessingTime
                                            .get())
                            .append(" beltMovement=")
                            .append(b.getBeltMovementSpeed());
                    state.append(" dropped=")
                            .append(
                                    h
                                            .getLevel()
                                            .getEntitiesOfClass(
                                                    net.minecraft.world.entity.item.ItemEntity
                                                            .class,
                                                    area)
                                            .stream()
                                            .map(item -> item.getItem() + "@" + item.position())
                                            .toList());
                    org.slf4j.LoggerFactory.getLogger(ForgingGameTests.class)
                            .info("FORGING LINE DIAGNOSTIC {}", state);
                });
        h.succeedWhen(
                () -> {
                    var chest =
                            (net.minecraft.world.level.block.entity.ChestBlockEntity)
                                    h.getBlockEntity(new BlockPos(18, 2, 5));
                    int steel = 0;
                    for (int slot = 0; slot < chest.getContainerSize(); slot++)
                        if (chest.getItem(slot).is(CEItems.STEEL_INGOT.get()))
                            steel += chest.getItem(slot).getCount();
                    h.assertTrue(
                            steel == 1,
                            "Six real presses and three lava fans deliver exactly one steel ingot"
                                + " through a native belt and funnel");
                });
    }

    private static com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor(
            GameTestHelper h, BlockPos pos) {
        return (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                h.getBlockEntity(pos);
    }
}
