package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Opt-in integrated-world check: the same pipe run in Create's copper (back row) and in stainless steel (front row), for side-by-side review. */
final class StainlessPipeClientCheck {
    /** Camera (x, y, z, yaw, pitch) relative to the origin with a narrow FOV: front-left isometric, then straight on. */
    private static final float[][] VIEWS = {{-8, 9, 13, -145, 30}, {3, 4, 14, 180, 12}};
    private static BlockPos origin;
    private static int view;
    private static int ticks;
    private static int fov;

    private StainlessPipeClientCheck() { }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        fov = mc.options.fov().get();
        mc.options.fov().set(30);
        origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 6);
        mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            build(level, origin, AllBlocks.FLUID_PIPE.get(), AllBlocks.GLASS_FLUID_PIPE.get(), AllBlocks.ENCASED_FLUID_PIPE.get());
            build(level, origin.south(3), CETransport.STAINLESS_PIPE.get(), CETransport.STAINLESS_GLASS_PIPE.get(), CETransport.STAINLESS_ENCASED_PIPE.get());
        });
        view = -1;
        ticks = 0;
    }

    /** An open end, a straight run with a branch, a glass section, an encased section and a corner rising at the far end. */
    private static void build(net.minecraft.server.level.ServerLevel level, BlockPos at, Block pipe, Block glass, Block encased) {
        for (int x = 0; x <= 6; x++)
            level.setBlock(at.east(x), pipe.defaultBlockState(), 3);
        level.setBlock(at.east(2).above(), pipe.defaultBlockState(), 3);
        level.setBlock(at.east(6).above(), pipe.defaultBlockState(), 3);
        for (BlockPos p : new BlockPos[]{at, at.east(1), at.east(2), at.east(2).above(), at.east(5), at.east(6), at.east(6).above()})
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
        level.setBlock(at.east(3), glass.defaultBlockState().setValue(GlassFluidPipeBlock.AXIS, Direction.Axis.X), 3);
        BlockState cased = encased.defaultBlockState();
        for (Direction d : Direction.values())
            cased = cased.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), d.getAxis() == Direction.Axis.X);
        level.setBlock(at.east(4), cased, 3);
        // A creative tank and a driven pump at the open end fill the run, so the glass sections show the fluid inside.
        level.setBlock(at.west(2), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState(), 3);
        if (level.getBlockEntity(at.west(2)) instanceof CreativeFluidTankBlockEntity tank
            && tank.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank source)
            source.setContainedFluid(new FluidStack(Fluids.WATER, 1000));
        level.setBlock(at.west(), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        level.setBlock(at.west().north(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        level.setBlock(at.west(2).north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(at.west(2).north()) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
    }

    /** Returns true once every view is captured. */
    static boolean tick(Minecraft mc) {
        ticks++;
        if (mc.screen != null)
            mc.setScreen(null);
        if (view < 0 && ticks >= 120 || view >= 0 && ticks == 45) {
            if (view >= 0)
                Screenshot.grab(mc.gameDirectory, "quality-stainless-pipe-" + view + ".png", mc.getMainRenderTarget(), message -> { });
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
        if (view >= 0 && ticks >= 35) {
            float[] v = VIEWS[view];
            mc.player.setYRot(v[3]);
            mc.player.setXRot(v[4]);
            mc.player.yRotO = v[3];
            mc.player.xRotO = v[4];
        }
        return false;
    }
}
