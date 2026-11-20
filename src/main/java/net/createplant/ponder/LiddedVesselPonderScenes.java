package net.createplant.ponder;

import com.mojang.math.Transformation;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** A 3x3 lidded vessel: joining, pipes, opening the hatch by hand, putting an internal in, the blowout under pressure, and reading it. */
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
        scene.world().setBlock(tank.west(), com.simibubi.create.AllBlocks.ANDESITE_FUNNEL.getDefaultState()
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING, Direction.WEST)
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, false),false);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.world().showSection(util.select().position(tank.west()), Direction.EAST);
        scene.overlay().showControls(util.vector().centerOf(tank.west()), Pointing.RIGHT, 100).withItem(net.createplant.registry.CEItems.SULFUR_DUST.toStack());
        scene.overlay().showControls(util.vector().centerOf(tank.above()), Pointing.DOWN, 100).withItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("oxygen_gas_bucket"))));
        text(scene,util,tank,120,"sulfur_feed");
        scene.overlay().showControls(util.vector().centerOf(tank.above()), Pointing.DOWN, 100).withItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("sulfur_dioxide_bucket"))));
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
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(3, 1, 3), Direction.SOUTH), Pointing.LEFT, 40).rightClick().withItem(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(util.select().position(controller), LiddedVesselBlockEntity.class, tag -> {
            var item = new CompoundTag();item.putString("id", CEReactor.INTERNALS.get(ReactorModule.STIRRED).getId().toString());item.putInt("count", 1);tag.put("Internal", item);
        });
        text(scene, util, top, 140, "internals");
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 35)
            .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(util.select().position(controller), LiddedVesselBlockEntity.class, tag -> tag.remove("Internal"));
        scene.idle(30);

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

    /** Full-size native item models are easier to inspect outside the closed pressure envelope. */
    public static void internals(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vessel_internals", PonderText.title(INTERNAL_LANG + "title", "Internals for reaction vessels"));
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        ReactorModule[] modules = {ReactorModule.STIRRED};
        Vec3[] centres = {new Vec3(4.5, 1.5, 4.5)};
        String[] captions = {"impeller"};
        for (int i = 0; i < modules.length; i++) {
            ItemStack stack = CEReactor.INTERNALS.get(modules[i]).get().getDefaultInstance();
            Vec3 centre = centres[i];
            scene.world().createEntity(world -> display(world, stack, centre));
        }
        scene.idle(30);
        for (int i = 0; i < modules.length; i++) {
            PonderText.caption(scene, centres[i], INTERNAL_LANG + captions[i], 120);
        }
        scene.overlay().showControls(new Vec3(4.5, 1, 4.5), Pointing.DOWN, 80).rightClick()
            .withItem(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());
        PonderText.caption(scene, new Vec3(4.5, 1, 4.5), INTERNAL_LANG + "install", 170);
        PonderText.caption(scene, new Vec3(4.5, 1, 4.5), INTERNAL_LANG + "operate", 150);
        // Leave a clean final view of the model for inspection and native screenshots.
        scene.idle(120);
        scene.markAsFinished();
    }

    private static Display.ItemDisplay display(Level world, ItemStack stack, Vec3 centre) {
        var tag = new CompoundTag();
        tag.put("item", stack.save(world.registryAccess()));
        tag.putString("item_display", "none");
        tag.putString("billboard", "fixed");
        tag.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, Transformation.identity()).getOrThrow());
        var brightness = new CompoundTag();
        brightness.putInt("block", 15);brightness.putInt("sky", 15);
        tag.put("brightness", brightness);
        tag.putFloat("view_range", 4);
        tag.putFloat("width", 3);tag.putFloat("height", 3);
        tag.putFloat("shadow_strength", 0);
        var item = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);
        item.load(tag);
        item.setNoGravity(true);
        item.setPos(centre);
        return item;
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
