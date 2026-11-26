package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmPattern;
import net.createplant.arm.ArmRecipe.Step;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.Trajectory;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Draws the work as it happens: the weld bead cooling behind the torch, wires and ferrite cores, the laser and its groove, the
 * paint film. Everything is cut from the same {@link Trajectory} the arm follows, at the jig's smoothed progress.
 */
public class AssemblyJigRenderer implements BlockEntityRenderer<AssemblyJigBlockEntity> {
    private static final ResourceLocation WHITE = CreatePlant.location("textures/misc/white.png");
    private static final int FULL = LightTexture.FULL_BRIGHT;
    /** White hot, yellow, orange, dark red, then cold silver. */
    private static final int[] HEAT = {0xFFFFEB, 0xFFE65A, 0xFF821E, 0x78140A, 0xAAAFB9};
    private static final double[] HEAT_AT = {0, .2, .45, .75, 1};
    private static final int COPPER = 0xC8733A, ENAMEL = 0x9E2F26, GOLD = 0xD4A84A, FERRITE = 0x3C3C42;

    public AssemblyJigRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public AABB getRenderBoundingBox(AssemblyJigBlockEntity be) {
        return AABB.encapsulatingFullBlocks(be.getBlockPos().offset(-1, 0, -1), be.getBlockPos().offset(3, 4, 3));
    }

    @Override
    public void render(AssemblyJigBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.recipe == null || be.shown.length != be.status.length) return;
        // laid items first: they use other buffers, which would end the overlay's batch below
        for (int i = 0; i < be.status.length; i++)
            if (be.recipe.steps().get(i).lay() && be.status[i] == AssemblyJigBlockEntity.DONE) laid(be, be.recipe.steps().get(i), ps, buffers, light);
        VertexConsumer q = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        PoseStack.Pose pose = ps.last();
        long now = be.getLevel().getGameTime();
        float time = now + partial;
        for (int i = 0; i < be.status.length; i++) {
            Step s = be.recipe.steps().get(i);
            if (s.place()) continue;
            boolean done = be.status[i] == AssemblyJigBlockEntity.DONE;
            float p = done ? 1 : be.shown[i];
            if (p <= 0.001f) continue;
            boolean live = !done && be.status[i] == AssemblyJigBlockEntity.RESERVED && p < .999f;
            Vec3 o = new Vec3(s.rel().getX(), s.rel().getY() + 1 + be.recipe.base(s), s.rel().getZ());
            Trajectory t = Trajectory.of(s.pattern(), s.seamDirection());
            switch (s.pattern()) {
                case SEAM_WELD -> weld(q, pose, o, t, p, live, done ? (now - be.doneAt[i] + partial) / 40f : 0, time);
                case PERIMETER_CUT, DIAGONAL_CUT -> cut(q, pose, o, t, p, live);
                case GRID_WIRE, CORE_SET, WIRE_THREAD -> wires(q, pose, light, o, t, p);
                case FULL_SPRAY -> spray(q, pose, light, o, t, p, live, time, tint(s));
                default -> {}
            }
        }
    }

    /** A laid item (an iron sheet) lies flat over its whole cell, as thick as {@link net.createplant.arm.ArmRecipe#LAID}. */
    private static void laid(AssemblyJigBlockEntity be, Step s, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.translate(s.rel().getX() + .5, s.rel().getY() + 1 + net.createplant.arm.ArmRecipe.LAID / 2, s.rel().getZ() + .5);
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(s.material()),
                net.minecraft.world.item.ItemDisplayContext.FIXED, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }

    private static int tint(Step s) {
        if (s.fluid() == null) return 0xFFFFFF;
        var f = BuiltInRegistries.FLUID.get(s.fluid());
        return IClientFluidTypeExtensions.of(f).getTintColor() & 0xFFFFFF;
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

    private static void cut(VertexConsumer q, PoseStack.Pose pose, Vec3 o, Trajectory t, float p, boolean live) {
        pieces(t, p, .1, (a, b, behind, n) -> {
            ArmDraw.bar(q, pose, o.add(a).add(0, .005, 0), o.add(b).add(0, .005, 0), .04, .014, behind < .12 ? ArmDraw.mix(0xFF6420, 0x1A1414, behind / .12) : 0x1A1414, 240, behind < .12 ? FULL : LightTexture.pack(8, 8));
        });
        if (live) {
            Vec3 tip = o.add(t.tip(p));
            ArmDraw.bar(q, pose, tip, tip.add(0, .7, 0), .035, .035, 0xFF2020, 120, FULL);
            ArmDraw.bar(q, pose, tip, tip.add(0, .7, 0), .014, .014, 0xFFFFFF, 255, FULL);
            ArmDraw.blob(q, pose, tip, .07, 0xFFE0C0, 230, FULL);
        }
    }

    private static void wires(VertexConsumer q, PoseStack.Pose pose, int light, Vec3 o, Trajectory t, float p) {
        pieces(t, p, 1, (a, b, behind, kind) -> {
            if (kind == Trajectory.CORES) return; // the head only sets cores on this pass
            int color = kind == Trajectory.X_WIRE ? COPPER : kind == Trajectory.Y_WIRE ? ENAMEL : GOLD;
            ArmDraw.bar(q, pose, o.add(a), o.add(b), .0025, .0025, color, 255, light);
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

    /** A ferrite core standing on edge, its hole along {@link Trajectory#CORE_AXIS}, so the wires visibly pass through it. */
    private static void ring(VertexConsumer q, PoseStack.Pose pose, int light, Vec3 c) {
        double r = .007; // dust-sized, like real cores
        Vec3 u = new Vec3(0, 1, 0).cross(Trajectory.CORE_AXIS).normalize().scale(r), v = new Vec3(0, r, 0);
        Vec3[] k = {u.add(v), u.reverse().add(v), u.reverse().subtract(v), u.subtract(v)};
        for (int i = 0; i < 4; i++) ArmDraw.bar(q, pose, c.add(k[i]), c.add(k[(i + 1) % 4]), .003, .004, FERRITE, 255, light);
    }

    private static void spray(VertexConsumer q, PoseStack.Pose pose, int light, Vec3 o, Trajectory t, float p, boolean live, float time, int color) {
        pieces(t, p, 1, (a, b, behind, n) -> {
            Vec3 a2 = new Vec3(a.x, 1.012, a.z), b2 = new Vec3(b.x, 1.012, b.z);
            ArmDraw.bar(q, pose, o.add(a2), o.add(b2), .16, .008, color, 140, light);
        });
        if (live) {
            Vec3 tip = o.add(t.tip(p));
            for (int k = 0; k < 3; k++) {
                double dx = .05 * Math.sin(time * 1.3 + k * 2.1), dz = .05 * Math.cos(time * 1.7 + k * 1.3);
                ArmDraw.bar(q, pose, tip.add(dx, 0, dz), new Vec3(tip.x + dx * 2, o.y + 1.012, tip.z + dz * 2), .014, .014, color, 190, light);
            }
        }
    }
}
