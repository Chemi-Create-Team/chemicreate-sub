package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorConsoleGameTests {
    private static final BlockPos ROOT = new BlockPos(10, 2, 10);

    @GameTest(template = "test_rbmk")
    public static void identicalUnitsFormAndButtonsFaceNorth(GameTestHelper h) {
        formation(h, Direction.NORTH);
    }
    @GameTest(template = "test_rbmk")
    public static void identicalUnitsFormAndButtonsFaceEast(GameTestHelper h) {
        formation(h, Direction.EAST);
    }
    @GameTest(template = "test_rbmk")
    public static void identicalUnitsFormAndButtonsFaceSouth(GameTestHelper h) {
        formation(h, Direction.SOUTH);
    }
    @GameTest(template = "test_rbmk")
    public static void identicalUnitsFormAndButtonsFaceWest(GameTestHelper h) {
        formation(h, Direction.WEST);
    }

    private static void formation(GameTestHelper h, Direction facing) {
        var desk = ReactorConsoleStructure.DESK;
        var state = CENuclear.REACTOR_CONTROL_CONSOLE.get().defaultBlockState().setValue(ReactorControlConsoleBlock.FACING, facing);
        for (int section : new int[] {2, 0, 3})
            h.setBlock(ROOT.offset(desk.offset(section, facing)), state);
        h.assertTrue(!desk.tryForm(h.getLevel(), h.absolutePos(ROOT)), "A missing desk unit prevents formation");
        BlockPos lastPos = ROOT.offset(desk.offset(1, facing));
        h.setBlock(lastPos, state.setValue(ReactorControlConsoleBlock.FACING, facing.getClockWise()));
        h.assertTrue(!desk.tryForm(h.getLevel(), h.absolutePos(ROOT)), "A wrongly facing unit prevents formation");
        h.setBlock(lastPos, state);
        h.assertTrue(desk.tryForm(h.getLevel(), h.absolutePos(lastPos)), "The last raw unit forms the desk in arbitrary placement order");
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(ROOT);
        for (int section = 0; section < desk.cells; section++) {
            BlockPos part = ROOT.offset(desk.offset(section, facing));
            h.assertTrue(h.getBlockState(part).getValue(ReactorControlConsoleBlock.SECTION) == section
                    && ReactorControlConsoleBlockEntity.masterAt(h.getLevel(), h.absolutePos(part)).equals(h.absolutePos(ROOT))
                    && MachineReadings.owner(h.getBlockEntity(part)) == console,
                "Every identical desk unit receives one consistent positional role");
            var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            ((ReactorControlConsoleBlockEntity) h.getBlockEntity(part)).addToGoggleTooltip(tooltip, false);
            h.assertTrue(tooltip.size() == 4, "Every desk unit delegates four useful goggle rows to the master");
        }
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(false);
        var menu = player.containerMenu;
        for (var button : ReactorConsoleButton.values()) {
            h.assertTrue(click(h, ROOT, facing, button).consumesAction(),
                "The visible " + button + " control has a physical hit region in " + facing);
            if (button.dial())
                h.assertTrue(click(h, ROOT, facing, button, false).consumesAction(), "Both halves of the " + button + " dial respond");
        }
        h.assertTrue(player.containerMenu == menu, "Console interaction never opens a menu");
        h.assertTrue(!console.guardOpen() && console.acknowledged(), "RESET closes the AZ-5 cover and ACK operates on an unlinked station");
        click(h, ROOT, facing, ReactorConsoleButton.AZ5);
        h.assertTrue(console.guardOpen(), "The first AZ-5 press only opens the cover");
        click(h, ROOT, facing, ReactorConsoleButton.ACKNOWLEDGE, true, true);
        h.assertTrue(console.lampTest(), "Sneaking on ACK runs the lamp test");
        BlockPos deskUnit = ROOT.offset(desk.offset(2, facing));
        h.setBlock(deskUnit, Blocks.AIR);
        h.assertTrue(!console.assembled() && !console.stationComplete(), "Removing a desk unit invalidates the one master immediately");
        h.setBlock(deskUnit, state);
        h.assertTrue(desk.tryForm(h.getLevel(), h.absolutePos(deskUnit)) && console.stationComplete(), "Replacement of one unit repairs the original desk");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void theBoardFormsOnItsOwnAndLinksByRightClickingWithTheOtherItem(GameTestHelper h) {
        var desk = ReactorConsoleStructure.DESK;
        var display = ReactorConsoleStructure.DISPLAY;
        desk.place(h::setBlock, ROOT, Direction.NORTH);
        h.assertTrue(desk.tryForm(h.getLevel(), h.absolutePos(ROOT)), "Four desk units form a station on their own");
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(ROOT);
        h.assertTrue(console.assembled() && !console.walled(), "A desk without a board operates without the board displays");
        h.assertTrue(click(h, ROOT, Direction.NORTH, ReactorConsoleButton.AZ5).consumesAction() && console.guardOpen(), "The desk controls work without a board");

        BlockPos board = new BlockPos(3, 2, 6);
        var raw = CENuclear.REACTOR_CONSOLE_DISPLAY.get().defaultBlockState().setValue(net.createplant.block.ReactorConsoleDisplayBlock.FACING, Direction.NORTH);
        BlockPos last = board.offset(display.offset(display.cells - 1, Direction.NORTH));
        for (int section = 0; section < display.cells - 1; section++)
            h.setBlock(board.offset(display.offset(section, Direction.NORTH)), raw);
        h.assertTrue(!display.tryForm(h.getLevel(), h.absolutePos(board)), "A missing display unit prevents the board from forming");

        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var boardItem = CENuclear.REACTOR_CONSOLE_DISPLAY_ITEM.get().getDefaultInstance();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, boardItem);
        var deskHit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(ROOT)), Direction.NORTH, h.absolutePos(ROOT), false);
        h.getBlockState(ROOT).useItemOn(boardItem, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, deskHit);
        h.assertTrue(h.absolutePos(ROOT).equals(net.createplant.nuclear.ReactorConsoleLink.remembered(boardItem)),
            "Right-clicking a formed desk with a display unit remembers that desk on the item");
        var below = h.absolutePos(last.below());
        var placeHit = new BlockHitResult(Vec3.atCenterOf(below).add(0, .5, 0), Direction.UP, below, false);
        boardItem.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, placeHit));
        h.assertTrue(h.getBlockState(last).is(CENuclear.REACTOR_CONSOLE_DISPLAY.get())
                && h.getBlockState(last).getValue(net.createplant.block.ReactorConsoleDisplayBlock.FACING) == Direction.NORTH,
            "The remembering display unit is placed facing the operator");
        h.assertTrue(display.tryForm(h.getLevel(), h.absolutePos(last)), "Placing the remembering unit completes the board");
        var boardEntity = (net.createplant.block.entity.ReactorConsoleDisplayBlockEntity) h.getBlockEntity(board);
        h.assertTrue(boardEntity.linkedConsole() == console && console.walled() && h.absolutePos(board).equals(console.display()),
            "The completed board links itself to the remembered desk");

        BlockPos unit = board.offset(display.offset(5, Direction.NORTH));
        h.setBlock(unit, Blocks.AIR);
        console.lazyTick();
        h.assertTrue(!boardEntity.assembled() && !console.walled() && console.assembled(),
            "Breaking a display unit takes the board offline while the desk keeps operating");
        h.setBlock(unit, raw);
        display.tryForm(h.getLevel(), h.absolutePos(unit));
        console.lazyTick();
        h.assertTrue(boardEntity.assembled() && console.walled(), "Repairing the board restores the existing link");
        h.succeed();
    }

    public static InteractionResult click(GameTestHelper h, BlockPos root, Direction facing, ReactorConsoleButton button) {
        return click(h, root, facing, button, true);
    }

    /** Clicks the centre of a control, or a dial's operator-left half when {@code up} is false. */
    public static InteractionResult click(GameTestHelper h, BlockPos root, Direction facing, ReactorConsoleButton button, boolean up) {
        return click(h, root, facing, button, up, false);
    }

    private static InteractionResult click(GameTestHelper h, BlockPos root, Direction facing, ReactorConsoleButton button, boolean up, boolean sneaking) {
        BlockPos pos = h.absolutePos(root.offset(ReactorConsoleStructure.DESK.offset(button.column, facing)));
        var right = facing.getCounterClockWise();
        double u = (button.u + (up ? 0 : -button.radius / 2)) / 16d - .5, v = button.v / 16d - .5;
        Vec3 point = Vec3.atLowerCornerOf(pos).add(.5 + right.getStepX() * u - facing.getStepX() * v,
            (11.25 + Math.min(3, button.v / 4)) / 16d, .5 + right.getStepZ() * u - facing.getStepZ() * v);
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        var held = player.getMainHandItem();boolean wasSneaking = player.isShiftKeyDown();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(sneaking);
        try {
            return h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), player, new BlockHitResult(point, Direction.UP, pos, false));
        } finally {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            player.setShiftKeyDown(wasSneaking);
        }
    }

    @GameTest(template = "test_rbmk")
    public static void chartsAndModesSurviveReloadWithBoundedData(GameTestHelper h) {
        RbmkGameTests.controlRoom(h, ROOT, Direction.NORTH);
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(ROOT);
        var tag = console.saveWithFullMetadata(h.getLevel().registryAccess());
        tag.putInt("ControlMode", 2);
        tag.putInt("BankTarget", 37);
        tag.putDouble("ControlTarget", 42.3);
        tag.putBoolean("GuardOpen", true);
        tag.putInt("ChartCount", 48);
        tag.putInt("ChartCursor", 7);
        byte[] samples = new byte[48];
        for (int i = 0; i < 48; i++) samples[i] = (byte) (i + 10);
        tag.putByteArray("WaterTrace", samples);
        tag.putByteArray("GraphiteTrace", samples);
        console.loadWithComponents(tag, h.getLevel().registryAccess());
        var saved = console.saveWithFullMetadata(h.getLevel().registryAccess());
        console.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(Math.abs(console.saveWithFullMetadata(h.getLevel().registryAccess()).getDouble("ControlTarget")-42.3)<1e-9
            && console.mode() == 2 && console.bankTarget() == 37 && console.controlTarget() == 42 && console.guardOpen()
                && console.chartCount() == 48 && console.waterSample(0) == 17 && console.graphiteSample(47) == 16,
            "Reload preserves modes, guard and chronological paper traces");
        tag.putInt("ControlMode", 1000);
        tag.putInt("BankTarget", -50);
        tag.putInt("ChartCount", Integer.MAX_VALUE);
        tag.putInt("ChartCursor", -100);
        tag.putByteArray("WaterTrace", new byte[10000]);
        console.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(console.mode() == 2 && console.bankTarget() == 0 && console.chartCount() == 48
                && console.saveWithFullMetadata(h.getLevel().registryAccess()).getByteArray("WaterTrace").length == 48,
            "Malformed saved settings and trace length are bounded");
        h.succeed();
    }
}
