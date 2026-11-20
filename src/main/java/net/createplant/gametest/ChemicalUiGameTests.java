package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ProcessPorts;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.createplant.chemistry.FluidChemicalRegistry.ChemicalHazard;
import net.createplant.event.ProcessPortInteraction;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.thermal.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalUiGameTests {
    @GameTest(template = "test_fractionation")
    public static void everyRegisteredPlantFluidHasAChemicalLabel(GameTestHelper h) {
        for (var entry : BuiltInRegistries.FLUID.entrySet()) {
            var id = entry.getKey().location();
            if (id.getNamespace().equals(CreatePlant.MOD_ID)) {
                var info = FluidChemicalRegistry.base(entry.getValue());
                h.assertTrue(info != null && !info.formula().isBlank() && !info.formula().matches(".*[0-9].*"), "Chemical information is missing or not subscripted for " + id);
            }
        }
        for (var fuel : java.util.List.of(CEPetroFluids.CRUDE_OIL.get(), CEPetroFluids.DIESEL.get(), CEPetroFluids.GASOLINE.get()))
            h.assertTrue(FluidChemicalRegistry.base(fuel) != null, "Refinery fuels supplied by CDG have formulas");
        var acid = new FluidStack(CEUranium.SULFURIC_ACID.get(), 1);
        var info = FluidChemicalRegistry.of(acid);
        h.assertTrue(info.formula().equals("H₂SO₄") && info.hazards().contains(ChemicalHazard.CORROSIVE), "Formula and in-game hazard are separate registered data");
        var bar = ChemicalReadout.heat(Grade.Heat.COLD, Grade.Heat.HOT, Grade.Heat.VERY_HOT).getString();
        h.assertTrue(bar.contains("□ ◆ □ ■ ■") && bar.contains("2"), "Current stage markers remain distinct from the valid range");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aBareHandPurgesOnlyAnExposedDrain(GameTestHelper h) {
        var relative = new BlockPos(5, 4, 5);
        var pos = h.absolutePos(relative);
        h.setBlock(relative, CETransport.LIDDED_VESSEL.get());
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(relative);
        vessel.getTankInventory().setFluid(new FluidStack(Fluids.WATER, 700));
        h.assertTrue(ProcessPorts.at(vessel, Direction.DOWN) == ProcessPorts.Kind.DRAIN, "The down-facing physical port is identified as a drain");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, -.5, 0), Direction.DOWN, pos, false);
        var blocked = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        h.setBlock(relative.below(), Blocks.STONE);
        ProcessPortInteraction.use(blocked);
        h.assertTrue(!blocked.isCanceled() && vessel.getTankInventory().getFluidAmount() == 700, "A covered drain is never purged");
        h.setBlock(relative.below(), Blocks.AIR);
        var exposed = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        ProcessPortInteraction.use(exposed);
        h.assertTrue(exposed.isCanceled() && vessel.getTankInventory().isEmpty(), "An exposed drain clears the real liquid precisely once");
        h.succeed();
    }
}
