package net.createplant.block;

import java.util.*;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A 3x3 stainless tank two to four layers high with a roof-mounted stirrer runs two parallel stirred batches. The hatch closes the pressure envelope. The northwest bottom member owns the process inventory.
 */
public final class ReactorStructure {
    public static final int MAX_SECTIONS=4;
    public record Result(int sections,List<BlockPos> members,BlockPos fault,String reason) { }
    private ReactorStructure() { }
    public static boolean loaded(Level level,BlockPos base) {
        if (level == null || !level.hasChunkAt(base) || !(level.getBlockEntity(base) instanceof LiddedVesselBlockEntity vessel)) return false;
        for (int x : new int[]{0,vessel.getWidth()-1}) for (int z : new int[]{0,vessel.getWidth()-1}) if (!level.hasChunkAt(base.offset(x,0,z))) return false;
        return true;
    }
    public static Result scan(Level level,BlockPos base,ReactorModule module) {
        List<BlockPos> members=new ArrayList<>();
        if (!loaded(level,base)) return new Result(0,members,base,"unloaded");
        if (!(level.getBlockEntity(base) instanceof LiddedVesselBlockEntity vessel) || !vessel.isController() || module==ReactorModule.NONE
            || vessel.getWidth()!=3) return new Result(0,members,base,"vessel");
        BlockPos origin=vessel.getBlockPos();int width=vessel.getWidth(),layers=vessel.getHeight();
        for (int y=0;y<layers;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) {
            BlockPos at=origin.offset(x,y,z);
            if (!(level.getBlockEntity(at) instanceof LiddedVesselBlockEntity part) || !part.getController().equals(origin)) return new Result(0,members,at,"vessel");
        }
        for (int y=0;y<layers;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) members.add(origin.offset(x,y,z));
        return new Result(LiddedVesselBlockEntity.IMPELLER_LAYERS,List.copyOf(members),base,"ready");
    }
    /** Whether the block at a place of the envelope is a lidded vessel; the build guide uses this. */
    public static boolean matches(Level level,BlockPos pos) { return level.getBlockState(pos).getBlock() instanceof LiddedVesselBlock; }
    public static void release(Level level,List<BlockPos> members) {
        for (BlockPos pos:members) {
            if (!level.hasChunkAt(pos)) continue;
            var state=level.getBlockState(pos);
            if (state.hasProperty(ThermalCasingBlock.FORMED) && state.getValue(ThermalCasingBlock.FORMED)) level.setBlock(pos,state.setValue(ThermalCasingBlock.FORMED,false),Block.UPDATE_CLIENTS);
        }
    }
    /** Parts notify their vessel controller; horizontal equipment keeps its existing nearby-controller lookup. */
    public static void changed(Level level,BlockPos pos) {
        if (level.isClientSide()) return;
        // Vessel members resolve their own controller; horizontal heater controllers stand within six blocks of their parts.
        for (int y=-6;y<=6;y++) for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) {
            BlockPos at=pos.offset(x,y,z);
            if (!level.hasChunkAt(at)) continue;
            if (level.getBlockEntity(at) instanceof LiddedVesselBlockEntity part) {
                var owner=part.getControllerBE();if (owner!=null) owner.invalidateStructure();
            } else if (level.getBlockEntity(at) instanceof ReactorMachineBlockEntity be) be.invalidateStructure();
        }
    }
}
