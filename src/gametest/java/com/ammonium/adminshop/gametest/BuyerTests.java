package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.AbstractBuyerEntity;
import com.ammonium.adminshop.blocks.entity.BuyerEntity2;
import com.ammonium.adminshop.network.PacketSetItemBuyerRecipe;
import com.ammonium.adminshop.screen.AbstractBuyerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Tests the Buyers. A Buyer set to a Buy Offer from its screen buys one trade of it per cycle of its tier and charges
 * the owning team's Account, while the Account can pay and the goods fit. Hoppers can take the goods out.
 * {@code lockedRecipe} stops survival players from changing the Offer. A Buyer keeps its settings and contents when
 * saved. The Offers are those in {@link ShopTradeTests}. {@code gametest/buy_diamonds} gives 2 diamonds for 50, and
 * {@code gametest/buy_enchanted_book} gives a named book for 40.
 */
final class BuyerTests {

    private static final ResourceKey<Recipe<?>> BUY_DIAMONDS = ShopTradeTests.BUY_DIAMONDS;
    private static final ResourceKey<Recipe<?>> BUY_BOOK = ShopTradeTests.BUY_BOOK;
    private static final long DIAMONDS_PRICE = 50;
    private static final BlockPos BUYER = new BlockPos(4, 1, 4);

    private BuyerTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_t1_buyer_buys_once_every_41_ticks", 500, helper -> buysOnceEveryCycle(helper, ModBlocks.BUYER_1.get(), "t1", 40));
        tests.test("a_t2_buyer_buys_once_every_21_ticks", 300, helper -> buysOnceEveryCycle(helper, ModBlocks.BUYER_2.get(), "t2", 20));
        tests.test("a_t3_buyer_buys_once_every_11_ticks", 200, helper -> buysOnceEveryCycle(helper, ModBlocks.BUYER_3.get(), "t3", 10));
        tests.test("a_buyer_stops_when_its_account_cannot_pay", 200, BuyerTests::stopsWhenBroke);
        tests.test("a_buyer_stops_when_its_output_is_full", 200, BuyerTests::stopsWhenFull);
        tests.test("a_buyer_without_an_offer_or_an_owner_buys_nothing", 200, BuyerTests::buysNothingUnset);
        tests.test("hoppers_take_from_a_buyer_and_put_anything_in", 100, BuyerTests::hoppers);
        tests.test("only_a_buyers_team_sets_its_offer", 20, TestPlayers.withPlayers(BuyerTests::onlyItsTeamSets));
        tests.test("a_locked_buyers_offer_only_a_creative_player_changes", 20, TestPlayers.withPlayers(BuyerTests::locking));
        tests.test("a_buyers_settings_and_contents_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(BuyerTests::survivesSaveAndReload));
        tests.test("a_broken_buyer_drops_itself_and_its_contents", 20, BuyerTests::dropsItselfAndContents);
    }

    /**
     * Known bug (adamico/AdminShop#20): every Buyer buys one tick slower than its tooltip says, so a T1, T2 or T3
     * Buyer buys once every 41, 21 or 11 ticks instead of every 40, 20 or 10. After ten tooltip cycles and a half it
     * has made 9 purchases, where it should have made 10.
     */
    private static void buysOnceEveryCycle(GameTestHelper helper, Block block, String name, int tooltipCooldown) {
        long start = 10_000;
        UUID team = setUp(helper, block, "buyer_speed_" + name, start, BUY_DIAMONDS);

        helper.runAfterDelay(10L * tooltipCooldown + 5, () -> {
            TestShop.assertBalance(helper, team, start - 9 * DIAMONDS_PRICE);
            assertDiamonds(helper, 2 * 9);
            helper.succeed();
        });
    }

    /** The Account holds enough for one purchase of 50 but not two. */
    private static void stopsWhenBroke(GameTestHelper helper) {
        UUID team = setUp(helper, ModBlocks.BUYER_3.get(), "buyer_broke", 75, BUY_DIAMONDS);

        helper.runAfterDelay(5L * 11, () -> {
            TestShop.assertBalance(helper, team, 25);
            assertDiamonds(helper, 2);
            helper.succeed();
        });
    }

    /** The output has room for 1 more diamond, less than a trade's 2. */
    private static void stopsWhenFull(GameTestHelper helper) {
        UUID team = setUp(helper, ModBlocks.BUYER_1.get(), "buyer_full", 1_000, BUY_DIAMONDS);
        buyer(helper).setItem(0, new ItemStack(Items.DIAMOND, 63));

        helper.runAfterDelay(3L * 41, () -> {
            TestShop.assertBalance(helper, team, 1_000);
            assertDiamonds(helper, 63);
            helper.succeed();
        });
    }

    /**
     * A Buyer with no Offer buys nothing. A Buyer that no player placed, as with a command, buys nothing even when set
     * to an Offer, because no Account pays.
     */
    private static void buysNothingUnset(GameTestHelper helper) {
        UUID team = setUp(helper, ModBlocks.BUYER_3.get(), "buyer_no_offer", 1_000, null);
        BlockPos unowned = BUYER.east(2);
        helper.setBlock(unowned, ModBlocks.BUYER_3.get());
        helper.getBlockEntity(unowned, AbstractBuyerEntity.class).setRecipe(BUY_DIAMONDS.identifier());

        helper.runAfterDelay(3L * 11, () -> {
            TestShop.assertBalance(helper, team, 1_000);
            assertDiamonds(helper, 0);
            helper.assertTrue(helper.getBlockEntity(unowned, AbstractBuyerEntity.class).isEmpty(), "an unowned Buyer bought something");
            helper.succeed();
        });
    }

    /**
     * Known bug (adamico/AdminShop#20): hoppers can put any item into a Buyer's output slots. A hopper above the
     * Buyer pushes dirt into it, and a hopper below takes the diamonds out. Nothing should go into a Buyer.
     */
    private static void hoppers(GameTestHelper helper) {
        helper.setBlock(BUYER, ModBlocks.BUYER_2.get());
        buyer(helper).setItem(1, new ItemStack(Items.DIAMOND, 4));
        BlockPos above = BUYER.above();
        helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(above, HopperBlockEntity.class).setItem(0, new ItemStack(Items.DIRT, 1));
        BlockPos below = BUYER.below();
        helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));

        helper.succeedWhen(() -> {
            Container under = helper.getBlockEntity(below, HopperBlockEntity.class);
            helper.assertValueEqual(under.countItem(Items.DIAMOND) + under.countItem(Items.DIRT), 5, "items the hopper below took");
            helper.assertTrue(helper.getBlockEntity(above, HopperBlockEntity.class).isEmpty(), "the hopper above still holds its dirt");
            helper.assertTrue(buyer(helper).isEmpty(), "the Buyer still holds items");
        });
    }

    /** Sets the Offer as the Buyer's screen does. */
    private static void onlyItsTeamSets(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("buyer_setter");
        ServerPlayer stranger = players.join("buyer_setter_stranger");
        owner.setGameMode(GameType.SURVIVAL);
        stranger.setGameMode(GameType.SURVIVAL);
        TestShop.place(helper, owner, ModBlocks.BUYER_1.get(), BUYER);
        TestShop.use(helper, owner, BUYER);

        helper.assertTrue(owner.containerMenu instanceof AbstractBuyerMenu, "the owner could not open the Buyer");
        select(helper, owner, BUY_DIAMONDS);
        assertOffer(helper, buyer(helper), BUY_DIAMONDS, "the Buyer's Offer");

        TestShop.use(helper, stranger, BUYER);
        helper.assertFalse(stranger.containerMenu instanceof AbstractBuyerMenu, "a player of another team opened the Buyer");
        select(helper, stranger, BUY_BOOK);
        assertOffer(helper, buyer(helper), BUY_DIAMONDS, "the Buyer's Offer after another team's request");
    }

    /** Sets the lock as an admin's {@code /data merge block ... {lockedRecipe: true}} does. */
    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buyer_locker");
        player.setGameMode(GameType.SURVIVAL);
        TestShop.place(helper, player, ModBlocks.BUYER_1.get(), BUYER);
        TestShop.use(helper, player, BUYER);
        select(helper, player, BUY_DIAMONDS);
        buyer(helper).setLockedRecipe(true);

        select(helper, player, BUY_BOOK);
        assertOffer(helper, buyer(helper), BUY_DIAMONDS, "the locked Buyer's Offer after a survival player's request");

        player.setGameMode(GameType.CREATIVE);
        select(helper, player, BUY_BOOK);
        assertOffer(helper, buyer(helper), BUY_BOOK, "the Offer a creative player set");
        helper.assertTrue(buyer(helper).isLockedRecipe(), "setting the Offer unlocked the Buyer");
    }

    /** The saved data keys, such as {@code lockedRecipe}, must match the names that admins' block entity data uses. */
    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("buyer_saver");
        TestShop.place(helper, owner, ModBlocks.BUYER_2.get(), BUYER);
        TestShop.use(helper, owner, BUYER);
        select(helper, owner, BUY_BOOK);
        AbstractBuyerEntity buyer = buyer(helper);
        buyer.setLockedRecipe(true);
        buyer.setItem(2, new ItemStack(Items.DIAMOND, 6));

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = buyer.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(buyer.getBlockPos(), buyer.getBlockState(), saved, registries);

        helper.assertTrue(saved.getBooleanOr("lockedRecipe", false), "the lock is not saved as lockedRecipe");
        helper.assertValueEqual(saved.getStringOr("recipe", ""), BUY_BOOK.identifier().toString(), "the saved recipe");
        if (!(loaded instanceof BuyerEntity2 reloaded)) {
            throw helper.assertionException(Component.literal("the saved T2 Buyer loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), TestShop.team(helper, owner), "the reloaded owner");
        assertOffer(helper, reloaded, BUY_BOOK, "the reloaded Offer");
        helper.assertTrue(reloaded.isLockedRecipe(), "the reloaded Buyer is not locked");
        helper.assertValueEqual(reloaded.getItem(2).getItem(), Items.DIAMOND, "the reloaded contents");
        helper.assertValueEqual(reloaded.getItem(2).getCount(), 6, "the reloaded count");
    }

    private static void dropsItselfAndContents(GameTestHelper helper) {
        helper.setBlock(BUYER, ModBlocks.BUYER_3.get());
        buyer(helper).setItem(0, new ItemStack(Items.DIAMOND, 3));
        buyer(helper).setItem(4, new ItemStack(Items.DIAMOND, 2));

        helper.getLevel().destroyBlock(helper.absolutePos(BUYER), true);

        helper.assertItemEntityPresent(ModBlocks.BUYER_3.get().asItem(), BUYER, 2);
        helper.assertItemEntityCountIs(Items.DIAMOND, BUYER, 2, 5);
        helper.succeed();
    }

    /**
     * A player with {@code balance} places a Buyer on the platform, sets it to {@code offer} from its screen if
     * {@code offer} is not null, and logs out. Returns the player's team.
     */
    private static UUID setUp(GameTestHelper helper, Block block, String name, long balance, ResourceKey<Recipe<?>> offer) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            TestShop.setBalance(helper, owner, balance);
            TestShop.place(helper, owner, block, BUYER);
            if (offer != null) {
                TestShop.use(helper, owner, BUYER);
                select(helper, owner, offer);
                assertOffer(helper, buyer(helper), offer, "the Buyer's Offer");
            }
            return TestShop.team(helper, owner);
        });
    }

    /** Sends the request the Buyer's screen sends when the player picks {@code offer}. */
    private static void select(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        TestShop.send(player, new PacketSetItemBuyerRecipe(helper.absolutePos(BUYER), offer.identifier()));
    }

    /** Asserts that {@code buyer} is set to the Offer the server loaded under {@code offer}. */
    static void assertOffer(GameTestHelper helper, AbstractBuyerEntity buyer, ResourceKey<Recipe<?>> offer, String what) {
        var set = buyer.getRecipe(helper.getLevel()).orElse(null);
        helper.assertTrue(set == TestShop.recipe(helper, offer), what + " is not " + offer.identifier());
    }

    /** Asserts the Buyer's output slots hold {@code count} diamonds in all and nothing else. */
    private static void assertDiamonds(GameTestHelper helper, int count) {
        AbstractBuyerEntity buyer = buyer(helper);
        int diamonds = 0;
        for (int slot = 0; slot < buyer.getContainerSize(); slot++) {
            ItemStack stack = buyer.getItem(slot);
            if (!stack.isEmpty()) {
                helper.assertValueEqual(stack.getItem(), Items.DIAMOND, "the item in the Buyer's slot " + slot);
                diamonds += stack.getCount();
            }
        }
        helper.assertValueEqual(diamonds, count, "the diamonds in the Buyer");
    }

    private static AbstractBuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(BUYER, AbstractBuyerEntity.class);
    }
}
