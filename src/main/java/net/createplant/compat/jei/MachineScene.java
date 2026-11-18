package net.createplant.compat.jei;

import java.util.ArrayList;
import java.util.List;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The machine of a recipe category, drawn from its Ponder storyboard the way Create draws a mixer over its basin.
 * The storyboard's floor plate is left out; the blocks are read once and kept.
 */
final class MachineScene {
    private static final float PITCH = -25, YAW = 45;
    private record Placed(BlockState state, float x, float y, float z) { }
    private final String storyboard;
    private List<Placed> blocks;
    private float extentX, extentY, extentZ;

    MachineScene(String storyboard) { this.storyboard = storyboard; }

    private void load() {
        blocks = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        try (var input = MachineScene.class.getResourceAsStream("/assets/" + CreatePlant.MOD_ID + "/ponder/" + storyboard + ".nbt")) {
            if (input == null || level == null) return;
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            var lookup = level.holderLookup(Registries.BLOCK);
            var palette = root.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(lookup, palette.getCompound(i)));
            List<int[]> cells = new ArrayList<>();
            int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            var list = root.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var block = list.getCompound(i);var pos = block.getList("pos", Tag.TAG_INT);
                int[] cell = {pos.getInt(0), pos.getInt(1), pos.getInt(2), block.getInt("state")};
                BlockState state = states.get(cell[3]);
                if (state.isAir() || cell[1] == 0 && state.is(Blocks.SMOOTH_STONE)) continue;
                cells.add(cell);
                for (int axis = 0; axis < 3; axis++) { min[axis] = Math.min(min[axis], cell[axis]);max[axis] = Math.max(max[axis], cell[axis]); }
            }
            if (cells.isEmpty()) return;
            extentX = max[0] - min[0] + 1;extentY = max[1] - min[1] + 1;extentZ = max[2] - min[2] + 1;
            for (int[] cell : cells) blocks.add(new Placed(states.get(cell[3]), cell[0] - min[0] - extentX / 2, -(cell[1] - min[1]) + extentY / 2 - 1, cell[2] - min[2] - extentZ / 2));
        } catch (java.io.IOException exception) {
            org.slf4j.LoggerFactory.getLogger(MachineScene.class).warn("Could not read the JEI scene {}", storyboard, exception);
        }
    }

    /** Fits the machine into the box, turned like Create's own recipe animations. */
    void draw(GuiGraphics graphics, int left, int top, int width, int height) {
        if (blocks == null) load();
        if (blocks.isEmpty()) return;
        float across = (extentX + extentZ) * .71f;
        float scale = Math.min(width / across, height / (extentY * .91f + across * .42f));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + width / 2f, top + height / 2f, 200);
        pose.mulPose(Axis.XP.rotationDegrees(PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(YAW));
        for (Placed block : blocks) AnimatedKinetics.defaultBlockElement(block.state()).atLocal(block.x(), block.y(), block.z()).scale(scale).render(graphics);
        pose.popPose();
    }
}
