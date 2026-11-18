package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import java.util.List;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.CEGasifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * The steam gasifier block. Laid 3x3 and six layers high it forms one furnace; {@link #CELL} then tells each block its place, which picks its
 * look: the iron base and lid bands, the brick-lined reaction bed with sight glasses, the charging hatch and the gas outlet on the front.
 */
public final class GasifierBlock extends Block implements IBE<GasifierBlockEntity>, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 0 for a loose block, otherwise 1 + layer * 9 + (dx + 1) * 3 + (dz + 1) from the bottom centre. */
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 54);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int CONTROLLER = cell(0, 0, 0);

    public GasifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(CELL, 0).setValue(LIT, false));
    }

    public static int cell(int layer, int dx, int dz) { return 1 + layer * 9 + (dx + 1) * 3 + dz + 1; }
    public static boolean viewport(int cell) {
        int layer = (cell - 1) / 9, column = (cell - 1) % 9;
        return cell > 0 && (layer == 2 || layer == 3) && column % 2 == 1;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, CELL, LIT); }
    /** The side toward the player; the block that caps the roof's centre sets the furnace's front, where the gas outlet is. */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    /** A wrench turns only loose blocks; a formed furnace keeps its front. */
    @Override public BlockState getRotatedBlockState(BlockState original, Direction targetedFace) {
        return original.getValue(CELL) == 0 ? com.simibubi.create.content.equipment.wrench.IWrenchable.super.getRotatedBlockState(original, targetedFace) : original;
    }
    @Override public Class<GasifierBlockEntity> getBlockEntityClass() { return GasifierBlockEntity.class; }
    @Override public BlockEntityType<? extends GasifierBlockEntity> getBlockEntityType() { return CEGasifier.PART.get(); }
    /** Only a formed furnace's bottom centre ticks; loose blocks wait for a placement to check them. */
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(CELL) == CONTROLLER ? IBE.super.getTicker(level, state, type) : null;
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof GasifierBlockEntity be && be.hasStoredContents()) {
            for (var stack : drops) if (stack.is(asItem())) {
                var tag = new CompoundTag();
                be.writeInventory(tag, be.getLevel().registryAccess());
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
            }
        }
        return drops;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(CELL) == 0) GasifierBlockEntity.formAround(level, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof GasifierBlockEntity part) part.invalidateStructure(true);
        super.onRemove(state, level, pos, next, moving);
    }
}
