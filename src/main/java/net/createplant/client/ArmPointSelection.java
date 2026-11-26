package net.createplant.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.catnip.outliner.Outliner;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmPoints;
import net.createplant.registry.CEArm;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The points marked with the arm in hand before it is placed, outlined in Create's arm colours: blue to take, orange to put. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class ArmPointSelection {
    private static final int TAKE = 0x7FCDE0, PUT = 0xFFCB74;
    /** true for a place to put to. */
    private static final Map<BlockPos, Boolean> POINTS = new LinkedHashMap<>();
    private static Level level;

    private ArmPointSelection() { }

    public static void toggle(BlockPos p) {
        p = p.immutable();
        Boolean put = POINTS.get(p);
        String said;
        if (put == null) { POINTS.put(p, false); said = "input"; }
        else if (!put) { POINTS.put(p, true); said = "output"; }
        else { POINTS.remove(p); Outliner.getInstance().remove(p); said = "removed"; }
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(ArmPoints.KEY + said), true);
    }

    /** The arm was placed at {@code arm}: hand it the marks and start over. */
    public static void flush(BlockPos arm) {
        var in = new ArrayList<BlockPos>();
        var out = new ArrayList<BlockPos>();
        POINTS.forEach((p, put) -> (put ? out : in).add(p));
        clear();
        PacketDistributor.sendToServer(new ArmPoints(arm.immutable(), in, out));
    }

    private static void clear() {
        POINTS.keySet().forEach(Outliner.getInstance()::remove);
        POINTS.clear();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != level) { clear(); level = mc.level; }
        if (mc.player == null || !mc.player.isHolding(CEArm.ARM_ITEM.get())) {
            POINTS.keySet().forEach(Outliner.getInstance()::remove);
            return;
        }
        POINTS.forEach((p, put) -> Outliner.getInstance().showAABB(p, new AABB(p).deflate(.03)).colored(put ? PUT : TAKE).lineWidth(1 / 16f));
    }
}
