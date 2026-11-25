package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.HashSet;
import net.createplant.CreatePlant;
import net.createplant.block.FlareTipBlock;
import net.createplant.block.entity.FlareTipBlockEntity;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.registry.CEFlare;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.cloud.GasClouds;
import net.createplant.safety.FlammableGas;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.GradeFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class FlareGameTests {
    private static final BlockPos BASE=new BlockPos(3,3,5);
    private static BlockState pipe(Direction... sides) {
        var state=CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (var side:Direction.values()) {
            boolean connected=false;for (var entry:sides) if (entry==side) connected=true;
            state=state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side),connected);
        }
        return state;
    }
    private static FluidStack hydrogen(int amount) { return FluidThermal.at(new FluidStack(CEFluids.HYDROGEN_GAS.get(),amount),293.15); }
    private static GradeFluidTank tank(GameTestHelper h,BlockPos pos) {
        h.getLevel().setBlock(pos,CETransport.PRESSURE_TANK.get().defaultBlockState(),3);
        return (GradeFluidTank)((FluidTankBlockEntity)h.getLevel().getBlockEntity(pos)).getTankInventory();
    }
    private static FlareTipBlockEntity flare(GameTestHelper h,BlockPos pos,boolean lit) {
        // GameTest roofs are invisible barriers, which block exhaust despite transmitting skylight.
        for (int y=1;y<=70;y++) h.getLevel().removeBlock(pos.above(y),false);
        for (int y=1;y<=3;y++) h.getLevel().setBlock(pos.below(y),pipe(Direction.UP,Direction.DOWN),3);
        h.getLevel().setBlock(pos,CEFlare.TIP.get().defaultBlockState().setValue(FlareTipBlock.PILOT,lit),3);
        return (FlareTipBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static IFluidHandler port(GameTestHelper h,BlockPos pos) { return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,Direction.DOWN); }
    private static java.util.List<net.createplant.cloud.GasCloud> clouds(GameTestHelper h,BlockPos pos) { return GasClouds.in(h.getLevel(),new AABB(pos).inflate(5)); }






    @GameTest(template="test_rbmk",timeoutTicks=150)
    public static void flareConsumesFiniteGasAndStopsWhenEmpty(GameTestHelper h) {
        var at=h.absolutePos(BASE).above(8);var tip=flare(h,at,true);var port=port(h,at);var gas=hydrogen(64000);
        h.assertTrue(port!=null && h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.NORTH)==null,"Only the bottom is a fluid inlet");
        h.assertTrue(port.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"Liquid is rejected");
        h.assertTrue(port.fill(gas,SIMULATE)==64000 && tip.gas().isEmpty(),"Simulated acceptance does not consume or fill: height="+h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,at.getX(),at.getZ())+" tip="+at.getY()+" sky="+h.getLevel().canSeeSky(at.above()));
        h.assertTrue(port.fill(gas,EXECUTE)==64000,"A chimney receives real combustible gas");
        h.runAfterDelay(5,()->{
            h.assertTrue(tip.burnt()==32000 && tip.gas().getFluidAmount()==32000 && tip.flameHeight(0)>14 && tip.flameHeight(0)<15,"32000 mB is burnt once, forming a 14-block flame: "+tip.burnt()+" / "+tip.flameHeight(0));
            var saved=tip.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(at);
            var loaded=new FlareTipBlockEntity(at,h.getLevel().getBlockState(at));loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(loaded);
            h.assertTrue(loaded.burnt()==32000 && loaded.gas().getFluidAmount()==32000 && !loaded.burning(),"Reload retains totals without inventing gas or an active flame");
            h.assertTrue(port.fill(gas,EXECUTE)==0,"A cached handler of a removed tip cannot accept material");
        });
        h.runAfterDelay(110,()->{var loaded=(FlareTipBlockEntity)h.getLevel().getBlockEntity(at);h.assertTrue(!loaded.burning() && loaded.burnt()==64000,"Empty flare retains pilot only");h.succeed();});
    }

    @GameTest(template="test_rbmk",timeoutTicks=120)
    public static void blockedOrIncompleteChimneyCannotDeleteGas(GameTestHelper h) {
        var level=h.getLevel();var at=h.absolutePos(BASE).above(8);var tip=flare(h,at,true);var port=port(h,at);
        level.removeBlock(at.below(2),false);
        h.assertTrue(port.fill(hydrogen(1000),EXECUTE)==0,"Three connected risers are required");
        level.setBlock(at.below(2),pipe(Direction.UP,Direction.DOWN),3);
        port.fill(hydrogen(10000),EXECUTE);level.setBlock(at.above(3),Blocks.STONE.defaultBlockState(),3);
        h.runAfterDelay(15,()->{h.assertTrue(tip.gas().getFluidAmount()==10000 && tip.burnt()==0 && port.fill(hydrogen(1000),EXECUTE)==0,"Blocked sky retains gas and refuses new input: stored="+tip.gas().getFluidAmount()+" burnt="+tip.burnt()+" sky="+level.canSeeSky(at.above())+" state="+tip.status());level.removeBlock(at.above(3),false);});
        h.runAfterDelay(100,()->{h.assertTrue(tip.burnt()==10000 && tip.gas().isEmpty(),"Unblocking resumes without loss");h.succeed();});
    }

    @GameTest(template="test_rbmk",timeoutTicks=260)
    public static void unlitFuelCreatesARealCloudButInertGasNeverCreatesTallFire(GameTestHelper h) {
        var at=h.absolutePos(BASE).above(8);var tip=flare(h,at,false);port(h,at).fill(hydrogen(10000),EXECUTE);
        h.runAfterDelay(80,()->{
            h.assertTrue(tip.vented()==10000 && tip.burnt()==0 && !tip.burning() && clouds(h,at).size()==1,"An unlit tip releases finite fuel instead of silently burning it");
            for (var cloud:clouds(h,at)) cloud.discard();
            h.getLevel().setBlock(at,tip.getBlockState().setValue(FlareTipBlock.PILOT,true),3);
            port(h,at).fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),10000),EXECUTE);
        });
        h.runAfterDelay(155,()->{
            h.assertTrue(tip.vented()==20000 && tip.burnt()==0 && !tip.burning() && clouds(h,at).isEmpty(),"Nitrogen vents without combustible cloud or a false flame");
            port(h,at).fill(FluidThermal.at(FluidForm.COMPRESSED_SYNTHESIS_FEED.stack(10000),320),EXECUTE);
        });
        h.runAfterDelay(235,()->{h.assertTrue(tip.burnt()==10000 && tip.vented()==20000,"The existing compressed Haber-Bosch feed burns as real fuel");h.succeed();});
    }


    @GameTest(template="test_rbmk",timeoutTicks=160)
    public static void cloudsPersistMergeDissipateAndOnlyRecognizeRealIgnition(GameTestHelper h) {
        var at=h.absolutePos(BASE).above(8);var level=h.getLevel();
        ToxicGas.release(level,at,hydrogen(1000));ToxicGas.release(level,at,hydrogen(2000));
        h.assertTrue(clouds(h,at).size()==1 && clouds(h,at).getFirst().amount()==3000,"Repeated leaks merge actual finite gas");
        var cloud=clouds(h,at).getFirst();var saved=new net.minecraft.nbt.CompoundTag();cloud.save(saved);cloud.discard();
        var copy=net.createplant.registry.CESafety.GAS_CLOUD.get().create(level);copy.load(saved);level.addFreshEntity(copy);
        h.assertTrue(copy.amount()==3000 && copy.content().is(net.createplant.registry.CEFluids.HYDROGEN_GAS.get()) && !copy.inert(),"Cloud persistence preserves its gas");
        h.assertTrue(FlammableGas.ignition(AllBlocks.BLAZE_BURNER.getDefaultState().setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED))
            && !FlammableGas.ignition(AllBlocks.BLAZE_BURNER.getDefaultState()) && !FlammableGas.ignition(Blocks.REDSTONE_TORCH.defaultBlockState()),"Burning Blaze burners ignite; cold burners and redstone signals do not");
        h.runAfterDelay(60,()->{int remaining=copy.amount();h.assertTrue(remaining>0 && remaining<3000,"Atmospheric cloud disperses without any material generation: "+remaining);copy.discard();h.succeed();});
    }


    @GameTest(template="test_rbmk",timeoutTicks=100)
    public static void nativeIgnitionConsumesAnItemAndWaterExtinguishesPilot(GameTestHelper h) {
        var at=h.absolutePos(BASE).above(8);var level=h.getLevel();var tip=flare(h,at,false);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        net.minecraft.world.level.GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var item=new ItemStack(Items.FIRE_CHARGE,2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),Direction.UP,at,false);
        level.getBlockState(at).useItemOn(item,level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(tip.pilot() && item.getCount()==1,"Actual ignition lights the pilot and consumes one charge");
        level.getBlockState(at).useItemOn(item,level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(item.getCount()==1,"An already lit pilot does not consume another item");
        level.setBlock(at,level.getBlockState(at).setValue(FlareTipBlock.WATERLOGGED,true),3);
        h.runAfterDelay(8,()->{h.assertTrue(!tip.pilot() && port(h,at).fill(hydrogen(1000),EXECUTE)==0,"Water extinguishes pilot and blocks inflow");h.succeed();});
    }


    @GameTest(template="test_rbmk",timeoutTicks=200)
    public static void manyColdCloudsStayBoundedAndFinite(GameTestHelper h) {
        var at=h.absolutePos(BASE).above(10);var level=h.getLevel();
        for (int i=0;i<64;i++) {
            ToxicGas.release(level,at.offset(i%8*2,0,i/8*2),hydrogen(500));
        }
        h.runAfterDelay(80,()->{
            var clouds=GasClouds.in(level,new AABB(at).expandTowards(26,3,26).inflate(3));
            long amount=0;for (var cloud:clouds) { amount+=cloud.amount();h.assertTrue(cloud.volume().size()<=512,"Cloud size is bounded");cloud.discard(); }
            h.assertTrue(!clouds.isEmpty() && clouds.size()<=64 && amount>0 && amount<32000,"64 releases remain finite after 80 ticks: clouds="+clouds.size()+" remaining="+amount);h.succeed();
        });
    }
}
