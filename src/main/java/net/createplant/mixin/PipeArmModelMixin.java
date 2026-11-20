package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.createplant.transport.PipeConnections;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipeAttachmentModel.class, remap = false)
abstract class PipeArmModelMixin {
    @Unique private static final ModelProperty<Integer> CREATE_PLANT_CUT_ENDS = new ModelProperty<>();
    @Unique private final Map<BakedQuad, BakedQuad[]> createPlant$shortenedQuads = new ConcurrentHashMap<>();

    @Inject(method = "gatherModelData", at = @At("RETURN"))
    private void createPlant$collectCuts(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
            ModelData data, CallbackInfoReturnable<ModelData.Builder> result) {
        if (!(state.getBlock() instanceof GlassFluidPipeBlock)) return;
        var axis = state.getValue(AxisPipeBlock.AXIS);
        int cut = PipeConnections.isClosed(world, pos, Direction.get(Direction.AxisDirection.NEGATIVE, axis)) ? 1 : 0;
        if (PipeConnections.isClosed(world, pos, Direction.get(Direction.AxisDirection.POSITIVE, axis))) cut |= 2;
        if (cut != 0) result.getReturnValue().with(CREATE_PLANT_CUT_ENDS, cut);
    }

    @WrapOperation(method = "getQuads", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/foundation/model/BakedModelWrapperWithData;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"))
    private List<BakedQuad> createPlant$drawPipeBody(PipeAttachmentModel model, BlockState state, Direction side, RandomSource random,
            ModelData data, RenderType layer, Operation<List<BakedQuad>> original) {
        if (state != null && FluidPipeBlock.isPipe(state) && FluidPipeBlock.PROPERTY_BY_DIRECTION.values().stream().noneMatch(state::getValue)) {
            // The one-sided native multipart supplies the center; attachments still use the true, closed state.
            return original.call(model, state.setValue(FluidPipeBlock.UP, true), side, random, data, layer);
        }
        var quads = original.call(model, state, side, random, data, layer);
        Integer cut = data.get(CREATE_PLANT_CUT_ENDS);
        if (cut == null || state == null) return quads;
        int axis = state.getValue(AxisPipeBlock.AXIS).ordinal();
        return quads.stream().map(quad -> createPlant$shortenedQuads.computeIfAbsent(quad, source -> {
            var variants = new BakedQuad[9];
            for (int coordinate = 0; coordinate < 3; coordinate++)
                for (int ends = 1; ends <= 3; ends++)
                    variants[coordinate * 3 + ends - 1] = createPlant$shorten(source, coordinate, ends);
            return variants;
        })[axis * 3 + cut - 1]).toList();
    }

    @Unique private static BakedQuad createPlant$shorten(BakedQuad original, int axis, int ends) {
        int[] source = original.getVertices();
        int[] vertices = Arrays.copyOf(source, source.length);
        int stride = vertices.length / 4;
        float min = (ends & 1) == 0 ? 0f : .25f, max = (ends & 2) == 0 ? 1f : .75f;
        for (int index = axis; index < vertices.length; index += stride) {
            float position = Float.intBitsToFloat(vertices[index]);
            vertices[index] = Float.floatToRawIntBits(min + (max - min) * position);
        }
        return new BakedQuad(vertices, original.getTintIndex(), original.getDirection(), original.getSprite(), original.isShade(),
            original.hasAmbientOcclusion());
    }
}
