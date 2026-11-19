package net.createplant.gametest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.CraneFrame;
import net.createplant.block.CraneStructure;
import net.createplant.block.QuarryDriveBlock;
import net.createplant.block.entity.QuarryConveyor;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CECrane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class QuarryGameTests {
    /** The drive's shaft lies across the rails, which run east from the drive; the side belt runs along z = 2. */
    private static final BlockPos DRIVE = new BlockPos(3, 8, 3);

    /** A quarry drive with its range extending east and south: five along the rails, three across, turned from the inside. */
    private static void configuredQuarry(GameTestHelper h, int speed) {
        h.setBlock(DRIVE, CECrane.QUARRY_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(DRIVE.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        ((QuarryDriveBlockEntity) h.getBlockEntity(DRIVE)).size(5, 3);
        h.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(DRIVE.south())).generatedSpeed.setValue(speed));
    }

    private static void supply(GameTestHelper h, ItemStack stack) {
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(DRIVE), Direction.UP);
        h.assertTrue(ItemHandlerHelper.insertItem(port, stack, false).isEmpty(), "The drive takes " + stack);
    }

    /** Rails seven above the floor: four legs of six girders and two rails of five, 34 girders for the 5 × 3 range. */
    private static final int FRAME_GIRDERS = 34;

    private static void supplyAll(GameTestHelper h) {
        supply(h, AllBlocks.METAL_GIRDER.asStack(FRAME_GIRDERS));
        supply(h, AllBlocks.MECHANICAL_DRILL.asStack());
        supply(h, AllItems.BELT_CONNECTOR.asStack());
        supply(h, AllBlocks.SHAFT.asStack(2));supply(h, AllBlocks.ENCASED_CHAIN_DRIVE.asStack(6));
        h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
    }

    private static int count(QuarryDriveBlockEntity quarry, Item item) {
        int total = 0;
        for (int slot = 0; slot < QuarryDriveBlockEntity.SLOTS; slot++)
            if (quarry.store().getStackInSlot(slot).is(item)) total += quarry.store().getStackInSlot(slot).getCount();
        return total;
    }

    /** Items thrown off a belt end inside {@code box}, in test coordinates. */
    private static int thrown(GameTestHelper h, Item item, BlockPos from, BlockPos to) {
        int total = 0;
        for (ItemEntity entity : h.getLevel().getEntities(EntityType.ITEM, new AABB(h.absolutePos(from)).minmax(new AABB(h.absolutePos(to))), e -> true))
            if (entity.getItem().is(item)) total += entity.getItem().getCount();
        return total;
    }

    private static void dirt(GameTestHelper h) {
        for (int x = 4; x <= 8; x++) for (int z = 4; z <= 6; z++) h.setBlock(new BlockPos(x, 7, z), Blocks.DIRT);
    }

    private static net.minecraft.nbt.CompoundTag clientData(GameTestHelper h, QuarryDriveBlockEntity quarry) {
        return quarry.writeClient(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
    }

    private static void missing(GameTestHelper h, QuarryDriveBlockEntity quarry, int girders, int drills, int belts) {
        var tag = clientData(h, quarry);
        h.assertTrue(tag.getBoolean("MaterialsKnown") && tag.getInt("MissingGirders") == girders
            && tag.getInt("MissingDrills") == drills && tag.getInt("MissingBelts") == belts,
            "All deficits are current: " + tag.getInt("MissingGirders") + "/" + tag.getInt("MissingDrills") + "/" + tag.getInt("MissingBelts"));
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 300)
    public static void constructionHighlightsTheCurrentFrameAndBeltObstruction(GameTestHelper h) {
        configuredQuarry(h, 64);
        BlockPos firstRail = DRIVE.east(), secondRail = firstRail.east(), chain = DRIVE.north(2);
        for (BlockPos pos : List.of(firstRail, secondRail, chain)) h.setBlock(pos, Blocks.CHEST);
        supplyAll(h);
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
        var client = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
        h.startSequence()
            .thenWaitUntil(() -> obstruction(h, quarry, client, firstRail, "frame_blocked"))
            .thenExecute(() -> h.setBlock(firstRail, Blocks.AIR))
            .thenWaitUntil(() -> obstruction(h, quarry, client, secondRail, "frame_blocked"))
            .thenExecute(() -> h.setBlock(secondRail, Blocks.AIR))
            .thenWaitUntil(() -> obstruction(h, quarry, client, chain, "belt_blocked"))
            .thenExecute(() -> h.setBlock(chain, Blocks.AIR))
            .thenWaitUntil(() -> {
                client.readClient(clientData(h, quarry), h.getLevel().registryAccess());
                h.assertTrue(quarry.structure() != null && client.constructionObstruction() == null
                    && h.getBlockState(chain).is(AllBlocks.ENCASED_CHAIN_DRIVE.get()), "Construction resumes and clears the highlight");
            })
            .thenSucceed();
    }

    private static void obstruction(GameTestHelper h, QuarryDriveBlockEntity quarry, QuarryDriveBlockEntity client, BlockPos at, String status) {
        client.readClient(clientData(h, quarry), h.getLevel().registryAccess());
        h.assertTrue(client.status().equals(status) && h.absolutePos(at).equals(client.constructionObstruction())
            && h.getBlockState(at).is(Blocks.CHEST), "The client receives the exact blocking chest for " + status + ": " + client.status());
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 200)
    public static void gogglesShowTheWholeShortageBeforeStartingAndAfterResizing(GameTestHelper h) {
        configuredQuarry(h, 0);
        h.setBlock(DRIVE.east().below(), AllBlocks.METAL_GIRDER.getDefaultState().setValue(GirderBlock.AXIS, Direction.Axis.Y));
        supply(h, AllBlocks.METAL_GIRDER.asStack(6));
        h.startSequence()
            .thenWaitUntil(() -> missing(h, (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE), FRAME_GIRDERS - 1 - 6, 1, 1))
            .thenExecute(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                var tag = clientData(h, quarry);
                var client = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
                client.readClient(tag, h.getLevel().registryAccess());
                var text = new java.util.ArrayList<net.minecraft.network.chat.Component>();
                client.addToGoggleTooltip(text, false);
                h.assertTrue(text.size() == 4 && text.stream().anyMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                    && translated.getKey().equals("crane.create_refinery_n_reactors.quarry_belt_missing")), "The client shows the missing drill and connector within four lines");
                for (int i = 0; i < 5; i++) quarry.addToGoggleTooltip(new java.util.ArrayList<>(), true);
                h.assertTrue(count(quarry, AllBlocks.METAL_GIRDER.asItem()) == 6 && h.getBlockState(DRIVE.east()).isAir(), "Goggles do not consume stock or start construction");
                var restored = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
                restored.setLevel(h.getLevel());restored.loadWithComponents(quarry.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
                restored.tick();missing(h, restored, FRAME_GIRDERS - 1 - 6, 1, 1);
                h.assertTrue(count(restored, AllBlocks.METAL_GIRDER.asItem()) == 6, "Reload recalculates the deficit without multiplying stock");
                quarry.size(7, 3);
            })
            .thenWaitUntil(() -> missing(h, (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE), FRAME_GIRDERS + 4 - 1 - 6, 1, 1))
            .thenExecute(() -> {
                supply(h, AllBlocks.METAL_GIRDER.asStack(FRAME_GIRDERS + 4 - 1 - 6));
                supply(h, AllBlocks.MECHANICAL_DRILL.asStack());supply(h, AllItems.BELT_CONNECTOR.asStack());
                supply(h, AllBlocks.SHAFT.asStack(2));supply(h, AllBlocks.ENCASED_CHAIN_DRIVE.asStack(8));
            })
            .thenWaitUntil(() -> missing(h, (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE), 0, 0, 0))
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 300)
    public static void theCompletedFrameStillShowsItsMissingBeltAndDrill(GameTestHelper h) {
        configuredQuarry(h, 64);
        supply(h, AllBlocks.METAL_GIRDER.asStack(FRAME_GIRDERS));
        h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
        h.startSequence()
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(quarry.structure() != null && quarry.status().equals("belt_supplies"), "The frame waits for its belt");
                missing(h, quarry, 0, 1, 1);
            })
            .thenExecute(() -> { supply(h, AllItems.BELT_CONNECTOR.asStack());supply(h, AllBlocks.SHAFT.asStack(2));supply(h, AllBlocks.ENCASED_CHAIN_DRIVE.asStack(6)); })
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(quarry.status().equals("drill"), "The belt is built and the drill is still missing");
                missing(h, quarry, 0, 1, 0);
                var text = new java.util.ArrayList<net.minecraft.network.chat.Component>();
                quarry.addToGoggleTooltip(text, false);h.assertTrue(text.size() <= 4, "A formed quarry still fits four lines");
            })
            .thenExecute(() -> supply(h, AllBlocks.MECHANICAL_DRILL.asStack()))
            .thenWaitUntil(() -> missing(h, (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE), 0, 0, 0))
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 240)
    public static void aStandardChuteFeedsEveryBuildingMaterialIntoTheStoppedDrive(GameTestHelper h) {
        configuredQuarry(h, 0);
        h.setBlock(DRIVE.above(), AllBlocks.CHUTE.getDefaultState());
        h.setBlock(DRIVE.above(2), Blocks.CHEST);
        var source = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(DRIVE.above(2));
        source.setItem(1, AllBlocks.METAL_GIRDER.asStack(FRAME_GIRDERS));
        source.setItem(2, AllBlocks.MECHANICAL_DRILL.asStack());source.setItem(3, AllItems.BELT_CONNECTOR.asStack());
        source.setItem(4, AllBlocks.SHAFT.asStack(2));source.setItem(5, AllBlocks.ENCASED_CHAIN_DRIVE.asStack(6));
        h.succeedWhen(() -> {
            var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
            h.assertTrue(count(quarry, AllBlocks.METAL_GIRDER.asItem()) == FRAME_GIRDERS
                && count(quarry, AllBlocks.MECHANICAL_DRILL.asItem()) == 1 && count(quarry, AllItems.BELT_CONNECTOR.get()) == 1,
                "A Create chute supplies all four materials from its chest without rotation or an activation signal");
            missing(h, quarry, 0, 0, 0);
        });
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 1600)
    public static void configuredDriveBuildsFrameAndBeltThenTheOreRidesBackToTheDrive(GameTestHelper h) {
        configuredQuarry(h, 256);
        dirt(h);
        h.setBlock(new BlockPos(4, 6, 3), Blocks.STONE);
        h.startSequence()
            .thenExecute(() -> supplyAll(h))
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(quarry.structure() != null && quarry.structure().length() == 5 && quarry.structure().span() == 4 && quarry.structure().legs() == 6,
                    "Two girder rails and four legs reaching the floor form the quarry: " + quarry.status());
                h.assertTrue(CraneFrame.rail(h.getBlockState(DRIVE.east()), Direction.Axis.X), "The first rail girder lies beside the drive");
                for (int x = 3; x <= 8; x++) h.assertTrue(AllBlocks.BELT.has(h.getBlockState(new BlockPos(x, 8, 2))), "The side belt runs beside the near rail and the drive: " + x);
                h.assertTrue(quarry.drill(), "The drive fitted its drill");
            })
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                int dirt = 0;
                for (int x = 4; x <= 8; x++) for (int z = 4; z <= 6; z++) if (h.getBlockState(new BlockPos(x, 7, z)).is(Blocks.DIRT)) dirt++;
                h.assertTrue(dirt == 0 && thrown(h, Items.DIRT, new BlockPos(0, 1, 0), new BlockPos(3, 9, 3)) == 15,
                    "The first layer comes back along the side belt and off its end by the drive: " + quarry.status() + " " + thrown(h, Items.DIRT, new BlockPos(0, 1, 0), new BlockPos(3, 9, 3)));
                h.assertTrue(thrown(h, Items.DIRT, new BlockPos(9, 1, 0), new BlockPos(12, 9, 3)) == 0, "Nothing leaves the far end");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 1600)
    public static void theSideBeltRunsToTheDriveWhicheverWayTheDriveTurns(GameTestHelper h) {
        configuredQuarry(h, -256);
        dirt(h);
        h.startSequence()
            .thenExecute(() -> supplyAll(h))
            .thenWaitUntil(() -> h.assertTrue(thrown(h, Items.DIRT, new BlockPos(0, 1, 0), new BlockPos(3, 9, 3)) == 15
                && thrown(h, Items.DIRT, new BlockPos(9, 1, 0), new BlockPos(12, 9, 3)) == 0,
                "Reversed rotation still brings the ore to the drive: " + ((QuarryDriveBlockEntity) h.getBlockEntity(DRIVE)).status()))
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 800)
    public static void quarryDrainsWaterAndLavaBeforeMiningUnderThem(GameTestHelper h) {
        configuredQuarry(h, 256);
        for (int x = 3; x <= 9; x++) for (int z = 3; z <= 7; z++) {
            h.setBlock(new BlockPos(x, 6, z), Blocks.BEDROCK);
            if (x == 3 || x == 6 || x == 9 || z == 3 || z == 7) h.setBlock(new BlockPos(x, 7, z), Blocks.BEDROCK);
        }
        for (int x : new int[] {4, 5, 7, 8}) {
            var liquid = x < 6 ? Blocks.WATER : Blocks.LAVA;
            for (int z = 4; z <= 6; z++)
                h.setBlock(new BlockPos(x, 7, z), liquid.defaultBlockState().setValue(LiquidBlock.LEVEL, z == 6 ? 3 : 0));
        }
        var outside = new BlockPos(10, 7, 5);
        h.setBlock(outside.below(), Blocks.BEDROCK);
        h.setBlock(outside.east(), Blocks.BEDROCK);
        h.setBlock(outside.north(), Blocks.BEDROCK);
        h.setBlock(outside.south(), Blocks.BEDROCK);
        h.setBlock(outside, Blocks.WATER);
        var buried = new BlockPos(8, 6, 5);
        h.setBlock(buried, Blocks.POLISHED_GRANITE);
        supplyAll(h);
        h.succeedWhen(() -> {
            for (int x : new int[] {4, 5, 7, 8}) for (int z = 4; z <= 6; z++)
                h.assertTrue(h.getBlockState(new BlockPos(x, 7, z)).isAir(), "Sources and flowing liquid are cleared in both enclosed pools");
            h.assertTrue(h.getBlockState(buried).isAir(), "The quarry continues mining beneath the drained lava");
            h.assertTrue(h.getBlockState(outside).is(Blocks.WATER) && h.getBlockState(outside).getFluidState().isSource(), "The source beyond the footprint is preserved");
        });
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 800)
    public static void aFinishedLegacyQuarryDrainsItsShaftAndKeepsInstalledBlocks(GameTestHelper h) {
        configuredQuarry(h, 256);
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
        var lookup = h.getLevel().registryAccess();
        int completedLayer = h.getLevel().getMinBuildHeight() - 1;
        var legacy = quarry.saveWithoutMetadata(lookup);
        legacy.remove("NextLiquidRow");
        legacy.putInt("Layer", completedLayer);
        quarry.loadWithComponents(legacy, lookup);

        var water = new BlockPos(4, 7, 4);
        var lava = new BlockPos(7, 7, 4);
        var chest = new BlockPos(6, 7, 6);
        for (var pos : List.of(water, lava, chest)) {
            h.setBlock(pos.below(), Blocks.BEDROCK);
            for (Direction direction : Direction.Plane.HORIZONTAL) h.setBlock(pos.relative(direction), Blocks.BEDROCK);
        }
        h.setBlock(water, Blocks.WATER);
        h.setBlock(lava, Blocks.LAVA);
        h.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        ((ChestBlockEntity) h.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
        var installed = new BlockPos(8, 7, 6);
        h.setBlock(installed, Blocks.POLISHED_GRANITE);

        h.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                h.assertTrue(h.getBlockState(water).is(Blocks.WATER) && h.getBlockState(lava).is(Blocks.LAVA), "A paused quarry retains both liquids");
                supplyAll(h);
            })
            .thenWaitUntil(() -> h.assertTrue(h.getBlockState(water).isAir() && quarry.status().equals("quarry_pumping"), "The old completed drive begins pumping its shaft"))
            .thenExecute(() -> {
                var saved = quarry.saveWithoutMetadata(lookup);
                h.assertTrue(saved.getInt("NextLiquidRow") > 0, "The drain cursor is saved");
                var restored = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
                restored.loadWithComponents(saved, lookup);
                h.assertTrue(restored.saveWithoutMetadata(lookup).getInt("NextLiquidRow") == saved.getInt("NextLiquidRow"),
                    "A restored drive resumes the same drainage row");
            })
            .thenWaitUntil(() -> {
                var tickets = quarryTickets(h, DRIVE);
                h.assertTrue(quarry.status().equals("quarry_done") && tickets.isEmpty(), "Pumping finishes and releases the tickets: " + quarry.status() + ", tickets=" + tickets.size());
                h.assertTrue(h.getBlockState(water).isAir() && h.getBlockState(lava).isAir(), "The migrated shaft has been drained");
                h.assertTrue(h.getBlockState(installed).is(Blocks.POLISHED_GRANITE)
                    && ((ChestBlockEntity) h.getBlockEntity(chest)).getItem(0).getCount() == 3 && !h.getBlockState(chest).getFluidState().isEmpty(),
                    "The installed block, waterlogged chest and contents survive");
                h.assertTrue(quarry.saveWithoutMetadata(lookup).getInt("Layer") == completedLayer, "Liquid cleanup does not restart excavation");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 600)
    public static void frameBuildingWaitsForPartsStopsLegsOnTheGroundAndClearsTheRails(GameTestHelper h) {
        configuredQuarry(h, 256);
        // A block under one corner is ground for that leg; a block where a rail goes is cleared.
        h.setBlock(new BlockPos(4, 5, 3), Blocks.STONE);
        h.setBlock(new BlockPos(6, 8, 3), Blocks.STONE);
        h.startSequence()
            .thenExecute(() -> {
                supply(h, AllBlocks.METAL_GIRDER.asStack(20));
                h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
            })
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(quarry.status().equals("frame_supplies") && quarry.structure() == null, "Twenty girders are not enough: " + quarry.status());
                h.assertTrue(!CraneFrame.rail(h.getBlockState(DRIVE.east()), Direction.Axis.X), "No rail is laid before every leg stands");
            })
            .thenExecute(() -> supply(h, AllBlocks.METAL_GIRDER.asStack(20)))
            .thenWaitUntil(() -> {
                var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
                h.assertTrue(quarry.structure() != null && quarry.status().equals("belt_supplies"), "The frame stands and waits for a belt connector: " + quarry.status());
                h.assertTrue(CraneFrame.leg(h.getBlockState(new BlockPos(4, 6, 3))) && h.getBlockState(new BlockPos(4, 5, 3)).is(Blocks.STONE),
                    "The leg over the stone stops on it");
                h.assertTrue(count(quarry, Items.COBBLESTONE) == 1 && count(quarry, AllBlocks.METAL_GIRDER.asItem()) == 40 - (FRAME_GIRDERS - 4),
                    "The stone cleared for the rail is in the store and the short leg saved four girders");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 160)
    public static void configuredQuarryUsesItsOwnRailsBesideOtherFrames(GameTestHelper h) {
        var drive = new BlockPos(6, 8, 6);
        var state = CECrane.QUARRY_DRIVE.get().defaultBlockState();
        h.setBlock(drive, state);
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(drive);
        quarry.size(5, 3);
        var rail = AllBlocks.METAL_GIRDER.getDefaultState().setValue(GirderBlock.AXIS, Direction.Axis.X);
        for (int z : new int[] {2, 6, 10}) {
            for (int x = 1; x <= 11; x++) if (x != 6) h.setBlock(new BlockPos(x, 8, z), rail);
            for (int x : new int[] {1, 5, 7, 11}) h.setBlock(new BlockPos(x, 7, z), Blocks.STONE);
        }
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(quarry.structure() != null && quarry.structure().along() == Direction.EAST
                && quarry.structure().across() == Direction.SOUTH, "The east and south rails form despite the west and north frames"))
            .thenExecute(() -> h.setBlock(drive, state.setValue(QuarryDriveBlock.FACING, Direction.NORTH)))
            .thenWaitUntil(() -> h.assertTrue(quarry.structure() != null && quarry.structure().along() == Direction.WEST
                && quarry.structure().across() == Direction.NORTH, "The same drive detects the rotated configuration"))
            .thenExecute(() -> {
                BlockPos end = new BlockPos(1, 8, 2);
                h.setBlock(end, Blocks.AIR);
                var level = h.getLevel();
                var pos = h.absolutePos(drive);
                h.assertTrue(CraneStructure.scanConfigured(level, pos, Direction.WEST, Direction.NORTH, 5, 4).reason().equals("rails"), "A gap in the selected rail fails validation");
                h.setBlock(end, rail);
                h.setBlock(end.west(), rail);
                h.assertTrue(CraneStructure.scanConfigured(level, pos, Direction.WEST, Direction.NORTH, 5, 4).reason().equals("frame_dimensions"), "A rail beyond the selected length is rejected");
                h.setBlock(end.west(), Blocks.AIR);
                h.setBlock(end.below(), Blocks.AIR);
                h.assertTrue(CraneStructure.scanConfigured(level, pos, Direction.WEST, Direction.NORTH, 5, 4).reason().equals("legs"), "The selected frame still needs supporting ground");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 1600)
    public static void quarryEstimatesProgressAndReleasesTicketsAfterOutput(GameTestHelper h) {
        configuredQuarry(h, 64);
        for (int x = 4; x <= 8; x++) for (int z = 4; z <= 6; z++) for (int y = 5; y <= 7; y++)
            h.setBlock(new BlockPos(x, y, z), Blocks.POLISHED_GRANITE);
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
        supplyAll(h);
        h.startSequence()
            .thenWaitUntil(() -> {
                var tag = clientData(h, quarry);
                h.assertTrue(tag.getLong("EtaSeconds") > 0, "The quarry publishes an ETA after recording progress");
                var replica = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
                replica.readClient(tag, h.getLevel().registryAccess());
                var tooltip = new ArrayList<Component>();
                replica.addToGoggleTooltip(tooltip, false);
                h.assertTrue(tooltip.size() == 4 && tooltip.get(2).getContents() instanceof TranslatableContents text
                    && text.getKey().equals("crane.create_refinery_n_reactors.quarry_eta"), "Goggles show the ETA within four lines");
                h.assertTrue(h.getLevel().players().isEmpty() && quarryTickets(h, DRIVE).contains(new ChunkPos(h.absolutePos(DRIVE))),
                    "An active quarry keeps ticking tickets without players");
            })
            .thenExecute(() -> {
                var lookup = h.getLevel().registryAccess();
                var restored = new QuarryDriveBlockEntity(CECrane.QUARRY_DRIVE_ENTITY.get(), h.absolutePos(DRIVE), quarry.getBlockState());
                restored.setLevel(h.getLevel());
                restored.loadWithComponents(quarry.saveWithoutMetadata(lookup), lookup);
                h.assertTrue(restored.structure() == null && restored.getRotationSpeedModifier(Direction.NORTH) == -1,
                    "Reloading keeps the belt's rotation sign before the frame has been validated");
                h.setBlock(DRIVE.above(), Blocks.AIR);
            })
            .thenWaitUntil(() -> h.assertTrue(quarryTickets(h, DRIVE).isEmpty() && quarry.status().equals("redstone"), "Pausing releases this quarry's tickets"))
            .thenExecute(() -> h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK))
            .thenWaitUntil(() -> h.assertTrue(!quarryTickets(h, DRIVE).isEmpty(), "Resuming acquires the tickets again"))
            .thenWaitUntil(() -> {
                h.assertTrue(quarry.status().equals("quarry_done") && quarryTickets(h, DRIVE).isEmpty(), "Completion releases the quarry's tickets");
                h.assertTrue(thrown(h, Items.POLISHED_GRANITE, new BlockPos(0, 1, 0), new BlockPos(3, 9, 3)) == 45,
                    "All 45 blocks have reached the belt outlet before the loader stops");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 300)
    public static void maximumQuarryKeepsItsFarChunksTickingAndReleasesThemOnRemoval(GameTestHelper h) {
        configuredQuarry(h, 0);
        ((QuarryDriveBlockEntity) h.getBlockEntity(DRIVE)).size(64, 64);
        h.setBlock(DRIVE.above(), Blocks.REDSTONE_BLOCK);
        BlockPos owner = h.absolutePos(DRIVE);
        var from = new ChunkPos(owner.west().north(2));
        var to = new ChunkPos(owner.east(65).south(65));
        var expected = new HashSet<>(ChunkPos.rangeClosed(from, to).toList());
        h.startSequence()
            .thenWaitUntil(() -> {
                h.assertTrue(quarryTickets(h, DRIVE).equals(expected) && expected.size() <= 36, "The tickets match the entire work and belt footprint");
                h.assertTrue(h.getLevel().getChunkSource().isPositionTicking(to.toLong()), "The distant end ticks without nearby players");
            })
            .thenExecute(() -> h.setBlock(DRIVE, Blocks.AIR))
            .thenWaitUntil(() -> h.assertTrue(quarryTickets(h, DRIVE).isEmpty(), "Breaking the drive releases every owned ticket"))
            .thenSucceed();
    }

    private static Set<ChunkPos> quarryTickets(GameTestHelper h, BlockPos drive) {
        var level = h.getLevel();
        var data = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (data == null) return Set.of();
        var owner = h.absolutePos(drive);
        var result = new HashSet<ChunkPos>();
        for (Tag controller : data.save(new CompoundTag(), level.registryAccess()).getList("ModForced", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) controller;
            if (!entry.getString("Controller").equals(CreatePlant.location("quarry").toString())) continue;
            for (Tag loaded : entry.getList("ModForced", Tag.TAG_COMPOUND)) {
                var chunk = (CompoundTag) loaded;
                for (Tag block : chunk.getList("TickingBlocks", Tag.TAG_COMPOUND)) {
                    var pos = (CompoundTag) block;
                    if (owner.equals(new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z")))) result.add(new ChunkPos(chunk.getLong("Chunk")));
                }
            }
        }
        return result;
    }

    @GameTest(template = "test_rbmk")
    public static void theChuteAndBridgeKeepEveryItemInOrderWhileTheBeltIsBusy(GameTestHelper h) {
        var conveyor = new QuarryConveyor(() -> { });
        var drops = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < QuarryConveyor.HEAD_SLOTS; i++) drops.add(new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.RAW_IRON, 64));
        h.assertTrue(conveyor.fits(drops), "Nine stacks fill the head bin");
        conveyor.collect(drops);
        h.assertTrue(!conveyor.fits(java.util.List.of(new ItemStack(Items.DIRT))), "A full bin stops the dig");
        var belt = new java.util.ArrayList<ItemStack>();
        boolean[] busy = {true};
        java.util.function.UnaryOperator<ItemStack> unload = stack -> {
            if (busy[0]) return stack;
            int taken = Math.min(stack.getCount(), 10);
            belt.add(stack.copyWithCount(taken));
            return stack.copyWithCount(stack.getCount() - taken);
        };
        for (long now = 0; now < 400; now++) conveyor.tick(now, 6, 3, unload);
        h.assertTrue(belt.isEmpty() && conveyor.contents().stream().mapToInt(ItemStack::getCount).sum() == QuarryConveyor.HEAD_SLOTS * 64,
            "A busy belt holds the parcels on the bridge and in the chute, none lost");
        busy[0] = false;
        for (long now = 400; now < 6000; now++) conveyor.tick(now, 6, 3, unload);
        h.assertTrue(conveyor.contents().isEmpty() && belt.stream().mapToInt(ItemStack::getCount).sum() == QuarryConveyor.HEAD_SLOTS * 64
            && belt.getFirst().is(Items.COBBLESTONE), "Every item reaches the belt, a part at a time and in order");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 1800)
    public static void vanillaAndModOresReachTheOutputWithoutLosingItems(GameTestHelper h) {
        configuredQuarry(h, 256);
        var ores = new java.util.ArrayList<net.minecraft.world.level.block.Block>();
        var expected = new java.util.HashMap<Item, Integer>();
        var vanilla = java.util.Map.of(
            Blocks.IRON_ORE, Items.RAW_IRON, Blocks.DEEPSLATE_IRON_ORE, Items.RAW_IRON,
            Blocks.GOLD_ORE, Items.RAW_GOLD, Blocks.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD,
            Blocks.DIAMOND_ORE, Items.DIAMOND, Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND,
            Blocks.EMERALD_ORE, Items.EMERALD, Blocks.DEEPSLATE_EMERALD_ORE, Items.EMERALD,
            Blocks.COAL_ORE, Items.COAL, Blocks.DEEPSLATE_COAL_ORE, Items.COAL);
        vanilla.forEach((ore, product) -> { ores.add(ore);expected.merge(product, 1, Integer::sum); });
        for (String name : new String[] {"bauxite", "pentlandite", "ilmenite", "zircon", "chromite", "molybdenite", "uranium"}) {
            Item product = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(CreatePlant.location(name.equals("uranium") ? "uraninite" : name));
            for (String prefix : new String[] {"", "deepslate_"}) {
                ores.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(CreatePlant.location(prefix + name + "_ore")));
                expected.merge(product, 1, Integer::sum);
            }
        }
        for (int i = 0; i < ores.size(); i++) h.setBlock(new BlockPos(4 + i % 5, 7 - i / 15, 4 + i % 15 / 5), ores.get(i));
        supplyAll(h);
        h.succeedWhen(() -> {
            for (var entry : expected.entrySet()) {
                int actual = thrown(h, entry.getKey(), new BlockPos(0, 1, 0), new BlockPos(3, 9, 3));
                h.assertTrue(actual == entry.getValue(), "Exact ore output for " + entry.getKey() + ": " + actual + "/" + entry.getValue()
                    + " status=" + ((QuarryDriveBlockEntity) h.getBlockEntity(DRIVE)).status());
            }
            for (int i = 0; i < ores.size(); i++) h.assertTrue(h.getBlockState(new BlockPos(4 + i % 5, 7 - i / 15, 4 + i % 15 / 5)).isAir(), "Every test ore was mined");
            var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
            h.assertTrue(quarry.conveyor().contents().stream().noneMatch(stack -> expected.containsKey(stack.getItem())), "No test ore remains in transit");
            System.out.println("QUARRY_ORE_AUDIT mined=" + ores.size() + " recovered=" + expected.values().stream().mapToInt(Integer::intValue).sum() + " types=" + expected.size() + " rpm=256 steps=" + QuarryDriveBlockEntity.WORK_STEPS);
        });
    }

    @GameTest(template = "test_rbmk")
    public static void blockedOreParcelsSurviveReloadAndPartialUnloading(GameTestHelper h) {
        var conveyor = new QuarryConveyor(() -> { });
        var original = new java.util.HashMap<Item, Integer>();
        for (int slot = 0; slot < QuarryConveyor.HEAD_SLOTS; slot++) {
            Item item = slot % 2 == 0 ? Items.RAW_IRON : Items.RAW_GOLD;
            conveyor.collect(java.util.List.of(new ItemStack(item, 64)));
            original.merge(item, 64, Integer::sum);
        }
        for (long now = 0; now < 400; now++) conveyor.tick(now, 120, 32, stack -> stack);
        var saved = new net.minecraft.nbt.CompoundTag();
        conveyor.write(saved, h.getLevel().registryAccess(), false, 400);
        var restored = new QuarryConveyor(() -> { });
        restored.read(saved, h.getLevel().registryAccess(), false);
        for (var entry : original.entrySet()) h.assertTrue(restored.contents().stream().filter(stack -> stack.is(entry.getKey())).mapToInt(ItemStack::getCount).sum() == entry.getValue(), "Reload preserves each ore type");
        var received = new java.util.HashMap<Item, Integer>();
        for (long now = 400; now < 8000; now++) restored.tick(now, 120, 32, stack -> {
            int accepted = Math.min(7, stack.getCount());
            received.merge(stack.getItem(), accepted, Integer::sum);
            return stack.copyWithCount(stack.getCount() - accepted);
        });
        h.assertTrue(restored.contents().isEmpty() && received.equals(original), "Every saved ore arrives exactly once after partial unloading: " + received);
        System.out.println("QUARRY_ORE_RELOAD before=576 after=" + received.values().stream().mapToInt(Integer::intValue).sum());
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void aBlockedBridgePassesBackpressureToTheDrill(GameTestHelper h) {
        var conveyor = new QuarryConveyor(() -> { });
        var drops = List.of(new ItemStack(Items.COBBLESTONE, QuarryConveyor.PARCEL));
        double width = 64 + 1 - QuarryConveyor.CHUTE_OFFSET;
        int produced = 0;
        for (long now = 0; now < 2_000; now++) {
            if (conveyor.fits(drops)) {
                conveyor.collect(drops);
                produced += QuarryConveyor.PARCEL;
            }
            conveyor.tick(now, 6, width, stack -> stack);
        }
        h.assertTrue(!conveyor.fits(drops), "Backpressure reaches the head bin and pauses digging");
        h.assertTrue(conveyor.parcelCount() == 33 + QuarryConveyor.MAX_RISING, "The 64-wide bridge holds at most 33 parcels");
        h.assertTrue(conveyor.contents().stream().mapToInt(ItemStack::getCount).sum() == produced, "The blocked route holds every mined item");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void aLegacyBacklogIsSavedInFullButSendsOnlyVisibleParcels(GameTestHelper h) throws IOException {
        h.setBlock(DRIVE, CECrane.QUARRY_DRIVE.get().defaultBlockState());
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(DRIVE);
        var lookup = h.getLevel().registryAccess();
        var saved = quarry.saveWithoutMetadata(lookup);
        var parcels = new ListTag();
        var namedOre = new ItemStack(Items.RAW_IRON, 16);
        namedOre.set(DataComponents.CUSTOM_NAME, Component.literal("Quarry sample"));
        var goldOre = new ItemStack(Items.RAW_GOLD, 16);
        int count = 11_683;
        long lastLaunch = 2_000_003;
        for (int i = 0; i < count; i++) {
            var entry = new CompoundTag();
            entry.put("Stack", (i % 2 == 0 ? namedOre : goldOre).save(lookup));
            entry.putBoolean("Bridge", i < count - QuarryConveyor.MAX_RISING);
            entry.putLong("Start", lastLaunch - (count - i - 1) * QuarryConveyor.LAUNCH_GAP);
            entry.putInt("Duration", 258);
            parcels.add(entry);
        }
        saved.put("Parcels", parcels);
        saved.putLong("Now", 87_474);
        saved.putLong("LastLaunch", lastLaunch);
        quarry.loadWithComponents(saved, lookup);
        long clock = quarry.conveyorTime();
        h.assertTrue(clock == 2_000_004, "Legacy start times are no longer in the future");
        h.assertTrue(parcels.equals(quarry.saveWithoutMetadata(lookup).getList("Parcels", Tag.TAG_COMPOUND)),
            "Every legacy parcel, order, item component and travel timestamp survives a save");

        var packet = quarry.getUpdateTag(lookup);
        int visible = packet.getList("Parcels", Tag.TAG_COMPOUND).size();
        var oldPacket = packet.copy();
        oldPacket.remove("ParcelCount");
        oldPacket.put("Parcels", parcels);
        var client = new QuarryConveyor(() -> { });
        client.read(packet, lookup, true);
        h.assertTrue(client.parcelCount() == count && client.contents().size() == visible
            && visible > QuarryConveyor.MAX_RISING && visible <= QuarryConveyor.MAX_VISIBLE_PARCELS,
            "The client receives a bounded visual list and the full count for goggles");
        client.read(oldPacket, lookup, true);
        h.assertTrue(client.parcelCount() == count && client.contents().size() == QuarryConveyor.MAX_VISIBLE_PARCELS,
            "A full update from an older server is also bounded at client read");
        System.out.println("QUARRY_BACKLOG saved=" + count + " visible=" + visible
            + " oldUpdateBytes=" + nbtSize(oldPacket) + " updateBytes=" + nbtSize(packet));

        var conveyor = quarry.conveyor();
        int[] delivered = {0};
        for (long now = clock; now < clock + 40_000 && conveyor.parcelCount() > 0; now++) {
            conveyor.tick(now, 120, 64, stack -> {
                ItemStack expected = delivered[0] / 16 % 2 == 0 ? namedOre : goldOre;
                h.assertTrue(ItemStack.isSameItemSameComponents(stack, expected) && stack.getCount() == 16 - delivered[0] % 16,
                    "Legacy parcels unload in order with their item components and remaining quantities");
                int accepted = Math.min(7, stack.getCount());
                delivered[0] += accepted;
                return stack.copyWithCount(stack.getCount() - accepted);
            });
        }
        h.assertTrue(delivered[0] == count * 16 && conveyor.contents().isEmpty(), "Every legacy item unloads exactly once");
        System.out.println("QUARRY_BACKLOG delivered=" + delivered[0]);
        h.succeed();
    }

    private static int nbtSize(CompoundTag tag) throws IOException {
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }
}
