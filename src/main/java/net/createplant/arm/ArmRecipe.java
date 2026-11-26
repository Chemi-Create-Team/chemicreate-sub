package net.createplant.arm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A staged 3D assembly. Cells are counted from the block above the jig. A stage opens when every step of the stages before it is
 * done; steps of one stage may run in parallel on different arms.
 */
public record ArmRecipe(ResourceLocation id, int sx, int sy, int sz, List<Step> steps, Item result, int resultCount) {
    public static final int MAX_SIZE = 3;
    /** Most parts one step may use up; a core memory plane takes 144 cores. */
    public static final int MAX_CONSUME = 1024;

    /**
     * One job. {@code place}: the gripper sets {@code block}. Otherwise the {@code tool} works the cell, using up {@code count} of {@code consume}
     * (a wire or part, may be null) and {@code fluid} (spray only). {@code time} is ticks at 16 rpm. {@code seam} is the 2D data value
     * of the horizontal direction of the neighbouring placed block, or -1.
     */
    public record Step(int stage, BlockPos rel, boolean place, ArmTool tool, Block block, Item consume, int count, ResourceLocation fluid,
                       int fluidAmount, int time, ArmPattern pattern, int seam) {
        public Direction seamDirection() {
            return seam < 0 ? null : Direction.from2DDataValue(seam);
        }

        /** The item an arm must fetch before starting, or null. */
        public Item material() {
            return place ? block.asItem() : consume;
        }

        /** How many of {@link #material()} the arm fetches. */
        public int materialCount() {
            return place ? 1 : count;
        }
    }

    public int stages() {
        int n = 0;
        for (Step s : steps) n = Math.max(n, s.stage + 1);
        return n;
    }

    public ItemStack resultStack() {
        return new ItemStack(result, resultCount);
    }

    // ---------------------------------------------------------------- json

    public static ArmRecipe fromJson(ResourceLocation id, JsonObject json) {
        JsonArray size = GsonHelper.getAsJsonArray(json, "size");
        int sx = size.get(0).getAsInt(), sy = size.get(1).getAsInt(), sz = size.get(2).getAsInt();
        if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_SIZE || sy > MAX_SIZE || sz > MAX_SIZE) throw new IllegalArgumentException("size must be 1 to 3 on each axis");
        List<Step> steps = new ArrayList<>();
        int stage = 0;
        for (JsonElement stageJson : GsonHelper.getAsJsonArray(json, "stages")) {
            for (JsonElement e : stageJson.getAsJsonArray()) steps.add(step(stage, e.getAsJsonObject(), sx, sy, sz));
            stage++;
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("no steps");
        JsonObject res = GsonHelper.getAsJsonObject(json, "result");
        Item result = item(GsonHelper.getAsString(res, "id"));
        List<Step> withSeams = new ArrayList<>();
        for (Step s : steps) withSeams.add(withSeam(s, steps));
        return new ArmRecipe(id, sx, sy, sz, List.copyOf(withSeams), result, GsonHelper.getAsInt(res, "count", 1));
    }

    private static Step step(int stage, JsonObject o, int sx, int sy, int sz) {
        JsonArray p = GsonHelper.getAsJsonArray(o, "pos");
        BlockPos rel = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        if (rel.getX() < 0 || rel.getY() < 0 || rel.getZ() < 0 || rel.getX() >= sx || rel.getY() >= sy || rel.getZ() >= sz) throw new IllegalArgumentException("step outside the work area: " + rel);
        String action = GsonHelper.getAsString(o, "action");
        boolean place = switch (action) {
            case "place" -> true;
            case "process" -> false;
            default -> throw new IllegalArgumentException("unknown action " + action);
        };
        ArmTool tool = ArmTool.byId(GsonHelper.getAsString(o, "tool", place ? "gripper" : ""));
        if (tool == null) throw new IllegalArgumentException("unknown tool");
        if (place != (tool == ArmTool.GRIPPER)) throw new IllegalArgumentException("only the gripper places blocks, and it does nothing else");
        Block block = null;
        if (place) {
            Item i = item(GsonHelper.getAsString(o, "block"));
            if (!(i instanceof net.minecraft.world.item.BlockItem bi)) throw new IllegalArgumentException("not a block");
            block = bi.getBlock();
        }
        Item consume = null;
        int count = 1;
        if (o.has("consume")) {
            // "consume": "id", or {"id": ..., "count": n} for many of one part
            if (o.get("consume").isJsonObject()) {
                JsonObject c = GsonHelper.getAsJsonObject(o, "consume");
                consume = item(GsonHelper.getAsString(c, "id"));
                count = GsonHelper.getAsInt(c, "count", 1);
                if (count < 1 || count > MAX_CONSUME) throw new IllegalArgumentException("consume count must be 1 to " + MAX_CONSUME);
            } else consume = item(GsonHelper.getAsString(o, "consume"));
        }
        ResourceLocation fluid = null;
        int amount = 0;
        if (o.has("fluid")) {
            JsonObject f = GsonHelper.getAsJsonObject(o, "fluid");
            fluid = ResourceLocation.parse(GsonHelper.getAsString(f, "id"));
            if (!BuiltInRegistries.FLUID.containsKey(fluid)) throw new IllegalArgumentException("unknown fluid " + fluid);
            amount = GsonHelper.getAsInt(f, "amount");
        }
        if (tool == ArmTool.SPRAY_NOZZLE && fluid == null) throw new IllegalArgumentException("the spray nozzle needs a fluid");
        ArmPattern pattern = o.has("pattern") ? ArmPattern.byId(GsonHelper.getAsString(o, "pattern")) : ArmPattern.defaultFor(tool);
        if (pattern == null) throw new IllegalArgumentException("unknown pattern");
        return new Step(stage, rel, place, tool, block, consume, count, fluid, amount, Math.max(1, GsonHelper.getAsInt(o, "time", 40)), place ? ArmPattern.PLACE : pattern, -1);
    }

    private static Step withSeam(Step s, List<Step> all) {
        if (s.pattern != ArmPattern.SEAM_WELD) return s;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = s.rel.relative(d);
            for (Step o : all) if (o.place && o.rel.equals(n)) return new Step(s.stage, s.rel, s.place, s.tool, s.block, s.consume, s.count, s.fluid, s.fluidAmount, s.time, s.pattern, d.get2DDataValue());
        }
        return s;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(rl)) throw new IllegalArgumentException("unknown item " + id);
        return BuiltInRegistries.ITEM.get(rl);
    }

    // ----------------------------------------------------------------- nbt

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id.toString());
        t.putIntArray("size", new int[] {sx, sy, sz});
        t.putString("result", BuiltInRegistries.ITEM.getKey(result).toString());
        t.putInt("count", resultCount);
        ListTag l = new ListTag();
        for (Step s : steps) {
            CompoundTag c = new CompoundTag();
            c.putInt("stage", s.stage);
            c.putIntArray("rel", new int[] {s.rel.getX(), s.rel.getY(), s.rel.getZ()});
            c.putBoolean("place", s.place);
            c.putString("tool", s.tool.id());
            if (s.block != null) c.putString("block", BuiltInRegistries.BLOCK.getKey(s.block).toString());
            if (s.consume != null) {
                c.putString("consume", BuiltInRegistries.ITEM.getKey(s.consume).toString());
                c.putInt("consumeCount", s.count);
            }
            if (s.fluid != null) { c.putString("fluid", s.fluid.toString()); c.putInt("amount", s.fluidAmount); }
            c.putInt("time", s.time);
            c.putString("pattern", s.pattern.id());
            c.putInt("seam", s.seam);
            l.add(c);
        }
        t.put("steps", l);
        return t;
    }

    public static ArmRecipe fromTag(CompoundTag t) {
        int[] size = t.getIntArray("size");
        List<Step> steps = new ArrayList<>();
        for (Tag e : t.getList("steps", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            int[] r = c.getIntArray("rel");
            boolean place = c.getBoolean("place");
            Block block = place ? BuiltInRegistries.BLOCK.get(ResourceLocation.parse(c.getString("block"))) : null;
            Item consume = c.contains("consume") ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.getString("consume"))) : null;
            steps.add(new Step(c.getInt("stage"), new BlockPos(r[0], r[1], r[2]), place, ArmTool.byId(c.getString("tool")), block, consume, Math.max(1, c.getInt("consumeCount")),
                    c.contains("fluid") ? ResourceLocation.parse(c.getString("fluid")) : null, c.getInt("amount"), c.getInt("time"),
                    ArmPattern.byId(c.getString("pattern")), c.getInt("seam")));
        }
        return new ArmRecipe(ResourceLocation.parse(t.getString("id")), size[0], size[1], size[2], List.copyOf(steps),
                BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("result"))), Math.max(1, t.getInt("count")));
    }
}
