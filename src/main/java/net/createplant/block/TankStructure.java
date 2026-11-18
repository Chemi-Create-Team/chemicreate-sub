package net.createplant.block;

import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Recognizes an open-top cylindrical tank built only from tank casing blocks.
 *
 * The floor and wall use the same symmetric pixel-circle footprint. The interior must be air and the layer
 * above the wall must hold no casing, so pipes and pumps may rest on the rim. Searches never load chunks; an
 * unloaded part reports "unloaded" instead of forming.
 */
public final class TankStructure {
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_HEIGHT = 64;

    private TankStructure() { }

    public record Shape(BlockPos origin, int diameter, int height) {
        public BlockPos center() {
            return origin.offset((diameter - 1) / 2, 0, (diameter - 1) / 2);
        }

        /** Interior cells plus every floor and wall casing: the full cylinder including the floor layer. */
        public int volumeBlocks() {
            return TankFootprint.blocks(diameter) * (height + 1);
        }
    }

    /** Either a recognized shape or the translation suffix of the reason it did not form. */
    public record Result(Shape shape, String failure) {
        static Result failed(String failure) {
            return new Result(null, failure);
        }
    }

    /** Finds the tank that contains a floor or wall casing at {@code start}. */
    public static Result find(Level level, BlockPos start) {
        if (!isCasing(level, start))
            return Result.failed("floor");

        int floorY = start.getY();
        for (int scanned = 0; isCasing(level, new BlockPos(start.getX(), floorY - 1, start.getZ())); scanned++) {
            if (scanned > MAX_HEIGHT)
                return Result.failed("height");
            floorY--;
        }

        BlockPos wall = findWallColumn(level, new BlockPos(start.getX(), floorY, start.getZ()));
        if (wall == null)
            return Result.failed("wall");

        // Every candidate circle whose outer ring passes through the wall column is checked once.
        Result first = null;
        for (int diameter = TankFootprint.MIN_DIAMETER; diameter <= TankFootprint.MAX_DIAMETER; diameter++) {
            for (int x = 0; x < diameter; x++) {
                for (int z = 0; z < diameter; z++) {
                    if (!TankFootprint.isWall(x, z, diameter))
                        continue;
                    Result result = inspect(level, wall.offset(-x, 0, -z), diameter);
                    if (result.shape() != null)
                        return result;
                    if (first == null && !result.failure().equals("floor"))
                        first = result;
                }
            }
        }
        return first != null ? first : Result.failed("floor");
    }

    /** Checks one candidate whose floor footprint starts at {@code origin}. */
    public static Result inspect(Level level, BlockPos origin, int diameter) {
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                BlockPos at = origin.offset(x, 0, z);
                if (!isReadable(level, at))
                    return Result.failed("unloaded");
                boolean casing = level.getBlockState(at).is(CEBlocks.TANK_CASING.get());
                // Casings in the square corners outside the circle reject square floors.
                if (casing != TankFootprint.contains(x, z, diameter))
                    return Result.failed("floor");
            }
        }

        // The first layer without any casing in the wall ring is the open top; pipes may rest on the rim.
        int height = 0;
        for (int dy = 1; dy <= MAX_HEIGHT + 1; dy++) {
            int ringCells = 0;
            int ringCasings = 0;
            boolean interiorAir = true;
            for (int x = 0; x < diameter; x++) {
                for (int z = 0; z < diameter; z++) {
                    if (!TankFootprint.contains(x, z, diameter))
                        continue;
                    BlockPos at = origin.offset(x, dy, z);
                    if (!isReadable(level, at))
                        return Result.failed("unloaded");
                    BlockState state = level.getBlockState(at);
                    if (TankFootprint.isWall(x, z, diameter)) {
                        ringCells++;
                        if (state.is(CEBlocks.TANK_CASING.get()))
                            ringCasings++;
                    } else if (!state.isAir()) {
                        interiorAir = false;
                    }
                }
            }

            if (ringCasings == 0) {
                return height >= MIN_HEIGHT ? new Result(new Shape(origin, diameter, height), "")
                    : Result.failed("height");
            }
            if (ringCasings < ringCells)
                return Result.failed("wall");
            if (!interiorAir)
                return Result.failed("interior");
            if (dy > MAX_HEIGHT)
                return Result.failed("height");
            height++;
        }
        return Result.failed("height");
    }

    /** A floor cell with casing above is part of the wall; interior cells walk west to the wall. */
    private static BlockPos findWallColumn(Level level, BlockPos floor) {
        BlockPos at = floor;
        for (int step = 0; step < TankFootprint.MAX_DIAMETER; step++) {
            if (!isCasing(level, at))
                return null;
            if (isCasing(level, at.above()))
                return at;
            at = at.west();
        }
        return null;
    }

    private static boolean isCasing(Level level, BlockPos pos) {
        return isReadable(level, pos) && level.getBlockState(pos).is(CEBlocks.TANK_CASING.get());
    }

    private static boolean isReadable(Level level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight()
            && pos.getY() < level.getMaxBuildHeight()
            && level.hasChunkAt(pos);
    }
}
