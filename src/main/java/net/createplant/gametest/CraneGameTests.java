package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.block.CraneStructure;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CECrane;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class CraneGameTests {
    private static final int CORE_BOTTOM = ReactorPhysics.LOWER_STRUCTURE_HEIGHT + 2;
    private static final int CAP_Y = CORE_BOTTOM + ReactorPhysics.FUEL_ASSEMBLY_LENGTH;
    private static final int SERVICE_RAIL_Y = CAP_Y + CraneDriveBlockEntity.SERVICE_MAST_LENGTH;
    private static final BlockPos CORE = new BlockPos(5, CORE_BOTTOM, 5);
    private static final BlockPos CAP = new BlockPos(5, CAP_Y, 5);
    private static final BlockPos POOL = new BlockPos(12, CAP_Y - net.createplant.nuclear.FuelPoolStructure.DEPTH, 5);

    private static BlockPos crane(GameTestHelper h, int x0, int x1, int z0, int z1, int railY, boolean powered) {
        // Legs stand on the floor one block above the template's origin.
        for (int x : new int[] {x0, x1}) for (int z : new int[] {z0, z1}) for (int y = 2; y < railY; y++)
            h.setBlock(new BlockPos(x, y, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), Direction.Axis.Y));
        for (int z : new int[] {z0, z1}) for (int x = x0; x <= x1; x++)
            h.setBlock(new BlockPos(x, railY, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, railY, z)), Direction.Axis.X));
        BlockPos drive = new BlockPos(x0 - 1, railY, z0);
        h.setBlock(drive, CECrane.DRIVE.get().defaultBlockState().setValue(CraneDriveBlock.FACING, Direction.EAST).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        ((CraneDriveBlockEntity) h.getBlockEntity(drive)).size(x1 - x0 + 1, z1 - z0 - 1);
        h.setBlock(drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        if (powered) h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(drive.west())).generatedSpeed.setValue(128));
        return drive;
    }

    private static BlockPos serviceCrane(GameTestHelper h, boolean powered) {
        return crane(h, 3, 15, 3, 12, SERVICE_RAIL_Y, powered);
    }

    private static void reactor(GameTestHelper h) {
        reactor(h, ReactorPhysics.CORE_HEIGHT);
    }

    private static void reactor(GameTestHelper h, int height) {
        var layout = ReactorPhysics.layout(height);
        int capY = CORE_BOTTOM + layout.fuelAssemblyLength();
        for (int x = 5; x <= 9; x++) for (int z = 5; z <= 9; z++) for (int y = CORE_BOTTOM; y < CORE_BOTTOM + height; y++)
            h.setBlock(new BlockPos(x, y, z), x == 5 && z == 5 ? CENuclear.FUEL_CHANNEL.get() : CENuclear.GRAPHITE.get());
        for (int y = 1; y <= layout.lowerStructureHeight(); y++) h.setBlock(CORE.below(y), CENuclear.FEED_BUNDLE.get());
        for (int y = CORE_BOTTOM + height; y < capY; y++) h.setBlock(new BlockPos(5, y, 5), CENuclear.FUEL_CHANNEL_TUBE.get());
        h.setBlock(new BlockPos(5, capY, 5), CENuclear.TOP_CAP.get());
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(CORE));
        h.assertTrue(result.formed(), "The minimum five-metre crane fixture forms: " + result.reason() + " at " + result.fault());
        ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).adopt(result, null);
    }

    @GameTest(template = "test_rbmk", batch = "crane_transfer", timeoutTicks = 1200)
    public static void craneLoadsCompactEnrichedFuelAndKeepsItsGrappleIdentity(GameTestHelper h) {
        reactor(h, 3);
        BlockPos drive = crane(h, 3, 15, 3, 12, CORE_BOTTOM + ReactorPhysics.COMPACT_LAYOUT.fuelAssemblyLength() + CraneDriveBlockEntity.SERVICE_MAST_LENGTH, false);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(((CraneDriveBlockEntity) h.getBlockEntity(drive)).structure() != null, "Compact service crane forms"))
            .thenExecute(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                crane.store().insertItem(0, CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack(2), false);
                h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> h.assertTrue(((CraneDriveBlockEntity) h.getBlockEntity(drive)).carried() == 1, "The grapple picks enriched fuel"))
            .thenExecute(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                var saved = crane.saveWithoutMetadata(h.getLevel().registryAccess());
                var restored = new CraneDriveBlockEntity(crane.getBlockPos(), crane.getBlockState());restored.setLevel(h.getLevel());
                restored.loadWithComponents(saved.copy(), h.getLevel().registryAccess());
                h.assertTrue(restored.carried() == 1 && restored.saveWithoutMetadata(h.getLevel().registryAccess()).getByte("CarriedFuelKind") == 2,
                    "A saved loaded grapple retains the enriched fuel identity");
            })
            .thenWaitUntil(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
                h.assertTrue(column.fresh() == 2 && crane.carried() == 0 && count(crane, CENuclear.COMPACT_ENRICHED_FUEL_ROD.get()) == 0
                    && column.cellReactivityBonus() > 0, "The real crane loads two short enriched assemblies without substituting ordinary rods: " + crane.status());
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", batch = "crane_transfer", timeoutTicks = 400)
    public static void craneLeavesCompactFuelInTheStoreBesideASevenMetreCore(GameTestHelper h) {
        reactor(h);
        BlockPos drive = serviceCrane(h, false);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(((CraneDriveBlockEntity) h.getBlockEntity(drive)).structure() != null, "Standard service crane forms"))
            .thenExecute(() -> {
                ((CraneDriveBlockEntity) h.getBlockEntity(drive)).store().insertItem(0, CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack(3), false);
                h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenIdle(200)
            .thenExecute(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                h.assertTrue(((FuelChannelBlockEntity) h.getBlockEntity(CORE)).fresh() == 0 && crane.carried() == 0
                    && count(crane, CENuclear.COMPACT_ENRICHED_FUEL_ROD.get()) == 3, "The crane rejects incompatible fuel without consuming or stranding it in the grapple");
            })
            .thenSucceed();
    }

    private static void pool(GameTestHelper h) {
        FuelPoolGameTests.build(h, POOL, 2, 2);
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(POOL), Direction.DOWN);
        int water = 4 * FuelPoolBlockEntity.WATER_PER_CELL;
        h.assertTrue(fluid != null && fluid.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
            "The pool accepts enough water to cover the long assemblies");
    }

    private static int count(CraneDriveBlockEntity crane, Item item) {
        int total = 0;
        for (int slot = 0; slot < CraneDriveBlockEntity.SLOTS; slot++)
            if (crane.store().getStackInSlot(slot).is(item)) total += crane.store().getStackInSlot(slot).getCount();
        return total;
    }

    private static int poolCount(GameTestHelper h, byte state) {
        int count = 0;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++)
            if (((FuelPoolBlockEntity) h.getBlockEntity(POOL.offset(x, 0, z))).slot(slot) == state) count++;
        return count;
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 160)
    public static void craneFormsAndReportsAMissingLeg(GameTestHelper h) {
        BlockPos drive = crane(h, 3, 9, 3, 8, 5, true);
        h.startSequence()
            .thenWaitUntil(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                h.assertTrue(crane.structure() != null && crane.structure().length() == 7 && crane.structure().span() == 5 && crane.structure().legs() == 3,
                    "Seven-long girder rails five apart on three-high legs form a crane: " + crane.status());
            })
            .thenExecute(() -> h.setBlock(new BlockPos(9, 2, 8), Blocks.AIR))
            .thenWaitUntil(() -> h.assertTrue(((CraneDriveBlockEntity) h.getBlockEntity(drive)).status().equals("frame_supplies"),
                "A broken leg stops the crane until it has a girder to mend it"))
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", batch = "crane_transfer", timeoutTicks = 3000)
    public static void craneServicesTwentyOneMetreFuelAssemblies(GameTestHelper h) {
        reactor(h);
        pool(h);
        BlockPos drive = serviceCrane(h, false);
        var firstPool = (FuelPoolBlockEntity) h.getBlockEntity(POOL);
        firstPool.set(0, FuelPoolBlockEntity.FRESH);
        firstPool.set(1, FuelPoolBlockEntity.FRESH);

        h.startSequence()
            .thenWaitUntil(() -> {
                var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
                h.assertTrue(column.core() != null && column.core().formed(), "The minimum five-metre core forms below its 21 m cap");
                h.assertTrue(firstPool.ready(), "The 22 m storage pool is ready");
            })
            .thenExecute(() -> {
                var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CAP), Direction.UP);
                h.assertTrue(items != null && items.insertItem(2, CENuclear.FUEL_ROD.toStack(), false).isEmpty(), "Fuel enters through the selected cap subdivision");
                ((FuelChannelBlockEntity) h.getBlockEntity(CORE)).burn(FuelChannelBlockEntity.ROD_JOULES);
                h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
                h.assertTrue(column.spent() == 0 && column.fresh() == 2 && poolCount(h, FuelPoolBlockEntity.FRESH) == 0
                    && poolCount(h, FuelPoolBlockEntity.SPENT) == 1 && crane.carried() == 0,
                    "Crane moves full 21 m assemblies between the 2 x 2 cap and deep pool without changing mast depth: " + crane.status());
                h.assertTrue(Math.abs(crane.depth() - CraneDriveBlockEntity.SERVICE_MAST_DEPTH) < 1e-6,
                    "The 10 + 5 handling mast stays at its fixed service depth");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", batch = "crane_transfer", timeoutTicks = 2100)
    public static void thePhysicalDeskJogsTheCraneAndPausesFuelExchange(GameTestHelper h) {
        reactor(h);
        pool(h);
        BlockPos drive = serviceCrane(h, false), desk = new BlockPos(18, CAP_Y, 5);
        var level = h.getLevel();
        var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
        var rack = (FuelPoolBlockEntity) h.getBlockEntity(POOL);
        var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
        Direction facing = Direction.NORTH;
        ReactorConsoleStructure.DESK.place(h::setBlock, desk, facing);
        h.assertTrue(ReactorConsoleStructure.DESK.tryForm(level, h.absolutePos(desk)), "Assemble the physical desk without a reactor Display Link");
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(desk);
        var player = ReactorConsoleGameTests.linkCrane(h, h.absolutePos(drive), h.absolutePos(desk));
        h.assertTrue(h.absolutePos(desk).equals(crane.console()), "Both endpoints link after the physical item interaction");
        double[] slide = {0};
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(crane.structure() != null && crane.getSpeed() != 0 && crane.status().equals("console_paused"),
                "The linked drive starts in STOP with rotation available"))
            .thenExecute(() -> {
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_ALONG);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_ACROSS);
            })
            .thenWaitUntil(() -> {
                var craneTag = crane.saveWithoutMetadata(level.registryAccess());
                h.assertTrue(craneTag.getDouble("U") == 1 && craneTag.getDouble("V") == 2 && crane.status().equals("console_manual"),
                    "The right halves of both physical buttons move one block in the respective axes");
            })
            .thenExecute(() -> {
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_ALONG, false);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_ACROSS, false);
            })
            .thenWaitUntil(() -> {
                var craneTag = crane.saveWithoutMetadata(level.registryAccess());
                h.assertTrue(craneTag.getDouble("U") == 0 && craneTag.getDouble("V") == 1 && crane.status().equals("console_manual"),
                    "The left halves move back toward the near rail and start");
            })
            .thenExecute(() -> {
                var port = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CAP), Direction.UP);
                h.assertTrue(port.insertItem(2, CENuclear.FUEL_ROD.toStack(), false).isEmpty(), "Load a fuel position for the exchange");
                column.burn(column.rodJoules());
                var target = net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.toStack();
                h.assertTrue(port.insertItem(0, target, false).isEmpty(), "Load a separate irradiation target");
                var saved = column.saveWithoutMetadata(level.registryAccess());
                var holes = saved.getByteArray("Holes");
                holes[0] = 4;
                saved.putByteArray("Holes", holes);
                column.loadWithComponents(saved, level.registryAccess());
                h.assertTrue(column.irradiatedTargets() == 1, "Restore an irradiated target from a finished run");
                rack.set(0, FuelPoolBlockEntity.FRESH);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
            })
            .thenWaitUntil(() -> h.assertTrue(crane.working() && crane.carried() == 2 && column.spent() == 0,
                "AUTO starts withdrawing the spent assembly without local redstone"))
            .thenExecute(() -> {
                slide[0] = crane.slide();
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
                h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenIdle(20)
            .thenExecute(() -> {
                h.assertTrue(crane.status().equals("console_paused") && crane.carried() == 2 && crane.slide() == slide[0],
                    "STOP holds the spent assembly and its lift progress even while local redstone is on");
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_ALONG);
            })
            .thenWaitUntil(() -> h.assertTrue(crane.status().equals("console_load") && crane.slide() == slide[0],
                "Manual travel does not move a grapple while it is part way through a lift"))
            .thenExecute(() -> {
                h.setBlock(drive.west(), Blocks.AIR);
                crane.loadWithComponents(crane.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                console.loadWithComponents(console.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                h.setBlock(drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
                h.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(drive.west())).generatedSpeed.setValue(128));
            })
            .thenWaitUntil(() -> h.assertTrue(crane.carried() == 2 && crane.status().equals("console_paused")
                && h.absolutePos(desk).equals(crane.console()) && console.linksCrane(h.absolutePos(drive)),
                "Both saved endpoints and the spent rod survive reload with the crane stopped"))
            .thenWaitUntil(() -> h.assertTrue(crane.getSpeed() != 0, "The reattached motor restores Create power"))
            .thenExecute(() -> {
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
                ReactorConsoleGameTests.click(h, desk, facing, ReactorConsoleButton.CRANE_MODE);
            })
            .thenWaitUntil(() -> h.assertTrue(column.fresh() == 1 && poolCount(h, FuelPoolBlockEntity.SPENT) == 1 && crane.carried() == 0
                && column.irradiatedTargets() == 1, "Resuming AUTO exchanges only the fuel rod and leaves the irradiated target intact: "
                    + crane.status() + ", speed=" + crane.getSpeed() + ", carried=" + crane.carried() + ", loaded=" + column.fresh()
                    + ", target=" + column.irradiatedTargets() + ", pool spent=" + poolCount(h, FuelPoolBlockEntity.SPENT)
                    + ", mode=" + console.craneModeFor(h.absolutePos(drive))))
            .thenExecute(() -> {
                console.loadWithComponents(console.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                h.assertTrue(console.linksCrane(h.absolutePos(drive)) && console.craneModeFor(h.absolutePos(drive)) == ReactorControlConsoleBlockEntity.CraneMode.STOPPED,
                    "Saving preserves the link and reloading requires a new command to move");
                player.setShiftKeyDown(true);
                console.press(player, ReactorConsoleButton.CRANE_MODE, true);
                h.assertTrue(crane.console() == null && !console.linksCrane(h.absolutePos(drive)), "Unlinking returns the drive to local redstone control");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", batch = "crane_transfer", timeoutTicks = 1600)
    public static void craneRenewsIntegratedControlRodThroughCap(GameTestHelper h) {
        reactor(h);
        BlockPos drive = serviceCrane(h, false);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(((FuelChannelBlockEntity) h.getBlockEntity(CORE)).core() != null, "Core forms"))
            .thenExecute(() -> {
                var cap = (RodCapBlockEntity) h.getBlockEntity(CAP);
                h.assertTrue(cap.installControl(0), "Control rod installs in one cap subdivision without a winch");
            })
            .thenWaitUntil(() -> h.assertTrue(((FuelChannelBlockEntity) h.getBlockEntity(CORE)).core().installedControlRods() == 1,
                "The formed core discovers the integrated rod"))
            .thenExecute(() -> {
                var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
                column.wearControl(0, .5);
                var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(drive), Direction.UP);
                h.assertTrue(port.insertItem(0, CENuclear.CONTROL_ROD.toStack(), false).isEmpty()
                    && port.insertItem(1, CENuclear.CONTROL_ROD.toStack(), false).isEmpty(), "Supply two replacement absorbers");
                for (int slot = 2; slot < port.getSlots(); slot++)
                    port.insertItem(slot, AllBlocks.METAL_GIRDER.asStack(64), false);
                h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> {
                var crane = (CraneDriveBlockEntity) h.getBlockEntity(drive);
                var column = (FuelChannelBlockEntity) h.getBlockEntity(CORE);
                h.assertTrue(column.controlWear(0) == 0 && count(crane, CENuclear.SPENT_CONTROL_ROD.get()) == 1
                    && count(crane, CENuclear.CONTROL_ROD.get()) == 1 && column.controlInstalled(0),
                    "Crane exchanges the withdrawn integrated rod at its exact 2 x 2 cap position: " + crane.status());
            })
            .thenSucceed();
    }

}
