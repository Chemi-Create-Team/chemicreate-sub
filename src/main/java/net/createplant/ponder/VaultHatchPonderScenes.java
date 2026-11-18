package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.block.VaultHatchBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VaultHatchPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.vault_hatch.";
    private VaultHatchPonderScenes() { }

    public static void hatch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vault_hatch", PonderText.title(LANG + "title", "Item vault hatch"));
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        var controller = util.grid().at(2, 1, 3);
        for (int x = 2; x <= 4; x++) {
            var part = util.grid().at(x, 1, 3);
            scene.world().modifyBlockEntityNBT(util.select().position(part), com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 1);tag.putInt("Length", 3); }
                else tag.put("Controller", net.minecraft.nbt.NbtUtils.writeBlockPos(controller));
            });
        }
        scene.world().showSection(util.select().fromTo(2, 1, 3, 4, 1, 3), Direction.DOWN);
        PonderText.caption(scene, util.vector().of(3, 1.5, 3), LANG + "vault", 100);
        var hatch = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().position(hatch), Direction.SOUTH);
        scene.world().showSection(util.select().position(4, 2, 3), Direction.DOWN);
        PonderText.caption(scene, util.vector().centerOf(hatch), LANG + "place", 100);
        scene.overlay().showControls(util.vector().centerOf(hatch), Pointing.RIGHT, 40).rightClick();
        scene.world().modifyBlock(hatch, state -> state.setValue(VaultHatchBlock.OPEN, true), false);
        PonderText.caption(scene, util.vector().centerOf(hatch), LANG + "open", 120);
        PonderText.caption(scene, util.vector().centerOf(hatch), LANG + "pages", 120);
        scene.world().showSection(util.select().position(2, 2, 3), Direction.DOWN);
        scene.world().createItemOnBeltLike(util.grid().at(2, 2, 3), Direction.UP, new ItemStack(Items.IRON_INGOT, 16));
        PonderText.caption(scene, util.vector().of(2.5, 2.5, 3.5), LANG + "transport", 100);
        scene.world().modifyBlock(hatch, state -> state.setValue(VaultHatchBlock.OPEN, false), false);
        scene.overlay().showControls(util.vector().centerOf(hatch), Pointing.RIGHT, 40).withItem(AllItems.WRENCH.asStack()).whileSneaking().rightClick();
        PonderText.caption(scene, util.vector().centerOf(hatch), LANG + "remove", 110);
        scene.markAsFinished();
    }
}
