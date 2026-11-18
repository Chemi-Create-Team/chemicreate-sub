package net.createplant.block;

import java.util.function.Predicate;
import java.util.function.Supplier;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.block.entity.PlantFluidTankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.fluids.FluidStack;

/** A Create fluid tank with its own multiblock type and accepted fluids; Create keeps connection, windows, rendering and pipes. */
public class PlantFluidTankBlock extends FluidTankBlock {
    private final Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type;
    public final Predicate<FluidStack> accepts;
    public PlantFluidTankBlock(Properties properties, Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type, Predicate<FluidStack> accepts) {
        super(properties,false);this.type=type;this.accepts=accepts;
    }
    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return type.get(); }
}
