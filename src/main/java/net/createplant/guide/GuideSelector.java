package net.createplant.guide;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/** Picks the candidate that needs the fewest more blocks, reusing what is already built. */
public final class GuideSelector {
    /** The chosen layout and the cells still to place. */
    public record Plan(GhostLayout layout, List<GhostLayout.Cell> missing) { }

    private GuideSelector() { }

    /**
     * A candidate is dropped when a part of it is unloaded or held by a block that is not replaceable, because the guide never
     * asks the player to break anything. Among the rest the fewest missing blocks win, then the smaller structure; null when none fits.
     */
    public static Plan choose(Level level, List<GhostLayout> layouts) {
        Plan best = null;
        for (GhostLayout layout : layouts) {
            List<GhostLayout.Cell> missing = new ArrayList<>();
            boolean possible = true;
            for (GhostLayout.Cell cell : layout.cells()) {
                if (!level.hasChunkAt(cell.pos())) { possible = false;break; }
                if (cell.accepts().test(level, cell.pos())) continue;
                var present = level.getBlockState(cell.pos());
                // A cell that must stay free is never filled, so whatever blocks it rules the candidate out.
                if (cell.ghost() != null && (present.isAir() || present.canBeReplaced())) missing.add(cell);
                else { possible = false;break; }
                if (best != null && missing.size() > best.missing().size()) { possible = false;break; }
            }
            if (!possible || missing.isEmpty()) continue;
            if (best == null || missing.size() < best.missing().size() || missing.size() == best.missing().size() && layout.cells().size() < best.layout().cells().size())
                best = new Plan(layout, missing);
        }
        return best;
    }
}
