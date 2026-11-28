package net.createplant.assembly;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.CoolingTowerStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The finished shape a facility constructor lays out: every block of the machine, and the air its airways and channels need. The
 * machine stands in front of the base, centred on it, with its foundation level with the base.
 */
public final class AssemblyPlan {
    public enum Kind {
        RBMK("rbmk"), FUSION("fusion"), COOLING_TOWER("cooling_tower"), CLARIFIER("clarifier"), CASING_TANK("casing_tank");

        public final String id;
        Kind(String id) { this.id = id; }
        public boolean hasServiceCrane() { return this == RBMK || this == FUSION; }
        public boolean isCircularTank() { return this == CLARIFIER || this == CASING_TANK; }
        public List<Integer> heights() { return isCircularTank() ? net.createplant.block.WetProcessStructure.CLARIFIER_HEIGHTS : CORE_HEIGHTS; }
    }

    /** Core heights (blocks) of the RBMK plans: compact and full. */
    public static final List<Integer> CORE_HEIGHTS = List.of(3, 7);

    /** What an addon supplies for a facility kind whose blocks it owns. */
    public interface Plan {
        List<Integer> sizes();
        List<Target> targets(BlockPos base, Direction facing, int size, int coreHeight);
        Dimensions dimensions(int size, int height);
    }

    private static final java.util.Map<Kind, Plan> ADDON = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register(Kind kind, Plan plan) { ADDON.put(kind, plan); }

    /** The kinds whose blocks an addon owns can only be built while it is installed. */
    public static boolean available(Kind kind) { return !kind.hasServiceCrane() || ADDON.containsKey(kind); }

    public static List<Integer> sizes(Kind kind) {
        return switch (kind) {
            case RBMK, FUSION -> ADDON.containsKey(kind) ? ADDON.get(kind).sizes() : List.of(0);
            case COOLING_TOWER -> CoolingTowerStructure.WIDTHS;
            case CLARIFIER, CASING_TANK -> net.createplant.block.WetProcessStructure.CLARIFIER_WIDTHS;
        };
    }

    public record Target(BlockPos pos, BlockState state, CompoundTag blockEntityData) {
        public Target(BlockPos pos, BlockState state) { this(pos, state, null); }
    }

    public record Dimensions(int width, int depth, int height) { }

    private AssemblyPlan() { }

    public static List<Target> targets(Kind kind, BlockPos base, Direction facing, int size, int coreHeight) {
        if (!sizes(kind).contains(size) || !kind.heights().contains(coreHeight)) throw new IllegalArgumentException("Unsupported facility dimensions");
        return switch (kind) {
            case RBMK, FUSION -> {
                var plan = ADDON.get(kind);
                if (plan == null) throw new IllegalArgumentException("Facility kind not installed");
                yield plan.targets(base, facing, size, coreHeight);
            }
            case COOLING_TOWER -> coolingTower(base, facing, size);
            case CLARIFIER, CASING_TANK -> wetProcess(kind, base, facing, size, coreHeight);
        };
    }

    public static String fileName(Kind kind, Direction facing, int size, int coreHeight) {
        return kind.id + (kind.hasServiceCrane() ? "_crane_" : kind == Kind.CLARIFIER ? "_casing_" : "_") + size
            + (kind == Kind.RBMK || kind.isCircularTank() ? "_h" + coreHeight : "") + "_" + facing.getSerializedName() + ".nbt";
    }

    public static Dimensions dimensions(Kind kind, int size, int height) {
        if (kind.hasServiceCrane()) return ADDON.containsKey(kind) ? ADDON.get(kind).dimensions(size, height) : new Dimensions(size, size, 1);
        int totalHeight = kind == Kind.COOLING_TOWER ? CoolingTowerStructure.SIZES.get(sizes(kind).indexOf(size)).height() : height;
        return new Dimensions(size, size, totalHeight);
    }

    private static List<Target> wetProcess(Kind kind, BlockPos base, Direction facing, int width, int height) {
        var floor = base.relative(facing, width / 2 + 1);
        var cells = kind == Kind.CASING_TANK ? net.createplant.block.WetProcessStructure.tankCells(floor, width, height)
            : net.createplant.block.WetProcessStructure.cells(floor.above(height - 1), facing.getOpposite(), width, height);
        return cells.stream().map(cell -> new Target(cell.pos(), cell.state())).toList();
    }

    /** The tower stands in front of the constructor; its ports retain their standard compass directions. */
    private static List<Target> coolingTower(BlockPos base, Direction facing, int width) {
        List<Target> targets = new ArrayList<>();
        int index = sizes(Kind.COOLING_TOWER).indexOf(width);
        BlockPos centre = base.relative(facing, CoolingTowerStructure.SIZES.get(index).radius() + 1);
        for (var cell : CoolingTowerStructure.cells(index))
            targets.add(new Target(centre.offset(cell.offset()), cell.block().defaultBlockState()));
        return targets;
    }
}
