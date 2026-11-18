package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonRenderer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.ShadedBlockSbbBuilder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class FacilityConstructorRenderer extends SchematicannonRenderer {
    private static final Map<FacilityConstructorBlockEntity, Preview> PREVIEWS = new WeakHashMap<>();
    private static final int RANGE = 96;
    private record Mesh(SuperByteBuffer buffer, boolean obstructed) { }
    private static final class Preview {
        long time = Long.MIN_VALUE;
        List<net.createplant.assembly.AssemblyPlan.Target> plan;
        BitSet missing = new BitSet(), obstructed = new BitSet();
        Map<Long, List<Integer>> sections = new HashMap<>();
        Map<Long, List<Mesh>> meshes = new HashMap<>();
        Map<Integer, BlockState> obstacles = new HashMap<>();
    }

    public FacilityConstructorRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @SubscribeEvent public static void reload(ModelEvent.BakingCompleted event) {
        for (var preview : PREVIEWS.values()) for (var meshes : preview.meshes.values()) for (var mesh : meshes) mesh.buffer().delete();
        PREVIEWS.clear();
    }

    @Override protected void renderSafe(SchematicannonBlockEntity cannon, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(cannon, partialTicks, pose, buffers, light, overlay);
        if (!(cannon instanceof FacilityConstructorBlockEntity be) || be.getLevel() == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || be.getLevel() == mc.level && !mc.player.blockPosition().closerThan(be.getBlockPos(), RANGE)) return;
        Preview preview = PREVIEWS.computeIfAbsent(be, ignored -> new Preview());
        long time = AnimationTickHolder.getTicks(be.getLevel());
        if (preview.plan != be.plan() || time < preview.time || time - preview.time >= 20) refresh(be, preview, time);
        var consumer = buffers.getBuffer(RenderType.translucentMovingBlock());
        for (var meshes : preview.meshes.values()) for (var mesh : meshes) mesh.buffer().light(0xF000F0)
            .color(255, mesh.obstructed() ? 90 : 255, mesh.obstructed() ? 70 : 255, mesh.obstructed() ? 110 : 72).renderInto(pose, consumer);
    }

    private static void refresh(FacilityConstructorBlockEntity be, Preview preview, long time) {
        var plan = be.plan();
        boolean changedPlan = preview.plan != plan;
        var missing = new BitSet(plan.size());
        var blocked = new BitSet(plan.size());
        var obstacles = new HashMap<Integer, BlockState>();
        var changed = new HashSet<Long>();
        if (changedPlan) {
            for (var meshes : preview.meshes.values()) for (var mesh : meshes) mesh.buffer().delete();
            preview.meshes.clear();preview.sections.clear();
            for (int i = 0; i < plan.size(); i++) {
                long section = SectionPos.of(plan.get(i).pos()).asLong();
                preview.sections.computeIfAbsent(section, ignored -> new ArrayList<>()).add(i);
                changed.add(section);
            }
        }
        for (int i = 0; i < plan.size(); i++) {
            var target = plan.get(i);
            if (be.getLevel().hasChunkAt(target.pos())) {
                var present = be.getLevel().getBlockState(target.pos());
                if (!present.is(target.state().getBlock())) {
                    if (target.state().isAir()) blocked.set(i, !present.isAir());
                    else { missing.set(i);blocked.set(i, !present.canBeReplaced()); }
                    if (blocked.get(i)) obstacles.put(i, present);
                }
            }
            if (!changedPlan && (missing.get(i) != preview.missing.get(i) || blocked.get(i) != preview.obstructed.get(i)
                || obstacles.get(i) != preview.obstacles.get(i))) changed.add(SectionPos.of(target.pos()).asLong());
        }
        preview.time = time;
        preview.plan = plan;preview.missing = missing;preview.obstructed = blocked;preview.obstacles = obstacles;
        if (changed.isEmpty()) return;
        var world = new SchematicLevel(be.getBlockPos(), be.getLevel());
        BoundingBox bounds = new BoundingBox(BlockPos.ZERO);
        for (var target : plan) {
            var local = target.pos().subtract(be.getBlockPos());
            bounds.encapsulate(local);
            if (!target.state().isAir()) world.getBlockMap().put(local, target.state());
        }
        world.setBounds(bounds);world.renderMode = true;
        ModelBlockRenderer.enableCaching();
        try {
            for (long section : changed) {
                var old = preview.meshes.remove(section);
                if (old != null) for (var mesh : old) mesh.buffer().delete();
                preview.meshes.put(section, build(be, world, preview.sections.get(section), missing, blocked));
            }
        } finally { ModelBlockRenderer.clearCache(); }
    }

    private static List<Mesh> build(FacilityConstructorBlockEntity be, SchematicLevel world, List<Integer> indices, BitSet missing, BitSet blocked) {
        var meshes = new ArrayList<Mesh>();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var random = RandomSource.create(42);
        var stack = new PoseStack();
        for (boolean obstructed : new boolean[]{false, true}) for (var layer : RenderType.chunkBufferLayers()) {
            var builder = ShadedBlockSbbBuilder.create();
            builder.begin();
            for (int i : indices) {
                if (blocked.get(i) != obstructed || !missing.get(i) && !blocked.get(i)) continue;
                var target = be.plan().get(i);
                BlockState state = target.state().isAir() ? be.getLevel().getBlockState(target.pos()) : target.state();
                var model = dispatcher.getBlockModel(state);
                var data = model.getModelData(world, target.pos(), state, ModelData.EMPTY);
                long seed = state.getSeed(target.pos());random.setSeed(seed);
                if (!model.getRenderTypes(state, random, data).contains(layer)) continue;
                var local = target.pos().subtract(be.getBlockPos());
                stack.pushPose();stack.translate(local.getX(), local.getY(), local.getZ());
                dispatcher.getModelRenderer().tesselateBlock(world, model, state, target.pos(), stack, builder, !obstructed, random, seed,
                    OverlayTexture.NO_OVERLAY, data, layer);
                stack.popPose();
            }
            SuperByteBuffer buffer = builder.end();
            if (!buffer.isEmpty()) meshes.add(new Mesh(buffer, obstructed));
        }
        return meshes;
    }
}
