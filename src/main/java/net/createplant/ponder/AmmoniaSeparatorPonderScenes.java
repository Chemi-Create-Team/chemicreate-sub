package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** The separator tank in ammonia mode, followed by graded reactor coolant separation. */
public final class AmmoniaSeparatorPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.ammonia_separator.";

    private AmmoniaSeparatorPonderScenes() { }

    public static void separator(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("ammonia_separator", PonderText.title(LANG + "title", "Gas-liquid separator"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        BlockPos controller = util.grid().at(2, 2, 2);
        for (int y = 0; y < 3; y++) {
            BlockPos part = controller.above(y);
            scene.world().modifyBlockEntityNBT(util.select().position(part), FluidTankBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 1);tag.putInt("Height", 3);tag.putString("Front", "north"); }
                else tag.put("Controller", NbtUtils.writeBlockPos(controller));
            });
        }
        var tank = util.select().fromTo(2, 2, 2, 2, 4, 2);
        var feed = util.select().fromTo(3, 4, 2, 6, 4, 3);
        var gas = util.select().position(2, 5, 2);
        var liquid = util.select().position(2, 1, 2);
        scene.idle(10);

        scene.world().showSection(tank, Direction.DOWN);
        text(scene, util, util.grid().at(2, 3, 2), 110, "tank");

        scene.world().showSection(feed, Direction.WEST);
        scene.overlay().showOutline(PonderPalette.INPUT, "feed", feed, 110);
        text(scene, util, util.grid().at(4, 4, 2), 110, "feed");

        text(scene, util, util.grid().at(4,4,2),140,"cool");

        scene.world().showSection(gas, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "gas", gas, 110);
        text(scene, util, util.grid().at(2, 5, 2), 110, "gas");

        scene.world().showSection(liquid, Direction.UP);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "liquid", liquid, 140);
        text(scene, util, util.grid().at(2, 1, 2), 140, "liquid");

        BlockPos target = util.grid().at(2, 3, 2);
        scene.overlay().showControls(util.vector().topOf(target), Pointing.DOWN, 100).withItem(AllItems.GOGGLES.asStack());
        showWater(scene, controller, 28000);
        text(scene, util, target, 110, "goggles");

        scene.overlay().showOutline(PonderPalette.INPUT, "reactor_feed", feed, 130);
        text(scene, util, util.grid().at(4, 4, 2), 130, "reactor_feed");

        scene.overlay().showOutline(PonderPalette.OUTPUT, "reactor_steam", gas, 130);
        scene.overlay().showOutline(PonderPalette.BLUE, "reactor_water", liquid, 130);
        text(scene, util, util.grid().at(2, 3, 2), 130, "reactor_split");
        showWater(scene, controller, 8000);
        scene.idle(40);
        scene.markAsFinished();
    }

    private static void showWater(CreateSceneBuilder scene, BlockPos controller, int amount) {
        scene.world().modifyBlockEntity(controller, AmmoniaSeparatorBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new CompoundTag(), lookup);
            var water = new ThermalFluidTank(48000, fluid -> { });water.setFluid(new FluidStack(Fluids.WATER, amount));
            tag.put("SeparatorWater", water.writeToNBT(lookup, new CompoundTag()));be.readClient(tag, lookup);
        });
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }
}
