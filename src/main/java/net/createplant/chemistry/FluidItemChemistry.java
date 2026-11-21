package net.createplant.chemistry;

import com.simibubi.create.AllFluids;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.createplant.mixture.MixtureHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidItemChemistry {
    private FluidItemChemistry() { }

    public static List<FluidStack> contents(ItemStack item) {
        if (item.isEmpty()) return List.of();
        var contents = new ArrayList<FluidStack>();
        var handler = item.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                var fluid = handler.getFluidInTank(tank);
                if (!fluid.isEmpty()) contents.addAll(constituents(fluid));
            }
        }
        if (contents.isEmpty() && item.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY)
            contents.add(new FluidStack(bucket.content, 1000));
        if (contents.isEmpty() && item.is(Items.HONEY_BOTTLE)) contents.add(new FluidStack((Fluid) AllFluids.HONEY.getSource(), 250));
        if (contents.isEmpty() && item.getItem() instanceof PotionItem) {
            var potion = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            contents.add(new FluidStack(potion.is(Potions.WATER) ? Fluids.WATER : AllFluids.POTION.getSource(), 250));
        }
        contents.sort(Comparator.comparingInt(FluidStack::getAmount).reversed());
        return contents;
    }

    private static List<FluidStack> constituents(FluidStack fluid) {
        if (fluid.getFluid().isSame(AllFluids.POTION.getSource()) && fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER))
            return List.of(new FluidStack(Fluids.WATER, fluid.getAmount()));
        if (MixtureHelper.isMixture(fluid)) {
            try { return MixtureHelper.components(fluid); }
            catch (IllegalArgumentException ignored) { }
        }
        return List.of(fluid.copy());
    }

    public static List<Component> formulaLines(ItemStack item) {
        var contents = contents(item);
        if (contents.isEmpty()) return List.of();
        var lines = new ArrayList<Component>(contents.size());
        for (var fluid : contents) {
            var info = FluidChemicalRegistry.base(fluid.getFluid());
            Component formula;
            if (info == null) formula = Component.translatable(ChemicalReadout.KEY + "unknown_composition");
            else if (info.formula().equals("…")) formula = Component.translatable(ChemicalReadout.KEY + "variable_composition");
            else formula = Component.literal(info.formula());
            lines.add((contents.size() == 1 ? Component.translatable(ChemicalReadout.KEY + "item_formula", formula)
                : Component.translatable(ChemicalReadout.KEY + "item_component_formula", fluid.getHoverName(), formula)).withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }
}
