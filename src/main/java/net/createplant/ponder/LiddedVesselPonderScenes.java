package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** A 3x3 lidded vessel: joining, pipes, opening the hatch by hand, where a stirrer goes, the blowout under pressure, and reading it; and the stirrer. */
public final class LiddedVesselPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.lidded_vessel.";
    private static final String COMPACT_LANG = "ponder.create_refinery_n_reactors.lidded_vessel_2x2.";
    private static final String INTERNAL_LANG = "ponder.create_refinery_n_reactors.vessel_internals.";

    private LiddedVesselPonderScenes() { }

    public static void sulfur(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("stainless_tank_sulfur", PonderText.title(LANG + "sulfur_title", "Sulfur dioxide in a stainless-steel tank"));
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        var tank = new BlockPos(3, 2, 3);
        var heat = com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL;
        scene.world().setBlock(tank, CETransport.LIDDED_VESSEL.get().defaultBlockState(), false);
        scene.world().setBlock(tank.below(), com.simibubi.create.AllBlocks.BLAZE_BURNER.getDefaultState().setValue(heat,
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING), false);
        scene.world().setBlock(tank.above(), CETransport.STAINLESS_PIPE.get().defaultBlockState()
            .setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.UP,true).setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.DOWN,true),false);
        scene.world().setBlock(tank.east(), CETransport.STAINLESS_PIPE.get().defaultBlockState()
            .setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.EAST,true).setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.WEST,true),false);
        scene.world().setBlock(tank.west(), com.simibubi.create.AllBlocks.ANDESITE_FUNNEL.getDefaultState()
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING, Direction.WEST)
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, false),false);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.world().showSection(util.select().position(tank.west()), Direction.EAST);
        scene.overlay().showControls(util.vector().centerOf(tank.west()), Pointing.RIGHT, 100).withItem(net.createplant.registry.CEItems.SULFUR_DUST.toStack());
        scene.overlay().showControls(util.vector().centerOf(tank.above()), Pointing.DOWN, 100).withItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("air_gas_bucket"))));
        text(scene,util,tank,120,"sulfur_feed");
        scene.world().showSection(util.select().position(tank.east()), Direction.WEST);
        scene.overlay().showControls(util.vector().centerOf(tank.east()), Pointing.LEFT, 100).withItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("sulfur_dioxide_bucket"))));
        text(scene,util,tank.below(),120,"sulfur_heat");
        scene.markAsFinished();
    }

    public static void vessel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("lidded_vessel", PonderText.title(LANG + "title", "Lidded vessel"));
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        // Create joins the cells on placement; the drawn vessel is given the controller data that joining would write.
        BlockPos controller = util.grid().at(1, 1, 1);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos part = controller.offset(x, 0, z);
            scene.world().modifyBlockEntityNBT(util.select().position(part), LiddedVesselBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 3);tag.putInt("Height", 1); }
                else tag.put("Controller", NbtUtils.writeBlockPos(controller));
            });
        }
        var vessel = util.select().fromTo(1, 1, 1, 3, 1, 3);
        var topPipe = util.select().fromTo(1, 2, 1, 1, 3, 1);
        var sidePipe = util.select().fromTo(4, 1, 1, 5, 1, 1);
        BlockPos top = util.grid().at(2, 1, 2);
        scene.idle(10);

        scene.world().showSection(vessel, Direction.DOWN);
        scene.world().modifyBlockEntity(controller, LiddedVesselBlockEntity.class, be -> be.getTankInventory().fill(new FluidStack(Fluids.WATER, 20000), FluidAction.EXECUTE));
        text(scene, util, top, 130, "place");
        scene.overlay().showControls(util.vector().blockSurface(controller,Direction.NORTH),Pointing.RIGHT,35).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        inspectionWindows(scene,controller,3,1);
        text(scene,util,controller,110,"window");

        scene.world().showSection(sidePipe, Direction.WEST);
        scene.world().showSection(topPipe, Direction.DOWN);
        text(scene, util, util.grid().at(1, 2, 1), 110, "pipes");

        lid(scene, util, vessel, top, true);
        text(scene, util, top, 120, "open");
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.DOWN), Pointing.UP, 60).withItem(CEReactor.STIRRER.toStack());
        text(scene, util, top, 140, "internals");

        lid(scene, util, vessel, top, false);
        scene.idle(20);
        text(scene, util, top, 120, "blowout");
        lid(scene, util, vessel, top, true);
        scene.effects().emitParticles(new Vec3(2.5, 2, 2.5), scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, .25, 0)), 6, 20);
        scene.idle(100);

        lid(scene, util, vessel, top, false);
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 80).withItem(AllItems.GOGGLES.asStack());
        text(scene, util, top, 100, "goggles");
        scene.markAsFinished();
    }

    public static void compactVessel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("lidded_vessel_2x2", PonderText.title(COMPACT_LANG + "title", "Stacking a storage-only 2x2 vessel"));
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        BlockPos controller = util.grid().at(1, 1, 1), top = controller.above();
        Selection lower = util.select().fromTo(1, 1, 1, 2, 1, 2), upper = util.select().fromTo(1, 2, 1, 2, 2, 2);
        Selection all = util.select().fromTo(1, 1, 1, 2, 2, 2);
        scene.world().modifyBlockEntityNBT(util.select().position(controller), LiddedVesselBlockEntity.class, tag -> tag.putInt("Height", 1));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var hatch = LiddedVesselBlock.Hatch.quarter(z == 0 ? Direction.SOUTH : Direction.NORTH, x == 0 ? Direction.EAST : Direction.WEST);
            scene.world().modifyBlocks(util.select().position(controller.offset(x, 0, z)), state -> state.setValue(LiddedVesselBlock.HATCH, hatch), false);
        }
        scene.world().showSection(lower, Direction.DOWN);
        compactText(scene, util, controller, 100, "base");

        scene.overlay().showControls(util.vector().topOf(controller), Pointing.DOWN, 45).rightClick()
            .withItem(CETransport.LIDDED_VESSEL.get().asItem().getDefaultInstance());
        scene.idle(15);
        scene.world().modifyBlockEntityNBT(util.select().position(controller), LiddedVesselBlockEntity.class, tag -> tag.putInt("Height", 2));
        scene.world().modifyBlocks(lower, state -> state.setValue(LiddedVesselBlock.HATCH, LiddedVesselBlock.Hatch.NONE), false);
        scene.world().showSection(upper, Direction.DOWN);
        compactText(scene, util, top, 130, "stack");
        scene.overlay().showControls(util.vector().blockSurface(controller,Direction.NORTH),Pointing.RIGHT,35).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        inspectionWindows(scene,controller,2,2);
        compactText(scene,util,controller,110,"window");

        lid(scene, util, all, top, true);
        compactText(scene, util, top, 100, "hatch");
        compactText(scene, util, controller, 100, "internals");
        lid(scene, util, all, top, false);
        compactText(scene, util, controller.below(), 150, "rotation");
        compactText(scene, util, top, 100, "top");
        scene.markAsFinished();
    }

    /** The stirrer under a stainless tank: placing it, the cogwheel that turns it, and the impellers it turns in the tank's two lowest layers. */
    public static void internals(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vessel_internals", PonderText.title(INTERNAL_LANG + "title", "Stirring a stainless tank"));
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        BlockPos stirrer = util.grid().at(4, 1, 4), cog = stirrer.north(), centre = stirrer.above();
        Selection drive = util.select().position(stirrer).add(util.select().position(cog));
        scene.idle(10);
        scene.world().showSection(drive, Direction.DOWN);
        scene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.GREEN, "stirrer", util.select().position(stirrer), 140);
        PonderText.caption(scene, util.vector().centerOf(stirrer), INTERNAL_LANG + "install", 140);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 5, 1, 5).substract(drive), Direction.UP);
        scene.world().showSection(util.select().fromTo(3, 2, 3, 5, 3, 5), Direction.DOWN);
        scene.idle(15);
        scene.world().setKineticSpeed(util.select().position(stirrer), 64);
        scene.world().setKineticSpeed(util.select().position(cog).add(util.select().position(cog.below())), -64);
        PonderText.caption(scene, util.vector().centerOf(centre), INTERNAL_LANG + "impeller", 160);
        PonderText.caption(scene, util.vector().blockSurface(cog, Direction.NORTH), INTERNAL_LANG + "operate", 160);
        scene.overlay().showControls(util.vector().topOf(centre.above()), Pointing.DOWN, 80).withItem(AllItems.GOGGLES.asStack());
        scene.idle(100);
        scene.markAsFinished();
    }

    private static void inspectionWindows(CreateSceneBuilder scene,BlockPos origin,int width,int height) {
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<width;z++)
            scene.world().modifyBlockEntity(origin.offset(x,y,z),LiddedVesselBlockEntity.class,be->{
                var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);tag.putBoolean("Windows",true);be.readClient(tag,lookup);
            });
        scene.addInstruction(s->s.forEach(net.createmod.ponder.api.element.WorldSectionElement.class,net.createmod.ponder.api.element.WorldSectionElement::queueRedraw));
    }

    /** An empty hand swings the hatch; the pipe beside it on the top stays connected. */
    private static void lid(CreateSceneBuilder scene, SceneBuildingUtil util, Selection vessel, BlockPos top, boolean open) {
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().modifyBlocks(vessel, state -> state.setValue(LiddedVesselBlock.OPEN, open), false);
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }

    private static void compactText(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), COMPACT_LANG + key, ticks);
    }
}
