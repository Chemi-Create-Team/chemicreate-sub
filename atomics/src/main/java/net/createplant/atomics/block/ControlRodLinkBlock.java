package net.createplant.atomics.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.ControlRodLinkBlockEntity;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Sends an adjacent 100-step controller's rod-depth command over Create-style two-item frequencies. */
public class ControlRodLinkBlock extends Block implements IBE<ControlRodLinkBlockEntity> {
    public ControlRodLinkBlock(Properties properties) { super(properties); }

    @Override public Class<ControlRodLinkBlockEntity> getBlockEntityClass() { return ControlRodLinkBlockEntity.class; }
    @Override public BlockEntityType<? extends ControlRodLinkBlockEntity> getBlockEntityType() { return CENuclear.ROD_LINK_ENTITY.get(); }
}
