package net.createplant.atomics.ponder;

import net.createplant.ponder.ManufacturingPonderScenes;
import net.createplant.ponder.PlantPonderScenes;
import net.createplant.ponder.PlantTutorialCatalog;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The converter's share of the catalogue tutorials and manufacturing scenes, plugged into the core Ponder classes by the atomics addon. */
public final class ConverterPonderScenes implements PlantPonderScenes.Extension {
    private Selection converterVessel;
    private Selection converterGas;
    private ElementLink<WorldSectionElement> movingVessel;

    public static void register() {
        PlantPonderScenes.register("converter", ConverterPonderScenes::new);
        ManufacturingPonderScenes.register("converter", (scene, util, process, recipe, at, complete) ->
            scene.world().modifyBlockEntity(at, ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(new BlockPos(process.offset() + 5, 1, 6), Direction.NORTH, complete ? 60 : 0, !complete)));
        ManufacturingPonderScenes.register("centrifuge", (scene, util, process, recipe, at, complete) -> {
            var fixed = net.createplant.chemistry.ProcessRecipe.all().stream().filter(r -> process.recipe().equals("process:" + r.id())).findFirst().orElse(null);
            if (fixed != null) scene.world().modifyBlockEntity(at, net.createplant.atomics.block.entity.GasCentrifugeBlockEntity.class, be -> be.preview(fixed, complete));
        });
    }

    @Override public boolean ownsReveal() { return true; }

    @Override
    public void step(CreateSceneBuilder scene, SceneBuildingUtil util, PlantTutorialCatalog.Tutorial tutorial, PlantTutorialCatalog.Step step, BlockPos target, Selection factory) {
        if (converterVessel == null) { converterVessel = converterVessel(util); converterGas = converterGas(util); }
        BlockPos owner = new BlockPos(1, 6, 6), origin = new BlockPos(5, 1, 6);
        if (step.key().equals("step_1"))
            scene.world().showSection(factory.copy().substract(converterVessel).substract(converterGas), Direction.DOWN);
        if (step.key().equals("step_2"))
            scene.world().showSection(converterVessel, Direction.DOWN);
        if (step.key().equals("step_6"))
            scene.world().showSection(converterGas, Direction.DOWN);
        if (step.key().equals(tutorial.formed()) || step.key().equals("step_2") || step.key().equals("step_3"))
            scene.world().modifyBlockEntity(owner, net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(origin, Direction.NORTH, 0, false));
        if (step.key().equals("step_4") || step.key().equals("step_7"))
            scene.world().modifyBlockEntity(owner, net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(origin, Direction.NORTH, 0, true));
        if (step.key().equals("step_5")) {
            scene.world().modifyBlockEntity(owner, net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(origin, Direction.NORTH, 60, false));
            movingVessel=scene.world().makeSectionIndependent(converterVessel);
            scene.world().configureCenterOfRotation(movingVessel, Vec3.atCenterOf(new BlockPos(5, 6, 6)));
            scene.world().rotateSection(movingVessel, -60, 0, 0, 40);
            scene.world().rotateBearing(owner, -60, 40);
        }
        if (step.key().equals("step_6") && movingVessel != null) {
            scene.world().rotateSection(movingVessel, 60, 0, 0, 40);
            scene.world().rotateBearing(owner, 60, 40);
            scene.world().modifyBlockEntity(owner, net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(origin, Direction.NORTH, 0, false));
        }
        if (step.key().equals("step_3")) {
            scene.overlay().showOutline(PonderPalette.OUTPUT, "converter_glue", converterVessel, 120);
            scene.overlay().showControls(util.vector().centerOf(target), net.createmod.catnip.math.Pointing.DOWN, 100)
                .rightClick().withItem(com.simibubi.create.AllItems.SUPER_GLUE.asStack());
        }
    }

    @Override
    public BlockState placed(BlockState state) {
        if (state.getBlock() instanceof net.createplant.atomics.block.ConverterShellBlock)
            return state.setValue(net.createplant.atomics.block.ConverterShellBlock.PART, net.createplant.atomics.block.ConverterShellBlock.Part.LOOSE);
        return state;
    }

    private static Selection converterVessel(SceneBuildingUtil util) {
        Selection selection=util.select().position(2, 6, 6).add(util.select().position(8, 6, 6));
        for (int x=4;x<=6;x++) for (int z=5;z<=7;z++)
            selection=selection.add(util.select().position(x, 3, z));
        for (int y=4;y<=7;y++) {
            for (int x=3;x<=7;x++) for (int z=4;z<=8;z++)
                if (x==3 || x==7 || z==4 || z==8)
                    selection=selection.add(util.select().position(x, y, z));
            for (int x=4;x<=6;x++) for (int z=5;z<=7;z++)
                if (x==4 || x==6 || z==5 || z==7)
                    selection=selection.add(util.select().position(x, y, z));
        }
        return selection;
    }

    private static Selection converterGas(SceneBuildingUtil util) {
        Selection selection=util.select().position(5, 10, 11);
        for (int x=6;x<=13;x++) selection=selection.add(util.select().position(x, 10, 11));
        for (int y=1;y<10;y++) selection=selection.add(util.select().position(13, y, 11));
        return selection;
    }
}
