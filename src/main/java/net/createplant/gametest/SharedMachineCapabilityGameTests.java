package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEOstwaldFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** External capability contracts used by Create's identity-based branch accounting and cached ports. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedMachineCapabilityGameTests {
    private static final BlockPos PETRO = new BlockPos(2, 1, 2);
    private static final BlockPos ACID = new BlockPos(5, 1, 5);


    private static void sharedColumn(GameTestHelper helper, BlockPos base, int height, Fluid feed) {
        IFluidHandler bottom = fluids(helper, base);
        IFluidHandler top = fluids(helper, base.above(height - 1));
        helper.assertTrue(bottom != null && bottom == top,
            "Every formed column port must expose the same handler object so Create cannot reserve the same capacity twice");
        helper.assertTrue(items(helper, base) == items(helper, base.above(height - 1)),
            "Shared fluid and item buffers must each have a canonical external endpoint");
        helper.assertTrue(bottom.fill(new FluidStack(feed, 7_900), IFluidHandler.FluidAction.EXECUTE) == 7_900,
            "The finite input tank must accept the first 7900 mB");
        FluidStack remaining = new FluidStack(feed, 200);
        helper.assertTrue(top.fill(remaining, IFluidHandler.FluidAction.SIMULATE) == 100
            && bottom.getFluidInTank(0).getAmount() == 7_900,
            "A branch simulation may reserve only the shared 100 mB of remaining capacity and must not mutate storage");
        helper.assertTrue(top.fill(remaining, IFluidHandler.FluidAction.EXECUTE) == 100
            && bottom.getFluidInTank(0).getAmount() == 8_000
            && bottom.fill(remaining, IFluidHandler.FluidAction.EXECUTE) == 0,
            "Filling through one section must debit the same remaining capacity observed by every other branch");
    }




    private static void column(GameTestHelper helper, BlockPos base, Block block, int height) {
        for (int y = 0; y < height; y++) helper.setBlock(base.above(y), block);
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.NORTH);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.NORTH);
    }

    private static void activeBurner(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AllBlocks.BLAZE_BURNER.getDefaultState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
    }
}
