package net.createplant.arm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The path a tool follows over one cell, in cell coordinates (0..1 on every axis, y up). The server aims the arm along it and the
 * client draws the weld bead, wires, grooves and paint along it, so the overlay always lies where the tool went.
 * A path is a polyline; strokes are where the tool works, hops are where it only repositions. Each stroke has a kind; only the
 * core-memory grid uses more than one ({@link #CORES}, {@link #X_WIRE}, {@link #Y_WIRE}, {@link #SENSE}).
 */
public final class Trajectory {
    /** Cores per side of the memory plane, which covers the middle quarter of the cell's top face (half a block square). */
    public static final int GRID = 12;
    public static final double PLANE_MIN = .25, PLANE_MAX = .75;
    public static final double MARGIN = .08, LIFT = .03;
    /** Kinds of the grid's strokes: setting the ferrite cores row by row, then threading the X, Y and sense wires through them. */
    public static final int CORES = 0, X_WIRE = 1, Y_WIRE = 2, SENSE = 3;
    /** Height of the cores' centres over the cell, where the wires pass through them, how far the head lifts between cores, and where it sets them. */
    public static final double CORE_Y = 1.012, BETWEEN_Y = 1.022, SET_Y = 1.04;
    private static final Map<Long, Trajectory> CACHE = new ConcurrentHashMap<>();

    public final Vec3[] points;
    public final boolean[] stroke;
    /** The kind of each segment's stroke, or -1 for a hop. */
    public final int[] kind;
    public final double[] cumulative;
    public final double length;

    private Trajectory(List<Vec3> pts, List<Integer> kinds) {
        points = pts.toArray(new Vec3[0]);
        stroke = new boolean[kinds.size()];
        kind = new int[kinds.size()];
        cumulative = new double[points.length];
        for (int i = 0; i < stroke.length; i++) {
            kind[i] = kinds.get(i);
            stroke[i] = kind[i] >= 0;
            cumulative[i + 1] = cumulative[i] + points[i].distanceTo(points[i + 1]);
        }
        length = cumulative[points.length - 1];
    }

    /** The path of a pattern; {@code seam} is the horizontal direction of the neighbouring block of a seam weld, or null. */
    public static Trajectory of(ArmPattern pattern, Direction seam) {
        return CACHE.computeIfAbsent((long) pattern.ordinal() * 8 + (seam == null ? 7 : seam.get3DDataValue()), k -> build(pattern, seam));
    }

    private static Trajectory build(ArmPattern pattern, Direction seam) {
        List<Vec3> p = new ArrayList<>();
        List<Integer> s = new ArrayList<>();
        switch (pattern) {
            case PLACE -> line(p, s, new Vec3(.5, 1.5, .5), new Vec3(.5, .56, .5), true);
            case SEAM_WELD -> {
                if (seam == null || seam.getAxis().isVertical()) perimeter(p, s);
                else {
                    Vec3 a = switch (seam) {
                        case EAST -> new Vec3(1, 1, 0);
                        case WEST -> new Vec3(0, 1, 0);
                        case SOUTH -> new Vec3(0, 1, 1);
                        default -> new Vec3(0, 1, 0);
                    };
                    Vec3 b = seam.getAxis() == Direction.Axis.X ? a.add(0, 0, 1) : a.add(1, 0, 0);
                    line(p, s, a, b, true);
                }
            }
            case PERIMETER_CUT -> perimeter(p, s);
            case DIAGONAL_CUT -> {
                line(p, s, new Vec3(0, 1, 0), new Vec3(1, 1, 1), true);
                hop(p, s, new Vec3(1, 1, 0));
                line(p, s, new Vec3(1, 1, 0), new Vec3(0, 1, 1), true);
            }
            case GRID_WIRE -> grid(p, s, true, true);
            case CORE_SET -> grid(p, s, true, false);
            case WIRE_THREAD -> grid(p, s, false, true);
            case FULL_SPRAY -> {
                int rows = 7;
                for (int r = 0; r < rows; r++) {
                    double z = .06 + r * .88 / (rows - 1), x0 = r % 2 == 0 ? .04 : .96, x1 = 1 - x0;
                    Vec3 a = new Vec3(x0, 1.1, z), b = new Vec3(x1, 1.1, z);
                    if (r == 0) p.add(a);
                    else { line(p, s, p.get(p.size() - 1), a, true); }
                    line(p, s, a, b, true);
                }
            }
        }
        return new Trajectory(p, s);
    }

    private static void line(List<Vec3> p, List<Integer> s, Vec3 a, Vec3 b, boolean stroke) {
        if (p.isEmpty()) p.add(a);
        else if (p.get(p.size() - 1).distanceToSqr(a) > 1e-9) { p.add(a); s.add(-1); }
        p.add(b);
        s.add(stroke ? 0 : -1);
    }

    private static void hop(List<Vec3> p, List<Integer> s, Vec3 to) {
        Vec3 from = p.get(p.size() - 1);
        p.add(from.add(to).scale(.5).add(0, .08, 0));
        s.add(-1);
        p.add(to);
        s.add(-1);
    }

    private static void perimeter(List<Vec3> p, List<Integer> s) {
        Vec3[] c = {new Vec3(0, 1, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1), new Vec3(0, 1, 1), new Vec3(0, 1, 0)};
        p.add(c[0]);
        for (int i = 1; i < c.length; i++) { p.add(c[i]); s.add(0); }
    }

    /** Position of grid line {@code i} across the face. */
    public static double gridAt(int i) {
        return PLANE_MIN + i * (PLANE_MAX - PLANE_MIN) / (GRID - 1);
    }

    /**
     * Core memory as it is made by hand: the head sets the ferrite cores row by row, all tilted the same way, then threads the
     * X wires and the Y wires through every core, dipping into each core and lifting between them, and last the sense wire,
     * which zigzags along the diagonals through every core once.
     */
    private static void grid(List<Vec3> p, List<Integer> s, boolean setCores, boolean threadWires) {
        int n = GRID - 1;
        for (int j = 0; setCores && j < GRID; j++) {
            boolean fwd = j % 2 == 0;
            Vec3 a = new Vec3(gridAt(fwd ? 0 : n), SET_Y, gridAt(j)), b = new Vec3(gridAt(fwd ? n : 0), SET_Y, gridAt(j));
            if (p.isEmpty()) p.add(a); else hop(p, s, a);
            p.add(b);
            s.add(CORES);
        }
        if (!threadWires) return;
        for (int dir = 0; dir < 2; dir++) {
            for (int line = 0; line < GRID; line++) {
                boolean fwd = line % 2 == 0;
                List<Vec3> cores = new ArrayList<>();
                for (int k = 0; k < GRID; k++) {
                    int along = fwd ? k : n - k;
                    // X and Y wires cross inside each core, one just under the other
                    double y = CORE_Y + (dir == 0 ? -.002 : .002);
                    cores.add(dir == 0 ? new Vec3(gridAt(along), y, gridAt(line)) : new Vec3(gridAt(line), y, gridAt(along)));
                }
                Vec3 lead = cores.get(0).add(cores.get(0).subtract(cores.get(1)).normalize().scale(.03));
                Vec3 tail = cores.get(n).add(cores.get(n).subtract(cores.get(n - 1)).normalize().scale(.03));
                Vec3 start = new Vec3(lead.x, BETWEEN_Y, lead.z);
                if (p.isEmpty()) p.add(start); else hop(p, s, start);
                thread(p, s, cores, dir == 0 ? X_WIRE : Y_WIRE);
                p.add(new Vec3(tail.x, BETWEEN_Y, tail.z));
                s.add(dir == 0 ? X_WIRE : Y_WIRE);
            }
        }
        // sense: the diagonals i - j = c from one corner to the other, each run the other way from the last
        boolean first = true;
        for (int c = -n; c <= n; c++) {
            List<Vec3> cores = new ArrayList<>();
            for (int j = Math.max(0, -c); j <= Math.min(n, n - c); j++) cores.add(new Vec3(gridAt(j + c), CORE_Y, gridAt(j)));
            if ((c + n) % 2 == 1) java.util.Collections.reverse(cores);
            if (first) {
                hop(p, s, new Vec3(cores.get(0).x, BETWEEN_Y, cores.get(0).z));
                first = false;
            }
            thread(p, s, cores, SENSE);
        }
    }

    /** Through each core in turn from where the head is, lifting halfway between. */
    private static void thread(List<Vec3> p, List<Integer> s, List<Vec3> cores, int kind) {
        for (Vec3 core : cores) {
            Vec3 from = p.get(p.size() - 1);
            p.add(new Vec3((from.x + core.x) / 2, BETWEEN_Y, (from.z + core.z) / 2));
            s.add(kind);
            p.add(core);
            s.add(kind);
        }
    }

    /** Which way the cores' holes face: every core is tilted 45 degrees, so the X, Y and diagonal sense wires all pass through. */
    public static final Vec3 CORE_AXIS = new Vec3(1, 0, 1).normalize();

    public int segments() {
        return stroke.length;
    }

    public int strokes() {
        int n = 0;
        for (boolean b : stroke) if (b) n++;
        return n;
    }

    /** The tool tip at progress 0..1 of the whole path. */
    public Vec3 tip(double progress) {
        double d = Math.max(0, Math.min(1, progress)) * length;
        for (int i = 0; i < stroke.length; i++) {
            if (d <= cumulative[i + 1] || i == stroke.length - 1) {
                double len = cumulative[i + 1] - cumulative[i];
                double t = len < 1e-9 ? 1 : Math.min(1, (d - cumulative[i]) / len);
                return points[i].lerp(points[i + 1], t);
            }
        }
        return points[0];
    }

    /** How much of segment {@code i} has been passed at the given progress, 0..1. */
    public double done(int i, double progress) {
        double len = cumulative[i + 1] - cumulative[i];
        if (len < 1e-9) return 1;
        return Math.max(0, Math.min(1, (progress * length - cumulative[i]) / len));
    }

    /** Progress, 0..1, at which the tool is at fraction {@code t} of segment {@code i}. */
    public double progressAt(int i, double t) {
        return length < 1e-9 ? 0 : (cumulative[i] + t * (cumulative[i + 1] - cumulative[i])) / length;
    }

    /** The n-th segment of the given stroke kind, or -1. */
    public int segmentOfKind(int kind, int n) {
        for (int i = 0; i < this.kind.length; i++) if (this.kind[i] == kind && n-- == 0) return i;
        return -1;
    }
}
