package com.ammonium.adminshop.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws a fluid in a screen as 1.21.1 did: its still texture, tinted with the fluid's colour. */
public final class FluidSprites {

    private FluidSprites() {
    }

    /** Fills the rectangle at {@code x}, {@code y} with {@code fluid}'s still texture; draws nothing for no fluid. */
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
        // Tints often leave out the alpha, which would draw nothing.
        if (ARGB.alpha(tint) == 0) {
            tint = ARGB.opaque(tint);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), x, y, width, height, tint);
    }
}
