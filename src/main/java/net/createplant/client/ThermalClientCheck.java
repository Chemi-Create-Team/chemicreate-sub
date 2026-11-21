package net.createplant.client;

import java.util.ArrayList;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.thermal.FluidState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Opt-in check for state rendering and native goggles. */
public final class ThermalClientCheck {
    private ThermalClientCheck() { }

    public static void verify(Minecraft mc) throws java.io.IOException {
        var tank=AllBlockEntityTypes.FLUID_TANK.create(BlockPos.ZERO,AllBlocks.FLUID_TANK.getDefaultState());
        tank.setLevel(mc.level);
        tank.getTankInventory().fill(FluidState.with(new FluidStack(Fluids.WATER,1000),8,4),EXECUTE);
        tank.getTankInventory().fill(FluidState.with(new FluidStack(Fluids.WATER,1000),10,4),EXECUTE);
        var tooltip=new ArrayList<Component>();
        tank.addToGoggleTooltip(tooltip,false);
        var lines=tooltip.stream().map(Component::getString).toList();
        var result=tank.getTankInventory().getFluid();
        if(result.getAmount()!=2000 || FluidState.temperature(result)!=9
            || lines.stream().noneMatch(line->line.contains(FluidState.describe(result).getString())))
            throw new IllegalStateException("Native tank state tooltip failed: "+lines);
        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("thermal-client-result.txt"),String.join("\n",lines));

        if(!Boolean.getBoolean("createplant.fluidStatePreview"))return;
        var server=mc.getSingleplayerServer();
        var playerId=mc.player.getUUID();
        server.execute(()->{
            var level=server.overworld();
            var player=server.getPlayerList().getPlayer(playerId);
            if(player==null)return;
            BlockPos center=player.blockPosition().offset(0,0,4);
            var liquid=FluidState.with(new FluidStack(Fluids.WATER,1000),4,4);
            var gas=FluidState.with(new FluidStack(Fluids.WATER,1000),14,4);
            var process=net.createplant.registry.CETransport.GAS_RECEIVER.get();
            for(int side:new int[]{-2,2}) {
                var sample=side<0 ? liquid : gas;
                var source=center.offset(side,0,0);
                var destination=source.south(4);
                var block=side<0 ? AllBlocks.FLUID_TANK.get() : process;
                var sourceState=block.defaultBlockState();
                level.setBlockAndUpdate(source,sourceState);
                level.setBlockAndUpdate(destination,sourceState);
                var supply=(FluidTankBlockEntity)level.getBlockEntity(source);
                var target=(FluidTankBlockEntity)level.getBlockEntity(destination);
                supply.setWindows(true);
                target.setWindows(true);
                supply.getTankInventory().setFluid(sample.copyWithAmount(side<0 ? 7500 : 440000));
                level.setBlockAndUpdate(source.south(),AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
                var pump=source.south(2);
                level.setBlockAndUpdate(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
                level.setBlockAndUpdate(source.south(3),AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
                var cog=pump.east();
                level.setBlockAndUpdate(cog,AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
                level.setBlockAndUpdate(cog.north(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
                if(level.getBlockEntity(cog.north()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
                    motor.generatedSpeed.setValue(16);
            }
            player.setItemSlot(EquipmentSlot.HEAD,AllItems.GOGGLES.asStack());
            player.getInventory().setItem(0,FluidUtil.getFilledBucket(liquid));
            player.getInventory().setItem(1,FluidUtil.getFilledBucket(gas));
            player.connection.teleport(center.getX()+.5,center.getY()+.7,center.getZ()-2.6,0,19);
        });
    }
}
