package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.compat.display.MachineReadings;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEDisplays;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class DisplayIntegrationGameTests {
    @GameTest(template="test_fractionation")
    public static void thermalRowsExposeIndependentTemperaturesAndBoundedFlow(GameTestHelper h) {
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,2,6)),Direction.SOUTH,2);
        be.thermal().tank(1).fill(new FluidStack(Fluids.WATER,100),EXECUTE);be.thermal().tank(3).fill(new FluidStack(Fluids.WATER,300),EXECUTE);
        h.assertTrue(!MachineReadings.KEYS.contains("temperature_1") && MachineReadings.read(be,"temperature_1")==null,"Retired fluid temperature metrics are absent");
        h.assertTrue(MachineReadings.read(be,"fluid_1").value()==100 && MachineReadings.read(be,"fluid_3").value()==300,"Independent row sampling preserves finite stocks");
        h.assertTrue(MachineReadings.owner(h.getLevel().getBlockEntity(be.members().get(5)))==be,"Bundle links resolve the live controller");
        for(boolean shift:new boolean[]{false,true}){var lines=new java.util.ArrayList<net.minecraft.network.chat.Component>();h.assertTrue(be.addToGoggleTooltip(lines,shift) && !lines.isEmpty(),"Grades and throughput show on the goggles");}h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void temperaturesAndInvalidSelectionsNeverInventPercentagesOrConsumeFluid(GameTestHelper h) {
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,2,6)),Direction.SOUTH,2);
        be.thermal().tank(1).fill(FluidThermal.at(new FluidStack(Fluids.WATER,100),350),EXECUTE);
        var pos=be.getBlockPos().below();
        h.getLevel().setBlock(pos,AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING,Direction.DOWN),3);
        var link=(DisplayLinkBlockEntity)h.getLevel().getBlockEntity(pos);link.activeSource=CEDisplays.MACHINE.get();
        var context=new DisplayLinkContext(h.getLevel(),link);var stats=new DisplayTargetStats(1,20,null);
        link.getSourceConfig().putInt("Metric",MachineReadings.KEYS.indexOf("fluid_1"));
        var text=link.activeSource.provideText(context,stats);
        h.assertTrue(text.size()==1 && text.getFirst().getString().equals("100"),"A native link reads the actual row inventory");
        for (int mode=1;mode<=2;mode++) {
            link.getSourceConfig().putInt("Mode",mode);
            h.assertTrue(!link.activeSource.provideText(context,stats).getFirst().getString().equals("--"),"A bounded inventory supports native percentage and bar modes");
        }
        link.getSourceConfig().putInt("Metric",Integer.MAX_VALUE);
        h.assertTrue(link.activeSource.provideText(context,stats).getFirst().getString().equals("--"),"An invalid saved selection is safely unmeasured");
        h.assertTrue(be.thermal().tank(1).getFluidAmount()==100,"Reading never changes fluid or heat");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void gogglesKeepEveryUsefulLineWithoutPadding(GameTestHelper h) {
        var lines=new java.util.ArrayList<net.minecraft.network.chat.Component>();
        for(int i=0;i<5;i++) lines.add(net.minecraft.network.chat.Component.literal("value "+i));
        lines.add(net.minecraft.network.chat.Component.literal(" "));
        MachineReadings.compact(lines);h.assertTrue(lines.size()==5,"Every useful row survives compaction and blank rows are dropped");
        lines.subList(2,5).clear();MachineReadings.compact(lines);
        h.assertTrue(lines.size()==2,"Short summaries are never padded");h.succeed();
    }

}
