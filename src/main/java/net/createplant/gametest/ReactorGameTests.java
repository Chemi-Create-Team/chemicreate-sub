package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.chemistry.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorGameTests {
    private static final BlockPos BASE=new BlockPos(4,2,4);

    @GameTest(template="test_reactor")
    public static void compactFuelManufacturingKeepsTheExistingMachinesAndAllIngredients(GameTestHelper h) {
        var enrichment=ProcessRecipe.all().stream().filter(recipe->recipe.id().equals("enrich_compact_uranium")).findFirst().orElseThrow();
        h.assertTrue(enrichment.module()==ProcessModule.GAS_CENTRIFUGE && enrichment.inputs().size()==1
            && enrichment.inputs().getFirst().is(net.createplant.registry.CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.inputs().getFirst().getAmount()==1000 && enrichment.outputs().getFirst().is(net.createplant.registry.CEUranium.COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.outputs().getFirst().getAmount()==789 && enrichment.outputs().getLast().is(net.createplant.registry.CEUranium.DEPLETED_URANIUM_HEXAFLUORIDE.get())
            && enrichment.outputs().getLast().getAmount()==211,"The existing centrifuge further enriches real 4% feed and preserves the 1000 mB product/tails balance");
        var manager=h.getLevel().getRecipeManager();
        var dioxide=(net.createplant.recipe.PressureReactionRecipe)manager.byKey(CreatePlant.location("pressure_reaction/compact_uranium_dioxide")).orElseThrow().value();
        h.assertTrue(dioxide.module()==ReactorModule.STIRRED && dioxide.getFluidIngredients().size()==3 && dioxide.getIngredients().isEmpty()
            && dioxide.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.NONE,"Reconversion combines enriched gas, liquid water and hydrogen in one stirred batch");
        var pellet=(net.createplant.recipe.PressureReactionRecipe)manager.byKey(CreatePlant.location("pressure_reaction/compact_uranium_pellet")).orElseThrow().value();
        h.assertTrue(pellet.module()==ReactorModule.ABSORBER && pellet.getIngredients().size()==1 && pellet.getFluidIngredients().isEmpty()
            && pellet.getRequiredHeat()==com.simibubi.create.content.processing.recipe.HeatCondition.HEATED,"A heated tray fires pressed pellets without another gas feed");
        h.assertTrue(manager.byKey(CreatePlant.location("smelting/compact_uranium_pellet")).orElseThrow().value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe,
            "The ordinary furnace and Create's burner fan can also fire the pressed pellet");
        var pressing=manager.byKey(CreatePlant.location("pressing/green_compact_uranium_pellet")).orElseThrow().value();
        h.assertTrue(pressing instanceof com.simibubi.create.content.kinetics.press.PressingRecipe
            && pressing.getResultItem(h.getLevel().registryAccess()).is(net.createplant.registry.CEUranium.GREEN_COMPACT_URANIUM_PELLET.get())
            && pressing.getResultItem(h.getLevel().registryAccess()).getCount()==2,"The ordinary mechanical press makes the two green compact pellets");
        var rod=manager.byKey(CreatePlant.location("compact_enriched_fuel_rod")).orElseThrow().value();
        h.assertTrue(rod instanceof net.minecraft.world.item.crafting.ShapedRecipe
            && rod.getResultItem(h.getLevel().registryAccess()).is(net.createplant.registry.CENuclear.COMPACT_ENRICHED_FUEL_ROD.get())
            && rod.getIngredients().stream().filter(ingredient->ingredient.test(net.createplant.registry.CEUranium.COMPACT_URANIUM_PELLET.toStack())).count()==7
            && rod.getIngredients().stream().filter(ingredient->ingredient.test(CEItems.ZIRCALOY_CLADDING_TUBE.toStack())).count()==2,"Seven compact pellets and two existing cladding tubes make the identifiable short rod");
        h.succeed();
    }
    public static ReactorMachineBlockEntity assemble(ServerLevel level,BlockPos base,ReactorModule kind,int sections) {
        if (kind==ReactorModule.PREHEATER) return ThermalControlGameTests.build(level,base,Direction.NORTH,sections);
        if (kind==ReactorModule.TOWER) return AbsorptionTowerGameTests.tower(level,base.offset(1,0,1),sections,false);
        for (int y=1;y<=sections;y++) layer(level,base,y,kind);
        var be=(ReactorMachineBlockEntity)level.getBlockEntity(base);be.validateStructure();return be;
    }
    /** One layer at the module's supported width, filled from the lowest empty internal slot. */
    public static void layer(ServerLevel level,BlockPos base,int y,ReactorModule kind) {
        int width=3;
        for (int x=0;x<width;x++) for (int z=0;z<width;z++) level.setBlock(base.offset(x,y-1,z),CETransport.LIDDED_VESSEL.get().defaultBlockState(),3);
        if (level.getBlockEntity(base.above(y-1)) instanceof LiddedVesselBlockEntity part) {
            var owner=part.getControllerBE();
            if (owner!=null && owner.canTakeInternal()) owner.putInternal(CEReactor.INTERNALS.get(kind).get().getDefaultInstance());
        }
    }
    private static ReactorMachineBlockEntity build(GameTestHelper h,ReactorModule kind,int sections) { return assemble(h.getLevel(),h.absolutePos(BASE),kind,sections); }
    private static IFluidHandler in(ReactorMachineBlockEntity be) { return be.horizontal() ? ThermalControlGameTests.tube(be,0) : be.port(be.inlet()); }
    /** Product-only view over the actual native top/side roles, or the shared ports of other modules. */
    private static IFluidHandler out(ReactorMachineBlockEntity be) {
        if (be.horizontal()) return ThermalControlGameTests.tube(be,1);
        IFluidHandler port=be.port(be.inlet().getOpposite());
        return port==null ? null : new IFluidHandler() {
            @Override public int getTanks() { return port.getTanks()-2; }
            @Override public FluidStack getFluidInTank(int tank) { return port.getFluidInTank(tank+2); }
            @Override public int getTankCapacity(int tank) { return port.getTankCapacity(tank+2); }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { return port.isFluidValid(tank+2,stack); }
            @Override public int fill(FluidStack stack,FluidAction action) { return port.fill(stack,action); }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) { return port.drain(stack,action); }
            @Override public FluidStack drain(int amount,FluidAction action) { return port.drain(amount,action); }
        };
    }
    static ReactorMachineBlockEntity receiver(ReactorMachineBlockEntity be) {
        return be instanceof LiddedVesselBlockEntity vessel && vessel.getWidth()==3 ? (ReactorMachineBlockEntity)be.getLevel().getBlockEntity(be.getBlockPos().offset(1,0,1)) : be;
    }
    static void speed(ReactorMachineBlockEntity be,float speed) {
        var input=receiver(be);float previous=input.getSpeed();input.setSpeed(speed);input.onSpeedChanged(previous);
    }
    static void heat(ReactorMachineBlockEntity be,com.simibubi.create.content.processing.recipe.HeatCondition heat) {
        if(heat!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE) be.getLevel().setBlock(be.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED),3);
    }
    private static void run(ReactorMachineBlockEntity be,int ticks) { speed(be,32);for (int i=0;i<ticks;i++) be.tickProcess(); }
    @GameTest(template = "test_reactor")
    public static void heatedSlimeBlendBecomesTwoCutsAndAReusableCoolant(GameTestHelper h) {
        var mixer = build(h, ReactorModule.STIRRED, 1);
        var water = new FluidStack(Fluids.WATER, 1000);
        var blend = new FluidStack(CEProcessFluids.SLIME_COOLANT_FEED.get(), 1000);
        h.assertTrue(in(mixer).fill(water, EXECUTE) == 1000
            && mixer.itemPort().insertItem(0, new ItemStack(Items.SLIME_BALL), false).isEmpty()
            && mixer.itemPort().insertItem(1, new ItemStack(Items.GLOWSTONE_DUST), false).isEmpty(), "Load the water and both Minecraft ingredients");
        run(mixer, 120);
        h.assertTrue(out(mixer).drain(blend, SIMULATE).isEmpty() && in(mixer).getFluidInTank(0).getAmount() == 1000,
            "The mixture waits for burner heat");
        heat(mixer, com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);
        run(mixer, 120);
        var batch = out(mixer).drain(blend, EXECUTE);
        h.assertTrue(batch.getAmount() == 1000 && mixer.itemPort().getStackInSlot(0).isEmpty() && mixer.itemPort().getStackInSlot(1).isEmpty(),
            "A heated batch makes one finite blend and consumes its ingredients");

        var column = DistillationGameTests.build(h.getLevel(), h.absolutePos(BASE.east(6)), 2, 2);
        h.assertTrue(DistillationGameTests.inlet(column, 1).fill(batch, EXECUTE) == batch.getAmount(), "The two-layer tower accepts the blend");
        DistillationGameTests.run(column, 90);
        var recovered = DistillationGameTests.outlet(column, 1).drain(1000, EXECUTE);
        var coolant = DistillationGameTests.outlet(column, 0).drain(1000, EXECUTE);
        h.assertTrue(recovered.is(Fluids.WATER) && recovered.getAmount() == 250 && column.stock(0) == 0
            && coolant.is(CEProcessFluids.SLIME_COOLANT.get()) && coolant.getAmount() == 750, "The top and bottom cuts conserve the full batch");

        var exchanger = ThermalControlGameTests.build(h.getLevel(), h.absolutePos(BASE.east(12)), Direction.NORTH, 2);
        h.assertTrue(ThermalControlGameTests.tube(exchanger, 0).fill(coolant, EXECUTE) == 750
            && ThermalControlGameTests.shell(exchanger, 0).fill(new FluidStack(CEProcessFluids.WARM_WATER.get(), 750), EXECUTE) == 750,
            "The heat exchanger accepts the refined coolant on its cold side");
        for (int i = 0; i < 25; i++) exchanger.thermal().tick();
        var warmed = ThermalControlGameTests.tube(exchanger, 1).drain(1000, EXECUTE);
        var recooling = net.createplant.thermal.ThermalStreams.cooling(warmed);
        h.assertTrue(warmed.is(CEProcessFluids.WARM_SLIME_COOLANT.get()) && warmed.getAmount() == 750 && recooling != null
            && FluidStack.matches(recooling.result(warmed, 750), coolant), "The radiator's cooling table returns the original coolant and volume");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void filtrationReturnsTheIodineForAnotherRearrangement(GameTestHelper h) {
        var mixer = build(h, ReactorModule.STIRRED, 1);
        var filter = assemble(h.getLevel(), h.absolutePos(BASE.east(5)), ReactorModule.STIRRED, 1);
        var feed = new FluidStack(CEProcessFluids.TRIMETHYL_PHOSPHITE.get(), 1000);
        var crude = new FluidStack(CEProcessFluids.CRUDE_METHYLPHOSPHONATE.get(), 1000);
        var product = new FluidStack(CEProcessFluids.DIMETHYL_METHYLPHOSPHONATE.get(), 1000);
        ItemStack iodine = CEItems.IODINE.toStack();

        h.assertTrue(in(mixer).fill(feed, EXECUTE) == 1000, "The rearrangement vessel accepts its liquid feed");
        run(mixer, 150);
        h.assertTrue(out(mixer).drain(crude, SIMULATE).isEmpty(), "The rearrangement needs iodine");
        for (int batch = 0; batch < 2; batch++) {
            h.assertTrue(mixer.itemPort().insertItem(0, iodine, false).isEmpty(), "The mixer takes one finite catalyst item");
            run(mixer, 150);
            h.assertTrue(mixer.itemPort().getStackInSlot(0).isEmpty() && out(mixer).drain(product, SIMULATE).isEmpty(),
                "The first vessel puts only the crude stream in its output");
            var unfiltered = out(mixer).drain(crude, EXECUTE);
            h.assertTrue(unfiltered.getAmount() == 1000 && in(filter).fill(unfiltered, EXECUTE) == 1000,
                "The conserved crude parcel is accepted by the second vessel");
            run(filter, 100);
            h.assertTrue(out(filter).drain(product, SIMULATE).isEmpty(), "The second vessel waits for filter medium");
            h.assertTrue(filter.itemPort().insertItem(0, CEWaterTreatment.FILTER_MEDIUM.toStack(), false).isEmpty(),
                "The existing filter medium fits the second vessel");
            run(filter, 100);
            iodine = filter.itemPort().extractItem(9, 64, false);
            h.assertTrue(out(filter).drain(product, EXECUTE).getAmount() == 1000
                && iodine.is(CEItems.IODINE.get()) && iodine.getCount() == 1 && filter.itemPort().getStackInSlot(0).isEmpty(),
                "Filtration consumes the medium and yields exactly one purified parcel and one reusable catalyst item");
            if (batch == 0) h.assertTrue(in(mixer).fill(feed, EXECUTE) == 1000, "A fresh feed is needed for the next batch");
        }
        h.succeed();
    }

    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void simulatedHandlersAndExactBatchConservation(GameTestHelper h) {
        var be=build(h,ReactorModule.STIRRED,1);var input=in(be);var output=out(be);
        var feed=new FluidStack(Fluids.WATER,1000);
        h.assertTrue(input.fill(feed,SIMULATE)==1000 && input.getFluidInTank(0).isEmpty(),"Simulating fill never stores feed");
        h.assertTrue(!input.isFluidValid(0,new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),250)),"Synthesis gas is not an input to a stirred batch");
        input.fill(feed,EXECUTE);run(be,120);
        h.assertTrue(input.getFluidInTank(0).getAmount()==1000 && output.getFluidInTank(0).isEmpty(),"Heat is required before consuming inputs");
        heat(be,com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);run(be,100);
        h.assertTrue(input.getFluidInTank(0).isEmpty() && output.getFluidInTank(0).getAmount()==1000,"One batch consumes 1000 mB and makes exactly 1000 mB of pressurised water");
        FluidStack alias=output.getFluidInTank(0);alias.setAmount(1);
        h.assertTrue(output.getFluidInTank(0).getAmount()==1000 && output.drain(250,SIMULATE).getAmount()==250 && output.getFluidInTank(0).getAmount()==1000,"Queries and simulations cannot mutate owned buffers");
        h.assertTrue(input.drain(feed.copyWithAmount(1000),EXECUTE).isEmpty() && output.getFluidInTank(0).getAmount()==1000,"Feed can never be drained back out of any face");
        input.fill(feed,EXECUTE);h.setBlock(BASE.below(),Blocks.REDSTONE_BLOCK);run(be,100);
        h.assertTrue(input.getFluidInTank(0).getAmount()==1000 && be.status().equals("redstone"),"A redstone stop consumes no feed or progress");
        h.setBlock(BASE.below(),Blocks.AIR);speed(be,0);be.tickProcess();
        h.assertTrue(be.status().equals("speed") && input.getFluidInTank(0).getAmount()==1000,"Stopped rotation cannot advance a batch");
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void rebuildSaveLoadAndRetiredPortsKeepContents(GameTestHelper h) {
        var be=build(h,ReactorModule.STIRRED,1);var old=in(be);
        old.fill(new FluidStack(Fluids.WATER,1000),EXECUTE);heat(be,com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);run(be,40);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(saved,h.getLevel().registryAccess());be.validateStructure();
        h.assertTrue(be.progress()==40 && in(be).getFluidInTank(0).getAmount()==1000,"Save and load preserve paid progress and feed");
        var port=in(be);h.setBlock(BASE.offset(1,0,0),Blocks.AIR);
        h.assertTrue(port.fill(new FluidStack(Fluids.WATER,100),EXECUTE)==0,"A cached port stops on dismantling");
        h.setBlock(BASE.offset(1,0,0),CETransport.LIDDED_VESSEL.get());be.validateStructure();
        h.assertTrue(port.fill(new FluidStack(Fluids.WATER,100),EXECUTE)==0 && in(be).getFluidInTank(0).getAmount()==1000,"Old ports remain retired after reforming; buffered feed remains");
        run(be,60);h.assertTrue(out(be).getFluidInTank(0).getAmount()==1000,"A resumed batch completes exactly once");
        var outlet=out(be);h.setBlock(BASE,Blocks.AIR);h.assertTrue(outlet.drain(1000,EXECUTE).isEmpty(),"Removed machines cannot be drained through stale references");h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void expandingIsBoundedAndOutputBackpressureIsAtomic(GameTestHelper h) {
        var be=build(h,ReactorModule.STIRRED,4);heat(be,com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);
        speed(be,32);h.assertTrue(be.sections()==4 && receiver(be).calculateStressApplied()==64 && be.calculateStressApplied()==0,"Four impeller layers charge 64 SU/RPM to the central receiver");
        for (int i=0;i<8;i++) { in(be).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);run(be,100); }
        h.assertTrue(out(be).getFluidInTank(0).getAmount()==8000 && out(be).getTankCapacity(0)==8000,"Finite liquid output capacity holds exactly eight batches");
        in(be).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);run(be,200);
        h.assertTrue(in(be).getFluidInTank(0).getAmount()==1000,"A full liquid product buffer consumes no feed");
        out(be).drain(1000,EXECUTE);run(be,100);h.assertTrue(in(be).getFluidInTank(0).isEmpty() && out(be).getFluidInTank(0).getAmount()==8000,"Making room permits exactly one further batch");
        layer(h.getLevel(),h.absolutePos(BASE),5,ReactorModule.STIRRED);
        be.validateStructure();h.assertTrue(be.formed() && be.sections()==4,"A fifth layer cannot join a vessel four blocks high");h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void heaterRequiresRealHeatAndColumnRequiresColdFeed(GameTestHelper h) {
        var heat=build(h,ReactorModule.PREHEATER,1);in(heat).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);run(heat,100);
        h.assertTrue(out(heat).getFluidInTank(0).isEmpty() && in(heat).getFluidInTank(0).getAmount()==1000,"A missing second stream retains the first feed");
        var hot=heat.thermal().port(2,true,false,true);hot.fill(new FluidStack(CEProcessFluids.HOT_OLEFINS.get(),1000),EXECUTE);run(heat,50);
        h.assertTrue(out(heat).getFluidInTank(0).is(CEProcessFluids.WARM_WATER.get()) && out(heat).getFluidInTank(0).getAmount()==1000 && heat.thermal().tank(3).getFluid().is(CEProcessFluids.COOLED_OLEFINS.get()),"Two finite rows convert together");
        var column=DistillationGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(12,2,4)),2,14);
        h.assertTrue(DistillationGameTests.inlet(column,2).fill(new FluidStack(CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get(),9000),EXECUTE)==0 && DistillationGameTests.inlet(column,2).fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000),EXECUTE)==0,"Ammonia mixtures belong in the dedicated gas-liquid separator");h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void theReactionTowerOxidizesSulfurDioxide(GameTestHelper h) {
        var tower=ReactionTowerGameTests.tower(h.getLevel(),h.absolutePos(new BlockPos(12,2,4)),3,ItemStack.EMPTY);var port=ReactionTowerGameTests.port(tower);
        h.assertTrue(port.fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),100),EXECUTE)==0,"Sulfur dioxide needs the oxidation catalyst");
        for (int i=0;i<3;i++) tower.insert(CEItems.OXIDATION_CATALYST.toStack());
        port.fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),100),EXECUTE);port.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),50),EXECUTE);tower.process(100);
        h.assertTrue(tower.tank(2).getAmount()==100 && tower.tank(2).getFluid()==CEUranium.SULFUR_TRIOXIDE.get(),"100 mB of sulfur dioxide and 50 mB of oxygen give 100 mB of sulfur trioxide");
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void stirredReactorRunsGasReactionsWithItemsAndBurnerHeat(GameTestHelper h) {
        var gas=build(h,ReactorModule.STIRRED,2);
        speed(gas,32);h.assertTrue(gas.formed() && receiver(gas).calculateStressApplied()==32,"Two agitator stages form a stirred reactor of 32 SU/RPM");
        in(gas).fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),1500),EXECUTE);in(gas).fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),500),EXECUTE);run(gas,100);
        h.assertTrue(in(gas).getFluidInTank(0).isEmpty() && in(gas).getFluidInTank(1).isEmpty() && out(gas).getFluidInTank(0).getAmount()==2000,"Two stages mix two batches of hydrogen and nitrogen into 2000 mB of synthesis gas");
        var hot=assemble(h.getLevel(),h.absolutePos(new BlockPos(12,2,4)),ReactorModule.STIRRED,1);
        var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,hot.getBlockPos().offset(2,0,1),Direction.EAST);
        var jacket=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,hot.getBlockPos().offset(0,0,1),Direction.WEST);
        h.assertTrue(items!=null && jacket!=null,"The vessel takes items and fluids on its outer faces");
        h.assertTrue(items.insertItem(0,new ItemStack(Items.DIRT),false).isEmpty() && hot.recoverInputItem(0,1).is(Items.DIRT),"A stirred vessel stores ordinary basin items and lets the player recover them independently of recipes");
        h.assertTrue(items.insertItem(0,CEUranium.URANIUM_DIOXIDE.toStack(),false).isEmpty() && items.extractItem(0,1,true).isEmpty(),"Feed slots take reaction ingredients and give nothing back");
        h.assertTrue(jacket.fill(new FluidStack(CEUranium.HYDROGEN_FLUORIDE.get(),500),EXECUTE)==500,"Gas goes in through the vessel");run(hot,100);
        h.assertTrue(hot.status().equals("heat") && items.getStackInSlot(0).getCount()==1,"Without a burner the heated green-salt reaction cannot start");
        h.getLevel().setBlock(hot.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED),3);run(hot,100);
        h.assertTrue(items.getStackInSlot(0).isEmpty() && in(hot).getFluidInTank(0).isEmpty() && items.extractItem(9,1,false).is(CEUranium.URANIUM_TETRAFLUORIDE.get()),"A kindled burner under the vessel stirs uranium dioxide with hydrogen fluoride into green salt in a product slot");
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void clausFurnaceAndConverterTurnHydrogenSulfideIntoSulfur(GameTestHelper h) {
        var furnace=build(h,ReactorModule.STIRRED,1);
        h.getLevel().setBlock(furnace.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED),3);
        in(furnace).fill(new FluidStack(CEProcessFluids.HYDROGEN_SULFIDE.get(),500),EXECUTE);in(furnace).fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),750),EXECUTE);run(furnace,120);
        h.assertTrue(in(furnace).getFluidInTank(0).isEmpty() && in(furnace).getFluidInTank(1).isEmpty(),"The furnace burns 500 mB hydrogen sulfide with 750 mB oxygen");
        var made=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
        for (int i=0;i<out(furnace).getTanks();i++) { var stack=out(furnace).getFluidInTank(i);if (!stack.isEmpty()) made.merge(stack.getFluid(),stack.getAmount(),Integer::sum); }
        h.assertTrue(made.getOrDefault(CEUranium.SULFUR_DIOXIDE.get(),0)==500 && made.getOrDefault(Fluids.WATER,0)==100,"into 500 mB sulfur dioxide and 100 mB water: "+made);
        var converter=assemble(h.getLevel(),h.absolutePos(new BlockPos(12,2,4)),ReactorModule.STIRRED,1);
        in(converter).fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),500),EXECUTE);in(converter).fill(new FluidStack(CEProcessFluids.HYDROGEN_SULFIDE.get(),1000),EXECUTE);run(converter,120);
        h.assertTrue(in(converter).getFluidInTank(0).getAmount()==500,"Without heat the converter does not start");
        h.getLevel().setBlock(converter.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED),3);run(converter,120);
        var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,converter.getBlockPos().offset(2,0,1),Direction.EAST);
        int sulfur=0;for (int slot=0;slot<items.getSlots();slot++) if (items.getStackInSlot(slot).is(CEItems.SULFUR_DUST.get())) sulfur+=items.getStackInSlot(slot).getCount();
        h.assertTrue(in(converter).getFluidInTank(0).isEmpty() && in(converter).getFluidInTank(1).isEmpty() && sulfur==3,"1000 mB hydrogen sulfide and 500 mB sulfur dioxide give three sulfur dust: "+sulfur);
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void stirredReactorMakesAmmoniumNitrateWithoutHeat(GameTestHelper h) {
        var reactor=build(h,ReactorModule.STIRRED,1);
        in(reactor).fill(new FluidStack(CEFluids.NITRIC_ACID.get(),250),EXECUTE);in(reactor).fill(new FluidStack(CEFluids.AMMONIA_GAS.get(),250),EXECUTE);run(reactor,120);
        var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,reactor.getBlockPos().offset(2,0,1),Direction.EAST);
        int made=0;for (int slot=0;slot<items.getSlots();slot++) if (items.getStackInSlot(slot).is(CEItems.AMMONIUM_NITRATE.get())) made+=items.getStackInSlot(slot).getCount();
        h.assertTrue(made==1 && in(reactor).getFluidInTank(0).isEmpty() && in(reactor).getFluidInTank(1).isEmpty(),"250 mB nitric acid and 250 mB ammonia give one ammonium nitrate: "+made);
        h.succeed();
    }
    /** Runs one iron-catalysed batch in a stirred vessel with the given oxidant; returns the vessel after the run. */
    private static ReactorMachineBlockEntity ironCatalysed(GameTestHelper h,ItemStack iron,FluidStack oxidant){
        var reactor=build(h,ReactorModule.STIRRED,1);
        var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,reactor.getBlockPos().offset(2,0,1),Direction.EAST);
        in(reactor).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);in(reactor).fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),250),EXECUTE);in(reactor).fill(oxidant,EXECUTE);
        run(reactor,220);
        h.assertTrue(out(reactor).getFluidInTank(0).isEmpty(),"Without iron the sulfur dioxide stays unoxidised");
        h.assertTrue(items.insertItem(0,iron,false).isEmpty(),"The vessel takes the iron through its item port");run(reactor,220);
        h.assertTrue(items.getStackInSlot(0).isEmpty() && in(reactor).getFluidInTank(0).isEmpty() && in(reactor).getFluidInTank(1).isEmpty(),"The batch uses the iron and the feeds: "+reactor.status());
        return reactor;
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void ironInWaterOxidisesSulfurDioxideWithOxygenIntoDiluteSulfuricAcid(GameTestHelper h) {
        var reactor=ironCatalysed(h,new ItemStack(Items.IRON_NUGGET),new FluidStack(CEFluids.OXYGEN_GAS.get(),125));
        h.assertTrue(out(reactor).getFluidInTank(0).is(CEMetals.DILUTE_SULFURIC_ACID.get()) && out(reactor).getFluidInTank(0).getAmount()==1000 && out(reactor).getFluidInTank(1).isEmpty(),
            "1000 mB water, 250 mB sulfur dioxide and 125 mB oxygen give 1000 mB dilute sulfuric acid without heat");
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void ironOxideAndAirWorkTooAndTheNitrogenBubblesOut(GameTestHelper h) {
        var reactor=build(h,ReactorModule.STIRRED,1);
        // Create attaches the pass-through buffer on the first tick.
        h.runAfterDelay(2,()->{
            var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,reactor.getBlockPos().offset(2,0,1),Direction.EAST);
            var pipe=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,reactor.getBlockPos(),Direction.NORTH);
            in(reactor).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);in(reactor).fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),250),EXECUTE);
            h.assertTrue(pipe.fill(new FluidStack(CEFluids.AIR_GAS.get(),600),EXECUTE)==600,"The vessel takes 600 mB air by its gases");
            items.insertItem(0,CEMetals.IRON_OXIDE_RED.toStack(),false);run(reactor,220);
            var key=net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(CEFluids.NITROGEN_GAS.get());
            int nitrogen=0,acid=0;
            for (var gas:List.of(net.createplant.mixture.GasPassThrough.of(reactor).gas(),((net.createplant.block.entity.LiddedVesselBlockEntity)reactor).getTankInventory().getFluid()))
                nitrogen+=net.createplant.mixture.MixtureHelper.extractContents(gas).amounts().getOrDefault(key,0);
            for (int i=0;i<out(reactor).getTanks();i++) if (out(reactor).getFluidInTank(i).is(CEMetals.DILUTE_SULFURIC_ACID.get())) acid+=out(reactor).getFluidInTank(i).getAmount();
            h.assertTrue(acid==1000 && nitrogen==468,"Air's 126 mB oxygen oxidises the sulfur dioxide; its 468 mB nitrogen waits in the vessel's gas space: "+acid+" "+nitrogen);
            h.succeed();
        });
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void theAbsorptionTowerAbsorbsSulfurTrioxideIntoSulfuricAcid(GameTestHelper h) {
        var absorber=build(h,ReactorModule.TOWER,4);
        in(absorber).fill(new FluidStack(CEUranium.SULFUR_TRIOXIDE.get(),250),EXECUTE);in(absorber).fill(new FluidStack(Fluids.WATER,250),EXECUTE);run(absorber,120);
        int acid=0;for (int i=0;i<out(absorber).getTanks();i++) if (out(absorber).getFluidInTank(i).is(CEUranium.SULFURIC_ACID.get())) acid+=out(absorber).getFluidInTank(i).getAmount();
        h.assertTrue(acid==250 && in(absorber).getFluidInTank(0).isEmpty() && in(absorber).getFluidInTank(1).isEmpty(),"250 mB sulfur trioxide and 250 mB water give 250 mB sulfuric acid: "+acid);
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void ethyleneOxideAndGlycolLeadToAntifreeze(GameTestHelper h) {
        var tower=ReactionTowerGameTests.tower(h.getLevel(),h.absolutePos(BASE),3,CEItems.OXIDATION_CATALYST.toStack());var port=ReactionTowerGameTests.port(tower);
        port.fill(net.createplant.thermal.FluidThermal.at(new FluidStack(net.createplant.registry.CEPetroFluids.ETHYLENE.get(),200),500),EXECUTE);port.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),100),EXECUTE);tower.process(150);
        h.assertTrue(tower.tank(2).isEmpty() && tower.status().equals("heat"),"Ethylene oxide needs heat under the tower: "+tower.status());
        ReactionTowerGameTests.heat(tower);tower.process(150);
        h.assertTrue(tower.tank(2).getAmount()==200 && tower.tank(2).getFluid()==CEProcessFluids.ETHYLENE_OXIDE.get(),"200 mB heated ethylene and 100 mB oxygen give 200 mB ethylene oxide");
        var stirred=assemble(h.getLevel(),h.absolutePos(new BlockPos(12,2,4)),ReactorModule.STIRRED,1);
        in(stirred).fill(new FluidStack(CEProcessFluids.ETHYLENE_OXIDE.get(),250),EXECUTE);in(stirred).fill(new FluidStack(Fluids.WATER,250),EXECUTE);run(stirred,100);
        h.assertTrue(in(stirred).getFluidInTank(0).getAmount()==250,"Glycol needs a lit burner");
        h.getLevel().setBlock(stirred.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED),3);run(stirred,100);
        int glycol=0;for (int i=0;i<out(stirred).getTanks();i++) if (out(stirred).getFluidInTank(i).is(CEProcessFluids.ETHYLENE_GLYCOL.get())) glycol+=out(stirred).getFluidInTank(i).getAmount();
        h.assertTrue(glycol==250,"250 mB ethylene oxide and 250 mB water give 250 mB ethylene glycol: "+glycol);
        h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/antifreeze")).isPresent(),"Glycol and water mix into antifreeze in a basin");
        h.succeed();
    }
    /** A burner under the north-west bottom corner at the heat a recipe asks for, or none. */
    private static void burner(GameTestHelper h,com.simibubi.create.content.processing.recipe.HeatCondition heat) {
        if (heat!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE) h.getLevel().setBlock(h.absolutePos(BASE).below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,heat==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED),3);
    }
    /** Clears a module's footprint, every layer a vessel can have and the burner positions underneath, so the next one is built fresh. */
    private static void clear(GameTestHelper h,int sections) {
        for (int y=-1;y<=net.createplant.block.entity.AbsorptionTowerBlockEntity.MAX_STOREYS;y++) for (int x=0;x<3;x++) for (int z=0;z<3;z++) h.getLevel().setBlock(h.absolutePos(BASE).offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
    }
    private static Map<net.minecraft.world.level.material.Fluid,Integer> products(ReactorMachineBlockEntity be) {
        var made=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
        for (int i=0;i<out(be).getTanks();i++) { var stack=out(be).getFluidInTank(i);if (!stack.isEmpty()) made.merge(stack.getFluid(),stack.getAmount(),Integer::sum); }
        return made;
    }
    private static Map<net.minecraft.world.level.material.Fluid,Integer> columnProducts(FractionatingColumnBlockEntity column) {
        var products=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
        for(int layer:new int[]{0,3,6,9,13}){var port=DistillationGameTests.outlet(column,layer);if(port!=null){var fluid=port.getFluidInTank(0);if(!fluid.isEmpty())products.merge(fluid.getFluid(),fluid.getAmount(),Integer::sum);}}
        return products;
    }
    /** Each fixed process, fed as a plant feeds it: heated and cooled streams arrive as their base fluids at the needed temperature. */
    @GameTest(template="test_reactor",timeoutTicks=100)
    public static void everyFixedProcessRunsFromItsRealFeeds(GameTestHelper h) {
        var failures=new ArrayList<String>();
        for (ProcessRecipe recipe:ProcessRecipe.all()) {
            if(recipe.module()==ProcessModule.GAS_CENTRIFUGE || recipe.module()==ProcessModule.FRACTION_SEPARATOR || recipe.module()==ProcessModule.GAS_LIQUID_SEPARATOR)continue;
            if (recipe.module()==ProcessModule.REACTION_VESSEL || recipe.module()==ProcessModule.CATALYST_UNIT) {
                clear(h,ReactionTowerBlockEntity.MIN_LAYERS);
                var tower=ReactionTowerGameTests.tower(h.getLevel(),h.absolutePos(BASE),ReactionTowerBlockEntity.MIN_LAYERS,new ItemStack(recipe.catalyst()));
                for (FluidStack input:recipe.inputs()) {
                    int accepted=ReactionTowerGameTests.port(tower).fill(input.copy(),EXECUTE);
                    if (accepted!=input.getAmount()) failures.add(recipe.id()+": tower took "+accepted+" of "+input.getAmount()+" "+input.getFluid());
                }
                burner(h,recipe.requiredHeat());
                tower.process(recipe.ticks());
                var made=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
                for (int i=2;i<4;i++) if (!tower.tank(i).isEmpty()) made.merge(tower.tank(i).getFluid(),tower.tank(i).getAmount(),Integer::sum);
                var expected=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
                for (FluidStack output:recipe.outputs()) expected.merge(output.getFluid(),output.getAmount(),Integer::sum);
                if (!made.equals(expected)) failures.add(recipe.id()+": tower made "+made+" status "+tower.status());
                continue;
            }
            ReactorModule kind=recipe.module()==ProcessModule.ABSORPTION_TRAYS ? ReactorModule.TOWER : ReactorModule.STIRRED;
            int sections=kind==ReactorModule.TOWER ? kind.minSections : 2;
            clear(h,sections);
            var be=build(h,kind,sections);
            if (in(be)==null) { failures.add(recipe.id()+": "+kind+" not formed, "+be.status());continue; }
            for (FluidStack input:recipe.inputs()) {
                FluidStack feed=input.copy();
                int accepted=in(be).fill(feed,EXECUTE);
                if (accepted!=feed.getAmount()) failures.add(recipe.id()+": port took "+accepted+" of "+feed.getAmount()+" "+feed.getFluid());
            }
            heat(be,recipe.requiredHeat());
            run(be,recipe.ticks()*2+40);
            var expected=new HashMap<net.minecraft.world.level.material.Fluid,Integer>();
            for (FluidStack output:recipe.outputs()) expected.merge(output.getFluid(),output.getAmount(),Integer::sum);
            var made=products(be);
            if (!made.equals(expected)) failures.add(recipe.id()+": made "+made+" status "+be.status());
        }
        h.assertTrue(failures.isEmpty(),"Processes that do not run from their real feeds: "+failures);
        h.succeed();
    }
    /** Each vessel reaction, with its required internals and burner heat. */
    @GameTest(template="test_reactor",timeoutTicks=100)
    public static void everyVesselReactionRunsWithItsInternalsAndHeat(GameTestHelper h) {
        var failures=new ArrayList<String>();
        var recipes=h.getLevel().getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<net.minecraft.world.item.crafting.RecipeInput,net.createplant.recipe.PressureReactionRecipe>getType());
        h.assertTrue(recipes.size()>=18,"Vessel reactions load: "+recipes.size());
        for (var holder:recipes) {
            if (holder.value().module()==ReactorModule.NONE) continue;
            var recipe=holder.value();clear(h,2);
            var be=build(h,recipe.module(),recipe.module().minSections);
            var heat=recipe.getRequiredHeat();
            if (heat!=com.simibubi.create.content.processing.recipe.HeatCondition.NONE) h.getLevel().setBlock(be.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,
                heat==com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED),3);
            var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos().offset(2,0,1),Direction.EAST);
            for (var ingredient:recipe.getIngredients()) {
                var stack=ingredient.getItems()[0].copyWithCount(1);
                if (!items.insertItem(0,stack,false).isEmpty() && !items.insertItem(1,stack,false).isEmpty() && !items.insertItem(2,stack,false).isEmpty()) failures.add(holder.id()+": refused "+stack);
            }
            for (var ingredient:recipe.getFluidIngredients()) {
                var feed=ingredient.getFluids()[0];
                if (in(be).fill(feed,EXECUTE)!=feed.getAmount()) failures.add(holder.id()+": port refused "+feed.getAmount()+" "+feed.getFluid());
            }
            run(be,200);
            var made=products(be);
            for (var result:recipe.getFluidResults()) if (made.getOrDefault(result.getFluid(),0)!=result.getAmount()) failures.add(holder.id()+": made "+made+" status "+be.status());
            for (var result:recipe.getRollableResults()) {
                int count=0;for (int slot=9;slot<items.getSlots();slot++) if (items.getStackInSlot(slot).is(result.getStack().getItem())) count+=items.getStackInSlot(slot).getCount();
                if (count!=result.getStack().getCount()) failures.add(holder.id()+": "+count+" "+result.getStack().getItem()+" status "+be.status());
            }
        }
        h.assertTrue(failures.isEmpty(),"Vessel reactions that do not run: "+failures);
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void theVesselRunsOnlyEmptyClosedAndWithItsInternals(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(BASE);
        for (int y=0;y<2;y++) for (int x=0;x<3;x++) for (int z=0;z<3;z++) level.setBlock(base.offset(x,y,z),CETransport.LIDDED_VESSEL.get().defaultBlockState(),3);
        var owner=((LiddedVesselBlockEntity)level.getBlockEntity(base)).getControllerBE();
        var impeller=CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance();
        h.assertTrue(owner.putInternal(impeller.copy()).equals(base) && owner.putInternal(impeller.copy()).equals(base.above()) && !owner.canTakeInternal(),
            "Internals fill the layers from the bottom up and a full vessel takes no more");
        owner.getTankInventory().fill(new FluidStack(Fluids.WATER,1000),EXECUTE);owner.validateStructure();
        h.assertTrue(owner.formed() && owner.getTankInventory().getFluidAmount()==1000,"A stirred vessel retains prefilled storage water as an additional mixing ingredient pool");
        owner.getTankInventory().drain(1000,EXECUTE);owner.validateStructure();
        h.assertTrue(owner.formed() && owner.sections()==2,"The emptied vessel and its impellers form the stirred reactor without a drive");
        h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(level.getBlockState(base).getShape(level,base),
            net.minecraft.world.phys.shapes.Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"The stirred vessel keeps its complete square corner");
        var port=level.getCapability(Capabilities.FluidHandler.BLOCK,base.offset(2,0,1),Direction.EAST);
        h.assertTrue(port!=null && port.getTanks()==10,"Every vessel wall reaches three feed buffers, five product buffers and two physical storage phases");
        owner.toggleLid();
        h.assertTrue(level.getBlockState(base.offset(1,1,1)).getValue(LiddedVesselBlock.HATCH)==LiddedVesselBlock.Hatch.FULL && level.getBlockState(base.offset(2,1,2)).getValue(LiddedVesselBlock.HATCH)==LiddedVesselBlock.Hatch.NONE,"Only the top centre opens");
        speed(owner,32);owner.tickProcess();h.assertTrue(owner.status().equals("lid"),"An open hatch stops the process");
        owner.toggleLid();owner.tickProcess();h.assertTrue(!owner.status().equals("lid"),"Closing permits the process again");
        h.assertTrue(owner.takeTopInternal().is(impeller.getItem()) && owner.slot(1).internal().isEmpty() && !owner.slot(0).internal().isEmpty(),"The wrench removes the highest internal first");
        owner.validateStructure();h.assertTrue(!owner.formed() && owner.getWidth()==3,"Missing internals stop the process without changing the square enclosure");
        level.setBlock(base,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(base).inflate(1),e->e.getItem().is(impeller.getItem())).isEmpty(),"A broken holder drops its internal once");
        h.succeed();
    }
    @GameTest(template="test_reactor",timeoutTicks=50)
    public static void aTwoByTwoVesselOnlyStoresAndRejectsAllProcessInternals(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(BASE);
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++) level.setBlock(base.offset(x,y,z),CETransport.LIDDED_VESSEL.get().defaultBlockState(),3);
        var owner=((LiddedVesselBlockEntity)level.getBlockEntity(base)).getControllerBE();
        h.assertTrue(owner.getWidth()==2 && owner.getHeight()==2,"Eight blocks form a 2x2 vessel two high");
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(base),Direction.NORTH,base,false);
        for (var module:new ReactorModule[]{ReactorModule.STIRRED}) {
            var internal=CEReactor.INTERNALS.get(module).get().getDefaultInstance();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,internal);
            h.assertTrue(level.getBlockState(base).useItemOn(internal,level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)==net.minecraft.world.ItemInteractionResult.FAIL && internal.getCount()==1,
                "A 2x2 tank rejects the internal without consuming it: "+module);
        }
        owner.validateStructure();h.assertTrue(!owner.formed() && owner.module()==ReactorModule.NONE && !owner.canTakeInternal(),"A 2x2 tank cannot select or form a process");
        h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(level.getBlockState(base).getShape(level,base),
            net.minecraft.world.phys.shapes.Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"A closed 2x2 vessel keeps its complete square corner");
        h.assertTrue(level.getBlockState(base.above()).getValue(LiddedVesselBlock.HATCH)==LiddedVesselBlock.Hatch.SE,"The hatch covers the inner quarters of the four top cells");
        var port=level.getCapability(Capabilities.FluidHandler.BLOCK,base,Direction.WEST);
        h.assertTrue(port!=null && port.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==1000 && owner.getTankInventory().getFluidAmount()==1000,"The 2x2 vessel retains its ordinary shared storage tank");
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++) for (Direction face:Direction.values()) {
            var at=base.offset(x,y,z);var state=level.getBlockState(at).setValue(LiddedVesselBlock.AXIS,face.getAxis());
            h.assertTrue(!CETransport.LIDDED_VESSEL.get().hasShaftTowards(level,at,state,face),"A 2x2 storage tank has no rotational connection");
        }
        var rotor=CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance();owner.setInternal(rotor.copy());
        var saved=owner.saveWithoutMetadata(level.registryAccess());owner.loadWithComponents(saved,level.registryAccess());owner.validateStructure();
        h.assertTrue(!owner.formed() && owner.module()==ReactorModule.NONE && owner.getTankInventory().getFluidAmount()==1000 && owner.takeTopInternal().is(rotor.getItem()),"An old saved internal is recoverable without reactivating a 2x2 process or losing its water");
        h.succeed();
    }
    @GameTest(template="test_empty")
    public static void sulfurGasesHaveTheirOwnPhaseProperties(GameTestHelper h) {
        double so2=net.createplant.thermal.FluidMaterials.molarMass(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(),1));
        double h2s=net.createplant.thermal.FluidMaterials.molarMass(new FluidStack(CEProcessFluids.HYDROGEN_SULFIDE.get(),1));
        h.assertTrue(so2>.064 && so2<.065 && h2s>.034 && h2s<.035,"Fixed sulfur gas molar quantities remain distinct");h.succeed();
    }
}
