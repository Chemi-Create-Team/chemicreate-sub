package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.item.SprayToolItem;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CESafety;
import net.createplant.safety.SprayEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class SprayToolGameTests {
    /** One spray from three blocks away, straight along +Z at the target's chest. */
    private static LivingEntity sprayAt(GameTestHelper h, SprayToolItem tool, FluidStack fluid, boolean burning) {
        var target = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 5));
        target.setNoAi(true);
        if (burning) target.igniteForSeconds(10);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 eye = h.absoluteVec(new Vec3(2.5, 3.2, 2.5));
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0, 15);
        SprayEffects.spray(h.getLevel(), player, new ItemStack(tool), fluid, tool);
        return target;
    }

    @GameTest(template = "test_reactor")
    public static void theFlameBurnsAndHitsForDoubleDamage(GameTestHelper h) {
        var target = sprayAt(h, CEPortableTools.FLAMETHROWER.get(), new FluidStack(Fluids.WATER, 20), false);
        h.assertTrue(target.isOnFire() && target.getMaxHealth() - target.getHealth() == 4, "The flame sets the target alight and deals 4: " + (target.getMaxHealth() - target.getHealth()));
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void acidHitsHardAndLeavesABurn(GameTestHelper h) {
        var target = sprayAt(h, CEPortableTools.CHEMICAL_SPRAYER.get(), new FluidStack(CEChlorAlkali.HYDROCHLORIC_ACID.get(), 20), false);
        h.assertTrue(target.getMaxHealth() - target.getHealth() == 8 && target.hasEffect(CESafety.ACID_BURN), "A direct hit of acid deals 8 and leaves a burn: " + (target.getMaxHealth() - target.getHealth()));
        float hit = target.getHealth();
        h.runAfterDelay(45, () -> {
            h.assertTrue(target.getHealth() < hit, "The burn keeps hurting after the spray");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void theExtinguisherPutsTheTargetOut(GameTestHelper h) {
        var target = sprayAt(h, CEPortableTools.FIRE_EXTINGUISHER.get(), new FluidStack(Fluids.WATER, 20), true);
        h.assertTrue(!target.isOnFire() && target.getHealth() == target.getMaxHealth(), "Water puts the fire out and does no harm");
        h.succeed();
    }
}
