package net.createplant.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllItems;
import net.createplant.CreatePlant;
import net.createplant.block.TransmissionTowerBlock;
import net.createplant.block.entity.ElectricPumpBlockEntity;
import net.createplant.registry.CEElectricUtilities;
import net.createplant.registry.CETransmission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransmissionTowerGameTests {
    private static InteractionResult click(Player player, BlockPos tower, double x, double z) {
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(tower).add(x, .875, z), Direction.UP, tower, false);
        return CETransmission.SPOOL.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    @GameTest(template = "test_empty")
    public static void placesAndWrenchesInFifteenDegreeSteps(GameTestHelper h) {
        var level = h.getLevel();
        var tower = CETransmission.TOWER.get();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5, .875, .5), Direction.UP, pos, false);
        var placement = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, CETransmission.TOWER.toStack(), hit);
        for (int degrees = 0; degrees < 360; degrees += 15) {
            player.setYRot(degrees + 3);
            var state = tower.getStateForPlacement(placement);
            double radians = Math.toRadians(degrees);
            for (int id = 0; id < 6; id++) {
                int row = id % 3;
                double span = (row == 1 ? 4.42 : 3.42) * (id < 3 ? -1 : 1);
                var expected = new Vec3(.5 + span * Math.cos(radians), 21.82 - row * 3, .5 + span * Math.sin(radians));
                var pad = new Vec3((row - 1) * .33, .875, id < 3 ? -.235 : .235).yRot((float) -radians).add(.5, 0, .5);
                h.assertTrue(expected.distanceToSqr(tower.getNodePosition(level, pos, state, id)) < 1e-12
                    && tower.terminalAt(state, pad) == id, "Aiming at " + degrees + " degrees rotates endpoint and pad " + id);
            }
        }
        player.setYRot(-15);
        var west345 = tower.getStateForPlacement(placement);
        player.setYRot(15);
        var north15 = tower.getStateForPlacement(placement);
        h.assertTrue(west345.mirror(Mirror.FRONT_BACK) == north15
            && west345.rotate(Rotation.CLOCKWISE_90).getValue(TransmissionTowerBlock.ROTATION_STEP) == 5,
            "Structure rotations and mirrors preserve the intermediate angles");
        h.assertTrue(north15.mirror(Mirror.LEFT_RIGHT).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST
            && north15.mirror(Mirror.LEFT_RIGHT).getValue(TransmissionTowerBlock.ROTATION_STEP) == 5,
            "A north-south mirror maps 15 degrees to 165 degrees");

        var north = tower.defaultBlockState();
        var saved = NbtUtils.writeBlockState(north.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        saved.getCompound("Properties").remove("rotation_step");
        var legacy = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), saved);
        h.assertTrue(legacy.getValue(HorizontalDirectionalBlock.FACING) == Direction.WEST
            && legacy.getValue(TransmissionTowerBlock.ROTATION_STEP) == 0, "Old cardinal towers keep their heading");

        var otherPos = pos.south(6);
        level.setBlockAndUpdate(pos, north);
        level.setBlockAndUpdate(otherPos, north);
        var first = new InWorldNode(4, pos);
        var second = new InWorldNode(4, otherPos);
        var connection = new InWorldNodeConnection(first, second);
        var infrastructure = InfrastructureSavedData.load(level);
        infrastructure.connect(first, second, CETransmission.wire());
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        h.assertTrue(AllItems.WRENCH.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && level.getBlockState(pos) == north15, "The Create wrench turns a placed tower by 15 degrees");
        var endpoint = tower.getNodePosition(level, pos, north15, 4).add(Vec3.atLowerCornerOf(pos));
        var wire = infrastructure.wireSimulationState.getConnection(connection);
        h.assertTrue(infrastructure.isConnected(first, second) && wire != null
            && (wire.pos1.distanceToSqr(endpoint) < 1e-12 || wire.pos2.distanceToSqr(endpoint) < 1e-12),
            "An attached conductor follows the new insulator position in the wire simulation");
        level.destroyBlock(pos, false);
        level.destroyBlock(otherPos, false);
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 160)
    public static void powersALoadAt128BlocksAndRemovesWiresOnBreak(GameTestHelper h) {
        var level = h.getLevel();
        var source = h.absolutePos(new BlockPos(2, 2, 2));
        var destination = source.south(128);
        var tooFar = source.south(129).east(5);
        var tower = CETransmission.TOWER.get();
        var state = tower.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        for (var pos : new BlockPos[]{source, destination, tooFar}) {
            level.getChunkAt(pos);
            level.setBlockAndUpdate(pos, state);
        }
        var remoteChunk = new ChunkPos(destination.south(2));
        level.setChunkForced(remoteChunk.x, remoteChunk.z, true);
        h.assertTrue(tower.getNodePositions(level, source, state).size() == 6
            && tower.getNodePosition(level, source, state, 1).equals(new Vec3(-3.92, 18.82, .5)),
            "Six independent endpoints match the top, middle and bottom insulator positions");
        var east = state.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        h.assertTrue(tower.terminalAt(east, new Vec3(.74, .875, .5)) == 1
            && tower.getNodePosition(level, source, east, 1).equals(new Vec3(.5, 18.82, -3.92)),
            "Rotating the tower rotates both the terminal pad and the corresponding cable endpoint");

        var player = h.makeMockPlayer(GameType.CREATIVE);
        var spool = CETransmission.SPOOL.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        var first = new InWorldNode(1, source);
        var last = new InWorldNode(1, destination);
        h.assertTrue(click(player, source, .5, .26).consumesAction()
            && first.equals(spool.get(CEEDataComponents.SELECTED_NODE)), "The base pad selects the middle arm");
        h.assertTrue(click(player, tooFar, .5, .26) == InteractionResult.FAIL, "A span beyond 128 blocks is rejected");
        h.assertTrue(click(player, destination, .5, .26).consumesAction(), "The corresponding pad connects the long span");
        h.assertTrue(click(player, source, .17, .74).consumesAction()
            && click(player, destination, .17, .74).consumesAction(), "A second independent conductor can be installed");

        var infrastructure = InfrastructureSavedData.load(level);
        var span = infrastructure.getConnectionData(new InWorldNodeConnection(first, last));
        h.assertTrue(span != null && span.wireType() == CETransmission.wire(),
            "The installed connection uses the registered high-voltage cable");
        var from = first.getPosition(level);
        var to = last.getPosition(level);
        float sag = span.getSag(from.distanceTo(to));
        var center = QuadraticWireHelper.posAt(from, to, .5f, sag);
        double drop = from.lerp(to, .5).y - center.y;
        h.assertTrue(Math.abs(drop - 1.28) < .01
            && QuadraticWireHelper.posAt(from, to, 0, sag).equals(from)
            && QuadraticWireHelper.posAt(from, to, 1, sag).equals(to),
            "A 128-block span has a shallow 1.28-block sag and stays attached to both insulators: " + drop);

        var batteryPos = source.north(2);
        var pumpPos = destination.south(2);
        level.setBlockAndUpdate(batteryPos, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        level.setBlockAndUpdate(pumpPos, CEElectricUtilities.PUMP.get().defaultBlockState());
        var battery = DevicesSavedData.load(level).getDevice(batteryPos, CreativeBatteryDevice.class);
        h.assertTrue(battery != null, "The native test battery is registered");
        battery.voltage = 480;
        battery.acFrequency = 0;
        h.runAfterDelay(5, () -> {
            CEEBlocks.CREATIVE_BATTERY.get().ensureNodesExist(level, batteryPos, level.getBlockState(batteryPos));
            CEElectricUtilities.PUMP.get().ensureNodesExist(level, pumpPos, level.getBlockState(pumpPos));
            int[] ids = {1, 3};
            for (int side = 0; side < ids.length; side++) {
                infrastructure.connect(new InWorldNode(side, batteryPos), new InWorldNode(ids[side], source), CETransmission.wire());
                infrastructure.connect(new InWorldNode(ids[side], destination), new InWorldNode(side, pumpPos), CETransmission.wire());
            }
        });
        h.runAfterDelay(95, () -> {
            var pump = (ElectricPumpBlockEntity) level.getBlockEntity(pumpPos);
            h.assertTrue(pump.watts() >= 250, "A real wired load receives power at the far end");
            level.destroyBlock(source, false);
        });
        h.runAfterDelay(115, () -> {
            h.assertTrue(!infrastructure.isConnected(first, last), "Breaking the root removes the connected wire");
            h.assertTrue(((ElectricPumpBlockEntity) level.getBlockEntity(pumpPos)).watts() == 0, "The broken line stops supplying power");
            for (var pos : new BlockPos[]{destination, tooFar, batteryPos, pumpPos}) level.destroyBlock(pos, false);
            level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
            h.succeed();
        });
    }
}
