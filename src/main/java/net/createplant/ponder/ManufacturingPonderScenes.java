package net.createplant.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.FractionatingColumnBlock;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.createplant.registry.CEIsotopeGases;
import net.createplant.thermal.FluidThermal;
import net.minecraft.world.level.material.Fluids;
import net.createplant.block.entity.ConverterTrunnionBearingBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** A production family shares one timeline, with full-sized equipment and visible ingredient/product changes. */
public final class ManufacturingPonderScenes {
    private ManufacturingPonderScenes() { }

    public static void show(SceneBuilder builder, SceneBuildingUtil util, PlantTutorialCatalog.Tutorial tutorial) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("plant_" + tutorial.id(), PonderText.title(tutorial.translation("title"), tutorial.id()));
        scene.configureBasePlate(0, 0, tutorial.size());
        ElementLink<WorldSectionElement> visible = null;
        int current = -1;
        for (var step : tutorial.steps()) {
            var process = step.process();
            var selection = util.select().fromTo(process.offset(), 0, 0, process.offset() + process.width() - 1, process.height() - 1, process.width() - 1);
            if (current != process.offset()) {
                if (visible != null) { scene.world().hideIndependentSection(visible, Direction.WEST);scene.idle(18); }
                boolean tall = process.height() > 12;
                scene.addInstruction(s -> {
                    s.builder().configureBasePlate(0, 0, process.width());
                    s.builder().scaleSceneView(tall ? .4f : process.width() > 12 ? .55f : .75f);
                    s.builder().setSceneOffsetY(tall ? -5 : 0);
                });
                visible = scene.world().showIndependentSection(selection, Direction.DOWN);
                scene.world().moveSection(visible, new Vec3(-process.offset(), 0, 0), 0);
                scene.idle(12);current = process.offset();
            }
            var recipe = ManufacturingRecipe.resolve(Minecraft.getInstance().level, process.recipe());
            scene.world().setKineticSpeed(selection, 64);
            if (process.kind().equals("crushing")) scene.world().setKineticSpeed(util.select().position(absolute(process, process.machine()).east(2)), -64);
            snapshot(scene, util, process, recipe, step.item(), false);
            var input = recipe == null ? icon(process.feed() == null ? step.item() : process.feed())
                : firstMaterial(recipe.items(), recipe.fluids());
            if (!input.isEmpty()) scene.overlay().showControls(util.vector().centerOf(local(process.input())), Pointing.DOWN, 85).withItem(input);
            scene.overlay().showOutline(PonderPalette.INPUT, tutorial.id() + step.key(), util.select().position(local(process.input())), 150);
            int cycles = recipe == null ? 1 : recipe.cycles();
            PonderText.caption(scene, null, tutorial.translation(step.key()) + (recipe == null ? "" : ".action"), 120);
            for (int cycle = 1; cycle < cycles; cycle++) {
                var press = absolute(process, process.machine());
                scene.world().modifyBlockEntity(press.below(2), DepotBlockEntity.class, depot -> depot.setHeldItem(recipe.intermediate().copy()));
                scene.world().modifyBlockEntity(press, MechanicalPressBlockEntity.class, be -> {
                    var pressing = be.getPressingBehaviour();
                    pressing.start(PressingBehaviour.Mode.BELT);
                    pressing.runningTicks = 0;
                });
                scene.idle(60);
            }
            snapshot(scene, util, process, recipe, step.item(), true);
            var output = recipe == null ? icon(step.item()) : firstMaterial(recipe.products(), recipe.fluidProducts());
            if (!output.isEmpty()) scene.overlay().showControls(util.vector().centerOf(local(process.output())), Pointing.DOWN, 85).withItem(output);
            scene.overlay().showOutline(PonderPalette.OUTPUT, tutorial.id() + step.key() + "_product", util.select().position(local(process.output())), 120);
            if (recipe != null)
                PonderText.caption(scene, null, tutorial.translation(step.key()) + ".recipe", 110, recipe.inputDescription(), recipe.outputDescription());
            else scene.idle(35);
        }
        scene.idle(15);scene.markAsFinished();
    }

    private static CompoundTag savedTank(FluidStack stack,net.minecraft.core.HolderLookup.Provider lookup){var tank=new net.createplant.thermal.ThermalFluidTank(Math.max(16000,stack.getAmount()),s->{});tank.setFluid(stack.copy());return tank.writeToNBT(lookup,new CompoundTag());}
    private static BlockPos local(int[] coordinates) { return new BlockPos(coordinates[0], coordinates[1], coordinates[2]); }
    private static BlockPos absolute(PlantTutorialCatalog.Process process, int[] coordinates) { return local(coordinates).offset(process.offset(), 0, 0); }
    private static ItemStack icon(String item) { return item == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item))); }
    private static ItemStack bucket(FluidStack fluid) {
        var liquid = CEWorldFluids.bucket(fluid);
        return liquid.isEmpty() ? new ItemStack(fluid.getFluid().getBucket()) : liquid;
    }

    private static ItemStack firstMaterial(java.util.List<ItemStack> items, java.util.List<FluidStack> fluids) {
        if (!items.isEmpty()) return items.getFirst();
        return fluids.isEmpty() ? ItemStack.EMPTY : bucket(fluids.getFirst());
    }

    private static void displayStorage(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, FluidStack stack, Direction.Axis axis) {
        if (!stack.isEmpty() && net.createplant.transport.FluidHazards.of(stack).gas()) {
            if (net.createplant.thermal.TankGrade.GAS.accepts(stack)) scene.world().setBlock(pos, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS, axis), false);
            else {
                var block = net.createplant.transport.FluidHazards.of(stack).corrosion() == net.createplant.transport.FluidHazards.Corrosion.FLUORIDE ? CETransport.NICKEL_ALLOY_PIPE.get() : CETransport.STAINLESS_PIPE.get();
                var state = block.defaultBlockState();
                for (var side : Direction.values()) state = state.setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == axis);
                scene.world().setBlock(pos, state, false);return;
            }
        } else scene.world().restoreBlocks(util.select().position(pos));
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class, tank -> tank.getTankInventory().setFluid(stack.copy()));
    }

    private static void snapshot(CreateSceneBuilder scene, SceneBuildingUtil util, PlantTutorialCatalog.Process process, ManufacturingRecipe recipe, String displayedItem, boolean complete) {
        var at = absolute(process, process.machine());
        switch (process.kind()) {
            case "vessel", "centrifuge" -> vessel(scene, util, process, recipe, at, complete);
            case "catalytic" -> reactionTower(scene, util, recipe, at, complete);
            case "absorber" -> tower(scene, process, recipe, at, complete);
            case "mixing" -> {
                if (recipe != null) scene.world().modifyBlockEntity(at, BasinBlockEntity.class, be -> {
                    var inventory = be.getInputInventory();
                    for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
                    if (!complete) insert(inventory, recipe.items());
                    for (int slot = 0; slot < be.getOutputInventory().getSlots(); slot++) be.getOutputInventory().setStackInSlot(slot, ItemStack.EMPTY);
                    var tank = be.getTanks().getFirst().getCapability();
                    clear(tank);
                    var outputTank = be.getTanks().getSecond().getCapability();
                    clear(outputTank);
                    if (complete) for (var fluid : recipe.fluidProducts()) outputTank.fill(fluid, EXECUTE);
                    if (!complete) for (var fluid : recipe.fluids()) tank.fill(fluid, EXECUTE);
                    if (complete) insert(be.getOutputInventory(), recipe.products());
                });
                scene.world().modifyBlockEntity(at.above(2), MechanicalMixerBlockEntity.class, mixer -> {
                    mixer.running = !complete;mixer.runningTicks = complete ? 0 : 20;mixer.processingTicks = complete ? 0 : 200;
                });
                heat(scene, at.below(), recipe);
            }
            case "pressing" -> {
                if (recipe != null) {
                    scene.world().modifyBlockEntity(at.below(2), DepotBlockEntity.class, depot -> depot.setHeldItem((complete ? recipe.products() : recipe.items()).getFirst().copy()));
                    if (!complete) scene.world().modifyBlockEntity(at, MechanicalPressBlockEntity.class, press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
                }
            }
            case "deploying" -> {
                if (recipe != null) {
                    scene.world().modifyBlockEntity(at.below(2), DepotBlockEntity.class, depot -> depot.setHeldItem((complete ? recipe.products() : recipe.items()).getFirst().copy()));
                    scene.world().modifyBlockEntityNBT(util.select().position(at), com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity.class,
                        tag -> tag.put("HeldItem", recipe.items().getLast().saveOptional(scene.world().getHolderLookupProvider())));
                    scene.world().moveDeployer(at, complete ? -1 : 1, 25);
                }
            }
            case "crushing", "milling", "crafting" -> {
                if (recipe != null) scene.world().modifyBlockEntity(absolute(process, process.output()), DepotBlockEntity.class,
                    depot -> depot.setHeldItem(complete && !recipe.products().isEmpty() ? recipe.products().getFirst().copy() : ItemStack.EMPTY));
            }
            case "separator" -> {
                if(recipe!=null)scene.world().modifyBlockEntityNBT(util.select().position(at),net.createplant.block.entity.AmmoniaSeparatorBlockEntity.class,tag->{
                    var lookup=scene.world().getHolderLookupProvider();
                    for(String key:new String[]{"Feed","Liquid","Recycle"})tag.remove(key);
                    if(!complete)tag.put("Feed",savedTank(recipe.fluids().getFirst(),lookup));
                    else {tag.put("Liquid",savedTank(recipe.fluidProducts().getFirst(),lookup));tag.put("Recycle",savedTank(recipe.fluidProducts().get(1),lookup));}
                });
            }
            case "column" -> column(scene, util, process, recipe, at, complete);
            case "electrolyzer" -> electrolyzer(scene, recipe, at, complete);
            case "furnace" -> furnace(scene, recipe, at, complete);
            case "carbonization" -> {
                var wood = complete ? CECarbonization.CHARRED_WOOD.get().defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
                scene.world().setBlock(at, wood, false);
                scene.world().setBlock(at.east(2), complete ? wood : Blocks.OAK_PLANKS.defaultBlockState(), false);
                if (complete) scene.world().setBlock(at.west(), Blocks.AIR.defaultBlockState(), false);
                else scene.world().restoreBlocks(util.select().position(at.west()));
            }
            case "converter" -> scene.world().modifyBlockEntity(at, ConverterTrunnionBearingBlockEntity.class,
                be -> be.ponderVisual(new BlockPos(process.offset() + 5, 1, 6), Direction.NORTH, complete ? 60 : 0, !complete));
            case "compressor" -> scene.world().modifyBlockEntity(at.relative(Direction.EAST, 2), net.createplant.block.entity.ReciprocatingShaftBlockEntity.class, shaft -> shaft.bind(at));
            case "cooling", "heat_exchanger" -> {
                if(recipe!=null){
                    var positions=process.kind().equals("cooling") ? java.util.List.of(new int[]{5,3,1},new int[]{5,3,7})
                        : java.util.List.of(new int[]{1,1,5},new int[]{9,1,5},new int[]{1,3,5},new int[]{9,3,5});
                    for(int index=0;index<positions.size();index++){
                        int row=index/2;boolean output=index%2==1;
                        var stacks=output ? recipe.fluidProducts() : recipe.fluids();
                        var stack=output==complete && row<stacks.size() ? stacks.get(row) : FluidStack.EMPTY;
                        displayStorage(scene,util,absolute(process,positions.get(index)),stack,process.kind().equals("cooling") ? Direction.Axis.Z : Direction.Axis.X);
                    }
                }
            }
            case "cold_box" -> {
                if(recipe!=null)scene.world().modifyBlockEntityNBT(util.select().position(at),net.createplant.block.entity.CryogenicColdBoxBlockEntity.class,tag->{
                    var lookup=scene.world().getHolderLookupProvider();
                    for(int index=0;index<4;index++){
                        boolean output=index%2==1;int row=index/2;var stocks=output ? recipe.fluidProducts() : recipe.fluids();
                        tag.put(new String[]{"HeliumIn","HeliumOut","WaterIn","WaterOut"}[index],savedTank(output==complete ? stocks.get(row) : FluidStack.EMPTY,lookup));
                    }
                    tag.putBoolean("Formed",true);tag.putString("Status",complete ? "feed" : "running");
                });
            }
            case "heating" -> { }
            default -> throw new IllegalArgumentException("Unknown manufacturing equipment " + process.kind());
        }
    }

    private static void insert(net.neoforged.neoforge.items.IItemHandler inventory, java.util.List<ItemStack> items) {
        for (var item : items) {
            var remainder = item.copy();
            for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++)
                remainder = inventory.insertItem(slot, remainder, false);
        }
    }

    private static void clear(net.neoforged.neoforge.fluids.capability.IFluidHandler tanks) {
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            var stored = tanks.getFluidInTank(tank);
            if (!stored.isEmpty()) tanks.drain(stored.copyWithAmount(Integer.MAX_VALUE), EXECUTE);
        }
    }

    private static void heat(CreateSceneBuilder scene, BlockPos position, ManufacturingRecipe recipe) {
        if (recipe == null) return;
        var heat = switch (recipe.heat()) { case HEATED -> BlazeBurnerBlock.HeatLevel.KINDLED;case SUPERHEATED -> BlazeBurnerBlock.HeatLevel.SEETHING;default -> BlazeBurnerBlock.HeatLevel.NONE; };
        scene.world().modifyBlock(position, state -> state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) ? state.setValue(BlazeBurnerBlock.HEAT_LEVEL, heat) : state, false);
    }

    private static void vessel(CreateSceneBuilder scene, SceneBuildingUtil util, PlantTutorialCatalog.Process process, ManufacturingRecipe recipe, BlockPos owner, boolean complete) {
        if (process.kind().equals("centrifuge")) {
            var fixed = net.createplant.chemistry.ProcessRecipe.all().stream().filter(r -> process.recipe().equals("process:" + r.id())).findFirst().orElse(null);
            if (fixed != null) scene.world().modifyBlockEntity(owner, net.createplant.block.entity.GasCentrifugeBlockEntity.class, be -> be.preview(fixed, complete));
            return;
        }
        int layers = 3;
        {
            BlockPos stirrer = owner.offset(1, layers, 1), cog = stirrer.north();
            var floor = util.select().fromTo(owner.below(), owner.offset(2, -1, 2));
            scene.world().setBlocks(floor, com.simibubi.create.AllBlocks.BLAZE_BURNER.getDefaultState(), false);
            scene.world().setBlock(stirrer, CEReactor.STIRRER.get().defaultBlockState(), false);
            scene.world().setBlock(cog, com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState(), false);
            scene.world().setBlock(cog.above(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.DOWN), false);
            scene.world().setKineticSpeed(util.select().position(stirrer).add(util.select().position(cog.above())), 64);
            scene.world().setKineticSpeed(util.select().position(cog), -64);
            heat(scene, owner.below(), recipe);
        }
        for (int layer = 0; layer < layers; layer++) {
            var part = owner.above(layer);
            scene.world().modifyBlockEntity(part, LiddedVesselBlockEntity.class, be -> {
                var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new CompoundTag(), lookup);
                tag.remove("Internal");tag.putBoolean("Stirrer", true);
                tag.putBoolean("Windows", true);tag.putBoolean("Formed", true);tag.putBoolean("Running", !complete);tag.putInt("Sections", LiddedVesselBlockEntity.IMPELLER_LAYERS);
                tag.putFloat("Progress", complete ? 0 : 40);tag.putInt("CycleTicks", 100);tag.putInt("Scale", 1);tag.putString("Status", complete ? "idle" : "running");
                if (part.equals(owner)) {
                    for (int bank = 0; bank < ReactorMachineBlockEntity.PROCESS_TANKS; bank++) tag.remove("Tank" + bank);
                    var charge = new ItemStackHandler(13);
                    if (recipe != null) {
                        var items = complete ? recipe.products() : recipe.items();
                        int first = complete ? 9 : 0;
                        for (int slot = 0; slot < items.size(); slot++) charge.setStackInSlot(first + slot, items.get(slot).copy());
                        var fluids = complete ? recipe.fluidProducts() : recipe.fluids();
                        for (int bank = 0; bank < fluids.size(); bank++) tag.put("Tank" + (complete ? 2 + bank : bank == 2 ? ReactorMachineBlockEntity.THIRD_FEED : bank), fluids.get(bank).save(lookup));
                        tag.putString("Batch", process.recipe().startsWith("process:") ? process.recipe().substring(8) : process.recipe());
                    }
                    tag.put("Charge", charge.serializeNBT(lookup));
                }
                be.readClient(tag, lookup);
            });
        }
        heat(scene, owner.below(), recipe);
    }

    /** A four-layer reaction tower with a tray and the recipe's catalyst in every layer, one batch of feed waiting or of products made, over four corner burners. */
    private static void reactionTower(CreateSceneBuilder scene, SceneBuildingUtil util, ManufacturingRecipe recipe, BlockPos owner, boolean complete) {
        BlockPos controller = owner.offset(-1, 0, -1);
        int layers = 4;
        for (int x = 0; x < 3; x++) for (int y = 0; y < layers; y++) for (int z = 0; z < 3; z++) {
            BlockPos part = controller.offset(x, y, z);
            scene.world().modifyBlockEntityNBT(util.select().position(part), FluidTankBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 3);tag.putInt("Height", layers); }
                else tag.put("Controller", NbtUtils.writeBlockPos(controller));
            });
        }
        var lookup = scene.world().getHolderLookupProvider();
        for (int y = 0; y < layers; y++) scene.world().modifyBlockEntityNBT(util.select().position(owner.above(y)), ReactionTowerBlockEntity.class, tag -> {
            tag.putBoolean("Basket", true);
            if (recipe != null && !recipe.catalyst().isEmpty()) tag.put("Catalyst", recipe.catalyst().copyWithCount(1).save(lookup));
        });
        scene.world().modifyBlockEntityNBT(util.select().position(owner), ReactionTowerBlockEntity.class, tag -> {
            tag.putString("Front", "north");tag.putString("TowerStatus", complete ? "idle" : "running");
            for (int slot = 0; slot < 4; slot++) tag.remove("TowerTank" + slot);
            if (recipe == null) return;
            var fluids = complete ? recipe.fluidProducts() : recipe.fluids();
            for (int slot = 0; slot < fluids.size() && slot < 2; slot++) tag.put("TowerTank" + (complete ? 2 + slot : slot), fluids.get(slot).save(lookup));
        });
        for (int x = -1; x <= 1; x += 2) for (int z = -1; z <= 1; z += 2) heat(scene, owner.offset(x, -1, z), recipe);
    }

    /** The absorption tower's bottom storey with one batch of feed waiting or of products made. */
    private static void tower(CreateSceneBuilder scene, PlantTutorialCatalog.Process process, ManufacturingRecipe recipe, BlockPos bottom, boolean complete) {
        scene.world().modifyBlockEntity(bottom, AbsorptionTowerBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new CompoundTag(), lookup);
            tag.putBoolean("Formed", true);tag.putBoolean("Running", !complete);tag.putInt("Sections", 6);
            tag.putFloat("Progress", complete ? 0 : 40);tag.putInt("CycleTicks", 100);tag.putInt("Scale", 1);tag.putString("Status", complete ? "idle" : "running");
            for (int bank = 0; bank < ReactorMachineBlockEntity.PROCESS_TANKS; bank++) tag.remove("Tank" + bank);
            if (recipe != null) {
                var fluids = complete ? recipe.fluidProducts() : recipe.fluids();
                for (int bank = 0; bank < fluids.size(); bank++) tag.put("Tank" + (complete ? 2 + bank : bank), fluids.get(bank).save(lookup));
                tag.putString("Batch", process.recipe().substring("process:".length()));
            }
            be.readClient(tag, lookup);
        });
    }

    private static void column(CreateSceneBuilder scene, SceneBuildingUtil util, PlantTutorialCatalog.Process process, ManufacturingRecipe recipe, BlockPos at, boolean complete) {
        boolean air = process.recipe().equals("operation:air_separation");
        int height = process.height() - 2;
        for (int layer = 0; layer < height; layer++) {
            int index = layer;
            int slot = air ? net.createplant.chemistry.DistillationProfile.outlet(index)
                : net.createplant.chemistry.DistillationProfile.fractionSlot(index,height,recipe==null ? 0 : recipe.fluidProducts().size());
            int active = recipe != null && slot <= recipe.fluidProducts().size() ? slot : 0;
            for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
                int cut=x==0 && z==0 ? active : 0;
                scene.world().modifyBlock(at.offset(x,layer,z), state -> state.setValue(FractionatingColumnBlock.CUT, cut), false);
            }
        }
        scene.world().modifyBlockEntity(at, FractionatingColumnBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new CompoundTag(), lookup);
            tag.putBoolean("Valid", true);tag.putInt("Width", 2);tag.putInt("Height", height);tag.putInt("Capacity", height*4000);
            tag.putString("ActiveRecipe", air ? "air" : process.recipe().substring(8));tag.putString("Status", complete ? "waiting" : "running");
            int[] stocks = new int[6];
            if (complete && recipe != null) for (int slot = 0; slot < recipe.fluidProducts().size(); slot++) stocks[slot + 1] = recipe.fluidProducts().get(slot).getAmount();
            if (!complete && recipe != null) stocks[0] = recipe.fluids().getFirst().getAmount();tag.putIntArray("Stocks", stocks);be.readClient(tag, lookup);
        });
    }

    private static void electrolyzer(CreateSceneBuilder scene, ManufacturingRecipe recipe, BlockPos at, boolean complete) {
        if (recipe == null) return;
        scene.world().modifyBlockEntity(at, net.createplant.block.entity.ElectrolysisControllerBlockEntity.class, be -> {
            be.validate();
            if (be.cells().isEmpty()) return;
            for (var cell : be.cells()) for (var tank : cell.tanks) tank.setFluid(FluidStack.EMPTY);
            be.heads().getFirst().electrodes.setStackInSlot(0, CEElectrolysis.ELECTRODE_PLATE.toStack());
            for (var fluid : complete ? recipe.fluidProducts() : recipe.fluids()) {
                int bank = !complete ? 0 : fluid.is(CEFluids.HYDROGEN_GAS.get()) || fluid.is(CEIsotopeGases.DEUTERIUM_GAS.get()) ? 1
                    : fluid.is(CEChlorAlkali.CAUSTIC_SODA.get()) ? 3 : 2;
                be.cells().getFirst().tanks.get(bank).setFluid(fluid.copy());
            }
            be.ponderVisual(complete);
        });
    }

    private static void furnace(CreateSceneBuilder scene, ManufacturingRecipe recipe, BlockPos at, boolean complete) {
        if (recipe == null) return;
        scene.world().modifyBlockEntity(at, ElectrolysisFurnaceBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new CompoundTag(), lookup);var items = new ItemStackHandler(3);
            if (complete) items.setStackInSlot(2, recipe.products().getFirst().copy());
            else { items.setStackInSlot(0, recipe.items().getFirst().copy());items.setStackInSlot(1, recipe.items().getLast().copy()); }
            tag.put("Inventory", items.serializeNBT(lookup));tag.putBoolean("Formed", true);tag.putBoolean("Lit", true);tag.putDouble("Watts",20000);tag.putString("Status", "running");be.readClient(tag, lookup);
        });
    }
}
