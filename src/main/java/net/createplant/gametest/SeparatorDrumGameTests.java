package net.createplant.gametest;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import java.util.HashSet;
import net.createplant.CreatePlant;
import net.createplant.block.AmmoniaSeparatorBlock;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class SeparatorDrumGameTests {
    private static AmmoniaSeparatorBlockEntity part(ServerLevel level,BlockPos at){return (AmmoniaSeparatorBlockEntity)level.getBlockEntity(at);}
    private static AmmoniaSeparatorBlockEntity column(ServerLevel level,BlockPos at,int height){
        for(int y=0;y<height;y++)level.setBlock(at.above(y),CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);
        ConnectivityHandler.formMulti(part(level,at));return (AmmoniaSeparatorBlockEntity)part(level,at).getControllerBE();
    }
    private static IFluidHandler port(ServerLevel level,BlockPos at,Direction side){return level.getCapability(Capabilities.FluidHandler.BLOCK,at,side);}

    @GameTest(template="test_fractionation")
    public static void adjacentColumnsStayOneWideAndASeventhLayerStartsAnotherDrum(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,1,2));
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)column(level,at.offset(x,0,z),7);
        var roots=new HashSet<BlockPos>();
        for(int x=0;x<2;x++)for(int z=0;z<2;z++){
            var base=at.offset(x,0,z);var root=part(level,base).getControllerBE();roots.add(root.getBlockPos());
            h.assertTrue(root.getWidth()==1 && root.getHeight()==6,"Each adjacent column stops at six layers");
            for(int y=0;y<6;y++)h.assertTrue(part(level,base.above(y)).getControllerBE()==root,"All six parts share their own column");
            var seventh=part(level,base.above(6));ConnectivityHandler.formMulti(seventh);
            h.assertTrue(seventh.isController() && seventh.getHeight()==1 && !seventh.ready(),"The seventh layer is a separate short drum");
        }
        h.assertTrue(roots.size()==4,"A 2x2 array remains four separate drums");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void twoLayersSeparateTwoBatchesInSixtyTicksAndOnlyExposeTheirPorts(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));var root=column(level,at,2);
        var inlet=port(level,at.above(),Direction.NORTH);var cold=new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),18000);
        h.assertTrue(inlet.getTankCapacity(0)==32000 && inlet.fill(cold,EXECUTE)==18000,"Each process inventory holds 16000 mB per layer");
        for(int i=0;i<11;i++)root.process(.25);
        h.assertTrue(root.liquid().isEmpty() && root.feed().getAmount()==18000,"No batch completes before 60 ticks");root.process(.25);
        var liquid=port(level,at,Direction.DOWN).drain(100000,EXECUTE);var gas=port(level,at.above(),Direction.UP).drain(100000,EXECUTE);
        h.assertTrue(root.feed().isEmpty() && liquid.is(CEProcessFluids.LIQUID_AMMONIA.get()) && liquid.getAmount()==2 && gas.is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()) && gas.getAmount()==16000,"Two layers process exactly two batches in 60 ticks");
        for(var side:Direction.Plane.HORIZONTAL)h.assertTrue(port(level,at.above(),side)!=null && port(level,at,side)==null,"Only the upper side exposes feed");
        h.assertTrue(port(level,at,Direction.UP)==null && port(level,at.above(),Direction.DOWN)==null,"The interior top and bottom are not ports");
        var single=column(level,at.east(4),1);single.process(1);
        h.assertTrue(!single.ready() && port(level,single.getBlockPos(),Direction.NORTH)==null && port(level,single.getBlockPos(),Direction.UP)==null,"A single layer cannot accept feed or operate");
        var text=new java.util.ArrayList<net.minecraft.network.chat.Component>();single.addToGoggleTooltip(text,false);
        h.assertTrue(text.toString().contains("separator.create_refinery_n_reactors.short"),"Single layer retains the short-drum explanation");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void placementFrontSurvivesReloadGrowthAndWrenchWithoutOpeningTankWindows(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));var root=column(level,at,2);var player=FakePlayerFactory.getMinecraft(level);
        for(var front:Direction.Plane.HORIZONTAL){
            player.setYRot(front.getOpposite().toYRot());var block=CETransport.AMMONIA_SEPARATOR.get();block.setPlacedBy(level,at,level.getBlockState(at),player,new ItemStack(block));root.notifyMultiUpdated();part(level,at.above()).notifyMultiUpdated();
            var saved=root.saveWithoutMetadata(level.registryAccess());root.loadWithComponents(saved,level.registryAccess());
            h.assertTrue(root.front()==front && saved.getString("Front").equals(front.getSerializedName()),"Placed front persists for every horizontal direction");
            h.assertTrue(level.getBlockState(at.above()).getValue(AmmoniaSeparatorBlock.FACING)==front,"Members use the controller front");
        }
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false));
        CETransport.AMMONIA_SEPARATOR.get().onWrenched(level.getBlockState(at),context);root.toggleWindows();root.setWindows(true);
        h.assertTrue(level.getBlockState(at).getValue(FluidTankBlock.SHAPE)==FluidTankBlock.Shape.PLAIN && level.getBlockState(at.above()).getValue(FluidTankBlock.SHAPE)==FluidTankBlock.Shape.PLAIN,"A wrench cannot turn the drum into a Create window tank");
        level.setBlock(at.above(2),CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);ConnectivityHandler.formMulti(root);
        h.assertTrue(part(level,at.above(2)).front()==root.front(),"Growth follows the bottom controller's front");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void realTankItemAddsOnlyOneLayerAndLiquidGaugeTracksWaterAndDraining(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));var root=column(level,at,2);var player=FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(CETransport.AMMONIA_SEPARATOR.get(),16));player.setShiftKeyDown(false);
        var hit=new BlockHitResult(Vec3.atCenterOf(at.above()).add(0,.5,0),Direction.UP,at.above(),false);
        var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        h.assertTrue(((BlockItem)player.getMainHandItem().getItem()).place(context).consumesAction(),"The registered PlantTankItem places onto a drum");ConnectivityHandler.formMulti(root);
        h.assertTrue(root.getWidth()==1 && root.getHeight()==3 && level.getBlockState(at.above(2).east()).isAir() && player.getMainHandItem().getCount()==15,"Bulk placement adds one block, without a horizontal layer");
        var inlet=port(level,at.above(2),Direction.NORTH);var water=new FluidStack(Fluids.WATER,24000);
        h.assertTrue(inlet.fill(water,SIMULATE)==24000 && root.liquidFraction()==0,"A simulated fill cannot move the gauge");inlet.fill(water,EXECUTE);
        h.assertTrue(root.gaugeLiquid().is(Fluids.WATER) && root.liquidFraction()==.5f,"Half the total liquid inventory fills half the sight glass");
        port(level,at,Direction.DOWN).drain(12000,EXECUTE);h.assertTrue(root.liquidFraction()==.25f,"The gauge falls when bottom water is drained");
        var saved=root.saveWithoutMetadata(level.registryAccess());root.loadWithComponents(saved,level.registryAccess());h.assertTrue(root.liquidFraction()==.25f,"Gauge quantity survives reload");
        port(level,at,Direction.DOWN).drain(12000,EXECUTE);h.assertTrue(root.gaugeLiquid().isEmpty() && root.liquidFraction()==0,"An empty drum leaves empty glass");h.succeed();
    }
}
