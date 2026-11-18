package net.createplant.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ElectricPumpBlockEntity;
import net.createplant.registry.CEElectricUtilities;
import net.createplant.registry.CETransmission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
        h.assertTrue(span != null && span.wireType() == CETransmission.wire() && span.getSag(128) == 0,
            "The actual Electro Energetics wire is straight at its maximum span");

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
