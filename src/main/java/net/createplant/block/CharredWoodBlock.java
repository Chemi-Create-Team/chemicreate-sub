package net.createplant.block;

import java.util.List;
import net.createplant.block.entity.CharredWoodBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class CharredWoodBlock extends Block implements EntityBlock {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    public static final BooleanProperty LOGS = BooleanProperty.create("logs");
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public CharredWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STAGE, 3).setValue(LOGS, false).setValue(AXIS, Direction.Axis.Y));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(STAGE, LOGS, AXIS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CharredWoodBlockEntity(pos, state); }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CharredWoodBlockEntity wood) wood.advance();
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (state.getValue(STAGE) < 3 && builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CharredWoodBlockEntity wood) {
            ItemStack original = wood.originalItem();
            return original.isEmpty() ? List.of() : List.of(original);
        }
        return super.getDrops(state, builder);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) < 3 && random.nextInt(5) == 0)
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                pos.getZ() + random.nextDouble(), 0, .015, 0);
    }
}
