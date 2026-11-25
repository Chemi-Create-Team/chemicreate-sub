package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CETransport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
}
