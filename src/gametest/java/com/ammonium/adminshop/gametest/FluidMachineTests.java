package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.FluidBuyerEntity;
import com.ammonium.adminshop.blocks.entity.FluidSellerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketSetFluidBuyerRecipe;
import com.ammonium.adminshop.screen.FluidBuyerMenu;
import com.ammonium.adminshop.screen.FluidSellerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests the Fluid Buyer and the Fluid Seller. A Fluid Buyer set to a fluid Buy Offer from its screen buys one trade
 * of it per cycle into its 64000 mB tank and charges its Account, while the Account can pay and the trade fits. Pipes
 * can take the fluid out. The Fluid Seller accepts only fluids that a fluid Sell Offer takes and sells one trade per
 * cycle. Both respect Permits and keep their settings and contents when saved. The Offers are those in
 * {@link FluidTradeTests}.
 */
final class FluidMachineTests {

    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** The ticks either machine takes for one trade. */
    private static final int CYCLE = FluidBuyerEntity.TICK_COOLDOWN + 1;
    private static final int CAPACITY = 64_000;

    private FluidMachineTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_fluid_buyer_buys_into_its_tank_every_21_ticks", 300, FluidMachineTests::buyerBuys);
        tests.test("a_fluid_buyer_stops_when_its_account_cannot_pay", 200, FluidMachineTests::buyerStopsWhenBroke);
        tests.test("a_fluid_buyer_stops_when_its_tank_is_full", 200, FluidMachineTests::buyerStopsWhenFull);
        tests.test("a_fluid_buyer_buys_a_gated_offer_only_with_its_permit", 6 * CYCLE, FluidMachineTests::buyerNeedsThePermit);
        tests.test("pipes_fill_a_fluid_buyer_with_any_fluid_and_take_it_out", 20, FluidMachineTests::pipes);
        tests.test("only_a_fluid_buyers_team_sets_its_offer", 20, TestPlayers.withPlayers(FluidMachineTests::onlyItsTeamSets));
        tests.test("a_locked_fluid_buyers_offer_only_a_creative_player_changes", 20, TestPlayers.withPlayers(FluidMachineTests::locking));
        tests.test("a_fluid_buyers_settings_and_tank_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(FluidMachineTests::buyerSurvivesSaveAndReload));
        tests.test("a_locked_fluid_seller_sells_through_another_offer", 200, FluidMachineTests::sellerSells);
        tests.test("a_fluid_seller_sells_once_every_21_ticks", 300, FluidMachineTests::sellerSellsOnceEveryCycle);
        tests.test("a_fluid_seller_takes_only_fluids_it_can_sell", 20, FluidMachineTests::sellerTakesOnlySellable);
        tests.test("a_fluid_seller_keeps_too_little_for_a_trade", 100, FluidMachineTests::sellerKeepsTooLittle);
        tests.test("a_fluid_seller_sells_a_gated_offer_only_with_its_permit", 6 * CYCLE, FluidMachineTests::sellerNeedsThePermit);
        tests.test("an_unowned_fluid_seller_sells_nothing", 100, FluidMachineTests::unownedSellerSellsNothing);
        tests.test("a_fluid_sellers_owner_lock_and_tank_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(FluidMachineTests::sellerSurvivesSaveAndReload));
        tests.test("a_fluid_machine_opens_for_its_team", 20, TestPlayers.withPlayers(FluidMachineTests::opensForItsTeam));
    }

    /**
     * Known bug (adamico/AdminShop#20): the Fluid Buyer buys once every 21 ticks, not every 20 as its tooltip says.
     * After 205 ticks it has made 9 purchases, where it should have made 10.
     */
    private static void buyerBuys(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_speed", 1_000, FluidTradeTests.BUY_WATER);

        helper.runAfterDelay(10L * FluidBuyerEntity.TICK_COOLDOWN + 5, () -> {
            TestShop.assertBalance(helper, team, 1_000 - 9 * 30);
            assertTank(helper, buyer(helper).getTank(), Fluids.WATER, 9_000);
            helper.succeed();
        });
    }

    /** The Account holds enough for one purchase of 30 but not two. */
    private static void buyerStopsWhenBroke(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_broke", 45, FluidTradeTests.BUY_WATER);

        helper.runAfterDelay(5L * CYCLE, () -> {
            TestShop.assertBalance(helper, team, 15);
            assertTank(helper, buyer(helper).getTank(), Fluids.WATER, 1_000);
            helper.succeed();
        });
    }

    /** The tank has room for 500 mB more, less than a trade's 1000. */
    private static void buyerStopsWhenFull(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_full", 1_000, FluidTradeTests.BUY_WATER);
        buyer(helper).getTank().set(0, FluidResource.of(Fluids.WATER), CAPACITY - 500);

        helper.runAfterDelay(3L * CYCLE, () -> {
            TestShop.assertBalance(helper, team, 1_000);
            assertTank(helper, buyer(helper).getTank(), Fluids.WATER, CAPACITY - 500);
            helper.succeed();
        });
    }

    /**
     * The server lets the Fluid Buyer be set to a gated Offer, as with the item Buyer, but the Fluid Buyer does not
     * buy it without the Permit.
     */
    private static void buyerNeedsThePermit(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_permit", 100, FluidTradeTests.BUY_GATED_LAVA);

        helper.runAfterDelay(2L * CYCLE + CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 100);
            assertTank(helper, buyer(helper).getTank(), Fluids.EMPTY, 0);
            MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        });
        helper.runAfterDelay(4L * CYCLE + CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 80);
            assertTank(helper, buyer(helper).getTank(), Fluids.LAVA, 2_000);
            helper.succeed();
        });
    }

    /**
     * Known bug (adamico/AdminShop#20): pipes can fill a Fluid Buyer's tank with any fluid. Lava piped into a Fluid
     * Buyer goes in, where nothing should. Pipes can take the fluid out again.
     */
    private static void pipes(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FLUID_BUYER.get());
        ResourceHandler<FluidResource> pipe = handler(helper);

        try (var transaction = Transaction.openRoot()) {
            int inserted = pipe.insert(FluidResource.of(Fluids.LAVA), 1_000, transaction);
            int extracted = pipe.extract(FluidResource.of(Fluids.LAVA), 5_000, transaction);
            transaction.commit();

            helper.assertValueEqual(inserted, 1_000, "lava piped into the Fluid Buyer");
            helper.assertValueEqual(extracted, 1_000, "lava piped out of the Fluid Buyer");
        }
        assertTank(helper, buyer(helper).getTank(), Fluids.EMPTY, 0);
        helper.succeed();
    }

    /** Sets the Offer as the Fluid Buyer's screen does. */
    private static void onlyItsTeamSets(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_buyer_setter");
        ServerPlayer stranger = players.join("fluid_buyer_setter_stranger");
        owner.setGameMode(GameType.SURVIVAL);
        stranger.setGameMode(GameType.SURVIVAL);
        TestShop.place(helper, owner, ModBlocks.FLUID_BUYER.get(), MACHINE);
        TestShop.use(helper, owner, MACHINE);

        helper.assertTrue(owner.containerMenu instanceof FluidBuyerMenu, "the owner could not open the Fluid Buyer");
        select(helper, owner, FluidTradeTests.BUY_WATER);
        assertOffer(helper, buyer(helper), FluidTradeTests.BUY_WATER, "the Fluid Buyer's Offer");

        TestShop.use(helper, stranger, MACHINE);
        helper.assertFalse(stranger.containerMenu instanceof FluidBuyerMenu, "a player of another team opened the Fluid Buyer");
        select(helper, stranger, FluidTradeTests.BUY_GATED_LAVA);
        assertOffer(helper, buyer(helper), FluidTradeTests.BUY_WATER, "the Fluid Buyer's Offer after another team's request");
    }

    /** Sets the lock as an admin's {@code /data merge block ... {lockedRecipe: true}} does. */
    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("fluid_buyer_locker");
        player.setGameMode(GameType.SURVIVAL);
        TestShop.place(helper, player, ModBlocks.FLUID_BUYER.get(), MACHINE);
        TestShop.use(helper, player, MACHINE);
        select(helper, player, FluidTradeTests.BUY_WATER);
        buyer(helper).setLockedRecipe(true);

        select(helper, player, FluidTradeTests.BUY_GATED_LAVA);
        assertOffer(helper, buyer(helper), FluidTradeTests.BUY_WATER, "the locked Fluid Buyer's Offer after a survival player's request");

        player.setGameMode(GameType.CREATIVE);
        select(helper, player, FluidTradeTests.BUY_GATED_LAVA);
        assertOffer(helper, buyer(helper), FluidTradeTests.BUY_GATED_LAVA, "the Offer a creative player set");
        helper.assertTrue(buyer(helper).isLockedRecipe(), "setting the Offer unlocked the Fluid Buyer");
    }

    /** The saved data keys, such as {@code lockedRecipe}, must match the names that admins' block entity data uses. */
    private static void buyerSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_buyer_saver");
        TestShop.place(helper, owner, ModBlocks.FLUID_BUYER.get(), MACHINE);
        TestShop.use(helper, owner, MACHINE);
        select(helper, owner, FluidTradeTests.BUY_WATER);
        FluidBuyerEntity buyer = buyer(helper);
        buyer.setLockedRecipe(true);
        buyer.getTank().set(0, FluidResource.of(Fluids.WATER), 7_000);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = buyer.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(buyer.getBlockPos(), buyer.getBlockState(), saved, registries);

        helper.assertTrue(saved.getBooleanOr("lockedRecipe", false), "the lock is not saved as lockedRecipe");
        helper.assertValueEqual(saved.getStringOr("recipe", ""), FluidTradeTests.BUY_WATER.identifier().toString(), "the saved recipe");
        if (!(loaded instanceof FluidBuyerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Fluid Buyer loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), TestShop.team(helper, owner), "the reloaded owner");
        assertOffer(helper, reloaded, FluidTradeTests.BUY_WATER, "the reloaded Offer");
        helper.assertTrue(reloaded.isLockedRecipe(), "the reloaded Fluid Buyer is not locked");
        assertTank(helper, reloaded.getTank(), Fluids.WATER, 7_000);
    }

    /**
     * Known bug (adamico/AdminShop#20): a Seller locked to a Sell Offer can sell through another Offer that takes the
     * same fluid. The Fluid Seller is locked to the test's lava Offer, as an admin's
     * {@code /data merge block ... {lockedRecipe: "adminshop:gametest/sell_lava"}} does, but sells through the
     * shipped {@code example_sell_fluid}, which takes 1000 mB of lava for 100. It should sell through the test's
     * Offer, for 20 a trade.
     */
    private static void sellerSells(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_sells", 0);
        seller(helper).setLockedRecipeId(FluidTradeTests.SELL_LAVA.identifier());
        helper.assertValueEqual(fill(helper, Fluids.LAVA, 3_000), 3_000, "lava piped in");

        helper.runAfterDelay(3L * CYCLE + CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 3 * 100);
            assertTank(helper, seller(helper).getTank(), Fluids.EMPTY, 0);
            helper.succeed();
        });
    }

    /**
     * Known bug (adamico/AdminShop#20): the Fluid Seller sells once every 21 ticks, not every 20 as its tooltip
     * says. After 205 ticks it has made 9 trades of 250 mB of water, where it should have made 10.
     */
    private static void sellerSellsOnceEveryCycle(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_speed", 0);
        MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        fill(helper, Fluids.WATER, 4_000);

        helper.runAfterDelay(10L * FluidSellerEntity.TICK_COOLDOWN + 5, () -> {
            TestShop.assertBalance(helper, team, 9 * 4);
            assertTank(helper, seller(helper).getTank(), Fluids.WATER, 4_000 - 9 * 250);
            helper.succeed();
        });
    }

    /**
     * Lava and water both have a fluid Sell Offer, and no Offer takes flowing lava. The Fluid Seller is then locked to
     * the lava Offer, as an admin's {@code /data merge block ... {lockedRecipe: "adminshop:gametest/sell_lava"}} does.
     */
    private static void sellerTakesOnlySellable(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FLUID_SELLER.get());
        helper.assertValueEqual(fill(helper, Fluids.FLOWING_LAVA, 1_000), 0, "flowing lava piped in");
        helper.assertValueEqual(fill(helper, Fluids.LAVA, 1_000), 1_000, "lava piped in");

        seller(helper).setLockedRecipeId(FluidTradeTests.SELL_LAVA.identifier());
        seller(helper).getTank().set(0, FluidResource.EMPTY, 0);
        helper.assertValueEqual(fill(helper, Fluids.WATER, 1_000), 0, "water piped into a Fluid Seller locked to lava");
        helper.assertValueEqual(fill(helper, Fluids.LAVA, CAPACITY + 1_000), CAPACITY, "lava piped into a locked Fluid Seller");
        helper.succeed();
    }

    /** 500 mB of lava is less than any lava Offer takes. */
    private static void sellerKeepsTooLittle(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_too_little", 0);
        fill(helper, Fluids.LAVA, 500);

        helper.runAfterDelay(3L * CYCLE, () -> {
            TestShop.assertBalance(helper, team, 0);
            assertTank(helper, seller(helper).getTank(), Fluids.LAVA, 500);
            helper.succeed();
        });
    }

    /** {@code gametest/sell_gated_water} takes 250 mB of water for 4. */
    private static void sellerNeedsThePermit(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_permit", 0);
        fill(helper, Fluids.WATER, 1_000);

        helper.runAfterDelay(2L * CYCLE + CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 0);
            assertTank(helper, seller(helper).getTank(), Fluids.WATER, 1_000);
            MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        });
        helper.runAfterDelay(4L * CYCLE + CYCLE / 2, () -> {
            TestShop.assertBalance(helper, team, 8);
            assertTank(helper, seller(helper).getTank(), Fluids.WATER, 500);
            helper.succeed();
        });
    }

    /** The Fluid Seller is placed by no player, as with a command. */
    private static void unownedSellerSellsNothing(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FLUID_SELLER.get());
        fill(helper, Fluids.LAVA, 2_000);

        helper.runAfterDelay(3L * CYCLE, () -> {
            assertTank(helper, seller(helper).getTank(), Fluids.LAVA, 2_000);
            helper.succeed();
        });
    }

    /** The saved data key {@code lockedRecipe} must match the name that admins' block entity data uses. */
    private static void sellerSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_seller_saver");
        TestShop.place(helper, owner, ModBlocks.FLUID_SELLER.get(), MACHINE);
        FluidSellerEntity seller = seller(helper);
        seller.setLockedRecipeId(FluidTradeTests.SELL_LAVA.identifier());
        seller.getTank().set(0, FluidResource.of(Fluids.LAVA), 2_500);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = seller.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(seller.getBlockPos(), seller.getBlockState(), saved, registries);

        helper.assertValueEqual(saved.getStringOr("lockedRecipe", ""), FluidTradeTests.SELL_LAVA.identifier().toString(),
                "the saved lockedRecipe");
        if (!(loaded instanceof FluidSellerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Fluid Seller loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), TestShop.team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.getLockedRecipeId(), FluidTradeTests.SELL_LAVA.identifier(), "the reloaded lock");
        assertTank(helper, reloaded.getTank(), Fluids.LAVA, 2_500);
    }

    /** The Fluid Buyer and the Fluid Seller open for a member of the owning team and not for another team's player. */
    private static void opensForItsTeam(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_menu_owner");
        ServerPlayer stranger = players.join("fluid_menu_stranger");
        BlockPos sellerPos = MACHINE.east(2);
        TestShop.place(helper, owner, ModBlocks.FLUID_BUYER.get(), MACHINE);
        TestShop.place(helper, owner, ModBlocks.FLUID_SELLER.get(), sellerPos);

        TestShop.use(helper, stranger, MACHINE);
        helper.assertFalse(stranger.containerMenu instanceof FluidBuyerMenu, "a player of another team opened the Fluid Buyer");
        TestShop.use(helper, stranger, sellerPos);
        helper.assertFalse(stranger.containerMenu instanceof FluidSellerMenu, "a player of another team opened the Fluid Seller");

        TestShop.use(helper, owner, MACHINE);
        helper.assertTrue(owner.containerMenu instanceof FluidBuyerMenu && ((FluidBuyerMenu) owner.containerMenu).getBlockEntity() == buyer(helper),
                "the owner could not open the Fluid Buyer");
        TestShop.use(helper, owner, sellerPos);
        helper.assertTrue(owner.containerMenu instanceof FluidSellerMenu, "the owner could not open the Fluid Seller");
    }

    /**
     * A player with {@code balance} places a Fluid Buyer, sets it to {@code offer} from its screen and logs out.
     * Returns their team.
     */
    private static UUID setUpBuyer(GameTestHelper helper, String name, long balance, ResourceKey<Recipe<?>> offer) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            TestShop.setBalance(helper, owner, balance);
            TestShop.place(helper, owner, ModBlocks.FLUID_BUYER.get(), MACHINE);
            TestShop.use(helper, owner, MACHINE);
            select(helper, owner, offer);
            assertOffer(helper, buyer(helper), offer, "the Fluid Buyer's Offer");
            return TestShop.team(helper, owner);
        });
    }

    /** A player with {@code balance} places a Fluid Seller and logs out. Returns their team. */
    private static UUID setUpSeller(GameTestHelper helper, String name, long balance) {
        return TestShop.placedBy(helper, name, balance, ModBlocks.FLUID_SELLER.get(), MACHINE);
    }

    /** Sends the request the Fluid Buyer's screen sends when the player picks {@code offer}. */
    private static void select(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer) {
        TestShop.send(player, new PacketSetFluidBuyerRecipe(helper.absolutePos(MACHINE), offer.identifier()));
    }

    /** Asserts that {@code buyer} is set to the Offer the server loaded under {@code offer}. */
    private static void assertOffer(GameTestHelper helper, FluidBuyerEntity buyer, ResourceKey<Recipe<?>> offer, String what) {
        var set = buyer.getRecipe(helper.getLevel()).orElse(null);
        helper.assertTrue(set == TestShop.recipe(helper, offer), what + " is not " + offer.identifier());
    }

    /** Fills the machine through its fluid capability, as a pipe would. Returns how much went in. */
    private static int fill(GameTestHelper helper, Fluid fluid, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = handler(helper).insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static void assertTank(GameTestHelper helper, ResourceHandler<FluidResource> tank, Fluid fluid, int amount) {
        int held = tank.getAmountAsInt(0);
        Fluid heldFluid = held == 0 ? Fluids.EMPTY : tank.getResource(0).getFluid();
        helper.assertValueEqual(heldFluid, fluid, "the fluid in the tank");
        helper.assertValueEqual(held, amount, "the amount in the tank");
    }

    private static ResourceHandler<FluidResource> handler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(MACHINE), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the machine exposes no fluid handler"));
        }
        return handler;
    }

    private static FluidBuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, FluidBuyerEntity.class);
    }

    private static FluidSellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, FluidSellerEntity.class);
    }
}
