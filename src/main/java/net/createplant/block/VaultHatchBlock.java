package net.createplant.block;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.VaultHatchBlockEntity;
import net.createplant.registry.CEVault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VaultHatchBlock extends DirectionalBlock implements IBE<VaultHatchBlockEntity>, com.simibubi.create.content.equipment.wrench.IWrenchable {
    @Override protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() { return simpleCodec(VaultHatchBlock::new); }

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public VaultHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos support = pos.relative(state.getValue(FACING).getOpposite());
        return level.hasChunkAt(support) && level.getBlockEntity(support) instanceof ItemVaultBlockEntity;
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
        LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return side == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof VaultHatchBlockEntity hatch) hatch.open(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) { return InteractionResult.PASS; }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof VaultHatchBlockEntity hatch) hatch.recheckOpen();
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(state.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) return switch (state.getValue(FACING)) {
            case NORTH -> box(0, 13, 0, 16, 16, 16);
            case SOUTH -> box(0, 13, 0, 16, 16, 16);
            case WEST -> box(0, 13, 0, 16, 16, 16);
            case EAST -> box(0, 13, 0, 16, 16, 16);
            case UP -> box(0, 0, 13, 16, 16, 16);
            case DOWN -> box(0, 0, 0, 16, 16, 3);
        };
        return switch (state.getValue(FACING)) {
            case NORTH -> box(0, 0, 13, 16, 16, 16);
            case SOUTH -> box(0, 0, 0, 16, 16, 3);
            case WEST -> box(13, 0, 0, 16, 16, 16);
            case EAST -> box(0, 0, 0, 3, 16, 16);
            case UP -> box(0, 0, 0, 16, 3, 16);
            case DOWN -> box(0, 13, 0, 16, 16, 16);
        };
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public Class<VaultHatchBlockEntity> getBlockEntityClass() { return VaultHatchBlockEntity.class; }
    @Override public BlockEntityType<? extends VaultHatchBlockEntity> getBlockEntityType() { return CEVault.HATCH_ENTITY.get(); }
}
