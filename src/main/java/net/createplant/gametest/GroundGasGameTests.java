package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.cloud.GasClouds;
import net.createplant.cloud.GroundGasAtmosphere;
import net.createplant.registry.CEPetroFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@ExplicitGameTest @GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GroundGasGameTests {
    @GameTest(template = "test_rbmk", batch = "ground_gas_tube", timeoutTicks = 180)
    public static void anOpenTubeFillsAndSpillsOverTheRim(GameTestHelper h) {
        var base = new BlockPos(5, 4, 5);
        for (int z = -4; z <= 4; z++)
            for (int x = -4; x <= 4; x++) h.setBlock(base.offset(x, -1, z), Blocks.STONE);
        for (int y = 0; y < 3; y++)
            for (int z = -1; z <= 1; z++)
                for (int x = -1; x <= 1; x++)
                    if (x != 0 || z != 0) h.setBlock(base.offset(x, y, z), Blocks.STONE);
        var chicken = h.spawn(EntityType.CHICKEN, base);
        chicken.setNoAi(true);
        chicken.setAirSupply(20);
        GasClouds.release(h.getLevel(), h.absolutePos(base.above(4)), gas());
        h.runAfterDelay(38, () -> {
            h.assertTrue(concentration(h, base) > .4f, "The gas should have collected at the bottom of the tube");
            h.assertTrue(concentration(h, base.east()) == 0 && concentration(h, base.east(2)) == 0,
                "The tube wall should block gas while the pool is below the rim");
            h.assertTrue(chicken.getAirSupply() < chicken.getMaxAirSupply(), "A mob breathing the pool should use up air");
        });
        h.runAfterDelay(135, () -> {
            h.assertTrue(concentration(h, base.east(2)) > .15f, "Gas should reach the outside floor by spilling over the open rim");
            h.assertTrue(concentration(h, base.east()) == 0, "The solid wall itself remains gas-free");
            h.assertTrue(chicken.getHealth() < chicken.getMaxHealth(), "The concentrated non-poisonous gas should suffocate the mob");
            h.succeed();
        });
    }

    @GameTest(template = "test_pressure_infrastructure", batch = "ground_gas_corridor", timeoutTicks = 650)
    public static void dilutionUsesElapsedTimeAndShelter(GameTestHelper h) {
        var outdoors = new BlockPos(6, 20, 4);
        var sheltered = outdoors.south(8);
        for (var start : new BlockPos[] {outdoors, sheltered}) {
            for (int x = -4; x <= 72; x++) {
                h.setBlock(start.offset(x, -1, 0), Blocks.STONE);
                h.setBlock(start.offset(x, 0, -1), Blocks.STONE);
                h.setBlock(start.offset(x, 0, 1), Blocks.STONE);
                if (start.equals(sheltered)) h.setBlock(start.offset(x, 1, 0), Blocks.STONE);
            }
            h.setBlock(start.west(5), Blocks.STONE);
            h.setBlock(start.east(73), Blocks.STONE);
        }
        h.runAfterDelay(10, () -> {
            var open = h.absolutePos(outdoors);
            var roofed = h.absolutePos(sheltered);
            int openRoof = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, open.getX(), open.getZ());
            int coveredRoof = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, roofed.getX(), roofed.getZ());
            h.assertTrue(open.getY() >= openRoof && roofed.getY() < coveredRoof,
                "The two corridors need different roofs (air Y " + open.getY() + ", roofs " + openRoof + "/" + coveredRoof + ")");
            GasClouds.release(h.getLevel(), h.absolutePos(outdoors), gas());
            GasClouds.release(h.getLevel(), h.absolutePos(sheltered), gas());
        });
        h.runAfterDelay(300, () -> {
            float open = concentration(h, outdoors.east(32)), roofed = concentration(h, sheltered.east(32));
            h.assertTrue(open > .15f && roofed > .35f,
                "Gas should move beyond the old effective limit (open " + open + ", roofed " + roofed + ")");
        });
        h.runAfterDelay(560, () -> {
            h.assertTrue(concentration(h, outdoors.east(32)) == 0 && concentration(h, sheltered.east(32)) > .35f,
                "Gas should clear outdoors and linger under the roof after the same time");
            h.succeed();
        });
    }

    private static float concentration(GameTestHelper h, BlockPos pos) {
        return GroundGasAtmosphere.concentration(h.getLevel(), Vec3.atBottomCenterOf(h.absolutePos(pos)).add(0, .1, 0));
    }

    private static FluidStack gas() {
        return new FluidStack(CEPetroFluids.PROPYLENE.get(), 3500);
    }
}
