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
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.nuclear.ReactorCoreStructure;
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
                port.insertItem(0, new ItemStack(CENuclear.CONTROL_ROD.get(), 2), false);
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
