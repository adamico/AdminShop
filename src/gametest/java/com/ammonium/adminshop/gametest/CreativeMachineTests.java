package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.BuyerEntity;
import com.ammonium.adminshop.blocks.entity.CreativeBuyerEntity;
import com.ammonium.adminshop.blocks.entity.CreativeSellerEntity;
import com.ammonium.adminshop.blocks.entity.SellerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.BuyerMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests the Creative Buyer and the Creative Seller. A placed one stays unclaimed and open to the placer, so a
 * creative player can set its Offer. The first player to use it after that claims it for their team. They trade
 * Offers gated by a Permit their team does not hold. The Creative Buyer works as a T3 Buyer locked to the Offer it
 * was given, and the Creative Seller works as a Seller. Nobody can break them, and no recipe crafts them. The Offers
 * come from {@link PermitTests}. {@code gametest/buy_gated_emeralds} gives 1 emerald for 10, and
 * {@code gametest/sell_gated_flint} buys 4 flint for 5. Both are gated by {@link PermitTests#GATE}.
 */
final class CreativeMachineTests {

    private static final ResourceKey<Recipe<?>> BUY_EMERALDS = offer("gametest/buy_gated_emeralds");
    private static final ResourceKey<Recipe<?>> BUY_DIAMONDS = offer("gametest/buy_diamonds");
    private static final ResourceKey<Recipe<?>> SELL_FLINT = offer("gametest/sell_gated_flint");
    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** The Creative Buyer's cooldown in ticks, the same as a T3 Buyer's. */
    private static final int BUYER_COOLDOWN = 10;
    /** The Creative Seller's cooldown in ticks, the same as a Seller's. */
    private static final int SELLER_COOLDOWN = 20;
    private static final int CYCLES = 4;

    private CreativeMachineTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_placed_creative_buyer_stays_unclaimed_and_opens_for_its_placer", 20,
                TestPlayers.withPlayers(CreativeMachineTests::buyerOpensForPlacer));
        tests.test("a_placed_creative_seller_stays_unclaimed_and_opens_for_its_placer", 20,
                TestPlayers.withPlayers(CreativeMachineTests::sellerOpensForPlacer));
        tests.test("a_claimed_creative_buyer_buys_a_gated_offer_once_every_10_ticks", 200,
                CreativeMachineTests::buyerBuysOnceClaimed);
        tests.test("a_claimed_creative_seller_sells_to_a_gated_offer_once_every_20_ticks", 200,
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

        place(helper, admin, ModBlocks.CREATIVE_BUYER.get());

        helper.assertTrue(buyer(helper).getTeamId() == null, "the placed Creative Buyer has an owner");
        helper.assertTrue(admin.containerMenu instanceof BuyerMenu, "placing the Creative Buyer did not open it");
        helper.assertTrue(BuyerMenu.select(admin, BUY_EMERALDS), "the placer could not set the Creative Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_EMERALDS, "the Creative Buyer's Offer");
        helper.assertTrue(buyer(helper).getTeamId() == null, "setting the Offer claimed the Creative Buyer");
    }

    private static void sellerOpensForPlacer(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_seller_placer");
        admin.setGameMode(GameType.CREATIVE);

        place(helper, admin, ModBlocks.CREATIVE_SELLER.get());

        helper.assertTrue(seller(helper).getTeamId() == null, "the placed Creative Seller has an owner");
        helper.assertTrue(admin.containerMenu instanceof SellerMenu, "placing the Creative Seller did not open it");
        helper.assertTrue(SellerMenu.lock(admin, SELL_FLINT), "the placer could not lock the Creative Seller");
        helper.assertValueEqual(seller(helper).lockedOffer(), SELL_FLINT, "the Creative Seller's locked Offer");
        helper.assertTrue(seller(helper).getTeamId() == null, "locking claimed the Creative Seller");
    }

    /**
     * The Balance is checked halfway between the {@code CYCLES}-th purchase and the next. The team holds no Permit
     * for the Offer.
     */
    private static void buyerBuysOnceClaimed(GameTestHelper helper) {
        long start = 1_000;
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer admin = players.join("creative_buyer_admin");
            admin.setGameMode(GameType.CREATIVE);
            place(helper, admin, ModBlocks.CREATIVE_BUYER.get());
            BuyerMenu.select(admin, BUY_EMERALDS);
            admin.closeContainer();

            ServerPlayer claimer = players.join("creative_buyer_claimer");
            claimer.setGameMode(GameType.SURVIVAL);
            setBalance(helper, claimer, start);
            use(helper, claimer);
            helper.assertTrue(claimer.containerMenu instanceof BuyerMenu, "claiming the Creative Buyer did not open it");
            return team(helper, claimer);
        });

        helper.assertValueEqual(buyer(helper).getTeamId(), team, "the Creative Buyer's owner after the first use");
        helper.runAfterDelay((long) CYCLES * BUYER_COOLDOWN + BUYER_COOLDOWN / 2, () -> {
            assertBalance(helper, team, start - CYCLES * 10);
            ResourceHandler<ItemResource> pipe = itemHandler(helper);
            try (var transaction = Transaction.openRoot()) {
                int inserted = ResourceHandlerUtil.insertStacking(pipe, ItemResource.of(Items.DIRT), 64, transaction);
                int extracted = pipe.extract(ItemResource.of(Items.EMERALD), 64, transaction);
                transaction.commit();

                helper.assertValueEqual(inserted, 0, "dirt piped into the Creative Buyer");
                helper.assertValueEqual(extracted, CYCLES, "emeralds piped out of the Creative Buyer");
            }
            helper.succeed();
        });
    }

    /** The Balance is checked halfway between the second sale and the third. The team holds no Permit for the Offer. */
    private static void sellerSellsOnceClaimed(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer admin = players.join("creative_seller_admin");
            admin.setGameMode(GameType.CREATIVE);
            place(helper, admin, ModBlocks.CREATIVE_SELLER.get());
            admin.closeContainer();

            ServerPlayer claimer = players.join("creative_seller_claimer");
            claimer.setGameMode(GameType.SURVIVAL);
            setBalance(helper, claimer, 0);
            use(helper, claimer);
            helper.assertTrue(claimer.containerMenu instanceof SellerMenu, "claiming the Creative Seller did not open it");
            return team(helper, claimer);
        });
        helper.assertValueEqual(seller(helper).getTeamId(), team, "the Creative Seller's owner after the first use");

        int inserted;
        try (var transaction = Transaction.openRoot()) {
            inserted = ResourceHandlerUtil.insertStacking(itemHandler(helper), ItemResource.of(Items.FLINT), 12, transaction);
            transaction.commit();
        }

        helper.assertValueEqual(inserted, 12, "flint piped into the Creative Seller");
        helper.runAfterDelay(2L * SELLER_COOLDOWN + SELLER_COOLDOWN / 2, () -> {
            assertBalance(helper, team, 10);
            helper.assertValueEqual(seller(helper).inventory().getAmountAsInt(0), 4, "flint left in the Creative Seller");
            helper.succeed();
        });
    }

    private static void buyerLocked(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_buyer_locker");
        admin.setGameMode(GameType.CREATIVE);
        place(helper, admin, ModBlocks.CREATIVE_BUYER.get());
        BuyerMenu.select(admin, BUY_EMERALDS);
        admin.closeContainer();
        ServerPlayer claimer = players.join("creative_buyer_locked_claimer");
        claimer.setGameMode(GameType.SURVIVAL);
        use(helper, claimer);

        helper.assertTrue(buyer(helper).isLocked(), "the Creative Buyer is not locked");
        helper.assertTrue(((BuyerMenu) claimer.containerMenu).isLocked(), "the menu doesn't show the lock");
        helper.assertFalse(BuyerMenu.select(claimer, BUY_DIAMONDS), "its survival owner changed the Creative Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_EMERALDS, "the Creative Buyer's Offer");

        claimer.setGameMode(GameType.CREATIVE);
        helper.assertTrue(BuyerMenu.select(claimer, BUY_DIAMONDS), "a creative player could not change the Creative Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), BUY_DIAMONDS, "the Offer a creative player set");
    }

    private static void onlyItsTeamOpens(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_team_admin");
        ServerPlayer owner = players.join("creative_team_owner");
        ServerPlayer stranger = players.join("creative_team_stranger");
        admin.setGameMode(GameType.CREATIVE);
        place(helper, admin, ModBlocks.CREATIVE_SELLER.get());
        admin.closeContainer();

        use(helper, owner);
        owner.closeContainer();
        use(helper, stranger);
        use(helper, admin);

        helper.assertValueEqual(seller(helper).getTeamId(), team(helper, owner), "the Creative Seller's owner");
        helper.assertFalse(stranger.containerMenu instanceof SellerMenu, "a player of another team opened the Creative Seller");
        helper.assertFalse(admin.containerMenu instanceof SellerMenu, "its placer, of another team, opened the claimed Creative Seller");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("creative_saver");
        admin.setGameMode(GameType.CREATIVE);
        place(helper, admin, ModBlocks.CREATIVE_BUYER.get());
        BuyerMenu.select(admin, BUY_EMERALDS);
        BlockPos sellerPos = MACHINE.east(2);
        place(helper, admin, ModBlocks.CREATIVE_SELLER.get(), sellerPos);
        SellerMenu.lock(admin, SELL_FLINT);

        BuyerEntity buyer = buyer(helper);
        SellerEntity seller = helper.getBlockEntity(sellerPos, SellerEntity.class);
        BlockEntity loadedBuyer = reload(helper, buyer);
        BlockEntity loadedSeller = reload(helper, seller);

        if (!(loadedBuyer instanceof CreativeBuyerEntity reloadedBuyer)) {
            throw helper.assertionException(Component.literal("the saved Creative Buyer loaded back as " + loadedBuyer));
        }
        helper.assertTrue(reloadedBuyer.getTeamId() == null, "the reloaded unclaimed Creative Buyer has an owner");
        helper.assertValueEqual(reloadedBuyer.offer(), BUY_EMERALDS, "the reloaded Offer");
        helper.assertTrue(reloadedBuyer.isLocked(), "the reloaded Creative Buyer is not locked");
        if (!(loadedSeller instanceof CreativeSellerEntity reloadedSeller)) {
            throw helper.assertionException(Component.literal("the saved Creative Seller loaded back as " + loadedSeller));
        }
        helper.assertTrue(reloadedSeller.getTeamId() == null, "the reloaded unclaimed Creative Seller has an owner");
        helper.assertValueEqual(reloadedSeller.lockedOffer(), SELL_FLINT, "the reloaded locked Offer");
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
                var result = display.result().resolveForFirstStack(context);
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

    private static void place(GameTestHelper helper, ServerPlayer player, Block block) {
        place(helper, player, block, MACHINE);
    }

    /** {@code player} places {@code block} at {@code pos} on the platform, as from their hand. */
    private static void place(GameTestHelper helper, ServerPlayer player, Block block, BlockPos pos) {
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(pos.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertTrue(result.consumesAction(), "placing " + block + " failed: " + result);
        helper.assertBlockPresent(block, pos);
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(MACHINE);
        helper.getBlockState(MACHINE).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static ResourceHandler<ItemResource> itemHandler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the Creative machine exposes no item handler"));
        }
        return handler;
    }

    private static BuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, BuyerEntity.class);
    }

    private static SellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, SellerEntity.class);
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
