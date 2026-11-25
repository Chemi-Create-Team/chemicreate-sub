package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.createplant.block.ReliefScrubberBlock;
import net.createplant.block.entity.ReliefScrubberBlockEntity;
import net.createplant.block.entity.ReciprocatingCylinderBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CECompressor;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReciprocating;
import net.createplant.registry.CESafety;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasClouds;
import net.createplant.cloud.CloudVolume;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** A toxic or flammable gas let out fills a cloud that stops at walls and closed doors; it poisons the breathing, a gas mask keeps it out, and a flame lights it. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ToxicGasGameTests {
    private static java.util.List<GasCloud> clouds(GameTestHelper h) {
        return GasClouds.in(h.getLevel(), h.getBounds());
    }

    /** A closed stone room whose air is the box from {@code corner} (relative), {@code width} by {@code height} by {@code depth}. */
    private static void room(GameTestHelper h, BlockPos corner, int width, int height, int depth) {
        for (int x = -1; x <= width; x++) for (int y = -1; y <= height; y++) for (int z = -1; z <= depth; z++) {
            boolean inside = x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
            h.setBlock(corner.offset(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
        }
    }

    private static FluidStack gas(net.minecraft.world.level.material.Fluid fluid, int amount) { return new FluidStack(fluid, amount); }

    private static Mob husk(GameTestHelper h, BlockPos at) {
        Mob mob = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(at.getX() + .5, at.getY(), at.getZ() + .5));
        mob.setNoAi(true);
        return mob;
    }

    @GameTest(template = "test_reactor", timeoutTicks = 130)
    public static void aToxicCloudPoisonsTheBareAndAMaskWithFilterKeepsItOut(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 2, 6);
        room(h, corner, 3, 3, 5);
        BlockPos at = corner.offset(1, 0, 1);
        // Sky light under the new roof settles over a few ticks; until then the room would count as open air.
        h.runAfterDelay(10, () -> poisonInRoom(h, at));
    }

    private static void poisonInRoom(GameTestHelper h, BlockPos at) {
        ToxicGas.release(h.getLevel(), h.absolutePos(at), new FluidStack(Fluids.WATER, 1000));
        h.assertTrue(clouds(h).isEmpty(), "Water is harmless and makes no cloud");
        ToxicGas.release(h.getLevel(), h.absolutePos(at), gas(CEChlorAlkali.CHLORINE.get(), 1000));
        ToxicGas.release(h.getLevel(), h.absolutePos(at.south()), gas(CEChlorAlkali.CHLORINE.get(), 4000));
        var all = clouds(h);
        h.assertTrue(all.size() == 1 && all.getFirst().amount() == 5000, "A second leak beside the first feeds the same cloud: " + all.size());
        h.assertTrue(all.getFirst().lifetime() == indoorLifetime(5000) && all.getFirst().volume().size() == 45,
            "Indoors it lasts its full time and fills the closed room: " + all.getFirst().lifetime() + " " + all.getFirst().volume().size());
        Mob bare = husk(h, at);
        Mob masked = husk(h, at.east());
        Mob spent = husk(h, at.south(2));
        masked.setItemSlot(EquipmentSlot.HEAD, CESafety.GAS_MASK.toStack());
        ItemStack used = CESafety.GAS_MASK.toStack();used.setDamageValue(used.getMaxDamage());spent.setItemSlot(EquipmentSlot.HEAD, used);
        h.runAfterDelay(80, () -> {
            h.assertTrue(bare.getHealth() < bare.getMaxHealth() && spent.getHealth() < spent.getMaxHealth(), "The gas poisons the bare and the wearer of a spent mask: " + bare.getHealth() + " " + spent.getHealth());
            ItemStack mask = masked.getItemBySlot(EquipmentSlot.HEAD);
            h.assertTrue(masked.getHealth() == masked.getMaxHealth() && !masked.hasEffect(CESafety.TOXIC_GAS) && mask.getDamageValue() > 0, "The mask keeps it out and uses its filter: " + mask.getDamageValue());
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 20)
    public static void heavyGasFillsFromTheFloorAndLightGasFromTheCeiling(GameTestHelper h) {
        for (boolean heavy : new boolean[]{true, false}) {
            BlockPos corner = new BlockPos(heavy ? 2 : 16, 2, 6);
            room(h, corner, 5, 4, 5);
            BlockPos source = h.absolutePos(corner.offset(2, 2, 2));
            ToxicGas.release(h.getLevel(), source, gas(heavy ? CEChlorAlkali.CHLORINE.get() : CEFluids.AMMONIA_GAS.get(), 400));
            var cloud = GasClouds.in(h.getLevel(), new net.minecraft.world.phys.AABB(source)).getFirst();
            int filled = heavy ? 0 : 3, empty = heavy ? 3 : 0;
            boolean layer = true, other = false;
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                layer &= cloud.contains(h.absolutePos(corner.offset(x, filled, z)));
                other |= cloud.contains(h.absolutePos(corner.offset(x, empty, z)));
            }
            h.assertTrue(layer && !other, (heavy ? "Chlorine settles on the floor" : "Ammonia gathers under the ceiling") + " of the closed room");
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 20)
    public static void aLeakNearTheCeilingStillReachesTheFloor(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 2, 6);
        room(h, corner, 5, 3, 5);
        BlockPos source = h.absolutePos(corner.offset(2, 2, 2));
        ToxicGas.release(h.getLevel(), source, gas(CEChlorAlkali.CHLORINE.get(), 600));
        var cloud = GasClouds.in(h.getLevel(), new net.minecraft.world.phys.AABB(source)).getFirst();
        h.assertTrue(cloud.contains(h.absolutePos(corner.offset(0, 0, 0))) && cloud.contains(h.absolutePos(corner.offset(4, 0, 4))),
            "Heavy gas let out under the ceiling lies on the floor");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void heavyGasFollowsADeepShaftAndItsVolumeRoundTrips(GameTestHelper h) {
        var lowest = new BlockPos(3, 3, 3);
        room(h, lowest, 1, 11, 1);
        var source = h.absolutePos(lowest.above(10));
        var floor = h.absolutePos(lowest);
        GasClouds.release(h.getLevel(), source, gas(CEChlorAlkali.CHLORINE.get(), 100));
        var cloud = GasClouds.in(h.getLevel(), new AABB(floor)).stream().filter(gas -> gas.blockPosition().equals(source)).findFirst().orElseThrow();
        h.assertTrue(cloud.contains(floor) && !cloud.contains(floor.below()) && !cloud.contains(floor.east()),
            "Heavy gas reaches a lower floor through the air column and stays inside solid walls");
        h.assertTrue(CloudVolume.load(cloud.volume().save()).sameCells(cloud.volume()), "Deep cells keep their positions through saving and loading");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 160)
    public static void aClosedDoorHoldsTheGasBackAndAnOpenOneLetsItThrough(GameTestHelper h) {
        BlockPos corner = new BlockPos(8, 2, 4);
        room(h, corner, 1, 2, 9);
        BlockPos door = corner.offset(0, 0, 4);
        var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH);
        h.setBlock(door, lower);
        h.setBlock(door.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        ToxicGas.release(h.getLevel(), h.absolutePos(corner), gas(CEChlorAlkali.CHLORINE.get(), 1000));
        var cloud = clouds(h).getFirst();
        BlockPos beyond = corner.offset(0, 1, 7);
        Mob near = husk(h, corner.offset(0, 0, 2));
        Mob far = husk(h, corner.offset(0, 0, 7));
        h.assertTrue(cloud.contains(h.absolutePos(corner.offset(0, 1, 3))) && !cloud.contains(h.absolutePos(beyond)), "The closed door stops the cloud");
        h.runAfterDelay(30, () -> {
            h.assertTrue(near.hasEffect(CESafety.TOXIC_GAS) && !far.hasEffect(CESafety.TOXIC_GAS), "Only the side of the gas is poisoned");
            for (var half : new BlockPos[]{door, door.above()})
                h.setBlock(half, h.getBlockState(half).setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true));
        });
        h.runAfterDelay(140, () -> {
            h.assertTrue(cloud.contains(h.absolutePos(beyond)), "Opened, the door lets the cloud through");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 30)
    public static void anOpenSkyShortensTheCloud(GameTestHelper h) {
        BlockPos corner = new BlockPos(4, 2, 6);
        room(h, corner, 3, 3, 3);
        h.runAfterDelay(10, () -> {
            ToxicGas.release(h.getLevel(), h.absolutePos(corner), gas(CEChlorAlkali.CHLORINE.get(), 1000));
            BlockPos open = h.absolutePos(new BlockPos(20, 6, 20));
            ToxicGas.release(h.getLevel(), open, gas(CEChlorAlkali.CHLORINE.get(), 1000));
            var indoor = GasClouds.in(h.getLevel(), new net.minecraft.world.phys.AABB(h.absolutePos(corner))).getFirst();
            var outdoor = GasClouds.in(h.getLevel(), new net.minecraft.world.phys.AABB(open)).getFirst();
            h.assertTrue(indoor.lifetime() - indoor.age() == indoorLifetime(1000) && outdoor.lifetime() - outdoor.age() == indoorLifetime(1000) / ToxicGas.OUTDOOR_DIVISOR,
                "Under the open sky the cloud lasts a third as long: " + indoor.lifetime() + " " + outdoor.lifetime());
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 60)
    public static void aDetectorSensesAnUnseenGasButNotThroughAWall(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 2, 6);
        room(h, corner, 3, 3, 3);
        BlockPos inside = corner.offset(2, 0, 2), outside = corner.offset(4, 0, 1);
        h.setBlock(inside, CESafety.GAS_DETECTOR.get());
        h.setBlock(outside, CESafety.GAS_DETECTOR.get());
        ToxicGas.release(h.getLevel(), h.absolutePos(corner), gas(CEProcessFluids.CARBON_MONOXIDE.get(), 1000));
        h.assertTrue(clouds(h).size() == 1 && clouds(h).getFirst().style().color() == null, "Carbon monoxide makes a single cloud that is not drawn");
        h.runAfterDelay(40, () -> {
            h.assertTrue(h.getBlockState(inside).getValue(net.createplant.block.GasDetectorBlock.POWER) > 0
                && h.getBlockState(outside).getValue(net.createplant.block.GasDetectorBlock.POWER) == 0, "The detector in the room senses it; one behind the wall does not");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 60)
    public static void burntGasStillPoisonsButNoLongerBurnsAndOldCloudsFade(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 2, 6);
        room(h, corner, 3, 3, 3);
        ToxicGas.releasePoison(h.getLevel(), h.absolutePos(corner), gas(CEProcessFluids.CARBON_MONOXIDE.get(), 1000));
        ToxicGas.releasePoison(h.getLevel(), h.absolutePos(new BlockPos(20, 3, 20)), gas(CEFluids.HYDROGEN_GAS.get(), 1000));
        h.assertTrue(clouds(h).size() == 1 && clouds(h).getFirst().inert(), "Only the toxic part of flared gas remains, as a cloud that cannot burn");
        h.setBlock(corner.offset(2, 0, 2), Blocks.TORCH);
        var old = new AreaEffectCloud(h.getLevel(), h.absolutePos(corner).getX(), h.absolutePos(corner).getY() + 8, h.absolutePos(corner).getZ());
        old.addTag(CreatePlant.MOD_ID + ".toxic_gas");
        old.setDuration(10);
        h.getLevel().addFreshEntity(old);
        h.runAfterDelay(45, () -> {
            h.assertTrue(clouds(h).size() == 1 && !old.isAlive(), "The torch does not light burnt gas, and a cloud from an older save fades away");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 60)
    public static void aFlameInsideLightsTheCloudButNotOneBehindAWall(GameTestHelper h) {
        BlockPos inside = new BlockPos(3, 2, 6), walled = new BlockPos(16, 2, 6);
        room(h, inside, 3, 3, 3);
        room(h, walled, 3, 3, 3);
        ToxicGas.release(h.getLevel(), h.absolutePos(inside), gas(CEProcessFluids.CARBON_MONOXIDE.get(), 1000));
        ToxicGas.release(h.getLevel(), h.absolutePos(walled), gas(CEFluids.HYDROGEN_GAS.get(), 1000));
        h.assertTrue(clouds(h).size() == 2, "A toxic, flammable gas makes one cloud carrying both");
        Mob breathing = husk(h, inside.offset(1, 0, 1));
        h.setBlock(walled.offset(4, 0, 1), Blocks.CAMPFIRE);
        h.runAfterDelay(25, () -> {
            h.assertTrue(breathing.hasEffect(CESafety.TOXIC_GAS) && clouds(h).size() == 2, "Carbon monoxide poisons, and the fire outside the wall lights nothing");
            h.setBlock(inside.offset(2, 0, 2), Blocks.TORCH);
        });
        h.runAfterDelay(50, () -> {
            var left = clouds(h);
            h.assertTrue(left.size() == 1 && left.getFirst().content().is(CEFluids.HYDROGEN_GAS.get()), "The torch in the room lights the carbon monoxide");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void withExplosionsOffABurningCloudJustGoesOut(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 2, 6);
        room(h, corner, 3, 3, 3);
        net.createplant.transport.TransportConfig.GAS_EXPLOSIONS.set(false);
        ToxicGas.release(h.getLevel(), h.absolutePos(corner), gas(CEFluids.HYDROGEN_GAS.get(), 1000));
        h.setBlock(corner.offset(2, 0, 2), Blocks.TORCH);
        h.runAfterDelay(25, () -> {
            net.createplant.transport.TransportConfig.GAS_EXPLOSIONS.set(true);
            h.assertTrue(clouds(h).isEmpty() && h.getBlockState(corner.offset(-1, 0, 0)).is(Blocks.STONE), "The cloud burns away without breaking the room");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 20)
    public static void everyHarmfulGasInTheModMakesACloud(GameTestHelper h) {
        int count = 0;
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            // Retired state entries load as their substance, which this loop checks under its own id.
            if (!id.getNamespace().equals(CreatePlant.MOD_ID) || id.getPath().startsWith("flowing_")) continue;
            var stack = new FluidStack(fluid, 100);
            boolean harmful = ToxicGas.strength(stack) >= 0 || net.createplant.safety.FlammableGas.fuel(stack);
            boolean gas = net.createplant.thermal.FluidMaterials.gas(fluid);
            h.assertTrue(!harmful || GasClouds.supported(stack), "A harmful gas has a cloud: " + id);
            h.assertTrue(!gas || harmful || net.createplant.thermal.SteamGrades.of(stack) != null || HARMLESS.contains(id.getPath())
                || id.getPath().equals("raw_process_gas") || id.getPath().equals("contaminated_gas"),
                "Every gas in the mod is either harmful, known to be harmless, or judged by what it carries: " + id);
            if (harmful) count++;
        }
        h.assertTrue(count >= 30, "The mod's harmful gases are all classified: " + count);
        h.succeed();
    }

    /** Gases that neither poison nor burn: the inert and breathing gases and air. */
    private static final java.util.Set<String> HARMLESS = java.util.Set.of("air_gas", "compressed_air", "oxygen_gas", "nitrogen_gas", "argon_gas", "other_gases",
        "helium_gas", "helium_recovery_feed", "resin_process_vapor");

    /** How long a cloud of this much gas lasts indoors. */
    private static int indoorLifetime(int amount) { return Math.min(2400, 400 + amount / 5); }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void dismantlingAReciprocatingCylinderLetsToxicContentsOut(GameTestHelper h) {
        BlockPos cylinder = new BlockPos(8, 2, 8);
        h.setBlock(cylinder, CEReciprocating.CYLINDER.get());
        var reciprocating = (ReciprocatingCylinderBlockEntity) h.getBlockEntity(cylinder);
        reciprocating.outlet().fill(new FluidStack(CEChlorAlkali.CHLORINE.get(), 250), EXECUTE);
        reciprocating.release();
        h.assertTrue(clouds(h).stream().anyMatch(cloud -> cloud.blockPosition().closerThan(h.absolutePos(cylinder), 2)),
            "A dismantled reciprocating cylinder releases its chlorine as a toxic cloud");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void dismantlingASeparatorLetsItsAmmoniaMixtureOut(GameTestHelper h) {
        BlockPos at = new BlockPos(16, 2, 16);
        var separator = HaberBoschGameTests.separator(h.getLevel(),h.absolutePos(at));
        var port = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,h.absolutePos(at.above()),Direction.NORTH);
        h.assertTrue(port.fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(), 800), EXECUTE) == 800,
            "The formed separator holds one batch of ammonia mixture");
        separator.vent();
        h.assertTrue(clouds(h).stream().anyMatch(cloud -> cloud.blockPosition().closerThan(h.absolutePos(at), 2)),
            "Dismantling a separator releases its ammonia-bearing mixture as an irritant cloud");
        h.succeed();
    }






    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void reliefScrubberStoresOnlyEightBucketsOfCausticAtItsMarkedSide(GameTestHelper h) {
        BlockPos at = new BlockPos(12, 2, 12);
        h.setBlock(at, CECompressor.RELIEF_SCRUBBER.get().defaultBlockState().setValue(ReliefScrubberBlock.FACING, Direction.NORTH));
        var inlet=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(at),Direction.NORTH);
        h.assertTrue(inlet!=null && inlet.getTankCapacity(0)==ReliefScrubberBlockEntity.CAPACITY,"The dedicated scrubber exposes its 8000 mB service tank at the marked side");
        h.assertTrue(inlet.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"The scrubber refuses water");
        h.assertTrue(inlet.fill(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),9000),EXECUTE)==8000,"The scrubber accepts at most 8000 mB caustic soda");
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(at),Direction.SOUTH)==null,"Unmarked sides do not pretend to be liquid ports");
        var scrubber=(ReliefScrubberBlockEntity)h.getBlockEntity(at);
        var drops=Block.getDrops(h.getBlockState(at),h.getLevel(),h.absolutePos(at),scrubber);
        var saved=drops.size()==1 ? drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA) : null;
        h.assertTrue(saved!=null && !saved.isEmpty(),"The dropped scrubber keeps its caustic inventory");
        BlockPos restored=at.east(2);h.setBlock(restored,CECompressor.RELIEF_SCRUBBER.get().defaultBlockState());
        h.assertTrue(saved.loadInto(h.getBlockEntity(restored),h.getLevel().registryAccess())
            && ((ReliefScrubberBlockEntity)h.getBlockEntity(restored)).amount()==8000,"Placing the saved scrubber restores all 8000 mB");
        h.succeed();
    }

}
