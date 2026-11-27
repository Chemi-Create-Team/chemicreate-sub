package net.createplant.ponder;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.function.Consumer;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Standard Create transport around an internally stirred vessel; the shown inventories use its normal client sync format. */
public final class VesselBasinPonderScenes {
    /** The two-layer stirred tank, its roof-mounted stirrer and the cogwheel beside it. */
    private static final BlockPos OWNER=new BlockPos(3,2,3), TOP=new BlockPos(4,3,4), STIRRER=new BlockPos(4,4,4), COG=new BlockPos(4,4,3), COG_MOTOR=new BlockPos(4,5,3);
    private static final BlockPos GAUGE=new BlockPos(2,2,3), INPUT=new BlockPos(2,2,4), OUTPUT=new BlockPos(6,2,3);
    private static final BlockPos PUMP_IN=new BlockPos(4,2,1), PUMP_OUT=new BlockPos(4,2,7);
    private static final BlockPos COOLANT=new BlockPos(3,2,2);
    private static final BlockPos RELIEF=new BlockPos(6,2,5);
    private static final String BASIN="ponder.create_refinery_n_reactors.vessel_basin.", MAINTENANCE="ponder.create_refinery_n_reactors.vessel_maintenance.";
    private VesselBasinPonderScenes() { }

    public static void basin(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=begin(builder,"vessel_basin",BASIN,"Mixing and heating in a lidded vessel");
        clientState(scene,OWNER,tag->tag.putBoolean("Stirrer",false));
        PonderText.build(scene, util, 3,2,3,5,3,5, Direction.DOWN);
        text(scene,util,OWNER,BASIN,"assemble",110);
        scene.overlay().showControls(util.vector().blockSurface(OWNER,Direction.NORTH),Pointing.RIGHT,45).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);windows(scene,true);
        text(scene,util,OWNER,BASIN,"window",120);

        PonderText.build(scene, util, 0,1,4,2,2,5, Direction.EAST);
        PonderText.build(scene, util, 4,1,0,4,2,2, Direction.SOUTH);
        speed(scene,util,true);
        scene.world().modifyBlockEntity(new BlockPos(4,2,0),FluidTankBlockEntity.class,be->be.getTankInventory().fill(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),12000),FluidAction.EXECUTE));
        scene.world().createItemOnBelt(new BlockPos(1,1,4),Direction.WEST,bauxite(2));
        scene.world().flapFunnel(INPUT,false);
        contents(scene,false,false);
        text(scene,util,INPUT,BASIN,"feed",140);

        scene.world().showSection(util.select().position(STIRRER).add(util.select().position(COG)).add(util.select().position(COG_MOTOR)),Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN,"stirrer",util.select().position(STIRRER),80);
        scene.idle(7);
        contents(scene,true,false);
        text(scene,util,STIRRER,BASIN,"impeller",130);
        var burners=util.select().fromTo(3,1,3,5,1,5);
        scene.world().showSection(burners,Direction.UP);
        scene.overlay().showControls(util.vector().centerOf(OWNER.below()),Pointing.RIGHT,45).rightClick().withItem(new ItemStack(Items.COAL));
        scene.world().modifyBlocks(burners,state->state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)
            ? state.setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED) : state,false);
        text(scene,util,OWNER.below(),BASIN,"heat",130);

        scene.world().showSection(util.select().position(GAUGE),Direction.EAST);
        contents(scene,true,true);gauge(scene,0,false);
        text(scene,util,COG,BASIN,"rotate",130);
        PonderText.build(scene, util, 6,1,2,8,2,3, Direction.WEST);
        PonderText.build(scene, util, 4,1,6,4,2,8, Direction.NORTH);
        scene.world().flapFunnel(OUTPUT,true);
        scene.world().createItemOnBelt(new BlockPos(7,1,3),Direction.WEST,new ItemStack(Items.IRON_NUGGET,3));
        contents(scene,true,true);
        text(scene,util,OUTPUT,BASIN,"products",140);
        scene.idle(120);scene.markAsFinished();
    }

    public static void maintenance(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=begin(builder,"vessel_maintenance",MAINTENANCE,"Grades and cooling");
        scene.rotateCameraY(90);
        scene.world().showSection(util.select().fromTo(0,1,0,8,3,8).substract(util.select().fromTo(3,1,2,3,2,2)),Direction.DOWN);
        windows(scene,true);coldContents(scene,250,0);
        storedFluid(scene,FluidForm.HIGH_PRESSURE_STEAM.stack(20000));gauge(scene,2,true);
        text(scene,util,GAUGE,MAINTENANCE,"pressure",140);
        text(scene,util,OWNER,MAINTENANCE,"boiling",180);
        scene.overlay().showControls(util.vector().topOf(TOP.east()),Pointing.DOWN,50).rightClick();
        scene.world().modifyBlocks(util.select().fromTo(3,2,3,5,3,5),state->state.setValue(LiddedVesselBlock.OPEN,true),false);
        storedFluid(scene,FluidStack.EMPTY);gauge(scene,-1,false);
        text(scene,util,OWNER,MAINTENANCE,"hatch",160);
        scene.world().modifyBlocks(util.select().fromTo(3,2,3,5,3,5),state->state.setValue(LiddedVesselBlock.OPEN,false),false);
        scene.overlay().showControls(util.vector().centerOf(OWNER.below()),Pointing.RIGHT,45).rightClick().withItem(new ItemStack(Items.ICE));
        PonderText.build(scene, util, 3,1,2,3,2,2, Direction.SOUTH);
        scene.world().modifyBlockEntity(COOLANT,FluidTankBlockEntity.class,be->be.getTankInventory().fill(
            FluidForm.LIQUID_NITROGEN.stack(6000),FluidAction.EXECUTE));
        coldContents(scene,250,0);gauge(scene,0,false);
        text(scene,util,OWNER,MAINTENANCE,"cooling",140);
        coldContents(scene,0,0,true);
        scene.world().hideSection(util.select().position(INPUT).add(util.select().position(4,2,6)),Direction.DOWN);
        text(scene,util,OWNER,MAINTENANCE,"cryo",140);
        scene.idle(120);scene.markAsFinished();
    }

    private static CreateSceneBuilder begin(SceneBuilder builder,String id,String prefix,String title) {
        var scene=new CreateSceneBuilder(builder);scene.title(id,PonderText.title(prefix+"title",title));
        scene.configureBasePlate(0,0,9);scene.scaleSceneView(.85f);scene.showBasePlate();scene.idle(15);return scene;
    }
    private static ItemStack bauxite(int count) { return new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location("bauxite")),count); }
    private static void windows(CreateSceneBuilder scene,boolean enabled) {
        for(int y=2;y<=3;y++)for(int x=3;x<=5;x++)for(int z=3;z<=5;z++)clientState(scene,new BlockPos(x,y,z),tag->tag.putBoolean("Windows",enabled));
        scene.addInstruction(s->s.forEach(net.createmod.ponder.api.element.WorldSectionElement.class,net.createmod.ponder.api.element.WorldSectionElement::queueRedraw));
    }
    private static void speed(CreateSceneBuilder scene,SceneBuildingUtil util,boolean running) {
        scene.world().setKineticSpeed(util.select().position(STIRRER),running ? 64 : 0);
        scene.world().setKineticSpeed(util.select().position(COG).add(util.select().position(COG_MOTOR)),running ? -64 : 0);
        scene.world().setKineticSpeed(util.select().fromTo(0,1,4,2,1,4).add(util.select().position(0,1,5)),running ? -64 : 0);
        scene.world().setKineticSpeed(util.select().fromTo(6,1,3,8,1,3).add(util.select().position(8,1,2)),running ? -64 : 0);
        for (BlockPos pump:new BlockPos[]{PUMP_IN,PUMP_OUT}) {
            scene.world().setKineticSpeed(util.select().position(pump),running ? 64 : 0);
            scene.world().setKineticSpeed(util.select().position(pump.below()),running ? -64 : 0);
        }
        scene.world().setKineticSpeed(util.select().position(4,1,0).add(util.select().position(4,1,7)),running ? -64 : 0);
    }
    private static void contents(CreateSceneBuilder scene,boolean internal,boolean running) {
        scene.world().modifyBlockEntity(OWNER,LiddedVesselBlockEntity.class,be->{
            var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);
            var items=new ItemStackHandler(13);items.setStackInSlot(0,bauxite(8));
            if(running)items.setStackInSlot(9,new ItemStack(Items.IRON_NUGGET,3));
            tag.put("Charge",items.serializeNBT(lookup));
            tag.put("TankContent",storage(FluidThermal.at(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),10000),running ? 363.15 : 293.15),lookup));
            if(running)tag.put("Tank2",FluidThermal.at(new FluidStack(CEChlorAlkali.SODIUM_ALUMINATE.get(),4000),363.15).save(lookup));else tag.remove("Tank2");
            tag.remove("Internal");tag.putBoolean("Stirrer",internal);
            tag.putBoolean("Formed",internal);tag.putBoolean("Running",running);tag.putBoolean("Windows",true);
            tag.putInt("DisplayedHeat",running ? BlazeBurnerBlock.HeatLevel.KINDLED.ordinal() : BlazeBurnerBlock.HeatLevel.NONE.ordinal());
            tag.putInt("Sections",LiddedVesselBlockEntity.IMPELLER_LAYERS);tag.putInt("Scale",1);tag.putInt("CycleTicks",100);tag.putFloat("Progress",40);
            tag.putString("Batch",CreatePlant.location("pressure_reaction/sodium_aluminate_solution").toString());tag.putString("Status",running ? "running" : "speed");
            be.readClient(tag,lookup);
        });
    }
    private static void coldContents(CreateSceneBuilder scene,int dirt,int wastewater) {
        coldContents(scene,dirt,wastewater,false);
    }
    private static void coldContents(CreateSceneBuilder scene,int dirt,int wastewater,boolean cryogenic) {
        scene.world().modifyBlockEntity(OWNER,LiddedVesselBlockEntity.class,be->{
            var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);
            var items=new ItemStackHandler(13);items.setStackInSlot(0,new ItemStack(Items.ICE,2));tag.put("Charge",items.serializeNBT(lookup));
            for(int i=0;i<7;i++)tag.remove("Tank"+i);
            tag.put("TankContent",storage(FluidThermal.at(cryogenic ? FluidForm.LIQUID_NITROGEN.stack(6000)
                : new FluidStack(Fluids.WATER,10000-wastewater),cryogenic ? 77.36 : 293.15),lookup));
            tag.remove("Internal");tag.putBoolean("Stirrer",false);
            tag.putBoolean("Windows",true);tag.putBoolean("Formed",false);tag.putBoolean("Running",false);tag.putInt("Sections",LiddedVesselBlockEntity.IMPELLER_LAYERS);
            tag.putInt("DisplayedHeat",BlazeBurnerBlock.HeatLevel.NONE.ordinal());tag.putString("Status","idle");
            be.readClient(tag,lookup);
        });
    }
    private static CompoundTag storage(FluidStack stack,net.minecraft.core.HolderLookup.Provider lookup) {
        var tank=new net.createplant.thermal.ThermalFluidTank(Math.max(16000,stack.getAmount()),s->{});
        tank.setFluid(stack.copy());return tank.writeToNBT(lookup,new CompoundTag());
    }
    private static void storedFluid(CreateSceneBuilder scene,FluidStack water) {
        clientState(scene,OWNER,tag->tag.put("TankContent",storage(water,scene.world().getHolderLookupProvider())));
    }
    private static void clientState(CreateSceneBuilder scene,BlockPos pos,Consumer<CompoundTag> change) {
        scene.world().modifyBlockEntity(pos,LiddedVesselBlockEntity.class,be->{var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);change.accept(tag);be.readClient(tag,lookup);});
    }
    private static void gauge(CreateSceneBuilder scene,int grade,boolean tripped) {
        scene.world().modifyBlockEntity(GAUGE,PipeGaugeBlockEntity.class,be->{var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);
            if(grade<0)tag.remove("Reading");else tag.putDouble("Reading",grade);
            tag.putBoolean("VesselMounted",true);tag.putBoolean("Alarm",tripped);be.readClient(tag,lookup);});
    }
    private static void text(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos pos,String prefix,String key,int ticks) {
        PonderText.caption(scene, util.vector().centerOf(pos), prefix+key, ticks);
    }
}
