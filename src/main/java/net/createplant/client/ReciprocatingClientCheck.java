package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.createplant.registry.CEReciprocating;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** Opt-in integrated-world check: an engine on top of a cylinder, and two engines facing each other, with their crank shafts turning from a creative motor. */
final class ReciprocatingClientCheck {
    private static final boolean OPPOSED = Boolean.getBoolean("createplant.opposedCompressorClientCheck");
    /** Camera (x, y, z, yaw, pitch) relative to the first cylinder, with a narrow FOV. */
    private static final float[][] VIEWS = {{-5, 5, 6, -140, 24}, {4, 5, 7, 160, 24}, {9, 9, 6, 180, 55}};
    private static BlockPos origin;
    private static int view, ticks, fov;

    private ReciprocatingClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(35);
        origin = new BlockPos(Boolean.getBoolean("createplant.reactorConsoleClientCheck") ? 24 : 0, mc.player.blockPosition().getY() + 1, 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            if (OPPOSED) { opposed(level);return; }
            var shaft = AllBlocks.SHAFT.getDefaultState();
            level.setBlock(origin, CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
            level.setBlock(origin.above(), CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.FACE, AttachFace.FLOOR).setValue(ReciprocatingCompressorBlock.FACING, Direction.NORTH), 3);
            level.setBlock(origin.above(3), shaft.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), 3);
            level.setBlock(origin.above(3).west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST), 3);
            ((CreativeMotorBlockEntity) level.getBlockEntity(origin.above(3).west())).generatedSpeed.setValue(32);
            BlockPos ceiling=origin.offset(3,5,0);
            level.setBlock(ceiling,CEReciprocating.CYLINDER.get().defaultBlockState(),3);
            level.setBlock(ceiling.below(),CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.FACE,AttachFace.CEILING).setValue(ReciprocatingCompressorBlock.FACING,Direction.NORTH),3);
            level.setBlock(ceiling.below(3),shaft.setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.X),3);
            level.setBlock(ceiling.below(3).west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING,Direction.EAST),3);
            ((CreativeMotorBlockEntity)level.getBlockEntity(ceiling.below(3).west())).generatedSpeed.setValue(32);
            // Two engines facing each other, their crank shafts joined into one network: both cranks must turn the way the shaft turns.
            BlockPos wall = origin.offset(6, 0, 0);
            var east = CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.FACE, AttachFace.WALL);
            level.setBlock(wall, CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
            level.setBlock(wall.east(), east.setValue(ReciprocatingCompressorBlock.FACING, Direction.EAST), 3);
            level.setBlock(wall.offset(6, 0, 2), CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
            level.setBlock(wall.offset(5, 0, 2), east.setValue(ReciprocatingCompressorBlock.FACING, Direction.WEST), 3);
            for (int z = 0; z <= 2; z++) level.setBlock(wall.offset(3, 0, z), shaft.setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), 3);
            level.setBlock(wall.offset(3, 0, -1), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), 3);
            ((CreativeMotorBlockEntity) level.getBlockEntity(wall.offset(3, 0, -1))).generatedSpeed.setValue(32);
        });
        view = -1;
        ticks = 0;
    }

    private static void opposed(ServerLevel level) {
        for (int x = -3; x <= 3; x++) for (int z = -1; z <= 3; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        var state = CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.OPPOSED, true)
            .setValue(ReciprocatingCompressorBlock.WINDOW, true);
        level.setBlock(origin, state, 3);
        for (var side : new Direction[]{Direction.WEST, Direction.EAST})
            level.setBlock(origin.relative(side, 2), CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
        level.setBlock(origin.south(), AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), 3);
        level.setBlock(origin.south(2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH), 3);
        ((CreativeMotorBlockEntity) level.getBlockEntity(origin.south(2))).generatedSpeed.setValue(32);
        level.setDayTime(6000);
    }

    /** Returns true once every view is captured. */
    static boolean tick(Minecraft mc) {
        ticks++;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) mc.setScreen(null);
        int hold=Integer.getInteger("createplant.reciprocatingClientHold",0);
        if (OPPOSED) {
            if (ticks == 65) {
                if (!(mc.level.getBlockEntity(origin) instanceof ReciprocatingCompressorBlockEntity drive) || drive.connectedSides() != 3
                    || !(mc.level.getBlockEntity(origin.south()) instanceof ReciprocatingShaftBlockEntity shaft) || shaft.getSpeed() == 0)
                    throw new IllegalStateException("The opposed compressor did not sync both cylinders and a moving shaft");
                mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.getAbilities().flying = true;player.onUpdateAbilities();
                    player.connection.teleport(origin.getX() + 3.5, origin.getY() + 3, origin.getZ() - 7, 23, 24);
                });
            }
            return ticks >= Math.max(100, hold);
        }
        if(hold>0) {
            if(ticks==60) {
                mc.options.hideGui=true;
                mc.getSingleplayerServer().execute(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.connection.teleport(origin.getX()-4,origin.getY()+2,origin.getZ()+6,-145,16);
                });
            }
            return ticks>=hold;
        }
        if (view < 0 && ticks >= 60 || view >= 0 && ticks == 25) {
            if (view >= 0) Screenshot.grab(mc.gameDirectory, "quality-reciprocating-" + view + ".png", mc.getMainRenderTarget(), message -> { });
            if (++view == VIEWS.length) {
                mc.options.hideGui = false;
                mc.options.fov().set(fov);
                return true;
            }
            float[] v = VIEWS[view];
            mc.options.hideGui = true;
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(origin.getX() + v[0], origin.getY() + v[1], origin.getZ() + v[2], v[3], v[4]);
            });
            ticks = 0;
        }
        if (view >= 0 && ticks >= 15) {
            float[] v = VIEWS[view];
            mc.player.setYRot(v[3]);
            mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3];
            mc.player.xRotO = v[4];
        }
        return false;
    }
}
