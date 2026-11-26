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

    /** One rolled product: what goes in (and the side material), the line it needs, the speed match, the product and its use. */
    public record Rolled(String id, int stands, String feed, int feedCount, String auxiliary, int auxiliaryCount, int count, String use) { }

    public static final java.util.List<Rolled> ROLLED = java.util.List.of(
        new Rolled("thick_steel_plate", 2, "cast_steel_slab", 1, null, 0, 3, "pressure_tank"),
        new Rolled("stainless_clad_plate", 2, "thick_steel_plate", 1, "stainless_steel_sheet", 1, 2, "fusion_vacuum_vessel"),
        new Rolled("lead_sheet", 2, "lead_ingot", 1, null, 0, 2, "biological_shield"),
        new Rolled("zircaloy_cladding_tube", 3, "zirconium_ingot", 3, "nickel_ingot", 1, 8, "fuel_rod"),
        new Rolled("titanium_sheet", 3, "titanium_block", 1, null, 0, 12, "low_pressure_turbine_blade"),
        new Rolled("copper_foil", 4, "minecraft:copper_block", 1, null, 0, 12, "lithium_ion_battery_casing"),
        new Rolled("aluminum_foil", 4, "aluminum_ingot", 4, null, 0, 12, "lithium_ion_battery_casing"),
        new Rolled("electrical_steel_sheet", 4, "steel_block", 1, "minecraft:quartz", 2, 6, "fusion_motor_generator"),
        new Rolled("gold_foil", 5, "minecraft:gold_block", 1, null, 0, 32, "cryogenic_tank"),
        new Rolled("stainless_steel_ingot", 3, "cast_stainless_steel_slab", 1, null, 0, 8, "stainless_steel_sheet"));

    private static ItemStack item(String id, int count) {
        var key = id.contains(":") ? net.minecraft.resources.ResourceLocation.parse(id) : CreatePlant.location(id);
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key), count);
    }

    public static void rolled(SceneBuilder builder, SceneBuildingUtil util, Rolled product) {
        var scene = new CreateSceneBuilder(builder);
        String name = "rolled_" + product.id();
        String lang = "ponder." + CreatePlant.MOD_ID + "." + name + ".";
        int length = 3 * product.stands() + 1;
        scene.title(name, PonderText.title(lang + "title", name));
        scene.configureBasePlate(0, 0, length + 6);
        scene.scaleSceneView(new float[]{.8f, .72f, .62f, .55f}[product.stands() - 2]);
        scene.showBasePlate();
        var feedPos = new BlockPos(4, 2, 2);
        var outlet = feedPos.south(length);
        scene.world().showSection(util.select().position(feedPos).add(util.select().position(outlet)), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(feedPos), Pointing.DOWN, 90).withItem(item(product.feed(), product.feedCount()));
        if (product.auxiliary() != null)
            scene.overlay().showControls(util.vector().blockSurface(feedPos, Direction.WEST), Pointing.RIGHT, 90)
                .withItem(item(product.auxiliary(), product.auxiliaryCount()));
        PonderText.caption(scene, util.vector().topOf(feedPos), lang + "feed", 100);
        scene.world().showSection(util.select().fromTo(feedPos.west(2).south().below(), outlet.east(3).above(4)), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(feedPos.below(), feedPos.above(3)), Direction.DOWN);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, false));
        PonderText.caption(scene, util.vector().centerOf(feedPos.below()), lang + "line", 110);
        scene.world().setKineticSpeed(util.select().fromTo(feedPos.below(), outlet.east(3).above(3)), 24);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, true));
        PonderText.caption(scene, util.vector().centerOf(feedPos.south(2).east(3)), lang + "speed", 110);
        scene.overlay().showControls(util.vector().topOf(outlet), Pointing.DOWN, 90).withItem(item(product.id(), product.count()));
        PonderText.caption(scene, util.vector().topOf(outlet), lang + "output", 100);
        scene.world().modifyBlockEntity(feedPos, ContinuousFactoryBlockEntity.class, be -> be.preview(length, false));
        scene.overlay().showControls(util.vector().topOf(outlet), Pointing.DOWN, 90).withItem(item(product.use(), 1));
        PonderText.caption(scene, util.vector().topOf(outlet), lang + "use", 100);
        scene.markAsFinished();
    }

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
