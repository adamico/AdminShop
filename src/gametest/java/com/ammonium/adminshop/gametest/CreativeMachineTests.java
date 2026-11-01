package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.AbstractBuyerEntity;
import com.ammonium.adminshop.blocks.entity.CreativeBuyerEntity;
import com.ammonium.adminshop.blocks.entity.CreativeSellerEntity;
import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.ammonium.adminshop.network.PacketSetItemBuyerRecipe;
import com.ammonium.adminshop.network.PacketSetSellerRecipe;
import com.ammonium.adminshop.screen.AbstractBuyerMenu;
import com.ammonium.adminshop.screen.SellerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests the Creative Buyer and the Creative Seller. A placed one stays unclaimed and open to the placer, so a
 * creative player can set its Offer. The first player to use it after that claims it for their team. They trade
 * Offers gated by a Permit their team does not hold. The Creative Buyer works as a five-slot Buyer locked to the
 * Offer it was given, and the Creative Seller works as a Seller. Nobody can break them, and no recipe crafts them.
 * The Offers come from {@link PermitTests}. {@code gametest/buy_gated_emeralds} gives 1 emerald for 10, and
 * {@code gametest/sell_gated_flint} buys 4 flint for 5. Both are gated by {@link PermitTests#GATE}.
 */
final class CreativeMachineTests {

    private static final ResourceKey<Recipe<?>> BUY_EMERALDS = PermitTests.BUY_EMERALDS;
    private static final ResourceKey<Recipe<?>> BUY_DIAMONDS = ShopTradeTests.BUY_DIAMONDS;
    private static final ResourceKey<Recipe<?>> SELL_FLINT = PermitTests.SELL_FLINT;
    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** The Creative Buyer's cooldown as its tooltip and the T3 Buyer's give it, in ticks. */
    private static final int BUYER_COOLDOWN = 10;
    /** The ticks the Creative Seller takes for one trade, the same as a Seller. */
    private static final int SELLER_CYCLE = SellerEntity.TICK_COOLDOWN + 1;

    private CreativeMachineTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_placed_creative_buyer_stays_unclaimed_and_opens_for_its_placer", 20,
                TestPlayers.withPlayers(CreativeMachineTests::buyerOpensForPlacer));
        tests.test("a_placed_creative_seller_stays_unclaimed_and_opens_for_its_placer", 20,
                TestPlayers.withPlayers(CreativeMachineTests::sellerOpensForPlacer));
        tests.test("a_claimed_creative_buyer_buys_a_gated_offer_once_every_11_ticks", 200,
                CreativeMachineTests::buyerBuysOnceClaimed);
        tests.test("a_claimed_creative_seller_sells_to_a_gated_offer", 200,
                CreativeMachineTests::sellerSellsOnceClaimed);
        tests.test("a_creative_buyers_offer_only_a_creative_player_changes", 20,
                TestPlayers.withPlayers(CreativeMachineTests::buyerLocked));
        tests.test("a_claimed_creative_machine_opens_only_for_its_team", 20,
                TestPlayers.withPlayers(CreativeMachineTests::onlyItsTeamOpens));
        tests.test("creative_machines_keep_their_settings_through_a_save_and_reload", 20,
                TestPlayers.withPlayers(CreativeMachineTests::survivesSaveAndReload));
        tests.test("creative_machines_cannot_be_broken_or_blown_up", 20, CreativeMachineTests::unbreakable);
        tests.test("no_recipe_crafts_a_creative_machine", 20, CreativeMachineTests::uncraftable);
    }

    private static void buyerOpensForPlacer(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_buyer_placer");
        admin.setGameMode(GameType.CREATIVE);

        TestShop.place(helper, admin, ModBlocks.CREATIVE_BUYER.get(), MACHINE);

        helper.assertTrue(buyer(helper).getTeamId() == null, "the placed Creative Buyer has an owner");
        helper.assertTrue(admin.containerMenu instanceof AbstractBuyerMenu, "placing the Creative Buyer did not open it");
        select(helper, admin, BUY_EMERALDS);
        BuyerTests.assertOffer(helper, buyer(helper), BUY_EMERALDS, "the Creative Buyer's Offer");
        helper.assertTrue(buyer(helper).getTeamId() == null, "setting the Offer claimed the Creative Buyer");
    }

    private static void sellerOpensForPlacer(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_seller_placer");
        admin.setGameMode(GameType.CREATIVE);

        TestShop.place(helper, admin, ModBlocks.CREATIVE_SELLER.get(), MACHINE);

        helper.assertTrue(seller(helper).getTeamId() == null, "the placed Creative Seller has an owner");
        helper.assertTrue(admin.containerMenu instanceof SellerMenu, "placing the Creative Seller did not open it");
        lock(helper, admin, SELL_FLINT, MACHINE);
        helper.assertValueEqual(seller(helper).getLockedRecipeId(), SELL_FLINT.identifier(), "the Creative Seller's locked Offer");
        helper.assertTrue(seller(helper).getTeamId() == null, "locking claimed the Creative Seller");
    }

    /**
     * Known bug (adamico/AdminShop#20): the Creative Buyer buys once every 11 ticks, not every 10 as the T3 Buyer
     * it matches. After 105 ticks it has bought 9 emeralds, where it should have bought 10. The team holds no Permit
     * for the Offer. A hopper below takes the emeralds out.
     */
    private static void buyerBuysOnceClaimed(GameTestHelper helper) {
        long start = 1_000;
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer admin = players.join("creative_buyer_admin");
            admin.setGameMode(GameType.CREATIVE);
            TestShop.place(helper, admin, ModBlocks.CREATIVE_BUYER.get(), MACHINE);
            select(helper, admin, BUY_EMERALDS);
            admin.closeContainer();

            ServerPlayer claimer = players.join("creative_buyer_claimer");
            claimer.setGameMode(GameType.SURVIVAL);
            TestShop.setBalance(helper, claimer, start);
            TestShop.use(helper, claimer, MACHINE);
            helper.assertTrue(claimer.containerMenu instanceof AbstractBuyerMenu, "claiming the Creative Buyer did not open it");
            return TestShop.team(helper, claimer);
        });

        helper.assertValueEqual(buyer(helper).getTeamId(), team, "the Creative Buyer's owner after the first use");
        helper.runAfterDelay(10L * BUYER_COOLDOWN + 5, () -> {
            TestShop.assertBalance(helper, team, start - 9 * 10);
            helper.assertValueEqual(buyer(helper).countItem(Items.EMERALD), 9, "emeralds in the Creative Buyer");
            BlockPos below = MACHINE.below();
            helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        });
        helper.succeedWhen(() -> helper.assertTrue(
                helper.getBlockEntity(MACHINE.below(), HopperBlockEntity.class).countItem(Items.EMERALD) >= 9,
                "the hopper below took too few emeralds out of the Creative Buyer"));
    }

    /** The Balance is checked halfway between the second sale and the third. The team holds no Permit for the Offer. */
    private static void sellerSellsOnceClaimed(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer admin = players.join("creative_seller_admin");
            admin.setGameMode(GameType.CREATIVE);
            TestShop.place(helper, admin, ModBlocks.CREATIVE_SELLER.get(), MACHINE);
            admin.closeContainer();

            ServerPlayer claimer = players.join("creative_seller_claimer");
            claimer.setGameMode(GameType.SURVIVAL);
            TestShop.setBalance(helper, claimer, 0);
            TestShop.use(helper, claimer, MACHINE);
            helper.assertTrue(claimer.containerMenu instanceof SellerMenu, "claiming the Creative Seller did not open it");
            return TestShop.team(helper, claimer);
        });
        helper.assertValueEqual(seller(helper).getTeamId(), team, "the Creative Seller's owner after the first use");

        int inserted;
        try (var transaction = Transaction.openRoot()) {
            var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE), null);
            helper.assertTrue(handler != null, "the Creative Seller exposes no item handler");
            inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(Items.FLINT), 12, transaction);
            transaction.commit();
        }

        helper.assertValueEqual(inserted, 12, "flint piped into the Creative Seller");
        helper.runAfterDelay(2L * SELLER_CYCLE + SELLER_CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 10);
            helper.assertValueEqual(seller(helper).getInventory().getAmountAsInt(0), 4, "flint left in the Creative Seller");
            helper.succeed();
        });
    }

    private static void buyerLocked(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_buyer_locker");
        admin.setGameMode(GameType.CREATIVE);
        TestShop.place(helper, admin, ModBlocks.CREATIVE_BUYER.get(), MACHINE);
        select(helper, admin, BUY_EMERALDS);
        admin.closeContainer();
        ServerPlayer claimer = players.join("creative_buyer_locked_claimer");
        claimer.setGameMode(GameType.SURVIVAL);
        TestShop.use(helper, claimer, MACHINE);

        helper.assertTrue(buyer(helper).isLockedRecipe(), "the Creative Buyer is not locked");
        select(helper, claimer, BUY_DIAMONDS);
        BuyerTests.assertOffer(helper, buyer(helper), BUY_EMERALDS, "the Creative Buyer's Offer after its survival owner's request");

        claimer.setGameMode(GameType.CREATIVE);
        select(helper, claimer, BUY_DIAMONDS);
        BuyerTests.assertOffer(helper, buyer(helper), BUY_DIAMONDS, "the Offer a creative player set");
    }

    private static void onlyItsTeamOpens(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_team_admin");
        ServerPlayer owner = players.join("creative_team_owner");
        ServerPlayer stranger = players.join("creative_team_stranger");
        admin.setGameMode(GameType.CREATIVE);
        TestShop.place(helper, admin, ModBlocks.CREATIVE_SELLER.get(), MACHINE);
        admin.closeContainer();

        TestShop.use(helper, owner, MACHINE);
        owner.closeContainer();
        TestShop.use(helper, stranger, MACHINE);
        TestShop.use(helper, admin, MACHINE);

        helper.assertValueEqual(seller(helper).getTeamId(), TestShop.team(helper, owner), "the Creative Seller's owner");
        helper.assertFalse(stranger.containerMenu instanceof SellerMenu, "a player of another team opened the Creative Seller");
        helper.assertFalse(admin.containerMenu instanceof SellerMenu, "its placer, of another team, opened the claimed Creative Seller");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_saver");
        admin.setGameMode(GameType.CREATIVE);
        TestShop.place(helper, admin, ModBlocks.CREATIVE_BUYER.get(), MACHINE);
        select(helper, admin, BUY_EMERALDS);
        BlockPos sellerPos = MACHINE.east(2);
        TestShop.place(helper, admin, ModBlocks.CREATIVE_SELLER.get(), sellerPos);
        lock(helper, admin, SELL_FLINT, sellerPos);

        BlockEntity loadedBuyer = reload(helper, buyer(helper));
        BlockEntity loadedSeller = reload(helper, helper.getBlockEntity(sellerPos, SellerEntity.class));

        if (!(loadedBuyer instanceof CreativeBuyerEntity reloadedBuyer)) {
            throw helper.assertionException(Component.literal("the saved Creative Buyer loaded back as " + loadedBuyer));
        }
        helper.assertTrue(reloadedBuyer.getTeamId() == null, "the reloaded unclaimed Creative Buyer has an owner");
        BuyerTests.assertOffer(helper, reloadedBuyer, BUY_EMERALDS, "the reloaded Offer");
        helper.assertTrue(reloadedBuyer.isLockedRecipe(), "the reloaded Creative Buyer is not locked");
        if (!(loadedSeller instanceof CreativeSellerEntity reloadedSeller)) {
            throw helper.assertionException(Component.literal("the saved Creative Seller loaded back as " + loadedSeller));
        }
        helper.assertTrue(reloadedSeller.getTeamId() == null, "the reloaded unclaimed Creative Seller has an owner");
        helper.assertValueEqual(reloadedSeller.getLockedRecipeId(), SELL_FLINT.identifier(), "the reloaded locked Offer");
    }

    private static void unbreakable(GameTestHelper helper) {
        for (Block block : new Block[] {ModBlocks.CREATIVE_BUYER.get(), ModBlocks.CREATIVE_SELLER.get()}) {
            helper.setBlock(MACHINE, block);
            var state = helper.getBlockState(MACHINE);

            helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), helper.absolutePos(MACHINE)), -1.0f,
                    block + "'s destroy speed");
            helper.assertValueEqual(block.getExplosionResistance(), 3_600_000.0f, block + "'s explosion resistance");
        }
        helper.succeed();
    }

    private static void uncraftable(GameTestHelper helper) {
        var context = SlotDisplayContext.fromLevel(helper.getLevel());
        for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            for (var display : recipe.value().display()) {
                ItemStack result = display.result().resolveForFirstStack(context);
                helper.assertFalse(result.is(ModBlocks.CREATIVE_BUYER.get().asItem()) || result.is(ModBlocks.CREATIVE_SELLER.get().asItem()),
                        recipe.id().identifier() + " makes a Creative machine");
            }
        }
        helper.succeed();
    }

    private static BlockEntity reload(GameTestHelper helper, BlockEntity entity) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = entity.saveWithFullMetadata(registries);
        return BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(), saved, registries);
    }

    /** Sends the request the Creative Buyer's screen sends when the player picks {@code offer}. */
    private static void select(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        TestShop.send(player, new PacketSetItemBuyerRecipe(helper.absolutePos(MACHINE), offer.identifier()));
    }

    /** Sends the request the Creative Seller's screen sends to lock it to {@code offer}. */
    private static void lock(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer, BlockPos pos) {
        TestShop.send(player, new PacketSetSellerRecipe(helper.absolutePos(pos), offer.identifier()));
    }

    private static AbstractBuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, AbstractBuyerEntity.class);
    }

    private static SellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, SellerEntity.class);
    }
}
