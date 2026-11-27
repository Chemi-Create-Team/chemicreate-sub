package net.createplant.client;

import net.createmod.ponder.foundation.ui.PonderUI;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in check of the actual connected casing model used by the lesson renderer. */
final class VesselClientCheck {
    private VesselClientCheck() { }

    static void verify(Minecraft mc) {
        if (!(mc.screen instanceof PonderUI ui)) throw new IllegalStateException("Vessel lesson is not open");
        var note = net.minecraft.network.chat.Component.translatable("jei.create_refinery_n_reactors.note.air_separation_nitrogen", 646);
        if (mc.font.split(note,178).size()>2) throw new IllegalStateException("Vessel recipe note does not fit its two-line area: "+note.getString());
        var world = ui.getActiveScene().getWorld();
        for (String name : new String[]{"window_frost", "sludge"}) {
            var sprite = mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(net.createplant.CreatePlant.location("block/lidded_vessel/"+name));
            if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                throw new IllegalStateException("Missing vessel material: "+name);
        }
        var pos = new BlockPos(3, 2, 3);
        if (!(world.getBlockEntity(pos) instanceof LiddedVesselBlockEntity vessel) || !vessel.windowsEnabled())
            throw new IllegalStateException("Vessel inspection windows are not enabled");
        verifyWindowQuads(mc, world, vessel);
        boolean fluid = false, item = false;
        for (int i = 0; i < vessel.displayedFluidSlots(); i++) fluid |= !vessel.displayedFluid(i).isEmpty();
        for (int i = 0; i < vessel.displayedItemSlots(); i++) item |= !vessel.displayedItem(i).isEmpty();
        if (!fluid) throw new IllegalStateException("No actual fluid inventory in vessel lesson");
        if (ui.getActiveScene().getId().getPath().equals("vessel_basin") && (!item || !vessel.mixing()))
            throw new IllegalStateException("Vessel lesson is missing its actual mixing inventory/state");
        if (ui.getActiveScene().getId().getPath().equals("vessel_maintenance") && (vessel.mixing() || vessel.frostLevel()<.5f))
            throw new IllegalStateException("Cold vessel lesson has no temperature-driven frost");
    }

    static void verifyWindowQuads(Minecraft mc, net.minecraft.world.level.BlockAndTintGetter world, LiddedVesselBlockEntity vessel) {
        int openings = 0;
        for (int layer = 0; layer < vessel.getHeight(); layer++) for (int x = 0; x < vessel.getWidth(); x++) for (int z = 0; z < vessel.getWidth(); z++) {
            var cellPos = vessel.getBlockPos().offset(x, layer, z);
            if (!(world.getBlockEntity(cellPos) instanceof LiddedVesselBlockEntity cell)) throw new IllegalStateException("Missing vessel cell");
            var profile = LiddedVesselWindowModel.profile(cell);
            var state = cell.getBlockState();
            var model = mc.getBlockRenderer().getBlockModel(state);
            var data = model.getModelData(world, cellPos, state, ModelData.EMPTY);
            for (var opening : profile.openings()) {
                if (vessel.getWidth() == 3 && layer == 0 && opening.box().minY < net.createplant.block.LiddedVesselBlock.DISH_RIM_HEIGHT / 16d)
                    throw new IllegalStateException("A window overlaps the tapered floor at " + cellPos);
                openings++;
                int axis = opening.face().getAxis().ordinal(), u = axis == 0 ? 2 : 0;
                for (int side = -1; side < 6; side++) for (var quad : model.getQuads(state, side < 0 ? null : Direction.values()[side], RandomSource.create(42), data, null)) {
                    if (quad.getDirection().getAxis() != opening.face().getAxis()) continue;
                    var vertices = quad.getVertices();int stride = vertices.length / 4;
                    float plane = Float.intBitsToFloat(vertices[axis]);
                    double p0 = axis == 0 ? opening.box().minX : opening.box().minZ, p1 = axis == 0 ? opening.box().maxX : opening.box().maxZ;
                    if (plane < p0 - 1e-5 || plane > p1 + 1e-5) continue;
                    float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY, bottom = low, top = high;
                    for (int i = 0; i < 4; i++) {
                        float along = Float.intBitsToFloat(vertices[i * stride + u]), y = Float.intBitsToFloat(vertices[i * stride + 1]);
                        low = Math.min(low, along);high = Math.max(high, along);bottom = Math.min(bottom, y);top = Math.max(top, y);
                    }
                    double a = u == 0 ? opening.box().minX : opening.box().minZ, b = u == 0 ? opening.box().maxX : opening.box().maxZ;
                    if (Math.min(high, b) - Math.max(low, a) > 1e-5 && Math.min(top, opening.box().maxY) - Math.max(bottom, opening.box().minY) > 1e-5)
                        throw new IllegalStateException("Connected casing still covers the inspection window at " + cellPos);
                }
            }
        }
        if (openings == 0) throw new IllegalStateException("No vessel window geometry");
    }
}
