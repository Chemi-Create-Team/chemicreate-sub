package net.createplant.block;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MaterialPumpBlock extends PumpBlock {
    public MaterialPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CETransport.MATERIAL_PUMP_ENTITY.get();
    }
}
