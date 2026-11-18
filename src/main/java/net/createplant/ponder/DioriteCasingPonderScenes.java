package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class DioriteCasingPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.diorite_casing.";

    private DioriteCasingPonderScenes() { }

    public static void building(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("diorite_casing", PonderText.title(LANG + "title", "Building with Diorite Casing"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        var sample = new BlockPos(2, 1, 1);
        scene.world().showSection(util.select().position(sample), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(sample), Pointing.DOWN, 45)
            .rightClick().withItem(AllItems.ANDESITE_ALLOY.asStack());
        scene.idle(7);
        scene.world().setBlock(sample, BuiltInRegistries.BLOCK.get(CreatePlant.location("diorite_casing")).defaultBlockState(), false);
        text(scene, util, sample, "make");

        var walls = util.select().fromTo(1, 1, 5, 5, 3, 5).add(util.select().fromTo(1, 1, 3, 1, 3, 4));
        scene.world().showSection(walls, Direction.DOWN);
        text(scene, util, new BlockPos(3, 2, 5), "walls");

        var supports = util.select().fromTo(2, 1, 4, 2, 3, 4).add(util.select().fromTo(5, 1, 4, 5, 3, 4))
            .add(util.select().fromTo(2, 4, 4, 5, 4, 4)).add(util.select().fromTo(3, 1, 2, 5, 1, 3));
        scene.world().showSection(supports, Direction.DOWN);
        text(scene, util, new BlockPos(5, 3, 4), "supports");

        var tank = new BlockPos(4, 2, 3);
        PonderText.build(scene, util, 4, 2, 3, 4, 3, 3, Direction.DOWN);
        scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> {
            be.setWindows(true);
            int filled = be.getTankInventory().fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE);
            if (filled != 8000 || be.getTankInventory().getFluidAmount() != 8000)
                throw new IllegalStateException("The casing lesson tank did not retain its finite water inventory");
            be.setFluidLevel(LerpedFloat.linear().startWithValue(be.getFillState()));
            if (be.getFluidLevel().getValue(1) <= 0)
                throw new IllegalStateException("The casing lesson tank has no rendered liquid level");
        });
        text(scene, util, tank, "machines");
        scene.idle(120);
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos position, String key) {
        PonderText.caption(scene, util.vector().centerOf(position), LANG + key, 120);
    }
}
