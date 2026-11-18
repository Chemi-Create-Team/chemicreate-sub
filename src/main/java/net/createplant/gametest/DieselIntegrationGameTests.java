package net.createplant.gametest;

import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.CDGFluids;
import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.CDGRegistries;
import com.jesz.createdieselgenerators.CDGTags;
import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity;
import com.jesz.createdieselgenerators.fuel_type.FuelType;
import net.createplant.CreatePlant;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class DieselIntegrationGameTests {
    private static void near(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.000001,message+": "+actual+" vs "+expected);
    }
    @GameTest(template="test_fractionation")
    public static void refineryUsesNativeFluidsFuelTagsAndPumpjackOutput(GameTestHelper h) {
        h.assertTrue(CEPetroFluids.CRUDE_OIL.get()==CDGFluids.CRUDE_OIL.getSource()
            && CEPetroFluids.GASOLINE.get()==CDGFluids.GASOLINE.getSource()
            && CEPetroFluids.DIESEL.get()==CDGFluids.DIESEL.getSource(),"No wrapper fluids replace CDG definitions");
        for (String name:new String[]{"crude_oil","gasoline","diesel","heated_crude_oil"}) {
            h.assertTrue(!BuiltInRegistries.FLUID.containsKey(CreatePlant.location(name)),"Duplicate fluid removed: "+name);
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(CreatePlant.location(name+"_bucket")),"Duplicate bucket removed: "+name);
        }
        h.assertTrue(CDGFluids.CRUDE_OIL.getSource().is(CDGTags.PUMPJACK_OUTPUT),"Unchanged Pumpjack output feeds the refinery");
        var fuels=h.getLevel().registryAccess().lookupOrThrow(CDGRegistries.FUEL_TYPE);
        for (var entry:java.util.List.of(CDGFluids.GASOLINE,CDGFluids.DIESEL,CDGFluids.BIODIESEL,CDGFluids.ETHANOL,CDGFluids.PLANT_OIL)) {
            var type=FuelType.getTypeFor(fuels,entry.getSource());
            h.assertTrue(type!=FuelType.EMPTY && type.isFlammable(),"Native fuel data remains loaded: "+entry.getId());
            var stack=FluidThermal.at(new FluidStack((net.minecraft.world.level.material.Fluid)entry.getSource(),100),450);
            h.assertTrue(FuelType.getTypeFor(fuels,stack.getFluid())==type,"Temperature cannot change fuel identity");
            h.assertTrue(stack.getFluid().defaultFluidState().createLegacyBlock().getBlock()==entry.getSource().defaultFluidState().createLegacyBlock().getBlock(),"World block remains native");
        }
        h.assertTrue(FuelType.getTypeFor(fuels,CDGFluids.CRUDE_OIL.getSource())==FuelType.EMPTY,"Crude does not acquire fuel-only explosion behavior");
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void nativePumpjackExtractsTheSharedCrudeWithoutAConverter(GameTestHelper h) {
        BlockPos at=new BlockPos(4,4,4);h.setBlock(at,CDGBlocks.PUMPJACK_HOLE.get());
        h.setBlock(at.below(),com.simibubi.create.AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
        h.setBlock(at.below(2),Blocks.BEDROCK);
        var hole=(com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity)h.getBlockEntity(at);
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(at));
        com.jesz.createdieselgenerators.world.OilChunksSavedData.setChunkOilAmount(h.getLevel(),chunk,4000);
        for (int i=0;i<20;i++) hole.tick();
        hole.headPos=1;hole.bearingPos=1;hole.pumpjackRotation(false);
        var port=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(at),null);
        var crude=port.drain(1000,SIMULATE);
        h.assertTrue(crude.is(CEPetroFluids.CRUDE_OIL.get()) && crude.getAmount()==1000,"Native rotation extracts the shared fluid from the oilfield");
        h.assertTrue(com.jesz.createdieselgenerators.world.OilChunksSavedData.getChunkOilAmount(h.getLevel(),chunk)==3000,"Native extraction debits the oilfield once");
        var stored=new ThermalFluidTank(1000,f -> {});
        stored.fill(port.drain(1000,EXECUTE),EXECUTE);
        h.assertTrue(stored.getFluid().is(CEPetroFluids.CRUDE_OIL.get()) && stored.getFluidAmount()==1000,"No intermediate fluid conversion is needed");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void nativeEnginesKeepNominalFuelGradesAndTheirFuelPerformance(GameTestHelper h) {
        Block[] engines={CDGBlocks.DIESEL_ENGINE.get(),CDGBlocks.MODULAR_DIESEL_ENGINE.get(),CDGBlocks.HUGE_DIESEL_ENGINE.get()};
        for (int i=0;i<engines.length;i++) {
            BlockPos pos=new BlockPos(3+5*i,2,4);h.setBlock(pos,engines[i]);
            var engine=(IEngine)h.getBlockEntity(pos);var tank=engine.getTank();
            h.assertTrue(tank!=null,"Each native engine exposes its original fuel tank");
            var hot=FluidThermal.at(new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.DIESEL.getSource(),40),400);
            var cold=FluidThermal.at(new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.DIESEL.getSource(),60),300);
            h.assertTrue(tank.fill(hot,SIMULATE)==40 && tank.isEmpty(),"Engine simulations are pure");
            tank.fill(hot,EXECUTE);h.assertTrue(tank.fill(cold,SIMULATE)==60 && tank.getFluidAmount()==40,"Different temperatures simulate correctly");
            tank.fill(cold,EXECUTE);near(h,FluidThermal.kelvin(tank.getFluid()),293,"Engine keeps its nominal fuel temperature");
            h.assertTrue(engine.validFS() && engine.getFuelSpeed()>0 && engine.getFuelBurnRate()>0,"Heated refined fuel runs the original engine");
            near(h,engine.getFuelSpeed(),engine.getFuelType().getGenerated(engine.self()).speed(),"Original engine fuel performance");
            var saved=engine.self().saveWithoutMetadata(h.getLevel().registryAccess());
            var restored=engine.self().getType().create(engine.self().getBlockPos(),engine.self().getBlockState());
            restored.setLevel(h.getLevel());restored.loadWithComponents(saved,h.getLevel().registryAccess());
            near(h,FluidThermal.kelvin(((IEngine)restored).getTank().getFluid()),293,"Engine NBT preserves the nominal fuel temperature");
            h.assertTrue(tank.drain(17,EXECUTE).getAmount()==17 && tank.getFluidAmount()==83,"Native engine tank cannot duplicate fuel");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void nativeOilBarrelCombinesAndSerializesFuel(GameTestHelper h) {
        BlockPos pos=new BlockPos(4,2,4);h.setBlock(pos,CDGBlocks.OIL_BARREL.get());
        var barrel=h.getBlockEntity(pos);
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),Direction.NORTH);
        h.assertTrue(handler!=null,"CDG barrel retains its native capability");
        handler.fill(FluidThermal.at(new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.GASOLINE.getSource(),200),500),EXECUTE);
        handler.fill(FluidThermal.at(new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.GASOLINE.getSource(),800),300),EXECUTE);
        near(h,FluidThermal.kelvin(handler.getFluidInTank(0)),293,"Barrel merges hot and cold fuel");
        var saved=barrel.saveWithoutMetadata(h.getLevel().registryAccess());
        barrel.loadWithComponents(saved,h.getLevel().registryAccess());
        var out=handler.drain(1000,EXECUTE);
        h.assertTrue(out.is(CEPetroFluids.GASOLINE.get()) && out.getAmount()==1000,"Barrel emits the original gasoline");
        near(h,FluidThermal.kelvin(out),293,"Barrel preserves stored temperature");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void canistersToolsSpoutsAndMountedBarrelsPreserveNominalFuel(GameTestHelper h) {
        BlockPos pos=new BlockPos(4,2,4);h.setBlock(pos,CDGBlocks.CANISTER.get());
        var block=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),null);
        var hot=FluidThermal.at(new FluidStack(CEPetroFluids.DIESEL.get(),40),400);
        var cold=FluidThermal.at(new FluidStack(CEPetroFluids.DIESEL.get(),60),300);
        block.fill(hot,EXECUTE);
        var spout=new com.jesz.createdieselgenerators.content.canister.SpoutCanisterFilling();
        h.assertTrue(spout.fillBlock(h.getLevel(),h.absolutePos(pos),null,cold,true)==60 && block.getFluidInTank(0).getAmount()==40,"Canister spout previews accept a different temperature without mutation");
        spout.fillBlock(h.getLevel(),h.absolutePos(pos),null,cold,false);
        near(h,FluidThermal.kelvin(block.getFluidInTank(0)),293,"Placed canisters combine their finite fuel");
        var item=new ItemStack(CDGBlocks.CANISTER.asItem());
        var tool=(com.jesz.createdieselgenerators.content.tools.FueledToolItem)item.getItem();
        var handler=tool.getFluidHandler(item);handler.fill(hot,EXECUTE);handler.fill(cold,EXECUTE);
        near(h,FluidThermal.kelvin(tool.readFluid(item)),293,"Native tool component preserves the nominal fuel temperature");
        var restored=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)item.save(h.getLevel().registryAccess()));
        near(h,FluidThermal.kelvin(tool.readFluid(restored)),293,"Carried canister serializes its mixture");
        var mounted=new com.jesz.createdieselgenerators.content.oil_barrel.OilBarrelMountedStorage(100,hot.copy());
        h.assertTrue(mounted.fill(cold,SIMULATE)==60 && mounted.getFluid().getAmount()==40 && !mounted.isDirty(),"Mounted barrel simulation has no side effects");
        h.assertTrue(mounted.fill(cold,EXECUTE)==60 && mounted.isDirty(),"Mounted barrel retains native synchronization callbacks");
        near(h,FluidThermal.kelvin(mounted.drain(100,EXECUTE)),293,"Mounted barrel mixes and drains the same fuel");
        var reduced=new ThermalFluidTank(100,f -> {});reduced.fill(hot,EXECUTE);reduced.setCapacity(20);
        h.assertTrue(reduced.fill(cold,SIMULATE)==0 && reduced.fill(cold,EXECUTE)==0 && reduced.getFluidAmount()==40,"Reducing capacity preserves contents and refuses further fuel");
        var undersized=new com.jesz.createdieselgenerators.content.oil_barrel.OilBarrelMountedStorage(20,hot.copy());
        h.assertTrue(undersized.fill(cold,EXECUTE)==0 && undersized.getFluid().getAmount()==40,"Mounted storage also preserves over-capacity contents without a negative fill");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void distillationIsDisabledWithoutRemovingOilfieldsOrFuelData(GameTestHelper h) {
        for (var recipe:h.getLevel().getRecipeManager().getRecipes()) {
            h.assertTrue(!BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType()).equals(ResourceLocation.parse("createdieselgenerators:distillation")),"CDG distillation recipes are removed on reload");
            h.assertTrue(!recipe.value().getResultItem(h.getLevel().registryAccess()).is(CDGItems.DISTILLATION_CONTROLLER.get()),"No crafting route to the obsolete controller");
        }
        BlockPos at=new BlockPos(4,2,4);h.setBlock(at,CDGBlocks.DISTILLATION_TANK.get());
        var tower=(DistillationTankBlockEntity)h.getBlockEntity(at);
        tower.tankInventory.fill(new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.CRUDE_OIL.getSource(),1000),EXECUTE);
        tower.progress=17;
        for (int i=0;i<250;i++) tower.tick();
        h.assertTrue(tower.tankInventory.getFluidAmount()==1000 && tower.progress==17,"Command-placed CDG towers do not process");
        var controller=new ItemStack(CDGItems.DISTILLATION_CONTROLLER.get());
        var context=new UseOnContext(h.getLevel(),null,InteractionHand.MAIN_HAND,controller,new BlockHitResult(Vec3.atCenterOf(h.absolutePos(at)),Direction.UP,h.absolutePos(at),false));
        h.assertTrue(controller.useOn(context)==InteractionResult.FAIL,"The controller cannot convert normal Create tanks");
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("createdieselgenerators:pumpjack_hole")),"Native extraction is not removed");h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void nativeAndRefinedGasolineReactIdenticallyToTnt(GameTestHelper h) {
        var tank=new ThermalFluidTank(1000,f -> {});
        tank.fill(FluidThermal.at(new FluidStack(CEPetroFluids.GASOLINE.get(),1000),350),EXECUTE);
        var refined=tank.drain(1000,EXECUTE);
        BlockPos nativePos=h.absolutePos(new BlockPos(5,8,5)),refinedPos=nativePos.east(10);
        var original=new FluidStack((net.minecraft.world.level.material.Fluid)CDGFluids.GASOLINE.getSource(),1000);
        h.getLevel().setBlockAndUpdate(nativePos,original.getFluid().defaultFluidState().createLegacyBlock());
        h.getLevel().setBlockAndUpdate(refinedPos,refined.getFluid().defaultFluidState().createLegacyBlock());
        for (BlockPos pos:java.util.List.of(nativePos,refinedPos)) {
            var tnt=new PrimedTnt(h.getLevel(),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,null);tnt.setFuse(1);h.getLevel().addFreshEntity(tnt);
        }
        h.runAfterDelay(15,()->{
            h.assertTrue(h.getLevel().getFluidState(nativePos).isEmpty() && h.getLevel().getFluidState(refinedPos).isEmpty(),"Native CDG server detonation consumes both gasoline sources");
            h.succeed();
        });
    }
}
