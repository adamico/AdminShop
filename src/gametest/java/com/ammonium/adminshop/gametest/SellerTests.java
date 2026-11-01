package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.ammonium.adminshop.network.PacketSetSellerRecipe;
import com.ammonium.adminshop.screen.SellerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests the Seller. Items piped in through the item capability are sold into the owning team's Account if a Sell
 * Offer takes them, one trade per cycle. The Seller accepts nothing it cannot sell. It belongs to the team that
 * placed it, or to the first team to use it, and keeps its owner, lock and contents when saved. The Offers are those
 * in {@link ShopTradeTests}. {@code gametest/sell_wheat} buys 10 wheat for 7, and {@code gametest/sell_named_paper}
 * buys 2 paper named "Ledger" for 9.
 */
final class SellerTests {

    private static final ResourceKey<Recipe<?>> SELL_WHEAT = ShopTradeTests.SELL_WHEAT;
    private static final ResourceKey<Recipe<?>> SELL_WOOL = ShopTradeTests.SELL_WOOL;
    private static final BlockPos SELLER = new BlockPos(4, 1, 4);
    /** The ticks the Seller takes for one trade. */
    private static final int CYCLE = SellerEntity.TICK_COOLDOWN + 1;

    private SellerTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_seller_sells_what_is_piped_in", 100, SellerTests::sellsWhatIsPipedIn);
        tests.test("a_seller_sells_once_every_21_ticks", 250, SellerTests::sellsOnceEveryCycle);
        tests.test("a_seller_sells_through_a_tag_sell_offer", 100, SellerTests::sellsThroughATag);
        tests.test("a_seller_takes_nothing_it_cannot_sell", 20, TestPlayers.withPlayers(SellerTests::takesNothingUnsellable));
        tests.test("a_seller_keeps_too_few_items_for_a_trade", 100, SellerTests::keepsTooFew);
        tests.test("a_seller_belongs_to_the_team_that_placed_it", 20, TestPlayers.withPlayers(SellerTests::belongsToPlacer));
        tests.test("an_unowned_seller_sells_once_claimed_on_first_use", 100, SellerTests::claimedOnFirstUse);
        tests.test("a_creative_player_locks_a_seller_to_one_offer", 20, TestPlayers.withPlayers(SellerTests::locking));
        tests.test("a_sellers_owner_lock_and_contents_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(SellerTests::survivesSaveAndReload));
        tests.test("a_broken_seller_drops_itself_and_its_contents", 20, SellerTests::dropsItselfAndContents);
    }

    /** The Seller makes one trade per cycle, so half the wheat is sold after the first cycle. */
    private static void sellsWhatIsPipedIn(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_owner");

        int inserted = insert(helper, new ItemStack(Items.WHEAT, 20));

        helper.assertValueEqual(inserted, 20, "wheat piped in");
        helper.runAfterDelay(CYCLE + 9, () -> {
            TestShop.assertBalance(helper, team, 7);
            assertContents(helper, Items.WHEAT, 10);
        });
        helper.succeedWhen(() -> {
            TestShop.assertBalance(helper, team, 14);
            assertContents(helper, Items.AIR, 0);
        });
    }

    /**
     * Known bug (adamico/AdminShop#20): the Seller sells once every 21 ticks, not every 20 as its tooltip says. After
     * 205 ticks it has made 9 trades, where it should have made 10.
     */
    private static void sellsOnceEveryCycle(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_speed");
        ItemStack ledgers = new ItemStack(Items.PAPER, 64);
        ledgers.set(DataComponents.CUSTOM_NAME, Component.literal("Ledger"));
        helper.assertValueEqual(insert(helper, ledgers), 64, "named paper piped in");

        helper.runAfterDelay(10L * SellerEntity.TICK_COOLDOWN + 5, () -> {
            TestShop.assertBalance(helper, team, 9 * 9);
            assertContents(helper, Items.PAPER, 64 - 9 * 2);
            helper.succeed();
        });
    }

    /**
     * The Seller sells through the first Sell Offer that takes its item. For wool that is the shipped
     * {@code example_sell_item_tag}, which takes 16 of any wool for 100.
     */
    private static void sellsThroughATag(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_tag_owner");

        int inserted = insert(helper, new ItemStack(Items.RED_WOOL, 16));

        helper.assertValueEqual(inserted, 16, "red wool piped in");
        helper.succeedWhen(() -> {
            TestShop.assertBalance(helper, team, 100);
            assertContents(helper, Items.AIR, 0);
        });
    }

    private static void takesNothingUnsellable(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("seller_dirt_owner");
        TestShop.place(helper, owner, ModBlocks.SELLER.get(), SELLER);

        int inserted = insert(helper, new ItemStack(Items.DIRT, 64));

        helper.assertValueEqual(inserted, 0, "dirt piped in");
        assertContents(helper, Items.AIR, 0);
    }

    private static void keepsTooFew(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_few_owner");
        insert(helper, new ItemStack(Items.WHEAT, 5));

        helper.runAfterDelay(2L * CYCLE + 5, () -> {
            TestShop.assertBalance(helper, team, 0);
            assertContents(helper, Items.WHEAT, 5);
            helper.succeed();
        });
    }

    private static void belongsToPlacer(GameTestHelper helper, TestPlayers players) throws Exception {
        ServerPlayer owner = players.join("seller_party_owner");
        ServerPlayer member = players.join("seller_party_member");
        ServerPlayer stranger = players.join("seller_stranger");
        UUID party = AccountTests.joinParty(owner, member).getId();
        TestShop.place(helper, owner, ModBlocks.SELLER.get(), SELLER);

        helper.assertValueEqual(seller(helper).getTeamId(), party, "the Seller's owner");
        TestShop.use(helper, stranger, SELLER);
        helper.assertFalse(stranger.containerMenu instanceof SellerMenu, "a player of another team opened the Seller");
        helper.assertValueEqual(seller(helper).getTeamId(), party, "the Seller's owner after another team used it");
        TestShop.use(helper, member, SELLER);
        helper.assertTrue(member.containerMenu instanceof SellerMenu, "a member of the owning team could not open the Seller");
    }

    /** The Seller is placed by no player, as with a command or another mod's machine. */
    private static void claimedOnFirstUse(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer claimer = players.join("seller_claimer");
            TestShop.setBalance(helper, claimer, 0);
            return TestShop.team(helper, claimer);
        });
        helper.setBlock(SELLER, ModBlocks.SELLER.get());
        insert(helper, new ItemStack(Items.WHEAT, 10));

        helper.runAfterDelay(CYCLE + 5, () -> {
            helper.assertTrue(seller(helper).getTeamId() == null, "a Seller placed by no player has an owner");
            assertContents(helper, Items.WHEAT, 10);

            boolean opened = TestPlayers.session(helper, players -> {
                ServerPlayer claimer = players.join("seller_claimer");
                TestShop.use(helper, claimer, SELLER);
                return claimer.containerMenu instanceof SellerMenu;
            });

            helper.assertTrue(opened, "claiming the Seller did not open it");
            helper.assertValueEqual(seller(helper).getTeamId(), team, "the owner after the first use");
        });
        helper.succeedWhen(() -> {
            TestShop.assertBalance(helper, team, 7);
            assertContents(helper, Items.AIR, 0);
        });
    }

    /** Locks the Seller as its screen does when a creative player clicks an item into it. */
    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("seller_locker");
        player.setGameMode(GameType.CREATIVE);
        TestShop.place(helper, player, ModBlocks.SELLER.get(), SELLER);
        TestShop.use(helper, player, SELLER);

        lock(helper, player, SELL_WHEAT);

        helper.assertValueEqual(seller(helper).getLockedRecipeId(), SELL_WHEAT.identifier(), "the Seller's locked Offer");
        helper.assertValueEqual(insert(helper, new ItemStack(Items.WHITE_WOOL, 4)), 0, "wool piped into a wheat-locked Seller");
        helper.assertValueEqual(insert(helper, new ItemStack(Items.WHEAT, 10)), 10, "wheat piped into a wheat-locked Seller");

        player.setGameMode(GameType.SURVIVAL);
        lock(helper, player, SELL_WOOL);
        helper.assertValueEqual(seller(helper).getLockedRecipeId(), SELL_WHEAT.identifier(), "the locked Offer after a survival player's try");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("seller_saver");
        owner.setGameMode(GameType.CREATIVE);
        TestShop.place(helper, owner, ModBlocks.SELLER.get(), SELLER);
        TestShop.use(helper, owner, SELLER);
        lock(helper, owner, SELL_WHEAT);
        insert(helper, new ItemStack(Items.WHEAT, 5));
        SellerEntity seller = seller(helper);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = seller.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(seller.getBlockPos(), seller.getBlockState(), saved, registries);

        if (!(loaded instanceof SellerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Seller loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), TestShop.team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.getLockedRecipeId(), SELL_WHEAT.identifier(), "the reloaded locked Offer");
        ResourceHandler<ItemResource> contents = reloaded.getInventory();
        helper.assertValueEqual(contents.getResource(0).getItem(), Items.WHEAT, "the reloaded contents");
        helper.assertValueEqual(contents.getAmountAsInt(0), 5, "the reloaded count");
    }

    private static void dropsItselfAndContents(GameTestHelper helper) {
        helper.setBlock(SELLER, ModBlocks.SELLER.get());
        insert(helper, new ItemStack(Items.WHEAT, 5));

        helper.getLevel().destroyBlock(helper.absolutePos(SELLER), true);

        helper.assertItemEntityPresent(ModBlocks.SELLER.get().asItem(), SELLER, 2);
        helper.assertItemEntityCountIs(Items.WHEAT, SELLER, 2, 5);
        helper.succeed();
    }

    /** A player with no money places a Seller on the platform and logs out. Returns their team. */
    private static UUID placedBy(GameTestHelper helper, String name) {
        return TestShop.placedBy(helper, name, 0, ModBlocks.SELLER.get(), SELLER);
    }

    /** Sends the request the Seller's screen sends to lock it to {@code offer}. */
    static void lock(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        TestShop.send(player, new PacketSetSellerRecipe(helper.absolutePos(SELLER), offer.identifier()));
    }

    /** Inserts {@code stack} into the Seller through its item capability, as a pipe would. Returns how many went in. */
    static int insert(GameTestHelper helper, ItemStack stack) {
        ResourceHandler<ItemResource> handler = handler(helper);
        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static void assertContents(GameTestHelper helper, Item item, int count) {
        ResourceHandler<ItemResource> handler = handler(helper);
        int amount = handler.getAmountAsInt(0);
        Item held = amount == 0 ? Items.AIR : handler.getResource(0).getItem();
        helper.assertValueEqual(held, item, "the item in the Seller");
        helper.assertValueEqual(amount, count, "the count in the Seller");
    }

    static ResourceHandler<ItemResource> handler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(SELLER), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the Seller exposes no item handler"));
        }
        return handler;
    }

    private static SellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(SELLER, SellerEntity.class);
    }
}
