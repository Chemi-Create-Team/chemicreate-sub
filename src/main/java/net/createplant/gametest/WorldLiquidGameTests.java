package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.WorldLiquidBlock;
import net.createplant.item.ProcessBucketItem;
import net.createplant.item.StainlessBuckets;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.FluidIdentity;
import net.createplant.thermal.FluidState;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class WorldLiquidGameTests {
    @GameTest(template = "test_empty")
    public static void everyRegisteredLiquidHasAPlaceableFullBucket(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        for (var entry : CEWorldFluids.entries()) {
            if(FluidIdentity.legacy(entry.fluid().get()))continue;
            FluidStack fluid = new FluidStack(entry.fluid().get(), 1000);
            h.assertTrue(!FluidHazards.of(fluid).gas(), entry.name() + " is a liquid");
            ItemStack bucket = FluidUtil.getFilledBucket(fluid);
            h.assertTrue(bucket.getItem() instanceof ProcessBucketItem, entry.name() + " has a process bucket");
            h.assertTrue(FluidUtil.getFluidContained(bucket).orElse(FluidStack.EMPTY).getAmount() == 1000,
                entry.name() + " exposes a full standard bucket capability");
            ProcessBucketItem item = (ProcessBucketItem) bucket.getItem();
            h.assertTrue(item.emptyContents(null, h.getLevel(), pos, null, bucket), entry.name() + " can be placed");
            h.assertTrue(h.getLevel().getFluidState(pos).isSource() && h.getLevel().getFluidState(pos).getType().isSame(fluid.getFluid()),
                entry.name() + " places its own liquid source");
            h.assertTrue(!fluid.getFluidType().canConvertToSource(fluid), entry.name() + " cannot form free sources");
            h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void alloyAndIronBucketsKeepTheirOwnMetalAndThermalContents(GameTestHelper h) {
        FluidStack acid = FluidThermal.at(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000), 343.15);
        var iron = FluidUtil.getFluidHandler(new ItemStack(Items.BUCKET)).orElseThrow();
        var alloy = FluidUtil.getFluidHandler(CEWorldFluids.EMPTY_ALLOY_BUCKET.toStack()).orElseThrow();
        h.assertTrue(iron.fill(acid, SIMULATE) == 0 && iron.fill(acid, EXECUTE) == 0,
            "Iron cannot become a free alloy bucket through an automatic fluid handler");
        h.assertTrue(alloy.fill(acid, SIMULATE) == 1000 && alloy.getFluidInTank(0).isEmpty(), "Simulation leaves the alloy bucket empty");
        h.assertTrue(alloy.fill(acid, EXECUTE) == 1000, "An alloy bucket accepts corrosive liquid");
        var filled = FluidUtil.getFluidHandler(alloy.getContainer()).orElseThrow();
        FluidStack drained = filled.drain(1000, EXECUTE);
        h.assertTrue(FluidStack.isSameFluidSameComponents(drained, acid)
            && filled.getContainer().is(CEWorldFluids.EMPTY_ALLOY_BUCKET.get()), "Emptying returns the alloy and preserves heat");
        h.assertTrue(alloy.getContainer().getCraftingRemainingItem().is(CEWorldFluids.EMPTY_ALLOY_BUCKET.get()),
            "Crafting also returns the alloy container");
        var brine = new FluidStack(CEChlorAlkali.BRINE.get(), 1000);
        for (var shell : new ItemStack[]{new ItemStack(Items.BUCKET), CEWorldFluids.EMPTY_ALLOY_BUCKET.toStack()}) {
            var bucket = FluidUtil.getFluidHandler(shell).orElseThrow();
            h.assertTrue(bucket.fill(brine, EXECUTE) == 1000, "Both bucket materials accept brine");
            var full = FluidUtil.getFluidHandler(bucket.getContainer()).orElseThrow();
            h.assertTrue(full.drain(1000, EXECUTE).is(CEChlorAlkali.BRINE.get()) && full.getContainer().is(shell.getItem()),
                "Draining brine returns the original bucket material");
        }
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void stainlessBucketsHoldWhatAStainlessTankHoldsAndStayStainless(GameTestHelper h) {
        var empty = FluidUtil.getFluidHandler(CEWorldFluids.STAINLESS_BUCKET.toStack()).orElseThrow();
        var hotAcid = new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(CreatePlant.location("titanyl_sulfate_solution")), 1000);
        var steam = FluidState.with(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), 14, 4);
        h.assertTrue(empty.fill(hotAcid, SIMULATE) == 0 && empty.fill(steam, SIMULATE) == 0,
            "Hot acids and gases that a stainless tank is not rated for do not go in");
        var sulfate = new FluidStack(net.createplant.registry.CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(), 1500);
        var containers = new ItemStack(CEWorldFluids.STAINLESS_BUCKET.get(), 2);
        h.assertTrue(com.simibubi.create.content.fluids.transfer.GenericItemFilling.getRequiredAmountForItem(h.getLevel(),containers,sulfate)==-1
            && containers.getCount()==2 && sulfate.getAmount()==1500,"Create filling skips a sulfate the stainless bucket cannot take");
        h.assertTrue(com.simibubi.create.content.fluids.transfer.GenericItemFilling.getRequiredAmountForItem(h.getLevel(),containers,
            new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000))==1000,"Create filling accepts a whole bucket for a compatible fluid");
        FluidStack base = FluidThermal.at(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000), 343.15);
        h.assertTrue(empty.fill(base, SIMULATE) == 1000 && empty.getContainer().is(CEWorldFluids.STAINLESS_BUCKET.get()), "Simulation leaves the bucket empty");
        h.assertTrue(empty.fill(base, EXECUTE) == 1000 && StainlessBuckets.marked(empty.getContainer()), "A base fills it as the liquid's bucket, marked stainless");
        h.assertTrue(empty.getContainer().getCraftingRemainingItem().is(CEWorldFluids.STAINLESS_BUCKET.get()), "Crafting returns the stainless bucket");
        var filled = FluidUtil.getFluidHandler(empty.getContainer()).orElseThrow();
        h.assertTrue(FluidStack.isSameFluidSameComponents(filled.drain(1000, EXECUTE), base)
            && filled.getContainer().is(CEWorldFluids.STAINLESS_BUCKET.get()), "Emptying gives back the stainless bucket and the liquid's heat");

        var water = FluidUtil.getFluidHandler(CEWorldFluids.STAINLESS_BUCKET.toStack()).orElseThrow();
        h.assertTrue(water.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), EXECUTE) == 1000
            && water.getContainer().is(Items.WATER_BUCKET) && StainlessBuckets.marked(water.getContainer()), "Water fills it as a marked water bucket");
        h.assertTrue(water.getContainer().getCraftingRemainingItem().is(CEWorldFluids.STAINLESS_BUCKET.get()), "A vanilla bucket crafts back to stainless");
        var vanilla = FluidUtil.getFluidHandler(water.getContainer()).orElseThrow();
        h.assertTrue(vanilla.drain(1000, EXECUTE).getAmount() == 1000 && vanilla.getContainer().is(CEWorldFluids.STAINLESS_BUCKET.get()),
            "Vanilla's own bucket handler empties it back into stainless");
        h.assertTrue(net.minecraft.world.item.BucketItem.getEmptySuccessItem(water.getContainer(), h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)).is(CEWorldFluids.STAINLESS_BUCKET.get()),
            "Pouring it into the world leaves the stainless bucket");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void unrepresentableStateCannotBePlacedAndDefaultStateCanBePickedUp(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        var acid=new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),1000);
        var hot=CEWorldFluids.bucket(FluidState.with(acid,9,4));
        h.assertTrue(!((ProcessBucketItem)hot.getItem()).emptyContents(null,h.getLevel(),pos,null,hot) && h.getLevel().getFluidState(pos).isEmpty(),
            "A placed source cannot discard a nondefault temperature");
        ItemStack filled = CEWorldFluids.bucket(acid);
        ((ProcessBucketItem) filled.getItem()).emptyContents(null, h.getLevel(), pos, null, filled);
        var block = (WorldLiquidBlock) h.getLevel().getBlockState(pos).getBlock();
        h.assertTrue(block.pickupBlock(null, h.getLevel(), pos, h.getLevel().getBlockState(pos)).isEmpty(),
            "A plain bucket cannot recover a corrosive source, including a dispenser");
        ItemStack recovered = block.pickupAlloy(h.getLevel(), pos, h.getLevel().getBlockState(pos));
        FluidStack pickedAcid = FluidUtil.getFluidContained(recovered).orElseThrow();
        h.assertTrue(FluidState.temperature(pickedAcid)==8,
            "An alloy bucket assigns the nominal fluid temperature to a world source");
        FluidStack oil = FluidThermal.at(new FluidStack(CEPetroFluids.NAPHTHA.get(), 1000), 321.15);
        ItemStack oilBucket = CEWorldFluids.bucket(oil);
        ((ProcessBucketItem) oilBucket.getItem()).emptyContents(null, h.getLevel(), pos, null, oilBucket);
        block = (WorldLiquidBlock) h.getLevel().getBlockState(pos).getBlock();
        recovered = block.pickupBlock(null, h.getLevel(), pos, h.getLevel().getBlockState(pos));
        FluidStack pickedOil = FluidUtil.getFluidContained(recovered).orElseThrow();
        h.assertTrue(FluidState.temperature(pickedOil)==8,
            "Placed oil is recovered at its default temperature");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void placedLiquidSpreadsWithoutCreatingMoreSourceBlocks(GameTestHelper h) {
        BlockPos center = h.absolutePos(new BlockPos(4, 4, 4));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            h.getLevel().setBlockAndUpdate(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 2; y++) h.getLevel().setBlockAndUpdate(center.offset(x, y, z),
                Math.abs(x) == 3 || Math.abs(z) == 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        h.getLevel().setBlockAndUpdate(center.west(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        h.getLevel().setBlockAndUpdate(center.east(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        h.runAfterDelay(35, () -> {
            var flowed = h.getLevel().getFluidState(center);
            h.assertTrue(!flowed.isEmpty() && !flowed.isSource(), "The liquid flows between two sources without making a third: " + flowed);
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void bucketComponentsSurviveItemSaveAndNetworkRoundTrips(GameTestHelper h) {
        FluidStack fluid = FluidThermal.at(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000), 343.15);
        ItemStack original = CEWorldFluids.bucket(fluid);
        CompoundTag saved = (CompoundTag) original.save(h.getLevel().registryAccess());
        ItemStack restored = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        h.assertTrue(ItemStack.isSameItemSameComponents(original, restored), "Saving restores the immutable bucket contents exactly");
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            ItemStack transmitted = ItemStack.STREAM_CODEC.decode(buffer);
            h.assertTrue(ItemStack.isSameItemSameComponents(original, transmitted), "Network encoding preserves bucket component equality");
            h.assertTrue(original.get(CEWorldFluids.BUCKET_CONTENTS.get()).hashCode()
                == transmitted.get(CEWorldFluids.BUCKET_CONTENTS.get()).hashCode(), "Equal contents have equal hashes");
            FluidStack copy = FluidUtil.getFluidContained(transmitted).orElseThrow();
            copy.shrink(500);
            h.assertTrue(FluidUtil.getFluidContained(transmitted).orElseThrow().getAmount() == 1000,
                "A mutable fluid copy cannot alter the stored component");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void realDispensersKeepContainerMetalsAndUseLocalPickupTemperature(GameTestHelper h) {
        BlockPos waterPos = h.absolutePos(new BlockPos(3, 4, 3));
        var water = dispenser(h, waterPos, new ItemStack(Items.BUCKET));
        h.getLevel().setBlockAndUpdate(waterPos.east(), Blocks.WATER.defaultBlockState());
        activate(h, waterPos);
        h.assertTrue(water.getItem(0).is(Items.WATER_BUCKET) && h.getLevel().getFluidState(waterPos.east()).isEmpty(),
            "A plain water source still uses the original dispenser behavior");

        BlockPos acidPos = waterPos.south(4);
        var alloy = dispenser(h, acidPos, CEWorldFluids.EMPTY_ALLOY_BUCKET.toStack());
        h.getLevel().setBlockAndUpdate(acidPos.east(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        activate(h, acidPos);
        FluidStack picked = FluidUtil.getFluidContained(alloy.getItem(0)).orElseThrow();
        h.assertTrue(picked.is(CEChlorAlkali.CAUSTIC_SODA.get())
            && FluidState.temperature(picked)==8,
            "The alloy dispenser recovers an acid source at the nominal fluid temperature");

        BlockPos pourPos = waterPos.south(8);
        var poured = dispenser(h, pourPos,
            CEWorldFluids.bucket(FluidThermal.at(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000), 363.15)));
        activate(h, pourPos);
        h.assertTrue(poured.getItem(0).is(CEWorldFluids.EMPTY_ALLOY_BUCKET.get())
            && h.getLevel().getFluidState(pourPos.east()).isSource(), "Pouring retains the alloy container and places one source");
        activate(h, pourPos);
        FluidStack recovered = FluidUtil.getFluidContained(poured.getItem(0)).orElseThrow();
        h.assertTrue(h.getLevel().getFluidState(pourPos.east()).isEmpty()
            && FluidState.temperature(recovered)==8,
            "Dispensing and recovering default-state liquid consumes the world source");

        BlockPos wrongPos = waterPos.south(12);
        var iron = dispenser(h, wrongPos, new ItemStack(Items.BUCKET));
        h.getLevel().setBlockAndUpdate(wrongPos.east(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        activate(h, wrongPos);
        int dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(wrongPos).inflate(2)).stream()
            .filter(item -> item.getItem().is(Items.BUCKET)).mapToInt(item -> item.getItem().getCount()).sum();
        h.assertTrue(iron.isEmpty() && dropped == 1 && h.getLevel().getFluidState(wrongPos.east()).isSource(),
            "A wrong iron bucket is ejected once without extracting acid or creating alloy");

        BlockPos stainlessPos = waterPos.south(16);
        var stainless = dispenser(h, stainlessPos, CEWorldFluids.STAINLESS_BUCKET.toStack());
        h.getLevel().setBlockAndUpdate(stainlessPos.east(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        activate(h, stainlessPos);
        h.assertTrue(StainlessBuckets.marked(stainless.getItem(0)) && FluidUtil.getFluidContained(stainless.getItem(0)).orElseThrow().is(CEChlorAlkali.CAUSTIC_SODA.get())
            && h.getLevel().getFluidState(stainlessPos.east()).isEmpty(), "A stainless bucket in a dispenser takes up a base it holds");
        activate(h, stainlessPos);
        h.assertTrue(stainless.getItem(0).is(CEWorldFluids.STAINLESS_BUCKET.get()) && h.getLevel().getFluidState(stainlessPos.east()).isSource(),
            "Pouring it again leaves the stainless bucket");
        h.getLevel().setBlockAndUpdate(stainlessPos.east(), Blocks.AIR.defaultBlockState());
        stainless.setItem(0, StainlessBuckets.mark(new ItemStack(Items.WATER_BUCKET)));
        activate(h, stainlessPos);
        h.assertTrue(stainless.getItem(0).is(CEWorldFluids.STAINLESS_BUCKET.get()) && h.getLevel().getFluidState(stainlessPos.east()).isSource(),
            "Vanilla's water bucket dispensed from stainless leaves the stainless bucket");
        h.succeed();
    }

    private static DispenserBlockEntity dispenser(GameTestHelper h, BlockPos pos, ItemStack stack) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        h.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.east().below(), Blocks.STONE.defaultBlockState());
        var dispenser = (DispenserBlockEntity) h.getLevel().getBlockEntity(pos);
        dispenser.setItem(0, stack);
        return dispenser;
    }

    private static void activate(GameTestHelper h, BlockPos pos) {
        h.getLevel().getBlockState(pos).tick(h.getLevel(), pos, h.getLevel().getRandom());
    }
}
