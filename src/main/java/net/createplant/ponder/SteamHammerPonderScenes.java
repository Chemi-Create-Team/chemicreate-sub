package net.createplant.ponder;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.block.entity.PulsationTubeBlockEntity;
import net.createplant.item.PortableToolFluidHandler;
import net.createplant.item.SteamHammerItem;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEPortableTools;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class SteamHammerPonderScenes {
    private static final String KEY = "ponder.create_refinery_n_reactors.steam_hammer.";
    private static final BlockPos RECEIVER = new BlockPos(2, 1, 2), DEPOT = new BlockPos(4, 1, 3), TARGET = new BlockPos(4, 2, 5);

    private SteamHammerPonderScenes() { }

    public static void controls(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("steam_hammer", PonderText.title(KEY + "title", "Charging and using the steam hammer"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.95f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 3, 6), Direction.DOWN);
        var hammer = CEPortableTools.STEAM_HAMMER.toStack();
        scene.world().modifyBlockEntity(RECEIVER, PulsationTubeBlockEntity.class,
            receiver -> receiver.getTankInventory().setFluid(FluidForm.STEAM.stack(6000)));
        scene.overlay().showControls(util.vector().topOf(RECEIVER), Pointing.DOWN, 95).whileSneaking().rightClick().withItem(hammer.copy());
        PonderText.caption(scene, util.vector().topOf(RECEIVER), KEY + "charge", 100);
        new PortableToolFluidHandler(hammer).fill(FluidForm.STEAM.stack(SteamHammerItem.CAPACITY), FluidAction.EXECUTE);
        scene.world().modifyBlockEntity(DEPOT, DepotBlockEntity.class, depot -> depot.setHeldItem(hammer.copy()));
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 95).leftClick().withItem(hammer.copy());
        scene.effects().emitParticles(new Vec3(4.5, 2.3, 3.4), scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0, -.14)), 6, 10);
        PonderText.caption(scene, util.vector().topOf(DEPOT), KEY + "swing", 100);
        scene.effects().emitParticles(new Vec3(4.5, 2.55, 5), scene.effects().simpleParticleEmitter(ParticleTypes.CRIT, new Vec3(0, .05, .12)), 6, 10);
        PonderText.caption(scene, util.vector().centerOf(TARGET), KEY + "impact", 100);
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 95).rightClick().withItem(hammer.copy());
        PonderText.caption(scene, util.vector().topOf(DEPOT), KEY + "guard", 100);
        scene.markAsFinished();
    }
}
