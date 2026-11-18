package net.createplant.client;

import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.block.GasHolderGeometry;
import net.createplant.gametest.GasBellGameTests;
import net.createplant.registry.CEFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Native tank construction and all three physical sleeve positions in a disposable integrated world. */
final class GasBellClientCheck {
    private static final int WIDTH=Math.clamp(Integer.getInteger("createplant.gasBellClientWidth",3),1,GasHolderGeometry.MAX_WIDTH),CHARGE=WIDTH*WIDTH*8000;
    private static final String PREFIX="quality-telescopic-holder-"+WIDTH+"x"+WIDTH+"-";
    private static BlockPos origin;
    private static int stage,ticks,fov;
    private static boolean hideGui;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private GasBellClientCheck() { }
    static void start(Minecraft mc) {
        mc.setScreen(null);mc.options.pauseOnLostFocus=false;fov=mc.options.fov().get();hideGui=mc.options.hideGui;mc.options.hideGui=true;mc.options.fov().set(48);
        origin=new BlockPos(0,mc.player.blockPosition().getY()+3,0);stage=0;ticks=0;ready=false;failure=null;
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->{
            try {
                var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                var be=GasBellGameTests.build(level,origin,WIDTH,3);
                if (be.getWidth()!=WIDTH || be.getHeight()!=3)throw new IllegalStateException("Native tank did not form");
                var player=server.getPlayerList().getPlayer(id);player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(WIDTH*.5-10.5,origin.getY()+9,WIDTH*.5+18.5,-151,14);ready=true;
            } catch(Throwable error){failure=error;}
        });
    }
    static boolean tick(Minecraft mc) {
        if(failure!=null)throw new IllegalStateException("Telescopic holder fixture failed",failure);
        if(mc.screen!=null)mc.setScreen(null);if(++ticks>450)throw new IllegalStateException("Holder synchronization timed out at "+stage);
        if(!ready || ticks<90)return false;
        if(!(mc.level.getBlockEntity(origin) instanceof GasHolderBlockEntity be) || be.sleeve(2)==null) {if(ticks<300)return false;diagnose(mc);if(!Boolean.getBoolean("createplant.clientQualitySkipScreenshots"))Screenshot.grab(mc.gameDirectory,PREFIX+"error.png",mc.getMainRenderTarget(),message->{});throw new IllegalStateException("Three native sleeve entities did not synchronize");}
        double expected=stage==0 || stage==4 ? 0 : stage*2.5;
        if(Math.abs(be.offset()-expected)>.02)throw new IllegalStateException("Wrong synchronized lift "+be.offset()+" expected "+expected);
        for(int i=0;i<3;i++) if(be.sleeve(i)==null || !be.sleeve(i).isAlive() || Math.abs(be.sleeve(i).getY()-(origin.getY()+.125+be.geometry().offset(expected,i)))>.03)
            throw new IllegalStateException("Native sleeve position disagrees at stage "+stage+" / sleeve "+i);
        if(ticks==90)org.slf4j.LoggerFactory.getLogger(GasBellClientCheck.class).info("TELESCOPIC HOLDER STAGE: {} width={} lift={}",new String[]{"empty","inner","middle","full","retracted"}[stage],WIDTH,be.offset());
        if(ticks<Math.clamp(Integer.getInteger("createplant.gasBellClientStageTicks",90),90,400))return false;
        if(!Boolean.getBoolean("createplant.clientQualitySkipScreenshots"))Screenshot.grab(mc.gameDirectory,PREFIX+new String[]{"empty","inner","middle","full","retracted"}[stage]+".png",mc.getMainRenderTarget(),message->{});
        if(stage==4) {
            if(!be.gas().isEmpty())throw new IllegalStateException("Gas remained after recovery");mc.options.hideGui=hideGui;mc.options.fov().set(fov);
            org.slf4j.LoggerFactory.getLogger(GasBellClientCheck.class).info("TELESCOPIC HOLDER CLIENT PASS: {}x{}x3 native casing; {} mB; inner/middle/outer sequence; 0/2.5/5/7.5/0 m; all three collision bodies synchronized",WIDTH,WIDTH,CHARGE*3);return true;
        }
        stage++;ticks=0;var server=mc.getSingleplayerServer();int next=stage;
        server.execute(()->{
            try {
                var port=server.overworld().getCapability(Capabilities.FluidHandler.BLOCK,origin.east(WIDTH-1),Direction.DOWN);
                if(next<4 && port.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),CHARGE),EXECUTE)!=CHARGE)throw new IllegalStateException("Native member rejected its gas charge");
                if(next==4 && port.drain(Integer.MAX_VALUE,EXECUTE).getAmount()!=CHARGE*3)throw new IllegalStateException("The finite charge was not recovered exactly once");
            } catch(Throwable error){failure=error;}
        });
        return false;
    }
    private static void diagnose(Minecraft mc) {
        var log=org.slf4j.LoggerFactory.getLogger(GasBellClientCheck.class);var raw=mc.level.getBlockEntity(origin);
        if(raw instanceof GasHolderBlockEntity be) {var data=be.saveWithoutMetadata(mc.level.registryAccess());log.info("HOLDER CLIENT dimensions={}/{} controller={} status={} ids={}/{}/{}",be.getWidth(),be.getHeight(),be.isController(),be.status(),data.get("Sleeve0"),data.get("Sleeve1"),data.get("Sleeve2"));}
        else log.info("HOLDER CLIENT missing root {}",raw);
        for(var entity:mc.level.entitiesForRendering()) if(entity instanceof com.simibubi.create.content.contraptions.ControlledContraptionEntity moving) {
            var data=new net.minecraft.nbt.CompoundTag();entity.save(data);log.info("HOLDER CLIENT entity={} pos={} controllerRelative={} sleeve={}",entity.getUUID(),entity.position(),data.get("ControllerRelative"),moving.getContraption() instanceof net.createplant.contraptions.GasHolderSleeveContraption sleeve ? sleeve.stage()+":"+sleeve.matches(WIDTH,3) : moving.getContraption());
        }
        mc.getSingleplayerServer().execute(()->{var rawServer=mc.getSingleplayerServer().overworld().getBlockEntity(origin);if(rawServer instanceof GasHolderBlockEntity be)log.info("HOLDER SERVER dimensions={}/{} status={} bodies={}/{}/{}",be.getWidth(),be.getHeight(),be.status(),be.sleeve(0),be.sleeve(1),be.sleeve(2));});
    }
}
