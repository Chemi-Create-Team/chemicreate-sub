package net.createplant.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.createplant.registry.CESharedChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class WorldLiquidsPonderScenes {
    private WorldLiquidsPonderScenes() { }

    public static void liquids(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String lesson = "world_liquids";
        scene.title(lesson, PonderText.title(key(lesson, "title"), "Buckets and placeable liquids"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.7f);
        scene.showBasePlate();
        BlockPos tank = new BlockPos(1, 1, 6), source = new BlockPos(5, 1, 3);
        var pool = util.select().fromTo(4, 1, 3, 6, 1, 3);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 3, 8).substract(pool), Direction.DOWN);
        scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> {
            be.setWindows(true);
            be.getTankInventory().fill(new FluidStack(CESharedChemicals.SULFURIC_ACID.get(), 4000), FluidAction.EXECUTE);
            be.setFluidLevel(LerpedFloat.linear().startWithValue(be.getFillState()));
        });
        scene.idle(15);
        scene.world().createItemEntity(new Vec3(1.4, 1.2, 1.8), Vec3.ZERO, item("nickel_ingot", 3));
        scene.world().createItemEntity(new Vec3(2.1, 1.2, 1.8), Vec3.ZERO, item("chromium_ingot", 1));
        scene.world().createItemEntity(new Vec3(2.8, 1.2, 1.8), Vec3.ZERO, item("molybdenum_ingot", 1));
        text(scene, util, new BlockPos(2, 1, 2), lesson, "alloy");

        scene.overlay().showControls(util.vector().topOf(tank), Pointing.DOWN, 100)
            .rightClick().withItem(item("corrosion_resistant_bucket", 1));
        scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, FluidAction.EXECUTE);
            be.setFluidLevel(LerpedFloat.linear().startWithValue(be.getFillState()));
        });
        text(scene, util, tank, lesson, "fill");

        scene.world().createItemEntity(new Vec3(3.6, 1.2, 1.8), Vec3.ZERO, item("stainless_steel_ingot", 3));
        text(scene, util, new BlockPos(3, 1, 2), lesson, "stainless");
        scene.overlay().showControls(util.vector().topOf(tank), Pointing.DOWN, 100)
            .rightClick().withItem(item("stainless_steel_bucket", 1));
        scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> {
            be.getTankInventory().drain(1000, FluidAction.EXECUTE);
            be.setFluidLevel(LerpedFloat.linear().startWithValue(be.getFillState()));
        });
        text(scene, util, tank, lesson, "stainless_fill");

        scene.overlay().showControls(util.vector().topOf(source), Pointing.DOWN, 100)
            .rightClick().withItem(item("sulfuric_acid_bucket", 1));
        scene.world().showSection(pool, Direction.DOWN);
        text(scene, util, source, lesson, "pour");

        scene.overlay().showOutline(PonderPalette.RED, "corrosive_spill", pool, 130);
        text(scene, util, new BlockPos(4, 1, 3), lesson, "danger");

        scene.overlay().showControls(util.vector().topOf(source), Pointing.DOWN, 100)
            .rightClick().withItem(item("corrosion_resistant_bucket", 1));
        scene.world().hideSection(pool, Direction.DOWN);
        text(scene, util, source, lesson, "recover");
        scene.overlay().showControls(util.vector().topOf(source), Pointing.DOWN, 60)
            .rightClick().withItem(item("sulfuric_acid_bucket", 1));
        scene.world().showSection(pool, Direction.DOWN);
        scene.idle(120);
        scene.markAsFinished();
    }

    private static ItemStack item(String name, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location(name)), count);
    }

    private static String key(String lesson, String caption) {
        return "ponder.create_refinery_n_reactors." + lesson + "." + caption;
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String lesson, String caption) {
        PonderText.caption(scene, util.vector().centerOf(pos), key(lesson, caption), 130);
    }
}
