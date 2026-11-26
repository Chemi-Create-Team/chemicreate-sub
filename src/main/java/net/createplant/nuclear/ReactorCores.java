package net.createplant.nuclear;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Change notices for reactor cores on the server. Graphite has no block entity, so a changed core block marks the formed
 * core whose box it touches, and bumps a counter that unformed channels compare before they try to form a core again.
 */
public final class ReactorCores {
    private static final Map<Level, Map<BlockPos, BoundingBox>> FORMED = new WeakHashMap<>();
    private static final Map<Level, Long> VERSIONS = new WeakHashMap<>();
    private static final Map<Level, java.util.List<Explosion>> EXPLOSIONS = new WeakHashMap<>();
    private static final int[][] SECONDARY_DIRECTIONS = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    /** A core explosion waiting its moment after the steam explosion. */
    private static final class Explosion {
        final BoundingBox box;
        final float strength;
        final boolean chernobyl;
        int ticks = ReactorPhysics.CORE_EXPLOSION_DELAY;
        int stage;

        Explosion(BoundingBox box, float strength, boolean chernobyl) {
            this.box = box;
            this.strength = strength;
            this.chernobyl = chernobyl;
        }
    }

    private ReactorCores() { }

    public static void scheduleCoreExplosion(Level level, BoundingBox box, float strength) {
        contaminate(level, box);
        EXPLOSIONS.computeIfAbsent(level, l -> new java.util.ArrayList<>()).add(new Explosion(box, strength, false));
    }

    /** Schedules the main blast and the expanding ring of secondary bursts for the exceptional AZ-5 accident. */
    public static void scheduleChernobylExplosion(Level level, BoundingBox box, float strength) {
        contaminate(level, box);
        EXPLOSIONS.computeIfAbsent(level, l -> new java.util.ArrayList<>()).add(new Explosion(box, strength, true));
    }

    private static void contaminate(Level level, BoundingBox box) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        for (int x = box.minX() - 8; x <= box.maxX() + 8; x += 4) for (int z = box.minZ() - 8; z <= box.maxZ() + 8; z += 4) {
            var pos = new BlockPos(x, box.minY(), z);
            if (level.hasChunkAt(pos)) RadiationContamination.deposit(server, pos, new Radionuclides(8e11, 1.99e11, 1e9, RadiationSources.now(level)));
        }
    }

    /**
     * Tears open the upper pressure-tube stack and most of its two-block shield before the exceptional steam blast. The vault
     * normally has enough blast resistance to contain any ordinary accident; this explicit breach represents Elena and the
     * upper reactor structure being lifted by the AZ-5 excursion, while leaving a jagged part of the side shield standing.
     *
     * @return number of reactor-vault blocks removed
     */
    public static int breachUpperStructure(Level level, BoundingBox box) {
        int removed = 0, shell = ReactorCoreStructure.SHIELD_THICKNESS;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - shell; x <= box.maxX() + shell; x++)
            for (int z = box.minZ() - shell; z <= box.maxZ() + shell; z++)
                for (int y = box.maxY() + 1; y <= box.maxY() + ReactorPhysics.layout(box.getYSpan()).capOffset(); y++) {
                    var state = level.getBlockState(pos.set(x, y, z));
                    boolean tube = state.is(net.createplant.registry.CENuclear.FUEL_CHANNEL_TUBE.get())
                        || state.is(net.createplant.registry.CENuclear.TOP_CAP.get());
                    boolean shield = state.is(net.createplant.registry.CENuclear.BIOLOGICAL_SHIELD.get())
                        || state.is(net.createplant.registry.CENuclear.SHIELD_DRIVE.get())
                        || state.is(net.createplant.registry.CENuclear.SHIELD_OUTLET.get());
                    if (!tube && (!shield || Math.floorMod(x * 31 + y * 13 + z * 17, 5) == 0)) continue;
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS);
                    removed++;
                }
        return removed;
    }

    /** Sets off the core explosions that are due, then lights the graphite left standing in the wreck. */
    public static void levelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        Level level = event.getLevel();
        var pending = EXPLOSIONS.get(level);
        if (level.isClientSide() || pending == null || pending.isEmpty()) return;
        for (var iterator = pending.iterator(); iterator.hasNext(); ) {
            var explosion = iterator.next();
            if (--explosion.ticks > 0) continue;
            var centre = explosion.box.getCenter();
            if (!explosion.chernobyl) {
                iterator.remove();
                level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, explosion.strength, Level.ExplosionInteraction.BLOCK);
                ignite(level, explosion.box, explosion.strength);
                continue;
            }
            if (explosion.stage == 0) {
                level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, explosion.strength, Level.ExplosionInteraction.BLOCK);
                explosion.stage = 1;
                explosion.ticks = ReactorPhysics.CHERNOBYL_SECONDARY_INTERVAL;
                continue;
            }
            int burst = explosion.stage - 1;
            int[] direction = SECONDARY_DIRECTIONS[burst % SECONDARY_DIRECTIONS.length];
            double length = Math.hypot(direction[0], direction[1]);
            double ring = Math.max(explosion.box.getXSpan(), explosion.box.getZSpan()) * 0.8 + explosion.strength * 0.35;
            double x = centre.getX() + 0.5 + direction[0] / length * ring;
            double z = centre.getZ() + 0.5 + direction[1] / length * ring;
            double y = explosion.box.minY() + 1.5 + burst % 3 * 2;
            level.explode(null, x, y, z, ReactorPhysics.chernobylSecondaryExplosion(explosion.strength), Level.ExplosionInteraction.BLOCK);
            explosion.stage++;
            if (burst + 1 >= ReactorPhysics.CHERNOBYL_SECONDARY_BLASTS) {
                iterator.remove();
                ignite(level, explosion.box, explosion.strength);
            } else explosion.ticks = ReactorPhysics.CHERNOBYL_SECONDARY_INTERVAL;
        }
    }

    /**
     * Fire on the graphite the explosion left standing, and on burning graphite thrown out of the core onto the ground around
     * it; graphite keeps such a fire burning, as netherrack does.
     */
    private static void ignite(Level level, BoundingBox box, float strength) {
        var pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - 2; x <= box.maxX() + 2; x++) for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
            if (level.random.nextInt(3) == 0) continue;
            for (int y = box.maxY() + 3; y >= box.minY() - 1; y--) {
                var state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir()) continue;
                if (burns(state)) burn(level, pos.above());
                break;
            }
        }
        int debris = Math.clamp(box.getXSpan() * box.getZSpan() * box.getYSpan() / 6, 4, 24), reach = (int) strength + 3;
        var centre = box.getCenter();
        for (int i = 0; i < debris; i++) {
            int x = centre.getX() + level.random.nextInt(2 * reach + 1) - reach, z = centre.getZ() + level.random.nextInt(2 * reach + 1) - reach;
            for (int y = box.maxY() + 8; y >= box.minY() - 8; y--) {
                var state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir() || !state.getFluidState().isEmpty() && !state.isSolid()) continue;
                if (!state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) break;
                level.setBlock(pos.above(), net.createplant.registry.CENuclear.GRAPHITE.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                burn(level, pos.above(2));
                break;
            }
        }
    }

    private static boolean burns(net.minecraft.world.level.block.state.BlockState state) {
        var kind = ReactorCoreStructure.kind(state.getBlock());
        return kind == ReactorCoreStructure.Kind.GRAPHITE || kind == ReactorCoreStructure.Kind.FUEL;
    }

    private static void burn(Level level, BlockPos pos) {
        level.setBlock(pos, net.minecraft.world.level.block.BaseFireBlock.getState(level, pos), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    public static long version(Level level) { return VERSIONS.getOrDefault(level, 0L); }

    /** Something a core reads at a distance, such as its feed bundles, changed; anything cached against the version is stale. */
    public static void touched(Level level) { if (!level.isClientSide()) VERSIONS.merge(level, 1L, Long::sum); }

    /**
     * The controller of the core a part of its vault belongs to, on either side: straight down from the part, or from the block
     * beside it towards the core, until a channel of the core. Its goggle readout stands for the whole reactor.
     */
    public static BlockPos controllerUnder(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[] {null, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
            net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
            int reach = side == null ? 0 : ReactorCoreStructure.SHIELD_THICKNESS;
            for (int offset = 0; offset <= reach; offset++) {
                BlockPos at = side == null ? pos : pos.relative(side, offset);
                if (level instanceof Level world && !world.hasChunkAt(at)) continue;
                for (int i = 0; i < ReactorCoreStructure.HEIGHT + ReactorPhysics.CAP_OFFSET + ReactorPhysics.LOWER_STRUCTURE_HEIGHT + 3; i++, at = at.below()) {
                    var be = level.getBlockEntity(at);
                    if (be instanceof net.createplant.block.entity.FuelChannelBlockEntity channel && channel.controllerPos() != null) return channel.controllerPos();
                }
            }
        }
        return pos;
    }

    /** Controller of the formed core whose box is closest to pos, within range blocks; null if none. */
    public static BlockPos nearest(Level level, BlockPos pos, int range) {
        var cores = FORMED.get(level);
        if (cores == null) return null;
        BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (var entry : cores.entrySet()) {
            BoundingBox box = entry.getValue();
            double dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
            double dy = Math.max(0, Math.max(box.minY() - pos.getY(), pos.getY() - box.maxY()));
            double dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= range * range && d < bestDistance) { bestDistance = d; best = entry.getKey(); }
        }
        return best;
    }

    public static void formed(Level level, BlockPos controller, BoundingBox box) {
        FORMED.computeIfAbsent(level, l -> new HashMap<>()).put(controller, box);
    }

    public static void released(Level level, BlockPos controller) {
        var cores = FORMED.get(level);
        if (cores != null) cores.remove(controller);
    }

    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        VERSIONS.merge(level, 1L, Long::sum);
        var cores = FORMED.get(level);
        if (cores == null) return;
        for (var entry : Map.copyOf(cores).entrySet()) {
            if (!entry.getValue().inflatedBy(1).isInside(pos)) continue;
            if (level.getBlockEntity(entry.getKey()) instanceof FuelChannelBlockEntity controller) controller.coreChanged();
            else cores.remove(entry.getKey());
        }
    }

    /** Controllers of the formed cores whose footprint overlaps the given horizontal box. */
    public static java.util.List<BlockPos> controllersIn(Level level, int minX, int minZ, int maxX, int maxZ) {
        var cores = FORMED.get(level);
        if (cores == null) return java.util.List.of();
        var found = new java.util.ArrayList<BlockPos>();
        for (var entry : cores.entrySet()) {
            var box = entry.getValue();
            if (box.maxX() >= minX && box.minX() <= maxX && box.maxZ() >= minZ && box.minZ() <= maxZ) found.add(entry.getKey());
        }
        return found;
    }

    /** The one loaded formed core whose full shield-and-service envelope contains this reactor part. */
    public static BlockPos controllerForPart(Level level, BlockPos pos) {
        var cores = FORMED.get(level);
        if (cores == null) return null;
        BlockPos found = null;
        for (var entry : Map.copyOf(cores).entrySet()) {
            var box = entry.getValue();
            if (pos.getX() < box.minX() - ReactorCoreStructure.SHIELD_THICKNESS
                || pos.getX() > box.maxX() + ReactorCoreStructure.SHIELD_THICKNESS
                || pos.getZ() < box.minZ() - ReactorCoreStructure.SHIELD_THICKNESS
                || pos.getZ() > box.maxZ() + ReactorCoreStructure.SHIELD_THICKNESS
                || pos.getY() < box.minY() - ReactorPhysics.layout(box.getYSpan()).lowerStructureHeight() - 1
                || pos.getY() > box.maxY() + ReactorPhysics.layout(box.getYSpan()).capOffset()) continue;
            BlockPos controller = entry.getKey();
            if (!level.hasChunkAt(controller)) continue;
            if (!(level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead)
                || lead.core() == null || !lead.core().formed()) {
                cores.remove(controller);
                continue;
            }
            if (found != null && !found.equals(controller)) return null;
            found = controller;
        }
        return found;
    }

    /** A redstone change at a channel of a formed core; the controller then reads every channel's signal again. */
    public static void signalChanged(Level level, BlockPos pos) {
        var cores = FORMED.get(level);
        if (cores == null) return;
        for (var entry : cores.entrySet())
            if (entry.getValue().isInside(pos) && level.getBlockEntity(entry.getKey()) instanceof FuelChannelBlockEntity controller) controller.signalChanged();
    }
}
