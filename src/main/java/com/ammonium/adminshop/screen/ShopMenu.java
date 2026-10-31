package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.blocks.ShopBlock;
import com.ammonium.adminshop.money.MoneyHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The shop screen's menu: only the player's inventory. Trades are requests to the server, which the
 * server accepts only from a player with this menu open.
 */
public class ShopMenu extends AbstractContainerMenu {

    public ShopMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.SHOP_MENU.get(), containerId);
        layoutPlayerInventorySlots(playerInventory, 16, 140);
    }

    /** Opens the shop screen for {@code player}, from the Shop block or the Shop Tablet. */
    public static void open(ServerPlayer player) {
        // The screen shows the Balance of the player's Account, so send it fresh.
        MoneyHelper.get(player.level()).syncToPlayer(player);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) -> new ShopMenu(containerId, inventory),
                Component.translatable(ShopBlock.SCREEN_ADMINSHOP_SHOP)));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // There is no other inventory to move items to.
        return ItemStack.EMPTY;
    }

    private void layoutPlayerInventorySlots(Inventory inventory, int leftCol, int topRow) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, leftCol + col * 18, topRow + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, leftCol + col * 18, topRow + 58));
        }
    }
}
