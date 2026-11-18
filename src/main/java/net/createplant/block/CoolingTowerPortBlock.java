package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CoolingTowerPortBlockEntity;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class CoolingTowerPortBlock extends Block implements IBE<CoolingTowerPortBlockEntity> {
    public final int tank;
    public CoolingTowerPortBlock(Properties properties, int tank) { super(properties);this.tank = tank; }
    @Override public Class<CoolingTowerPortBlockEntity> getBlockEntityClass() { return CoolingTowerPortBlockEntity.class; }
    @Override public BlockEntityType<? extends CoolingTowerPortBlockEntity> getBlockEntityType() { return CEPlantSystems.TOWER_PORT_ENTITY.get(); }
}
