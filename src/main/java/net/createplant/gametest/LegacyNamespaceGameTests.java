package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Saves from before the mod took its current id load with the same blocks, items, fluids and block entities. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyNamespaceGameTests {
    private static String old(String path) { return "create_heavy_indusstry:" + path; }

    @GameTest(template = "test_reactor")
    public static void oldIdsLoadAsTheCurrentObjects(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(old("tank_casing"))) == CEBlocks.TANK_CASING.get()
            && BuiltInRegistries.ITEM.get(ResourceLocation.parse(old("steel_ingot"))) == CEItems.STEEL_INGOT.get()
            && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(old("tank_casing"))) == CEBlockEntityTypes.TANK_CASING.get(),
            "Old block, item, fluid and block entity ids resolve to the current ones");
        var boiler = ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, "tubular_boiler");
        h.assertTrue(BuiltInRegistries.BLOCK.get(boiler) == CETransport.HORIZONTAL_FLUID_TANK.get() && BuiltInRegistries.ITEM.get(boiler) == CETransport.HORIZONTAL_FLUID_TANK.get().asItem()
            && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(boiler) == CETransport.HORIZONTAL_FLUID_TANK_ENTITY.get()
            && BuiltInRegistries.BLOCK.get(ResourceLocation.parse(old("tubular_boiler"))) == CETransport.HORIZONTAL_FLUID_TANK.get(),
            "The retired tube boiler loads as the horizontal tank, which now boils water itself");
        var lookup = h.getLevel().registryAccess();
        var state = new CompoundTag();state.putString("Name", old("tank_casing"));
        h.assertTrue(NbtUtils.readBlockState(lookup.lookupOrThrow(Registries.BLOCK), state).is(CEBlocks.TANK_CASING.get()), "A chunk palette entry with the old id is the block");
        var item = new CompoundTag();item.putString("id", old("steel_ingot"));item.putInt("count", 5);
        var stack = ItemStack.parseOptional(lookup, item);
        h.assertTrue(stack.is(CEItems.STEEL_INGOT.get()) && stack.getCount() == 5, "A saved stack with the old id keeps its item and count: " + stack);
        var fluid = new CompoundTag();fluid.putString("id", old("nitrogen_gas"));fluid.putInt("amount", 250);
        var saved = FluidStack.parseOptional(lookup, fluid);
        h.assertTrue(saved.is(net.createplant.registry.CEFluids.NITROGEN_GAS.get()) && saved.getAmount() == 250, "A saved fluid with the old id keeps its fluid and amount: " + saved);
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void retiredClarifierLiningLoadsAsTankCasing(GameTestHelper h) {
        var level = h.getLevel();
        var lookup = level.registryAccess();
        var casing = CEBlocks.TANK_CASING.get();
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        for (String id : new String[]{CreatePlant.location("clarifier_basin_wall").toString(), old("clarifier_basin_wall")}) {
            var retired = ResourceLocation.parse(id);
            h.assertTrue(!BuiltInRegistries.BLOCK.keySet().contains(retired) && !BuiltInRegistries.ITEM.keySet().contains(retired),
                "The old lining is an alias, not a separate block or item");
            var palette = new CompoundTag();
            palette.putString("Name", id);
            palette.put("Properties", new CompoundTag());
            palette.getCompound("Properties").putString("facing", "down");
            var state = NbtUtils.readBlockState(lookup.lookupOrThrow(Registries.BLOCK), palette);
            h.assertTrue(state.is(casing), "A saved wall palette entry resolves to tank casing");

            var item = new CompoundTag();
            item.putString("id", id);
            item.putInt("count", 6);
            var stack = ItemStack.parseOptional(lookup, item);
            h.assertTrue(stack.is(casing.asItem()) && stack.getCount() == 6, "Stored linings keep their stack size as tank casings");

            var part = new CompoundTag();
            part.putString("id", ResourceLocation.fromNamespaceAndPath(retired.getNamespace(), "wet_process_part").toString());
            part.putLong("Controller", pos.above(2).asLong());
            var wall = BlockEntity.loadStatic(pos, state, part, lookup);
            h.assertTrue(wall != null && wall.getType() == CEBlockEntityTypes.TANK_CASING.get()
                && wall.saveWithId(lookup).getString("id").equals(CreatePlant.location("tank_casing").toString()),
                "A saved wall entity loads and saves with the casing type");

            var feed = CEWetProcessing.PARTS.get(net.createplant.block.WetProcessPartBlock.Role.FEED).get().defaultBlockState();
            var port = BlockEntity.loadStatic(pos, feed, part, lookup);
            h.assertTrue(port != null && port.getType() == CEWetProcessing.PART_ENTITY.get(),
                "The same saved entity id still loads as a port on a feed block");
        }
        h.assertTrue(level.getRecipeManager().byKey(CreatePlant.location("clarifier_basin_wall")).isEmpty(), "The lining recipe is removed");
        h.succeed();
    }
}
