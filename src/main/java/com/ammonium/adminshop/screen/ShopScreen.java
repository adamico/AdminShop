package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.client.gui.BuySellButton;
import com.ammonium.adminshop.client.gui.ShopButton;
import com.ammonium.adminshop.item.PermitItem;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyFormat;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketAccountAddPermit;
import com.ammonium.adminshop.network.PacketBuyRequest;
import com.ammonium.adminshop.network.PacketSellRequest;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopDisplay;
import com.ammonium.adminshop.shop.ShopTrades;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * The shop screen: the Offers of the client shop cache in a grid of 4 rows of 9, Buy or Sell Offers at a
 * time, with a search box. Clicking an Offer trades it (shift and control trade more); shift-clicking a
 * stack in the inventory sells it through the first Sell Offer that takes it, and shift-clicking a fluid
 * container sells its fluid the same way.
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    private static final Identifier GUI = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/shop_gui.png");
    private static final String GUI_BUY = "gui.buy";
    private static final String GUI_SELL = "gui.sell";
    private static final int NUM_ROWS = 4, NUM_COLS = 9;
    private static final int SHOP_BUTTON_X = 16;
    private static final int SHOP_BUTTON_Y = 33;
    private static final int SHOP_BUTTON_SIZE = 18;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<ShopButton> shopButtons = new ArrayList<>();
    private List<OfferDisplay> searchResults = List.of();
    /** The display the buttons were made from; a new sync, after {@code /reload}, replaces it. */
    private ShopDisplay shownDisplay;
    private int rowsPassed = 0;
    private boolean isBuy = true;
    private EditBox searchBar;

    public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 195, 222);
    }

    @Override
    protected void init() {
        super.init();
        String search = searchBar == null ? "" : searchBar.getValue();
        searchBar = new EditBox(font, leftPos + 16, topPos + 18, 70, 12, Component.empty());
        searchBar.setValue(search);
        searchBar.setResponder(value -> {
            rowsPassed = 0;
            refreshShopButtons();
        });
        addRenderableWidget(searchBar);
        addRenderableWidget(new BuySellButton(leftPos + 15, topPos + 4,
                Component.translatable(GUI_BUY), Component.translatable(GUI_SELL), isBuy, button -> {
            isBuy = button.switchBuySell();
            rowsPassed = 0;
            refreshShopButtons();
        }));
        refreshShopButtons();
    }

    /** Lists the Offers that match the search, from the first row not scrolled past. */
    private void refreshShopButtons() {
        shownDisplay = ShopCache.CLIENT.display();
        searchResults = shownDisplay.search(isBuy, searchBar.getValue());
        rowsPassed = Math.min(rowsPassed, maxRowsPassed());
        shopButtons.forEach(this::removeWidget);
        shopButtons.clear();

        int first = rowsPassed * NUM_COLS;
        List<OfferDisplay> shown = searchResults.subList(Math.min(first, searchResults.size()),
                Math.min(first + NUM_ROWS * NUM_COLS, searchResults.size()));
        for (int i = 0; i < shown.size(); i++) {
            ShopButton button = new ShopButton(shown.get(i),
                    leftPos + SHOP_BUTTON_X + SHOP_BUTTON_SIZE * (i % NUM_COLS),
                    topPos + SHOP_BUTTON_Y + SHOP_BUTTON_SIZE * (i / NUM_COLS),
                    pressed -> attemptTransaction(pressed.getOffer(), pressed.getQuantity()));
            shopButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private int maxRowsPassed() {
        return (int) Math.max(Math.ceil(searchResults.size() / (double) NUM_COLS) - NUM_ROWS, 0);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (ShopCache.CLIENT.display() != shownDisplay) {
            refreshShopButtons();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        // Scroll indicators, over the grid.
        if (rowsPassed > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, leftPos + 15, topPos + 32, 15, 223, 162, 8, 256, 256);
        }
        if (rowsPassed < maxRowsPassed()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, leftPos + 15, topPos + 96, 15, 232, 162, 8, 256, 256);
        }
        shopButtons.stream().filter(button -> button.isMouseOn).findFirst()
                .ifPresent(button -> graphics.setComponentTooltipForNextFrame(font, button.getTooltipContent(), mouseX, mouseY));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.centeredText(font, title, imageWidth / 2, 6, TEXT_COLOR);
        graphics.text(font, playerInventoryTitle, 16, imageHeight - 94, TEXT_COLOR);

        long balance = -1;
        Component name = Component.empty();
        MoneyHelper.MoneyAccount account = ClientCache.getAccount();
        if (account != null) {
            balance = account.balance();
            name = account.name();
        }
        String formatted = MoneyFormat.forcedFormat(balance,
                minecraft.hasAltDown() ? MoneyFormat.FormatType.RAW : MoneyFormat.FormatType.SHORT);
        graphics.text(font, formatted, imageWidth - font.width(formatted) - 6, 6, TEXT_COLOR);
        graphics.text(font, name, 16, 112, TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (searchBar.isFocused() && !searchBar.isMouseOver(event.x(), event.y())) {
            searchBar.setFocused(false);
        }
        Slot slot = hoveredSlot;
        if (slot != null && event.hasShiftDown() && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (slot.container instanceof Inventory && PermitItem.permit(stack).isPresent()) {
                // Redeem the Trade Permit for the Account.
                ClientPacketDistributor.sendToServer(new PacketAccountAddPermit(slot.getContainerSlot()));
                return true;
            }
            // Sell a fluid container's fluid, in as many trades as it holds.
            FluidStack fluid = FluidUtil.getFirstStackContained(stack);
            Optional<OfferDisplay> fluidOffer = ShopCache.CLIENT.display().sellOfferFor(fluid);
            if (fluidOffer.isPresent()) {
                if (fluid.getAmount() >= fluidOffer.get().count()) {
                    ClientPacketDistributor.sendToServer(new PacketSellRequest(fluidOffer.get().offer(), slot.getContainerSlot(), ShopTrades.ALL));
                }
                return true;
            }
            // Sell the whole stack, in as many trades as it holds.
            Optional<OfferDisplay> offer = ShopCache.CLIENT.display().sellOfferFor(stack);
            if (offer.isPresent()) {
                if (stack.getCount() >= offer.get().count()) {
                    ClientPacketDistributor.sendToServer(new PacketSellRequest(offer.get().offer(), slot.getContainerSlot(), ShopTrades.ALL));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBar.isFocused()) {
            if (event.isEscape()) {
                // Escape leaves the search box first; a second Escape closes the screen.
                searchBar.setFocused(false);
                return true;
            }
            // Typing in the search box never closes the screen or reaches a keybind.
            return searchBar.keyPressed(event) || searchBar.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            rowsPassed = Math.max(0, rowsPassed - 1);
        } else if (scrollY < 0) {
            rowsPassed = Math.min(maxRowsPassed(), rowsPassed + 1);
        }
        refreshShopButtons();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void attemptTransaction(OfferDisplay offer, int quantity) {
        if (offer.isBuy()) {
            ClientPacketDistributor.sendToServer(new PacketBuyRequest(offer.offer(), quantity));
        } else {
            ClientPacketDistributor.sendToServer(new PacketSellRequest(offer.offer(), ShopTrades.ANY_SLOT, quantity));
        }
    }
}
