package net.createplant.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.createplant.CreatePlant;
import net.createplant.item.SteamGunAction;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamGunGameTests {
    private SteamGunGameTests() { }

    private static FakePlayer shooter(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "steam_gun_test"));
        var eye = h.absoluteVec(new Vec3(2.5, 3.2, 2.5));
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, CEPortableTools.STEAM_GUN.toStack());
        return player;
    }

    @GameTest(template = "test_reactor")
    public static void shiftUseTakesFiniteSteamFromTheReceiver(GameTestHelper h) {
        var player = shooter(h);
        var receiver = new BlockPos(2, 2, 4);
        h.setBlock(receiver, CETransport.GAS_RECEIVER.get());
        var at = h.absolutePos(receiver);
        var tank = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.UP);
        var steam = new FluidStack(CEFluids.STEAM.get(), 2500);
        h.assertTrue(tank != null && tank.fill(steam, EXECUTE) == 2500, "The standard gas receiver stores steam");
        var empty = FluidUtil.getFluidHandler(player.getMainHandItem()).orElseThrow();
        h.assertTrue(empty.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 0
            && empty.fill(steam, SIMULATE) == 2000 && empty.getFluidInTank(0).isEmpty(), "The gun accepts steam, and simulations leave it empty");
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, .5, 0), Direction.UP, at, false);
        h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Sneaking use charges the gun");
        var loaded = FluidUtil.getFluidHandler(player.getMainHandItem()).orElseThrow();
        h.assertTrue(loaded.getFluidInTank(0).is(CEFluids.STEAM.get()) && loaded.getFluidInTank(0).getAmount() == 2000
            && tank.getFluidInTank(0).getAmount() == 500, "The gun's charge and the receiver's remaining steam balance");
        h.assertTrue(loaded.fill(new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 1000), SIMULATE) == 0, "Different steam grades stay separate");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void aShotRequiresAimConsumesSteamAndHitsTheNearestMobForThirtyTwo(GameTestHelper h) {
        var player = shooter(h);
        var target = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 6));
        var behind = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 10));
        var held = player.getMainHandItem();
        var tank = FluidUtil.getFluidHandler(held).orElseThrow();
        tank.fill(new FluidStack(CEFluids.STEAM.get(), 500), EXECUTE);
        var shot = new SteamGunAction(false);
        shot.handle(player);
        h.assertTrue(target.getHealth() == 100 && tank.getFluidInTank(0).getAmount() == 500, "A packet cannot fire the gun before it is raised");
        held.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        shot.handle(player);
        shot.handle(player);
        h.assertTrue(target.getHealth() == 68 && behind.getHealth() == 100, "One shot deals 32 damage to the nearest target only");
        h.assertTrue(tank.getFluidInTank(0).getAmount() == 250, "The cooldown blocks a second charge in the same tick");
        for (int tick = 0; tick < 20; tick++) player.getCooldowns().tick();
        h.setBlock(new BlockPos(2, 3, 4), Blocks.STONE);
        target.invulnerableTime = 0;
        shot.handle(player);
        h.assertTrue(target.getHealth() == 68 && tank.getFluidInTank(0).isEmpty(), "A wall blocks the next shot, but the shot still uses its steam");
        for (int tick = 0; tick < 20; tick++) player.getCooldowns().tick();
        h.setBlock(new BlockPos(2, 3, 4), Blocks.AIR);
        target.invulnerableTime = 0;
        shot.handle(player);
        h.assertTrue(target.getHealth() == 68, "An empty gun cannot fire");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void theBayonetUsesNormalReachAndDealsSixWithoutSteam(GameTestHelper h) {
        var player = shooter(h);
        var distant = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 8));
        var jab = new SteamGunAction(true);
        jab.handle(player);
        h.assertTrue(distant.getHealth() == 100 && player.swinging, "A thrust animates but cannot hit beyond melee reach");
        for (int tick = 0; tick < 13; tick++) player.getCooldowns().tick();
        var close = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 4));
        jab.handle(player);
        h.assertTrue(close.getHealth() == 94, "The empty gun's bayonet deals six damage");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        close.invulnerableTime = 0;
        for (int tick = 0; tick < 13; tick++) player.getCooldowns().tick();
        jab.handle(player);
        h.assertTrue(close.getHealth() == 94, "A stale thrust packet cannot attack after the gun is put away");
        h.succeed();
    }
}
