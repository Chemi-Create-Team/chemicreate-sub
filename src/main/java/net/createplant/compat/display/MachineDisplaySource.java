package net.createplant.compat.display;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** One chosen measurement, using Create's native targets, settings packets and bar rendering. */
public class MachineDisplaySource extends PercentOrProgressBarDisplaySource {
    private String metric(DisplayLinkContext context) {
        int index=context.sourceConfig().getInt("Metric");
        return index<0 || index>=MachineReadings.KEYS.size() ? "" : MachineReadings.KEYS.get(index);
    }

    private MachineReadings.Reading reading(DisplayLinkContext context) {
        return MachineReadings.read(context.getSourceBlockEntity(),metric(context),context.blockEntity().getDirection().getOpposite());
    }

    @Override protected Float getProgress(DisplayLinkContext context) {
        var r=reading(context);
        return r==null || r.maximum()<=0 ? null : (float)(r.value()/r.maximum());
    }
    @Override protected boolean progressBarActive(DisplayLinkContext context) {
        var r=reading(context);
        return context.sourceConfig().getInt("Mode")==2 && r!=null && r.maximum()>0;
    }
    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats) {
        if (metric(context).equals("process_name") && MachineReadings.owner(context.getSourceBlockEntity()) instanceof net.createplant.block.entity.GasCentrifugeBlockEntity c) return c.processName().copy();
        if (metric(context).equals("dispatch_order") && context.getSourceBlockEntity() instanceof net.createplant.block.entity.IndustrialDispatchBlockEntity dispatch) return dispatch.orderDescription().copy();
        if (metric(context).equals("exchange_mode")) return MachineReadings.exchangeMode(context.getSourceBlockEntity()).copy();
        if (metric(context).equals("status")) return MachineReadings.status(context.getSourceBlockEntity()).copy();
        var r=reading(context);
        if (r==null || !Double.isFinite(r.value())) return Component.literal("--");
        if (context.sourceConfig().getInt("Mode")!=0) return r.maximum()>0 ? super.provideLine(context,stats) : Component.literal("--");
        return Component.literal(String.format(Locale.ROOT,"%.2f",r.value()).replaceAll("\\.?0+$",""));
    }
    @Override protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
        return (metric(context).equals("process_name") || metric(context).equals("status") || metric(context).equals("exchange_mode") || metric(context).equals("dispatch_order")) ? "MachineStatus" : super.getFlapDisplayLayoutName(context);
    }
    @Override protected FlapDisplaySection createSectionForValue(DisplayLinkContext context,int size) {
        return (metric(context).equals("process_name") || metric(context).equals("status") || metric(context).equals("exchange_mode") || metric(context).equals("dispatch_order")) ? new FlapDisplaySection(size*FlapDisplaySection.MONOSPACE,"alphabet",false,false)
            : super.createSectionForValue(context,size);
    }
    @Override protected boolean allowsLabeling(DisplayLinkContext context) { return true; }
    @Override public int getPassiveRefreshTicks() { return 20; }

    @Override @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context,ModularGuiLineBuilder builder,boolean first) {
        if (first) {
            builder.addSelectionScrollInput(0,137,(input,label)->input.forOptions(MachineReadings.KEYS.stream()
                .map(k->(Component)Component.translatable("display.create_refinery_n_reactors."+k)).toList())
                .titled(Component.translatable("display.create_refinery_n_reactors.metric")),"Metric");
        } else {
            builder.addSelectionScrollInput(0,60,(input,label)->input.forOptions(List.of(Component.translatable("display.create_refinery_n_reactors.number"),
                Component.translatable("display.create_refinery_n_reactors.percent"),Component.translatable("display.create_refinery_n_reactors.bar")))
                .titled(Component.translatable("display.create_refinery_n_reactors.format")),"Mode");
            builder.addTextInput(64,73,(input,label)->label.withTooltip(List.of(Component.translatable("create.display_source.label"))),"Label");
        }
    }
}
