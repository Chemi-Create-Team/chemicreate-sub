package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ControlRodControllerBlockEntity;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The control-rod controller, a lever of a hundred steps on an operator pedestal; it also fills one reactor-desk bay. */
public class ControlRodControllerBlock extends Block implements IBE<ControlRodControllerBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public ControlRodControllerBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) withBlockEntityDo(level, pos, controller -> controller.step(player.isShiftKeyDown()));
        return InteractionResult.SUCCESS;
    }

    @Override public Class<ControlRodControllerBlockEntity> getBlockEntityClass() { return ControlRodControllerBlockEntity.class; }
    @Override public BlockEntityType<? extends ControlRodControllerBlockEntity> getBlockEntityType() { return CENuclear.ROD_CONTROLLER_ENTITY.get(); }
}
