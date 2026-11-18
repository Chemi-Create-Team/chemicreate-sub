package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.List;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A counter-current absorption tower of four to eight storeys, a 3 × 3 octagon across. Gas rises from the bottom and water falls
 * from the top: the bottom storey takes the gas and gives the acid, the top storey takes the water and gives what the water did not hold.
 * Every storey runs one batch.
 */
public class AbsorptionTowerBlockEntity extends StackedReactorBlockEntity {
    public static final int MAX_STOREYS = 8;

    public AbsorptionTowerBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.ABSORPTION_ENTITY.get(), pos, state); }

    @Override public ReactorModule module() { return ReactorModule.TOWER; }
    @Override protected int minStoreys() { return ReactorModule.TOWER.minSections; }
    @Override protected int maxSections() { return MAX_STOREYS; }
    @Override public float rotationSpeed() { return formed() ? 32 : 0; }
    @Override public float calculateStressApplied() { return lastStressApplied = 0; }

    @Override protected List<BlockPos> storeyCells(BlockPos centre) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(centre.offset(x, 0, z));
        return cells;
    }

    @Override protected IFluidHandler storeyPort(StackedReactorBlockEntity bottom, Direction side) {
        if (side == Direction.DOWN) return null;
        if (this == bottom && side != Direction.UP) return LiddedVesselBlockEntity.combined(bottom.processPort(0, 1, true, false), bottom.processPort(2, 1, false, true));
        if (top()) return LiddedVesselBlockEntity.combined(bottom.processPort(1, 1, true, false), bottom.processPort(3, 1, false, true));
        return null;
    }
}
