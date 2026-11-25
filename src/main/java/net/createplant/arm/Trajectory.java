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
 * A path is a polyline; strokes are where the tool works, hops are where it only repositions.
 */
public final class Trajectory {
    public static final int GRID = 6;
    public static final double MARGIN = .08, LIFT = .03;
    private static final Map<Long, Trajectory> CACHE = new ConcurrentHashMap<>();

    public final Vec3[] points;
    public final boolean[] stroke;
    public final double[] cumulative;
    public final double length;
    private final int[] strokeNumber;

    private Trajectory(List<Vec3> pts, List<Boolean> strokes) {
        points = pts.toArray(new Vec3[0]);
        stroke = new boolean[strokes.size()];
        strokeNumber = new int[strokes.size()];
        cumulative = new double[points.length];
        int n = 0;
        for (int i = 0; i < stroke.length; i++) {
            stroke[i] = strokes.get(i);
            strokeNumber[i] = stroke[i] ? n++ : -1;
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
        List<Boolean> s = new ArrayList<>();
        switch (pattern) {
            case PLACE -> line(p, s, new Vec3(.5, 1.5, .5), new Vec3(.5, .6, .5), true);
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
            case GRID_WIRE -> grid(p, s);
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

    private static void line(List<Vec3> p, List<Boolean> s, Vec3 a, Vec3 b, boolean stroke) {
        if (p.isEmpty()) p.add(a);
        else if (p.get(p.size() - 1).distanceToSqr(a) > 1e-9) { p.add(a); s.add(false); }
        p.add(b);
        s.add(stroke);
    }

    private static void hop(List<Vec3> p, List<Boolean> s, Vec3 to) {
        Vec3 from = p.get(p.size() - 1);
        p.add(from.add(to).scale(.5).add(0, .08, 0));
        s.add(false);
        p.add(to);
        s.add(false);
    }

    private static void perimeter(List<Vec3> p, List<Boolean> s) {
        Vec3[] c = {new Vec3(0, 1, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1), new Vec3(0, 1, 1), new Vec3(0, 1, 0)};
        p.add(c[0]);
        for (int i = 1; i < c.length; i++) { p.add(c[i]); s.add(true); }
    }

    /** Position of grid line {@code i} across the face. */
    public static double gridAt(int i) {
        return MARGIN + i * (1 - 2 * MARGIN) / (GRID - 1);
    }

    /** Height of wire number {@code k}: later wires lie over earlier ones. */
    public static double wireY(int k) {
        return 1.02 + .004 * k;
    }

    private static void grid(List<Vec3> p, List<Boolean> s) {
        int k = 0;
        for (int dir = 0; dir < 2; dir++) {
            for (int i = 0; i < GRID; i++, k++) {
                double c = gridAt(i), y = wireY(k), a = MARGIN, b = 1 - MARGIN;
                boolean fwd = i % 2 == 0;
                Vec3 from = dir == 0 ? new Vec3(c, y, fwd ? a : b) : new Vec3(fwd ? a : b, y, c);
                Vec3 to = dir == 0 ? new Vec3(c, y, fwd ? b : a) : new Vec3(fwd ? b : a, y, c);
                if (!p.isEmpty()) hop(p, s, from);
                else p.add(from);
                p.add(to);
                s.add(true);
            }
        }
        Vec3 d1a = new Vec3(MARGIN, wireY(k), MARGIN), d1b = new Vec3(1 - MARGIN, wireY(k), 1 - MARGIN);
        hop(p, s, d1a);
        p.add(d1b);
        s.add(true);
        k++;
        Vec3 d2a = new Vec3(1 - MARGIN, wireY(k), MARGIN), d2b = new Vec3(MARGIN, wireY(k), 1 - MARGIN);
        hop(p, s, d2a);
        p.add(d2b);
        s.add(true);
    }

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

    /** The n-th stroke (hops not counted) as a segment index. */
    public int segmentOfStroke(int n) {
        for (int i = 0; i < stroke.length; i++) if (strokeNumber[i] == n) return i;
        return -1;
    }
}
