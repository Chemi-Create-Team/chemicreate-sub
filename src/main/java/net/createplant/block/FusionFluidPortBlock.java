package net.createplant.block;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FusionFluidPortBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** One labelled penetration through the cryostat; each fluid has its own line so Create pipes cannot cross-contaminate it. */
public class FusionFluidPortBlock extends Block implements IBE<FusionFluidPortBlockEntity>, IProxyHoveringInformation {
    public enum Kind { DEUTERIUM, TRITIUM, ASH, HELIUM_IN, HELIUM_OUT, COOLANT_IN, COOLANT_OUT }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Kind kind;

    public FusionFluidPortBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        FusionReactorControllerBlockEntity controller = FusionReactorControllerBlockEntity.find(level, pos);
        return controller == null ? pos : controller.getBlockPos();
    }

    @Override public Class<FusionFluidPortBlockEntity> getBlockEntityClass() { return FusionFluidPortBlockEntity.class; }
    @Override public BlockEntityType<? extends FusionFluidPortBlockEntity> getBlockEntityType() { return CEFusion.PORT_ENTITY.get(); }
}
