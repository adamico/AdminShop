package com.ammonium.adminshop.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws a fluid in a screen as its still texture, tinted with the fluid's colour. */
public final class FluidSprites {

    private FluidSprites() {
    }

    /**
     * Fills the rectangle at {@code x}, {@code y} with the still texture of {@code fluid}. Draws nothing for an empty
     * fluid. A tint without alpha is made opaque, because many tints leave the alpha out and would draw nothing.
     */
    public static void draw(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y, int width, int height) {
        if (fluid.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        if (model == null) {
            return;
        }
        var tintSource = model.fluidTintSource();
        int tint = tintSource != null ? tintSource.colorAsStack(fluid) : -1;
        if (ARGB.alpha(tint) == 0) {
            tint = ARGB.opaque(tint);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), x, y, width, height, tint);
    }
}
