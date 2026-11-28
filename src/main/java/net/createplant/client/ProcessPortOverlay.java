package net.createplant.client;

import com.mojang.math.Axis;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.createplant.block.ProcessPorts;
import net.createplant.block.ProcessPorts.Kind;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.event.ProcessPortInteraction;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.transport.ContainmentWear;
import net.createplant.transport.HoseSocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class ProcessPortOverlay {
    private static final List<Class<?>> HATCH_WITHOUT_PROMPT = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** A block entity whose hatch port the player does not operate, so the overlay shows no prompt for it. */
    public static void hatchWithoutPrompt(Class<?> type) { HATCH_WITHOUT_PROMPT.add(type); }

    private static final Direction[] SIDES = Direction.values();
    private static final int PORT_RANGE = 12;
    private record Port(BlockPos pos, Direction face, Kind kind, ItemStack displayed) { }
    private static List<Port> ports = List.of();
    private static List<HoseSocket.Endpoint> hoses = List.of();
    private static ClientLevel scanned;
    private static boolean toolsHeld;
    private static Component currentPrompt;

    private ProcessPortOverlay() { }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ports = List.of();
            hoses = List.of();
            scanned = null;
            currentPrompt = null;
            return;
        }
        currentPrompt = mc.screen == null && !mc.options.hideGui && mc.options.getCameraType().isFirstPerson() ? prompt(mc) : null;
        boolean held = tool(mc.player.getMainHandItem()) || tool(mc.player.getOffhandItem());
        if (scanned == mc.level && held == toolsHeld && mc.player.tickCount % 8 != 0) return;
        scanned = mc.level;
        toolsHeld = held;
        var nearby = new ArrayList<Port>();
        var links = new ArrayList<HoseSocket.Endpoint>();
        BlockPos origin = mc.player.blockPosition();
        int reach = HoseSocket.MAX_DISTANCE + 5;
        for (int x = (origin.getX() - reach) >> 4; x <= (origin.getX() + reach) >> 4; x++) {
            for (int z = (origin.getZ() - reach) >> 4; z <= (origin.getZ() + reach) >> 4; z++) {
                var chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof HoseSocket.Endpoint hose && hose.hose().connected() && HoseRenderer.bounds(hose).inflate(5).contains(mc.player.getEyePosition())
                        && (hose.hose().peer() == null || be.getBlockPos().compareTo(hose.hose().remote()) < 0)) links.add(hose);
                    if (be.getBlockPos().distSqr(origin) > PORT_RANGE * PORT_RANGE || !ProcessPorts.plant(be)) continue;
                    for (var face : SIDES) {
                        var kind = ProcessPorts.at(be, face);
                        if (kind == null || !held && kind != Kind.HATCH) continue;
                        var display = kind == Kind.HATCH && !(be instanceof ReactionTowerBlockEntity || be instanceof ElectrodeHeadBlockEntity)
                            ? ProcessPorts.shownItem(be, face) : ItemStack.EMPTY;
                        if (held || !display.isEmpty()) nearby.add(new Port(be.getBlockPos(), face, kind, display.copy()));
                    }
                }
            }
        }
        ports = List.copyOf(nearby);
        hoses = List.copyOf(links);
    }

    private static boolean tool(ItemStack stack) {
        return stack.is(AllItems.WRENCH.get()) || stack.is(CEPressureInfrastructure.HOSE.get());
    }

    public static void drawPorts(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ports.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.lines();
        var consumer = buffers.getBuffer(type);
        var pose = event.getPoseStack();
        for (var port : ports) {
            if (port.pos().distToCenterSqr(camera.x, camera.y, camera.z) > PORT_RANGE * PORT_RANGE) continue;
            var at = HoseSocket.mouth(port.pos(), port.face());
            double x = port.face().getAxis() == Direction.Axis.X ? .025 : .275;
            double y = port.face().getAxis() == Direction.Axis.Y ? .025 : .275;
            double z = port.face().getAxis() == Direction.Axis.Z ? .025 : .275;
            var bounds = AABB.ofSize(at, x * 2, y * 2, z * 2);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds.inflate(.05))) continue;
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            if (toolsHeld) {
                int color = switch (port.kind()) {
                    case INLET -> 0x56AFFF;
                    case OUTLET, HOSE -> 0xFF923D;
                    case SHAFT -> 0xF2D65D;
                    case HATCH, DRAIN -> 0x60CA86;
                };
                LevelRenderer.renderLineBox(pose, consumer, bounds, (color >> 16) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, .65f);
            }
            if (!port.displayed().isEmpty()) {
                pose.translate(at.x, at.y + .13, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-event.getCamera().getYRot()));
                pose.scale(.39f, .39f, .39f);
                int light = LevelRenderer.getLightColor(mc.level, port.pos().relative(port.face()));
                mc.getItemRenderer().renderStatic(port.displayed(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, port.pos().hashCode());
            }
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    public static void drawPrompt(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        var prompt = currentPrompt;
        if (prompt == null) return;
        var graphics = event.getGuiGraphics();
        int width = mc.font.width(prompt);
        boolean goggles = GogglesItem.isWearingGoggles(mc.player);
        int desiredX = graphics.guiWidth() / 2 + (goggles ? -width / 2 : 8);
        int x = Math.max(3, Math.min(desiredX, graphics.guiWidth() - width - 4));
        int y = graphics.guiHeight() / 2 + (goggles ? 36 : 9);
        graphics.fill(x - 2, y - 2, x + width + 3, y + 10, 0xB51B2025);
        graphics.drawString(mc.font, prompt, x, y, 0xFFE3EDF0, false);
    }

    private static Component prompt(Minecraft mc) {
        var key = ChemicalReadout.KEY;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var be = mc.level.getBlockEntity(hit.getBlockPos());
            var warning = ContainmentWear.warning(be);
            if (warning != null) return warning;
            if (be instanceof HoseSocket.Endpoint hose && hose.hoseFace() == hit.getDirection()) return hose(hose);
            var kind = be == null ? null : ProcessPorts.at(be, hit.getDirection());
            if (kind == Kind.HATCH) {
                if (be instanceof ElectrodeHeadBlockEntity head) return Component.translatable(key + (!head.raised() ? "electrodes_raised"
                    : ProcessPorts.shownItem(be, hit.getDirection()).isEmpty() ? "electrodes_insert" : "electrodes_take"));
                if (HATCH_WITHOUT_PROMPT.stream().anyMatch(type -> type.isInstance(be))) return null;
                if (be instanceof ReactionTowerBlockEntity part) {
                    var tower = part.owner();
                    if (tower == null) return null;
                    if (!tower.hatchOpen()) return Component.translatable(key + "hatch_open");
                    return Component.translatable(key + (tower.topCatalyst().isEmpty() ? "catalyst_insert" : "catalyst_take"));
                }
                if (be instanceof LiddedVesselBlockEntity part && part.getControllerBE() != null && !part.getControllerBE().open()) return Component.translatable(key + "hatch_open");
                return Component.translatable(key + (ProcessPorts.shownItem(be, hit.getDirection()).isEmpty() ? "feed_insert" : "feed_take"));
            }
            if ((kind == Kind.DRAIN || kind == Kind.OUTLET)
                && ProcessPortInteraction.exposed(be, hit.getDirection())) {
                if (kind == Kind.DRAIN || !ProcessPortInteraction.purge(be, hit.getDirection(), FluidAction.SIMULATE).isEmpty())
                    return Component.translatable(key + "drain");
            }
        }
        if (hoses.isEmpty()) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = mc.hitResult != null ? mc.hitResult.getLocation() : eye.add(mc.player.getLookAngle().scale(mc.player.blockInteractionRange()));
        HoseSocket.Endpoint nearest = null;
        double distance = eye.distanceToSqr(end);
        for (var hose : hoses) {
            if (!hose.hose().connected()) continue;
            var bounds = HoseRenderer.bounds(hose);
            if (!bounds.contains(eye) && bounds.clip(eye, end).isEmpty()) continue;
            var a = HoseSocket.mouth(hose.hose().position(), hose.hoseFace());
            var b = HoseSocket.mouth(hose.hose().remote(), hose.hose().remoteFace());
            int slices = Math.max(4, (int) Math.ceil(a.distanceTo(b) * 6));
            for (int i = 0; i <= slices; i++) {
                var point = HoseRenderer.point(a, b, i / (double) slices);
                var found = AABB.ofSize(point, .19, .19, .19).clip(eye, end);
                if (found.isPresent() && eye.distanceToSqr(found.get()) < distance) {
                    distance = eye.distanceToSqr(found.get());
                    nearest = hose;
                }
            }
        }
        return nearest == null ? null : hose(nearest);
    }

    private static Component hose(HoseSocket.Endpoint endpoint) {
        var key = ChemicalReadout.KEY;
        var hose = endpoint.hose();
        if (!hose.connected()) return Component.translatable(key + "hose_connect");
        var fluid = hose.measuredFluid();
        return fluid.isEmpty() ? Component.translatable(key + "hose_idle")
            : Component.translatable(key + "hose_rate", ChemicalReadout.name(fluid), String.format(Locale.ROOT, "%,.1f", hose.measuredRate()));
    }
}
