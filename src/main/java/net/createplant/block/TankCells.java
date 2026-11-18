package net.createplant.block;

import java.util.function.Consumer;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The cells of a Create tank multiblock, whichever way its length runs: width x width across, height along the main connection axis. */
public final class TankCells {
    private TankCells() { }

    /** The cell at the given step along the main axis and the two steps across it, counted from the controller. */
    public static BlockPos at(FluidTankBlockEntity controller, int along, int u, int v) {
        BlockPos origin = controller.getBlockPos();
        return switch (controller.getMainConnectionAxis()) {
            case X -> origin.offset(along, u, v);
            case Z -> origin.offset(u, v, along);
            default -> origin.offset(u, along, v);
        };
    }

    public static void forEach(FluidTankBlockEntity controller, Consumer<BlockPos> action) {
        for (int along = 0; along < controller.getHeight(); along++) for (int u = 0; u < controller.getWidth(); u++) for (int v = 0; v < controller.getWidth(); v++)
            action.accept(at(controller, along, u, v));
    }

    /** The cells of the lowest layer, the ones standing on whatever heats the tank. */
    public static void forEachBase(FluidTankBlockEntity controller, Consumer<BlockPos> action) {
        if (controller.getMainConnectionAxis() == Direction.Axis.Y) {
            for (int u = 0; u < controller.getWidth(); u++) for (int v = 0; v < controller.getWidth(); v++) action.accept(at(controller, 0, u, v));
            return;
        }
        for (int along = 0; along < controller.getHeight(); along++) for (int across = 0; across < controller.getWidth(); across++)
            action.accept(controller.getMainConnectionAxis() == Direction.Axis.X ? at(controller, along, 0, across) : at(controller, along, across, 0));
    }
}
