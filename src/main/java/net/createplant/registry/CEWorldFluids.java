package net.createplant.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.WorldLiquidBlock;
import net.createplant.item.AlloyBucketItem;
import net.createplant.item.BucketFluidContents;
import net.createplant.item.ProcessBucketItem;
import net.createplant.item.ProcessBucketWrapper;
import net.createplant.item.StainlessBucketItem;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEWorldFluids {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePlant.MOD_ID);
    private static final List<Entry> ENTRIES = new ArrayList<>();
    public static final Supplier<DataComponentType<BucketFluidContents>> BUCKET_CONTENTS = COMPONENTS.register("bucket_contents",
        () -> DataComponentType.<BucketFluidContents>builder().persistent(BucketFluidContents.CODEC).networkSynchronized(BucketFluidContents.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<net.minecraft.util.Unit>> IRON_BUCKET_SHELL = COMPONENTS.register("iron_bucket_shell",
        () -> DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
            .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());
    public static final DeferredItem<AlloyBucketItem> EMPTY_ALLOY_BUCKET = ITEMS.register("corrosion_resistant_bucket",
        () -> new AlloyBucketItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<StainlessBucketItem> STAINLESS_BUCKET = ITEMS.register("stainless_steel_bucket",
        () -> new StainlessBucketItem(new Item.Properties().stacksTo(16)));
    /** On a filled bucket: it was filled from the stainless bucket and empties back into it. */
    public static final Supplier<DataComponentType<net.minecraft.util.Unit>> STAINLESS_SHELL = COMPONENTS.register("stainless_bucket",
        () -> DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
            .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());

    public record Entry(String name, int color, Supplier<? extends Fluid> fluid,
        Supplier<? extends Item> bucket, Supplier<? extends LiquidBlock> block) { }

    private CEWorldFluids() { }

    static Entry define(String name, int color, Supplier<? extends Fluid> fluid) {
        Supplier<? extends Item> bucket = switch (name) {
            case "heavy_oil" -> () -> CEItems.HEAVY_OIL_BUCKET.get();
            case "naphtha" -> () -> CEItems.NAPHTHA_BUCKET.get();
            case "lubricating_oil" -> () -> CEItems.LUBRICATING_OIL_BUCKET.get();
            case "nitric_acid" -> () -> CEItems.NITRIC_ACID_BUCKET.get();
            default -> ITEMS.register(name + "_bucket", () -> new ProcessBucketItem(fluid.get(), bucketProperties()));
        };
        var block = BLOCKS.register(name, () -> new WorldLiquidBlock((net.minecraft.world.level.material.FlowingFluid) fluid.get(),
            BlockBehaviour.Properties.of().noCollission().strength(100).liquid().replaceable().noLootTable()));
        Entry entry = new Entry(name, color, fluid, bucket, block);
        ENTRIES.add(entry);
        return entry;
    }

    public static Item.Properties bucketProperties() {
        return new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET);
    }

    public static List<Entry> entries() { return List.copyOf(ENTRIES); }

    public static boolean isLiquid(Fluid fluid) {
        for (Entry entry : ENTRIES) if (entry.fluid().get().isSame(fluid)) return true;
        return false;
    }

    /** Older brine buckets used an alloy shell, which still accepts brine and is returned when emptied. */
    public static boolean acceptsAlloyBucket(FluidStack fluid) {
        return CEChlorAlkali.BRINE.get().isSame(fluid.getFluid()) || FluidHazards.of(fluid).corrosive();
    }

    public static ItemStack bucket(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        for (Entry entry : ENTRIES) if (entry.fluid().get().isSame(fluid.getFluid())) {
            ItemStack result = new ItemStack(entry.bucket().get());
            result.set(BUCKET_CONTENTS.get(), BucketFluidContents.of(fluid));
            return result;
        }
        return ItemStack.EMPTY;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        bus.addListener(CEWorldFluids::commonSetup);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        List<Item> items = new ArrayList<>();
        items.add(EMPTY_ALLOY_BUCKET.get());
        for (Entry entry : ENTRIES) items.add(entry.bucket().get());
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new ProcessBucketWrapper(stack), items.toArray(Item[]::new));
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new net.createplant.item.StainlessBucketWrapper(stack), STAINLESS_BUCKET.get());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DefaultDispenseItemBehavior filled = new DefaultDispenseItemBehavior() {
                @Override protected ItemStack execute(BlockSource source, ItemStack stack) {
                    ProcessBucketItem bucket = (ProcessBucketItem) stack.getItem();
                    BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    return bucket.emptyContents(null, source.level(), pos, null, stack)
                        ? consumeWithRemainder(source, stack, bucket.emptyBucket(stack)) : super.execute(source, stack);
                }
            };
            for (Entry entry : ENTRIES) DispenserBlock.registerBehavior(entry.bucket().get(), filled);
            DispenseItemBehavior ordinary = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET);
            DefaultDispenseItemBehavior empty = new DefaultDispenseItemBehavior() {
                @Override protected ItemStack execute(BlockSource source, ItemStack stack) {
                    BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    var state = source.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof WorldLiquidBlock block)) return super.execute(source, stack);
                    ItemStack result = stack.is(EMPTY_ALLOY_BUCKET.get()) ? block.pickupAlloy(source.level(), pos, state)
                        : block.pickupBlock(null, source.level(), pos, state);
                    return result.isEmpty() ? super.execute(source, stack) : consumeWithRemainder(source, stack, result);
                }
            };
            DispenserBlock.registerBehavior(Items.BUCKET, (source, stack) -> {
                BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                return source.level().getBlockState(pos).getBlock() instanceof WorldLiquidBlock
                    ? empty.dispense(source, stack) : ordinary.dispense(source, stack);
            });
            DispenserBlock.registerBehavior(EMPTY_ALLOY_BUCKET.get(), empty);
            DispenserBlock.registerBehavior(STAINLESS_BUCKET.get(), new DefaultDispenseItemBehavior() {
                @Override protected ItemStack execute(BlockSource source, ItemStack stack) {
                    BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    ItemStack result = StainlessBucketItem.pickup(source.level(), pos, source.level().getBlockState(pos), null);
                    return result.isEmpty() ? super.execute(source, stack) : consumeWithRemainder(source, stack, result);
                }
            });
            // Vanilla's water and lava buckets dispense into an iron bucket; a marked one goes back to stainless.
            for (Item item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
                DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(item);
                DispenserBlock.registerBehavior(item, (source, stack) -> {
                    boolean stainless = net.createplant.item.StainlessBuckets.marked(stack);
                    ItemStack result = original.dispense(source, stack);
                    return stainless && result.is(Items.BUCKET) ? net.createplant.item.StainlessBuckets.empty() : result;
                });
            }
        });
    }
}
