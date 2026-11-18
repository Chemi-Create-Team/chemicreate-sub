package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ElectrolysisFurnacePartBlockEntity;
import net.createplant.registry.CEFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Insulated shell, or the carbon anode at the top centre, of the electrolysis furnace. A formed shell takes the shape of its
 * place in the 5 × 5 × 3 vessel (cell = layer × 25 + row × 5 + column from the north-west, {@link #LOOSE} when not formed), and
 * every part glows while the bath is molten.
 */
public class ElectrolysisFurnacePartBlock extends Block implements IBE<ElectrolysisFurnacePartBlockEntity> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LOOSE = 75;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, LOOSE);
    public ElectrolysisFurnacePartBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(CELL, LOOSE)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(LIT, CELL); }
    @Override public Class<ElectrolysisFurnacePartBlockEntity> getBlockEntityClass() { return ElectrolysisFurnacePartBlockEntity.class; }
    @Override public BlockEntityType<? extends ElectrolysisFurnacePartBlockEntity> getBlockEntityType() { return CEFurnace.PART_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(state.getBlock()) && level.getBlockEntity(pos) instanceof ElectrolysisFurnacePartBlockEntity part)
            part.structureChanged();
        IBE.onRemove(state, level, pos, newState);
    }
}
