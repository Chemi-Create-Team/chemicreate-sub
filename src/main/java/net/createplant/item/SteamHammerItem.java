package net.createplant.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.registry.CEPortableTools;
import net.createplant.safety.LoadedRay;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class SteamHammerItem extends PortableFluidToolItem {
    public static final int CAPACITY = 2000, STEAM_PER_SWING = 250, DAMAGE = 4;
    private static final int WINDUP_TICKS = 3, LAST_IMPACT_TICK = 6, DAZE_TICKS = 14, SPEED_TICKS = 80;
    private static final String TOOLTIP = "tooltip.create_refinery_n_reactors.steam_hammer.";
    private final Map<ServerPlayer, Swing> swings = new HashMap<>();

    private record Swing(ServerLevel level, ItemStack stack, Vec3 direction, long startTick) { }

    public SteamHammerItem(Properties properties) { super(properties.attributes(attributes())); }

    private static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.75, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
    }

    @Override public int capacity() { return CAPACITY; }
    @Override public boolean accepts(FluidStack fluid) { return SteamGrades.of(fluid) != null; }
    @Override public int getBarColor(ItemStack stack) { return 0xB8DAE1; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }
    @Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) { return ItemAbilities.DEFAULT_SHIELD_ACTIONS.contains(ability); }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) { return true; }

    @Override protected InteractionResultHolder<ItemStack> beginUse(Level level, Player player, InteractionHand hand, ItemStack stack) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    public static boolean guarding(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof SteamHammerItem;
    }

    void startSwing(ServerPlayer player) {
        var stack = player.getMainHandItem();
        if (!stack.is(this) || !canStrike(player) || player.getAttackStrengthScale(.5f) < 1 || swings.containsKey(player)) return;
        player.resetAttackStrengthTicker();
        var tank = new PortableToolFluidHandler(stack);
        var available = tank.drain(STEAM_PER_SWING, FluidAction.SIMULATE);
        var level = player.serverLevel();
        if (available.getAmount() != STEAM_PER_SWING || !accepts(available)) {
            player.displayClientMessage(Component.translatable(TOOLTIP + "empty"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, .55f, 1);
            return;
        }
        if (!player.getAbilities().instabuild) tank.drain(STEAM_PER_SWING, FluidAction.EXECUTE);
        swings.put(player, new Swing(level, stack, player.getLookAngle(), level.getGameTime()));
        player.swing(InteractionHand.MAIN_HAND, true);

        var forward = Vec3.directionFromRotation(0, player.getYRot());
        if (!player.isPassenger() && !player.isFallFlying()) {
            var speed = player.getDeltaMovement();
            player.setDeltaMovement(speed.x * .35 + forward.x * .78, speed.y, speed.z * .35 + forward.z * .78);
            player.hurtMarked = true;
        }
        var vent = player.position().add(0, 1.1, 0).subtract(forward.scale(.55));
        level.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 10, .18, .16, .18, .06);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .85f, .7f);
    }

    private static boolean canStrike(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isUsingItem() && !player.hasEffect(CEPortableTools.DAZED);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var it = CEPortableTools.STEAM_HAMMER.get().swings.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var player = entry.getKey();
            var swing = entry.getValue();
            if (player.serverLevel() != swing.level || player.getMainHandItem() != swing.stack || !canStrike(player)) {
                it.remove();
                continue;
            }
            long age = swing.level.getGameTime() - swing.startTick;
            if (age > LAST_IMPACT_TICK || age >= WINDUP_TICKS && strike(player, swing)) it.remove();
        }
    }

    private static boolean strike(ServerPlayer player, Swing swing) {
        var level = swing.level;
        var start = player.getEyePosition();
        var end = LoadedRay.end(level, start, start.add(swing.direction.scale(player.entityInteractionRange())));
        end = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, player, start, end, new AABB(start, end).inflate(1),
            target -> target instanceof LivingEntity living && living.isAlive() && living.isPickable() && !living.isSpectator(), 0f);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return false;
        if (target instanceof Player other && (!level.getServer().isPvpAllowed() || !player.canHarmPlayer(other))) return true;
        if (!target.hurt(level.damageSources().playerAttack(player), DAMAGE) || !target.isAlive()) return true;
        target.knockback(1.25, -swing.direction.x, -swing.direction.z);
        if (target instanceof ServerPlayer) target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_TICKS), player);
        if (target.addEffect(new MobEffectInstance(CEPortableTools.DAZED, DAZE_TICKS), player)) target.stopUsingItem();
        var impact = hit.getLocation();
        level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 12, .23, .23, .23, .16);
        level.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, .65f, 1);
        return true;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(contentsText(stack));
        tooltip.add(Component.translatable("tooltip.create_refinery_n_reactors.spray.refill"));
        tooltip.add(Component.translatable(TOOLTIP + "swing", STEAM_PER_SWING));
        tooltip.add(Component.translatable(TOOLTIP + "guard"));
    }
}
