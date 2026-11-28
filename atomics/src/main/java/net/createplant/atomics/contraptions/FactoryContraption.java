package net.createplant.atomics.contraptions;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import com.simibubi.create.foundation.collision.CollisionList.Populate;
import net.createplant.atomics.factory.FactoryGeometry;
import net.createplant.atomics.registry.CEContinuousFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

public final class FactoryContraption extends TranslatingContraption {
    private FactoryGeometry geometry = FactoryGeometry.PINION;
    private float x = 1, y = 1, z = 1;

    public FactoryContraption() { }

    public FactoryContraption(BlockPos owner, FactoryGeometry geometry) {
        anchor = owner;
        this.geometry = geometry;
        invalidateColliders();
    }

    public FactoryGeometry geometry() { return geometry; }
    public BlockPos owner() { return anchor; }
    @Override public ContraptionType getType() { return CEContinuousFactory.BODY_CONTRAPTION.get(); }
    @Override public boolean assemble(Level level, BlockPos pos) { return false; }

    public void resize(float width, float height, float length) {
        if (x == width && y == height && z == length) return;
        x = width;
        y = height;
        z = length;
        invalidateColliders();
    }

    @Override public void invalidateColliders() {
        simplifiedEntityColliders.size = 0;
        var populate = new Populate(simplifiedEntityColliders);
        for (var piece : geometry.pieces()) Shapes.create(local(piece.bounds())).forAllBoxes(populate);
        bounds = local(geometry.extent());
    }

    private AABB local(AABB raw) {
        return new AABB(.5 + raw.minX * x, .5 + raw.minY * y, .5 + raw.minZ * z, .5 + raw.maxX * x, .5 + raw.maxY * y, .5 + raw.maxZ * z);
    }

    @Override public CompoundTag writeNBT(HolderLookup.Provider lookup, boolean packet) {
        var tag = super.writeNBT(lookup, packet);
        tag.putString("FactoryGeometry", geometry.name());
        return tag;
    }

    @Override public void readNBT(Level level, CompoundTag tag, boolean packet) {
        super.readNBT(level, tag, packet);
        try { geometry = FactoryGeometry.valueOf(tag.getString("FactoryGeometry")); }
        catch (IllegalArgumentException ignored) { geometry = FactoryGeometry.PINION; }
        invalidateColliders();
    }
}
