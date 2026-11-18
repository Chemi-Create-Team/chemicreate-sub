package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A control-rod controller: a lever set from 0, fully withdrawn, to 100, fully inserted, in single steps by scrolling over
 * its value box, or ten at a time by using it. The control rod link it stands on or beside sends its setting.
 */
public class ControlRodControllerBlockEntity extends SmartBlockEntity {
    public static final int STEPS = 100;
    private ScrollValueBehaviour value;

    public ControlRodControllerBlockEntity(BlockPos pos, BlockState state) { super(CENuclear.ROD_CONTROLLER_ENTITY.get(), pos, state); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        value = new ScrollValueBehaviour(Component.translatable("control_rod_controller.create_refinery_n_reactors.setting"), this,
            new CenteredSideValueBoxTransform((state, side) -> side.getAxis().isHorizontal()));
        value.between(0, STEPS);
        value.withCallback(next -> sendData());
        behaviours.add(value);
    }

    public int setting() { return value.getValue(); }

    /** Selectors on a reactor desk preload the lever without simulating clicks. */
    public void setSetting(int setting) { value.setValue(Math.clamp(setting, 0, STEPS)); }

    /** Using the lever moves it ten steps, back when sneaking. */
    public void step(boolean back) { value.setValue(Math.clamp(value.getValue() + (back ? -10 : 10), 0, STEPS)); }
}
