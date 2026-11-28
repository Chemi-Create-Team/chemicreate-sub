package net.createplant.atomics.item;

import net.createplant.atomics.registry.CERadiation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public class RadiationMeterItem extends Item {
    private final boolean survey;

    public RadiationMeterItem(boolean survey, Properties properties) {
        super(properties);
        this.survey = survey;
    }

    public static double rate(CompoundTag data, boolean beta) {
        return data.getDouble("GammaSvS") + (beta ? data.getDouble("AmbientBetaSvS") : 0);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4g", value);
    }

    public static Component reading(ItemStack stack, CompoundTag data) {
        if (!data.getBoolean("Migrated"))
            return Component.translatable("radiation.meter.unmeasured");
        boolean beta = stack.getOrDefault(CERadiation.BETA_WINDOW.get(), false);
        if (stack.is(CERadiation.SURVEY_METER.get()))
            return Component.translatable(
                    "radiation.meter.survey",
                    beta ? "β+γ" : "γ",
                    number(rate(data, beta) * 3600 * 1000));
        return Component.translatable(
                "radiation.meter.dosimeter",
                number(data.getDouble("LifetimeSv") * 1000),
                number(
                        (data.getDouble("GammaSvS")
                                        + data.getDouble("BetaSvS")
                                        + data.getDouble("NeutronSvS")
                                        + data.getDouble("InternalSvS"))
                                * 3600
                                * 1000));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            if (survey)
                stack.set(
                        CERadiation.BETA_WINDOW.get(),
                        !stack.getOrDefault(CERadiation.BETA_WINDOW.get(), false));
            update(server);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void update(ServerPlayer player) {
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof RadiationMeterItem)) continue;
            player.displayClientMessage(reading(stack, player.getData(CERadiation.EXPOSURE)), true);
            break;
        }
    }

    public static double clickProbability(double sievertsPerSecond) {
        return 1 - Math.exp(-Math.max(0, sievertsPerSecond) * 1e6 / 5);
    }

    public static void clickTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 4 != 0)
            return;
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (!stack.is(CERadiation.SURVEY_METER.get())) continue;
            var data = player.getData(CERadiation.EXPOSURE);
            if (player.getRandom().nextDouble()
                    < clickProbability(
                            rate(data, stack.getOrDefault(CERadiation.BETA_WINDOW.get(), false))))
                player.playNotifySound(
                        SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, .2f, 1.8f);
            break;
        }
    }

    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof RadiationMeterItem meter)) return;
        var data =
                event.getEntity() == null
                        ? new CompoundTag()
                        : event.getEntity().getData(CERadiation.EXPOSURE);
        meter.appendReadingTooltip(stack, data, event.getToolTip());
    }

    private void appendReadingTooltip(ItemStack stack, CompoundTag data, List<Component> tooltip) {
        tooltip.add(reading(stack, data));
        tooltip.add(
                Component.translatable(
                        "radiation.meter.committed", number(data.getDouble("CommittedSv") * 1000)));
        tooltip.add(
                Component.translatable(
                        "radiation.meter.risk",
                        number(Math.clamp(data.getDouble("LifetimeSv") * 5, 0, 100))));
        if (survey) tooltip.add(Component.translatable("radiation.meter.toggle"));
    }
}
