package net.createplant.block;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MaterialPumpBlock extends PumpBlock {
    private final float soundPitch;

    public MaterialPumpBlock(Properties properties, float soundPitch) {
        super(properties);
        this.soundPitch = soundPitch;
    }

    public float soundPitch() { return soundPitch; }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CETransport.MATERIAL_PUMP_ENTITY.get();
    }
}
