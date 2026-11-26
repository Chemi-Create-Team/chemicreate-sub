package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createplant.arm.ArmRecipe;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The surface plate the arms build on. A wrench switches its recipe, an empty hand shows what it is doing. The work area is the
 * space above it, counted from the block right above the jig.
 */
public class AssemblyJigBlock extends Block implements EntityBlock, IWrenchable {
    public AssemblyJigBlock(Properties p) {
        super(p);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyJigBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CEArm.JIG_ENTITY.get() ? (l, p, s, be) -> ((AssemblyJigBlockEntity) be).tick() : null;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.getBlockEntity(context.getClickedPos()) instanceof AssemblyJigBlockEntity jig) {
            if (!level.isClientSide && context.getPlayer() != null) {
                if (!jig.cycleRecipe())
                    context.getPlayer().displayClientMessage(Component.translatable("message.create_refinery_n_reactors.jig_busy"), true);
                else context.getPlayer().displayClientMessage(jig.auto ? Component.translatable("message.create_refinery_n_reactors.jig_auto")
                        : Component.translatable("message.create_refinery_n_reactors.jig_recipe", name(jig.recipe)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AssemblyJigBlockEntity jig)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (jig.recipe == null) {
                player.displayClientMessage(Component.translatable(jig.auto ? "message.create_refinery_n_reactors.jig_auto_waiting" : "message.create_refinery_n_reactors.jig_none"), true);
            } else {
                int done = 0;
                for (byte b : jig.status) if (b == AssemblyJigBlockEntity.DONE) done++;
                player.displayClientMessage(Component.translatable("message.create_refinery_n_reactors.jig_status", name(jig.recipe), done,
                        jig.status.length, Math.max(0, jig.currentStage() + 1), jig.recipe.stages()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static Component name(ArmRecipe r) {
        return Component.translatable("assembly." + r.id().getNamespace() + "." + r.id().getPath());
    }
}
