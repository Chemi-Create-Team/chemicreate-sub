package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import java.util.List;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.CEGasifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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

public final class GasifierBlock extends Block implements IBE<GasifierBlockEntity>, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public enum Role {
        CASING("gasifier_casing"), WALL("water_cooled_refractory_wall"), NOZZLE("entrained_burner_nozzle"),
        HEARTH("slag_tap_hearth"), WINDOW("quench_sight_glass");
        public final String id;
        Role(String id) { this.id = id; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Role role;

    public GasifierBlock(Role role, Properties properties) {
        super(properties);
        this.role = role;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override public Class<GasifierBlockEntity> getBlockEntityClass() { return GasifierBlockEntity.class; }
    @Override public BlockEntityType<? extends GasifierBlockEntity> getBlockEntityType() { return CEGasifier.PART.get(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return role == Role.CASING ? IBE.super.getTicker(level, state, type) : null;
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

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state != next && level.getBlockEntity(pos) instanceof GasifierBlockEntity part) part.invalidateStructure(!state.is(next.getBlock()));
        super.onRemove(state, level, pos, next, moving);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moving) {
        super.neighborChanged(state, level, pos, block, neighbor, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GasifierBlockEntity part) {
            var owner = part.owner();
            if (owner != null) owner.cellChanged(neighbor);
        }
    }
}
