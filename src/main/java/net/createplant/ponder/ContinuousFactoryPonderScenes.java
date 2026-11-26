package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.factory.FactoryKind;
import net.createplant.registry.CEArm;
import net.createplant.registry.CEMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ContinuousFactoryPonderScenes {
    private ContinuousFactoryPonderScenes() { }

    public static void kiln(SceneBuilder builder, SceneBuildingUtil util) { show(builder, util, FactoryKind.KILN, 10, new ItemStack(Items.CALCITE)); }
    public static void mill(SceneBuilder builder, SceneBuildingUtil util) { show(builder, util, FactoryKind.MILL, 10, CEMetals.STEEL_BLOCK.toStack()); }
    public static void winder(SceneBuilder builder, SceneBuildingUtil util) { show(builder, util, FactoryKind.WINDER, 6, CEArm.ENAMELLED_COPPER_WIRE.toStack()); }

    private static void show(SceneBuilder builder, SceneBuildingUtil util, FactoryKind machine, int length, ItemStack feed) {
        var scene = new CreateSceneBuilder(builder);
        String lang = "ponder." + CreatePlant.MOD_ID + "." + machine.id + ".";
        scene.title(machine.id, PonderText.title(lang + "title", machine.id));
        scene.configureBasePlate(0, 0, length + 6);
        scene.scaleSceneView(machine == FactoryKind.WINDER ? .85f : .72f);
        scene.showBasePlate();
        var feedPos = new BlockPos(4, 2, 2);
        var outlet = feedPos.south(length);
        scene.world().showSection(util.select().position(feedPos).add(util.select().position(outlet)), Direction.DOWN);
        PonderText.caption(scene, util.vector().topOf(feedPos), lang + "start", 95);
        scene.world().showSection(util.select().fromTo(feedPos.west(2).south().below(), outlet.east(3).above(4)), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(feedPos.below(), feedPos.above(3)), Direction.DOWN);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, false));
        PonderText.caption(scene, util.vector().centerOf(feedPos.south(length / 2)), lang + "structure", 120);
        PonderText.caption(scene, util.vector().centerOf(machine == FactoryKind.KILN ? outlet.below() : feedPos.south(2).east(3)), lang + "power", 120);
        scene.world().setKineticSpeed(util.select().fromTo(feedPos.below(), outlet.east(3).above(3)), 24);
        scene.overlay().showControls(util.vector().topOf(feedPos), Pointing.DOWN, 50).withItem(feed);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, true));
        PonderText.caption(scene, util.vector().centerOf(feedPos.south(length / 2).above()), lang + "process", 125);
        PonderText.caption(scene, util.vector().centerOf(outlet), lang + "output", 110);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, false));
        scene.overlay().showControls(util.vector().topOf(feedPos), Pointing.DOWN, 50).withItem(AllItems.WRENCH.asStack());
        PonderText.caption(scene, util.vector().topOf(feedPos), lang + "trouble", 115);
        scene.markAsFinished();
    }
}
