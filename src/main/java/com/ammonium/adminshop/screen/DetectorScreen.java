package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.client.gui.TextConfirmButton;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketSetDetectorThreshold;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A Detector's screen: a box for its threshold, a button to confirm it, and the name of its Account, red
 * "No account" while nobody owns it. The box shows the threshold the server sends until the player types in it.
 */
public class DetectorScreen extends AbstractContainerScreen<DetectorMenu> {
    private static final int ACCOUNT_COLOR = 0xFFFFFFFF;
    private static final int NO_ACCOUNT_COLOR = 0xFFFF0000;

    private EditBox thresholdBox;
    private TextConfirmButton confirmButton;
    /** The threshold the box last showed from the server, or null before it showed any. */
    private @Nullable Long shownThreshold;

    public DetectorScreen(DetectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        inventoryLabelY = 60;
    }

    @Override
    protected void init() {
        super.init();
        String typed = thresholdBox == null ? null : thresholdBox.getValue();
        thresholdBox = new EditBox(font, leftPos + 38, topPos + 24, 121, 12, Component.empty());
        thresholdBox.setFilter(input -> input.matches("[0-9]*"));
        if (typed != null) {
            thresholdBox.setValue(typed);
        } else {
            showThreshold();
        }
        addRenderableWidget(thresholdBox);
        confirmButton = addRenderableWidget(new TextConfirmButton(leftPos + 159, topPos + 24, button -> confirm()));
    }

    /** Shows the server's threshold in the box, unless the player has typed another. */
    private void showThreshold() {
        long threshold = menu.threshold();
        if (shownThreshold == null || thresholdBox.getValue().equals(Long.toString(shownThreshold))) {
            thresholdBox.setValue(Long.toString(threshold));
            shownThreshold = threshold;
        }
    }

    private @Nullable Long typedThreshold() {
        try {
            long value = Long.parseLong(thresholdBox.getValue());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void confirm() {
        Long threshold = typedThreshold();
        if (threshold == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(new PacketSetDetectorThreshold(threshold));
        // The box follows the server's threshold again from here.
        shownThreshold = threshold;
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("gui.adminshop.set_threshold", threshold));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (shownThreshold == null || menu.threshold() != shownThreshold) {
            showThreshold();
        }
        confirmButton.setValid(typedThreshold() != null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, menu.kind().texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        MoneyHelper.MoneyAccount account = menu.hasOwner() ? ClientCache.getAccount() : null;
        if (account != null) {
            graphics.text(font, account.name(), 7, 48, ACCOUNT_COLOR);
        } else {
            graphics.text(font, Component.translatable("gui.adminshop.no_account"), 7, 48, NO_ACCOUNT_COLOR);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (thresholdBox.isFocused()) {
            if (event.isEscape()) {
                // Escape leaves the box first; a second Escape closes the screen.
                thresholdBox.setFocused(false);
                return true;
            }
            // Typing in the box never closes the screen or reaches a keybind.
            return thresholdBox.keyPressed(event) || thresholdBox.canConsumeInput();
        }
        return super.keyPressed(event);
    }
}
