package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AssemblyPonderScenes {
    private static final String ID = "facility_constructor";
    private AssemblyPonderScenes() { }

    public static void constructor(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(ID, PonderText.title("ponder.create_refinery_n_reactors." + ID + ".title", ID));
        scene.configureBasePlate(0, 0, 17);
        scene.scaleSceneView(.33f);
        scene.setSceneOffsetY(-2);
        scene.showBasePlate();
        BlockPos base = new BlockPos(8, 1, 1), chest = base.west();
        scene.world().modifyBlockEntity(base, SchematicannonBlockEntity.class, be -> ((FacilityConstructorBlockEntity) be).configure(AssemblyPlan.Kind.FUSION.ordinal(), 0));
        scene.world().showSection(util.select().position(base), Direction.DOWN);
        text(scene, util, base, "preview");
        scene.overlay().showControls(util.vector().topOf(base), Pointing.DOWN, 60).rightClick();
        text(scene, util, base, "select");
        scene.world().modifyBlockEntity(base, SchematicannonBlockEntity.class, be -> {
            var constructor = (FacilityConstructorBlockEntity) be;
            constructor.configure(AssemblyPlan.Kind.RBMK.ordinal(), 0);constructor.configureHeight(0);
        });
        text(scene, util, base, "rbmk_size");
        scene.world().modifyBlockEntity(base, SchematicannonBlockEntity.class, be -> ((FacilityConstructorBlockEntity) be).configure(AssemblyPlan.Kind.FUSION.ordinal(), 0));
        text(scene, util, base, "other_sizes");
        scene.world().showSection(util.select().position(chest), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(base), Pointing.DOWN, 60).withItem(new ItemStack(Items.GUNPOWDER));
        text(scene, util, chest, "supply");
        scene.overlay().showControls(util.vector().topOf(base), Pointing.DOWN, 60).withItem(AllBlocks.CLIPBOARD.asStack());
        text(scene, util, base, "clipboard");
        var targets = AssemblyPlan.targets(AssemblyPlan.Kind.FUSION, base.south(2), Direction.SOUTH, 11, 7);
        for (var target : targets)
            if (!target.state().isAir()) scene.world().setBlock(target.pos(), target.state(), false);
        PonderText.build(scene, util, 2, 1, 4, 14, 11, 15, Direction.DOWN);
        var drive = targets.stream().filter(target -> target.blockEntityData() != null).findFirst().orElseThrow();
        scene.world().modifyBlockEntityNBT(util.select().position(drive.pos()), CraneDriveBlockEntity.class, tag -> {
            tag.merge(drive.blockEntityData());
            tag.putLong("Start", drive.pos().south().asLong());
            tag.putInt("Along", Direction.SOUTH.get3DDataValue());tag.putInt("Across", Direction.WEST.get3DDataValue());
            tag.putInt("Length", 11);tag.putInt("Span", 12);tag.putInt("Legs", 9);
            tag.putDouble("U", 5);tag.putDouble("V", 6);
        });
        text(scene, util, base.south(7), "build");
        text(scene, util, drive.pos(), "crane");
        text(scene, util, base, "stop");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos at, String key) {
        PonderText.caption(scene, util.vector().centerOf(at), "ponder.create_refinery_n_reactors." + ID + "." + key, 120);
    }
}
