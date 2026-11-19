package net.createplant.item;

import java.util.List;
import net.createplant.registry.CEPortableTools;
import net.createplant.safety.LoadedRay;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class SteamGunItem extends PortableFluidToolItem {
    public static final int CAPACITY = 2000, STEAM_PER_SHOT = 250;
    public static final int SHOT_DAMAGE = 32, BAYONET_DAMAGE = 6;
    private static final int SHOT_DELAY = 20, BAYONET_DELAY = 13, DRY_DELAY = 5;
    private static final double SHOT_RANGE = 32;
    public static final String BAYONET_KEY = "key.create_refinery_n_reactors.steam_gun_bayonet";
    private static final String TOOLTIP = "tooltip.create_refinery_n_reactors.steam_gun.";

    public SteamGunItem(Properties properties) { super(properties); }

    @Override public int capacity() { return CAPACITY; }
    @Override public boolean accepts(FluidStack fluid) { return SteamGrades.of(fluid) != null; }
    @Override public int getBarColor(ItemStack stack) { return 0xB8DAE1; }

    public static boolean aiming(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND && entity.getUseItem().getItem() instanceof SteamGunItem;
    }

    @Override protected InteractionResultHolder<ItemStack> beginUse(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity target) { return true; }

    void act(ServerPlayer player, boolean bayonet) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(this) || !player.isAlive() || player.isSpectator() || player.hasEffect(CEPortableTools.DAZED)
            || player.getCooldowns().isOnCooldown(this)) return;
        var level = player.serverLevel();
        if (bayonet) {
            player.swing(InteractionHand.MAIN_HAND, true);
            player.getCooldowns().addCooldown(this, BAYONET_DELAY);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, .6f, .9f);
        } else {
            if (!aiming(player)) return;
            var tank = new PortableToolFluidHandler(stack);
            var available = tank.drain(STEAM_PER_SHOT, FluidAction.SIMULATE);
            if (available.getAmount() != STEAM_PER_SHOT || !accepts(available)) {
                player.getCooldowns().addCooldown(this, DRY_DELAY);
                player.displayClientMessage(Component.translatable(TOOLTIP + "empty"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, .55f, 1);
                return;
            }
            if (!player.getAbilities().instabuild) tank.drain(STEAM_PER_SHOT, FluidAction.EXECUTE);
            player.getCooldowns().addCooldown(this, SHOT_DELAY);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, .9f, .65f);
        }

        var start = player.getEyePosition();
        var direction = player.getLookAngle();
        double range = bayonet ? player.entityInteractionRange() : SHOT_RANGE;
        var end = LoadedRay.end(level, start, start.add(direction.scale(range)));
        end = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, player, start, end, new AABB(start, end).inflate(1),
            entity -> entity instanceof LivingEntity living && living.isAlive() && living.isPickable() && !living.isSpectator(), 0f);
        if (hit != null) {
            var target = hit.getEntity();
            if (!(target instanceof Player other) || level.getServer().isPvpAllowed() && player.canHarmPlayer(other)) {
                var source = level.damageSources().source(bayonet ? DamageTypes.PLAYER_ATTACK : DamageTypes.ARROW, player);
                target.hurt(source, bayonet ? BAYONET_DAMAGE : SHOT_DAMAGE);
            }
            end = hit.getLocation();
        }
        if (!bayonet) {
            var muzzle = start.add(direction.scale(Math.min(.7, start.distanceTo(end)))).add(0, -.08, 0);
            level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 6, .08, .08, .08, .035);
            level.sendParticles(ParticleTypes.POOF, end.x, end.y, end.z, 3, .04, .04, .04, .02);
        }
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(contentsText(stack));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.refill"));
        tooltip.add(Component.translatable(TOOLTIP + "shoot", SHOT_DAMAGE, STEAM_PER_SHOT));
        tooltip.add(Component.translatable(TOOLTIP + "bayonet", Component.keybind(BAYONET_KEY), BAYONET_DAMAGE));
    }
}
