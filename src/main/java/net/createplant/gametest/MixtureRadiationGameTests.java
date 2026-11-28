package net.createplant.gametest;

import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.mixture.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.gametest.MixtureGameTests.*;
import io.netty.buffer.Unpooled;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CERadiation;
import net.createplant.thermal.FluidState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class MixtureRadiationGameTests {
    @GameTest(template = "test_empty")
    public static void mixtureKeepsRadioactiveMaterialAndHeatDataDuringSplitAndReload(GameTestHelper h) {
        var radio = new FluidStack(BuiltInRegistries.FLUID.get(CreatePlant.location("high_level_raffinate")), 667);
        long now = h.getLevel().getGameTime();var isotope = new net.createplant.nuclear.Radionuclides(10, 20, 30, now);
        radio.set(CERadiation.MATERIAL.get(), isotope);net.createplant.thermal.FluidState.setTemperature(radio, 12);
        var tank = new MixtureFluidTank(1000, false, () -> {});tank.fill(new FluidStack(Fluids.WATER, 333), EXECUTE);tank.fill(radio, EXECUTE);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        FluidStack.STREAM_CODEC.encode(buffer, tank.getFluid());var synchronizedStack = FluidStack.STREAM_CODEC.decode(buffer);buffer.release();
        h.assertTrue(FluidStack.matches(tank.getFluid(), synchronizedStack), "Full network stack preserves both component amounts and isotope/heat patches");
        var before = net.createplant.nuclear.RadiationSources.fluid(tank.getFluid(), now);
        var saved = tank.writeToNBT(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());var reloaded = new MixtureFluidTank(1000, false, () -> {});reloaded.readFromNBT(h.getLevel().registryAccess(), saved);
        var a = reloaded.drain(317, EXECUTE);var b = reloaded.getFluid().copy();
        var after = net.createplant.nuclear.RadiationSources.fluid(a, now).plus(net.createplant.nuclear.RadiationSources.fluid(b, now));
        h.assertTrue(Math.abs(before.activity() - after.activity()) < 1e-8, "Splitting and saving do not dilute away isotope inventory");
        for (var part : MixtureHelper.components(b)) if (part.is(radio.getFluid())) h.assertTrue(part.get(CERadiation.MATERIAL.get()).equals(isotope) && net.createplant.thermal.FluidState.temperature(part) == 12, "Constituent metadata survives carrier serialization");h.succeed();
    }

}
