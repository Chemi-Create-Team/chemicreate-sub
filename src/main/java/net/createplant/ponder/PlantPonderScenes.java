package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createplant.block.CoolerRadiatorBlock;
import net.createplant.block.CoolingFanShroudBlock;
import net.createplant.block.ThermalCasingBlock;
import net.createplant.cloud.GasCloud;
import net.createplant.registry.CEFluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Each catalogue entry has a distinct storyboard and a real model-scale structure. */
public final class PlantPonderScenes {
    private PlantPonderScenes() { }

    public static void show(SceneBuilder builder, SceneBuildingUtil util, PlantTutorialCatalog.Tutorial tutorial) {
        if (tutorial.family() != null) {
            ManufacturingPonderScenes.show(builder, util, tutorial);
            return;
        }
        var scene = new CreateSceneBuilder(builder);
        scene.title("plant_" + tutorial.id(), PonderText.title(tutorial.translation("title"), tutorial.id()));
        int size=tutorial.size()==null ? 7 : tutorial.size();
        scene.configureBasePlate(0, 0, size);
        scene.scaleSceneView(tutorial.scale() == null ? .65f : tutorial.scale());
        scene.showBasePlate();
        if (tutorial.id().startsWith("tubular_boiler")) {
            int width=tutorial.id().endsWith("3x3") ? 3 : tutorial.id().endsWith("2x2") ? 2 : 1;
            BlockPos controller=new BlockPos(2,2,3);
            for (int x=0;x<3;x++) for (int y=0;y<width;y++) for (int z=0;z<width;z++) {
                BlockPos part=controller.offset(x,y,z);
                scene.world().modifyBlockEntityNBT(util.select().position(part),com.simibubi.create.content.fluids.tank.FluidTankBlockEntity.class,tag->{
                    tag.putInt("Size",width);tag.putInt("Height",3);
                    if (part.equals(controller)) tag.remove("Controller");
                    else tag.put("Controller",net.minecraft.nbt.NbtUtils.writeBlockPos(controller));
                });
            }
        }
        int height=tutorial.height()==null ? 7 : tutorial.height();
        var factory = util.select().fromTo(0, 1, 0, size-1, height-1, size-1);
        boolean converter=tutorial.id().equals("converter");
        Selection converterVessel=converter ? converterVessel(util) : null;
        Selection converterGas=converter ? converterGas(util) : null;
        ElementLink<WorldSectionElement> movingVessel=null;
        if(tutorial.id().startsWith("turbine_")) {
            scene.world().setBlocks(factory, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
            ElectrolysisPonderScenes.completeThermalTurbine(scene,util);
        }
        if (tutorial.formed() != null) scene.world().modifyBlocks(factory, PlantPonderScenes::placed, false);
        if (tutorial.id().startsWith("turbine_") || tutorial.steps().stream().noneMatch(step -> step.reveal() != null)) {
            PonderText.build(scene, util, 0, 1, 0, size-1, height-1, size-1, Direction.DOWN);
        }
        scene.idle(15);
        for (var step : tutorial.steps()) {
            BlockPos target = new BlockPos(step.target()[0], step.target()[1], step.target()[2]);
            if (step.reveal() != null && !converter && !tutorial.id().startsWith("turbine_")) {
                int[] box = step.reveal();
                PonderText.build(scene, util, box[0], box[1], box[2], box[3], box[4], box[5], Direction.DOWN);
                scene.idle(15);
            }
            if (step.replace() != null) {
                scene.world().setBlock(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(step.replace())).defaultBlockState(), false);
            }
            if (step.key().equals(tutorial.formed())) scene.world().restoreBlocks(factory);
            if (tutorial.id().startsWith("tubular_boiler") && step.key().equals("step_3")) {
                scene.world().modifyBlockEntityNBT(util.select().position(2, 2, 3), net.createplant.block.entity.HorizontalTankBlockEntity.class, tag -> {
                    tag.putBoolean("TubeMode", true);
                    tag.putInt("TubeGauge", 42);
                });
            }
            if (converter) {
                BlockPos owner = new BlockPos(1, 6, 6), origin = new BlockPos(5, 1, 6);
                if (step.key().equals("step_1"))
                    scene.world().showSection(factory.copy().substract(converterVessel).substract(converterGas), Direction.DOWN);
                if (step.key().equals("step_2"))
                    scene.world().showSection(converterVessel, Direction.DOWN);
                if (step.key().equals("step_6"))
                    scene.world().showSection(converterGas, Direction.DOWN);
                if (step.key().equals(tutorial.formed()) || step.key().equals("step_2") || step.key().equals("step_3"))
                    scene.world().modifyBlockEntity(owner, net.createplant.block.entity.ConverterTrunnionBearingBlockEntity.class,
                        be -> be.ponderVisual(origin, Direction.NORTH, 0, false));
                if (step.key().equals("step_4") || step.key().equals("step_7"))
                    scene.world().modifyBlockEntity(owner, net.createplant.block.entity.ConverterTrunnionBearingBlockEntity.class,
                        be -> be.ponderVisual(origin, Direction.NORTH, 0, true));
                if (step.key().equals("step_5")) {
                    scene.world().modifyBlockEntity(owner, net.createplant.block.entity.ConverterTrunnionBearingBlockEntity.class,
                        be -> be.ponderVisual(origin, Direction.NORTH, 60, false));
                    movingVessel=scene.world().makeSectionIndependent(converterVessel);
                    scene.world().configureCenterOfRotation(movingVessel, Vec3.atCenterOf(new BlockPos(5, 6, 6)));
                    scene.world().rotateSection(movingVessel, -60, 0, 0, 40);
                    scene.world().rotateBearing(owner, -60, 40);
                }
                if (step.key().equals("step_6") && movingVessel != null) {
                    scene.world().rotateSection(movingVessel, 60, 0, 0, 40);
                    scene.world().rotateBearing(owner, 60, 40);
                    scene.world().modifyBlockEntity(owner, net.createplant.block.entity.ConverterTrunnionBearingBlockEntity.class,
                        be -> be.ponderVisual(origin, Direction.NORTH, 0, false));
                }
                if (step.key().equals("step_3")) {
                    scene.overlay().showOutline(PonderPalette.OUTPUT, "converter_glue", converterVessel, 120);
                    scene.overlay().showControls(util.vector().centerOf(target), net.createmod.catnip.math.Pointing.DOWN, 100)
                        .rightClick().withItem(com.simibubi.create.AllItems.SUPER_GLUE.asStack());
                }
            }
            if(tutorial.id().startsWith("turbine_") && step.key().equals("step_2")){
                scene.world().modifyBlockEntity(new BlockPos(17,5,10),net.createplant.block.entity.SteamTurbineBlockEntity.class,rotor->{rotor.thermodynamics().input().fill(net.createplant.thermal.FluidThermal.at(new net.neoforged.neoforge.fluids.FluidStack(net.createplant.registry.CEFluids.STEAM.get(),1000),750),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);rotor.thermodynamics().step(true,64);});
                scene.world().setKineticSpeed(util.select().fromTo(2,5,10,19,5,10),256);
            }
            if (tutorial.id().equals("process_gas_recovery")) steamRecovery(scene, util, step.key(), target);
            if (tutorial.id().equals("gas_pipes") && step.key().equals("step_5")) {
                scene.world().destroyBlock(target);
                scene.world().createEntity(world -> GasCloud.preview(world, target, new FluidStack(CEFluids.STEAM.get(), 1000)));
            }
            if (tutorial.id().equals("toxic_gas")) toxicGas(scene, step.key(), target);
            if (step.speed() != null) {
                scene.world().setKineticSpeed(factory, step.speed());
                scene.world().modifyBlockEntityNBT(factory, net.createplant.block.entity.LiddedVesselBlockEntity.class, tag -> tag.putFloat("InputSpeed", step.speed()));
            }
            if (step.smoke() != null)
                scene.effects().emitParticles(Vec3.atBottomCenterOf(new BlockPos(step.smoke()[0], step.smoke()[1] + 1, step.smoke()[2])),
                    scene.effects().simpleParticleEmitter(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, new Vec3(0, .05, 0)), 1, 120);
            boolean last = step == tutorial.steps().getLast();
            var palette = last ? PonderPalette.OUTPUT : step.wrench() != null || step.use() != null ? PonderPalette.BLUE : PonderPalette.INPUT;
            scene.overlay().showOutline(palette, tutorial.id() + "_" + step.key(), util.select().position(target), 120);
            if (Boolean.TRUE.equals(step.wrench())) scene.overlay().showControls(util.vector().centerOf(target),net.createmod.catnip.math.Pointing.DOWN,100).rightClick().withItem(com.simibubi.create.AllItems.WRENCH.asStack());
            if (step.use() != null)
                scene.overlay().showControls(util.vector().centerOf(target), net.createmod.catnip.math.Pointing.DOWN, 100).rightClick()
                    .withItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.use()))));
            if (step.item() != null) {
                // Material icons are illustrative; no free production is simulated.
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item())));
                scene.world().createItemEntity(Vec3.atCenterOf(target).add(0, 1, 0), new Vec3(0, .04, 0), stack);
            }
            PonderText.caption(scene, util.vector().centerOf(target), tutorial.translation(step.key()), 120);
        }
        scene.markAsFinished();
    }

    private static void toxicGas(CreateSceneBuilder scene, String step, BlockPos target) {
        switch (step) {
            case "step_2" -> {
                scene.world().destroyBlock(target);
                scene.world().createEntity(world -> GasCloud.preview(world, target,
                    new FluidStack(net.createplant.registry.CEChlorAlkali.CHLORINE.get(), 1000)));
            }
            case "step_5" -> scene.world().modifyEntities(GasCloud.class, GasCloud::discard);
            default -> { }
        }
    }

    private static void steamRecovery(CreateSceneBuilder scene, SceneBuildingUtil util, String step, BlockPos target) {
        switch (step) {
            case "step_1" -> {
                scene.world().destroyBlock(target);
                scene.world().createEntity(world -> GasCloud.preview(world, target, new FluidStack(CEFluids.STEAM.get(), 1100)));
            }
            case "step_3" -> {
                scene.world().modifyBlocks(util.select().fromTo(target, target.above()),
                    state -> state.setValue(BlockStateProperties.OPEN, true), false);
                scene.world().modifyEntities(GasCloud.class, cloud -> cloud.refreshPreview(1100));
            }
            case "step_4" -> {
                scene.world().modifyBlock(target.west(2), state -> state.setValue(BlockStateProperties.POWER, 12), false);
                scene.world().modifyBlock(target.west(), state -> state.setValue(BlockStateProperties.POWERED, true), false);
                scene.world().modifyBlock(target, state -> state.setValue(BlockStateProperties.LIT, true), false);
            }
            case "step_5" -> {
                scene.world().modifyBlock(target.below(), state -> state.setValue(net.createplant.block.GasRecoveryDuctBlock.ACTIVE, true), false);
                scene.world().modifyEntities(GasCloud.class, cloud -> cloud.refreshPreview(550));
            }
            case "step_6" -> {
                scene.world().setBlocks(util.select().fromTo(1, 4, 3, 5, 4, 7), Blocks.AIR.defaultBlockState(), false);
                scene.world().modifyEntities(GasCloud.class, cloud -> cloud.refreshPreview(200));
                scene.idle(25);
                scene.world().modifyEntities(GasCloud.class, cloud -> cloud.refreshPreview(50));
                scene.idle(20);
                scene.world().modifyEntities(GasCloud.class, GasCloud::discard);
                scene.world().modifyBlock(new BlockPos(7, 1, 4), state -> state.setValue(BlockStateProperties.LIT, false), false);
            }
            default -> { }
        }
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

    /** The look a multiblock part has as placed, before the structure forms; the structure NBT keeps the formed look. */
    private static BlockState placed(BlockState state) {
        if (state.getBlock() instanceof net.createplant.block.GasTankContainerBlock) return state.setValue(net.createplant.block.GasTankContainerBlock.SIZE, 0);
        if (state.is(net.createplant.registry.CEHeating.JACKET.get()) || state.is(net.createplant.registry.CEHeating.JACKET_CONTROLLER.get())) {
            var axis = state.getValue(ThermalCasingBlock.FACING).getAxis();
            var pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState();
            for (Direction side : Direction.values())
                pipe = pipe.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == axis);
            return pipe;
        }
        if (state.hasProperty(ThermalCasingBlock.FORMED)) return state.setValue(ThermalCasingBlock.FORMED, false);
        if (state.getBlock() instanceof net.createplant.block.ExchangerControllerBlock controller)
            return controller.kind.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING, state.getValue(net.createplant.block.ThermalMachineBlock.FACING));
        if (state.getBlock() instanceof CoolerRadiatorBlock || state.getBlock() instanceof CoolingFanShroudBlock) return state.getBlock().defaultBlockState();
        if (state.getBlock() instanceof net.createplant.block.ConverterShellBlock)
            return state.setValue(net.createplant.block.ConverterShellBlock.PART, net.createplant.block.ConverterShellBlock.Part.LOOSE);
        return state;
    }
}
