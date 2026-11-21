package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class NativeFluidInventoryGameTests {
    private static FluidStack water(int n) { return new FluidStack(net.minecraft.world.level.material.Fluids.WATER,n); }
    private static BlockState straight(Block block) {
        var s=block.defaultBlockState();for (Direction d:Direction.values()) s=s.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d),d.getAxis()==Direction.Axis.X);return s;
    }
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos at,boolean insulated) {
        h.setBlock(at,insulated ? CETransport.CRYOGENIC_TANK.get() : AllBlocks.FLUID_TANK.get());return (FluidTankBlockEntity)h.getBlockEntity(at);
    }
    private static void near(GameTestHelper h,double a,double b,String msg) { h.assertTrue(Math.abs(a-b)<1e-7,msg+": "+a+" / "+b); }


    /** Liquid ammonia at -33 °C pumped between two cryogenic tanks: only the pipe between them can let the air's heat in. */

    @GameTest(template="test_petrochemical",timeoutTicks=220)
    public static void partialAcceptanceNeverDrainsTheRemainder(GameTestHelper h) {
        var source=tank(h,new BlockPos(1,2,3),true);var target=tank(h,new BlockPos(4,2,3),true);
        source.getTankInventory().fill(water(1000),EXECUTE);target.getTankInventory().fill(water(target.getTankInventory().getCapacity()-17),EXECUTE);
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(new BlockPos(3,2,3),straight(CEBlocks.RESIN_PIPE.get()));
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var motor=new BlockPos(1,2,4);h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(128));
        h.runAfterDelay(160,()->{
            h.assertTrue(source.getTankInventory().getFluidAmount()==983 && target.getTankInventory().getSpace()==0,"Exactly 17 mB accepted; blocked remainder stays in source");
            h.assertTrue(!FluidThermal.tracked(target.getTankInventory().getFluid()),"Native pumping does not attach temperature metadata");h.succeed();
        });
    }
    @GameTest(template="test_petrochemical",timeoutTicks=260)
    public static void twoFacesOfOneNearlyFullTankCannotDuplicateAcceptance(GameTestHelper h) {
        var source=tank(h,new BlockPos(1,2,3),true);var target=tank(h,new BlockPos(5,2,3),true);
        source.getTankInventory().fill(water(1000),EXECUTE);target.getTankInventory().fill(water(target.getTankInventory().getCapacity()-17),EXECUTE);
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        BlockPos[] positions={new BlockPos(3,2,3),new BlockPos(4,2,3),new BlockPos(3,2,4),new BlockPos(4,2,4),new BlockPos(5,2,4)};
        Direction[][] open={{Direction.WEST,Direction.EAST,Direction.SOUTH},{Direction.WEST,Direction.EAST},{Direction.NORTH,Direction.EAST},{Direction.WEST,Direction.EAST},{Direction.WEST,Direction.NORTH}};
        for (int i=0;i<positions.length;i++) {
            var state=AllBlocks.ENCASED_FLUID_PIPE.getDefaultState();
            for (Direction d:Direction.values()) state=state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d),java.util.Arrays.asList(open[i]).contains(d));
            h.setBlock(positions[i],state);
        }
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var motor=new BlockPos(1,2,4);h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(128));
        h.runAfterDelay(220,()->{
            h.assertTrue(source.getTankInventory().getFluidAmount()==983 && target.getTankInventory().getSpace()==0,"Two target faces share exactly 17 mB of acceptance across native simulation/execute");h.succeed();
        });
    }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void branchLoopAndReplacementInvalidateTheRepresentativeCache(GameTestHelper h) {
        var source=tank(h,new BlockPos(1,2,3),true);var a=tank(h,new BlockPos(5,2,3),true);var b=tank(h,new BlockPos(4,2,5),true);
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        for (var pos:new BlockPos[]{new BlockPos(3,2,3),new BlockPos(4,2,3),new BlockPos(4,2,4),new BlockPos(3,2,4)}) {
            var state=CETransport.REINFORCED_PIPE.get().defaultBlockState();
            for (Direction side:Direction.values()) {
                var next=pos.relative(side);boolean open=next.equals(new BlockPos(2,2,3)) || next.equals(new BlockPos(5,2,3)) || next.equals(new BlockPos(4,2,5)) || next.getY()==2 && next.getX()>=3 && next.getX()<=4 && next.getZ()>=3 && next.getZ()<=4;
                state=state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side),open);
            }
            h.setBlock(pos,state);
        }
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var motor=new BlockPos(1,2,4);h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        source.getTankInventory().fill(water(8000),EXECUTE);
        var cache=new FluidRoutes();
        FluidStack[] maintenance={FluidStack.EMPTY};
        var start=new net.createmod.catnip.math.BlockFace(h.absolutePos(new BlockPos(2,2,3)),Direction.WEST);
        var end=new net.createmod.catnip.math.BlockFace(h.absolutePos(new BlockPos(4,2,3)),Direction.EAST);
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(32));
        h.runAfterDelay(80,()->{
            var path=cache.path(h.getLevel(),start,end);h.assertTrue(!path.isEmpty(),"Representative route exists around loop");
            long count=cache.rebuilds;for (int i=0;i<100;i++) cache.path(h.getLevel(),start,end);
            h.assertTrue(cache.rebuilds==count,"Unchanged route queries never rescan pipes");
            h.assertTrue(source.getTankInventory().getFluidAmount()+a.getTankInventory().getFluidAmount()+b.getTankInventory().getFluidAmount()==8000,"Loop conserves volume before maintenance");
            ((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(0);
            maintenance[0]=source.getTankInventory().drain(8000,EXECUTE);
        });
        h.runAfterDelay(110,()->{
            long count=cache.rebuilds;
            var changed=new BlockPos(4,2,3);var old=h.getBlockState(changed);var next=CETransport.CRYOGENIC_PIPE.get().defaultBlockState();
            for (Direction d:Direction.values()) next=next.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d),old.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d)));
            h.setBlock(changed,next);
            cache.path(h.getLevel(),start,end);h.assertTrue(cache.rebuilds>count,"Material replacement invalidates cached coefficient");
        });
        h.runAfterDelay(150,()->{
            source.getTankInventory().fill(maintenance[0],EXECUTE);maintenance[0]=FluidStack.EMPTY;
            ((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(32);
        });
        h.runAfterDelay(220,()->{
            int total=source.getTankInventory().getFluidAmount()+a.getTankInventory().getFluidAmount()+b.getTankInventory().getFluidAmount();
            h.assertTrue(total==8000 && a.getTankInventory().getFluidAmount()>0 && b.getTankInventory().getFluidAmount()>0,"Loop/branch/replacement preserves volume and both destinations: "+total+" states="+h.getBlockState(new BlockPos(4,2,3))+" / "+h.getBlockState(new BlockPos(4,2,4)));
            h.assertTrue(FluidStack.isSameFluidSameComponents(a.getTankInventory().getFluid(),water(1)),"All routes deliver the water unchanged");h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void cryogenicTanksFormAllWidthsAndKeepIndependentTypes(GameTestHelper h) {
        for (int width=1;width<=3;width++) {
            BlockPos base=new BlockPos(width*7,2,3);
            for (int x=0;x<width;x++) for (int z=0;z<width;z++) for (int y=0;y<3;y++) tank(h,base.offset(x,y,z),true);
            var root=(FluidTankBlockEntity)h.getBlockEntity(base);com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(root);root=root.getControllerBE();
            h.assertTrue(root.getWidth()==width && root.getHeight()==3,"Cryogenic tank forms width "+width);
            h.assertTrue(root.getTankInventory().getCapacity()==width*width*3*FluidTankBlockEntity.getCapacityMultiplier(),"Native volume formula");
            root.getTankInventory().fill(water(1000),EXECUTE);root.getTankInventory().fill(water(3000),EXECUTE);
            h.assertTrue(root.getTankInventory().getFluidAmount()==4000,"All widths preserve combined volume");
            var neighbour=tank(h,base.west(),false);com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(root);
            h.assertTrue(neighbour.getControllerBE()!=root,"Ordinary and cryogenic tanks cannot accidentally merge");
        }
        h.succeed();
    }
}
