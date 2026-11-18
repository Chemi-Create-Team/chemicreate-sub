package net.createplant.mixin;

import net.createplant.client.CreativeSectionBanners;
import net.createplant.registry.SectionedCreativeTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventorySectionsMixin {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;

    @Inject(
            method = {"selectTab", "refreshCurrentTabContents"},
            at = @At("TAIL"))
    private void createPlant$applySections(CallbackInfo ci) {
        if (!(selectedTab instanceof SectionedCreativeTab tab)) return;
        var screen = (CreativeModeInventoryScreen) (Object) this;
        screen.getMenu().items.clear();
        screen.getMenu().items.addAll(tab.layoutItems());
        screen.getMenu().scrollTo(scrollOffs);
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void createPlant$renderSections(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (selectedTab instanceof SectionedCreativeTab tab)
            CreativeSectionBanners.render(
                    (CreativeModeInventoryScreen) (Object) this, tab, scrollOffs, graphics);
    }
}
