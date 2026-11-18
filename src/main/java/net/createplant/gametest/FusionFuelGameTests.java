package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.CryogenicColdBoxBlock;
import net.createplant.block.entity.CryogenicColdBoxBlockEntity;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEFusionFuel;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CENuclear;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class FusionFuelGameTests {
    private static final BlockPos BOX = new BlockPos(6, 3, 5);
    private static CryogenicColdBoxBlockEntity box(GameTestHelper h) {
        h.setBlock(BOX, CEFusionFuel.COLD_BOX.get().defaultBlockState().setValue(CryogenicColdBoxBlock.HORIZONTAL_FACING, Direction.NORTH));
        var be = (CryogenicColdBoxBlockEntity)h.getBlockEntity(BOX);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = 1; z <= 3; z++)
            h.getLevel().setBlock(be.body(x, y, z), (x == 0 && y == 0 && z == 2 ? CEFusionFuel.REGENERATOR.get() : CEMetals.STEEL_BLOCK.get().getBlock()).defaultBlockState(), 3);
        be.setSpeed(256);be.step();
        return be;
    }
    private static void near(GameTestHelper h, double a, double b, String message) {
        h.assertTrue(Double.isFinite(a) && Math.abs(a - b) <= Math.max(1e-6, Math.abs(b) * 1e-8), message + ": " + a + " / " + b);
    }

    @GameTest(template = "test_empty")
    public static void coldBoxLiquefiesNitrogenOxygenAndHeliumWithPaidShaftWork(GameTestHelper h) {
        var be=box(h);
        for(var type:new net.minecraft.world.level.material.Fluid[]{CEFluids.NITROGEN_GAS.get(),CEFluids.OXYGEN_GAS.get(),CEFusion.HELIUM_GAS.get()}){
            be.gas().setFluid(FluidStack.EMPTY);be.liquid().setFluid(FluidStack.EMPTY);be.water().setFluid(new FluidStack(Fluids.WATER,32000));be.warm().setFluid(FluidStack.EMPTY);
            var conversion=net.createplant.thermal.ThermalStreams.liquefaction(new FluidStack(type,1));int amount=conversion.inputAmount()*10;
            be.port(Direction.UP).fill(new FluidStack(type,amount),EXECUTE);be.step();
            h.assertTrue(be.gas().isEmpty() && be.liquid().getFluid().is(conversion.output()) && be.liquid().getFluidAmount()==10,"The fixed liquefaction table processes "+type);
            int coolant=type==CEFusion.HELIUM_GAS.get() ? 2000 : 500;
            h.assertTrue(be.water().getFluidAmount()==32000-coolant && be.warm().getFluid().is(CEProcessFluids.WARM_WATER.get()) && be.warm().getFluidAmount()==coolant,"Finite water becomes the exact warm discharge");
            near(h,be.watts(),coolant*20d*4,"The actual shaft work pays the batch cost");
        }h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void coldBoxStopsWithoutWorkAndKeepsAllBuffersThroughSavingAndBreaking(GameTestHelper h) {
        var be = box(h);
        var preview = new FluidStack(CEFusion.HELIUM_GAS.get(), 10000);
        h.assertTrue(be.port(Direction.UP).fill(preview, SIMULATE) == 10000 && be.gas().isEmpty(), "Fill simulation leaves the helium buffer untouched");
        be.port(Direction.UP).fill(preview, EXECUTE);be.water().fill(new FluidStack(Fluids.WATER, 32000), EXECUTE);
        be.setSpeed(0);be.step();
        h.assertTrue(be.gas().getFluidAmount() == 10000 && be.watts() == 0, "A stationary shaft cannot prime or refrigerate");
        be.setSpeed(256);be.liquid().fill(FluidThermal.at(new FluidStack(CEFusion.LIQUID_HELIUM.get(), 16000), 4.2), EXECUTE);be.step();
        int gas = be.gas().getFluidAmount();
        h.assertTrue(be.watts() == 0 && be.water().getFluidAmount() == 32000, "A full liquid outlet consumes no shaft work or cooling water");
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved, h.getLevel().registryAccess());be.setSpeed(256);
        h.assertTrue(be.gas().getFluidAmount() == gas && be.liquid().getFluidAmount() == 16000 && be.watts() == 0, "Reload preserves cycle charge and both phases without inventing operating power");
        be.liquid().drain(Integer.MAX_VALUE, EXECUTE);h.getLevel().setBlock(be.body(1, 0, 2), Blocks.AIR.defaultBlockState(), 3);be.step();
        h.assertTrue(!be.formed() && be.watts() == 0 && be.gas().getFluidAmount() == gas, "A removed jacket stops processing before touching stored gas");
        h.assertTrue(be.port(Direction.NORTH) == null && be.port(Direction.SOUTH) == null && be.port(Direction.DOWN).fill(preview, EXECUTE) == 0, "Shaft/back faces and product drains cannot accept feed");
        h.succeed();
    }


    @GameTest(template = "test_empty")
    public static void aPartialGasBatchWaitsAndRemovedHandlersCannotDrain(GameTestHelper h) {
        var be=box(h);var inlet=be.port(Direction.UP);var outlet=be.port(Direction.DOWN);inlet.fill(new FluidStack(CEFusion.HELIUM_GAS.get(),1),EXECUTE);be.water().fill(new FluidStack(Fluids.WATER,32000),EXECUTE);be.step();be.step();
        h.assertTrue(be.gas().getFluidAmount()==1 && be.liquid().isEmpty() && be.watts()==0,"Less than one conversion batch waits without consuming gas, water or work");
        be.liquid().fill(new FluidStack(CEFusion.LIQUID_HELIUM.get(),1),EXECUTE);h.setBlock(BOX,Blocks.AIR);
        h.assertTrue(outlet.drain(1,EXECUTE).isEmpty() && inlet.fill(new FluidStack(CEFusion.HELIUM_GAS.get(),1),EXECUTE)==0 && outlet.getFluidInTank(0).isEmpty(),"Removed handlers cannot resurrect feed or drain destroyed stock");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void createPipesRecognizeTheFourColdBoxPortsAndRejectTheShaft(GameTestHelper h) {
        var be = box(h);
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}) {
            BlockPos pipe = be.getBlockPos().relative(side);
            h.getLevel().setBlock(pipe, AllBlocks.FLUID_PIPE.getDefaultState(), 3);
            h.assertTrue(FluidPipeBlock.canConnectTo(h.getLevel(), be.getBlockPos(), be.getBlockState(), side.getOpposite()), "Create pipe recognizes the cold-box port on " + side);
        }
        BlockPos shaft = be.getBlockPos().north();h.getLevel().setBlock(shaft, AllBlocks.FLUID_PIPE.getDefaultState(), 3);
        h.assertTrue(!FluidPipeBlock.canConnectTo(h.getLevel(), be.getBlockPos(), be.getBlockState(), Direction.SOUTH), "A pipe cannot attach to the rotary shaft");
        h.succeed();
    }


    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void aRealCreateNetworkStopsForInsufficientCapacityAndRecovers(GameTestHelper h) {
        var be = box(h);be.setSpeed(0);
        BlockPos motorPos = BOX.north();
        h.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
        h.runAfterDelay(10, () -> {
            var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(motorPos);
            motor.generatedSpeed.setValue(256);
        });
        h.runAfterDelay(20, () -> {
            var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(motorPos);
            h.assertTrue(Math.abs(be.getSpeed()) == 256 && be.getOrCreateNetwork() == motor.getOrCreateNetwork(), "The real front shaft joins the motor's rotating network");
            be.port(Direction.UP).fill(new FluidStack(CEFusion.HELIUM_GAS.get(), 100_000), EXECUTE);
            be.port(Direction.EAST).fill(new FluidStack(Fluids.WATER, 32_000), EXECUTE);
            be.step();
            h.assertTrue(be.watts() > 0 && !be.liquid().isEmpty(), "Sufficient real network capacity pays for cooldown");
            int gas = be.gas().getFluidAmount(), water = be.water().getFluidAmount();int produced = be.liquid().getFluidAmount();
            motor.getOrCreateNetwork().updateCapacityFor(motor, 1);
            for (int n = 0; n < 4; n++) be.step();
            h.assertTrue(be.watts() == 0 && be.gas().getFluidAmount() == gas && be.water().getFluidAmount() == water && be.liquid().getFluidAmount()==produced, "Insufficient network capacity rejects refrigeration before any resource or cold-state mutation");
            motor.getOrCreateNetwork().updateCapacityFor(motor, motor.calculateAddedStressCapacity());
            be.step();
            h.assertTrue(be.watts() > 0 && be.liquid().getFluidAmount()>produced, "Restoring source capacity restarts the same connected machine");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void heavyWaterElectrolysisSeparatesD2AndKeepsPreviewAndReloadSafe(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);
        ElectrolysisCellGameTests.feed(owner, new FluidStack(CEFusionFuel.HEAVY_WATER.get(), 30));ElectrolysisCellGameTests.run(owner, 10, 400);
        h.assertTrue(owner.stored(1) == 20 && owner.stored(2) == 10 && owner.fluid(1).is(CEFusion.DEUTERIUM_GAS.get()), "Heavy water produces D2 and oxygen without mislabelling hydrogen");
        var lookup = h.getLevel().registryAccess();for (var cell : owner.cells()) cell.loadWithComponents(cell.saveWithoutMetadata(lookup), lookup);
        var cell = (net.createplant.block.entity.ElectrolysisCellBlockEntity)h.getLevel().getBlockEntity(owner.structure().minimum());
        var out = cell.port(Direction.WEST);
        h.assertTrue(out.drain(7, SIMULATE).is(CEFusion.DEUTERIUM_GAS.get()) && owner.stored(1) == 20, "D2 survives reload; preview does not drain it");
        h.assertTrue(out.drain(new FluidStack(Fluids.WATER, 1), EXECUTE).isEmpty() && owner.stored(1) == 20, "A drain request cannot relabel the saved gas");h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 100)
    public static void lithiumTargetsNeedARealOperatingCoreAndDoNotCountAsFissionFuel(GameTestHelper h) {
        BlockPos base = new BlockPos(3, 6, 3);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 0; y < 3; y++) h.setBlock(base.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 1; y <= 3; y++) h.setBlock(base.offset(x, -y, z), CENuclear.FEED_BUNDLE.get());
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(base));
        h.assertTrue(result.formed(), "Compact test core forms");
        var lead = (FuelChannelBlockEntity)h.getLevel().getBlockEntity(result.controller());lead.adopt(result, null);
        var coolant = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(base.below(3)), Direction.DOWN);
        h.assertTrue(coolant != null && coolant.fill(new FluidStack(Fluids.WATER, 300_000), EXECUTE) == 300_000, "Real feed-bundle ports fill the compact core's complete finite coolant inventory");
        FuelChannelBlockEntity chosen = null;int targetSlot = -1;boolean sourceLoaded = false;
        for (BlockPos pos : lead.core().fuelBottoms()) {
            var column = (FuelChannelBlockEntity)h.getLevel().getBlockEntity(pos);
            for (int slot = 0; slot < 4; slot++) if (column.active(slot) && !column.control(slot)) {
                if (chosen == null) {
                    chosen = column;targetSlot = slot;
                    h.assertTrue(column.topItems().insertItem(slot, new ItemStack(CEFusionFuel.LITHIUM_TARGET.get()), true).isEmpty() && column.lithiumTargets() == 0, "Target insertion simulation leaves the fuel position empty");
                    for (int y = 3; y < column.layout().fuelAssemblyLength(); y++) h.getLevel().setBlock(column.getBlockPos().above(y), CENuclear.FUEL_CHANNEL_TUBE.get().defaultBlockState(), 3);
                    BlockPos capPos = column.getBlockPos().above(column.layout().fuelAssemblyLength());
                    h.getLevel().setBlock(capPos, CENuclear.TOP_CAP.get().defaultBlockState(), 3);
                    var cap = (net.createplant.block.entity.RodCapBlockEntity)h.getLevel().getBlockEntity(capPos);
                    h.assertTrue(cap.installFuel(slot, new ItemStack(CEFusionFuel.LITHIUM_TARGET.get())), "The real top cap installs the supplied lithium item instead of turning it into uranium fuel");
                    h.assertTrue(((net.createplant.block.ReactorCapBlock)CENuclear.TOP_CAP.get()).getInformationSource(h.getLevel(), capPos, cap.getBlockState()).equals(column.getBlockPos()), "Goggles on the target cap address the target's actual column");
                } else if (!sourceLoaded) {
                    column.topItems().insertItem(slot, new ItemStack(CENuclear.FUEL_ROD.get()), false);sourceLoaded = true;
                }
            }
        }
        var target = chosen;int slot = targetSlot;
        h.assertTrue(target.lithiumTargets() == 1 && target.fresh() < target.fuelChannels(), "Lithium target occupies one real hole without becoming fresh fission fuel");
        double stopped = target.targetExposure();target.irradiateTargets();
        h.assertTrue(target.targetExposure() == stopped, "A newly formed core at zero actual power cannot irradiate targets");
        lead.core().tick();
        double ratio = Math.clamp(lead.core().power() / lead.core().nominalWatts(), 0, 2);
        h.assertTrue(ratio > 0, "Real nuclear fuel creates a positive neutron-source power");
        double before = target.targetExposure();
        for (int second = 0; second < 3; second++) target.irradiateTargets();
        double measured = (target.targetExposure() - before) * FuelChannelBlockEntity.TARGET_FULL_POWER_SECONDS / 100;
        h.assertTrue(Math.abs(measured - 3 * ratio) < 1e-12, "Three irradiation quanta accumulate three seconds times the measured core-power fraction: " + measured + " / " + 3 * ratio);
        double held = target.targetExposure();lead.core().changed();target.irradiateTargets();
        h.assertTrue(target.targetExposure() == held && lead.core().irradiationPower() == 0, "Structural invalidation immediately prevents reuse of the previous core-power solution");
        var saved = target.saveWithoutMetadata(h.getLevel().registryAccess());
        target.loadWithComponents(saved, h.getLevel().registryAccess());
        near(h, target.targetExposure(), before + 3 * ratio / FuelChannelBlockEntity.TARGET_FULL_POWER_SECONDS * 100, "Saving and reloading retains the measured accumulated exposure");
        saved.putDouble("Burnt" + slot, FuelChannelBlockEntity.TARGET_FULL_POWER_SECONDS - 1e-12);
        target.loadWithComponents(saved, h.getLevel().registryAccess());lead.adopt(ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(base)), null);
        h.assertTrue(target.targetExposure() > 99 && target.topItems().extractItem(slot, 1, true).isEmpty(), "An incomplete target retains its exposure and cannot be extracted as a completed product");
        h.runAfterDelay(60, () -> {
            h.assertTrue(lead.core().power() > 0 && target.irradiatedTargets() == 1, "Real core heat generation completes the near-finished target: power=" + lead.core().power() + ", state=" + lead.core().status() + ", targets=" + target.lithiumTargets() + "/" + target.irradiatedTargets() + ", exposure=" + target.targetExposure());
            var product = target.topItems().extractItem(slot, 1, false);
            h.assertTrue(product.is(CEFusionFuel.IRRADIATED_TARGET.get()) && target.topItems().extractItem(slot, 1, false).isEmpty(), "The completed target is extracted once through the standard item handler");
            h.succeed();
        });
    }
    @GameTest(template="test_empty")
    public static void aColdBoxReloadRetiresOldPortsWithoutReplayingTheBatch(GameTestHelper h){
        var be=box(h);var in=be.port(Direction.UP);var out=be.port(Direction.DOWN);int ratio=net.createplant.thermal.ThermalStreams.liquefaction(new FluidStack(CEFusion.HELIUM_GAS.get(),1)).inputAmount();
        in.fill(new FluidStack(CEFusion.HELIUM_GAS.get(),ratio*10),EXECUTE);be.water().fill(new FluidStack(Fluids.WATER,32000),EXECUTE);be.step();
        h.assertTrue(be.liquid().getFluidAmount()==10,"Ten exact batches complete");be.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());be.setSpeed(256);be.step();
        h.assertTrue(in.fill(new FluidStack(CEFusion.HELIUM_GAS.get(),ratio),EXECUTE)==0 && out.drain(1,EXECUTE).isEmpty(),"A held capability cannot reach the reloaded owner");
        h.assertTrue(be.port(Direction.DOWN).drain(100,EXECUTE).getAmount()==10 && be.gas().isEmpty() && be.water().getFluidAmount()==30000 && be.warm().getFluidAmount()==2000,"Reload preserves all stocks and cannot replay liquefaction");h.succeed();
    }
}
