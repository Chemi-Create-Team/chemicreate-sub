package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBuildingMaterials;
import net.createplant.registry.CESharedChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingMaterialsGameTests {
    private BuildingMaterialsGameTests() { }

    private static BasinBlockEntity basin(GameTestHelper h, BlockPos at) {
        h.setBlock(at, AllBlocks.BASIN.get());
        return (BasinBlockEntity) h.getBlockEntity(at);
    }

    private static Recipe<?> recipe(GameTestHelper h, String name) {
        return h.getLevel().getRecipeManager().byKey(CreatePlant.location(name)).orElseThrow().value();
    }

    private static void insert(GameTestHelper h, BasinBlockEntity basin, ItemStack stack) {
        h.assertTrue(ItemHandlerHelper.insertItemStacked(basin.getInputInventory(), stack, false).isEmpty(), "The real basin accepts " + stack);
    }

    private static int count(IItemHandler inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) count += inventory.getStackInSlot(slot).getCount();
        return count;
    }

    private static int water(net.neoforged.neoforge.fluids.capability.IFluidHandler port) {
        int amount = 0;
        for (int tank = 0; tank < port.getTanks(); tank++) if (port.getFluidInTank(tank).is(Fluids.WATER)) amount += port.getFluidInTank(tank).getAmount();
        return amount;
    }

    @GameTest(template = "test_reactor")
    public static void clinkerNeedsSuperheatAndTheBasinCommitsOnce(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var basin = basin(h, pos);
        var kiln = recipe(h, "mixing/cement_clinker");
        insert(h, basin, new ItemStack(Items.CALCITE, 4));insert(h, basin, new ItemStack(Items.CLAY_BALL));
        insert(h, basin, new ItemStack(Items.SAND));insert(h, basin, new ItemStack(Items.IRON_NUGGET));
        h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        h.assertTrue(!BasinRecipe.apply(basin, kiln) && count(basin.getInputInventory()) == 7,
            "Ordinary heat cannot sinter clinker and does not consume the raw meal");
        h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
        basin.tick();
        h.assertTrue(BasinRecipe.match(basin, kiln) && count(basin.getInputInventory()) == 7 && count(basin.getOutputInventory()) == 0,
            "Matching the recipe simulates without producing or consuming clinker");
        h.assertTrue(BasinRecipe.apply(basin, kiln) && count(basin.getInputInventory()) == 0 && count(basin.getOutputInventory()) == 4,
            "Superheated Create processing converts one finite charge into four clinker nodules");
        h.assertTrue(!BasinRecipe.apply(basin, kiln) && count(basin.getOutputInventory()) == 4, "An exhausted charge cannot run twice");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void cementAndConcreteUseRealBasinPortsAndPauseWhenFull(GameTestHelper h) {
        var cementBasin = basin(h, new BlockPos(2, 2, 2));
        insert(h, cementBasin, new ItemStack(CEBuildingMaterials.GROUND_CLINKER.get(), 8));
        insert(h, cementBasin, new ItemStack(CESharedChemicals.GYPSUM.get()));
        h.assertTrue(BasinRecipe.apply(cementBasin, recipe(h, "mixing/cement_powder")) && count(cementBasin.getOutputInventory()) == 8,
            "Ground clinker and the existing gypsum become eight binder portions");
        BlockPos pos = new BlockPos(6, 2, 2);
        var concreteBasin = basin(h, pos);
        insert(h, concreteBasin, cementBasin.getOutputInventory().extractItem(0, 1, false));
        insert(h, concreteBasin, new ItemStack(Items.SAND, 4));insert(h, concreteBasin, new ItemStack(Items.GRAVEL, 4));
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.NORTH);
        h.assertTrue(port != null && port.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
            "The same standard fluid capability used by Create pipes supplies the mixing water");
        var output = concreteBasin.getOutputInventory();
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        var concrete = recipe(h, "mixing/industrial_concrete");
        h.assertTrue(!BasinRecipe.apply(concreteBasin, concrete) && count(concreteBasin.getInputInventory()) == 9
            && water(port) == 1000, "A blocked outlet retains all aggregates, binder and water");
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
        h.assertTrue(BasinRecipe.apply(concreteBasin, concrete) && count(output) == 8 && count(concreteBasin.getInputInventory()) == 0
            && water(port) == 0, "Clearing the outlet resumes a charge and consumes exactly one bucket of water");
        h.assertTrue(count(cementBasin.getOutputInventory()) == 7, "Unused cement remains available for subsequent building batches");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void slabsKeepTheirVolume(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var slab = CEBuildingMaterials.CONCRETE_SLAB.get().defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
        var drops = Block.getDrops(slab, level, pos, null);
        h.assertTrue(drops.stream().filter(stack -> stack.is(CEBuildingMaterials.CONCRETE_SLAB.get().asItem())).mapToInt(ItemStack::getCount).sum() == 2,
            "Breaking a double slab returns both halves");
        h.succeed();
    }
}
