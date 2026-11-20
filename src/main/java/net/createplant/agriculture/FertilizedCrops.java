package net.createplant.agriculture;

import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Sparse treatment data for one crop life at a position. */
public final class FertilizedCrops extends SavedData {
    private static final String FILE = "createplant_fertilized_crops";
    private static final Factory<FertilizedCrops> FACTORY = new Factory<>(FertilizedCrops::new, FertilizedCrops::load);
    private static final int CLEANUP_INTERVAL = 1200;

    private record Treatment(Block crop, FertilizerProfile profile, long expires, int uses, long removed) {
        boolean expired(long now) {
            return expires <= now || expires - now > profile.duration || removed >= 0 && removed != now;
        }
    }

    private final Long2ObjectOpenHashMap<Treatment> crops = new Long2ObjectOpenHashMap<>();

    private static FertilizedCrops existing(ServerLevel level) { return level.getDataStorage().get(FACTORY, FILE); }

    public static boolean growable(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
    }

    public static boolean apply(ServerLevel level, BlockPos pos, FertilizerProfile profile) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        if (!growable(state) || !state.canSurvive(level, pos)) return false;
        var data = level.getDataStorage().computeIfAbsent(FACTORY, FILE);
        long key = pos.asLong(), now = level.getGameTime();
        var active = data.crops.get(key);
        if (active != null && !active.expired(now) && active.removed < 0 && active.crop == state.getBlock()) return false;
        var crop = (CropBlock) state.getBlock();
        if (!level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), Block.UPDATE_CLIENTS)) return false;
        data.crops.put(key, new Treatment(crop, profile, now + profile.duration, profile.growthUses, -1));
        data.setDirty();
        return true;
    }

    public static void grew(ServerLevel level, BlockPos pos, BlockState before, BlockState reported) {
        if (!(reported.getBlock() instanceof CropBlock crop) || before.getBlock() != crop || crop.getAge(reported) <= crop.getAge(before)
                || crop.isMaxAge(reported)) return;
        var data = existing(level);
        if (data == null) return;
        long key = pos.asLong();
        var active = data.crops.get(key);
        if (active == null || active.crop != crop || active.expired(level.getGameTime()) || active.removed >= 0 || active.uses <= 0) return;
        var state = level.getBlockState(pos);
        if (state.getBlock() != crop || crop.isMaxAge(state)) return;
        int age = Math.min(crop.getMaxAge(), crop.getAge(state) + active.profile.bonusStages);
        if (level.setBlock(pos, crop.getStateForAge(age), Block.UPDATE_CLIENTS)) {
            data.crops.put(key, new Treatment(crop, active.profile, active.expires, active.uses - 1, -1));
            data.setDirty();
        }
    }

    public static void replaced(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        boolean wasCrop = before.getBlock() instanceof CropBlock;
        boolean isCrop = after.getBlock() instanceof CropBlock;
        if (!wasCrop && !isCrop) return;
        if (wasCrop && before.getBlock() == after.getBlock()) {
            var crop = (CropBlock) before.getBlock();
            if (crop.getAge(after) >= crop.getAge(before)) return;
        }
        var data = existing(level);
        if (data == null) return;
        long key = pos.asLong();
        var active = data.crops.get(key);
        if (active == null) return;
        if (!isCrop && wasCrop && active.crop == before.getBlock() && active.removed < 0
                && !active.expired(level.getGameTime()) && ((CropBlock) before.getBlock()).isMaxAge(before)) {
            // Vanilla can obtain drops after removing the block. The old crop is eligible only during this tick.
            data.crops.put(key, new Treatment(active.crop, active.profile, active.expires, 0, level.getGameTime()));
        } else data.crops.remove(key);
        data.setDirty();
    }

    public static ItemStack harvest(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> normalDrops) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state) || normalDrops.isEmpty()) return ItemStack.EMPTY;
        var data = existing(level);
        if (data == null) return ItemStack.EMPTY;
        long key = pos.asLong();
        var active = data.crops.get(key);
        if (active == null || active.crop != crop || active.expired(level.getGameTime())) return ItemStack.EMPTY;
        data.crops.remove(key);
        data.setDirty();
        Item produce = produce(crop, normalDrops);
        if (produce == Items.AIR) return ItemStack.EMPTY;
        int count = active.profile.rollHarvest(level.random);
        return count == 0 ? ItemStack.EMPTY : new ItemStack(produce, count);
    }

    private static Item produce(CropBlock crop, List<ItemStack> drops) {
        Item cropItem = crop == Blocks.WHEAT ? Items.WHEAT : crop == Blocks.CARROTS ? Items.CARROT
            : crop == Blocks.POTATOES ? Items.POTATO : crop == Blocks.BEETROOTS ? Items.BEETROOT : Items.AIR;
        if (cropItem != Items.AIR) return drops.stream().anyMatch(stack -> stack.is(cropItem)) ? cropItem : Items.AIR;
        Item seed = crop.asItem();
        return drops.stream().filter(stack -> !stack.isEmpty() && !stack.is(seed)).findFirst()
            .map(ItemStack::getItem).orElse(Items.AIR);
    }

    public static void expire(ServerLevel level) {
        long now = level.getGameTime();
        if (now % CLEANUP_INTERVAL != 0) return;
        var data = existing(level);
        if (data != null && data.crops.values().removeIf(treatment -> treatment.expired(now))) data.setDirty();
    }

    public static FertilizedCrops load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data = new FertilizedCrops();
        for (var raw : tag.getList("Crops", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            var profile = FertilizerProfile.byId(row.getString("Fertilizer"));
            var id = ResourceLocation.tryParse(row.getString("Crop"));
            var crop = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
            if (profile == null || !(crop instanceof CropBlock) || row.getLong("Expires") <= 0) continue;
            data.crops.put(row.getLong("Pos"), new Treatment(crop, profile, row.getLong("Expires"),
                Math.clamp(row.getInt("Uses"), 0, profile.growthUses), -1));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        var rows = new ListTag();
        for (var entry : crops.long2ObjectEntrySet()) {
            var active = entry.getValue();
            if (active.removed >= 0) continue;
            var row = new CompoundTag();
            row.putLong("Pos", entry.getLongKey());
            row.putString("Crop", BuiltInRegistries.BLOCK.getKey(active.crop).toString());
            row.putString("Fertilizer", active.profile.id());
            row.putLong("Expires", active.expires);
            row.putInt("Uses", active.uses);
            rows.add(row);
        }
        tag.put("Crops", rows);
        return tag;
    }
}
