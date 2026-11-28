package net.createplant.assembly;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import java.util.ArrayList;
import java.util.List;
import net.createplant.assembly.AssemblyPlan.Dimensions;
import net.createplant.assembly.AssemblyPlan.Kind;
import net.createplant.assembly.AssemblyPlan.Target;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The RBMK and fusion facility layouts, installed into the core {@link AssemblyPlan}. */
public final class AtomicsAssemblyPlans {
    private static final int FUSION_CRANE_RAIL_Y = FusionPhysics.HEIGHT + 2;

    private AtomicsAssemblyPlans() { }

    public static void register() {
        AssemblyPlan.register(Kind.RBMK, new AssemblyPlan.Plan() {
            @Override public List<Integer> sizes() { return ReactorCoreVariants.RECOMMENDED_DIAMETERS; }
            @Override public List<Target> targets(BlockPos base, Direction facing, int size, int coreHeight) { return rbmk(base, facing, size, coreHeight); }
            @Override public Dimensions dimensions(int size, int height) {
                int width = ReactorCoreVariants.outerDiameter(size);
                return new Dimensions(width + 2, width + 1, rbmkCraneRailY(height) + 1);
            }
        });
        AssemblyPlan.register(Kind.FUSION, new AssemblyPlan.Plan() {
            @Override public List<Integer> sizes() { return FusionStructure.WIDTHS; }
            @Override public List<Target> targets(BlockPos base, Direction facing, int size, int coreHeight) { return fusion(base, facing, size); }
            @Override public Dimensions dimensions(int size, int height) { return new Dimensions(size + 2, size + 1, FUSION_CRANE_RAIL_Y + 1); }
        });
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

    private static List<Target> fusion(BlockPos base, Direction facing, int width) {
        List<Target> targets = new ArrayList<>();
        BlockPos controller = base.relative(facing).above(FusionPhysics.MIDPLANE);
        for (var cell : FusionStructure.cells(controller, facing, width))
            targets.add(new Target(cell.pos(), cell.role() == FusionStructure.Role.CLEAR ? Blocks.AIR.defaultBlockState() : cell.expected()));
        serviceCrane(targets, Kind.FUSION, base, facing, width, FUSION_CRANE_RAIL_Y);
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
