package net.createplant.ponder;

import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

public final class FertilizerPonderScenes {
    private static final String KEY = "ponder.create_refinery_n_reactors.fertilizer_cycle.";
    private static final String INDUSTRY_KEY = "ponder.create_refinery_n_reactors.fertilizer_industry.";
    private static final BlockPos DEPOT = new BlockPos(5, 1, 4);
    private static final Vec3 DISPLAY = Vec3.atCenterOf(DEPOT).add(0, .5, 0);
    private static final Vec3 CENTRE = new Vec3(2.5, 2.6, 4.5);

    private FertilizerPonderScenes() { }

    public static void cycle(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fertilizer_cycle", PonderText.title(KEY + "title", "Industrial fertilizers"));
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 2, 5, 2, 5), Direction.DOWN);
        scene.idle(20);

        display(scene, CEItems.NPK_FERTILIZER.toStack());
        PonderText.caption(scene, DISPLAY, KEY + "blend", 100);
        scene.overlay().showControls(CENTRE, Pointing.RIGHT, 85).rightClick().withItem(CEItems.NPK_FERTILIZER.toStack());
        scene.idle(12);
        age(scene, util, 1);
        PonderText.caption(scene, CENTRE, KEY + "apply", 85);
        age(scene, util, 4);
        PonderText.caption(scene, CENTRE, KEY + "grow", 100);
        age(scene, util, 7);
        scene.idle(18);
        age(scene, util, 0);
        display(scene, new ItemStack(Items.WHEAT, 3));
        PonderText.caption(scene, DISPLAY, KEY + "harvest", 100);
        scene.markAsFinished();
    }

    public static void industry(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fertilizer_industry", PonderText.title(INDUSTRY_KEY + "title", "From gas to manufacturing"));
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().position(DEPOT), Direction.DOWN);
        scene.idle(12);
        display(scene, CEItems.QUICKLIME.toStack());
        PonderText.caption(scene, DISPLAY, INDUSTRY_KEY + "carbon", 100);
        display(scene, CEItems.SODIUM_CARBONATE.toStack());
        PonderText.caption(scene, DISPLAY, INDUSTRY_KEY + "carbonate", 100);
        PonderText.caption(scene, DISPLAY, INDUSTRY_KEY + "recovery", 100);
        display(scene, CEProcessFluids.METHANOL.get().getBucket().getDefaultInstance());
        PonderText.caption(scene, DISPLAY, INDUSTRY_KEY + "methanol", 100);
        scene.markAsFinished();
    }

    private static void display(CreateSceneBuilder scene, ItemStack stack) {
        scene.world().modifyBlockEntity(DEPOT, DepotBlockEntity.class, depot -> depot.setHeldItem(stack.copy()));
    }

    private static void age(CreateSceneBuilder scene, SceneBuildingUtil util, int age) {
        scene.world().setBlocks(util.select().fromTo(1, 2, 3, 3, 2, 5),
            Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age), false);
    }
}
