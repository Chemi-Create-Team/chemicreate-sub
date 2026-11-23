package net.createplant.client;

import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.gametest.ThermalControlGameTests;
import net.createplant.registry.CEGasifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The front of a playtest plant that makes clean syngas from felled wood: logs in a chest are baked to charcoal on a belt in the blast of a row of
 * fans past lit blaze burners and drop through a chute into the gasifier roof; a Create boiler sends steam to the gasifier; the wet water gas is
 * cooled in a water-cooled exchanger, washed in an absorption tower and split into carbon monoxide and hydrogen in a fractionating column.
 * Every position is relative to {@code origin}, the gasifier's bottom centre on the ground.
 */
final class SyngasTrain {
    static final String SPLIT = "separate_purified_syngas";
    private static final int BELT_RPM = 8;
    private final PlaytestPlant plant;
    private final ServerLevel level;
    private final int ground;
    final BlockPos gasifier, roof, boiler, water, exchanger, washer, splitter, beltStart;
    private BlockPos coolant;

    SyngasTrain(PlaytestPlant plant, ServerLevel level, BlockPos origin) {
        this.plant = plant;
        this.level = level;
        this.ground = origin.getY() - 1;
        gasifier = origin;
        roof = gasifier.above(5);
        boiler = origin.offset(-7, 0, -1);
        water = origin.offset(-10, 0, 0);
        exchanger = origin.offset(3, -1, -6);
        washer = origin.offset(11, 1, -10);
        splitter = origin.offset(-2, -1, -10);
        beltStart = new BlockPos(origin.getX() + 9, roof.getY() + 1, roof.getZ() + 1);
    }

    /** Builds the train and returns the column's cuts. */
    List<PlaytestPlant.Cut> build() {
        gasifier();
        charcoal();
        steam();
        var column = plant.column(splitter, SPLIT);
        // The wet water gas from the front of the gasifier roof through the tubes of a water-cooled exchanger, where its steam condenses.
        var cooler = ThermalControlGameTests.build(level, exchanger, Direction.NORTH, 3);
        var along = cooler.getBlockState().getValue(net.createplant.block.ThermalMachineBlock.FACING);
        var members = cooler.members();
        int cells = cooler.exchanger().width * cooler.exchanger().width, top = cooler.exchanger().width - 1;
        BlockPos tubeIn = members.getFirst().relative(along.getOpposite()), tubeOut = members.get(members.size() - cells).relative(along);
        BlockPos shellIn = members.get(members.size() - cells + top).above(), shellOut = members.get(top).above();
        BlockPos outlet = roof.north();
        int gasRack = outlet.getY() + 3;
        plant.route(Direction.UP, outlet.above(), List.of(outlet.above(3), new BlockPos(outlet.getX(), gasRack, tubeIn.getZ()),
            new BlockPos(outlet.getX(), tubeIn.getY(), tubeIn.getZ()), tubeIn), along);
        coolant = shellIn.above(3);
        plant.put(coolant, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.DOWN, shellIn.above(2), List.of(shellIn), Direction.DOWN);
        var warm = plant.route(Direction.UP, shellOut, List.of(shellOut.above(2), shellOut.above(2).south(3), shellOut.south(3).atY(ground + 1)), Direction.SOUTH);
        plant.store(warm.getLast(), Direction.SOUTH, "冷却水（蒸気）", PlaytestPlant.Kind.GAS);
        // The cooled stream is washed in an absorption tower: the condensate leaves its underside, the clean syngas its top storey.
        AbsorptionTowerGameTests.tower(level, washer, 4, true);
        BlockPos washIn = washer.west(2), washTop = washer.above(3).north(2), washUnder = washer.below();
        BlockPos clear = tubeOut.relative(along, 2);
        plant.route(along, tubeOut, List.of(clear, clear.atY(washIn.getY()), new BlockPos(clear.getX(), washIn.getY(), washIn.getZ()), washIn), Direction.EAST);
        BlockPos inlet = plant.inlet(column, SPLIT);
        int cooledRack = ground + 8;
        plant.route(Direction.NORTH, washTop, List.of(washTop.north(), washTop.north().atY(cooledRack), new BlockPos(washTop.getX(), cooledRack, inlet.getZ()),
            new BlockPos(inlet.getX(), cooledRack, inlet.getZ()), inlet), Direction.WEST);
        var drain = plant.route(Direction.DOWN, washUnder, List.of(washUnder.below(), washUnder.below().east(3)), Direction.EAST);
        plant.store(drain.getLast(), Direction.EAST, "洗浄塔の排水", PlaytestPlant.Kind.ANY);
        plant.sign(beltStart.offset(0, 0, 3), "原木→木炭");
        plant.sign(gasifier.offset(-2, -1, 2), "ガス化炉");
        plant.sign(boiler.offset(0, -1, 3), "ボイラー（蒸気）");
        plant.sign(exchanger.offset(0, 0, 3), "冷却器");
        plant.sign(washer.offset(0, -2, 3), "洗浄塔");
        plant.sign(splitter.offset(-1, 0, 2), "水性ガス分留");
        return plant.cuts(column, SPLIT);
    }

    /** Fills the creative water supplies and lights the gasifier, as a player starting the plant would. */
    void start() {
        plant.supply(water, new FluidStack(Fluids.WATER, 1_000));
        plant.supply(coolant, new FluidStack(Fluids.WATER, 1_000));
        gasifierEntity().rebuild();
    }

    GasifierBlockEntity gasifierEntity() { return (GasifierBlockEntity) level.getBlockEntity(gasifier); }

    /** Whether the drive at {@code pos} turns backwards: the charcoal belt must carry toward the gasifier, every pump toward its front. */
    boolean wrongWay(BlockPos pos, float speed) {
        if (level.getBlockState(pos).getBlock() instanceof com.simibubi.create.content.kinetics.belt.BeltBlock)
            return com.simibubi.create.content.kinetics.belt.BeltHelper.getSegmentBE(level, pos) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt
                && belt.getMovementFacing() != Direction.WEST;
        return speed < 0;
    }

    private void gasifier() {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            plant.put(gasifier.offset(x, y, z), CEGasifier.GASIFIER.get().defaultBlockState());
        plant.burner(gasifier.below(), BlazeBurnerBlock.HeatLevel.KINDLED);
        PlaytestPlant.require(gasifierEntity().rebuild(), "The gasifier forms");
    }

    /** Felled logs in a chest drop through a hopper onto a belt, which carries them through a fan's blast past a lit blaze burner and into a chute on the roof. */
    private void charcoal() {
        int z = beltStart.getZ();
        BlockPos chute = new BlockPos(roof.getX() + 1, beltStart.getY(), z), end = chute.east();
        plant.put(chute, AllBlocks.CHUTE.getDefaultState());
        plant.put(beltStart, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        plant.put(end, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        BeltConnectorItem.createBelts(level, beltStart, end);
        // A slow belt keeps each log long enough in the blast to bake through.
        plant.motor(beltStart.north(), Direction.SOUTH, BELT_RPM);
        plant.DRIVES.add(new BlockPos[] {beltStart, beltStart.north()});
        // A row of fans blows south across the belt, each through a lit burner.
        for (int x = end.getX() + 1; x < beltStart.getX(); x++) {
            BlockPos fanAt = new BlockPos(x, beltStart.getY(), z - 2);
            plant.put(fanAt, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            plant.burner(fanAt.south(), BlazeBurnerBlock.HeatLevel.KINDLED);
            plant.motor(fanAt.north(), Direction.SOUTH);
        }
        plant.put(beltStart.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        plant.put(beltStart.above(2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        var chest = (ChestBlockEntity) level.getBlockEntity(beltStart.above(2));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
    }

    /** A 2x2 Create tank on four burners is the boiler; a pump fills it from the water supply and one on its roof sends steam to the gasifier's west inlet. */
    private void steam() {
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            plant.put(boiler.offset(x, 0, z), AllBlocks.FLUID_TANK.getDefaultState());
            plant.burner(boiler.offset(x, -1, z), BlazeBurnerBlock.HeatLevel.KINDLED);
        }
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti((FluidTankBlockEntity) level.getBlockEntity(boiler));
        plant.put(water, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.EAST, water.east(), List.of(water.east().above(2), new BlockPos(boiler.getX(), water.getY() + 2, water.getZ()),
            new BlockPos(boiler.getX(), boiler.getY() + 1, water.getZ())), Direction.DOWN);
        BlockPos boilerRoof = boiler.offset(1, 1, 0), inlet = gasifier.west(2);
        int z = gasifier.getZ() - 3;
        plant.route(Direction.UP, boilerRoof, List.of(boilerRoof.above(2), new BlockPos(boilerRoof.getX(), boilerRoof.getY() + 2, z), new BlockPos(inlet.getX(), boilerRoof.getY() + 2, z),
            new BlockPos(inlet.getX(), boilerRoof.getY() + 2, inlet.getZ()), inlet), Direction.EAST);
    }
}
