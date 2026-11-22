package net.createplant.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createplant.block.entity.VesselStirrerBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** The stirrer's gear turning in the gap round its middle, as Create's mechanical mixer's does, where Flywheel does not draw it. */
public class VesselStirrerRenderer extends KineticBlockEntityRenderer<VesselStirrerBlockEntity> {
    public VesselStirrerRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected SuperByteBuffer getRotatedModel(VesselStirrerBlockEntity be, BlockState state) {
        return CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state);
    }
}
