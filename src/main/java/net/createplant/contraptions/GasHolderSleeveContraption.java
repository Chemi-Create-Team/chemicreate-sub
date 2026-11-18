package net.createplant.contraptions;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import com.simibubi.create.foundation.collision.CollisionList.Populate;
import net.createplant.block.GasHolderGeometry;
import net.createplant.registry.CEGasBell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/** Collision geometry for one manufactured sleeve; its appearance is drawn by the owning tank. */
public class GasHolderSleeveContraption extends TranslatingContraption {
    private int width=1,height=2,stage;
    public GasHolderSleeveContraption() { }
    public GasHolderSleeveContraption(BlockPos at,int width,int height,int stage) {
        this.anchor=at;this.width=width;this.height=height;this.stage=stage;invalidateColliders();
    }
    public int stage() { return stage; }
    public boolean matches(int width,int height) { return this.width==width && this.height==height; }
    @Override public ContraptionType getType() { return CEGasBell.SLEEVE.get(); }
    @Override public boolean assemble(Level level,BlockPos pos) { return false; }
    @Override public void invalidateColliders() {
        bounds=new AABB(0,0,0,width,height+3/16d,width);simplifiedEntityColliders.size=0;
        var populate=new Populate(simplifiedEntityColliders);
        for (var box:new GasHolderGeometry(width,height).shell(stage)) Shapes.create(box).forAllBoxes(populate);
    }
    @Override public CompoundTag writeNBT(HolderLookup.Provider lookup,boolean packet) {
        var tag=super.writeNBT(lookup,packet);tag.putInt("SleeveWidth",width);tag.putInt("SleeveHeight",height);tag.putInt("SleeveStage",stage);return tag;
    }
    @Override public void readNBT(Level level,CompoundTag tag,boolean packet) {
        super.readNBT(level,tag,packet);width=Math.clamp(tag.getInt("SleeveWidth"),1,GasHolderGeometry.MAX_WIDTH);height=Math.clamp(tag.getInt("SleeveHeight"),2,GasHolderGeometry.MAX_HEIGHT);
        stage=Math.clamp(tag.getInt("SleeveStage"),0,2);invalidateColliders();
    }
}
