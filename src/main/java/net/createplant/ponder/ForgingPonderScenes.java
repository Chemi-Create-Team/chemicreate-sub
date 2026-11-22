package net.createplant.ponder;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ForgingPonderScenes {
    private ForgingPonderScenes() {}

    public static void steel(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String key = "ponder.create_refinery_n_reactors.steel_forging.";
        scene.title("steel_forging", PonderText.title(key + "title", "Forge steel along a belt"));
        scene.configureBasePlate(0, 0, 21);
        scene.scaleSceneView(.5f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(1, 0, 9, 3, 4, 11), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(2, 3, 10), 64);
        scene.idle(10);
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(2, 1, 10),
                        com.simibubi.create.content.processing.basin.BasinBlockEntity.class,
                        basin -> {
                            basin.getInputInventory()
                                    .setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
                            basin.getInputInventory()
                                    .setStackInSlot(1, new ItemStack(Items.CHARCOAL));
                        });
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(2, 3, 10),
                        com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity.class,
                        mixer -> mixer.startProcessingBasin());
        scene.overlay()
                .showControls(util.vector().of(2, 2, 10), Pointing.DOWN, 90)
                .withItem(new ItemStack(Items.IRON_INGOT));
        scene.overlay()
                .showControls(util.vector().of(3, 2, 10), Pointing.DOWN, 90)
                .withItem(new ItemStack(Items.CHARCOAL));
        PonderText.caption(scene, util.vector().of(2, 1, 10), key + "pig", 100);
        scene.world().showSection(util.select().fromTo(1, 1, 0, 19, 3, 6), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 5, 17, 1, 5), -1);
        for (int x : new int[] {2, 6, 7, 11, 12, 16})
            scene.world().setKineticSpeed(util.select().position(x, 3, 5), 128);
        for (int x : new int[] {4, 9, 14})
            scene.world().setKineticSpeed(util.select().position(x, 2, 1), 256);
        var input =
                scene.world()
                        .createItemOnBelt(
                                new BlockPos(1, 1, 5), Direction.WEST, CEItems.PIG_IRON.toStack());
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(2, 3, 5),
                        MechanicalPressBlockEntity.class,
                        press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        PonderText.caption(scene, util.vector().of(2, 2, 5), key + "press", 100);
        scene.world().changeBeltItemTo(input, CEItems.HEATED_UNFINISHED_STEEL.toStack());
        scene.overlay()
                .showControls(util.vector().of(4, 2, 2), Pointing.DOWN, 100)
                .withItem(new ItemStack(Items.LAVA_BUCKET));
        PonderText.caption(scene, util.vector().of(4, 1, 5), key + "heat", 100);
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(6, 3, 5),
                        MechanicalPressBlockEntity.class,
                        press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        PonderText.caption(scene, util.vector().of(6, 2, 5), key + "fold", 100);
        PonderText.caption(scene, util.vector().of(11, 2, 5), key + "repeat", 110);
        scene.world()
                .createItemOnBelt(
                        new BlockPos(17, 1, 5), Direction.WEST, CEItems.STEEL_INGOT.toStack());
        scene.overlay()
                .showControls(util.vector().of(17, 3, 5), Pointing.DOWN, 100)
                .withItem(CEItems.STEEL_SLAG.toStack());
        PonderText.caption(scene, util.vector().of(18, 2, 5), key + "output", 110);
        scene.markAsFinished();
    }

    public static void fluorite(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String key = "ponder.create_refinery_n_reactors.fluorite_sources.";
        scene.title(
                "fluorite_sources",
                PonderText.title(key + "title", "Make fluorite with fans or mixing"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.7f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 1, 4, 2, 3), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(1, 2, 2), 128);
        scene.world()
                .createItemOnBeltLike(
                        new BlockPos(4, 1, 2), Direction.UP, new ItemStack(Items.DIORITE));
        PonderText.caption(scene, util.vector().of(2, 2, 2), key + "haunt", 120);
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(4, 1, 2),
                        com.simibubi.create.content.logistics.depot.DepotBlockEntity.class,
                        depot -> depot.setHeldItem(new ItemStack(Items.CALCITE)));
        scene.world().showSection(util.select().fromTo(5, 2, 5, 8, 5, 7), Direction.DOWN);
        scene.world()
                .setKineticSpeed(
                        util.select().position(5, 4, 6).add(util.select().position(7, 2, 6)), 64);
        scene.idle(10);
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(5, 2, 6),
                        com.simibubi.create.content.processing.basin.BasinBlockEntity.class,
                        basin -> {
                            basin.getInputInventory()
                                    .setStackInSlot(0, new ItemStack(Items.BONE_MEAL));
                            basin.getInputInventory()
                                    .setStackInSlot(1, new ItemStack(Items.GRAVEL));
                            basin.getTanks()
                                    .getFirst()
                                    .getCapability()
                                    .fill(
                                            new net.neoforged.neoforge.fluids.FluidStack(
                                                    net.minecraft.world.level.material.Fluids.WATER,
                                                    250),
                                            net.neoforged.neoforge.fluids.capability.IFluidHandler
                                                    .FluidAction.EXECUTE);
                        });
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(5, 4, 6),
                        com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity.class,
                        mixer -> mixer.startProcessingBasin());
        scene.overlay()
                .showControls(util.vector().of(5, 3, 6), Pointing.DOWN, 110)
                .withItem(new ItemStack(Items.BONE_MEAL));
        scene.overlay()
                .showControls(util.vector().of(6, 3, 6), Pointing.DOWN, 110)
                .withItem(new ItemStack(Items.GRAVEL));
        PonderText.caption(scene, util.vector().of(5, 2, 6), key + "mix", 120);
        scene.overlay()
                .showControls(util.vector().of(6, 2, 6), Pointing.DOWN, 100)
                .withItem(new ItemStack(Items.WATER_BUCKET));
        PonderText.caption(scene, util.vector().of(7, 2, 6), key + "water", 100);
        scene.markAsFinished();
    }
}
