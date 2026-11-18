package net.createplant.assembly;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.foundation.utility.CreatePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Writes a facility plan in the Create schematic format for the constructor's internal printer. */
public final class AssemblySchematic {
    /** The owner folder under Create's uploaded schematics; the files are generated, so any server can rewrite them. */
    public static final String OWNER = CreatePlant.MOD_ID;
    private static final Logger LOGGER = LogUtils.getLogger();

    private AssemblySchematic() { }

    /** A deployed blueprint for the plan, or empty if the file could not be written. */
    public static ItemStack blueprint(Level level, String file, List<AssemblyPlan.Target> targets) {
        BlockPos min = min(targets);
        if (!write(file, targets, min)) return ItemStack.EMPTY;
        ItemStack blueprint = SchematicItem.create(level, file, OWNER);
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, min);
        blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        return blueprint;
    }

    private static BlockPos min(List<AssemblyPlan.Target> targets) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (var target : targets) { x = Math.min(x, target.pos().getX());y = Math.min(y, target.pos().getY());z = Math.min(z, target.pos().getZ()); }
        return new BlockPos(x, y, z);
    }

    private static boolean write(String file, List<AssemblyPlan.Target> targets, BlockPos min) {
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag blocks = new ListTag();
        int sx = 0, sy = 0, sz = 0;
        for (var target : targets) {
            BlockPos at = target.pos().subtract(min);
            sx = Math.max(sx, at.getX() + 1);sy = Math.max(sy, at.getY() + 1);sz = Math.max(sz, at.getZ() + 1);
            CompoundTag block = new CompoundTag();
            block.put("pos", position(at.getX(), at.getY(), at.getZ()));
            block.putInt("state", palette.computeIfAbsent(target.state(), state -> palette.size()));
            blocks.add(block);
        }
        ListTag states = new ListTag();
        for (BlockState state : palette.keySet()) states.add(NbtUtils.writeBlockState(state));
        CompoundTag tag = new CompoundTag();
        tag.put("size", position(sx, sy, sz));
        tag.put("palette", states);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        Path path = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(OWNER).resolve(file);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(tag, path);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not write the assembly schematic {}", path, exception);
            return false;
        }
    }

    private static ListTag position(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));list.add(IntTag.valueOf(y));list.add(IntTag.valueOf(z));
        return list;
    }
}
