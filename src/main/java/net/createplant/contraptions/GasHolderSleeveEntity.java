package net.createplant.contraptions;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.createplant.registry.CEGasBell;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Automatic tank formation can reach a client before the controller block or complex spawn data. */
public class GasHolderSleeveEntity extends ControlledContraptionEntity {
    private int awaitingController;
    public GasHolderSleeveEntity(EntityType<?> type,Level level) { super(type,level); }
    public static GasHolderSleeveEntity create(Level level,IControlContraption controller,GasHolderSleeveContraption sleeve) {
        var entity=new GasHolderSleeveEntity(CEGasBell.MOVING_SLEEVE.get(),level);entity.controllerPos=controller.getBlockPosition();entity.setContraption(sleeve);return entity;
    }
    @Override public void tick() {
        if (level().isClientSide && (getContraption()==null || controllerPos==null || !level().hasChunkAt(controllerPos)
            || !(level().getBlockEntity(controllerPos) instanceof IControlContraption))) {if (++awaitingController>100) discard();return;}
        awaitingController=0;super.tick();
    }
}
