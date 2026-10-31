package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.entity.FluidSellerEntity;
import com.ammonium.adminshop.client.gui.ProgressBar;
import com.ammonium.adminshop.client.gui.TankGauge;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Fluid Seller's screen: its tank, the progress to its next sale and the name of its Account, red "No account"
 * while nobody owns it.
 */
public class FluidSellerScreen extends AbstractContainerScreen<FluidSellerMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/fluid_seller.png");
    private static final int ACCOUNT_COLOR = 0xFFFFFFFF;
    private static final int NO_ACCOUNT_COLOR = 0xFFFF0000;

    private ProgressBar progressBar;
    private TankGauge tankGauge;

    public FluidSellerScreen(FluidSellerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        tankGauge = addRenderableWidget(new TankGauge(menu::tank, FluidSellerEntity.TANK_CAPACITY, leftPos + 63, topPos + 10, 16, 50));
        progressBar = addRenderableWidget(new ProgressBar(leftPos + 88, topPos + 30));
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (tankGauge.isHovered()) {
            graphics.setComponentTooltipForNextFrame(font, tankGauge.tooltip(), mouseX, mouseY);
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
}
