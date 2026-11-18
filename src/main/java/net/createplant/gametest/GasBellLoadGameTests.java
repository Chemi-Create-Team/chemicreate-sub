package net.createplant.gametest;

import java.util.ArrayList;
import java.util.Locale;
import net.createplant.CreatePlant;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.registry.CEFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@ExplicitGameTest @GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GasBellLoadGameTests {
    @GameTest(template="test_fusion",timeoutTicks=1700)
    public static void sixteenIdleAndMovingNativeRoofsKeepAllGas(GameTestHelper h) {
        var level=h.getLevel();var holders=new ArrayList<GasHolderBlockEntity>();
        int width=Math.clamp(Integer.getInteger("createplant.gasBellLoadWidth",3),1,net.createplant.block.GasHolderGeometry.MAX_WIDTH),charge=width*width*8000;
        for(int x=0;x<4;x++) for(int z=0;z<4;z++) {
            var be=GasBellGameTests.build(level,h.absolutePos(new BlockPos(2+x*(width+5),3,2+z*(width+5))),width,2);
            h.assertTrue(be.formed(),"Load fixture forms every native holder");holders.add(be);
        }
        long total=16L*charge,pool[]={total},previous[]={0};var idle=new ArrayList<Long>();var moving=new ArrayList<Long>();
        h.onEachTick(()->{
            long now=System.nanoTime();long tick=h.getTick();
            if(previous[0]>0) {if(tick>200 && tick<=600) idle.add(now-previous[0]);if(tick>650 && tick<=1450)moving.add(now-previous[0]);}
            previous[0]=now;
            if(tick>=650 && tick<1450 && (tick-650)%40==0) {
                boolean fill=(tick-650)/40%2==0;
                for(var be:holders) {
                    var port=level.getCapability(Capabilities.FluidHandler.BLOCK,be.getBlockPos(),Direction.DOWN);
                    if(fill) {
                        int amount=port.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),charge),EXECUTE);
                        if(amount!=charge)h.fail("Load rejected "+amount+" / "+charge+" at "+be.getBlockPos()+" status="+be.status()+" size="+be.getWidth()+"x"+be.getHeight()+" removed="+be.isRemoved()+" gas="+be.gas().getFluidAmount()+" lift="+be.offset()+" loaded="+level.hasChunkAt(be.getBlockPos()));
                        pool[0]-=amount;
                    }
                    else {int amount=port.drain(charge,EXECUTE).getAmount();h.assertTrue(amount==charge,"Each unload cycle recovers its complete charge");pool[0]+=amount;}
                }
            }
        });
        h.runAfterDelay(1500,()->{
            long kept=pool[0];for(var be:holders) {
                kept+=be.gas().getFluidAmount();h.assertTrue(be.formed(),"Every holder stays formed");
                for(int stage=0;stage<3;stage++)h.assertTrue(be.sleeve(stage)!=null && be.sleeve(stage).isAlive(),"All native sleeves stay attached");
            }
            h.assertTrue(kept==total,"Repeated loading and unloading preserve the complete finite charge: "+kept+" / "+total);
            String result=String.format(Locale.ROOT,"{\"holders\":16,\"width\":%d,\"height\":2,\"collision_bodies_each\":3,\"gas_mB\":%d,\"idle\":%s,\"moving\":%s}",width,kept,summary(idle),summary(moving));
            try {java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("createplant.safetyLoadReport","gas-bell-load.json")),result+"\n");}
            catch(java.io.IOException e){throw new RuntimeException(e);}
            org.slf4j.LoggerFactory.getLogger(GasBellLoadGameTests.class).info("GAS BELL LOAD {}",result);h.succeed();
        });
    }
    private static String summary(ArrayList<Long> values) {
        long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT,"{\"samples\":%d,\"mean_loop_ms\":%.6f,\"p95_loop_ms\":%.6f,\"max_loop_ms\":%.6f}",values.size(),values.stream().mapToLong(Long::longValue).average().orElseThrow()/1e6,sorted[(int)(sorted.length*.95)]/1e6,sorted[sorted.length-1]/1e6);
    }
}
