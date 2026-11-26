package net.createplant.factory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;

/** Meshes and Create's local colliders are built from these same closed cuboids, about the origin. */
public enum FactoryGeometry {
    DRUM, GIRTH_GEAR, PINION, ROLLER, SPOOL, CARRIER, COIL, WIRE, SLAB, BUCKLED_SLAB, CHARGE;

    public enum Surface { BARREL, STEEL, GEAR, COPPER, HOT, ORE }
    public record Piece(AABB bounds, Surface surface) { }

    private List<Piece> pieces;
    private AABB extent;

    public List<Piece> pieces() { return pieces; }
    public AABB extent() { return extent; }

    static {
        DRUM.define(drum());
        GIRTH_GEAR.define(gear(1.58, 2.04, .55, 48, true));
        PINION.define(gear(0, .64, .47, 16, false));
        var roller = new ArrayList<Piece>();
        ring(roller, 0, .5, -1.38, 1.38, 16, Surface.STEEL);
        ring(roller, 0, .51, -1.47, -1.34, 16, Surface.COPPER);
        ring(roller, 0, .51, 1.34, 1.47, 16, Surface.COPPER);
        ring(roller, 0, .15, -1.98, -1.46, 8, Surface.GEAR);
        ring(roller, 0, .15, 1.46, 1.98, 8, Surface.GEAR);
        ROLLER.define(roller);
        var spool = new ArrayList<Piece>();
        ring(spool, 0, .24, -.39, .39, 12, Surface.COPPER);
        ring(spool, 0, .32, -.48, -.39, 12, Surface.STEEL);
        ring(spool, 0, .32, .39, .48, 12, Surface.STEEL);
        spool.add(piece(-.055, .22, -.38, .055, .275, .38, Surface.GEAR));
        SPOOL.define(spool);
        var carrier = new ArrayList<Piece>();
        ring(carrier, .94, 1.16, -.12, .12, 24, Surface.GEAR);
        ring(carrier, 0, .29, -.18, .18, 12, Surface.STEEL);
        carrier.add(piece(-1.04, -.074, -.10, 1.04, .074, .10, Surface.STEEL));
        carrier.add(piece(-.074, -1.04, -.10, .074, 1.04, .10, Surface.STEEL));
        CARRIER.define(carrier);
        var coil = new ArrayList<Piece>();
        ring(coil, .22, .59, -.53, .53, 20, Surface.COPPER);
        ring(coil, 0, .33, -.65, -.53, 20, Surface.STEEL);
        ring(coil, 0, .33, .53, .65, 20, Surface.STEEL);
        COIL.define(coil);
        WIRE.define(List.of(piece(-.017, -.017, -.5, .017, .017, .5, Surface.COPPER)));
        SLAB.define(List.of(piece(-1.12, -.5, -.5, 1.12, .5, .5, Surface.HOT)));
        BUCKLED_SLAB.define(List.of(piece(-1.12, -.5, -.5, 1.12, .5, -.18, Surface.HOT),
            piece(-1.12, -.15, -.18, 1.12, .85, .08, Surface.HOT),
            piece(-1.12, .1, .08, 1.12, 1.1, .25, Surface.HOT),
            piece(-1.12, -.15, .25, 1.12, .85, .5, Surface.HOT)));
        CHARGE.define(List.of(piece(-.22, -.16, -.29, .24, .13, .3, Surface.ORE),
            piece(-.14, .13, -.19, .17, .23, .16, Surface.ORE)));
    }

    private void define(List<Piece> parts) {
        pieces = List.copyOf(parts);
        extent = pieces.getFirst().bounds;
        for (var part : pieces) extent = extent.minmax(part.bounds);
    }

    private static List<Piece> drum() {
        var pieces = new ArrayList<Piece>();
        ring(pieces, 1.45, 1.64, -.48, .48, 32, Surface.BARREL);
        ring(pieces, 1.43, 1.78, -.5, -.47, 32, Surface.COPPER);
        ring(pieces, 1.43, 1.78, .47, .5, 32, Surface.COPPER);
        for (double band : new double[]{-.37, .37}) ring(pieces, 1.63, 1.74, band - .014, band + .014, 32, Surface.GEAR);
        pieces.add(piece(-.08, -1.48, -.44, .08, -1.31, .44, Surface.STEEL));
        pieces.add(piece(-.08, 1.31, -.44, .08, 1.48, .44, Surface.STEEL));
        return pieces;
    }

    private static List<Piece> gear(double bore, double outer, double depth, int teeth, boolean spokes) {
        var pieces = new ArrayList<Piece>();
        double root = outer - (spokes ? .14 : .095);
        ring(pieces, bore, root, -depth / 2, depth / 2, spokes ? 32 : 16, Surface.GEAR);
        double tooth = spokes ? .078 : .052;
        for (int i = 0; i < teeth; i++) {
            double angle = i * Math.PI * 2 / teeth;
            double x = (outer - tooth) * Math.cos(angle), y = (outer - tooth) * Math.sin(angle);
            pieces.add(piece(x - tooth, y - tooth, -depth / 2, x + tooth, y + tooth, depth / 2, Surface.STEEL));
        }
        return pieces;
    }

    private static void ring(List<Piece> parts, double bore, double radius, double z0, double z1, int steps, Surface surface) {
        double stride = 2 * radius / steps;
        for (int i = 0; i < steps; i++) {
            double x0 = -radius + i * stride, x1 = x0 + stride, x = (x0 + x1) / 2;
            double outer = Math.sqrt(radius * radius - x * x);
            double inner = Math.sqrt(Math.max(0, bore * bore - x * x));
            if (inner == 0) parts.add(piece(x0, -outer, z0, x1, outer, z1, surface));
            else {
                parts.add(piece(x0, inner, z0, x1, outer, z1, surface));
                parts.add(piece(x0, -outer, z0, x1, -inner, z1, surface));
            }
        }
    }

    private static Piece piece(double x0, double y0, double z0, double x1, double y1, double z1, Surface surface) {
        return new Piece(new AABB(x0, y0, z0, x1, y1, z1), surface);
    }
}
