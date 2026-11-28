package net.createplant.atomics.ponder;

import net.createplant.ponder.PonderText;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.atomics.block.entity.LargeMechanicalPressBlockEntity;
import net.createplant.atomics.registry.CELargePress;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LargePressPonderScenes {
    private LargePressPonderScenes() { }

    public static void press(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String lang = "ponder.create_refinery_n_reactors.large_press.";
        scene.title("large_press", PonderText.title(lang + "title", "Stamping a large iron plate"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.85f);
        scene.showBasePlate();
        var depot = util.grid().at(3, 1, 3);
        var press = depot.above(2);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 1, 4), Direction.DOWN);
        PonderText.caption(scene, util.vector().topOf(depot), lang + "depot", 85);
        scene.world().showSection(util.select().fromTo(2, 3, 2, 4, 3, 4), Direction.DOWN);
        PonderText.caption(scene, util.vector().centerOf(depot.above()), lang + "position", 85);
        scene.world().showSection(util.select().fromTo(press.above(), press.above(2)), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(press, press.above(2)), -32);
        PonderText.caption(scene, util.vector().centerOf(press.above()), lang + "power", 85);
        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 35).rightClick().withItem(new ItemStack(Items.IRON_BLOCK));
        scene.world().modifyBlockEntity(depot, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.IRON_BLOCK)));
        PonderText.caption(scene, util.vector().topOf(depot), lang + "workpiece", 85);
        scene.world().modifyBlockEntity(press, LargeMechanicalPressBlockEntity.class, be -> be.pressing().start(PressingBehaviour.Mode.BELT));
        scene.idle(30);
        scene.world().modifyBlockEntity(depot, DepotBlockEntity.class, be -> be.setHeldItem(CELargePress.LARGE_IRON_PLATE.toStack()));
        scene.idle(15);
        scene.world().showSection(util.select().position(depot.west(2)).add(util.select().position(depot.east(2))), Direction.DOWN);
        scene.world().modifyBlockEntity(depot.east(2), FunnelBlockEntity.class,
            be -> be.getBehaviour(FilteringBehaviour.TYPE).setFilter(CELargePress.LARGE_IRON_PLATE.toStack()));
        scene.overlay().showControls(util.vector().topOf(depot.east(2)), Pointing.DOWN, 60).withItem(CELargePress.LARGE_IRON_PLATE.toStack());
        PonderText.caption(scene, util.vector().centerOf(depot.east(2)), lang + "automation", 110);
        scene.overlay().showControls(util.vector().blockSurface(press.west(), Direction.WEST), Pointing.RIGHT, 60).withItem(AllItems.GOGGLES.asStack());
        PonderText.caption(scene, util.vector().centerOf(press.west()), lang + "checks", 85);
        scene.markAsFinished();
    }
}
