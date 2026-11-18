package net.createplant.block;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A horizontal octagonal gas tank in an open container frame. It becomes a tank only at the container sizes: 1 x 1 across and 2 long,
 * 2 x 2 and 5 long, 3 x 3 and 7 long. {@code size} is the width of the finished tank a block belongs to, or 0 while it is not part of one;
 * {@code cell} is the block's place across the tank, across + size * up, which picks the part of the frame and tank its model draws.
 */
public class GasTankContainerBlock extends HorizontalFluidTankBlock {
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 8);

    public GasTankContainerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SIZE, 0).setValue(CELL, 0));
    }

    /** The length a finished tank of this width has, or 0 for no container size. */
    public static int length(int width) { return switch (width) { case 1 -> 2; case 2 -> 5; case 3 -> 7; default -> 0; }; }

    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return CETransport.GAS_TANK_CONTAINER_ENTITY.get(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIZE, CELL);
    }
}
