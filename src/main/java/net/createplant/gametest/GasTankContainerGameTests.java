package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.GasTankContainerBlock;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GasTankContainerGameTests {
    /** Lays a run of containers east from {@code first}, {@code width} across and {@code length} long. */
    private static void run(GameTestHelper h, BlockPos first, int width, int length) {
        var state = CETransport.GAS_TANK_CONTAINER.get().defaultBlockState().setValue(GasTankContainerBlock.AXIS, Direction.Axis.X);
        for (int x = 0; x < length; x++) for (int y = 0; y < width; y++) for (int z = 0; z < width; z++) h.setBlock(first.offset(x, y, z), state);
    }

    private static int size(GameTestHelper h, BlockPos pos) { return h.getBlockState(pos).getValue(GasTankContainerBlock.SIZE); }

    private static boolean open(GameTestHelper h, BlockPos pos) { return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.UP) != null; }

    @GameTest(template = "test_fractionation")
    public static void onlyTheContainerSizesFormAGasTank(GameTestHelper h) {
        int z = 1;
        for (int width = 1; width <= 3; width++) {
            var first = new BlockPos(1, 2, z);
            int length = GasTankContainerBlock.length(width);
            run(h, first, width, length);
            var far = first.offset(length - 1, width - 1, width - 1);
            var root = ((FluidTankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(first))).getControllerBE();
            h.assertTrue(size(h, first) == width && size(h, far) == width && open(h, first) && open(h, far),
                "A " + width + " x " + width + " run " + length + " long is one finished tank open to pipes at every block: " + size(h, first) + "/" + size(h, far) + " " + open(h, first) + " " + root.getWidth() + "x" + root.getHeight());
            h.assertTrue(root.getWidth() == width && root.getHeight() == length, "The tank holds the whole run: " + root.getWidth() + "x" + root.getHeight());
            z += width + 1;
        }
        var short2 = new BlockPos(1, 2, 12);
        run(h, short2, 2, 3);
        h.assertTrue(size(h, short2) == 0 && !open(h, short2), "A 2 x 2 run three long is no tank and takes no pipes");
        run(h, new BlockPos(1, 6, 1), 1, 1);
        h.assertTrue(size(h, new BlockPos(1, 6, 1)) == 0 && !open(h, new BlockPos(1, 6, 1)), "A single block is no tank");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void aFinishedTankHoldsGasOnlyAndFormsAgainWhenRepaired(GameTestHelper h) {
        var first = new BlockPos(2, 2, 2);
        run(h, first, 2, 5);
        var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(first.east(2)), Direction.NORTH);
        h.assertTrue(handler != null && handler.fill(new FluidStack(net.createplant.registry.CEPetroFluids.DIESEL.get(), 1000), FluidAction.SIMULATE) == 0, "A liquid is turned away");
        var gas = FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(), 4000), 293.15);
        h.assertTrue(handler.fill(gas, FluidAction.EXECUTE) == 4000, "Nitrogen goes in");
        var last = first.offset(4, 1, 1);
        var removed = h.getBlockState(last);
        h.setBlock(last, net.minecraft.world.level.block.Blocks.AIR);
        h.runAfterDelay(3, () -> {
            h.assertTrue(size(h, first) == 0 && !open(h, first), "Without one block the run is no longer a tank");
            h.setBlock(last, removed.setValue(GasTankContainerBlock.SIZE, 0));
            h.runAfterDelay(3, () -> {
                var root = ((FluidTankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(first))).getControllerBE();
                h.assertTrue(size(h, first) == 2 && open(h, first) && root.getTankInventory().getFluidAmount() == 4000 - 4000 / 20,
                    "Putting the block back finishes the tank again, keeping all the gas but the removed block's share, as Create's tanks do: " + root.getTankInventory().getFluidAmount());
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_fractionation")
    public static void wetGasLeavesDrainWaterAtTheBottom(GameTestHelper h) {
        var first=new BlockPos(2,2,2);
        run(h,first,1,GasTankContainerBlock.length(1));
        var world=h.getLevel();
        var at=h.absolutePos(first);
        var side=world.getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.NORTH);
        var drain=world.getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.DOWN);
        var wet=net.createplant.mixture.MixtureHelper.merge(new FluidStack(CEFluids.NITROGEN_GAS.get(),500),
            FluidForm.STEAM.stack(100),true);
        h.assertTrue(side!=null && drain!=null && side.fill(wet,FluidAction.EXECUTE)==600,"Wet gas enters the tank as one mixture");
        h.runAfterDelay(24,()-> {
            var water=new FluidStack(net.minecraft.world.level.material.Fluids.WATER,100);
            h.assertTrue(drain.drain(water,FluidAction.SIMULATE).getAmount()==100 &&
                drain.drain(water,FluidAction.EXECUTE).getAmount()==100,"A bottom pump can take the condensed water");
            h.assertTrue(side.getFluidInTank(0).is(CEFluids.NITROGEN_GAS.get()) && side.getFluidInTank(0).getAmount()==500,
                "Draining the water keeps the original dry gas volume");
            h.succeed();
        });
    }
}
