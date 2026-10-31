package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.entity.FluidBuyerEntity;
import com.ammonium.adminshop.client.gui.FluidSprites;
import com.ammonium.adminshop.client.gui.ProgressBar;
import com.ammonium.adminshop.client.gui.TankGauge;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketSetFluidBuyerRecipe;
import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.shop.ShopCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * The Fluid Buyer's screen. It shows the tank, the fluid the Fluid Buyer buys, its progress to the next purchase and
 * the name of its Account, or a red "No account" if nobody owns it. Clicking a fluid container in the inventory whose
 * fluid a fluid Buy Offer gives sets the Fluid Buyer to that Offer. Only a creative player can change a locked one.
 */
public class FluidBuyerScreen extends AbstractContainerScreen<FluidBuyerMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/fluid_buyer.png");
    private static final int ACCOUNT_COLOR = 0xFFFFFFFF;
    private static final int NO_ACCOUNT_COLOR = 0xFFFF0000;
    private static final int TARGET_X = 104;
    private static final int TARGET_Y = 24;

    private ProgressBar progressBar;
    private TankGauge tankGauge;

    public FluidBuyerScreen(FluidBuyerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        tankGauge = addRenderableWidget(new TankGauge(menu::tank, FluidBuyerEntity.TANK_CAPACITY, leftPos + 146, topPos + 10, 16, 50));
        progressBar = addRenderableWidget(new ProgressBar(leftPos + 79, topPos + 25));
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
        FluidSprites.draw(graphics, menu.target(), leftPos + TARGET_X, topPos + TARGET_Y, 16, 16);
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

    /** Only a creative player changing a locked Fluid Buyer can skip the Offer's Permit. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (minecraft.player != null && hoveredSlot != null && hoveredSlot.container instanceof Inventory && hoveredSlot.hasItem()) {
            FluidStack fluid = FluidUtil.getFirstStackContained(hoveredSlot.getItem());
            FluidResource resource = FluidResource.of(fluid);
            var offer = ShopCache.CLIENT.offers().stream()
                    .filter(holder -> !fluid.isEmpty() && holder.value() instanceof BuyFluidRecipe buy && buy.fluid().equals(resource))
                    .findFirst();
            if (offer.isPresent()) {
                if (menu.isLocked() && !minecraft.player.isCreative()) {
                    minecraft.player.sendSystemMessage(Component.translatable("message.adminshop.recipe_locked"));
                } else if (!menu.isLocked() && !ClientCache.hasPermit(((BuyFluidRecipe) offer.get().value()).permit())) {
                    minecraft.player.sendSystemMessage(Component.translatable("gui.adminshop.no_permit"));
                } else {
                    ClientPacketDistributor.sendToServer(new PacketSetFluidBuyerRecipe(offer.get().id()));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
