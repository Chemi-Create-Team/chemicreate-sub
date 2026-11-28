package net.createplant.atomics.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.atomics.block.ReactorConsoleDisplayBlock;
import net.createplant.atomics.nuclear.ReactorConsoleStation;
import net.createplant.atomics.nuclear.ReactorConsoleStructure;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The 7 x 4 display board. It holds no reactor data of its own: it shows, and routes core-map clicks to, the desk it is linked to.
 * The desk owns the link; the board keeps the desk's position so that clients can find what to draw.
 */
public class ReactorConsoleDisplayBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, ReactorConsoleStation {
    public static final int MAP_SIZE = 4;
    /** The map leaves this margin, in blocks, around the scaled core footprint. */
    private static final double MAP_MARGIN = .15;
    private static final String KEY = "reactor_control_console.create_refinery_n_reactors.";
    private BlockPos console, remembered;
    private boolean assembled;

    public ReactorConsoleDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.REACTOR_CONSOLE_DISPLAY_ENTITY.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    /** Converts a map-face coordinate in blocks, from the map's lower left, into the core fraction used by the rod snapshot. */
    public static double mapFraction(double blocks) { return (blocks - MAP_MARGIN) / (MAP_SIZE - 2 * MAP_MARGIN); }
    /** The inverse of {@link #mapFraction}: a packed 0-255 core coordinate as blocks across the map face. */
    public static float mapBlocks(int packed) { return (float) (MAP_MARGIN + packed / 255d * (MAP_SIZE - 2 * MAP_MARGIN)); }

    public Direction facing() { return getBlockState().getValue(ReactorConsoleDisplayBlock.FACING); }
    public BlockPos wallPos(int column, int row) { return worldPosition.relative(facing().getCounterClockWise(), column).above(row); }
    public boolean isMaster() { return getBlockState().getValue(ReactorConsoleDisplayBlock.SECTION) == ReactorConsoleStructure.MASTER; }
    @Override public boolean assembled() { return assembled; }
    public BlockPos console() { return console; }

    public static BlockPos masterAt(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos))
            return null;
        var state = level.getBlockState(pos);
        BlockPos root = ReactorConsoleStructure.DISPLAY.master(pos, state);
        if (root == null || !level.hasChunkAt(root))
            return null;
        var lead = level.getBlockState(root);
        return lead.is(CENuclear.REACTOR_CONSOLE_DISPLAY.get()) && lead.getValue(ReactorConsoleDisplayBlock.SECTION) == ReactorConsoleStructure.MASTER
                && lead.getValue(ReactorConsoleDisplayBlock.FACING) == state.getValue(ReactorConsoleDisplayBlock.FACING)
            ? root
            : null;
    }
    private ReactorConsoleDisplayBlockEntity owner() {
        BlockPos root = level == null ? null : masterAt(level, worldPosition);
        return root != null && level.getBlockEntity(root) instanceof ReactorConsoleDisplayBlockEntity board ? board : null;
    }

    /** The desk that currently claims this board, on either side; null when unlinked, unloaded or claimed by another board. */
    public ReactorControlConsoleBlockEntity linkedConsole() {
        if (console == null || level == null || !assembled || !level.hasChunkAt(console)
            || !(level.getBlockEntity(console) instanceof ReactorControlConsoleBlockEntity desk))
            return null;
        return worldPosition.equals(desk.display()) ? desk : null;
    }
    public void linkTo(BlockPos desk) {
        if (desk.equals(console))
            return;
        console = desk;
        setChanged();
        sendData();
    }
    /** Ponder-only link to a desk, without the server-side formation checks. */
    public void previewLink(BlockPos desk) {
        console = desk;
        assembled = true;
    }
    /** Called when a unit is placed from a desk-unit item that remembered a desk. */
    public void remember(BlockPos desk) {
        remembered = desk;
        setChanged();
    }

    @Override public void stationChanged() {
        if (level == null || level.isClientSide || !isMaster())
            return;
        assembled = ReactorConsoleStructure.DISPLAY.complete(level, worldPosition, facing());
        if (assembled)
            for (int part = 0; part < ReactorConsoleStructure.DISPLAY.cells; part++) {
                var unit = worldPosition.offset(ReactorConsoleStructure.DISPLAY.offset(part, facing()));
                if (level.getBlockEntity(unit) instanceof ReactorConsoleDisplayBlockEntity board && board.remembered != null) {
                    if (level.hasChunkAt(board.remembered) && level.getBlockEntity(board.remembered) instanceof ReactorControlConsoleBlockEntity desk)
                        desk.linkDisplay(worldPosition);
                    board.remembered = null;
                    board.setChanged();
                }
            }
        sendData();
    }
    @Override public void invalidateStation() {
        assembled = false;
        if (level != null && !level.isClientSide)
            sendData();
    }

    @Override protected AABB createRenderBoundingBox() {
        return isMaster()
            ? AABB.encapsulatingFullBlocks(worldPosition, wallPos(ReactorConsoleStructure.DISPLAY.width - 1, ReactorConsoleStructure.DISPLAY.height - 1)).inflate(.25)
            : new AABB(worldPosition);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        if (console != null) tag.putLong("Console", console.asLong());
        if (!packet && remembered != null) tag.putLong("Remembered", remembered.asLong());
        tag.putBoolean("Assembled", assembled);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        console = tag.contains("Console") ? BlockPos.of(tag.getLong("Console")) : null;
        if (!packet) remembered = tag.contains("Remembered") ? BlockPos.of(tag.getLong("Remembered")) : null;
        assembled = tag.getBoolean("Assembled");
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var board = owner();
        var desk = board == null ? null : board.linkedConsole();
        if (desk != null)
            return desk.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(Component.translatable(KEY + (board == null || !board.assembled ? "link.display_incomplete" : "link.display_unlinked")));
        return true;
    }
}
