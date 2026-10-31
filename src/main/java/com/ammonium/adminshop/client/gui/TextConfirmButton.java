package com.ammonium.adminshop.client.gui;

import com.ammonium.adminshop.AdminShop;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Confirms the text in a box next to it: a green tick while the text is valid, a red cross while it isn't. */
public class TextConfirmButton extends AbstractButton {

    private static final Identifier GUI = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/detector.png");
    private static final int SIZE = 12;

    private final Consumer<TextConfirmButton> onPress;
    private boolean valid;

    public TextConfirmButton(int x, int y, Consumer<TextConfirmButton> onPress) {
        super(x, y, SIZE, SIZE, Component.translatable("button.adminshop.confirm"));
        this.onPress = onPress;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, getX(), getY(), valid ? 180 : 192, 0, SIZE, SIZE, 256, 256);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
