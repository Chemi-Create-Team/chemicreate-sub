package net.createplant.assembly;

import java.util.ArrayList;
import java.util.List;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.nuclear.ReactorCoreVariants;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The finished shape a facility constructor lays out: every block of the machine, and the air its airways and channels need. The
 * machine stands in front of the base, centred on it, with its foundation level with the base.
 */
public final class AssemblyPlan {
    public enum Kind {
        RBMK("rbmk"), FUSION("fusion"), COOLING_TOWER("cooling_tower"), CLARIFIER("clarifier");

        public final String id;
        Kind(String id) { this.id = id; }
    }

    public static final List<Integer> CORE_HEIGHTS = List.of(ReactorPhysics.COMPACT_CORE_HEIGHT, ReactorPhysics.CORE_HEIGHT);

    public static List<Integer> sizes(Kind kind) {
        return switch (kind) {
            case RBMK -> ReactorCoreVariants.RECOMMENDED_DIAMETERS;
            case FUSION -> FusionStructure.WIDTHS;
            case COOLING_TOWER -> CoolingTowerStructure.WIDTHS;
            case CLARIFIER -> net.createplant.block.WetProcessStructure.CLARIFIER_WIDTHS;
        };
    }

    public record Target(BlockPos pos, BlockState state) { }

    private AssemblyPlan() { }

    public static List<Target> targets(Kind kind, BlockPos base, Direction facing, int size, int coreHeight) {
        if (!sizes(kind).contains(size) || !(kind == Kind.CLARIFIER ? net.createplant.block.WetProcessStructure.CLARIFIER_HEIGHTS : CORE_HEIGHTS).contains(coreHeight)) throw new IllegalArgumentException("Unsupported facility dimensions");
        return switch (kind) {
            case RBMK -> rbmk(base, facing, size, coreHeight);
            case FUSION -> fusion(base, facing, size);
            case COOLING_TOWER -> coolingTower(base, facing, size);
            case CLARIFIER -> wetProcess(base, facing, size, coreHeight);
        };
    }

    public static String fileName(Kind kind, Direction facing, int size, int coreHeight) {
        return kind.id + "_" + size + (kind == Kind.RBMK || kind == Kind.CLARIFIER ? "_h" + coreHeight : "") + "_" + facing.getSerializedName() + ".nbt";
    }

    private static List<Target> wetProcess(BlockPos base, Direction facing, int width, int height) {
        var drive = base.relative(facing, width / 2 + 1).above(height - 1);
        return net.createplant.block.WetProcessStructure.cells(drive, facing.getOpposite(), width, height).stream()
            .map(cell -> new Target(cell.pos(), cell.state())).toList();
    }

    private static List<Target> fusion(BlockPos base, Direction facing, int width) {
        List<Target> targets = new ArrayList<>();
        BlockPos controller = base.relative(facing).above(FusionPhysics.MIDPLANE);
        for (var cell : FusionStructure.cells(controller, facing, width))
            targets.add(new Target(cell.pos(), cell.role() == FusionStructure.Role.CLEAR ? Blocks.AIR.defaultBlockState() : cell.expected()));
        return targets;
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

    /** A standard RBMK: feed bundles, core, upper tubes and caps inside a biological shield, with its outlet and drive. */
    private static List<Target> rbmk(BlockPos base, Direction facing, int size, int coreHeight) {
        int shell = ReactorCoreVariants.SHIELD_THICKNESS, outer = ReactorCoreVariants.outerDiameter(size);
        var layout = ReactorPhysics.layout(coreHeight);
        int coreBottom = layout.lowerStructureHeight(), coreTop = coreBottom + coreHeight - 1;
        int cap = coreTop + layout.capOffset(), outletY = coreTop + layout.upperShieldHeight() + layout.suspensionHeight() / 2;
        Rotation rotation = rotation(facing);
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < outer; i++) for (int j = 0; j < outer; j++) {
            if (!ReactorCoreVariants.inCircle(i, j, outer)) continue;
            int ci = i - shell, cj = j - shell;
            boolean core = ci >= 0 && ci < size && cj >= 0 && cj < size && ReactorCoreVariants.inCircle(ci, cj, size);
            for (int y = 0; y <= cap; y++) {
                BlockState state;
                if (!core) state = CENuclear.BIOLOGICAL_SHIELD.get().defaultBlockState();
                else if (y < coreBottom) state = CENuclear.FEED_BUNDLE.get().defaultBlockState();
                else if (y <= coreTop) state = CENuclear.FUEL_CHANNEL.get().defaultBlockState();
                else if (y < cap) state = CENuclear.FUEL_CHANNEL_TUBE.get().defaultBlockState();
                else state = CENuclear.TOP_CAP.get().defaultBlockState();
                if (i == outer - 1 && j == outer / 2 && y == outletY) state = CENuclear.SHIELD_OUTLET.get().defaultBlockState();
                if (i == 0 && j == outer / 2 && y == coreBottom + Math.min(3, coreHeight - 1))
                    state = CENuclear.SHIELD_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                targets.add(new Target(base.offset(new BlockPos(i - outer / 2, y, j + 1).rotate(rotation)), state.rotate(rotation)));
            }
        }
        return targets;
    }

    /** The rotation that turns a layout drawn facing south to face the given way. */
    private static Rotation rotation(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
