package net.createplant.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.item.PortableToolFluidHandler;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CESharedChemicals;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class PortableSprayerPonderScenes {
    private static final String KEY="ponder.create_refinery_n_reactors.portable_sprayers.";
    private PortableSprayerPonderScenes() { }
    public static void tools(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("portable_sprayers",PonderText.title(KEY+"title","Portable fluid sprayers"));
        scene.configureBasePlate(0,0,9);scene.scaleSceneView(.8f);scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0,1,0,8,4,8),Direction.DOWN);
        var water=new FluidStack(Fluids.WATER,2000);var fuel=new FluidStack(CEPetroFluids.DIESEL.get(),4000);var acid=new FluidStack(CESharedChemicals.SULFURIC_ACID.get(),4000);
        fill(scene,new BlockPos(1,1,2),water);fill(scene,new BlockPos(4,1,2),acid);fill(scene,new BlockPos(7,1,2),fuel);
        text(scene,util,new BlockPos(4,2,4),"overview");
        var extinguisher=loaded(CEPortableTools.FIRE_EXTINGUISHER.get().getDefaultInstance(),water);
        var sprayer=loaded(CEPortableTools.CHEMICAL_SPRAYER.get().getDefaultInstance(),acid);
        var flame=loaded(CEPortableTools.FLAMETHROWER.get().getDefaultInstance(),fuel);
        scene.world().createItemEntity(new Vec3(2.5,1.3,4.5),Vec3.ZERO,extinguisher);
        scene.world().createItemEntity(new Vec3(4.5,1.3,4.5),Vec3.ZERO,sprayer);
        scene.world().createItemEntity(new Vec3(6.5,1.3,4.5),Vec3.ZERO,flame);
        scene.overlay().showControls(util.vector().topOf(1,1,2),Pointing.DOWN,90).rightClick().whileSneaking().withItem(extinguisher);
        text(scene,util,new BlockPos(1,1,2),"refill");
        scene.overlay().showOutline(PonderPalette.INPUT,"spout",util.select().fromTo(4,1,4,4,3,4),90);
        text(scene,util,new BlockPos(4,3,4),"spout");
        scene.overlay().showControls(util.vector().topOf(2,1,6),Pointing.RIGHT,80).rightClick().withItem(extinguisher);
        scene.effects().emitParticles(new Vec3(2.5,1.5,5),scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD,new Vec3(0,0,.15)),3,30);
        scene.idle(25);
        scene.world().modifyBlock(new BlockPos(2,1,6),state->state.setValue(BlockStateProperties.LIT,false),false);
        text(scene,util,new BlockPos(2,1,6),"extinguish");
        scene.overlay().showControls(util.vector().topOf(4,1,6),Pointing.RIGHT,80).rightClick().withItem(sprayer);
        scene.effects().emitParticles(new Vec3(4.5,1.5,5),scene.effects().simpleParticleEmitter(ParticleTypes.SPLASH,new Vec3(0,0,.15)),3,30);
        text(scene,util,new BlockPos(4,1,6),"liquid");
        scene.overlay().showControls(util.vector().topOf(6,1,6),Pointing.RIGHT,80).rightClick().withItem(flame);
        scene.effects().emitParticles(new Vec3(6.5,1.5,5),scene.effects().simpleParticleEmitter(ParticleTypes.FLAME,new Vec3(0,0,.15)),3,30);
        text(scene,util,new BlockPos(6,1,6),"flame");
        text(scene,util,new BlockPos(4,1,4),"stop");scene.markAsFinished();
    }
    private static ItemStack loaded(ItemStack stack,FluidStack fluid) { new PortableToolFluidHandler(stack).fill(fluid,FluidAction.EXECUTE);return stack; }
    private static void fill(CreateSceneBuilder scene,BlockPos pos,FluidStack fluid) { scene.world().modifyBlockEntity(pos,FluidTankBlockEntity.class,be->be.getTankInventory().setFluid(fluid.copy())); }
    private static void text(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos at,String key) { PonderText.caption(scene,util.vector().centerOf(at),KEY+key,105); }
}
