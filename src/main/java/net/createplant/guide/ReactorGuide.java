package net.createplant.guide;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.ReactorStructure;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The stirred tank's footprint and every layer count accepted over its stirrer. */
public final class ReactorGuide implements StructureGuide {
    @Override public BlockPos anchor(BlockEntity be) {
        return be instanceof LiddedVesselBlockEntity vessel && vessel.isController() && vessel.module() != ReactorModule.NONE && !vessel.formed() ? be.getBlockPos() : null;
    }

    @Override public List<GhostLayout> layouts(Level level, BlockPos base) {
        if (!(level.getBlockEntity(base) instanceof LiddedVesselBlockEntity vessel)) return List.of();
        return layouts(base, vessel.module(),vessel.getWidth());
    }

    public static List<GhostLayout> layouts(BlockPos base, ReactorModule module) {
        return layouts(base,module,3);
    }
    private static List<GhostLayout> layouts(BlockPos base, ReactorModule module,int width) {
        if (module == ReactorModule.NONE) return List.of();
        List<GhostLayout> result = new ArrayList<>();
        var vessel = CETransport.LIDDED_VESSEL.get().defaultBlockState();
        for (int sections = width==1 ? 1 : Math.max(module.minSections, LiddedVesselBlockEntity.IMPELLER_LAYERS); sections <= ReactorStructure.MAX_SECTIONS; sections++) {
            List<GhostLayout.Cell> cells = new ArrayList<>();
            for (int y = 0; y < sections; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                cells.add(new GhostLayout.Cell(base.offset(x, y, z), vessel, ReactorStructure::matches));
            result.add(new GhostLayout(sections + " sections", cells));
        }
        return result;
    }
}
