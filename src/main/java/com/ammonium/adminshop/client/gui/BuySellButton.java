package com.ammonium.adminshop.client.gui;

import com.ammonium.adminshop.AdminShop;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Switches the shop screen between its Buy Offers and its Sell Offers. */
public class BuySellButton extends AbstractButton {

    private static final Identifier GUI = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/shop_gui.png");
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Component buyText;
    private final Component sellText;
    private final Consumer<BuySellButton> onPress;
    private boolean isBuy;

    public BuySellButton(int x, int y, Component buyText, Component sellText, boolean isBuy, Consumer<BuySellButton> onPress) {
        super(x, y, 50, 12, isBuy ? buyText : sellText);
        this.buyText = buyText;
        this.sellText = sellText;
        this.isBuy = isBuy;
        this.onPress = onPress;
    }

    /** Switches between Buy Offers and Sell Offers. Returns whether the screen now shows Buy Offers. */
    public boolean switchBuySell() {
        isBuy = !isBuy;
        setMessage(isBuy ? buyText : sellText);
        return isBuy;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, x, y, 195, isBuy ? 68 : 56, 50, 12, 256, 256);
        Font font = Minecraft.getInstance().font;
        graphics.centeredText(font, buyText, x + 12, y + 6 - font.lineHeight / 2, TEXT_COLOR);
        graphics.centeredText(font, sellText, x + 37, y + 6 - font.lineHeight / 2, TEXT_COLOR);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
