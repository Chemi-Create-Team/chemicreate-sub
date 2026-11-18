package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.TankFootprint;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.registry.*;
import net.createplant.transport.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Salt from the sun, brine electrolysed into hydrogen, chlorine and caustic soda, and the PVC pipe. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ChlorAlkaliGameTests {
    private record BrineRun(int soda, int asbestos) { }

    private static BrineRun runBrine(GameTestHelper h, BlockPos origin, boolean diaphragm) {
        var owner = ElectrolysisCellGameTests.assemble(h.getLevel(), h.absolutePos(origin), 2, Direction.NORTH);
        ElectrolysisCellGameTests.load(owner, 32);
        var membrane = owner.heads().getFirst();
        if (diaphragm) membrane.electrodes.insertItem(2, CEItems.ASBESTOS.toStack(), false);
        owner.setRunning(true);ElectrolysisCellGameTests.lower(owner);
        int supplied = 0, soda = 0;
        for (int tick = 0; tick < 300; tick++) {
            supplied += ElectrolysisCellGameTests.feed(owner, new FluidStack(CEChlorAlkali.BRINE.get(), 10000 - supplied));
            ElectrolysisCellGameTests.run(owner, 1, 400);
            for (var cell : owner.cells()) soda += cell.tanks.get(3).drain(Integer.MAX_VALUE, EXECUTE).getAmount();
        }
        h.assertTrue(supplied == 10000 && owner.stored(1) == 5000 && owner.stored(2) == 5000, "10000 mB brine conserves both gases");
        return new BrineRun(soda, membrane.electrodes.getStackInSlot(2).getCount());
    }

    @GameTest(template = "test_petrochemical")
    public static void theDiaphragmKeepsItsWearWhenTheStackReloadsAndReforms(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);
        var head = owner.heads().getFirst();
        var asbestos = CEItems.ASBESTOS.toStack();asbestos.set(CEChlorAlkali.DIAPHRAGM_WEAR.get(), 9998);
        head.electrodes.insertItem(2, asbestos, false);
        var lookup = h.getLevel().registryAccess();head.loadWithComponents(head.saveWithoutMetadata(lookup), lookup);
        owner.invalidateMachine();owner.validate();
        ElectrolysisCellGameTests.feed(owner, new FluidStack(CEChlorAlkali.BRINE.get(), 2));ElectrolysisCellGameTests.run(owner, 1, 400);
        h.assertTrue(owner.stored(3) == 3 && head.electrodes.getStackInSlot(2).isEmpty(), "Reload and reform retain the carried quarter; the last two mB consume the membrane at exactly 10000 mB");h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void brineGivesHydrogenChlorineAndCausticSoda(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);
        ElectrolysisCellGameTests.feed(owner, new FluidStack(CEChlorAlkali.BRINE.get(), 300));ElectrolysisCellGameTests.run(owner, 100, 400);
        h.assertTrue(owner.stored(0) == 100 && owner.stored(1) == 100 && owner.stored(2) == 100 && owner.stored(3) == 200
            && owner.fluid(2).is(CEChlorAlkali.CHLORINE.get()), "Brine 2:1:1:2 ledger: " + owner.stored(0) + "/" + owner.stored(1) + "/" + owner.stored(2) + "/" + owner.stored(3) + ", " + owner.status());h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void asbestosDiaphragmRaisesOnlyCausticOutputAndWearsAfterTenBuckets(GameTestHelper h) {
        BrineRun plain = runBrine(h, new BlockPos(2, 1, 2), false);
        BrineRun diaphragm = runBrine(h, new BlockPos(2, 1, 8), true);
        h.assertTrue(plain.soda() == 10000, "Without a diaphragm, 10000 mB brine makes 10000 mB caustic soda: " + plain.soda());
        h.assertTrue(diaphragm.soda() == 12500, "An asbestos diaphragm raises the caustic soda output by one quarter: " + diaphragm.soda());
        h.assertTrue(diaphragm.asbestos() == 0, "One asbestos is consumed after 10000 mB of brine");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 400)
    public static void seaWaterDriesIntoSaltOnASunnyCoast(GameTestHelper h) {
        BlockPos origin = new BlockPos(2, 2, 2);int diameter = 16;
        for (int x = 0; x < diameter; x++) for (int z = 0; z < diameter; z++) {
            if (!TankFootprint.contains(x, z, diameter)) continue;
            h.setBlock(origin.offset(x, 0, z), CEBlocks.TANK_CASING.get());
            if (TankFootprint.isWall(x, z, diameter)) for (int y = 1; y <= 2; y++) h.setBlock(origin.offset(x, y, z), CEBlocks.TANK_CASING.get());
        }
        ServerLevel level = h.getLevel();
        BlockPos low = h.absolutePos(origin), high = h.absolutePos(origin.offset(diameter - 1, 4, diameter - 1));
        String biome = level.getBiome(low).unwrapKey().orElseThrow().location().toString();
        long time = level.getDayTime();
        fillBiome(level, low, high, "minecraft:beach");
        h.onEachTick(() -> {level.setDayTime(6000);level.setRainLevel(0);level.setThunderLevel(0);});
        BlockPos wall = origin.offset(0, 1, diameter / 2);
        h.runAfterDelay(3, () -> h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(wall), Direction.WEST).fill(new FluidStack(Fluids.WATER, 4000), EXECUTE) == 4000,
            "The formed tank takes the sea water"));
        h.runAfterDelay(320, () -> {
            var tank = ((TankCasingBlockEntity)h.getBlockEntity(wall)).getTank();
            int water = tank.getFluidAmount(), salt = tank.getSalt();
            h.assertTrue(salt >= 2 && salt == (4000 - water) / TankCasingBlockEntity.WATER_PER_SALT, "Every 1000 mB dried leaves one salt: " + water + " mB, " + salt + " salt");
            var items = level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(wall), Direction.WEST);
            h.assertTrue(items.extractItem(0, 64, true).getCount() == salt && items.extractItem(0, 1, false).is(CEChlorAlkali.SALT.get()) && tank.getSalt() == salt - 1
                && items.insertItem(0, new ItemStack(CEChlorAlkali.SALT.get()), false).getCount() == 1, "The salt is taken out from any casing, and nothing goes in");
            fillBiome(level, low, high, biome);level.setDayTime(time);
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void pvcPipeTakesAcidButSoftensWhenHot(GameTestHelper h) {
        var acid = FluidHazards.named("hydrochloric_acid");var liquor = FluidHazards.named("sodium_aluminate_solution");
        h.assertTrue(PipeMaterial.of(CETransport.PVC_PIPE.get().defaultBlockState()) == PipeMaterial.PVC, "The PVC pipe is PVC");
        h.assertTrue(PipeMaterial.PVC.incompatibility(acid).equals("safe") && PipeMaterial.REINFORCED.incompatibility(acid).equals("corrosion"),
            "PVC takes hydrochloric acid, carbon steel does not");
        h.assertTrue(PipeMaterial.PVC.incompatibility(liquor).equals("softening") && PipeMaterial.STAINLESS.incompatibility(liquor).equals("safe"),
            "The hot Bayer liquor softens PVC");
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[]{"mixing/brine", "mixing/copper_block_from_oxidized_copper"})
            h.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, id)).isPresent(), "Recipe loads: " + id);
        h.succeed();
    }

    private static void fillBiome(ServerLevel level, BlockPos low, BlockPos high, String biome) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(),
            "fillbiome " + low.getX() + " " + low.getY() + " " + low.getZ() + " " + high.getX() + " " + high.getY() + " " + high.getZ() + " " + biome);
    }
}
