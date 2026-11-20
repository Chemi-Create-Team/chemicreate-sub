package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltMovementHandler;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.HoseConnectorBlock;
import net.createplant.block.PressurePumpStructure;
import net.createplant.block.PressurePumpStructure.Part;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.registry.CESafety;
import net.createplant.safety.CorrosiveContact;
import net.createplant.safety.HazardSuit;
import net.createplant.transport.HoseSocket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PressureInfrastructureGameTests {
    private static final BlockPos CORE = new BlockPos(5, 4, 6);
    private static final Direction FRONT = Direction.EAST;

    @GameTest(template = "test_pressure_infrastructure", timeoutTicks = 420)
    public static void topShaftDrivesHeavyOilThrough128PipeBlocks(GameTestHelper h) {
        var pump = assemble(h, CORE);
        h.assertTrue(pump.formed() && pump.calculateStressApplied() == 8, "Eight formed parts share an 8 SU/RPM top drive");
        var input = tank(h, CORE.below().west(3));
        input.getTankInventory().setFluid(new FluidStack(CEPetroFluids.HEAVY_OIL.get(), 4_000));
        pipe(h, CORE.below().west(2));
        var target = tank(h, CORE.east(129));
        for (int i = 1; i <= 128; i++) pipe(h, CORE.east(i));
        motor(h, CORE);
        h.succeedWhen(() -> {
            h.assertTrue(target.getTankInventory().getFluidAmount() >= 256,
                "Native Create pipeline receives heavy oil through 128 pipes; rpm " + pump.getSpeed()
                    + ", pump buffer " + pump.internalPort(false).getFluidInTank(0).getAmount()
                    + ", source " + input.getTankInventory().getFluidAmount());
            h.assertTrue(target.getTankInventory().getFluid().is(CEPetroFluids.HEAVY_OIL.get()), "Flow retains fluid identity");
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 150)
    public static void hoseItemConnectsThePumpToATankAndReleasesOnlyOneHose(GameTestHelper h) {
        var pump = assemble(h, CORE);
        var sourceFlange = CORE.below().west(3).south(4);
        h.setBlock(sourceFlange, CEPressureInfrastructure.CONNECTOR.get().defaultBlockState()
            .setValue(HoseConnectorBlock.FACING, Direction.EAST));
        var source = tank(h, sourceFlange.west());
        source.getTankInventory().setFluid(new FluidStack(Fluids.WATER, 1_000));
        var suction = HoseSocket.endpoint(h.getLevel(), h.absolutePos(Part.SUCTION.at(CORE, FRONT)));
        h.assertTrue(suction.hose().connect(HoseSocket.endpoint(h.getLevel(), h.absolutePos(sourceFlange))),
            "A remote tank also connects directly to the pump suction");
        var flange = CORE.east(13).above(2).south(3);
        h.setBlock(flange, CEPressureInfrastructure.CONNECTOR.get().defaultBlockState().setValue(HoseConnectorBlock.FACING, Direction.WEST));
        var target = tank(h, flange.east());
        var end = HoseSocket.endpoint(h.getLevel(), h.absolutePos(flange));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var hose = CEPressureInfrastructure.HOSE.toStack(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, hose);
        h.assertTrue(click(h, player, CORE, FRONT).consumesAction() && hose.getCount() == 2 && !pump.hose().connected(),
            "First ring click records a start without spending the item");
        h.assertTrue(click(h, player, flange, Direction.WEST).consumesAction() && hose.getCount() == 1
            && pump.hose().remote().equals(h.absolutePos(flange)) && end.hose().peer() == pump,
            "Second click secures both endpoints and spends one item");
        motor(h, CORE);
        h.succeedWhen(() -> {
            h.assertTrue(target.getTankInventory().getFluidAmount() >= 400, "Water reaches the remote tank over the tall, offset hose");
            h.assertTrue(pump.hose().disconnect(true) && !end.hose().connected(), "Removal clears both ends");
            h.assertTrue(!end.hose().disconnect(true), "The second removal cannot duplicate the hose");
        });
    }

    @GameTest(template = "test_pressure_infrastructure")
    public static void connectorsExposeFluidInBothDirectionsAtThe32BlockLimit(GameTestHelper h) {
        var first = new BlockPos(10, 4, 8);
        var last = first.east(32);
        var extra = first.east(33).south(2);
        var west = CEPressureInfrastructure.CONNECTOR.get().defaultBlockState().setValue(HoseConnectorBlock.FACING, Direction.WEST);
        h.setBlock(first, west.setValue(HoseConnectorBlock.FACING, Direction.EAST));
        h.setBlock(last, west);
        h.setBlock(extra, west);
        var source = HoseSocket.endpoint(h.getLevel(), h.absolutePos(first));
        var distant = HoseSocket.endpoint(h.getLevel(), h.absolutePos(extra));
        var nearest = HoseSocket.endpoint(h.getLevel(), h.absolutePos(last));
        h.assertTrue(!source.hose().connect(distant) && source.hose().connect(nearest),
            "A hose reaches exactly 32 blocks and refuses longer links");
        var tank = tank(h, last.east());
        var api = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            h.absolutePos(first), Direction.WEST);
        h.assertTrue(api != null && api.fill(new FluidStack(Fluids.WATER, 300), EXECUTE) == 300
            && tank.getTankInventory().getFluidAmount() == 300
            && api.drain(50, EXECUTE).getAmount() == 50 && tank.getTankInventory().getFluidAmount() == 250,
            "The same exposed rear capability can fill and drain a linked backing tank");
        var entity = h.getBlockEntity(last);
        var copy = entity.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(source.hose().disconnect(false), "The link can be cut");
        entity.loadWithComponents(copy, h.getLevel().registryAccess());
        nearest.hose().validate();
        h.assertTrue(!nearest.hose().connected(), "An old unloaded endpoint cannot resurrect a removed link");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void armorSealsGasWhileBootsGripWithoutTheRest(GameTestHelper h) {
        var animal = h.spawn(EntityType.COW, 6, 3, 5);
        animal.setItemSlot(EquipmentSlot.FEET, CEPressureInfrastructure.BOOTS.toStack());
        h.assertTrue(!BeltMovementHandler.canBeTransported(animal), "Create cannot move a wearer of the safety boots");
        float health = animal.getHealth();
        animal.causeFallDamage(15, 1, animal.damageSources().fall());
        h.assertTrue(animal.getHealth() == health, "The boots absorb the first fifteen blocks of a fall");
        animal.setItemSlot(EquipmentSlot.HEAD, CEPressureInfrastructure.HELMET.toStack());
        animal.setItemSlot(EquipmentSlot.CHEST, CEPressureInfrastructure.CHEST.toStack());
        animal.setItemSlot(EquipmentSlot.LEGS, CEPressureInfrastructure.LEGS.toStack());
        for (var effect : java.util.List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.CONFUSION, MobEffects.BLINDNESS))
            h.assertTrue(!animal.addEffect(new MobEffectInstance(effect, 200)), "The complete suit blocks " + effect);
        animal.hurt(animal.damageSources().source(CorrosiveContact.DAMAGE), 2);
        animal.hurt(animal.damageSources().source(HazardSuit.HOT_LIQUID), 2);
        h.assertTrue(animal.getHealth() == health, "The complete suit protects against chemical and thermal contact");
        var helmet = animal.getItemBySlot(EquipmentSlot.HEAD);
        h.assertTrue(!animal.addEffect(new MobEffectInstance(CESafety.TOXIC_GAS, 200)) && helmet.getDamageValue() == 1,
            "A filter prevents gas, with proportional wear");
        helmet.setDamageValue(helmet.getMaxDamage());
        h.assertTrue(animal.addEffect(new MobEffectInstance(CESafety.TOXIC_GAS, 200)), "A spent filter must be replaced");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void kitChecksClearanceAndReturnsItsContentsOnDismantle(GameTestHelper h) {
        var kit = CEPressureInfrastructure.PUMP_ITEM.toStack();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, kit);
        var support = CORE.below(2);
        h.setBlock(support, Blocks.STONE);
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(support)), Direction.UP, h.absolutePos(support), false);
        var ctx = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        h.setBlock(CORE.relative(FRONT.getClockWise()), Blocks.STONE);
        h.assertTrue(!kit.getItem().useOn(ctx).consumesAction() && kit.getCount() == 1 && h.getBlockState(CORE).isAir(),
            "A blocked footprint leaves the entire kit in hand");
        h.setBlock(CORE.relative(FRONT.getClockWise()), Blocks.AIR);
        h.assertTrue(kit.getItem().useOn(ctx).consumesAction() && kit.isEmpty(), "The kit places and consumes once");
        var pump = (HighPressurePumpBlockEntity) h.getBlockEntity(CORE);
        h.assertTrue(pump != null && pump.formed()
            && pump.internalPort(true).fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 500, "Placement assembles the full working machine");
        var broken = CORE.relative(FRONT.getClockWise());
        h.getLevel().destroyBlock(h.absolutePos(broken), true);
        var returned = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(h.absolutePos(broken)).inflate(4),
            item -> item.getItem().is(CEPressureInfrastructure.PUMP_ITEM.get()));
        h.assertTrue(returned.size() == 1 && returned.getFirst().getItem()
                .getOrDefault(net.createplant.registry.CEPortableTools.CONTENTS.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.EMPTY)
                .copy().getAmount() == 500 && h.getBlockState(CORE).isAir(),
            "Dismantling returns one kit with all remaining buffer contents");
        h.succeed();
    }

    private static net.minecraft.world.InteractionResult click(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos pos, Direction side) {
        var worldPos = h.absolutePos(pos);
        return CEPressureInfrastructure.HOSE.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(worldPos), side, worldPos, false)));
    }

    private static HighPressurePumpBlockEntity assemble(GameTestHelper h, BlockPos drive) {
        for (var part : Part.values()) h.setBlock(part.at(drive, FRONT), part.state(FRONT));
        PressurePumpStructure.refresh(h.getLevel(), h.absolutePos(drive), FRONT);
        return (HighPressurePumpBlockEntity) h.getBlockEntity(drive);
    }

    private static FluidTankBlockEntity tank(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AllBlocks.FLUID_TANK.get());
        return (FluidTankBlockEntity) h.getBlockEntity(pos);
    }

    private static void pipe(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true)
            .setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.NORTH, false).setValue(FluidPipeBlock.SOUTH, false)
            .setValue(FluidPipeBlock.UP, false).setValue(FluidPipeBlock.DOWN, false));
    }

    private static void motor(GameTestHelper h, BlockPos root) {
        h.setBlock(root.above(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        h.setBlock(root.above(2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        h.runAfterDelay(4, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(root.above(2))).generatedSpeed.setValue(64));
    }
}
