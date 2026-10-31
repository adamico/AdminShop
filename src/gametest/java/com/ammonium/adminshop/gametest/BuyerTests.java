package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.BuyerBlock;
import com.ammonium.adminshop.blocks.BuyerTier;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.BuyerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.BuyerMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Buyers, as their team and a pipe see them: set to a Buy Offer from their screen, they buy one trade of it per
 * cycle of their tier, charging the owning team's Account, while it can pay and the goods fit; pipes take the goods
 * out and can put nothing in; {@code lockedRecipe} keeps survival players from changing the Offer; and they keep
 * their settings and contents when saved. Offers as in {@link ShopTradeTests}: {@code gametest/buy_diamonds} gives
 * 2 diamonds for 50, {@code gametest/buy_enchanted_book} a named book for 40.
 */
final class BuyerTests {

    private static final ResourceKey<Recipe<?>> BUY_DIAMONDS = offer("gametest/buy_diamonds");
    private static final ResourceKey<Recipe<?>> BUY_BOOK = offer("gametest/buy_enchanted_book");
    private static final long DIAMONDS_PRICE = 50;
    private static final BlockPos BUYER = new BlockPos(4, 1, 4);
    /** How many purchases the speed tests wait for. */
    private static final int CYCLES = 4;

    private BuyerTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_t1_buyer_buys_once_every_40_ticks", 400, helper -> buysAtItsTierSpeed(helper, ModBlocks.BUYER_1, "t1", 40));
        tests.test("a_t2_buyer_buys_once_every_20_ticks", 400, helper -> buysAtItsTierSpeed(helper, ModBlocks.BUYER_2, "t2", 20));
        tests.test("a_t3_buyer_buys_once_every_10_ticks", 400, helper -> buysAtItsTierSpeed(helper, ModBlocks.BUYER_3, "t3", 10));
        tests.test("a_buyer_stops_when_its_account_cannot_pay", 200, BuyerTests::stopsWhenBroke);
        tests.test("a_buyer_stops_when_its_output_is_full", 200, BuyerTests::stopsWhenFull);
        tests.test("a_buyer_without_an_offer_or_an_owner_buys_nothing", 200, BuyerTests::buysNothingUnset);
        tests.test("pipes_take_from_a_buyer_but_put_nothing_in", 20, BuyerTests::pipesOnlyTake);
        tests.test("only_a_buyers_team_sets_its_offer", 20, TestPlayers.withPlayers(BuyerTests::onlyItsTeamSets));
        tests.test("a_locked_buyers_offer_only_a_creative_player_changes", 20, TestPlayers.withPlayers(BuyerTests::locking));
        tests.test("a_buyers_settings_and_contents_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(BuyerTests::survivesSaveAndReload));
        tests.test("a_broken_buyer_drops_itself_and_its_contents", 20, BuyerTests::dropsItselfAndContents);
    }

    /** {@code cooldown} is the tier's 1.21.1 speed, as its tooltip gives it. */
    private static void buysAtItsTierSpeed(GameTestHelper helper, DeferredBlock<BuyerBlock> block, String name, int cooldown) {
        long start = 10_000;
        UUID team = setUp(helper, block, "buyer_speed_" + name, start, BUY_DIAMONDS);

        // Halfway between the CYCLES-th purchase and the next.
        helper.runAfterDelay((long) CYCLES * cooldown + cooldown / 2, () -> {
            assertBalance(helper, team, start - CYCLES * DIAMONDS_PRICE);
            assertDiamonds(helper, 2 * CYCLES);
            helper.succeed();
        });
    }

    private static void stopsWhenBroke(GameTestHelper helper) {
        // Enough for one purchase of 50, not two.
        UUID team = setUp(helper, ModBlocks.BUYER_3, "buyer_broke", 75, BUY_DIAMONDS);

        helper.runAfterDelay(5L * BuyerTier.THREE.cooldown(), () -> {
            assertBalance(helper, team, 25);
            assertDiamonds(helper, 2);
            helper.succeed();
        });
    }

    private static void stopsWhenFull(GameTestHelper helper) {
        UUID team = setUp(helper, ModBlocks.BUYER_1, "buyer_full", 1_000, BUY_DIAMONDS);
        // Room for 1 more diamond, not a trade's 2.
        buyer(helper).inventory().set(0, ItemResource.of(Items.DIAMOND), 63);

        helper.runAfterDelay(3L * BuyerTier.ONE.cooldown(), () -> {
            assertBalance(helper, team, 1_000);
            assertDiamonds(helper, 63);
            helper.succeed();
        });
    }

    private static void buysNothingUnset(GameTestHelper helper) {
        UUID team = setUp(helper, ModBlocks.BUYER_3, "buyer_no_offer", 1_000, null);
        // Placed by no player, as by a command, and set to an Offer: nobody's Account pays.
        BlockPos unowned = BUYER.east(2);
        helper.setBlock(unowned, ModBlocks.BUYER_3.get());
        helper.getBlockEntity(unowned, BuyerEntity.class).setOffer(BUY_DIAMONDS, false);

        helper.runAfterDelay(3L * BuyerTier.THREE.cooldown(), () -> {
            assertBalance(helper, team, 1_000);
            assertDiamonds(helper, 0);
            helper.assertValueEqual(helper.getBlockEntity(unowned, BuyerEntity.class).inventory().getAmountAsInt(0), 0,
                    "what an unowned Buyer bought");
            helper.succeed();
        });
    }

    private static void pipesOnlyTake(GameTestHelper helper) {
        helper.setBlock(BUYER, ModBlocks.BUYER_2.get());
        buyer(helper).inventory().set(1, ItemResource.of(Items.DIAMOND), 4);
        ResourceHandler<ItemResource> pipe = handler(helper);

        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(pipe, ItemResource.of(Items.DIRT), 64, transaction);
            int insertedSame = ResourceHandlerUtil.insertStacking(pipe, ItemResource.of(Items.DIAMOND), 1, transaction);
            int extracted = pipe.extract(ItemResource.of(Items.DIAMOND), 64, transaction);
            transaction.commit();

            helper.assertValueEqual(inserted, 0, "dirt piped into the Buyer");
            helper.assertValueEqual(insertedSame, 0, "diamonds piped into the Buyer");
            helper.assertValueEqual(extracted, 4, "diamonds piped out of the Buyer");
        }
        assertDiamonds(helper, 0);
        helper.succeed();
    }

    private static void onlyItsTeamSets(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("buyer_setter");
        ServerPlayer stranger = players.join("buyer_setter_stranger");
        owner.setGameMode(GameType.SURVIVAL);
        stranger.setGameMode(GameType.SURVIVAL);
        place(helper, owner, ModBlocks.BUYER_1);
        use(helper, owner);

        helper.assertTrue(BuyerMenu.select(owner, BUY_DIAMONDS), "the owner could not set the Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_DIAMONDS, "the Buyer's Offer");
        helper.assertValueEqual(((BuyerMenu) owner.containerMenu).target().getItem(), Items.DIAMOND, "the screen's goods");

        use(helper, stranger);
        helper.assertFalse(stranger.containerMenu instanceof BuyerMenu, "a player of another team opened the Buyer");
        helper.assertFalse(BuyerMenu.select(stranger, BUY_BOOK), "a player of another team set the Buyer's Offer");
        helper.assertFalse(BuyerMenu.select(owner, offer("gametest/sell_wheat")), "the Buyer was set to a Sell Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_DIAMONDS, "the Buyer's Offer after refused requests");
    }

    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buyer_locker");
        player.setGameMode(GameType.SURVIVAL);
        place(helper, player, ModBlocks.BUYER_1);
        use(helper, player);
        BuyerMenu.select(player, BUY_DIAMONDS);
        // As an admin's `/data merge block ... {lockedRecipe: true}` does.
        buyer(helper).setLocked(true);

        helper.assertTrue(((BuyerMenu) player.containerMenu).isLocked(), "the menu doesn't show the lock");
        helper.assertFalse(BuyerMenu.select(player, BUY_BOOK), "a survival player changed a locked Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_DIAMONDS, "the locked Buyer's Offer");

        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(BuyerMenu.select(player, BUY_BOOK), "a creative player could not change a locked Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_BOOK, "the Offer a creative player set");
        helper.assertTrue(buyer(helper).isLocked(), "setting the Offer unlocked the Buyer");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("buyer_saver");
        place(helper, owner, ModBlocks.BUYER_2);
        use(helper, owner);
        BuyerMenu.select(owner, BUY_BOOK);
        BuyerEntity buyer = buyer(helper);
        buyer.setLocked(true);
        buyer.inventory().set(2, ItemResource.of(Items.DIAMOND), 6);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = buyer.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(buyer.getBlockPos(), buyer.getBlockState(), saved, registries);

        // The 1.21.1 names, which admins' block entity data uses.
        helper.assertTrue(saved.getBooleanOr("lockedRecipe", false), "the lock is not saved as lockedRecipe");
        helper.assertValueEqual(saved.getStringOr("recipe", ""), BUY_BOOK.identifier().toString(), "the saved recipe");
        if (!(loaded instanceof BuyerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Buyer loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.offer(), BUY_BOOK, "the reloaded Offer");
        helper.assertTrue(reloaded.isLocked(), "the reloaded Buyer is not locked");
        helper.assertValueEqual(reloaded.tier(), BuyerTier.TWO, "the reloaded tier");
        helper.assertValueEqual(reloaded.inventory().getResource(2).getItem(), Items.DIAMOND, "the reloaded contents");
        helper.assertValueEqual(reloaded.inventory().getAmountAsInt(2), 6, "the reloaded count");
    }

    private static void dropsItselfAndContents(GameTestHelper helper) {
        helper.setBlock(BUYER, ModBlocks.BUYER_3.get());
        buyer(helper).inventory().set(0, ItemResource.of(Items.DIAMOND), 3);
        buyer(helper).inventory().set(4, ItemResource.of(Items.DIAMOND), 2);

        helper.getLevel().destroyBlock(helper.absolutePos(BUYER), true);

        helper.assertItemEntityPresent(ModBlocks.BUYER_3.get().asItem(), BUYER, 2);
        helper.assertItemEntityCountIs(Items.DIAMOND, BUYER, 2, 5);
        helper.succeed();
    }

    /**
     * A player with {@code balance} places a Buyer on the platform, sets it to {@code offer} (if any) from its screen
     * and logs out; returns their team.
     */
    private static UUID setUp(GameTestHelper helper, DeferredBlock<BuyerBlock> block, String name, long balance,
                              ResourceKey<Recipe<?>> offer) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            setBalance(helper, owner, balance);
            place(helper, owner, block);
            if (offer != null) {
                use(helper, owner);
                helper.assertTrue(BuyerMenu.select(owner, offer), "setting the Buyer's Offer failed");
            }
            return team(helper, owner);
        });
    }

    /** {@code player} places a Buyer on the platform, as from their hand. */
    private static void place(GameTestHelper helper, ServerPlayer player, DeferredBlock<BuyerBlock> block) {
        ItemStack stack = new ItemStack(block.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(BUYER.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertTrue(result.consumesAction(), "placing the Buyer failed: " + result);
        helper.assertBlockPresent(block.get(), BUYER);
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(BUYER);
        helper.getBlockState(BUYER).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    /** Asserts the Buyer's output slots hold {@code count} diamonds in all and nothing else. */
    private static void assertDiamonds(GameTestHelper helper, int count) {
        var inventory = buyer(helper).inventory();
        int diamonds = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.stack(slot);
            if (!stack.isEmpty()) {
                helper.assertValueEqual(stack.getItem(), Items.DIAMOND, "the item in the Buyer's slot " + slot);
                diamonds += stack.getCount();
            }
        }
        helper.assertValueEqual(diamonds, count, "the diamonds in the Buyer");
    }

    private static ResourceHandler<ItemResource> handler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BUYER), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the Buyer exposes no item handler"));
        }
        return handler;
    }

    private static BuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(BUYER, BuyerEntity.class);
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
