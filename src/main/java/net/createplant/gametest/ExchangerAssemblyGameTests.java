package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.block.entity.ThermalPartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExchangerAssemblyGameTests {
    @GameTest(template="test_fractionation")
    public static void bothExchangersFormFromOneCasingInEveryDirectionAndLength(GameTestHelper h) {
        int index=0;
        for (var kind:ExchangerStructure.Kind.values()) for (Direction along:Direction.Plane.HORIZONTAL) for (int length:new int[]{kind.min,kind.min+1,kind.max}) {
            var first=h.absolutePos(new BlockPos(2048+index%6*24,2,2048+index/6*24));index++;
            var be=ThermalControlGameTests.exchanger(h.getLevel(),first,along,length,kind);
            h.assertTrue(be.formed() && be.exchanger()==kind && be.sections()==length*kind.sections && be.members().size()==length*kind.width*kind.width,
                "A run of one casing forms its controller at the first end: "+kind+" "+along+" "+length+" / "+be.status());
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,be.members().getFirst(),along.getOpposite())!=null
                && ThermalControlGameTests.tube(be,1)!=null && ThermalControlGameTests.shell(be,0)!=null && ThermalControlGameTests.shell(be,1)!=null,
                "The tube ends and the two shell connections are real fluid ports");
            if (length==1) continue;
            var missing=be.members().getLast();var state=h.getLevel().getBlockState(missing);
            h.getLevel().removeBlock(missing,false);be.tickProcess();
            boolean shorter=length-1>=kind.min;
            h.assertTrue(be.formed()==shorter && (!shorter || be.sections()==(length-1)*kind.sections),"Removing a casing at the end shortens the run, or stops it below its minimum");
            h.getLevel().setBlock(missing,state.setValue(ThermalCasingBlock.FORMED,false),3);be.tickProcess();
            h.assertTrue(be.formed() && be.sections()==length*kind.sections,"Putting it back lets the same controller take it in again");
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void anExchangerWithoutItsControllerFallsBackToLooseCasings(GameTestHelper h) {
        var level=h.getLevel();var first=h.absolutePos(new BlockPos(4,2,4));
        var be=ThermalControlGameTests.exchanger(level,first,Direction.EAST,3,ExchangerStructure.Kind.SHELL);
        var middle=be.members().get(5);
        h.assertTrue(level.getBlockState(middle).getValue(ThermalCasingBlock.FORMED) && level.getBlockEntity(middle) instanceof ThermalPartBlockEntity part && part.owner()==be,
            "Every casing delegates to the controller");
        level.destroyBlock(first,true);
        h.assertTrue(!level.getBlockState(middle).getValue(ThermalCasingBlock.FORMED),"Without its controller the run is loose casings again");
        level.setBlock(first,ExchangerStructure.Kind.SHELL.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,Direction.EAST),3);
        var again=level.getBlockEntity(first) instanceof ReactorMachineBlockEntity found ? found : null;
        if (again!=null) again.tickProcess();
        h.assertTrue(level.getBlockState(first).getBlock() instanceof ExchangerControllerBlock && again!=null && again.formed(),"Putting a casing back forms a new controller");
        var jacket=ThermalControlGameTests.exchanger(level,first.south(4),Direction.EAST,1,ExchangerStructure.Kind.JACKET);
        level.setBlock(first.south(4).east(),ExchangerStructure.Kind.JACKET.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,Direction.EAST),3);jacket.tickProcess();
        h.assertTrue(jacket.formed() && jacket.sections()==2 && level.getBlockState(first.south(4).east()).is(ExchangerStructure.Kind.JACKET.casing()),
            "A jacketed pipe laid on in line lengthens the same exchanger instead of making a second one");
        h.succeed();
    }

    /** Lays tube-piped tanks {@code width} across and {@code length} long from {@code first}, starting {@code from} slices along. */
    private static void tanks(net.minecraft.world.level.Level level,BlockPos first,Direction along,int from,int length,int width) {
        for (int index=from;index<length;index++) for (int across=0;across<width;across++) for (int up=0;up<width;up++) {
            var pos=first.relative(along,index).relative(along.getClockWise(),across).above(up);
            if (!(level.getBlockState(pos).getBlock() instanceof ExchangerCasingBlock || level.getBlockState(pos).getBlock() instanceof ExchangerControllerBlock))
                level.setBlock(pos,ExchangerStructure.Kind.SHELL.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,along),3);
        }
    }

    private static int controllers(net.minecraft.world.level.Level level,BlockPos first,int reach,ExchangerStructure.Kind kind) {
        int found=0;
        for (var pos:BlockPos.betweenClosed(first.offset(-reach,-1,-reach),first.offset(reach,reach,reach))) if (level.getBlockState(pos).is(kind.controller())) found++;
        return found;
    }

    @GameTest(template="test_fractionation")
    public static void tubePipedTanksFormOnlyAtTheirSizesFullLengthAndJoinTheirTextures(GameTestHelper h) {
        var level=h.getLevel();var first=h.absolutePos(new BlockPos(4,2,4));
        tanks(level,first,Direction.EAST,0,2,3);
        h.assertTrue(controllers(level,first,6,ExchangerStructure.Kind.LARGE)==0 && controllers(level,first,6,ExchangerStructure.Kind.SHELL)==0,
            "A 3 x 3 block of tanks two long is neither a large exchanger yet nor a 2 x 2 one inside it");
        tanks(level,first,Direction.EAST,2,3,3);
        h.assertTrue(level.getBlockState(first).is(ExchangerStructure.Kind.LARGE.controller()) && controllers(level,first,6,ExchangerStructure.Kind.SHELL)==0,
            "The third slice completes the large exchanger at its first end");
        var be=(ReactorMachineBlockEntity)level.getBlockEntity(first);be.tickProcess();
        h.assertTrue(be.formed() && be.sections()==6,"Three blocks of the large exchanger are six tube sections: "+be.status());
        var middle=first.east().south().above();
        h.assertTrue(ExchangerStructure.joined(level.getBlockState(middle),middle,level.getBlockState(middle.above()),middle.above())
            && ExchangerStructure.joined(level.getBlockState(middle),middle,level.getBlockState(middle.east()),middle.east())
            && ExchangerStructure.joined(level.getBlockState(first.above()),first.above(),level.getBlockState(first),first),
            "Cells of one formed exchanger, the controller among them, join their textures");
        var beside=first.north(2);
        tanks(level,beside,Direction.EAST,0,2,2);
        var shell=level.getBlockState(beside);
        h.assertTrue(shell.is(ExchangerStructure.Kind.SHELL.controller()),"A 2 x 2 run two long forms beside a formed exchanger");
        ((ReactorMachineBlockEntity)level.getBlockEntity(beside)).tickProcess();
        tanks(level,beside.north(2),Direction.EAST,0,2,2);
        h.assertTrue(level.getBlockState(beside.north(2)).is(ExchangerStructure.Kind.SHELL.controller()),"Two 2 x 2 exchangers stand side by side");
        h.assertTrue(!ExchangerStructure.joined(level.getBlockState(beside.north()),beside.north(),level.getBlockState(beside),beside)
            && !ExchangerStructure.joined(level.getBlockState(beside.south()),beside.south(),level.getBlockState(beside.south(2)),beside.south(2)),
            "Neighbouring exchangers keep their own textures");
        var loose=first.south(6);
        level.setBlock(loose,ExchangerStructure.Kind.SHELL.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,Direction.EAST),3);
        level.setBlock(loose.east(),ExchangerStructure.Kind.SHELL.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,Direction.EAST),3);
        h.assertTrue(!ExchangerStructure.joined(level.getBlockState(loose),loose,level.getBlockState(loose.east()),loose.east()),"Loose tanks show each block on its own");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void aTwoByTwoExchangerGrowsIntoTheLargeOneWhenItsTanksAreBuiltOut(GameTestHelper h) {
        var level=h.getLevel();var first=h.absolutePos(new BlockPos(4,2,4));
        tanks(level,first,Direction.EAST,0,3,2);
        h.assertTrue(level.getBlockState(first).is(ExchangerStructure.Kind.SHELL.controller()),"The 2 x 2 run forms first");
        tanks(level,first,Direction.EAST,0,3,3);
        h.assertTrue(level.getBlockState(first).is(ExchangerStructure.Kind.LARGE.controller()) && controllers(level,first,6,ExchangerStructure.Kind.SHELL)==0,
            "Building it out to 3 x 3 turns it into one large exchanger");
        var be=(ReactorMachineBlockEntity)level.getBlockEntity(first);be.tickProcess();
        h.assertTrue(be.formed() && be.members().size()==27,"The large exchanger takes in every tank: "+be.status());
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void ironSheetsPressIntoPipesThatMakeTubePipedTanksWithAnyTank(GameTestHelper h) {
        var level=h.getLevel();var manager=level.getRecipeManager();
        var pressing=manager.byKey(net.createplant.CreatePlant.location("pressing/iron_pipe")).orElseThrow().value();
        h.assertTrue(pressing.getIngredients().getFirst().test(com.simibubi.create.AllItems.IRON_SHEET.asStack())
            && pressing.getResultItem(level.registryAccess()).is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("iron_pipe"))),
            "Create's press turns an iron sheet into an iron pipe");
        var pipe=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("iron_pipe")));
        for (var tank:new net.minecraft.world.item.ItemStack[]{com.simibubi.create.AllBlocks.FLUID_TANK.asStack(),new net.minecraft.world.item.ItemStack(net.createplant.registry.CETransport.PRESSURE_TANK.get())}) {
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,java.util.List.of(tank,pipe,pipe,pipe,pipe,
                net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY));
            var result=manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).map(r->r.value().assemble(input,level.registryAccess()));
            h.assertTrue(result.isPresent() && result.get().is(ExchangerStructure.Kind.SHELL.casing().asItem()),"A tank and four iron pipes make a tube-piped tank: "+tank);
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void aJacketClosesOnlyAroundAStraightLevelFluidPipeAndGivesBothBack(GameTestHelper h) {
        var level=h.getLevel();var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var jacket=net.createplant.registry.CEHeating.JACKET_ITEM.get();
        var pipe=com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side:Direction.values()) pipe=pipe.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),false);
        java.util.function.BiFunction<BlockPos,net.minecraft.world.level.block.state.BlockState,net.minecraft.world.InteractionResult> use=(at,state)->{
            if (state!=null) level.setBlock(at,state,3);
            var stack=new net.minecraft.world.item.ItemStack(jacket,2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),Direction.UP,at,false));
            var result=stack.onItemUseFirst(context);
            return result==net.minecraft.world.InteractionResult.PASS ? stack.useOn(context) : result;
        };
        var straight=h.absolutePos(new BlockPos(3,2,3));
        var result=use.apply(straight,pipe.setValue(PipeBlock.EAST,true).setValue(PipeBlock.WEST,true));
        var state=level.getBlockState(straight);
        h.assertTrue(result.consumesAction() && state.is(ExchangerStructure.Kind.JACKET.controller()) && state.getValue(ThermalMachineBlock.FACING).getAxis()==Direction.Axis.X
            && player.getMainHandItem().getCount()==1,"Right-clicking a straight level pipe closes one jacket around it along the pipe and uses one jacket");
        var drops=Block.getDrops(state,level,straight,level.getBlockEntity(straight)).stream().map(net.minecraft.world.item.ItemStack::getItem).toList();
        h.assertTrue(drops.contains(jacket) && drops.contains(com.simibubi.create.AllBlocks.FLUID_PIPE.asItem()),"A broken jacketed pipe gives back the jacket and the pipe inside it");
        var bend=h.absolutePos(new BlockPos(3,2,6));
        use.apply(bend,pipe.setValue(PipeBlock.EAST,true).setValue(PipeBlock.NORTH,true));
        var riser=h.absolutePos(new BlockPos(6,2,6));
        use.apply(riser,pipe.setValue(PipeBlock.UP,true).setValue(PipeBlock.DOWN,true));
        h.assertTrue(com.simibubi.create.AllBlocks.FLUID_PIPE.has(level.getBlockState(bend)) && com.simibubi.create.AllBlocks.FLUID_PIPE.has(level.getBlockState(riser))
            && player.getMainHandItem().getCount()==2,"A bend or a riser keeps its pipe and the jacket");
        var floor=h.absolutePos(new BlockPos(6,1,3));
        use.apply(floor,null);
        h.assertTrue(level.getBlockState(floor.above()).isAir() && player.getMainHandItem().getCount()==2,"A jacket is not placed without a pipe");
        h.succeed();
    }

    @GameTest(template="test_fractionation",timeoutTicks=80)
    public static void shippedPonderAssembliesReallyFormInEveryRotation(GameTestHelper h) throws Exception {
        int index=0;
        for(String lesson:new String[]{"heat_exchanger","large_heat_exchanger","jacketed_pipe"}) for(Rotation rotation:Rotation.values()) {
            var template=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
            try(var stream=net.createplant.ResourceStack.open(ExchangerAssemblyGameTests.class, "/assets/create_refinery_n_reactors/ponder/plant/"+lesson+".nbt")) {
                template.load(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),net.minecraft.nbt.NbtIo.readCompressed(stream,net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
            }
            var base=h.absolutePos(new BlockPos(3072+index%4*40,2,3072+index/4*40));index++;
            template.placeInWorld(h.getLevel(),base,base,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation),h.getLevel().random,3);
            ReactorMachineBlockEntity be=null;
            for(var pos:BlockPos.betweenClosed(base.offset(-16,0,-16),base.offset(16,6,16)))
                if(h.getLevel().getBlockEntity(pos) instanceof ReactorMachineBlockEntity found && found.exchanger()!=null) { be=found;break; }
            h.assertTrue(be!=null,"The lesson holds its exchanger's controller: "+lesson+" / "+rotation);
            be.tickProcess();
            h.assertTrue(be.formed(),"The shipped Ponder layout forms in every rotation: "+lesson+" / "+rotation+" / "+be.status());
        }
        h.succeed();
    }
}
