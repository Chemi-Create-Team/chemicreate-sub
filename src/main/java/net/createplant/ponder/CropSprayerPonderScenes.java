package net.createplant.ponder;

import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.item.PortableToolFluidHandler;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEProcessFluids;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class CropSprayerPonderScenes {
    private static final String KEY = "ponder.create_refinery_n_reactors.liquid_fertilizer.";

    private CropSprayerPonderScenes() { }

    public static void fertilize(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("liquid_fertilizer", PonderText.title(KEY + "title", "Applying liquid fertilizer"));
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        var depot = new BlockPos(6, 1, 4);
        var spout = depot.above(2);
        var crops = util.select().fromTo(2, 2, 3, 4, 2, 5);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 7, 4, 6), Direction.DOWN);
        scene.idle(20);

        var empty = CEPortableTools.CHEMICAL_SPRAYER.toStack();
        var charged = empty.copy();
        var fill = new FluidStack(CEProcessFluids.LIQUID_FERTILIZER.get(), 1000);
        new PortableToolFluidHandler(charged).fill(fill, FluidAction.EXECUTE);
        scene.world().modifyBlockEntity(depot, DepotBlockEntity.class, be -> be.setHeldItem(empty));
        scene.overlay().showControls(Vec3.atCenterOf(spout), Pointing.RIGHT, 85)
            .withItem(fill.getFluidType().getBucket(fill));
        PonderText.caption(scene, Vec3.atCenterOf(spout), KEY + "fill", 100);
        scene.world().modifyBlockEntity(depot, DepotBlockEntity.class, be -> be.setHeldItem(charged));
        PonderText.caption(scene, Vec3.atCenterOf(depot).add(0, .5, 0), KEY + "refill", 100);

        Vec3 center = new Vec3(3.5, 2.55, 4.5);
        scene.overlay().showControls(center, Pointing.RIGHT, 95).rightClick().withItem(charged);
        scene.idle(20);
        scene.world().setBlocks(crops, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), false);
        PonderText.caption(scene, center, KEY + "spray", 120);
        scene.world().setBlocks(crops, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), false);
        PonderText.caption(scene, center, KEY + "harvest", 105);
        scene.markAsFinished();
    }
}
