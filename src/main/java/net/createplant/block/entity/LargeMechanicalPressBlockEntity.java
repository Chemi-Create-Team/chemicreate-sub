package net.createplant.block.entity;
import net.createplant.registry.CEAtomicsRecipeTypes;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.LargePressFootprint;
import net.createplant.recipe.LargePressingRecipe;
import net.createplant.registry.CELargePress;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class LargeMechanicalPressBlockEntity extends KineticBlockEntity implements PressingBehaviour.PressingBehaviourSpecifics {
    private static final String STATUS = "large_press." + CreatePlant.MOD_ID + ".";
    private PressingBehaviour pressing;

    public LargeMechanicalPressBlockEntity(BlockPos pos, BlockState state) { super(CELargePress.PRESS_ENTITY.get(), pos, state); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        pressing = new PressingBehaviour(this) {
            @Override public void tick() {
                if (!running) return; // This press receives work from its depot only.
                if (!level.isClientSide() && !isVirtual() && level.getGameTime() % 8 == 0 && structureProblem() != null) {
                    running = false;
                    notifyUpdate();
                    return;
                }
                super.tick();
            }
        };
        behaviours.add(pressing);
    }

    public PressingBehaviour pressing() { return pressing; }

    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition.below(2)).expandTowards(0, 3, 0).inflate(1, 0, 1); }

    @Override public boolean tryProcessOnBelt(TransportedItemStack item, List<ItemStack> results, boolean simulate) {
        if (structureProblem() != null) return false;
        DepotBehaviour depot = BlockEntityBehaviour.get(level, worldPosition.below(2), DepotBehaviour.TYPE);
        if (depot == null || !depot.isOutputEmpty()) return false;
        var input = new SingleRecipeInput(item.stack);
        var recipe = level.getRecipeManager().getRecipeFor(CEAtomicsRecipeTypes.LARGE_PRESSING.<SingleRecipeInput, LargePressingRecipe>getType(), input, level);
        if (recipe.isEmpty()) return false;
        if (!simulate) {
            results.add(recipe.get().value().assemble(input, level.registryAccess()));
            pressing.particleItems.add(item.stack.copyWithCount(1));
            setChanged();
        }
        return true;
    }

    @Override public boolean tryProcessInBasin(boolean simulate) { return false; }
    @Override public boolean tryProcessInWorld(ItemEntity entity, boolean simulate) { return false; }
    @Override public boolean canProcessInBulk() { return false; }
    @Override public int getParticleAmount() { return 20; }
    @Override public float getKineticSpeed() { return getSpeed(); }
    @Override public void onPressingCompleted() { setChanged(); }

    private String structureProblem() {
        if (!LargePressFootprint.complete(level, worldPosition, false)) return "frame";
        if (!LargePressFootprint.complete(level, worldPosition.below(2), true)) return "depot";
        return LargePressFootprint.openWorkingSpace(level, worldPosition.below()) ? null : "clearance";
    }

    private String statusKey(String problem) {
        return problem != null ? problem : getSpeed() == 0 ? "power" : pressing.running ? "pressing" : "ready";
    }

    public Component status() { return Component.translatable(STATUS + statusKey(structureProblem())); }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        String problem = structureProblem();
        boolean warning = problem != null || getSpeed() == 0;
        tooltip.add(Component.literal(warning ? "   ⚠ " : "   ").append(Component.translatable(STATUS + statusKey(problem)))
            .withStyle(warning ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return true;
    }
}
