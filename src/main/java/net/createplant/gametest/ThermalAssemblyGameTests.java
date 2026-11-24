package net.createplant.gametest;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThermalAssemblyGameTests {
    @GameTest(template="test_fractionation")
    public static void removedThermalRegistrationsAreAbsent(GameTestHelper h) {
        for(var name:new String[]{"industrial_heater","industrial_heater_burner","industrial_heater_chimney","heat_exchanger_support","heat_exchanger_casing","heat_exchanger_cover","heat_exchanger_tubes","reactor_preheater_core"}) {
            var id=CreatePlant.location(name);
            h.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id) && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id),"Removed equipment has no hidden block or item registration: "+id);
        }
        h.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(CreatePlant.location("reactor_fluid_port")),"The old jacket endpoint entity is removed");
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void bothExchangersKeepTheirTwoStreamsSeparateInEveryDirection(GameTestHelper h) {
        int index=0;
        for(var kind:ExchangerStructure.Kind.values()) for(var along:Direction.Plane.HORIZONTAL) {
            var be=ThermalControlGameTests.exchanger(h.getLevel(),h.absolutePos(new BlockPos(4+index%4*8,2,4+index/4*8)),along,kind.min+1,kind);index++;
            var ports=new net.neoforged.neoforge.fluids.capability.IFluidHandler[]{ThermalControlGameTests.tube(be,0),ThermalControlGameTests.tube(be,1),
                ThermalControlGameTests.shell(be,1),ThermalControlGameTests.shell(be,0)};
            for(var port:ports) h.assertTrue(port!=null,"Tube and shell each have a real inlet and outlet: "+kind+" "+along);
            var cold=net.createplant.thermal.FluidThermal.at(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,100),300);
            // A gas that stays a gas at the water's temperature (olefins would condense, and 100 mB is less than one liquid unit).
            var hot=net.createplant.thermal.FluidThermal.at(new net.neoforged.neoforge.fluids.FluidStack(net.createplant.registry.CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get(),100),360);
            var execute=net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
            var tubeSide=index%2==0 ? cold : hot;var shellSide=index%2==0 ? hot : cold;
            ports[0].fill(tubeSide,execute);ports[2].fill(shellSide,execute);
            h.assertTrue(ports[0].drain(100,execute).isEmpty() && ports[1].fill(cold,execute)==0,"Ports enforce one-way transfer");
            for(int tick=0;tick<5;tick++) be.tickProcess();
            var c=ports[1].drain(100,execute);var t=ports[3].drain(100,execute);
            var water=net.minecraft.world.level.material.Fluids.WATER;var olefins=net.createplant.registry.CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get();
            h.assertTrue(c.is(index%2==0 ? water : olefins) && t.is(index%2==0 ? olefins : water) && c.getAmount()==100 && t.getAmount()==100,"The tube and shell sides do not mix or lose fluid: "+kind+" "+along+" "+c+" / "+t);

        }
        h.succeed();
    }
}
