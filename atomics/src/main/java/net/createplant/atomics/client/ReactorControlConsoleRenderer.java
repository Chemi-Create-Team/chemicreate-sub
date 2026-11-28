package net.createplant.atomics.client;

import net.createplant.client.NixieStrip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import java.util.Locale;
import net.createplant.CreatePlant;
import net.createplant.atomics.nuclear.ReactorConsoleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.createplant.atomics.block.entity.ReactorConsoleDisplayBlockEntity;
import net.createplant.atomics.block.entity.ReactorControlConsoleBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/**
 * The moving desk controls with their paper charts, and, for a linked display board, the core's live top-down fuel temperatures and
 * rods, six analogue meters, the annunciator windows and three rows of scaled-down Create nixie tubes.
 */
public class ReactorControlConsoleRenderer extends SafeBlockEntityRenderer<ReactorControlConsoleBlockEntity> {
    private static final PartialModel LAMP_ROD = part("rod_lamp_white");
    private static final PartialModel LAMP_SELECTED = part("rod_lamp_selected");
    private static final PartialModel METER_NEEDLE = part("meter_needle"), GUARD = part("guard");
    private static final PartialModel TRACE_WATER = part("trace_water"), TRACE_GRAPHITE = part("trace_graphite");
    private static final PartialModel DIAL = part("dial"), SWITCH = part("mode_switch");
    /** Push-button caps only; dials, the mode switch and the paper have their own moving models or none. */
    private static final PartialModel[] BUTTONS = java.util.Arrays.stream(ReactorConsoleButton.values())
                                                      .map(button -> pushButton(button) ? part("button_" + button.name().toLowerCase(Locale.ROOT)) : null)
                                                      .toArray(PartialModel[] ::new);
    private static final PartialModel[] LAMPS = {part("lamp_grey"), part("lamp_red"), part("lamp_green")};
    private static final String KEY = "reactor_control_console.create_refinery_n_reactors.";
    private static final PartialModel[] FUEL_TEMPERATURE = new PartialModel[8];
    /** The glass set one pixel into the board face, and the depth of the nixie tubes behind it, in pixels; shared with the model generator. */
    private static final float BOARD_GLASS = 4, NIXIE_DEPTH = 2.5f;
    /** Needle pivot height in pixels of the meter window; shared with the model generator. */
    private static final float METER_PIVOT = 8.5f;
    private static final String[] METERS = {"power", "reactivity", "outlet", "flow", "void", "xenon"};
    private static final String[] METER_FORMATS = {"%.0f", "%+.2f", "%.0f", "%.0f", "%.1f", "%.0f"};
    private static final String[] NIXIES = {"fuel", "graphite", "rods"};

    static {
        for (int i = 0; i < FUEL_TEMPERATURE.length; i++) FUEL_TEMPERATURE[i] = part("fuel_lamp_" + i);
    }

    private static PartialModel part(String name) {
        return PartialModel.of(CreatePlant.location("block/reactor_console/" + name));
    }
    public static void init() {}
    public ReactorControlConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(
        ReactorControlConsoleBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isMaster() || !be.assembled())
            return;
        float yaw = (180 - be.facing().toYRot()) * Mth.DEG_TO_RAD;
        drawDesk(be, yaw, pose, buffers, light);
    }

    /** Draws the linked desk's live readings on a display board, in the board's own block space. */
    static void drawBoard(
        ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, PoseStack pose, MultiBufferSource buffers, int light) {
        float yaw = (180 - board.facing().toYRot()) * Mth.DEG_TO_RAD;
        drawMap(be, board, yaw, pose, buffers);
        drawMeters(be, board, yaw, pose, buffers, light);
        drawAnnunciator(be, board, yaw, pose, buffers, light);
        drawNixies(be, board, yaw, pose, buffers, light);
    }

    private static void drawMap(
        ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, float yaw, PoseStack pose, MultiBufferSource buffers) {
        boolean blinkOff = be.getLevel().getGameTime() % 20 >= 10;
        for (int packed : be.fuelMap()) {
            if ((packed & ReactorControlConsoleBlockEntity.LOW_FLOW_BIT) != 0 && blinkOff)
                continue;
            int temperature = packed >>> 16 & 255;
            drawMapMark(FUEL_TEMPERATURE[Math.min(7, temperature * 8 / 256)], be, board, packed & 255, packed >>> 8 & 255, yaw, pose, buffers);
        }
        int[] rods = be.rodMap();
        for (int i = 0; i < rods.length; i++) {
            int packed = rods[i];
            drawMapMark(LAMP_ROD, be, board, packed & 255, packed >>> 8 & 255, yaw, pose, buffers);
            if (i == be.selectedIndex())
                drawMapMark(LAMP_SELECTED, be, board, packed & 255, packed >>> 8 & 255, yaw, pose, buffers);
        }
    }

    private static void drawMeters(
        ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, float yaw, PoseStack pose, MultiBufferSource buffers, int light) {
        double nominal = Math.max(1, be.nominalWatts());
        double[] values = {be.power() / 1e6, be.reactivity() * 100, be.representativeOutletKelvin() - 273.15, be.totalFlow(),
            be.voidFraction() * 100, be.xenon() * 100};
        double[] min = {0, -10, 0, 0, 0, 0}, max = {nominal / 1e6, 10, 400, Math.max(1, nominal / 765e6 * 220_000), 100, 150};
        for (int i = 0; i < METERS.length; i++) {
            transformToPanel(board.getBlockPos(), meterPos(board, i), yaw, pose);
            pose.pushPose();
            pose.translate(.5, METER_PIVOT / 16d, 1 / 16d);
            float ratio = be.linked() ? (float) Math.clamp((values[i] - min[i]) / (max[i] - min[i]), 0, 1) : 0;
            pose.mulPose(Axis.ZP.rotation((-.72f + 1.44f * ratio) * Mth.PI));
            pose.translate(-.5, -METER_PIVOT / 16d, -1 / 16d);
            CachedBuffers.partial(METER_NEEDLE, be.getBlockState()).light(light).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
            pose.popPose();
            NixieStrip.draw(pose, buffers, Component.translatable(KEY + "panel." + METERS[i]).getString(), 1, 15, 13, 15.5f, BOARD_GLASS, NIXIE_DEPTH,
                DyeColor.ORANGE, light);
            String value = be.linked() ? String.format(Locale.ROOT, METER_FORMATS[i], values[i]) : "--";
            NixieStrip.draw(pose, buffers, value, 2, 14, 1, 4, BOARD_GLASS, NIXIE_DEPTH, DyeColor.ORANGE, light);
            pose.popPose();
        }
    }

    /** Meters fill the middle two rows of the three right-hand wall columns, top row first. */
    private static BlockPos meterPos(ReactorConsoleDisplayBlockEntity board, int index) {
        return board.wallPos(ReactorConsoleDisplayBlockEntity.MAP_SIZE + index % 3, 2 - index / 3);
    }

    private static void drawAnnunciator(
        ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, float yaw, PoseStack pose, MultiBufferSource buffers, int light) {
        for (int i = 0; i < 6; i++) {
            int alarm = i < 5 ? be.alarms() & 1 << i : be.linked() ? 1 : 0;
            boolean lit = be.lampTest() || alarm != 0;
            if (i < 5 && alarm != 0 && !be.acknowledged() && be.getLevel().getGameTime() % 20 >= 10)
                lit = false;
            transformToPanel(board.getBlockPos(), board.wallPos(ReactorConsoleDisplayBlockEntity.MAP_SIZE + i / 2, 3), yaw, pose);
            // Each annunciator is its name in tubes: dim grey when quiet, red (the link lamp green) when the condition is on.
            DyeColor colour = !lit ? DyeColor.GRAY : i == 5 ? DyeColor.LIME : DyeColor.RED;
            NixieStrip.draw(pose, buffers, Component.translatable(KEY + "alarm." + i).getString(), 1, 15, i % 2 == 0 ? 9 : 2, i % 2 == 0 ? 14 : 7,
                BOARD_GLASS, NIXIE_DEPTH, colour, light);
            pose.popPose();
        }
    }

    private static void drawNixies(
        ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, float yaw, PoseStack pose, MultiBufferSource buffers, int light) {
        double[] values = {be.hottestKelvin() - 273.15, be.graphiteKelvin() - 273.15, be.rodInsertion() * 100};
        for (int i = 0; i < NIXIES.length; i++) {
            transformToPanel(board.getBlockPos(), board.wallPos(ReactorConsoleDisplayBlockEntity.MAP_SIZE + i, 0), yaw, pose);
            String digits = be.linked() ? String.format(Locale.ROOT, "%4d", Math.clamp(Math.round(values[i]), 0, 9999)) : "    ";
            NixieStrip.draw(pose, buffers, Component.translatable(KEY + "panel." + NIXIES[i]).getString(), 1, 15, 11.5f, 14.5f, BOARD_GLASS, NIXIE_DEPTH,
                DyeColor.ORANGE, light);
            NixieStrip.draw(pose, buffers, digits, 1, 15, 2.5f, 9.5f, BOARD_GLASS, NIXIE_DEPTH, DyeColor.ORANGE, light);
            pose.popPose();
        }
    }

    private static void drawDesk(
        ReactorControlConsoleBlockEntity be, float yaw, PoseStack pose, MultiBufferSource buffers, int light) {
        for (var button : ReactorConsoleButton.values()) {
            transformToPanel(be.getBlockPos(), be.deskPos(button.column), yaw, pose);
            double x = 1 - button.u / 16d, y = surface(button.v) / 16d, z = button.v / 16d;
            if (button.dial() || button == ReactorConsoleButton.MODE) {
                int command = button == ReactorConsoleButton.MODE ? be.mode() * 50
                    : button == ReactorConsoleButton.ROD_DIAL ? Math.max(0, be.selectedCommand()) : be.controlTarget() < 0 ? be.bankTarget() : be.controlTarget();
                float degrees = button == ReactorConsoleButton.MODE ? (be.mode() - 1) * 45 : -135 + 2.7f * command;
                pose.translate(x, y, z);
                pose.mulPose(Axis.YP.rotationDegrees(-degrees));
                pose.translate(-.5, 0, -.5);
                CachedBuffers.partial(button.dial() ? DIAL : SWITCH, be.getBlockState()).light(light)
                    .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
            } else if (pushButton(button)) {
                if (be.pressed(button))
                    pose.translate(0, -.035, 0);
                CachedBuffers.partial(BUTTONS[button.ordinal()], be.getBlockState()).light(light)
                    .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
            }
            pose.popPose();
        }
        ReactorConsoleButton az5 = ReactorConsoleButton.AZ5;
        transformToPanel(be.getBlockPos(), be.deskPos(az5.column), yaw, pose);
        double hinge = 1 - ReactorConsoleButton.GUARD_HINGE_U / 16d, top = ReactorConsoleButton.GUARD_HINGE_Y / 16d;
        pose.translate(hinge, top, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(be.guardOpen() ? -90 : 0));
        pose.translate(-hinge, -top, 0);
        CachedBuffers.partial(GUARD, be.getBlockState()).light(light).renderInto(pose, buffers.getBuffer(RenderType.translucent()));
        pose.popPose();
        for (int i = 0; i < 3; i++) {
            boolean lit = be.lampTest() || be.mode() == i;
            lamp(be, yaw, pose, buffers, ReactorConsoleButton.MODE.column, 3 + 5 * i, 15, LAMPS[lit ? 2 : 0], lit ? LightTexture.FULL_BRIGHT : light);
        }
        boolean alarm = be.lampTest() || be.alarms() != 0 && (be.acknowledged() || be.getLevel().getGameTime() % 20 < 10);
        lamp(be, yaw, pose, buffers, ReactorConsoleButton.ACKNOWLEDGE.column, 13, 2.5, LAMPS[alarm ? 1 : 0], alarm ? LightTexture.FULL_BRIGHT : light);
        transformToPanel(be.getBlockPos(), be.deskPos(ReactorConsoleButton.CHART.column), yaw, pose);
        for (int chart = 0; chart < 2; chart++) {
            double left = chart == 0 ? 2 : 9;
            for (int i = 0; i < be.chartCount(); i++) {
                pose.pushPose();
                double value = (chart == 0 ? be.waterSample(i) : be.graphiteSample(i)) / 255d;
                double u = left + value * 5, v = 5.5 + i / (double) (ReactorControlConsoleBlockEntity.SAMPLES - 1) * 9.5;
                pose.translate(1 - u / 16d, 13.27 / 16d, v / 16d);
                CachedBuffers.partial(chart == 0 ? TRACE_WATER : TRACE_GRAPHITE, be.getBlockState()).light(light)
                    .renderInto(pose, buffers.getBuffer(RenderType.cutout()));
                pose.popPose();
            }
        }
        pose.popPose();
        drawDeskLabels(be, yaw, pose, buffers);
    }

    private static void drawDeskLabels(ReactorControlConsoleBlockEntity be, float yaw, PoseStack pose, MultiBufferSource buffers) {
        for (var button : ReactorConsoleButton.values()) {
            transformToPanel(be.getBlockPos(), be.deskPos(button.column), yaw, pose);
            Component text = Component.translatable(KEY + "label." + button.name().toLowerCase(Locale.ROOT));
            if (button == ReactorConsoleButton.ROD_DIAL)
                text = Component.translatable(KEY + "label.rod_dial").append(" " + (be.selectedDepth() < 0 ? "--" : be.selectedDepth() + "% > "
                    + (be.selectedCommand() < 0 ? Component.translatable(KEY + "cap_control").getString() : be.selectedCommand() + "%")));
            else if (button == ReactorConsoleButton.BANK_DIAL)
                text = Component.translatable(KEY + "label.bank_dial").append(" " + (be.controlTarget() < 0 ? "--" : be.controlTarget() + "%"));
            else if (button == ReactorConsoleButton.MODE)
                text = Component.translatable(KEY + "mode." + be.mode());
            double z = button == ReactorConsoleButton.CHART ? 15.4 : button.v - button.radius - .7;
            topText(text, 1 - button.u / 16d, (surface(Math.max(0, z)) + .05) / 16d, Math.max(.4, z) / 16d, fit(text, button.crane() ? .22 : .9, .0085f),
                button == ReactorConsoleButton.CHART ? 0xFF252B29 : 0xFFEDE8D0, pose, buffers);
            pose.popPose();
        }
        transformToPanel(be.getBlockPos(), be.deskPos(ReactorConsoleButton.CRANE_MODE.column), yaw, pose);
        var crane = be.craneModeLabel();
        double craneStatusDepth = 5.5;
        topText(crane, 1 - ReactorConsoleButton.CRANE_MODE.u / 16d, (surface(craneStatusDepth) + .05) / 16d,
            craneStatusDepth / 16d, fit(crane, .24, .0085f), 0xFFE7AC62, pose, buffers);
        pose.popPose();
        transformToPanel(be.getBlockPos(), be.deskPos(ReactorConsoleButton.CHART.column), yaw, pose);
        for (int chart = 0; chart < 2; chart++) {
            Component title = Component.translatable(KEY + (chart == 0 ? "panel.chart_outlet" : "panel.chart_graphite"));
            topText(title, 1 - (chart == 0 ? 4.5 : 11.5) / 16d, 13.3 / 16d, 4.9 / 16d, fit(title, .4, .006f), 0xFF252B29, pose, buffers);
        }
        pose.popPose();
    }

    private static void lamp(ReactorControlConsoleBlockEntity be, float yaw, PoseStack pose, MultiBufferSource buffers, int column, double u, double v,
        PartialModel model, int light) {
        transformToPanel(be.getBlockPos(), be.deskPos(column), yaw, pose);
        pose.translate(.5 - u / 16d, (surface(v) - 10) / 16d, v / 16d - .5);
        CachedBuffers.partial(model, be.getBlockState()).light(light).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        pose.popPose();
    }

    private static boolean pushButton(ReactorConsoleButton button) {
        return !button.dial() && button != ReactorConsoleButton.MODE && button != ReactorConsoleButton.CHART;
    }

    /** Height in pixels of the stepped desk top at a depth measured from the operator's edge; shared with the model generator. */
    private static double surface(double v) {
        return 10 + Math.min(3, (int) (v / 4)) + .15;
    }

    /** The given scale, reduced so that the text fits within {@code width} blocks. */
    private static float fit(Component text, double width, float scale) {
        return (float) Math.min(scale, width / Math.max(1, Minecraft.getInstance().font.width(text)));
    }

    private static void topText(
        Component text, double x, double y, double z, float scale, int colour, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        text(text, scale, colour, pose, buffers);
        pose.popPose();
    }
    private static void text(Component text, float scale, int colour, PoseStack pose, MultiBufferSource buffers) {
        var font = Minecraft.getInstance().font;
        pose.scale(-scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -3.5f, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
            LightTexture.FULL_BRIGHT);
    }

    private static void drawMapMark(PartialModel model, ReactorControlConsoleBlockEntity be, ReactorConsoleDisplayBlockEntity board, int u, int v,
        float yaw, PoseStack pose,
        MultiBufferSource buffers) {
        float screenX = ReactorConsoleDisplayBlockEntity.mapBlocks(u), screenY = ReactorConsoleDisplayBlockEntity.mapBlocks(v);
        int column = Math.min(ReactorConsoleDisplayBlockEntity.MAP_SIZE - 1, (int) screenX);
        int row = Math.min(ReactorConsoleDisplayBlockEntity.MAP_SIZE - 1, (int) screenY);
        transformToPanel(board.getBlockPos(), board.wallPos(column, row), yaw, pose);
        pose.translate(1 - (screenX - column) - .5f, screenY - row - .5f, 0);
        CachedBuffers.partial(model, be.getBlockState()).light(LightTexture.FULL_BRIGHT).renderInto(pose, buffers.getBuffer(RenderType.cutout()));
        pose.popPose();
    }

    private static void transformToPanel(BlockPos origin, BlockPos panel, float yaw, PoseStack pose) {
        pose.pushPose();
        pose.translate(panel.getX() - origin.getX(), panel.getY() - origin.getY(), panel.getZ() - origin.getZ());
        rotatePanel(pose, yaw);
    }

    private static void rotatePanel(PoseStack pose, float yaw) {
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.translate(-.5, -.5, -.5);
    }

    @Override
    public boolean shouldRenderOffScreen(ReactorControlConsoleBlockEntity be) {
        return be.isMaster();
    }
    @Override
    public int getViewDistance() {
        return 64;
    }
}
