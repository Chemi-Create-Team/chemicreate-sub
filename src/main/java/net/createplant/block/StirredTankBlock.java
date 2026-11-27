package net.createplant.block;

import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** A stainless vessel built to be stirred: it forms and behaves like {@link LiddedVesselBlock}, and only it runs the stirred process under a roof stirrer. */
public class StirredTankBlock extends LiddedVesselBlock {
    public StirredTankBlock(Properties properties) { super(properties); }

    @Override public BlockEntityType<? extends LiddedVesselBlockEntity> getBlockEntityType() { return CETransport.STIRRED_TANK_ENTITY.get(); }
}
