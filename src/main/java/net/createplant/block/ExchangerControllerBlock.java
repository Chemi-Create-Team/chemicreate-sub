package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** The casing a complete exchanger run turns into its owner, at the bottom corner of its first end; it looks and drops as a casing. */
public class ExchangerControllerBlock extends ThermalMachineBlock {
    public final ExchangerStructure.Kind kind;

    public ExchangerControllerBlock(Properties properties, ExchangerStructure.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(kind.casing());
    }

    /** Without its controller the run is loose casings again. */
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && !level.isClientSide && level.getBlockEntity(pos) instanceof net.createplant.block.entity.ReactorMachineBlockEntity be)
            ReactorStructure.release(level, be.members());
        super.onRemove(state, level, pos, next, moving);
    }
}
