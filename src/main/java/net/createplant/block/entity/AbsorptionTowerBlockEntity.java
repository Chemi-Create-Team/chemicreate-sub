package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.List;
import net.createplant.chemistry.ReactorModule;
import net.createplant.mixture.GasMixturePort;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

    /** Every storey shows the tower that its bottom storey runs. */
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        var owner = bottomStorey();
        return owner == null || owner == this ? super.addToGoggleTooltip(text, sneaking) : owner.addToGoggleTooltip(text, sneaking);
    }

    @Override protected void structureTooltip(List<Component> text) {
        text.add(Component.translatable("reactor.create_refinery_n_reactors.tower_structure", sections(), MAX_STOREYS, CAPACITY, CAPACITY * 64));
        text.add(Component.translatable("reactor.create_refinery_n_reactors.tower_ports"));
    }

    @Override protected List<BlockPos> storeyCells(BlockPos centre) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(centre.offset(x, 0, z));
        return cells;
    }

    /**
     * Pipes join only the centre and the four straight-side blocks of the octagon, never its diagonal corners. At the bottom storey their sides
     * take gas and give liquid and their undersides give liquid; at the top storey their sides give spent gas and their tops take water.
     */
    @Override protected IFluidHandler storeyPort(StackedReactorBlockEntity bottom, BlockPos cell, Direction side) {
        int x = cell.getX() - worldPosition.getX(), z = cell.getZ() - worldPosition.getZ();
        if (x != 0 && z != 0) return null;
        if (side == Direction.DOWN) return this == bottom ? bottom.processPort(2, 1, false, true) : null;
        if (side == Direction.UP) return top() ? bottom.processPort(1, 1, true, false) : null;
        if (x == 0 && z == 0 || side != Direction.fromDelta(x, 0, z)) return null;
        if (this == bottom) return GasMixturePort.inlet(LiddedVesselBlockEntity.combined(bottom.processPort(0, 1, true, false), bottom.processPort(2, 1, false, true)), bottom);
        return top() ? GasMixturePort.outlet(bottom.processPort(3, 1, false, true), bottom) : null;
    }
}
