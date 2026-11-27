package net.createplant.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import net.createplant.CreatePlant;
import net.createplant.mixin.FluidTankModelInvoker;
import net.createplant.registry.CETransport;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Connected textures for the plant tanks, applied through Create's tank model. */
public final class PlantTankModels {
    private static final CTSpriteShiftEntry[] SEPARATOR_TANK=shifts("ammonia_separator"), PRESSURE=shifts("pressure_tank"), REACTION=shifts("reaction_tower"),
        STAINLESS=shifts("stainless_steel_tank"), CRYOGENIC=shifts("cryogenic_tank"), GAS_HOLDER=shifts("gas_holder");
    /** The stirred tank's side: the stainless tank's middle panel stretched across the whole face. */
    private static final CTSpriteShiftEntry STIRRED_SIDE=shift("stirred_tank");
    /** The iron-grey Create tank sheet is shared by these steel pressure-vessel end plates. */
    static final CTSpriteShiftEntry VESSEL_CAP=CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,CreatePlant.location("block/tank/ammonia_separator_top"),CreatePlant.location("block/lidded_vessel/cap_connected"));
    /** The tube sheet on the ends of the shell-and-tube exchangers' tanks; their sides are Create's own tank side. */
    private static final CTSpriteShiftEntry TUBE_SHEET=CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,CreatePlant.location("block/tube_piped_tank/tube_sheet"),CreatePlant.location("block/tube_piped_tank/tube_sheet_connected"));
    /** The cover a formed exchanger shows on its ends instead of the bare tube sheet. */
    private static final CTSpriteShiftEntry CHANNEL_HEAD=CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,CreatePlant.location("block/tube_piped_tank/channel_head"),CreatePlant.location("block/tube_piped_tank/channel_head_connected"));
    private PlantTankModels() { }
    /** Sprite shifts must be registered before the block atlas is stitched. */
    public static void init() { }
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        horizontal(event,CETransport.HORIZONTAL_FLUID_TANK.get(),com.simibubi.create.AllSpriteShifts.FLUID_TANK,com.simibubi.create.AllSpriteShifts.FLUID_TANK_TOP,com.simibubi.create.AllSpriteShifts.FLUID_TANK_INNER);
        horizontal(event,CETransport.STAINLESS_BOILER.get(),SEPARATOR_TANK[0],SEPARATOR_TANK[1],SEPARATOR_TANK[2]);
        for (Block block:new Block[]{net.createplant.registry.CEHeating.TUBE_TANK.get(),net.createplant.registry.CEHeating.EXCHANGER_CONTROLLER.get(),net.createplant.registry.CEHeating.LARGE_EXCHANGER_CONTROLLER.get()})
            for (var state:block.getStateDefinition().getPossibleStates())
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (location,model)->new com.simibubi.create.foundation.block.connected.CTModel(model,new TubeTankCTBehaviour(com.simibubi.create.AllSpriteShifts.FLUID_TANK,TUBE_SHEET,CHANNEL_HEAD)));
        wrap(event,CETransport.PRESSURE_TANK.get(),PRESSURE);
        wrap(event,CETransport.REACTION_TOWER.get(),REACTION);
        wrap(event,CETransport.STAINLESS_STEEL_TANK.get(),STAINLESS);
        wrap(event,CETransport.CRYOGENIC_TANK.get(),CRYOGENIC);
        wrap(event,net.createplant.registry.CEGasBell.TANK.get(),GAS_HOLDER);
        for (var state:CETransport.LIDDED_VESSEL.get().getStateDefinition().getPossibleStates())
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),(location,model)->new com.simibubi.create.foundation.block.connected.CTModel(new LiddedVesselWindowModel(model),new LiddedVesselCTBehaviour(SEPARATOR_TANK[0],VESSEL_CAP)));
        for (var state:CETransport.STIRRED_TANK.get().getStateDefinition().getPossibleStates())
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),(location,model)->new com.simibubi.create.foundation.block.connected.CTModel(new LiddedVesselWindowModel(model),new LiddedVesselCTBehaviour(STIRRED_SIDE,VESSEL_CAP)));
    }
    private static void horizontal(ModelEvent.ModifyBakingResult event,Block block,CTSpriteShiftEntry side,CTSpriteShiftEntry top,CTSpriteShiftEntry inner) {
        for (var state:block.getStateDefinition().getPossibleStates())
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                (location,model)->new com.simibubi.create.foundation.block.connected.CTModel(model,new HorizontalTankCTBehaviour(side,top,inner)));
    }
    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, CTSpriteShiftEntry[] shifts) {
        for (var state:block.getStateDefinition().getPossibleStates())
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                (location,model)->FluidTankModelInvoker.createPlant$create(model,shifts[0],shifts[1],shifts[2]));
    }
    private static CTSpriteShiftEntry[] shifts(String tank) {
        return new CTSpriteShiftEntry[]{shift(tank),shift(tank+"_top"),shift(tank+"_inner")};
    }
    private static CTSpriteShiftEntry shift(String name) {
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,CreatePlant.location("block/tank/"+name),CreatePlant.location("block/tank/"+name+"_connected"));
    }
}
