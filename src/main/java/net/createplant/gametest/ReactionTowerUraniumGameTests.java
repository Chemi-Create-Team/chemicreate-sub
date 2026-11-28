package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import static net.createplant.gametest.ReactionTowerGameTests.*;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEUranium;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ReactionTowerUraniumGameTests {
    @GameTest(template="test_fractionation")
    public static void solidFeedFillsTheLowestTrayFirstAndKeepsCatalystsOut(GameTestHelper h){
        var owner=tower(h.getLevel(),h.absolutePos(BASE),3,ItemStack.EMPTY);
        var solids=items(h,BASE.offset(0,0,1),Direction.WEST);
        h.assertTrue(items(h,BASE.offset(0,1,1),Direction.WEST)==null && solids!=null && solids.getSlots()==6,"Only the bottom layer's sides take solids: one feed and one product slot per tray");
        h.assertTrue(!solids.isItemValid(0,new ItemStack(net.minecraft.world.item.Items.DIRT)) && !solids.insertItem(0,new ItemStack(net.minecraft.world.item.Items.DIRT),false).isEmpty(),"No tray reaction takes dirt");
        var rest=net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(solids,net.createplant.registry.CEUranium.YELLOWCAKE.toStack(20),false);
        h.assertTrue(rest.isEmpty() && owner.layer(0).load().getCount()==ReactionTowerBlockEntity.TRAY_LOAD && owner.layer(1).load().getCount()==4 && owner.layer(2).load().isEmpty(),
            "A funnel fills the lowest tray to sixteen, then the next");
        h.assertTrue(solids.extractItem(0,1,false).isEmpty(),"Feed on a tray cannot be pulled back out of the side");
        h.assertTrue(!owner.canInsert(new ItemStack(CEItems.OXIDATION_CATALYST.get())),"A catalyst cannot share the tower with solid feed");
        owner.toggleHatch();
        h.assertTrue(ReactionTowerBlockEntity.tray(owner.takeTop()) && !owner.layer(2).hasTray() && owner.takeTop().getCount()==4 && owner.layer(1).load().isEmpty() && owner.layer(1).hasTray(),
            "Through the hatch the highest tray comes out first; the next tray gives its feed before itself");
        h.setBlock(CENTRE,net.minecraft.world.level.block.Blocks.AIR);
        // Containers.dropItemStack splits a drop into random pieces of 10 to 30, so the sixteen may come as one stack or as two.
        int dropped=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(CENTRE)).inflate(2),
            e->e.getItem().is(net.createplant.registry.CEUranium.YELLOWCAKE.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(dropped==ReactionTowerBlockEntity.TRAY_LOAD,"Breaking a layer's centre drops the feed on its tray: "+dropped);
        h.succeed();
    }

}
