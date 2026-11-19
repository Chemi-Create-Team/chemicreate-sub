package net.createplant.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.createplant.CreatePlant;
import net.createplant.item.SteamHammerAction;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEPortableTools;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamHammerGameTests {
    private SteamHammerGameTests() { }

    private static final class Fighter extends FakePlayer {
        Fighter(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "steam_hammer_test")); }
        @Override public float getAttackStrengthScale(float adjust) { return 1; }
        void raiseGuard() { for (int tick = 0; tick < 5; tick++) updateUsingItem(getUseItem()); }
    }

    private static Fighter fighter(GameTestHelper h, int steam) {
        var player = new Fighter(h.getLevel());
        var eye = h.absoluteVec(new Vec3(2.5, 3.2, 2.5));
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0, 0);
        var hammer = CEPortableTools.STEAM_HAMMER.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        if (steam > 0) h.assertTrue(FluidUtil.getFluidHandler(hammer).orElseThrow().fill(new FluidStack(CEFluids.STEAM.get(), steam), EXECUTE) == steam,
            "The hammer uses the normal portable steam capability");
        return player;
    }

    @GameTest(template = "test_reactor")
    public static void poweredSwingSpendsOneDoseAndHitsAfterTheBoost(GameTestHelper h) {
        var player = fighter(h, 500);
        var tank = FluidUtil.getFluidHandler(player.getMainHandItem()).orElseThrow();
        h.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 0, "The hammer rejects liquid water");
        var target = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 4));
        target.setNoAi(true);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        double originalSpeed = target.getAttributeValue(Attributes.MOVEMENT_SPEED), startZ = target.getZ();
        SteamHammerAction.SWING.handle(player);
        SteamHammerAction.SWING.handle(player);
        h.assertTrue(player.swinging && player.getDeltaMovement().z > .7 && tank.getFluidInTank(0).getAmount() == 250 && target.getHealth() == 100,
            "One steam dose starts a forward boost and a wind-up before damage");
        h.runAfterDelay(7, () -> {
            h.assertTrue(target.getHealth() == 96 && (target.getZ() > startZ || target.getDeltaMovement().z > 0) && target.hasEffect(MobEffects.MOVEMENT_SPEED)
                && target.hasEffect(CEPortableTools.DAZED) && target.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0,
                "The blow deals four damage, knocks back and dazes the target with Speed active: health=" + target.getHealth()
                    + ", displacement=" + (target.getZ() - startZ) + ", velocity=" + target.getDeltaMovement() + ", effects=" + target.getActiveEffects());
        });
        h.runAfterDelay(24, () -> {
            h.assertTrue(!target.hasEffect(CEPortableTools.DAZED) && target.hasEffect(MobEffects.MOVEMENT_SPEED)
                && target.getAttributeValue(Attributes.MOVEMENT_SPEED) > originalSpeed, "Speed remains after the stun wears off");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void dryDazedAndObstructedSwingsCannotHit(GameTestHelper h) {
        var player = fighter(h, 0);
        var target = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 4));
        target.setNoAi(true);
        SteamHammerAction.SWING.handle(player);
        h.assertTrue(!player.swinging && player.getDeltaMovement().lengthSqr() == 0, "An empty hammer does not boost or swing");
        var tank = FluidUtil.getFluidHandler(player.getMainHandItem()).orElseThrow();
        tank.fill(new FluidStack(CEFluids.STEAM.get(), 250), EXECUTE);
        player.addEffect(new MobEffectInstance(CEPortableTools.DAZED, 14));
        SteamHammerAction.SWING.handle(player);
        h.assertTrue(!player.swinging && tank.getFluidInTank(0).getAmount() == 250, "A stunned player cannot swing the hammer");
        player.removeEffect(CEPortableTools.DAZED);
        h.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
        SteamHammerAction.SWING.handle(player);
        h.runAfterDelay(7, () -> {
            h.assertTrue(target.getHealth() == 100 && !target.hasEffect(CEPortableTools.DAZED) && tank.getFluidInTank(0).isEmpty(),
                "A solid wall stops the blow and effects while the swing still uses its charge");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void raisedGuardBlocksFrontDamageAndConsumesNoSteam(GameTestHelper h) {
        var player = fighter(h, 250);
        var hammer = player.getMainHandItem();
        hammer.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        player.raiseGuard();
        h.assertTrue(player.isBlocking(), "Right-click starts native shield-equivalent guarding");
        SteamHammerAction.SWING.handle(player);
        h.assertTrue(!player.swinging && FluidUtil.getFluidHandler(hammer).orElseThrow().getFluidInTank(0).getAmount() == 250,
            "A raised guard prevents attacking and consumes no charge");

        var defender = h.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 2));
        var attacker = h.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 5));
        defender.setNoAi(true);
        attacker.setNoAi(true);
        defender.setYRot(0);
        defender.setItemInHand(InteractionHand.MAIN_HAND, hammer.copy());
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        h.runAfterDelay(7, () -> {
            var damage = h.getLevel().damageSources().mobAttack(attacker);
            h.assertTrue(defender.isBlocking() && !defender.hurt(damage, 4) && defender.getHealth() == 100,
                "A raised guard blocks real frontal damage");
            defender.stopUsingItem();
            defender.invulnerableTime = 0;
            h.assertTrue(defender.hurt(damage, 4) && defender.getHealth() == 96, "Releasing use lowers the guard");
            h.succeed();
        });
    }
}
