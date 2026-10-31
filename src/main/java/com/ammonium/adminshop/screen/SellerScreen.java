package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.client.gui.ProgressBar;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketSetSellerRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.shop.ShopCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Seller's screen. It shows the Seller's slot, its progress to the next sale and the name of its Account, or a
 * red "No account" if nobody owns it. When a creative player clicks an item in their inventory, the Seller locks to
 * the Sell Offer that takes it.
 */
public class SellerScreen extends AbstractContainerScreen<SellerMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/seller.png");
    private static final int ACCOUNT_COLOR = 0xFFFFFFFF;
    private static final int NO_ACCOUNT_COLOR = 0xFFFF0000;

    private ProgressBar progressBar;

    public SellerScreen(SellerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        progressBar = addRenderableWidget(new ProgressBar(leftPos + 80, topPos + 30));
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
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
        if (minecraft.player != null && minecraft.player.isCreative() && hoveredSlot != null
                && hoveredSlot.container instanceof Inventory && hoveredSlot.hasItem()) {
            ItemStack stack = hoveredSlot.getItem();
            var offer = ShopCache.CLIENT.offers().stream()
                    .filter(holder -> holder.value() instanceof SellItemRecipe sell && sell.isMatchingItemStack(stack))
                    .findFirst();
            if (offer.isPresent()) {
                ClientPacketDistributor.sendToServer(new PacketSetSellerRecipe(offer.get().id()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
