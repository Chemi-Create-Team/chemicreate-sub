package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class MixturePonderScenes {
    private MixturePonderScenes() { }
    public static void mixing(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);String lang = "ponder.create_refinery_n_reactors.chemical_mixtures.";
        scene.title("chemical_mixtures", PonderText.title(lang + "title", "Liquid and gas mixtures"));
        scene.configureBasePlate(0, 1, 11);scene.scaleSceneView(.65f);scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(2, 1, 3, 3, 2, 4), Direction.DOWN);
        PonderText.caption(scene, util.vector().of(3, 2, 4), lang + "vessel", 150);
        var root = new BlockPos(2, 1, 3);
        scene.world().modifyBlockEntity(root, LiddedVesselBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(Fluids.WATER, 2000), EXECUTE);
            be.getTankInventory().fill(new FluidStack(CEProcessFluids.ANTIFREEZE.get(), 1000), EXECUTE);
        });
        scene.overlay().showControls(util.vector().of(3, 3, 4), net.createmod.catnip.math.Pointing.DOWN, 70).withItem(Items.WATER_BUCKET.getDefaultInstance());
        PonderText.caption(scene, util.vector().of(3, 2, 4), lang + "blend", 160);
        scene.world().showSection(util.select().fromTo(4, 1, 3, 9, 2, 4), Direction.DOWN);scene.world().setKineticSpeed(util.select().fromTo(4, 1, 3, 5, 1, 4), 64);
        scene.world().modifyBlockEntity(root, LiddedVesselBlockEntity.class, be -> {
            var parcel = be.getTankInventory().drain(501, EXECUTE);
            if (be.getLevel().getBlockEntity(new BlockPos(9, 1, 3)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity target) target.getTankInventory().fill(parcel, EXECUTE);
        });
        PonderText.caption(scene, util.vector().of(6, 1, 3), lang + "transport", 160);
        PonderText.caption(scene, util.vector().of(3, 2, 4), lang + "machines", 170);
        scene.world().showSection(util.select().fromTo(7, 1, 6, 9, 1, 6), Direction.DOWN);
        PonderText.caption(scene, util.vector().of(9, 2, 3), lang + "readout", 170);scene.markAsFinished();
    }
}
