package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Opt-in real-world liquid level, synchronization and installed-size comparison for all four cartridges. */
final class VesselContentsClientCheck {
    private static final ReactorModule[] MODULES={ReactorModule.STIRRED};
    private static BlockPos[] origins;
    private static int phase,ticks,fov;
    private static boolean hideGui;
    private static volatile int ready;
    private static volatile Throwable failure;

    private VesselContentsClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus=false;mc.setScreen(null);
        fov=mc.options.fov().get();hideGui=mc.options.hideGui;
        mc.options.fov().set(50);mc.options.hideGui=true;
        int y=mc.player.blockPosition().getY()+2;
        origins=new BlockPos[]{new BlockPos(0,y,0),new BlockPos(5,y,0),new BlockPos(10,y,0),new BlockPos(15,y,0)};
        phase=0;ticks=0;ready=0;failure=null;
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->{
            try {
                var level=server.overworld();level.setDayTime(6000);
                for(int x=-1;x<=18;x++)for(int z=-1;z<=3;z++)level.setBlock(new BlockPos(x,y-2,z),Blocks.SMOOTH_STONE.defaultBlockState(),Block.UPDATE_ALL);
                for(var origin:origins)for(int dy=0;dy<2;dy++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)
                    level.setBlock(origin.offset(x,dy,z),CETransport.LIDDED_VESSEL.get().defaultBlockState().setValue(LiddedVesselBlock.HINGE,Direction.NORTH),Block.UPDATE_ALL);
                var player=server.getPlayerList().getPlayer(id);player.getAbilities().flying=true;player.onUpdateAbilities();
                player.connection.teleport(9,y+4,24,180,10);
                ready=1;
            } catch(Throwable error){failure=error;}
        });
    }

    static boolean tick(Minecraft mc) {
        if(failure!=null)throw new IllegalStateException("Vessel contents fixture failed",failure);
        if(mc.screen!=null)mc.setScreen(null);
        if(++ticks>400)throw new IllegalStateException("Vessel contents synchronization timed out at phase "+phase);
        mc.player.setYRot(180);mc.player.yRotO=180;mc.player.setXRot(phase>=4 ? 7 : 10);mc.player.xRotO=mc.player.getXRot();
        if(phase==0 && ready==1 && ticks>=20){prepare(mc,1);phase=1;ticks=0;return false;}
        if(phase<1 || ready!=phase+1 || ticks<(phase>=4 ? Integer.getInteger("createplant.vesselContentsCaptureDelay",70) : 70))return false;
        verify(mc,phase==1 ? .5 : phase==3 ? 0 : 1);
        String name=phase==1 ? "half" : phase==2 ? "full" : phase==3 ? "internals" : "submerged-"+MODULES[phase-4].name().toLowerCase(java.util.Locale.ROOT);
        Screenshot.grab(mc.gameDirectory,"quality-vessel-contents-"+name+".png",mc.getMainRenderTarget(),message->{});
        if(phase<3+MODULES.length){phase++;ticks=0;prepare(mc,phase);return false;}
        mc.options.hideGui=hideGui;mc.options.fov().set(fov);
        org.slf4j.LoggerFactory.getLogger(VesselContentsClientCheck.class).info("VESSEL CONTENTS PASS: four native 3x3x2 vessels; half/full/empty amounts, window geometry, bottom drives and submerged internals inspected");
        return true;
    }

    private static void prepare(Minecraft mc,int stage) {
        var server=mc.getSingleplayerServer();
        server.execute(()->{
            try {
                var level=server.overworld();
                for(int i=0;i<origins.length;i++) {
                    var origin=origins[i];var owner=((LiddedVesselBlockEntity)level.getBlockEntity(origin)).getControllerBE();
                    if(stage==1){
                        level.setBlock(owner.stirrerPos(), CEReactor.STIRRER.get().defaultBlockState(), 3);
                        owner.validateStructure();owner.toggleWindows();
                    }
                    int capacity=MODULES[i]==ReactorModule.STIRRED ? owner.getTankInventory().getCapacity()+ReactorMachineBlockEntity.CAPACITY : ReactorMachineBlockEntity.CAPACITY;
                    var port=level.getCapability(Capabilities.FluidHandler.BLOCK,origin,Direction.NORTH);
                    if(port==null)throw new IllegalStateException("No real fluid input for "+MODULES[i]);
                    var fluid=Fluids.WATER;
                    if(stage<=2 || stage==4) {
                        if(stage==4) owner.toggleLid();
                        port.fill(new FluidStack(fluid,stage==1 ? capacity/2 : capacity),EXECUTE);
                    } else if(stage==3) {
                        owner.processRecoveryPort().drain(Integer.MAX_VALUE,EXECUTE);owner.getTankInventory().drain(Integer.MAX_VALUE,EXECUTE);owner.toggleLid();
                        var motor=origin.offset(1,-1,1);
                        level.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP),Block.UPDATE_ALL);
                        ((CreativeMotorBlockEntity)level.getBlockEntity(motor)).generatedSpeed.setValue(32);
                    }
                    owner.sendData();
                }
                if(stage>=4) {
                    var origin=origins[stage-4];var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.connection.teleport(origin.getX()+1.5,origin.getY()+.25,origin.getZ()+7,180,7);
                    org.slf4j.LoggerFactory.getLogger(VesselContentsClientCheck.class).info("VESSEL SUBMERGED VIEW {}",MODULES[stage-4]);
                }
                ready=stage+1;
            } catch(Throwable error){failure=error;}
        });
    }

    private static void verify(Minecraft mc,double expected) {
        for(int i=0;i<origins.length;i++) {
            var owner=((LiddedVesselBlockEntity)mc.level.getBlockEntity(origins[i])).getControllerBE();
            if(owner==null || owner.getWidth()!=3 || owner.getHeight()!=2 || !owner.formed() || owner.module()!=MODULES[i])
                throw new IllegalStateException("Installed module or dimensions did not synchronize: "+MODULES[i]);
            long amount=0;for(int slot=0;slot<owner.displayedFluidSlots();slot++)amount+=owner.displayedLiquid(slot).getAmount();
            double fraction=(double)amount/owner.getDisplayedCapacity();
            if(Math.abs(fraction-expected)>.002)throw new IllegalStateException("Wrong client liquid fraction for "+MODULES[i]+": "+fraction+" expected "+expected);
            VesselClientCheck.verifyWindowQuads(mc,mc.level,owner);
            if(phase==3 && owner.rotationSpeed()==0)throw new IllegalStateException("The installed module is not receiving actual shaft power: "+MODULES[i]);
        }
    }
}
