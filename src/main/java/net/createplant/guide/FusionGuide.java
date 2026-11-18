package net.createplant.guide;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.fusion.FusionStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exact ghost of the detected tokamak size and controller orientation. */
public final class FusionGuide implements StructureGuide {
    @Override public BlockPos anchor(BlockEntity be) {
        return be instanceof FusionReactorControllerBlockEntity controller && !controller.formed() ? be.getBlockPos() : null;
    }

    @Override public int limit() { return 1; }

    @Override public List<GhostLayout> layouts(Level level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof FusionReactorControllerBlockEntity controller)) return List.of();
        List<GhostLayout.Cell> cells = new ArrayList<>();
        for (FusionStructure.Cell expected : FusionStructure.cells(controllerPos, controller.inward(), controller.structureWidth())) {
            if (expected.role() == FusionStructure.Role.CLEAR) {
                cells.add(GhostLayout.Cell.free(expected.pos(), (actual, at) -> actual.getBlockState(at).isAir()));
            } else {
                cells.add(new GhostLayout.Cell(expected.pos(), expected.expected(),
                    (actual, at) -> FusionStructure.matches(actual.getBlockState(at), expected)));
            }
        }
        return List.of(new GhostLayout(controller.structureWidth() + " x " + controller.structureWidth()
            + " x " + net.createplant.fusion.FusionPhysics.HEIGHT + " D-T tokamak", List.copyOf(cells)));
    }
}
