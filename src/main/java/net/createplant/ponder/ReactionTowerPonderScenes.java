package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.material.Fluid;

/** A four-layer reaction tower: with a basket, catalyst and gauges per layer, loaded through its roof hatch, as a reactor, and without baskets as a gas-liquid separator behind its sight glasses. */
public final class ReactionTowerPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.reaction_tower.", SEPARATOR = "ponder.create_refinery_n_reactors.reaction_tower_separator.";
    private static final int LAYERS = 4;

    private ReactionTowerPonderScenes() { }

    public static void tower(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("reaction_tower", PonderText.title(LANG + "title", "Reaction tower"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.65f);
        scene.showBasePlate();
        BlockPos centre = util.grid().at(4, 2, 4), top = centre.above(LAYERS - 1);
        form(scene, util);

        PonderText.build(scene, util, 3, 2, 3, 5, 5, 5, Direction.DOWN);
        caption(scene, util, top, "layers");

        hatch(scene, util, centre, top, true);
        caption(scene, util, top, "hatch");

        for (int y = 0; y < LAYERS; y++) {
            scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 20).rightClick().withItem(CEReactor.CATALYST_BASKET.get().getDefaultInstance());
            scene.world().modifyBlockEntityNBT(util.select().position(centre.above(y)), ReactionTowerBlockEntity.class, tag -> tag.putBoolean("Basket", true));
            scene.idle(12);
        }
        caption(scene, util, top, "baskets");

        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 50).rightClick().withItem(CEItems.IRON_CATALYST.get().getDefaultInstance());
        caption(scene, util, top, "catalyst");
        hatch(scene, util, centre, top, false);

        PonderText.build(scene, util, 0, 1, 4, 2, 2, 4, Direction.EAST);
        caption(scene, util, util.grid().at(2, 2, 4), "feed");
        PonderText.build(scene, util, 6, 1, 4, 8, 2, 4, Direction.WEST);
        caption(scene, util, util.grid().at(6, 2, 4), "product");

        var burners = util.select().position(3, 1, 3).add(util.select().position(5, 1, 3)).add(util.select().position(3, 1, 5)).add(util.select().position(5, 1, 5));
        scene.world().showSection(burners, Direction.UP);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "burners", burners, 100);
        caption(scene, util, util.grid().at(3, 1, 3), "heat");

        // Readings of a fresh tower in the loop, then of the same tower with worn catalyst: the heat moves down from the upper layers.
        BlockPos gauges = util.grid().at(4, 4, 3);
        readings(scene, util, centre, 21, 384, 393, 422, 520);
        caption(scene, util, gauges, "gauges");
        readings(scene, util, centre, 21, 395, 407, 430, 470);
        caption(scene, util, gauges, "aging");

        hatch(scene, util, centre, top, true);
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 50).withItem(AllItems.WRENCH.asStack());
        caption(scene, util, top, "wear");
        hatch(scene, util, centre, top, false);

        BlockPos corner = util.grid().at(5, 5, 3);
        scene.overlay().showControls(util.vector().topOf(corner), Pointing.DOWN, 60).withItem(AllItems.GOGGLES.asStack());
        caption(scene, util, corner, "observe");
        scene.markAsFinished();
    }

    public static void separator(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("reaction_tower_separator", PonderText.title(SEPARATOR + "title", "Reaction tower as a separator"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.65f);
        scene.showBasePlate();
        BlockPos centre = util.grid().at(4, 2, 4), top = centre.above(LAYERS - 1);
        form(scene, util);
        PonderText.build(scene, util, 3, 2, 3, 5, 5, 5, Direction.DOWN);
        caption(scene, util, SEPARATOR, top, "mode");
        PonderText.build(scene, util, 0, 1, 4, 2, 2, 4, Direction.EAST);
        caption(scene, util, SEPARATOR, util.grid().at(2, 2, 4), "feed");
        // A separating tower holds a little liquid ammonia under the recycled feed gas.
        scene.world().modifyBlockEntityNBT(util.select().position(centre), ReactionTowerBlockEntity.class, tag -> {
            tag.putBoolean("Glazed", true);
            tag.put("TowerTank2", fluid(CEProcessFluids.LIQUID_AMMONIA.get(), 4000));
            tag.put("TowerTank3", fluid(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(), 64000));
        });
        PonderText.build(scene, util, 6, 2, 4, 8, 2, 4, Direction.WEST);
        caption(scene, util, SEPARATOR, util.grid().at(6, 2, 4), "liquid");
        PonderText.build(scene, util, 6, 5, 4, 7, 5, 4, Direction.WEST);
        PonderText.build(scene, util, 8, 3, 4, 8, 5, 4, Direction.DOWN);
        caption(scene, util, SEPARATOR, util.grid().at(6, 5, 4), "gas");
        scene.overlay().showOutline(PonderPalette.OUTPUT, "window", util.select().position(4, 3, 3), 100);
        caption(scene, util, SEPARATOR, util.grid().at(4, 3, 3), "window");
        scene.markAsFinished();
    }

    /** An empty hand on the roof swings the hatch open or shut. */
    private static void hatch(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos owner, BlockPos top, boolean open) {
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 20).rightClick();
        scene.world().modifyBlockEntityNBT(util.select().position(owner), ReactionTowerBlockEntity.class, tag -> tag.putBoolean("HatchOpen", open));
        scene.idle(20);
    }

    /** Sets the loop pressure and the temperature of each layer, from the bottom. */
    private static void readings(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos owner, float pressure, float... temperatures) {
        scene.world().modifyBlockEntityNBT(util.select().position(owner), ReactionTowerBlockEntity.class, tag -> {
            tag.putString("Front", "north");
            tag.putFloat("Pressure", pressure);
            for (int y = 0; y < temperatures.length; y++) tag.putFloat("Temperature" + y, temperatures[y]);
        });
    }

    /** A fluid stack as it is saved, for block entity data written without a registry lookup. */
    private static CompoundTag fluid(Fluid fluid, int amount) {
        var tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.FLUID.getKey(fluid).toString());tag.putInt("amount", amount);
        return tag;
    }

    /** Gives the drawn tower the Create controller data that ties its blocks into one 3x3 tank. */
    private static void form(CreateSceneBuilder scene, SceneBuildingUtil util) {
        BlockPos controller = util.grid().at(3, 2, 3);
        for (int x = 0; x < 3; x++) for (int y = 0; y < LAYERS; y++) for (int z = 0; z < 3; z++) {
            BlockPos part = controller.offset(x, y, z);
            scene.world().modifyBlockEntityNBT(util.select().position(part), FluidTankBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 3);tag.putInt("Height", LAYERS); }
                else tag.put("Controller", NbtUtils.writeBlockPos(controller));
            });
        }
        scene.idle(10);
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, String lang, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), lang + key, 140);
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, 140);
    }
}
