package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.SellerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Seller, as a pipe and its team see it: items piped in through the item capability that a Sell Offer
 * takes are sold into the owning team's Account, one trade every {@link SellerEntity#TICK_COOLDOWN} ticks; it
 * takes nothing it can't sell; it belongs to the team that placed it, or to the first team to use it; and
 * it keeps its owner, lock and contents when saved. Offers as in {@link ShopTradeTests}: {@code gametest/sell_wheat}
 * buys 10 wheat for 7, {@code gametest/sell_wool} 4 of any wool for 3.
 */
final class SellerTests {

    private static final ResourceKey<Recipe<?>> SELL_WHEAT = offer("gametest/sell_wheat");
    private static final ResourceKey<Recipe<?>> SELL_WOOL = offer("gametest/sell_wool");
    private static final BlockPos SELLER = new BlockPos(4, 1, 4);

    private SellerTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_seller_sells_what_is_piped_in", 100, SellerTests::sellsWhatIsPipedIn);
        tests.test("a_seller_sells_through_a_tag_sell_offer", 100, SellerTests::sellsThroughATag);
        tests.test("a_seller_takes_nothing_it_cannot_sell", 20, TestPlayers.withPlayers(SellerTests::takesNothingUnsellable));
        tests.test("a_seller_keeps_too_few_items_for_a_trade", 100, SellerTests::keepsTooFew);
        tests.test("a_seller_belongs_to_the_team_that_placed_it", 20, TestPlayers.withPlayers(SellerTests::belongsToPlacer));
        tests.test("an_unowned_seller_sells_once_claimed_on_first_use", 100,
                SellerTests::claimedOnFirstUse);
        tests.test("a_creative_player_locks_a_seller_to_one_offer", 20, TestPlayers.withPlayers(SellerTests::locking));
        tests.test("a_sellers_owner_lock_and_contents_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(SellerTests::survivesSaveAndReload));
        tests.test("a_broken_seller_drops_itself_and_its_contents", 20, SellerTests::dropsItselfAndContents);
    }

    private static void sellsWhatIsPipedIn(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_owner");

        int inserted = insert(helper, new ItemStack(Items.WHEAT, 20));

        helper.assertValueEqual(inserted, 20, "wheat piped in");
        // One trade per cycle: after the first cycle, half is sold.
        helper.runAfterDelay(SellerEntity.TICK_COOLDOWN + 10, () -> {
            assertBalance(helper, team, 7);
            assertContents(helper, Items.WHEAT, 10);
        });
        helper.succeedWhen(() -> {
            assertBalance(helper, team, 14);
            assertContents(helper, Items.AIR, 0);
        });
    }

    private static void sellsThroughATag(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_tag_owner");

        int inserted = insert(helper, new ItemStack(Items.RED_WOOL, 8));

        helper.assertValueEqual(inserted, 8, "red wool piped in");
        helper.succeedWhen(() -> {
            assertBalance(helper, team, 6);
            assertContents(helper, Items.AIR, 0);
        });
    }

    private static void takesNothingUnsellable(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("seller_dirt_owner");
        place(helper, owner);

        int inserted = insert(helper, new ItemStack(Items.DIRT, 64));

        helper.assertValueEqual(inserted, 0, "dirt piped in");
        assertContents(helper, Items.AIR, 0);
    }

    private static void keepsTooFew(GameTestHelper helper) {
        UUID team = placedBy(helper, "seller_few_owner");
        insert(helper, new ItemStack(Items.WHEAT, 5));

        helper.runAfterDelay(2L * SellerEntity.TICK_COOLDOWN + 5, () -> {
            assertBalance(helper, team, 0);
            assertContents(helper, Items.WHEAT, 5);
            helper.succeed();
        });
    }

    private static void belongsToPlacer(GameTestHelper helper, TestPlayers players) throws Exception {
        ServerPlayer owner = players.join("seller_party_owner");
        ServerPlayer member = players.join("seller_party_member");
        ServerPlayer stranger = players.join("seller_stranger");
        UUID party = AccountTests.joinParty(owner, member).getId();
        place(helper, owner);

        helper.assertValueEqual(seller(helper).getTeamId(), party, "the Seller's owner");
        use(helper, stranger);
        helper.assertFalse(stranger.containerMenu instanceof SellerMenu, "a player of another team opened the Seller");
        helper.assertValueEqual(seller(helper).getTeamId(), party, "the Seller's owner after another team used it");
        use(helper, member);
        helper.assertTrue(member.containerMenu instanceof SellerMenu, "a member of the owning team could not open the Seller");
    }

    private static void claimedOnFirstUse(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer claimer = players.join("seller_claimer");
            setBalance(helper, claimer, 0);
            return team(helper, claimer);
        });
        // Placed by no player, as by a command or another mod's machine.
        helper.setBlock(SELLER, ModBlocks.SELLER.get());
        insert(helper, new ItemStack(Items.WHEAT, 10));

        helper.runAfterDelay(SellerEntity.TICK_COOLDOWN + 5, () -> {
            helper.assertTrue(seller(helper).getTeamId() == null, "a Seller placed by no player has an owner");
            assertContents(helper, Items.WHEAT, 10);

            boolean opened = TestPlayers.session(helper, players -> {
                ServerPlayer claimer = players.join("seller_claimer");
                use(helper, claimer);
                return claimer.containerMenu instanceof SellerMenu;
            });

            helper.assertTrue(opened, "claiming the Seller did not open it");
            helper.assertValueEqual(seller(helper).getTeamId(), team, "the owner after the first use");
        });
        helper.succeedWhen(() -> {
            assertBalance(helper, team, 7);
            assertContents(helper, Items.AIR, 0);
        });
    }

    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("seller_locker");
        player.setGameMode(GameType.CREATIVE);
        place(helper, player);
        use(helper, player);

        boolean locked = SellerMenu.lock(player, SELL_WHEAT);

        helper.assertTrue(locked, "a creative player could not lock the Seller");
        helper.assertValueEqual(seller(helper).lockedOffer(), SELL_WHEAT, "the Seller's locked Offer");
        helper.assertValueEqual(insert(helper, new ItemStack(Items.WHITE_WOOL, 4)), 0, "wool piped into a wheat-locked Seller");
        helper.assertValueEqual(insert(helper, new ItemStack(Items.WHEAT, 10)), 10, "wheat piped into a wheat-locked Seller");

        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(SellerMenu.lock(player, SELL_WOOL), "a survival player locked the Seller");
        helper.assertValueEqual(seller(helper).lockedOffer(), SELL_WHEAT, "the locked Offer after a survival player's try");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("seller_saver");
        owner.setGameMode(GameType.CREATIVE);
        place(helper, owner);
        use(helper, owner);
        SellerMenu.lock(owner, SELL_WHEAT);
        insert(helper, new ItemStack(Items.WHEAT, 5));
        SellerEntity seller = seller(helper);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = seller.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(seller.getBlockPos(), seller.getBlockState(), saved, registries);

        if (!(loaded instanceof SellerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Seller loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.lockedOffer(), SELL_WHEAT, "the reloaded locked Offer");
        ResourceHandler<ItemResource> contents = reloaded.inventory();
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

    /** A player with no money places a Seller on the platform and logs out; returns their team. */
    private static UUID placedBy(GameTestHelper helper, String name) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            setBalance(helper, owner, 0);
            place(helper, owner);
            return team(helper, owner);
        });
    }

    /** {@code player} places a Seller on the platform, as from their hand. */
    private static void place(GameTestHelper helper, ServerPlayer player) {
        ItemStack stack = new ItemStack(ModBlocks.SELLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(SELLER.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertTrue(result.consumesAction(), "placing the Seller failed: " + result);
        helper.assertBlockPresent(ModBlocks.SELLER.get(), SELLER);
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(SELLER);
        helper.getBlockState(SELLER).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    /** Inserts {@code stack} into the Seller through its item capability, as a pipe would; returns how many went in. */
    private static int insert(GameTestHelper helper, ItemStack stack) {
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

    private static ResourceHandler<ItemResource> handler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(SELLER), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the Seller exposes no item handler"));
        }
        return handler;
    }

    private static SellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(SELLER, SellerEntity.class);
    }

    private static UUID team(GameTestHelper helper, ServerPlayer player) {
        return MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).teamId();
    }

    private static void setBalance(GameTestHelper helper, ServerPlayer player, long balance) {
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        var account = accounts.getPlayerAccount(player);
        accounts.removeMoney(account.teamId(), account.balance());
        accounts.addMoney(account.teamId(), balance);
    }

    private static void assertBalance(GameTestHelper helper, UUID team, long expected) {
        var account = MoneyHelper.get(helper.getLevel()).getAccountById(team);
        helper.assertTrue(account != null, "the team has no Account");
        helper.assertValueEqual(account.balance(), expected, "the team's Balance");
    }

    private static ResourceKey<Recipe<?>> offer(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(AdminShop.MODID, path));
    }
}
