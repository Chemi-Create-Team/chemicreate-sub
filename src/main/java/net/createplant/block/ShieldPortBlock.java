package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ShieldPortBlockEntity;
import net.createplant.registry.CENuclear;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The coolant outlet set into the biological shield of an RBMK vault. It emits the core's wet water/steam stream without
 * separating its phases. Like a shield block it stops radiation.
 */
public class ShieldPortBlock extends Block implements IBE<ShieldPortBlockEntity>, com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    public ShieldPortBlock(Properties properties) { super(properties); }

    /** Goggles on any part of the vault read the whole reactor. */
    @Override public net.minecraft.core.BlockPos getInformationSource(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return net.createplant.nuclear.ReactorCores.controllerUnder(level, pos);
    }

    @Override public Class<ShieldPortBlockEntity> getBlockEntityClass() { return ShieldPortBlockEntity.class; }
    @Override public BlockEntityType<? extends ShieldPortBlockEntity> getBlockEntityType() { return CENuclear.SHIELD_PORT_ENTITY.get(); }
}
