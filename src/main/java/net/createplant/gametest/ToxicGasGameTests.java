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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** A toxic gas let out hangs as a cloud that poisons, a gas mask keeps it out while its filter lasts, and a burst tank of chlorine lets it out. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ToxicGasGameTests {
    private static java.util.List<AreaEffectCloud> clouds(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(AreaEffectCloud.class, h.getBounds(), ToxicGas::toxic);
    }





    private static int scrubberAmount(GameTestHelper h) {
        return ((ReliefScrubberBlockEntity)h.getBlockEntity(new BlockPos(5, 3, 4))).amount();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 120)
    public static void aToxicCloudPoisonsTheBareAndAMaskWithFilterKeepsItOut(GameTestHelper h) {
        BlockPos at = new BlockPos(8, 2, 8);
        ToxicGas.release(h.getLevel(), h.absolutePos(at), new FluidStack(Fluids.WATER, 1000));
        h.assertTrue(clouds(h).isEmpty(), "Water is harmless and makes no cloud");
        ToxicGas.release(h.getLevel(), h.absolutePos(at), new FluidStack(CEChlorAlkali.CHLORINE.get(), 1000));
        ToxicGas.release(h.getLevel(), h.absolutePos(at.east()), new FluidStack(CEChlorAlkali.CHLORINE.get(), 4000));
        var all = clouds(h);
        boolean outdoors = h.getLevel().canSeeSky(h.absolutePos(at).above());
        h.assertTrue(all.size() == 1 && Math.abs(all.getFirst().getRadius() - 4.5f) < 1e-3, "A second leak beside the first feeds the same cloud, as wide as 4000 mB makes it: " + all.size());
        h.assertTrue(all.getFirst().getDuration() == (ToxicGas.MIN_TICKS + 800) / (outdoors ? ToxicGas.OUTDOOR_DIVISOR : 1), "It lasts longer indoors than under the sky: " + all.getFirst().getDuration());
        Mob bare = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(at.getX() + .5, at.getY(), at.getZ() + .5));
        Mob masked = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(at.getX() + 1.5, at.getY(), at.getZ() + .5));
        Mob spent = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(at.getX() + .5, at.getY(), at.getZ() + 1.5));
        masked.setItemSlot(EquipmentSlot.HEAD, CESafety.GAS_MASK.toStack());
        ItemStack used = CESafety.GAS_MASK.toStack();used.setDamageValue(used.getMaxDamage());spent.setItemSlot(EquipmentSlot.HEAD, used);
        for (Mob mob : new Mob[]{bare, masked, spent}) mob.setNoAi(true);
        h.runAfterDelay(80, () -> {
            h.assertTrue(bare.getHealth() < bare.getMaxHealth() && spent.getHealth() < spent.getMaxHealth(), "The gas poisons the bare and the wearer of a spent mask: " + bare.getHealth() + " " + spent.getHealth());
            ItemStack mask = masked.getItemBySlot(EquipmentSlot.HEAD);
            h.assertTrue(masked.getHealth() == masked.getMaxHealth() && !masked.hasEffect(CESafety.TOXIC_GAS) && mask.getDamageValue() > 0, "The mask keeps it out and uses its filter: " + mask.getDamageValue());
            h.succeed();
        });
    }


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
