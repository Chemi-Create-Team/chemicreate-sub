package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmPattern;
import net.createplant.arm.ArmRecipe.Step;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.Trajectory;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the work on the item an arm is busy with: the weld bead cooling behind the torch, the ferrite cores and the wires. Everything
 * is cut from the same {@link Trajectory} the arm follows, at the synced progress; the steps done before are drawn finished.
 */
public final class ArmWorkDraw {
    private static final ResourceLocation WHITE = CreatePlant.location("textures/misc/white.png");
    private static final int FULL = LightTexture.FULL_BRIGHT;
    /** White hot, yellow, orange, dark red, then cold silver. */
    private static final int[] HEAT = {0xFFFFEB, 0xFFE65A, 0xFF821E, 0x78140A, 0xAAAFB9};
    private static final double[] HEAT_AT = {0, .2, .45, .75, 1};
    private static final int COPPER = 0xD58A4F, ENAMEL = 0xBC493D, GOLD = 0xDAB863, FERRITE = 0x37383E;
    private static final double WIRE_WIDTH = .005;

    private ArmWorkDraw() {}

    /** In the arm's block frame. */
    public static void draw(IndustrialArmBlockEntity be, PoseStack ps, MultiBufferSource buffers, int light, float time) {
        ArmRecipe r = be.workRecipe;
        if (r == null || be.workAt == null) return;
        VertexConsumer q = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        BlockPos at = be.getBlockPos();
        for (int i = 0; i <= be.workStep && i < r.steps().size(); i++) {
            Step s = r.steps().get(i);
            if (s.pattern() == ArmPattern.PLACE) continue;
            boolean current = i == be.workStep;
            float p = current ? be.progress : 1;
            if (p <= .001f) continue;
            float scale = (float) s.pattern().scale();
            ps.pushPose();
            // path units onto the item, as IndustrialArmBlockEntity.on does for the tool tip
            ps.translate(be.workAt.x - at.getX(), be.workAt.y - at.getY(), be.workAt.z - at.getZ());
            ps.scale(scale, scale, scale);
            ps.translate(-.5, -1, -.5);
            Trajectory t = Trajectory.of(s.pattern());
            if (s.pattern() == ArmPattern.SEAM_WELD) weld(q, ps.last(), Vec3.ZERO, t, p, current && be.phase == IndustrialArmBlockEntity.WORK && p < .999f, current ? 0 : 1, time);
            else {
                wires(q, ps.last(), light, Vec3.ZERO, t, p);
                if (current && be.phase == IndustrialArmBlockEntity.WORK && s.pattern() == ArmPattern.WIRE_THREAD)
                    wireEnds(q, ps.last(), light, t, p, s.helpers() > 0);
            }
            ps.popPose();
        }
    }

    private static int heat(double h) {
        h = Math.max(0, Math.min(1, h));
        for (int k = 1; k < HEAT.length; k++) if (h <= HEAT_AT[k]) return ArmDraw.mix(HEAT[k - 1], HEAT[k], (h - HEAT_AT[k - 1]) / (HEAT_AT[k] - HEAT_AT[k - 1]));
        return HEAT[HEAT.length - 1];
    }

    private interface Piece {
        void draw(Vec3 a, Vec3 b, double behind, int kind);
    }

    /** Cuts every stroke up to the progress into short pieces; {@code behind} is how far the tool has moved on since the piece. */
    private static void pieces(Trajectory t, double progress, double step, Piece out) {
        for (int i = 0; i < t.segments(); i++) {
            if (!t.stroke[i]) continue;
            double d = t.done(i, progress);
            if (d <= 0) continue;
            Vec3 a = t.points[i], b = t.points[i + 1];
            int cuts = Math.max(1, (int) Math.ceil(a.distanceTo(b) * d / step));
            for (int c = 0; c < cuts; c++) {
                double f0 = d * c / cuts, f1 = d * (c + 1) / cuts;
                double behind = progress * t.length - (t.cumulative[i] + (f0 + f1) / 2 * (t.cumulative[i + 1] - t.cumulative[i]));
                out.draw(a.lerp(b, f0), a.lerp(b, f1), behind, t.kind[i]);
            }
        }
    }

    private static void weld(VertexConsumer q, PoseStack.Pose pose, Vec3 o, Trajectory t, float p, boolean live, double cool, float time) {
        pieces(t, p, .05, (a, b, behind, n) -> {
            double h = Math.max(behind / .7, Math.min(1, cool));
            int c = heat(h);
            ArmDraw.bar(q, pose, o.add(a), o.add(b), .075, .055, c, 245, h < .75 ? FULL : LightTexture.pack(10, 10));
            if (h < .3) ArmDraw.bar(q, pose, o.add(a), o.add(b), .12, .09, 0xFF9632, (int) (90 * (1 - h / .3)), FULL);
        });
        if (live) {
            Vec3 tip = o.add(t.tip(p)).add(0, .04, 0);
            double flash = .11 + .07 * Math.sin(time * 2.9) + .04 * Math.sin(time * 7.3);
            ArmDraw.blob(q, pose, tip, flash * 2.2, 0x9BC8FF, 70, FULL);
            ArmDraw.blob(q, pose, tip, flash, 0xFFFFFF, 255, FULL);
        }
    }

    private static void wires(VertexConsumer q, PoseStack.Pose pose, int light, Vec3 o, Trajectory t, float p) {
        pieces(t, p, 1, (a, b, behind, kind) -> {
            if (kind == Trajectory.CORES) return; // the head only sets cores on this pass
            ArmDraw.bar(q, pose, o.add(a), o.add(b), WIRE_WIDTH, WIRE_WIDTH, wireColor(kind), 255, light);
        });
        // each core appears once the head has passed over its place on the setting pass
        for (int j = 0; j < Trajectory.GRID; j++) {
            int seg = t.segmentOfKind(Trajectory.CORES, j);
            if (seg < 0) continue;
            double done = t.done(seg, p);
            for (int i = 0; i < Trajectory.GRID; i++) {
                double f = (double) i / (Trajectory.GRID - 1);
                if (done <= 0 || done < (j % 2 == 0 ? f : 1 - f) - 1e-6) continue;
                ring(q, pose, light, o.add(Trajectory.gridAt(i), Trajectory.CORE_Y, Trajectory.gridAt(j)));
            }
        }
    }

    private static int wireColor(int kind) {
        return kind == Trajectory.X_WIRE ? COPPER : kind == Trajectory.Y_WIRE ? ENAMEL : GOLD;
    }

    private static void wireEnds(VertexConsumer q, PoseStack.Pose pose, int light, Trajectory t, float progress, boolean helper) {
        int kind = t.kindAt(progress);
        if (kind < Trajectory.X_WIRE) return;
        int color = wireColor(kind);
        Vec3 tip = t.tip(progress);
        ArmDraw.bar(q, pose, tip, tip.add(0, .022, 0), WIRE_WIDTH, WIRE_WIDTH, color, 255, light);
        if (helper) {
            Vec3 end = t.runEnd(progress);
            Vec3 bend = end.add(.022, .027, .009);
            ArmDraw.bar(q, pose, end, bend, WIRE_WIDTH, WIRE_WIDTH, color, 255, light);
            ArmDraw.bar(q, pose, bend, end.add(0, .072, 0), WIRE_WIDTH, WIRE_WIDTH, color, 255, light);
        }
    }

    /** A ferrite core standing on edge, its hole along {@link Trajectory#CORE_AXIS}, so the wires visibly pass through it. */
    private static void ring(VertexConsumer q, PoseStack.Pose pose, int light, Vec3 c) {
        double r = .009;
        Vec3 u = new Vec3(0, 1, 0).cross(Trajectory.CORE_AXIS).normalize().scale(r), v = new Vec3(0, r, 0);
        Vec3[] k = {u.add(v), u.reverse().add(v), u.reverse().subtract(v), u.subtract(v)};
        for (int i = 0; i < 4; i++)
            ArmDraw.bar(q, pose, c.add(k[i]), c.add(k[(i + 1) % 4]), .004, .004, i == 0 ? 0x666266 : FERRITE, 255, light);
    }
}
