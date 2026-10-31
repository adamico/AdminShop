package com.ammonium.adminshop.client.gui;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A fluid machine screen's tank: the fluid fills it from the bottom as far as the tank is full, with the amount in
 * millibuckets at half size below it. Hovered, its tooltip gives the amount, the capacity and the fluid.
 */
public class TankGauge extends AbstractWidget {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Supplier<FluidStack> tank;
    private final int capacity;

    public TankGauge(Supplier<FluidStack> tank, int capacity, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.tank = tank;
        this.capacity = capacity;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        FluidStack fluid = tank.get();
        int filled = capacity <= 0 ? 0 : (int) ((long) height * Math.min(fluid.getAmount(), capacity) / capacity);
        FluidSprites.draw(graphics, fluid, getX(), getY() + height - filled, width, filled);

        Font font = Minecraft.getInstance().font;
        String amount = String.valueOf(fluid.getAmount());
        graphics.pose().pushMatrix();
        graphics.pose().scale(0.5f, 0.5f);
        graphics.text(font, amount, 2 * (getX() + 16) - font.width(amount), 2 * getY() + 92, TEXT_COLOR);
        graphics.pose().popMatrix();
    }

    /** The amount and the capacity, then the fluid's name or "Empty". */
    public List<Component> tooltip() {
        FluidStack fluid = tank.get();
        return List.of(Component.literal(fluid.getAmount() + "/" + capacity),
                fluid.isEmpty() ? Component.translatable("gui.adminshop.tank_empty") : fluid.getHoverName());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
