package net.createplant.block.entity;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import net.createmod.catnip.data.Iterate;
import net.createplant.block.TankCells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Create boiler rules for a tank lying on its side: the burners are counted under the whole lowest layer, and engines and whistles are
 * counted around every cell. Everything else (size limits, water supply, engine efficiency) is Create's own.
 */
public class HorizontalBoilerData extends BoilerData {
    @Override public boolean evaluate(FluidTankBlockEntity tank) {
        var level = tank.getLevel();
        int engines = attachedEngines, whistles = attachedWhistles;
        attachedEngines = 0;
        attachedWhistles = 0;
        TankCells.forEach(tank, cell -> {
            if (!FluidTankBlock.isTank(level.getBlockState(cell))) return;
            for (Direction direction : Iterate.directions) {
                var state = level.getBlockState(cell.relative(direction));
                if (AllBlocks.STEAM_ENGINE.has(state) && SteamEngineBlock.getFacing(state) == direction) attachedEngines++;
                if (AllBlocks.STEAM_WHISTLE.has(state) && WhistleBlock.getAttachedDirection(state).getOpposite() == direction) attachedWhistles++;
            }
        });
        needsHeatLevelUpdate = true;
        return engines != attachedEngines || whistles != attachedWhistles;
    }

    @Override public boolean updateTemperature(FluidTankBlockEntity tank) {
        var level = tank.getLevel();
        needsHeatLevelUpdate = false;
        boolean previousPassive = passiveHeat;
        int previousActive = activeHeat;
        passiveHeat = false;
        activeHeat = 0;
        TankCells.forEachBase(tank, cell -> {
            BlockPos under = cell.below();
            float heat = BoilerHeater.findHeat(level, under, level.getBlockState(under));
            if (heat == BoilerHeater.PASSIVE_HEAT) passiveHeat = true;
            else if (heat > 0) activeHeat = (int) (activeHeat + heat);
        });
        passiveHeat &= activeHeat == 0;
        return previousActive != activeHeat || previousPassive != passiveHeat;
    }

    @Override public void updateOcclusion(FluidTankBlockEntity tank) { }
}
