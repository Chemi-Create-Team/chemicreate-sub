package net.createplant.block.entity;

import net.createplant.block.CharredWoodBlock;
import net.createplant.registry.CECarbonization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CharredWoodBlockEntity extends BlockEntity {
    public static final int PROCESS_TICKS = 600;
    private int remaining;
    private ItemStack original = ItemStack.EMPTY;

    public CharredWoodBlockEntity(BlockPos pos, BlockState state) { super(CECarbonization.CHARRED_WOOD_ENTITY.get(), pos, state); }
    public int remainingTicks() { return remaining; }
    public ItemStack originalItem() { return original.copy(); }

    public void begin(BlockState source) {
        original = new ItemStack(source.getBlock());
        remaining = PROCESS_TICKS;
        setChanged();
        if (level != null && !level.isClientSide) level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
    }

    public void advance() {
        if (level == null || level.isClientSide || getBlockState().getValue(CharredWoodBlock.STAGE) >= 3) return;
        remaining = Math.max(0, remaining - 20);
        int stage = remaining == 0 ? 3 : Math.min(2, (PROCESS_TICKS - remaining) / 200);
        if (getBlockState().getValue(CharredWoodBlock.STAGE) != stage)
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(CharredWoodBlock.STAGE, stage));
        if (remaining == 0) original = ItemStack.EMPTY;
        else level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
        setChanged();
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && getBlockState().getValue(CharredWoodBlock.STAGE) < 3)
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("CarbonizationRemaining", remaining);
        if (!original.isEmpty()) tag.put("CarbonizationOriginal", original.save(lookup));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        remaining = Math.clamp(tag.getInt("CarbonizationRemaining"), 0, PROCESS_TICKS);
        original = ItemStack.parseOptional(lookup, tag.getCompound("CarbonizationOriginal"));
        if (!original.isEmpty()) original.setCount(1);
    }
}
