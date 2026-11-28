package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.ElectrolysisCellBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.registry.*;
import net.createplant.registry.CESharedChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ElectrolysisCellGameTests {
    static ElectrolysisControllerBlockEntity assemble(ServerLevel level, BlockPos minimum, int length, Direction facing) {
        var along = facing.getAxis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int n = 0; n < length; n++) {
            level.setBlock(minimum.relative(along, n), CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, facing.getAxis()), 3);
            level.setBlock(minimum.relative(along, n).above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, facing.getAxis()), 3);
        }
        var panel = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? minimum.relative(facing) : minimum.relative(along, length);
        for (int y = 0; y < 2; y++)
            level.setBlock(panel.above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, facing), 3);
        var owner = (ElectrolysisControllerBlockEntity) level.getBlockEntity(panel);
        owner.validate();
        return owner;
    }

    static ElectrolysisControllerBlockEntity one(GameTestHelper h) {
        var owner = assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 1, Direction.NORTH);
        owner.heads().getFirst().electrodes.insertItem(0, new ItemStack(CEElectrolysis.ELECTRODE_PLATE.get()), false);
        owner.setRunning(true);lower(owner);
        return owner;
    }

    /** Moves the heads until each has finished the stroke the controller asked for. */
    static void lower(ElectrolysisControllerBlockEntity owner) { for (var head : owner.heads()) turn(head, 17); }
    static void turn(ElectrodeHeadBlockEntity head, int ticks) { for (int n = 0; n < ticks; n++) head.move(); }
    static void load(ElectrolysisControllerBlockEntity owner, int plates) {
        for (var head : owner.heads()) { int amount = Math.min(16, plates);head.electrodes.insertItem(0, new ItemStack(CEElectrolysis.ELECTRODE_PLATE.get(), amount), false);plates -= amount; }
    }

    static int feed(ElectrolysisControllerBlockEntity owner, FluidStack fluid) { return owner.cells().getFirst().port(Direction.DOWN).fill(fluid, EXECUTE); }
    static void run(ElectrolysisControllerBlockEntity owner, int ticks, double volts) {
        for (int n = 0; n < ticks; n++) { owner.supply(owner.getLevel().getGameTime(), owner.resistance(), volts * volts);owner.step(); }
    }

    @GameTest(template = "test_petrochemical")
    public static void waterUsesFinitePowerAndSeparatesBothGasPorts(GameTestHelper h) {
        var owner = one(h);
        h.assertTrue(feed(owner, new FluidStack(Fluids.WATER, 300)) == 300, "Input accepts real water");
        h.assertTrue(owner.resistance() == 160, "One normal plate draws 1 kW at 400 V");
        run(owner, 100, 400);
        h.assertTrue(owner.stored(0) == 0 && owner.stored(1) == 200 && owner.stored(2) == 100 && Math.abs(owner.consumed() - 5000) < .001,
            "100 powered ticks conserve the 300:200:100 fluid ledger and 5000 J");
        var cell = (ElectrolysisCellBlockEntity) h.getLevel().getBlockEntity(owner.structure().minimum());
        var cathode = cell.port(Direction.WEST);var anode = cell.port(Direction.EAST);
        h.assertTrue(cathode.drain(50, SIMULATE).getAmount() == 50 && owner.stored(1) == 200, "Preview is read only");
        h.assertTrue(cathode.drain(new FluidStack(CEFluids.OXYGEN_GAS.get(), 10), EXECUTE).isEmpty(), "Gas identities remain separate");
        h.assertTrue(cathode.drain(100, EXECUTE).is(CEFluids.HYDROGEN_GAS.get()) && anode.drain(100, EXECUTE).is(CEFluids.OXYGEN_GAS.get()), "Exposed top sides export the correct gas");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void sulfurousAcidSplitsIntoHydrogenAndSulfuricAcidOnLessPower(GameTestHelper h) {
        var owner = one(h);
        h.assertTrue(feed(owner, new FluidStack(CESharedChemicals.SULFUROUS_ACID.get(), 200)) == 200, "Input accepts sulfurous acid");
        run(owner, 100, 400);
        h.assertTrue(owner.stored(0) == 0 && owner.stored(1) == 100 && owner.stored(2) == 0 && owner.stored(3) == 100,
            "100 cycles turn 200 mB sulfurous acid into 100 mB hydrogen and 100 mB sulfuric acid, with no anode gas: "
                + owner.stored(0) + " " + owner.stored(1) + " " + owner.stored(2) + " " + owner.stored(3));
        var cell = (ElectrolysisCellBlockEntity) h.getLevel().getBlockEntity(owner.structure().minimum());
        h.assertTrue(cell.port(Direction.WEST).drain(100, SIMULATE).is(CEFluids.HYDROGEN_GAS.get()), "Hydrogen leaves the cathode side");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void outletLabelsFollowTheExposedFaceAndItsProduct(GameTestHelper h) {
        var owner = assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 2, Direction.WEST);
        var first = owner.cells().getFirst();
        var last = owner.cells().getLast();
        outlet(h, first.outletTooltip(Direction.NORTH), "cathode", Component.translatable("electrolysis.port.awaiting_feed"));
        h.assertTrue(first.outletTooltip(Direction.WEST) == null && first.outletTooltip(Direction.EAST) == null
            && first.outletTooltip(Direction.UP) == null, "Joined, covered and panel faces have no outlet label");
        feed(owner, new FluidStack(CEChlorAlkali.BRINE.get(), 500));
        outlet(h, first.outletTooltip(Direction.NORTH), "cathode", CEFluids.HYDROGEN_GAS.get().getFluidType().getDescription());
        outlet(h, first.outletTooltip(Direction.SOUTH), "anode", CEChlorAlkali.CHLORINE.get().getFluidType().getDescription());
        outlet(h, last.outletTooltip(Direction.EAST), "liquid", CEChlorAlkali.CAUSTIC_SODA.get().getFluidType().getDescription());
        var stored = new FluidStack(CESharedChemicals.SULFURIC_ACID.get(), 100);
        last.tanks.get(3).setFluid(stored);
        outlet(h, last.outletTooltip(Direction.EAST), "liquid", stored.getHoverName());
        var head = owner.heads().getFirst();
        var metal = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        head.products.setStackInSlot(0, metal);
        outlet(h, head.outletTooltip(Direction.UP), "metal", metal.getHoverName());
        head.lower(true);turn(head, 17);
        outlet(h, head.outletTooltip(Direction.NORTH), "metal_locked", metal.getHoverName());
        h.assertTrue(head.outletTooltip(Direction.DOWN) == null, "The head's underside does not expose an item port");
        h.succeed();
    }

    private static void outlet(GameTestHelper h, Component label, String port, Component product) {
        h.assertTrue(label != null && label.getContents() instanceof TranslatableContents text
            && text.getKey().equals("electrolysis.port." + port) && product.equals(text.getArgs()[0]), "The " + port + " outlet identifies " + product);
    }

    @GameTest(template = "test_reactor")
    public static void fullEightLongStacksWorkAlongBothHorizontalAxes(GameTestHelper h) {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST}) {
            var minimum = h.absolutePos(new BlockPos((facing == Direction.WEST || facing == Direction.EAST) ? 14 : 2, 1, (facing == Direction.SOUTH || facing == Direction.EAST) ? 16 : 2));
            var owner = assemble(h.getLevel(), minimum, 8, facing);
            h.assertTrue(owner.formed() && owner.cells().size() == 8 && owner.structure().length() == 8, "An eight-cell row forms facing " + facing);
            load(owner, 128);
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            press(h,player,owner.structure().controls().get(1),.25,3.4/16);
            h.assertTrue(owner.enabled(),"The upper START button works facing "+facing);
            h.assertTrue(owner.heads().size() == 8, "Eight heads facing " + facing);
            lower(owner);
            feed(owner, new FluidStack(Fluids.WATER, 1000));run(owner, 1, 400);
            h.assertTrue(owner.plates() == 128 && owner.running() == 128 && owner.stored(1) == 256, "Every fitted plate runs exactly once");
        }
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void missingCellsAndDuplicatePanelsCannotRunAndResourcesSurviveReload(GameTestHelper h) {
        var owner = one(h);feed(owner, new FluidStack(Fluids.WATER, 300));run(owner, 10, 400);
        var lookup = h.getLevel().registryAccess();
        int[] before = {owner.stored(0), owner.stored(1), owner.stored(2)};
        for (var cell : owner.cells()) cell.loadWithComponents(cell.saveWithoutMetadata(lookup), lookup);
        for (var head : owner.heads()) head.loadWithComponents(head.saveWithoutMetadata(lookup), lookup);
        owner.loadWithComponents(owner.saveWithoutMetadata(lookup), lookup);owner.validate();
        h.assertTrue(owner.stored(0) == before[0] && owner.stored(1) == before[1] && owner.stored(2) == before[2] && owner.plates() == 1
            && owner.heads().stream().allMatch(ElectrodeHeadBlockEntity::lowered), "Reload does not copy or add contents and keeps the heads lowered");
        var head = owner.heads().getFirst().getBlockPos();h.getLevel().setBlock(head, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!owner.validate(), "A missing electrode head stops the stack from forming");
        h.getLevel().setBlock(head, CEElectrolysis.HEAD.get().defaultBlockState(), 3);h.assertTrue(owner.validate(), "A head set back on the cell reforms the stack");
        var end = owner.structure().minimum().south();
        h.getLevel().setBlock(end, CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.SOUTH), 3);
        h.assertTrue(!owner.validate(), "Second panel is rejected");
        h.getLevel().setBlock(end, Blocks.AIR.defaultBlockState(), 3);h.assertTrue(owner.validate(), "Removing the duplicate permits reforming");
        var removed = owner.cells().getLast().getBlockPos();
        h.getLevel().setBlock(removed, Blocks.AIR.defaultBlockState(), 3);
        owner.step();h.assertTrue(owner.running() == 0 && !owner.validate(), "A missing cross-section cell stops electrolysis immediately");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void blockedProductsAndARaisedHeadConsumeNoFeedOrStoredWork(GameTestHelper h) {
        var owner = one(h);feed(owner, new FluidStack(Fluids.WATER, 300));
        for (var cell : owner.cells()) cell.tanks.get(2).fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), ElectrolysisCellBlockEntity.CAPACITY), EXECUTE);
        run(owner, 10, 400);
        h.assertTrue(owner.stored(0) == 300 && owner.consumed() == 0 && owner.status().equals("output"), "Full anode output disconnects the circuit before material consumption");
        for (var cell : owner.cells()) cell.tanks.get(2).drain(ElectrolysisCellBlockEntity.CAPACITY, EXECUTE);
        var head = owner.heads().getLast();head.lower(false);turn(head, 2);
        run(owner, 10, 400);
        h.assertTrue(owner.stored(0) == 300 && owner.consumed() == 0 && owner.status().equals("raised"), "A head on its way up interlocks the whole stack");
        head.lower(true);turn(head, 17);run(owner, 1, 400);
        h.assertTrue(owner.running() == 1 && owner.stored(0) == 297, "Lowering it again permits the next paid cycle");h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void ninthSectionAndExtraPanelUnitsAreRejectedWithoutMovingStoredFluid(GameTestHelper h) {
        var owner = assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 8, Direction.NORTH);
        feed(owner, new FluidStack(Fluids.WATER, 2345));
        var retained = owner.cells();
        var next = owner.structure().minimum().south(8);
        h.getLevel().setBlock(next, CEElectrolysis.CELL.get().defaultBlockState(), 3);
        h.assertTrue(!owner.validate() && retained.stream().mapToInt(cell -> cell.tanks.getFirst().getFluidAmount()).sum() == 2345, "Nine sections cannot form or transfer storage");
        h.getLevel().setBlock(next, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(owner.validate() && owner.stored(0) == 2345, "Eight sections recover the original material ledger");
        var extra = owner.getBlockPos().above(2);
        h.getLevel().setBlock(extra, CEElectrolysis.CONTROLLER.get().defaultBlockState(), 3);
        h.assertTrue(!owner.validate(), "A third row of panel units is not an alternate controller");h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void lowerVoltagePaysForFewerCyclesAndRedstoneStopsTheActualLoad(GameTestHelper h) {
        var owner = one(h);feed(owner, new FluidStack(Fluids.WATER, 300));
        run(owner, 100, 200);
        h.assertTrue(owner.stored(1) == 50 && owner.stored(0) == 225 && Math.abs(owner.consumed() - 1250) < .001,
            "Half voltage delivers a quarter of the work: 250 W, 25 cycles and 1250 J");
        var panel = owner.getBlockPos();h.getLevel().setBlock(panel.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        run(owner, 10, 400);
        h.assertTrue(owner.resistance() == 0 && owner.running() == 0 && owner.status().equals("redstone") && owner.stored(0) == 225, "Panel redstone breaks the electrical load without consuming feed");h.succeed();
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 260)
    public static void aDrivenCreatePumpFeedsTheBottomAndConservesItsFiniteSource(GameTestHelper h) {
        var owner = one(h);var below = new BlockPos(3, 0, 3);var pump = below.west(2);
        h.setBlock(below.west(3), com.simibubi.create.AllBlocks.FLUID_TANK.get());
        h.setBlock(pump, com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
        var pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState();var bend = pipe;
        for (var side : Direction.values()) {
            pipe = pipe.setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == Direction.Axis.X);
            bend = bend.setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side == Direction.WEST || side == Direction.UP);
        }
        h.setBlock(below.west(), pipe);h.setBlock(below, bend);
        var source = ((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) h.getBlockEntity(below.west(3))).getTankInventory();
        source.fill(new FluidStack(Fluids.WATER, 4000), EXECUTE);
        h.setBlock(pump.south(), com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(pump.south().west(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(4, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getBlockEntity(pump.south().west())).generatedSpeed.setValue(64));
        h.runAfterDelay(200, () -> {
            h.assertTrue(owner.stored(0) > 0 && owner.stored(0) + source.getFluidAmount() == 4000 && owner.consumed() == 0,
                "Real rotating Create pump transfers finite water up into the bottom without electrical processing or fluid loss: " + owner.stored(0) + "+" + source.getFluidAmount());h.succeed();
        });
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 120)
    public static void aFunnelOnTheHeadLoadsPlatesOnlyWhileItIsRaised(GameTestHelper h) {
        var owner = assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 1, Direction.NORTH);var head = owner.heads().getFirst();
        h.assertTrue(head.raised(), "A new stack starts with its heads raised");
        var funnel = head.getBlockPos().above();
        h.getLevel().setBlock(funnel, com.simibubi.create.AllBlocks.ANDESITE_FUNNEL.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP)
            .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, false)
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false), 3);
        var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), funnel.getX() + .5, funnel.getY() + 1.1, funnel.getZ() + .5, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(4));
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(item);
        h.succeedWhen(() -> {
            h.assertTrue(head.nickel() == 4 && !item.isAlive(), "The funnel on the raised head collects the four nickel electrodes once: " + head.nickel());
            var port = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, head.getBlockPos(), Direction.UP);
            h.assertTrue(port.extractItem(1, 1, false).isEmpty(), "Funnels never pull the plates back out");
            owner.setRunning(true);turn(head, 17);
            h.assertTrue(head.lowered() && port.insertItem(0, CEElectrolysis.ELECTRODE_PLATE.toStack(), false).getCount() == 1, "The same item port is locked while the plates are down");h.succeed();
        });
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 100)
    public static void startLowersEveryHeadAndStopRaisesThemWithoutRotation(GameTestHelper h) {
        var owner = assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 3, Direction.NORTH);
        load(owner, 3);
        h.assertTrue(owner.heads().size() == 3 && owner.heads().stream().allMatch(ElectrodeHeadBlockEntity::raised), "The heads start raised");
        owner.setRunning(true);
        h.runAfterDelay(8, () -> {
            owner.step();
            h.assertTrue(owner.heads().stream().noneMatch(ElectrodeHeadBlockEntity::lowered) && owner.status().equals("lowering"), "The plates are on their way down: " + owner.status());
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(owner.heads().stream().allMatch(ElectrodeHeadBlockEntity::lowered), "START lowers every head by itself, with no rotation");
            owner.setRunning(false);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(owner.heads().stream().allMatch(ElectrodeHeadBlockEntity::raised), "STOP brings them back up");h.succeed();
        });
    }

    @GameTest(template = "test_petrochemical")
    public static void survivalPlacementAboveACellDoesNotOpenItsHatchAndMinedContentsCanBePlacedAgain(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);h.setBlock(pos, CEElectrolysis.CELL.get());
        var at = h.absolutePos(pos);var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        var stack = CEElectrolysis.CELL.toStack(2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0, .5, 0), Direction.UP, at, false);
        var state = h.getLevel().getBlockState(at);
        h.assertTrue(state.useItemOn(stack, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            && state.useWithoutItem(h.getLevel(), player, hit) == net.minecraft.world.InteractionResult.PASS, "A held construction block leaves both normal placement stages available");
        stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        h.assertTrue(h.getLevel().getBlockState(at.above()).is(CEElectrolysis.CELL.get()), "Normal block placement completes the second layer");
        var upper = (ElectrolysisCellBlockEntity)h.getLevel().getBlockEntity(at.above());
        upper.tanks.getFirst().fill(new FluidStack(Fluids.WATER, 321), EXECUTE);
        var drops = net.minecraft.world.level.block.Block.getDrops(upper.getBlockState(), h.getLevel(), upper.getBlockPos(), upper);
        h.assertTrue(drops.size() == 1 && drops.getFirst().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA), "Survival loot includes the finite cell inventory");
        h.getLevel().setBlock(upper.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        var replacement = drops.getFirst();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, replacement);
        replacement.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var restored = (ElectrolysisCellBlockEntity)h.getLevel().getBlockEntity(at.above());
        h.assertTrue(restored != null && restored.tanks.getFirst().getFluidAmount() == 321 && replacement.isEmpty(), "Replacing the mined block restores its 321 mB and consumes the dropped item once");h.succeed();
    }

    private static net.minecraft.world.InteractionResult press(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos part, double across, double height) {
        var facing = h.getLevel().getBlockState(part).getValue(ElectrolysisControllerBlock.FACING);
        double x = switch (facing) { case NORTH -> 1-across;case SOUTH -> across;case EAST -> 1;default -> 0; };
        double z = switch (facing) { case WEST -> across;case EAST -> 1-across;case SOUTH -> 1;default -> 0; };
        var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(part.getX()+x,part.getY()+height,part.getZ()+z),facing,part,false);
        return h.getLevel().getBlockState(part).useWithoutItem(h.getLevel(), player, hit);
    }

    @GameTest(template = "test_petrochemical")
    public static void upperButtonsAndFineDialControlTheRealLoad(GameTestHelper h) {
        var owner = one(h);feed(owner,new FluidStack(Fluids.WATER,300));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var controls = owner.structure().controls();var buttons = controls.get(1);var dial = controls.get(0);
        h.assertTrue(press(h,player,buttons,.25,.8)==net.minecraft.world.InteractionResult.PASS,"The voltmeter is read-only");
        h.assertTrue(press(h,player,buttons,2.45/16,6.15/16)==net.minecraft.world.InteractionResult.PASS,"The removed preset switches do not remain interactive");
        press(h,player,buttons,.75,3.4/16);owner.step();
        h.assertTrue(!owner.enabled() && owner.resistance()==0,"STOP disconnects the shared load");
        press(h,player,buttons,.25,3.4/16);owner.step();
        h.assertTrue(owner.enabled() && owner.resistance()>0,"START connects the shared load");
        press(h,player,dial,7/16d,7/16d);
        h.assertTrue(owner.limit()==99,"Turning the dial left changes the setting by one percent");
        player.setShiftKeyDown(true);press(h,player,dial,7/16d,7/16d);
        h.assertTrue(owner.limit()==89,"Sneaking turns the dial ten percent at a time");
        owner.selectLoad(1);press(h,player,dial,7/16d,7/16d);h.assertTrue(owner.limit()==1,"The lower stop is one percent");
        owner.selectLoad(100);press(h,player,dial,9/16d,7/16d);h.assertTrue(owner.limit()==100,"The upper stop is one hundred percent");
        player.setShiftKeyDown(false);owner.selectLoad(38);press(h,player,dial,7/16d,7/16d);
        h.assertTrue(owner.limit()==37 && Math.abs(owner.resistance()* .37-160)<.00001,"A non-preset 37 percent setting controls actual circuit resistance");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,CEElectrolysis.CONTROLLER.toStack());
        h.assertTrue(press(h,player,dial,9/16d,7/16d)==net.minecraft.world.InteractionResult.PASS && owner.limit()==37,"Building and wiring cannot turn the dial");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        run(owner,100,400);
        h.assertTrue(owner.stored(0)==189 && owner.stored(1)==74 && owner.stored(2)==37 && Math.abs(owner.consumed()-1850)<.001,
            "37 percent produces 37 paid cycles and conserves water, both gases and 1850 J");
        var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());owner.loadWithComponents(saved,h.getLevel().registryAccess());owner.validate();
        h.assertTrue(owner.limit()==37 && Math.abs(owner.consumed()-1850)<.001,"Saving retains the exact fine setting and consumed work");
        saved.putBoolean("EmergencyStop",true);saved.putBoolean("Enabled",true);
        owner.loadWithComponents(saved,h.getLevel().registryAccess());owner.validate();
        h.assertTrue(!owner.enabled() && owner.resistance()==0,"An old emergency-stop save stays stopped after its removed button is retired");
        press(h,player,buttons,.25,3.4/16);
        h.assertTrue(owner.enabled() && owner.resistance()>0,"The new START button can restart that safely migrated save");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void onePercentAccumulatesEnoughRealWorkForOneCycle(GameTestHelper h) {
        var owner=one(h);feed(owner,new FluidStack(Fluids.WATER,300));owner.selectLoad(1);run(owner,100,400);
        h.assertTrue(owner.stored(0)==297 && owner.stored(1)==2 && owner.stored(2)==1 && Math.abs(owner.consumed()-50)<.001,
            "At one percent, one plate draws 10 W and pays 50 J for exactly one cycle in 100 ticks");h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void aComparatorReadsHowFullTheElectrodeRacksAre(GameTestHelper h) {
        var owner = one(h);owner.step();
        BlockPos panel = owner.structure().controls().get(1);
        var state = h.getLevel().getBlockState(panel);
        h.assertTrue(state.hasAnalogOutputSignal() && state.getAnalogOutputSignal(h.getLevel(), panel) == 1, "One plate of sixteen reads 1 of 15: " + state.getAnalogOutputSignal(h.getLevel(), panel));
        owner.setRunning(false);for (var head : owner.heads()) turn(head, 17);load(owner, 16);
        owner.electrodeInventoryChanged();owner.step();
        h.assertTrue(owner.plates() == owner.plateCapacity() && state.getAnalogOutputSignal(h.getLevel(), panel) == 15, "Full racks read 15: " + owner.plates() + "/" + owner.plateCapacity());
        for (var head : owner.heads()) head.electrodes.extractItem(0, 64, false);
        owner.electrodeInventoryChanged();owner.step();
        owner.setRunning(true);owner.step();
        h.assertTrue(owner.plates() == 0 && state.getAnalogOutputSignal(h.getLevel(), panel) == 0 && owner.status().equals("plates"), "With no electrode left the signal is 0 and the cell stops: " + owner.status());
        h.succeed();
    }
}
