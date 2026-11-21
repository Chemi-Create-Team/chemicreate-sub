package net.createplant.block;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.transport.PipeMaterial;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MaterialPumpBlock extends PumpBlock {
    private final float soundPitch;
    private final PipeMaterial material;

    public MaterialPumpBlock(Properties properties, float soundPitch, PipeMaterial material) {
        super(properties);
        this.soundPitch = soundPitch;
        this.material = material;
    }

    public float soundPitch() { return soundPitch; }
    /** The pipe this pump is made from, whose ratings it shares. */
    public PipeMaterial material() { return material; }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CETransport.MATERIAL_PUMP_ENTITY.get();
    }
}
