package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.screen.ShopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Tests that the shop screen opens from the Shop block and from the Shop Tablet, wherever the player is, and that
 * a broken Shop drops itself.
 */
final class ShopBlockTests {

    private static final BlockPos SHOP = new BlockPos(4, 1, 4);

    private ShopBlockTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("the_shop_block_opens_the_shop", 20, TestPlayers.withPlayers(ShopBlockTests::shopBlock));
        tests.test("the_shop_tablet_opens_the_shop", 20, TestPlayers.withPlayers(ShopBlockTests::shopTablet));
        tests.test("a_broken_shop_drops_itself", 20, ShopBlockTests::dropsItself);
    }

    private static void shopBlock(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("open_block");
        helper.setBlock(SHOP, ModBlocks.SHOP.get());

        TestShop.use(helper, player, SHOP);

        helper.assertTrue(player.containerMenu instanceof ShopMenu, "using the Shop block did not open the shop");
    }

    private static void shopTablet(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("open_tablet");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TABLET.get()));

        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);

        helper.assertTrue(player.containerMenu instanceof ShopMenu, "using the Shop Tablet did not open the shop");
    }

    private static void dropsItself(GameTestHelper helper) {
        helper.setBlock(SHOP, ModBlocks.SHOP.get());

        helper.getLevel().destroyBlock(helper.absolutePos(SHOP), true);

        helper.assertItemEntityPresent(ModBlocks.SHOP.get().asItem(), SHOP, 2);
        helper.succeed();
    }
}
