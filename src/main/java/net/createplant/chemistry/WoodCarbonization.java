package net.createplant.chemistry;

import net.createplant.block.CharredWoodBlock;
import net.createplant.block.entity.CharredWoodBlockEntity;
import net.createplant.registry.CECarbonization;
import net.createplant.registry.CEUranium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class WoodCarbonization {
    private WoodCarbonization() { }

    public static boolean accepts(BlockState state) { return !state.hasBlockEntity() && (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)); }

    public static boolean tryStart(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState original = level.getBlockState(pos);
        if (!accepts(original)) return false;
        if (level.isClientSide) return true;
        Direction.Axis axis = original.hasProperty(BlockStateProperties.AXIS) ? original.getValue(BlockStateProperties.AXIS) : Direction.Axis.Y;
        BlockState charred = CECarbonization.CHARRED_WOOD.get().defaultBlockState().setValue(CharredWoodBlock.STAGE, 0)
            .setValue(CharredWoodBlock.LOGS, original.is(BlockTags.LOGS)).setValue(CharredWoodBlock.AXIS, axis);
        // Neighbours are notified after a source is consumed, so one acid source cannot react twice.
        if (!level.setBlock(pos, charred, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return false;
        if (level.getBlockEntity(pos) instanceof CharredWoodBlockEntity wood) wood.begin(original);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .4f, .8f);
        return true;
    }

    public static boolean tryFromSource(ServerLevel level, BlockPos sourcePos) {
        if (!level.hasChunkAt(sourcePos)) return false;
        var fluid = level.getFluidState(sourcePos);
        if (!fluid.isSource() || fluid.getType() != CEUranium.SULFURIC_ACID.get()) return false;
        for (Direction side : Direction.values()) {
            BlockPos woodPos = sourcePos.relative(side);
            if (!level.hasChunkAt(woodPos) || !accepts(level.getBlockState(woodPos))) continue;
            if (!tryStart(level, woodPos)) continue;
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.updateNeighborsAt(woodPos, CECarbonization.CHARRED_WOOD.get());
            return true;
        }
        return false;
    }
}
