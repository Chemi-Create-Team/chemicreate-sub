package net.createplant.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Turns the authored collision boxes of cast fittings and converter shells. */
public final class CompressorShapes {
    private CompressorShapes() { }
    public static VoxelShape of(double[][] boxes, Direction facing) {
        VoxelShape shape=Shapes.empty();
        for (double[] b:boxes) {
            double x1=b[0],z1=b[2],x2=b[3],z2=b[5];
            double[] r=switch (facing) {
                case EAST -> new double[]{16-z2,x1,16-z1,x2};
                case SOUTH -> new double[]{16-x2,16-z2,16-x1,16-z1};
                case WEST -> new double[]{z1,16-x2,z2,16-x1};
                default -> new double[]{x1,z1,x2,z2};
            };
            shape=Shapes.or(shape,Block.box(r[0],b[1],r[1],r[2],b[4],r[3]));
        }
        return shape.optimize();
    }
    /** The world offset of an authored offset (x east, y up, z south while facing north) turned like the models. */
    public static net.minecraft.core.BlockPos turn(int x, int y, int z, Direction facing) {
        return switch (facing) {
            case EAST -> new net.minecraft.core.BlockPos(-z,y,x);
            case SOUTH -> new net.minecraft.core.BlockPos(-x,y,-z);
            case WEST -> new net.minecraft.core.BlockPos(z,y,-x);
            default -> new net.minecraft.core.BlockPos(x,y,z);
        };
    }
}
