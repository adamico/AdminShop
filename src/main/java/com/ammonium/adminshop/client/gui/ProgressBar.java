package com.ammonium.adminshop.client.gui;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.entity.MachineEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A machine screen's arrow, filling from left to right as the machine nears its next trade. */
public class ProgressBar extends AbstractWidget {
    private static final int WIDTH = 22;
    private static final int HEIGHT = 16;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/progress_right.png");

    private int progress = 0;

    public ProgressBar(int x, int y) {
        super(x, y, WIDTH, HEIGHT, Component.translatable("gui.adminshop.progress_bar"));
    }

    /** From 0 to {@link MachineEntity#PROGRESS_STEPS}. */
    public void setProgress(int progress) {
        this.progress = progress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The texture holds the empty arrow above the full one.
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), 0, 0, width, height, WIDTH, HEIGHT * 2);
        int filled = progress >= MachineEntity.PROGRESS_STEPS ? width : width * progress / MachineEntity.PROGRESS_STEPS;
        if (filled > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), 0, HEIGHT, filled, height, WIDTH, HEIGHT * 2);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
