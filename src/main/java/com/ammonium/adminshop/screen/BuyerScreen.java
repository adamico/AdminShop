package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.client.gui.ProgressBar;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketSetBuyerRecipe;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.shop.ShopCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Buyer's screen: its output slots, the goods it buys, the progress to its next purchase and the name of its
 * Account, red "No account" while nobody owns it. Clicking an item in the inventory that a Buy Offer gives sets the
 * Buyer to that Offer; a locked Buyer only a creative player can set.
 */
public class BuyerScreen extends AbstractContainerScreen<BuyerMenu> {
    private static final int ACCOUNT_COLOR = 0xFFFFFFFF;
    private static final int NO_ACCOUNT_COLOR = 0xFFFF0000;
    private static final int COMPONENTS_COLOR = 0xFFFF55FF;
    private static final int TARGET_X = 104;
    private static final int TARGET_Y = 14;

    private ProgressBar progressBar;

    public BuyerScreen(BuyerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        progressBar = addRenderableWidget(new ProgressBar(leftPos + 77, topPos + 15));
        progressBar.setProgress(menu.progress());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        progressBar.setProgress(menu.progress());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, menu.tier().texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        ItemStack target = menu.target();
        if (!target.isEmpty()) {
            int x = leftPos + TARGET_X;
            int y = topPos + TARGET_Y;
            graphics.fakeItem(target, x, y);
            if (!target.getComponentsPatch().isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().scale(0.5f, 0.5f);
                graphics.text(font, "+NBT", 2 * x, 2 * (y + 2), COMPONENTS_COLOR);
                graphics.pose().popMatrix();
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        MoneyHelper.MoneyAccount account = menu.hasOwner() ? ClientCache.getAccount() : null;
        if (account != null) {
            graphics.text(font, account.name(), 7, 62, ACCOUNT_COLOR);
        } else {
            graphics.text(font, Component.translatable("gui.adminshop.no_account"), 7, 62, NO_ACCOUNT_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (minecraft.player != null && hoveredSlot != null && hoveredSlot.container instanceof Inventory && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            var offer = ShopCache.CLIENT.offers().stream()
                    .filter(holder -> holder.value() instanceof BuyItemRecipe buy && ItemStack.isSameItemSameComponents(stack, buy.getItem()))
                    .findFirst();
            if (offer.isPresent()) {
                if (menu.isLocked() && !minecraft.player.isCreative()) {
                    minecraft.player.sendSystemMessage(Component.translatable("message.adminshop.recipe_locked"));
                } else if (!menu.isLocked() && !ClientCache.hasPermit(((BuyItemRecipe) offer.get().value()).permit())) {
                    // As on 1.21.1: only a creative player changing a locked Buyer skips the Permit.
                    minecraft.player.sendSystemMessage(Component.translatable("gui.adminshop.no_permit"));
                } else {
                    ClientPacketDistributor.sendToServer(new PacketSetBuyerRecipe(offer.get().id()));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
