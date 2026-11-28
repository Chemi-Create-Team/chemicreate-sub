package net.createplant.atomics.fusion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createplant.atomics.block.FusionFluidPortBlock;
import net.createplant.atomics.block.FusionReactorControllerBlock;
import net.createplant.atomics.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Bounded torus variants sharing a two-by-two plasma channel and an open centre. */
public final class FusionStructure {
    public enum Role {
        CASING("casing"), COIL("coil"), SOLENOID("solenoid"), VESSEL("vessel"), BLANKET("blanket"),
        DIVERTOR("divertor"), CLEAR("clearance"), CONTROLLER("controller"), VACUUM_PUMP("vacuum_pump"),
        DEUTERIUM("deuterium"), TRITIUM("tritium"), ASH("ash"), HELIUM_IN("helium_in"),
        HELIUM_OUT("helium_out"), COOLANT_IN("coolant_in"), COOLANT_OUT("coolant_out");

        public final String reason;
        Role(String reason) { this.reason = reason; }
    }

    private record Relative(int x, int y, int z, Role role) { }
    public record Cell(BlockPos pos, Role role, BlockState expected) { }
    public record Result(boolean formed, BlockPos centre, BlockPos fault, String reason, List<Cell> cells, int width) { }

    public static final List<Integer> WIDTHS = List.of(11, 15, 19);
    private record Geometry(List<BlockPos> path, List<BlockPos> channel, List<Vec3> centreline, List<Relative> body, List<BlockPos> serviceTops) { }
    private static final Map<Integer, Geometry> GEOMETRIES = createGeometries();

    private FusionStructure() { }

    /** Ordered inner lane of the upper channel layer; offsets are relative to the centre. */
    public static List<BlockPos> plasmaPath() {
        return plasmaPath(FusionPhysics.WIDTH);
    }

    /** Complete two-by-two channel volume, with both horizontal lanes at relative Y -1 and 0. */
    public static List<BlockPos> plasmaChannel() {
        return plasmaChannel(FusionPhysics.WIDTH);
    }

    /** Ordered unit segments halfway between the lanes and layers, relative to the centre's block centre. */
    public static List<Vec3> plasmaCentreline() {
        return plasmaCentreline(FusionPhysics.WIDTH);
    }

    public static List<BlockPos> plasmaPath(int width) { return geometry(width).path(); }
    public static List<BlockPos> plasmaChannel(int width) { return geometry(width).channel(); }
    public static List<Vec3> plasmaCentreline(int width) { return geometry(width).centreline(); }
    public static BlockPos centre(BlockPos controller, Direction inward) { return centre(controller, inward, FusionPhysics.WIDTH); }
    public static BlockPos centre(BlockPos controller, Direction inward, int width) { return controller.relative(inward, width / 2); }

    public static BlockPos bottomCentre(BlockPos controller, Direction inward) {
        return centre(controller, inward).below(FusionPhysics.MIDPLANE);
    }

    /** Every block of the top blanket layer: the crane may lower its tool onto any of them to exchange components. */
    public static List<BlockPos> serviceTops(BlockPos controller, Direction inward) {
        return serviceTops(controller, inward, FusionPhysics.WIDTH);
    }

    public static List<BlockPos> serviceTops(BlockPos controller, Direction inward, int width) {
        BlockPos bottom = centre(controller, inward, width).below(FusionPhysics.MIDPLANE);
        return geometry(width).serviceTops().stream().map(bottom::offset).toList();
    }

    /** Complete expected layout, including the plasma chamber and open viewing clearances. */
    public static List<Cell> cells(BlockPos controller, Direction inward) {
        return cells(controller, inward, FusionPhysics.WIDTH);
    }

    public static List<Cell> cells(BlockPos controller, Direction inward, int width) {
        BlockPos centre = centre(controller, inward, width);
        int half = width / 2;
        BlockPos bottom = centre.below(FusionPhysics.MIDPLANE);
        Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        for (Relative relative : geometry(width).body()) {
            BlockPos pos = bottom.offset(relative.x, relative.y, relative.z);
            cells.put(pos, new Cell(pos, relative.role, state(relative.role, inward, Direction.NORTH)));
        }

        Direction outward = inward.getOpposite();
        Direction left = inward.getCounterClockWise();
        Direction right = left.getOpposite();
        put(cells, controller, Role.CONTROLLER, state(Role.CONTROLLER, inward, outward));
        putPort(cells, centre.relative(outward, half).below(2), Role.DEUTERIUM, inward, outward);
        putPort(cells, centre.relative(outward, half).below(), Role.TRITIUM, inward, outward);
        putPort(cells, centre.relative(outward, half).above(), Role.ASH, inward, outward);
        putPort(cells, centre.relative(inward, half).below(), Role.HELIUM_IN, inward, inward);
        putPort(cells, centre.relative(inward, half).above(), Role.HELIUM_OUT, inward, inward);
        putPort(cells, centre.relative(left, half).below(), Role.COOLANT_IN, inward, left);
        putPort(cells, centre.relative(left, half).above(), Role.COOLANT_OUT, inward, left);
        BlockPos pump = centre.relative(right, half);
        put(cells, pump, Role.VACUUM_PUMP, CEFusion.VACUUM_PUMP.get().defaultBlockState()
            .setValue(BlockStateProperties.AXIS, right.getAxis()));
        return List.copyOf(cells.values());
    }

    private static void putPort(Map<BlockPos, Cell> cells, BlockPos pos, Role role, Direction inward, Direction outward) {
        put(cells, pos, role, state(role, inward, outward));
    }

    private static void put(Map<BlockPos, Cell> cells, BlockPos pos, Role role, BlockState state) {
        cells.put(pos, new Cell(pos, role, state));
    }

    public static Result scan(Level level, BlockPos controller, Direction inward) {
        return scan(level, controller, inward, FusionPhysics.WIDTH);
    }

    public static Result scan(Level level, BlockPos controller, Direction inward, int preferredWidth) {
        Result preferred = scanSize(level, controller, inward, preferredWidth);
        if (preferred.formed()) return preferred;
        for (int width : WIDTHS) if (width != preferredWidth) {
            Result result = scanSize(level, controller, inward, width);
            if (result.formed()) return result;
        }
        return preferred;
    }

    private static Result scanSize(Level level, BlockPos controller, Direction inward, int width) {
        BlockPos centre = centre(controller, inward, width);
        int half = width / 2;
        BlockPos min = centre.offset(-half, -FusionPhysics.MIDPLANE, -half);
        BlockPos max = centre.offset(half, FusionPhysics.ROOF_OFFSET, half);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
            if (!level.hasChunk(x, z))
                return new Result(false, centre, new BlockPos(x << 4, centre.getY(), z << 4), "unloaded", List.of(), width);
        List<Cell> cells = cells(controller, inward, width);
        for (Cell cell : cells) {
            BlockState present = level.getBlockState(cell.pos);
            if (!matches(present, cell)) return new Result(false, centre, cell.pos, cell.role.reason, cells, width);
        }
        return new Result(true, centre, controller, "ready", cells, width);
    }

    public static boolean matches(BlockState present, Cell cell) {
        if (cell.role == Role.CLEAR) return present.isAir();
        if (cell.role == Role.VESSEL && present.is(CEFusion.VIEWPORT.get())) return true;
        if (!present.is(cell.expected.getBlock())) return false;
        if (cell.expected.hasProperty(FusionReactorControllerBlock.FACING)
            && present.hasProperty(FusionReactorControllerBlock.FACING)
            && present.getValue(FusionReactorControllerBlock.FACING) != cell.expected.getValue(FusionReactorControllerBlock.FACING)) return false;
        if (cell.expected.hasProperty(FusionFluidPortBlock.FACING)
            && present.hasProperty(FusionFluidPortBlock.FACING)
            && present.getValue(FusionFluidPortBlock.FACING) != cell.expected.getValue(FusionFluidPortBlock.FACING)) return false;
        if (cell.expected.hasProperty(BlockStateProperties.AXIS) && present.hasProperty(BlockStateProperties.AXIS)
            && present.getValue(BlockStateProperties.AXIS) != cell.expected.getValue(BlockStateProperties.AXIS)) return false;
        return true;
    }

    private static BlockState state(Role role, Direction inward, Direction outward) {
        return switch (role) {
            case CASING -> CEFusion.REINFORCED_CASING.get().defaultBlockState();
            case COIL -> CEFusion.TOROIDAL_FIELD_COIL.get().defaultBlockState();
            case SOLENOID -> CEFusion.CENTRAL_SOLENOID.get().defaultBlockState();
            case VESSEL -> CEFusion.VACUUM_VESSEL.get().defaultBlockState();
            case BLANKET -> CEFusion.BREEDING_BLANKET.get().defaultBlockState();
            case DIVERTOR -> CEFusion.DIVERTOR.get().defaultBlockState();
            case CLEAR -> net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            case CONTROLLER -> CEFusion.CONTROLLER.get().defaultBlockState().setValue(FusionReactorControllerBlock.FACING, inward);
            case VACUUM_PUMP -> CEFusion.VACUUM_PUMP.get().defaultBlockState();
            case DEUTERIUM -> port(CEFusion.DEUTERIUM_PORT.get(), outward);
            case TRITIUM -> port(CEFusion.TRITIUM_PORT.get(), outward);
            case ASH -> port(CEFusion.ASH_PORT.get(), outward);
            case HELIUM_IN -> port(CEFusion.HELIUM_INLET.get(), outward);
            case HELIUM_OUT -> port(CEFusion.HELIUM_OUTLET.get(), outward);
            case COOLANT_IN -> port(CEFusion.COOLANT_INLET.get(), outward);
            case COOLANT_OUT -> port(CEFusion.COOLANT_OUTLET.get(), outward);
        };
    }

    private static BlockState port(Block block, Direction outward) {
        return block.defaultBlockState().setValue(FusionFluidPortBlock.FACING, outward);
    }

    private static int[][] plasmaCorners(int radius) {
        int inner = radius - 1;
        return new int[][] {{-inner, -radius}, {inner, -radius}, {inner, -inner}, {radius, -inner},
            {radius, inner}, {inner, inner}, {inner, radius}, {-inner, radius}, {-inner, inner},
            {-radius, inner}, {-radius, -inner}, {-inner, -inner}};
    }

    private static List<BlockPos> createPlasmaPath(int radius) {
        // Each lane is a face-connected loop. Moving its inner corner inward retains a second path
        // around the bend instead of making the diagonal outer corner a one-cell bottleneck.
        int[][] corners = plasmaCorners(radius);
        List<BlockPos> path = new ArrayList<>();
        for (int i = 0; i < corners.length; i++) {
            int[] from = corners[i], to = corners[(i + 1) % corners.length];
            int dx = Integer.compare(to[0], from[0]), dz = Integer.compare(to[1], from[1]);
            for (int x = from[0], z = from[1]; x != to[0] || z != to[1]; x += dx, z += dz)
                path.add(new BlockPos(x, 0, z));
        }
        return List.copyOf(path);
    }

    private static List<BlockPos> createPlasmaChannel(int radius) {
        List<BlockPos> channel = new ArrayList<>();
        for (int y = -1; y <= 0; y++) {
            for (BlockPos at : createPlasmaPath(radius)) channel.add(at.offset(0, y, 0));
            for (BlockPos at : createPlasmaPath(radius + 1)) channel.add(at.offset(0, y, 0));
        }
        return List.copyOf(channel);
    }

    private static List<Vec3> createPlasmaCentreline(int radius) {
        int[][] inner = plasmaCorners(radius), outer = plasmaCorners(radius + 1);
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i < inner.length; i++) {
            int next = (i + 1) % inner.length;
            double x = (inner[i][0] + outer[i][0]) / 2d, z = (inner[i][1] + outer[i][1]) / 2d;
            double endX = (inner[next][0] + outer[next][0]) / 2d, endZ = (inner[next][1] + outer[next][1]) / 2d;
            double dx = Math.signum(endX - x), dz = Math.signum(endZ - z);
            for (; x != endX || z != endZ; x += dx, z += dz) path.add(new Vec3(x, -.5, z));
        }
        return List.copyOf(path);
    }

    private static List<Relative> body(int width) {
        Map<Long, Relative> cells = new LinkedHashMap<>();
        int mid = FusionPhysics.MIDPLANE, half = width / 2, radius = half - 2;
        Set<BlockPos> channel = new LinkedHashSet<>(createPlasmaPath(radius));
        channel.addAll(createPlasmaPath(radius + 1));
        Set<BlockPos> shell = new LinkedHashSet<>(channel);
        for (BlockPos plasma : channel)
            for (Direction normal : Direction.Plane.HORIZONTAL) shell.add(plasma.relative(normal));

        // Required viewing clearances prevent the old cylinder, roof or centre post returning.
        for (int y = 1; y < FusionPhysics.HEIGHT; y++) for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++)
            add(cells, x, y, z, Role.CLEAR);

        // The pressure wall is one cell thick. Roof, floor and field-coil sections also serve as wall
        // surfaces at this game scale; adding separate full-size layers would obscure the small torus.
        for (BlockPos at : shell) {
            int x = at.getX(), z = at.getZ();
            add(cells, x, 0, z, Role.CASING);
            add(cells, x, mid - 2, z, Role.DIVERTOR);
            add(cells, x, mid - 1, z, channel.contains(at) ? Role.CLEAR : Role.VESSEL);
            add(cells, x, mid, z, channel.contains(at) ? Role.CLEAR : Role.VESSEL);
            add(cells, x, mid + 1, z, Role.BLANKET);
        }

        // The centre solenoid is sunk into the foundation, leaving the central opening clear above it.
        add(cells, 0, 0, -1, Role.CASING);
        add(cells, 0, 0, 0, Role.SOLENOID);
        int[][] cardinal = {{0, -radius}, {radius, 0}, {0, radius}, {-radius, 0}};
        for (int[] at : cardinal) add(cells, at[0], 1, at[1], Role.CASING);

        // Four low field coils wrap the tube itself. Their off-axis locations leave every port free.
        int[][] coils = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int i = 0; i < coils.length; i++) for (int normal = radius - 1; normal <= half; normal++) for (int y = mid - 2; y <= mid + 1; y++) {
            if (normal != radius - 1 && normal != half && y != mid - 2 && y != mid + 1) continue;
            int x = i % 2 == 0 ? coils[i][0] : coils[i][0] * normal;
            int z = i % 2 == 0 ? coils[i][1] * normal : coils[i][1];
            add(cells, x, y, z, Role.COIL);
        }

        // Service connections have short local supports rather than a frame around the whole reactor.
        for (int y = 1; y <= mid + 1; y++) {
            add(cells, 0, y, -half, Role.CASING);
            add(cells, half, y, 0, Role.CASING);
            add(cells, 0, y, half, Role.CASING);
            add(cells, -half, y, 0, Role.CASING);
        }
        return List.copyOf(cells.values());
    }

    private static void add(Map<Long, Relative> cells, int x, int y, int z, Role role) {
        cells.put(key(x, y, z), new Relative(x, y, z, role));
    }

    private static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static Geometry geometry(int width) {
        Geometry geometry = GEOMETRIES.get(width);
        if (geometry == null) throw new IllegalArgumentException("Unsupported fusion width " + width);
        return geometry;
    }

    private static Map<Integer, Geometry> createGeometries() {
        var geometries = new LinkedHashMap<Integer, Geometry>();
        for (int width : WIDTHS) {
            int radius = width / 2 - 2;
            var body = body(width);
            geometries.put(width, new Geometry(createPlasmaPath(radius), createPlasmaChannel(radius), createPlasmaCentreline(radius), body,
                body.stream().filter(cell -> cell.role == Role.BLANKET).map(cell -> new BlockPos(cell.x, cell.y, cell.z)).toList()));
        }
        return Map.copyOf(geometries);
    }

    public static int count(Role role) {
        int count = 0;
        for (Relative cell : geometry(FusionPhysics.WIDTH).body()) if (cell.role == role) count++;
        return count;
    }
}
