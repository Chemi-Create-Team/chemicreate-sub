package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.GasDetectorBlock;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProtectiveClothing;
import net.createplant.registry.CESharedChemicals;
import net.createplant.registry.CETransport;
import net.createplant.registry.CESafety;
import net.createplant.safety.CorrosiveContact;
import net.createplant.safety.ToxicGas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class IndustrialGasSafetyGameTests {
    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void detectorPowersAnAlarmAndClearsAfterTheCloud(GameTestHelper h) {
        var detector = new BlockPos(5, 2, 5);
        var lamp = detector.east();
        // The gas lies on the floor, so a detector mounted higher up reads it more weakly.
        var remote = detector.west(2).above(3);
        h.setBlock(detector, CESafety.GAS_DETECTOR.get());
        h.setBlock(remote, CESafety.GAS_DETECTOR.get());
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        var world = h.getLevel();
        var emission = h.absolutePos(detector.north());
        h.startSequence().thenIdle(15).thenExecute(() -> {
            h.assertTrue(!h.getBlockState(lamp).getValue(BlockStateProperties.LIT), "An empty room has no alarm");
            ToxicGas.releasePoison(world, emission, new FluidStack(CESharedChemicals.SULFUR_DIOXIDE.get(), 1000));
        }).thenWaitUntil(() -> {
            int nearPower = h.getBlockState(detector).getValue(GasDetectorBlock.POWER);
            int farPower = h.getBlockState(remote).getValue(GasDetectorBlock.POWER);
            h.assertTrue(farPower > 0 && nearPower > farPower && h.getBlockState(lamp).getValue(BlockStateProperties.LIT),
                "A nearer detector sends a stronger redstone signal and turns on the lamp");
        }).thenExecute(() -> net.createplant.cloud.GasClouds.in(world, new AABB(emission).inflate(2))
            .forEach(net.createplant.cloud.GasCloud::discard))
            .thenWaitUntil(() -> h.assertTrue(h.getBlockState(detector).getValue(GasDetectorBlock.POWER) == 0
                && !h.getBlockState(lamp).getValue(BlockStateProperties.LIT), "The alarm clears when the cloud is gone"))
            .thenSucceed();
    }

    @GameTest(template = "test_reactor")
    public static void cylinderTransfersInertGasBetweenNativePorts(GameTestHelper h) {
        var source = new BlockPos(3, 2, 3);
        var destination = source.east(3);
        var tank = CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        h.setBlock(source, tank);
        h.setBlock(destination, tank);
        var level = h.getLevel();
        var feed = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(source), Direction.WEST);
        var receiver = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(destination), Direction.WEST);
        var gas = new FluidStack(CEFluids.ARGON_GAS.get(), 4000);
        h.assertTrue(feed.fill(gas, EXECUTE) == gas.getAmount(), "The source has one full cylinder of inert gas");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var cylinder = CESafety.GAS_CYLINDER.toStack();
        var port = cylinder.getCapability(Capabilities.FluidHandler.ITEM);
        h.assertTrue(port.fill(new FluidStack(CESharedChemicals.SULFUR_DIOXIDE.get(), 1000), EXECUTE) == 0
            && port.fill(gas, SIMULATE) == 4000 && port.getFluidInTank(0).isEmpty(), "The portable cylinder only accepts inert gas, and simulation is read-only");
        player.setItemInHand(InteractionHand.MAIN_HAND, cylinder);
        useCylinder(h, player, source);
        var carried = player.getItemInHand(InteractionHand.MAIN_HAND).getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0);
        h.assertTrue(carried.is(gas.getFluid()) && carried.getAmount() == 4000 && feed.getFluidInTank(0).isEmpty(),
            "Right click loads the cylinder and debits the source");
        useCylinder(h, player, destination);
        h.assertTrue(receiver.getFluidInTank(0).is(gas.getFluid()) && receiver.getFluidInTank(0).getAmount() == 4000
            && player.getItemInHand(InteractionHand.MAIN_HAND).getCapability(Capabilities.FluidHandler.ITEM).getFluidInTank(0).isEmpty(),
            "The second right click puts the same charge in another tank");
        h.succeed();
    }

    private static void useCylinder(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos tank) {
        var absolute = h.absolutePos(tank);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(absolute), Direction.WEST, absolute, false)));
    }

    @GameTest(template = "test_reactor")
    public static void completeClothingSetBlocksOnlyCorrosiveContact(GameTestHelper h) {
        var worker = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(5.5, 2, 5.5));
        worker.setItemSlot(EquipmentSlot.CHEST, CEProtectiveClothing.PROTECTIVE_COAT.toStack());
        worker.setItemSlot(EquipmentSlot.LEGS, CEProtectiveClothing.PROTECTIVE_TROUSERS.toStack());
        worker.setItemSlot(EquipmentSlot.FEET, CEProtectiveClothing.PROTECTIVE_SHOE_COVERS.toStack());
        worker.hurt(worker.damageSources().source(CorrosiveContact.DAMAGE), 2);
        h.assertTrue(worker.getHealth() == worker.getMaxHealth(), "The three-piece clothing set blocks corrosive contact");
        worker.hurt(worker.damageSources().source(ToxicGas.DAMAGE), 2);
        h.assertTrue(worker.getHealth() < worker.getMaxHealth(), "Clothing alone does not block the existing respiratory damage");
        h.succeed();
    }
}
