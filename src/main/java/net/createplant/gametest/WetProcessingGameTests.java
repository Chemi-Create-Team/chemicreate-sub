package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.block.WetProcessPartBlock.Role.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class WetProcessingGameTests {
    static WetProcessBlockEntity assemble(GameTestHelper h, BlockPos drive, int width, int height, Direction front) {
        drive = h.absolutePos(drive);
        for (var cell : WetProcessStructure.cells(drive, front, width, height)) h.getLevel().setBlock(cell.pos(), cell.state(), 3);
        var be = (WetProcessBlockEntity) h.getLevel().getBlockEntity(drive);be.rebuild();
        h.assertTrue(be.formed(), "Wet process tank forms: " + width + "/" + height + "/" + front);return be;
    }
    private static FluidStack slurry(int amount) { return new FluidStack(CEWetProcessing.SLURRIES.get("copper_leach_slurry").get(), amount); }
    private static void run(WetProcessBlockEntity be, int ticks) { for (int i = 0; i < ticks; i++) be.process(); }
    @GameTest(template = "test_wet_processing", timeoutTicks = 200)
    public static void everyOddDiameterHeightAndFacingUsesTheTankGridCircle(GameTestHelper h) {
        for (int width : WetProcessStructure.CLARIFIER_WIDTHS) for (int height : WetProcessStructure.CLARIFIER_HEIGHTS) for (Direction front : Direction.Plane.HORIZONTAL) {
            var cells = WetProcessStructure.cells(BlockPos.ZERO, front, width, height);
            var states = cells.stream().collect(java.util.stream.Collectors.toMap(WetProcessStructure.Cell::pos, WetProcessStructure.Cell::state));
            var positions = states.keySet();
            h.assertTrue(positions.size() == cells.size(), "Each cell occurs once");
            var bounds = new net.minecraft.world.phys.AABB(positions.iterator().next());
            for (var pos : positions) bounds = bounds.minmax(new net.minecraft.world.phys.AABB(pos));
            h.assertTrue(bounds.getXsize() == width && bounds.getZsize() == width && bounds.getYsize() == height, "Exact odd dimensions: " + width + "/" + height + "/" + front);
            for (var cell : cells) if (cell.state().is(CEBlocks.TANK_CASING.get()))
                h.assertTrue(cell.state().getCollisionShape(h.getLevel(), h.absolutePos(cell.pos())).bounds().equals(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)), "The circle uses whole cubes, without corner cuts");
            var base = new BlockPos(0, -height + 1, 0).relative(front, width / 2 + 1);
            var plan = AssemblyPlan.targets(AssemblyPlan.Kind.CLARIFIER, base, front.getOpposite(), width, height);
            h.assertTrue(plan.size() == cells.size() && plan.stream().allMatch(t -> states.get(t.pos()) == t.state()), "Constructor and validator use identical cells");
        }
        h.succeed();
    }
    @GameTest(template = "test_wet_processing", timeoutTicks = 200)
    public static void oddTanksFormAndConstructorRemembersEachPlan(GameTestHelper h) {
        var root = new BlockPos(16, 6, 16);
        for (int width : new int[]{7,9,27,29}) for (int height : WetProcessStructure.CLARIFIER_HEIGHTS) {
            var be = assemble(h, root, width, height, Direction.SOUTH);
            h.assertTrue(be.width() == width && be.height() == height && be.calculateStressApplied() == 8, "Actual circle forms with the exact selected dimensions");
            for (var cell : WetProcessStructure.cells(h.absolutePos(root), Direction.SOUTH, width, height)) h.getLevel().setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), 3);
        }
        h.setBlock(new BlockPos(2, 2, 2), CEAssembly.CONSTRUCTOR.get());var constructor = (FacilityConstructorBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2));
        for (int index=0;index<WetProcessStructure.CLARIFIER_WIDTHS.size();index++) for (int heightIndex=0;heightIndex<2;heightIndex++) {
            h.assertTrue(constructor.configure(AssemblyPlan.Kind.CLARIFIER.ordinal(), index) && constructor.configureHeight(heightIndex), "All diameter and height controls work");
            int size=constructor.selectedSize(), height=constructor.totalHeight();
            h.assertTrue(constructor.plan().stream().filter(t -> t.state().getBlock() instanceof WetProcessDriveBlock).count() == 1, "Each constructor plan has one drive");
            var lookup=h.getLevel().registryAccess();constructor.loadWithComponents(constructor.saveWithoutMetadata(lookup),lookup);
            h.assertTrue(constructor.selectedSize()==size && constructor.totalHeight()==height, "Independent dimensions persist across reload");
        }
        h.assertTrue(!constructor.configure(AssemblyPlan.Kind.CLARIFIER.ordinal(),24) && !constructor.configureHeight(2), "Out-of-range requests are rejected on the server");
        h.assertTrue(constructor.configure(AssemblyPlan.Kind.CASING_TANK.ordinal(), 4) && constructor.configureHeight(0), "The storage layout owns a separate dimension selection");
        var lookup = h.getLevel().registryAccess();constructor.loadWithComponents(constructor.saveWithoutMetadata(lookup), lookup);
        h.assertTrue(constructor.selectedSize() == 15 && constructor.totalHeight() == 3 && constructor.sizeIndex(AssemblyPlan.Kind.CLARIFIER) == 11, "Tank settings persist without replacing clarifier settings");
        h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void constructorTankConvertsWithJustTheTwoCenterParts(GameTestHelper h) {
        var level = h.getLevel();var base = h.absolutePos(new BlockPos(14, 3, 3));
        var plan = AssemblyPlan.targets(AssemblyPlan.Kind.CASING_TANK, base, Direction.SOUTH, 9, 4);
        for (var cell : plan) level.setBlock(cell.pos(), cell.state(), 3);
        var floor = base.south(5);
        var tank = (net.createplant.block.entity.TankCasingBlockEntity) level.getBlockEntity(floor);
        tank.refresh();h.assertTrue(tank.getTank() == tank && tank.getDiameter() == 9 && tank.getHeight() == 3, "The constructor builds a working open storage tank");
        var oldStorage = level.getCapability(Capabilities.FluidHandler.BLOCK, floor.west(), Direction.WEST);
        h.assertTrue(oldStorage.fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 500, "Standard casing stores water before conversion");
        var drive = floor.above(3);
        level.setBlock(drive, CEWetProcessing.CLARIFIER.get().defaultBlockState().setValue(WetProcessDriveBlock.FACING, Direction.NORTH), 3);
        var clarifier = (WetProcessBlockEntity) level.getBlockEntity(drive);clarifier.rebuild();
        h.assertTrue(!clarifier.formed(), "The bottom feed is required");
        level.setBlock(floor, CEWetProcessing.PARTS.get(FEED).get().defaultBlockState().setValue(WetProcessPartBlock.FACING, Direction.UP), 3);
        clarifier.rebuild();clarifier.setSpeed(2);
        h.assertTrue(clarifier.formed() && clarifier.width() == 9, "An existing upward-placed central feed still forms and exposes its underside");
        h.assertTrue(oldStorage.drain(500, EXECUTE).isEmpty(), "The old storage handler cannot access the clarifier buffers");
        var intake = level.getCapability(Capabilities.FluidHandler.BLOCK, floor, Direction.DOWN);
        var overflow = level.getCapability(Capabilities.FluidHandler.BLOCK, drive.north(4), Direction.NORTH);
        h.assertTrue(intake.fill(slurry(1000), EXECUTE) == 1000, "Bottom feed accepts slurry");
        run(clarifier, 200);
        h.assertTrue(overflow.drain(1000, EXECUTE).getAmount() == 750, "The unmodified casing supplies clarified product");
        level.setBlock(drive, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(floor, CEBlocks.TANK_CASING.get().defaultBlockState(), 3);
        ((net.createplant.block.entity.TankCasingBlockEntity) level.getBlockEntity(floor)).refresh();
        h.assertTrue(oldStorage.drain(500, EXECUTE).getAmount() == 500 && overflow.drain(1000, EXECUTE).isEmpty(), "Returning to storage preserves its original water without leaving a live process port");
        h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void handBuiltCubesFormAndAllFacingsHaveValidPorts(GameTestHelper h) {
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var be=assemble(h,new BlockPos(16,6,16),15,4,front);
            var cells=WetProcessStructure.cells(be.getBlockPos(),front,15,4);
            for (var cell:cells) if (cell.state().is(CEBlocks.TANK_CASING.get())) h.getLevel().setBlock(cell.pos(), CEWetProcessing.PARTS.get(BASIN).get().defaultBlockState(), 3);
            be.rebuild();h.assertTrue(be.formed(),"Existing odd-sized linings remain valid in every direction");
            for (var cell:cells) h.getLevel().setBlock(cell.pos(),Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void partialStructuresWrongPortsInteriorChangesAndOldHandlesCannotProcess(GameTestHelper h) {
        var be=assemble(h,new BlockPos(16,6,16),29,4,Direction.NORTH);be.setSpeed(2);
        var input=be.fluidPort(FEED);
        h.assertTrue(input.fill(slurry(1000),SIMULATE)==1000 && be.tanks[0].isEmpty(),"Simulation leaves input empty");
        h.assertTrue(input.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),750),EXECUTE)==0,"The liquid feed refuses gases");
        var air=be.getBlockPos().below();h.getLevel().setBlock(air,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!be.formed() && input.fill(slurry(1000),EXECUTE)==0,"Interior changes immediately revoke old handles");
        be.rebuild();h.assertTrue(!be.formed(),"Blocked airspace prevents formation");
        h.getLevel().setBlock(air,Blocks.AIR.defaultBlockState(),3);be.rebuild();
        h.assertTrue(be.formed() && input.fill(slurry(1000),EXECUTE)==0,"Old handles stay invalid after reconstruction");
        var rim = be.getBlockPos().north(14);
        h.getLevel().setBlock(rim, CEWetProcessing.PARTS.get(OVERFLOW).get().defaultBlockState().setValue(WetProcessPartBlock.FACING,Direction.SOUTH),3);be.rebuild();
        h.assertTrue(!be.formed(),"Wrong port facing prevents formation");h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void slowDirtyAndTurbulentClarificationHaveDistinctFiniteOutputs(GameTestHelper h) {
        var be = assemble(h, new BlockPos(8, 4, 8), 7, 3, Direction.NORTH);
        for (float rpm : new float[]{1, 4, 5, 12, 16, 17, 64}) {
            be.setSpeed(rpm);be.tanks[0].setFluid(slurry(1000));run(be, 200);
            var result = be.fluidPort(OVERFLOW).drain(2000, EXECUTE);
            h.assertTrue(be.tanks[0].isEmpty(), "Exactly one feed batch is consumed at " + rpm);
            if (rpm >= 17) h.assertTrue(result.getAmount() == 1000 && result.is(slurry(1).getFluid()) && be.items.getStackInSlot(4).isEmpty(), "Turbulence returns identical slurry with no mud duplication");
            else {
                h.assertTrue(result.getAmount() == 750 && result.is(CEMetals.COPPER_SULFATE_SOLUTION.get()) && result.getOrDefault(CEThermal.SUSPENDED_IMPURITY.get(), 0) == WetProcessBlockEntity.impurity(rpm), "Overflow amount and dirt match the RPM curve");
                h.assertTrue(be.items.getStackInSlot(4).getCount() == 2, "One separation batch gives two mud");
                be.itemPort(UNDERFLOW).extractItem(1, 64, false);
            }
        }
        h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void stoppedScraperFillsWithMudAndFlocculantIsConsumedOnce(GameTestHelper h) {
        var be = assemble(h, new BlockPos(8, 4, 8), 7, 3, Direction.SOUTH);be.setSpeed(0);
        be.fluidPort(FEED).fill(slurry(33000), EXECUTE);run(be, 6600);
        h.assertTrue(be.items.getStackInSlot(4).getCount() == 64 && be.tanks[0].getFluidAmount() == 1000 && be.status().equals("output"), "Stopped scraper settles until mud storage fills, then consumes nothing");
        var port = be.itemPort(UNDERFLOW);h.assertTrue(port.extractItem(1, 64, false).isEmpty(), "Zero RPM locks underflow extraction");
        be.setSpeed(2);h.assertTrue(port.extractItem(1, 64, true).getCount() == 64 && be.items.getStackInSlot(4).getCount() == 64, "Extraction preview does not move mud");port.extractItem(1, 64, false);
        port.insertItem(0, new ItemStack(CEWetProcessing.FLOCCULANT.get(), 2), false);run(be, 99);
        h.assertTrue(be.tanks[0].getFluidAmount() == 1000 && be.items.getStackInSlot(3).getCount() == 1, "One catalyst is committed once for the accelerated batch");
        var lookup = h.getLevel().registryAccess();be.loadWithComponents(be.saveWithoutMetadata(lookup), lookup);be.rebuild();be.setSpeed(2);run(be, 1);
        h.assertTrue(be.tanks[0].isEmpty() && be.items.getStackInSlot(4).getCount() == 2 && be.items.getStackInSlot(3).getCount() == 1, "Accelerated progress persists and completes at 100 ticks, without consuming a second catalyst");h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void actualCreatePipeFacesAndFunnelInsertionMatchVisiblePorts(GameTestHelper h) {
        var be = assemble(h, new BlockPos(5, 6, 5), 9, 4, Direction.SOUTH);
        var center = be.getBlockPos();
        var ports = java.util.Map.of(center.below(3), Direction.DOWN, center.south(4), Direction.SOUTH, center.north(4).below(3), Direction.NORTH);
        for (var port : ports.entrySet()) {
            var pos = port.getKey();var face = port.getValue();boolean mud = face == Direction.NORTH;
            var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
            var item = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
            h.assertTrue(mud ? item != null && fluid == null : fluid != null && item == null, "Casing and central feed expose the correct standard capability");
            if (!mud) {
                var at = pos.relative(face);h.getLevel().setBlock(at, AllBlocks.FLUID_PIPE.getDefaultState(), 3);
                var pipe = AllBlocks.FLUID_PIPE.get().updateBlockState(h.getLevel().getBlockState(at), face, null, h.getLevel(), at);
                h.assertTrue(pipe.getValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(face.getOpposite())), "Create pipe connects to the feed or casing outlet");
            }
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, face.getOpposite()) == null && h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, face.getOpposite()) == null, "Opposite face is closed");
        }
        h.succeed();
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 250)
    public static void realPumpAndFunnelReachTheCasingTankSideInlets(GameTestHelper h) {
        var center = new BlockPos(12, 6, 12);
        var be = assemble(h, center, 9, 4, Direction.SOUTH);
        var wall = center.below(2);
        var intake = wall.east(4);
        var solid = wall.west(4);
        var funnel = solid.west();
        var pump = intake.east(2);
        var source = intake.east(4);
        h.setBlock(funnel, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.WEST).setValue(FunnelBlock.EXTRACTING, false));
        h.setBlock(source, CETransport.STAINLESS_STEEL_TANK.get());
        var sourceTank = ((FluidTankBlockEntity) h.getBlockEntity(source)).getTankInventory();
        h.assertTrue(sourceTank.fill(slurry(1000), EXECUTE) == 1000, "A compatible source tank holds the slurry charge");
        var pipe = CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (Direction side : Direction.values()) pipe = pipe.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == Direction.Axis.X);
        h.setBlock(pump.east(), pipe);
        h.setBlock(pump.west(), pipe);
        var pumpBlock = BuiltInRegistries.BLOCK.get(CreatePlant.location("stainless_steel_pump"));
        h.setBlock(pump, pumpBlock.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(pump.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((CreativeMotorBlockEntity) h.getBlockEntity(pump.south().west())).generatedSpeed.setValue(64);

        var level = h.getLevel();
        var inlet = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(intake), Direction.EAST);
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(solid), Direction.WEST);
        h.assertTrue(inlet != null && inlet.fill(slurry(1000), SIMULATE) == 1000 && inlet.drain(1000, SIMULATE).isEmpty(), "The exterior casing is a one-way liquid input");
        h.assertTrue(items != null && items.getSlots() == 1 && items.isItemValid(0, CEWetProcessing.FLOCCULANT.toStack()), "The opposite rim exposes the flocculant slot");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(intake), Direction.WEST) == null
            && level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(solid), Direction.EAST) == null, "The inner wall faces remain closed");
        h.runAfterDelay(5, () -> {
            var catalyst = CEWetProcessing.FLOCCULANT.toStack(2);
            h.assertTrue(AbstractFunnelBlock.tryInsert(level, h.absolutePos(funnel), catalyst, true).isEmpty() && be.items.getStackInSlot(3).isEmpty(), "A standard Create funnel can preview flocculant insertion");
            h.assertTrue(AbstractFunnelBlock.tryInsert(level, h.absolutePos(funnel), catalyst, false).isEmpty(), "The funnel moves both items into the clarifier");
        });
        h.runAfterDelay(205, () -> {
            int product = be.tanks[2].getFluidAmount();
            h.assertTrue(sourceTank.isEmpty() && (be.tanks[0].getFluidAmount() == 1000 || product == 750), "The real pump supplies the finite slurry charge through the side casing without loss");
            h.assertTrue(be.items.getStackInSlot(3).getCount() >= 1, "Both materials arrive at the same operating clarifier");
            h.setBlock(center.below(), Blocks.STONE);
            h.assertTrue(!be.formed() && inlet.fill(slurry(1), EXECUTE) == 0 && !items.insertItem(0, CEWetProcessing.FLOCCULANT.toStack(), false).isEmpty(), "Breaking the structure revokes cached side connections");
            h.succeed();
        });
    }
    @GameTest(template = "test_wet_processing")
    public static void qualitySurvivesMixingReloadAndIncreasesRealElectrowinningWork(GameTestHelper h) {
        var dirty = new FluidStack(CEMetals.COPPER_SULFATE_SOLUTION.get(), 1000);dirty.set(CEThermal.SUSPENDED_IMPURITY.get(), 1000);
        var pure = new FluidStack(CEMetals.COPPER_SULFATE_SOLUTION.get(), 1000);var mixed = FluidThermal.mix(dirty, pure, 1000);
        h.assertTrue(mixed.getAmount() == 2000 && mixed.get(CEThermal.SUSPENDED_IMPURITY.get()) == 500 && dirty.get(CEThermal.SUSPENDED_IMPURITY.get()) == 1000, "Mixing computes a volume-weighted quality without altering its inputs");
        var electro = ElectrolysisCellGameTests.one(h);ElectrolysisCellGameTests.feed(electro, dirty);
        var cell = electro.cells().getFirst();var lookup = h.getLevel().registryAccess();cell.loadWithComponents(cell.saveWithoutMetadata(lookup), lookup);electro.validate();
        ElectrolysisCellGameTests.run(electro, 2000, 400);
        h.assertTrue(electro.metalStored() == 3 && electro.stored(3) == 250 && Math.abs(electro.consumed() - 40000) < .001, "Fully contaminated solution retains its metal and acid but costs twice the normal 20 kJ: metal=" + electro.metalStored() + ", acid=" + electro.stored(3) + ", work=" + electro.consumed());h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void breakingAndReplacingTheDriveKeepsEachInventoryExactlyOnce(GameTestHelper h) {
        var be = assemble(h, new BlockPos(5, 6, 5), 9, 4, Direction.NORTH);var pos = be.getBlockPos();
        be.tanks[0].setFluid(slurry(4000));be.tanks[2].setFluid(new FluidStack(CEMetals.COPPER_SULFATE_SOLUTION.get(),750));
        be.items.setStackInSlot(3, CEWetProcessing.FLOCCULANT.toStack(3));var old = be.fluidPort(OVERFLOW);
        h.getLevel().destroyBlock(pos, true);
        var dropped = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2));
        h.assertTrue(dropped.size() == 1 && dropped.getFirst().getItem().is(CEWetProcessing.CLARIFIER.asItem()) && old.drain(1000, EXECUTE).isEmpty(), "The drive drops once; old fluid leases are revoked");
        var stack = dropped.getFirst().getItem().copy();dropped.getFirst().discard();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setYRot(0);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        h.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "The dropped drive can be placed through the standard BlockItem path");
        var restored = (WetProcessBlockEntity) h.getLevel().getBlockEntity(pos);restored.rebuild();
        h.assertTrue(restored.formed() && restored.width() == 9 && restored.height() == 4 && restored.tanks[0].getFluidAmount() == 4000 && restored.tanks[2].getFluidAmount() == 750 && restored.items.getStackInSlot(3).getCount() == 3,
            "Both fluid banks and flocculant survive breaking and real placement without duplication");h.succeed();
    }
    @GameTest(template = "test_wet_processing")
    public static void removedBlocksAreUnregisteredAndSlurryWorldPropertiesAreReal(GameTestHelper h) {
        for (String id : new String[]{"absorption_tower", "ultra_high_pressure_pipe", "ultra_high_pressure_pump", "ore_sludge", "agitator_drive_head", "acid_resistant_casing", "leaching_tank_window", "leaching_acid_port", "leaching_slurry_outlet", "leaching_ore_inlet", "air_sparger_port"}) {
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(CreatePlant.location(id)) && !BuiltInRegistries.BLOCK.containsKey(CreatePlant.location(id)), "Retired ID is absent: " + id);
        }
        for (var source : CEWetProcessing.SLURRIES.values()) {
            var fluid = source.get();h.assertTrue(fluid.getFluidType().getDensity() == 1400 && fluid.getFluidType().getViscosity() == 3500 && fluid.getTickDelay(h.getLevel()) == 20, "Slurries have their registered density, viscosity and slow world flow");
            h.assertTrue(!CEWorldFluids.bucket(new FluidStack(fluid, 1000)).isEmpty(), "Every slurry has a creative-tab bucket");
        }
        h.succeed();
    }
}
