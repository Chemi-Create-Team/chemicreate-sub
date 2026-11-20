package net.createplant.assembly;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import java.util.ArrayList;
import java.util.List;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.block.CraneDriveBlock;
import net.createplant.block.CraneFrame;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.nuclear.ReactorCoreVariants;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CECrane;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
        RBMK("rbmk"), FUSION("fusion"), COOLING_TOWER("cooling_tower"), CLARIFIER("clarifier"), CASING_TANK("casing_tank");

        public final String id;
        Kind(String id) { this.id = id; }
        public boolean hasServiceCrane() { return this == RBMK || this == FUSION; }
        public boolean isCircularTank() { return this == CLARIFIER || this == CASING_TANK; }
        public List<Integer> heights() { return isCircularTank() ? net.createplant.block.WetProcessStructure.CLARIFIER_HEIGHTS : CORE_HEIGHTS; }
    }

    public static final List<Integer> CORE_HEIGHTS = List.of(ReactorPhysics.COMPACT_CORE_HEIGHT, ReactorPhysics.CORE_HEIGHT);
    private static final int FUSION_CRANE_RAIL_Y = FusionPhysics.HEIGHT + 2;

    public static List<Integer> sizes(Kind kind) {
        return switch (kind) {
            case RBMK -> ReactorCoreVariants.RECOMMENDED_DIAMETERS;
            case FUSION -> FusionStructure.WIDTHS;
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
            case RBMK -> rbmk(base, facing, size, coreHeight);
            case FUSION -> fusion(base, facing, size);
            case COOLING_TOWER -> coolingTower(base, facing, size);
            case CLARIFIER, CASING_TANK -> wetProcess(kind, base, facing, size, coreHeight);
        };
    }

    public static String fileName(Kind kind, Direction facing, int size, int coreHeight) {
        return kind.id + (kind.hasServiceCrane() ? "_crane_" : kind == Kind.CLARIFIER ? "_casing_" : "_") + size
            + (kind == Kind.RBMK || kind.isCircularTank() ? "_h" + coreHeight : "") + "_" + facing.getSerializedName() + ".nbt";
    }

    public static Dimensions dimensions(Kind kind, int size, int height) {
        int width = kind == Kind.RBMK ? ReactorCoreVariants.outerDiameter(size) : size;
        int totalHeight = switch (kind) {
            case RBMK -> rbmkCraneRailY(height) + 1;
            case FUSION -> FUSION_CRANE_RAIL_Y + 1;
            case COOLING_TOWER -> CoolingTowerStructure.SIZES.get(sizes(kind).indexOf(size)).height();
            case CLARIFIER, CASING_TANK -> height;
        };
        return kind.hasServiceCrane() ? new Dimensions(width + 2, width + 1, totalHeight) : new Dimensions(width, width, totalHeight);
    }

    private static int rbmkCraneRailY(int coreHeight) {
        return ReactorPhysics.layout(coreHeight).totalHeight() - 1 + CraneDriveBlockEntity.SERVICE_MAST_LENGTH;
    }

    private static void serviceCrane(List<Target> targets, Kind kind, BlockPos base, Direction facing, int width, int railY) {
        Direction across = facing.getClockWise();
        BlockPos start = base.relative(facing, 2).relative(across.getOpposite(), width / 2 + 1).above(railY);
        int leg = railY - 1;
        var frame = new CraneFrame.Plan(start, facing, across, width, width, new int[]{leg, leg, leg, leg});
        for (int i = 0; i < 4; i++)
            targets.add(new Target(frame.corner(i).below(railY), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState()));
        BlockState upright = CraneFrame.girder(Direction.Axis.Y), rail = CraneFrame.girder(facing.getAxis());
        int legs = frame.legs();
        for (int n = 0; n < legs; n++) targets.add(new Target(frame.pos(n), upright));
        for (int n = 0; n < frame.rails(); n++) {
            int along = n % frame.length();
            targets.add(new Target(frame.pos(legs + n), rail.setValue(GirderBlock.BOTTOM, along == 0 || along == frame.length() - 1)));
        }
        var drive = kind == Kind.RBMK ? CECrane.DRIVE.get() : CECrane.FUSION_DRIVE.get();
        var settings = CraneDriveBlockEntity.rangeSettings(frame.length(), frame.width());
        settings.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(CECrane.DRIVE_ENTITY.get()).toString());
        targets.add(new Target(start.relative(facing.getOpposite()), drive.defaultBlockState()
            .setValue(CraneDriveBlock.FACING, facing).setValue(BlockStateProperties.AXIS, facing.getAxis()), settings));
    }

    private static List<Target> wetProcess(Kind kind, BlockPos base, Direction facing, int width, int height) {
        var floor = base.relative(facing, width / 2 + 1);
        var cells = kind == Kind.CASING_TANK ? net.createplant.block.WetProcessStructure.tankCells(floor, width, height)
            : net.createplant.block.WetProcessStructure.cells(floor.above(height - 1), facing.getOpposite(), width, height);
        return cells.stream().map(cell -> new Target(cell.pos(), cell.state())).toList();
    }

    private static List<Target> fusion(BlockPos base, Direction facing, int width) {
        List<Target> targets = new ArrayList<>();
        BlockPos controller = base.relative(facing).above(FusionPhysics.MIDPLANE);
        for (var cell : FusionStructure.cells(controller, facing, width))
            targets.add(new Target(cell.pos(), cell.role() == FusionStructure.Role.CLEAR ? Blocks.AIR.defaultBlockState() : cell.expected()));
        serviceCrane(targets, Kind.FUSION, base, facing, width, FUSION_CRANE_RAIL_Y);
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
        serviceCrane(targets, Kind.RBMK, base, facing, outer, rbmkCraneRailY(coreHeight));
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
