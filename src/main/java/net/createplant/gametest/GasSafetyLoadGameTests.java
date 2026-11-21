package net.createplant.gametest;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.createplant.CreatePlant;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.AreaEffectCloud;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The same fixture also compiles at 212aec0f for a before/after server-loop comparison. */
@ExplicitGameTest @GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GasSafetyLoadGameTests {
    @GameTest(template="test_rbmk",timeoutTicks=2500)
    public static void oneHundredTwentyEightPipesAndSixtyFourGasClouds(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(1,2,1));
        for(int row=0;row<8;row++) for(int x=0;x<16;x++) {
            var at=base.offset(x,0,row*2);
            level.setBlock(at,CETransport.STAINLESS_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true),3);
        }
        for(int i=0;i<64;i++) {
            var at=base.offset(i%8*2,5,i/8*2);var cloud=new AreaEffectCloud(level,at.getX()+.5,at.getY(),at.getZ()+.5);
            cloud.addTag(CreatePlant.MOD_ID+".flammable_gas");cloud.setWaitTime(0);cloud.setDuration(10000);cloud.setRadius(4);
            cloud.getPersistentData().put("FlammableContents",FluidThermal.at(new FluidStack(CEFluids.HYDROGEN_GAS.get(),50000),293.15).save(level.registryAccess()));
            cloud.getPersistentData().putInt("FlammableSettled",0);level.addFreshEntity(cloud);
        }
        long[] previous={0};var samples=new java.util.ArrayList<Long>();
        h.onEachTick(()->{
            long now=System.nanoTime();if(h.getTick()>200 && previous[0]>0) samples.add(now-previous[0]);previous[0]=now;
        });
        h.runAfterDelay(2200,()->{
            var sorted=samples.stream().mapToLong(Long::longValue).sorted().toArray();
            double mean=samples.stream().mapToLong(Long::longValue).average().orElseThrow()/1e6;
            double p95=sorted[(int)(sorted.length*.95)]/1e6,maximum=sorted[sorted.length-1]/1e6;
            String result=String.format(java.util.Locale.ROOT,"{\"pipes\":128,\"clouds\":64,\"warmup_ticks\":200,\"samples\":%d,\"mean_loop_ms\":%.6f,\"p95_loop_ms\":%.6f,\"max_loop_ms\":%.6f}",samples.size(),mean,p95,maximum);
            try {var file=java.nio.file.Path.of(System.getProperty("createplant.safetyLoadReport","gas-safety-load.json"));java.nio.file.Files.writeString(file,result+"\n");}
            catch(java.io.IOException e) {throw new RuntimeException(e);}
            org.slf4j.LoggerFactory.getLogger(GasSafetyLoadGameTests.class).info("GAS SAFETY LOAD {}",result);h.succeed();
        });
    }
}
