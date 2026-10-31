package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.FluidBuyerEntity;
import com.ammonium.adminshop.blocks.entity.FluidSellerEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.FluidBuyerMenu;
import com.ammonium.adminshop.screen.FluidSellerMenu;
import com.ammonium.adminshop.screen.TankData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Tests the Fluid Buyer and the Fluid Seller. A Fluid Buyer set to a fluid Buy Offer from its screen buys one trade
 * of it every 20 ticks into its 64000 mB tank and charges its Account, while the Account can pay and the trade fits.
 * Pipes can take the fluid out but put nothing in. The Fluid Seller accepts only fluids that a fluid Sell Offer takes
 * and sells one trade every 20 ticks. Both respect Permits and keep their settings and contents when saved. The
 * Offers are those in {@link FluidTradeTests}.
 */
final class FluidMachineTests {

    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    private static final int CYCLE = 20;
    private static final int CAPACITY = 64_000;

    private FluidMachineTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_fluid_buyer_buys_into_its_tank_every_20_ticks", 200, FluidMachineTests::buyerBuys);
        tests.test("a_fluid_buyer_stops_when_its_account_cannot_pay", 200, FluidMachineTests::buyerStopsWhenBroke);
        tests.test("a_fluid_buyer_stops_when_its_tank_is_full", 200, FluidMachineTests::buyerStopsWhenFull);
        tests.test("a_fluid_buyer_buys_a_gated_offer_only_with_its_permit", 6 * CYCLE, FluidMachineTests::buyerNeedsThePermit);
        tests.test("pipes_take_from_a_fluid_buyer_but_put_nothing_in", 20, FluidMachineTests::pipesOnlyTake);
        tests.test("only_a_fluid_buyers_team_sets_its_offer", 20, TestPlayers.withPlayers(FluidMachineTests::onlyItsTeamSets));
        tests.test("a_locked_fluid_buyers_offer_only_a_creative_player_changes", 20, TestPlayers.withPlayers(FluidMachineTests::locking));
        tests.test("a_fluid_buyers_settings_and_tank_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(FluidMachineTests::buyerSurvivesSaveAndReload));
        tests.test("a_fluid_seller_sells_what_is_piped_in", 200, FluidMachineTests::sellerSells);
        tests.test("a_fluid_seller_takes_only_fluids_it_can_sell", 20, FluidMachineTests::sellerTakesOnlySellable);
        tests.test("a_fluid_seller_keeps_too_little_for_a_trade", 100, FluidMachineTests::sellerKeepsTooLittle);
        tests.test("a_fluid_seller_sells_a_gated_offer_only_with_its_permit", 6 * CYCLE, FluidMachineTests::sellerNeedsThePermit);
        tests.test("an_unowned_fluid_seller_sells_nothing", 100, FluidMachineTests::unownedSellerSellsNothing);
        tests.test("a_fluid_sellers_owner_lock_and_tank_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(FluidMachineTests::sellerSurvivesSaveAndReload));
        tests.test("a_fluid_machines_menu_shows_its_tank", 20, TestPlayers.withPlayers(FluidMachineTests::menusShowTheTank));
    }

    /** The Balance is checked halfway between the 4th purchase and the 5th. */
    private static void buyerBuys(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_speed", 1_000, FluidTradeTests.BUY_WATER);

        helper.runAfterDelay(4L * CYCLE + CYCLE / 2, () -> {
            assertBalance(helper, team, 1_000 - 4 * 30);
            assertTank(helper, buyer(helper).tank(), Fluids.WATER, 4_000);
            helper.succeed();
        });
    }

    /** The Account holds enough for one purchase of 30 but not two. */
    private static void buyerStopsWhenBroke(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_broke", 45, FluidTradeTests.BUY_WATER);

        helper.runAfterDelay(5L * CYCLE, () -> {
            assertBalance(helper, team, 15);
            assertTank(helper, buyer(helper).tank(), Fluids.WATER, 1_000);
            helper.succeed();
        });
    }

    /** The tank has room for 500 mB more, less than a trade's 1000. */
    private static void buyerStopsWhenFull(GameTestHelper helper) {
        UUID team = setUpBuyer(helper, "fluid_buyer_full", 1_000, FluidTradeTests.BUY_WATER);
        buyer(helper).tank().set(0, FluidResource.of(Fluids.WATER), CAPACITY - 500);

        helper.runAfterDelay(3L * CYCLE, () -> {
            assertBalance(helper, team, 1_000);
            assertTank(helper, buyer(helper).tank(), Fluids.WATER, CAPACITY - 500);
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
            assertBalance(helper, team, 100);
            assertTank(helper, buyer(helper).tank(), Fluids.EMPTY, 0);
            MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        });
        helper.runAfterDelay(4L * CYCLE + CYCLE / 2, () -> {
            assertBalance(helper, team, 80);
            assertTank(helper, buyer(helper).tank(), Fluids.LAVA, 2_000);
            helper.succeed();
        });
    }

    private static void pipesOnlyTake(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FLUID_BUYER.get());
        buyer(helper).tank().set(0, FluidResource.of(Fluids.WATER), 3_000);
        ResourceHandler<FluidResource> pipe = handler(helper);

        try (var transaction = Transaction.openRoot()) {
            int inserted = pipe.insert(FluidResource.of(Fluids.LAVA), 1_000, transaction);
            int insertedSame = pipe.insert(FluidResource.of(Fluids.WATER), 1_000, transaction);
            int extracted = pipe.extract(FluidResource.of(Fluids.WATER), 5_000, transaction);
            transaction.commit();

            helper.assertValueEqual(inserted, 0, "lava piped into the Fluid Buyer");
            helper.assertValueEqual(insertedSame, 0, "water piped into the Fluid Buyer");
            helper.assertValueEqual(extracted, 3_000, "water piped out of the Fluid Buyer");
        }
        assertTank(helper, buyer(helper).tank(), Fluids.EMPTY, 0);
        helper.succeed();
    }

    private static void onlyItsTeamSets(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_buyer_setter");
        ServerPlayer stranger = players.join("fluid_buyer_setter_stranger");
        owner.setGameMode(GameType.SURVIVAL);
        stranger.setGameMode(GameType.SURVIVAL);
        place(helper, owner, ModBlocks.FLUID_BUYER.get());
        use(helper, owner);

        helper.assertTrue(owner.containerMenu instanceof FluidBuyerMenu, "the owner could not open the Fluid Buyer");
        helper.assertTrue(FluidBuyerMenu.select(owner, FluidTradeTests.BUY_WATER), "the owner could not set the Fluid Buyer's Offer");
        helper.assertValueEqual(buyer(helper).offer(), FluidTradeTests.BUY_WATER, "the Fluid Buyer's Offer");
        helper.assertValueEqual(((FluidBuyerMenu) owner.containerMenu).target().getFluid(), Fluids.WATER, "the screen's fluid");

        use(helper, stranger);
        helper.assertFalse(stranger.containerMenu instanceof FluidBuyerMenu, "a player of another team opened the Fluid Buyer");
        helper.assertFalse(FluidBuyerMenu.select(stranger, FluidTradeTests.BUY_GATED_LAVA), "a player of another team set the Offer");
        helper.assertFalse(FluidBuyerMenu.select(owner, ShopTradeTests.offer("gametest/buy_diamonds")),
                "the Fluid Buyer was set to an item Buy Offer");
        helper.assertFalse(FluidBuyerMenu.select(owner, FluidTradeTests.SELL_LAVA), "the Fluid Buyer was set to a Sell Offer");
        helper.assertValueEqual(buyer(helper).offer(), FluidTradeTests.BUY_WATER, "the Fluid Buyer's Offer after refused requests");
    }

    /** Sets the lock as an admin's {@code /data merge block ... {lockedRecipe: true}} does. */
    private static void locking(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("fluid_buyer_locker");
        player.setGameMode(GameType.SURVIVAL);
        place(helper, player, ModBlocks.FLUID_BUYER.get());
        use(helper, player);
        FluidBuyerMenu.select(player, FluidTradeTests.BUY_WATER);
        buyer(helper).setLocked(true);

        helper.assertTrue(((FluidBuyerMenu) player.containerMenu).isLocked(), "the menu doesn't show the lock");
        helper.assertFalse(FluidBuyerMenu.select(player, FluidTradeTests.BUY_GATED_LAVA), "a survival player changed a locked Offer");
        helper.assertValueEqual(buyer(helper).offer(), FluidTradeTests.BUY_WATER, "the locked Fluid Buyer's Offer");

        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(FluidBuyerMenu.select(player, FluidTradeTests.BUY_GATED_LAVA), "a creative player could not change a locked Offer");
        helper.assertValueEqual(buyer(helper).offer(), FluidTradeTests.BUY_GATED_LAVA, "the Offer a creative player set");
        helper.assertTrue(buyer(helper).isLocked(), "setting the Offer unlocked the Fluid Buyer");
    }

    /** The saved data keys, such as {@code lockedRecipe}, must match the names that admins' block entity data uses. */
    private static void buyerSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_buyer_saver");
        place(helper, owner, ModBlocks.FLUID_BUYER.get());
        use(helper, owner);
        FluidBuyerMenu.select(owner, FluidTradeTests.BUY_WATER);
        FluidBuyerEntity buyer = buyer(helper);
        buyer.setLocked(true);
        buyer.tank().set(0, FluidResource.of(Fluids.WATER), 7_000);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = buyer.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(buyer.getBlockPos(), buyer.getBlockState(), saved, registries);

        helper.assertTrue(saved.getBooleanOr("lockedRecipe", false), "the lock is not saved as lockedRecipe");
        helper.assertValueEqual(saved.getStringOr("recipe", ""), FluidTradeTests.BUY_WATER.identifier().toString(), "the saved recipe");
        if (!(loaded instanceof FluidBuyerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Fluid Buyer loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.offer(), FluidTradeTests.BUY_WATER, "the reloaded Offer");
        helper.assertTrue(reloaded.isLocked(), "the reloaded Fluid Buyer is not locked");
        assertTank(helper, reloaded.tank(), Fluids.WATER, 7_000);
    }

    /**
     * The Fluid Seller is locked to the test's lava Offer, because the shipped {@code example_sell_fluid} also takes
     * lava at another price.
     */
    private static void sellerSells(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_sells", 0);
        seller(helper).lockTo(FluidTradeTests.SELL_LAVA);
        helper.assertValueEqual(fill(helper, Fluids.LAVA, 3_000), 3_000, "lava piped in");

        helper.runAfterDelay(3L * CYCLE + CYCLE / 2, () -> {
            assertBalance(helper, team, 3 * 20);
            assertTank(helper, seller(helper).tank(), Fluids.EMPTY, 0);
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

        seller(helper).lockTo(FluidTradeTests.SELL_LAVA);
        seller(helper).tank().set(0, FluidResource.EMPTY, 0);
        helper.assertValueEqual(fill(helper, Fluids.WATER, 1_000), 0, "water piped into a Fluid Seller locked to lava");
        helper.assertValueEqual(fill(helper, Fluids.LAVA, CAPACITY + 1_000), CAPACITY, "lava piped into a locked Fluid Seller");
        helper.succeed();
    }

    /** 500 mB of lava is less than any lava Offer takes. */
    private static void sellerKeepsTooLittle(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_too_little", 0);
        fill(helper, Fluids.LAVA, 500);

        helper.runAfterDelay(3L * CYCLE, () -> {
            assertBalance(helper, team, 0);
            assertTank(helper, seller(helper).tank(), Fluids.LAVA, 500);
            helper.succeed();
        });
    }

    /** {@code gametest/sell_gated_water} takes 250 mB of water for 4. */
    private static void sellerNeedsThePermit(GameTestHelper helper) {
        UUID team = setUpSeller(helper, "fluid_seller_permit", 0);
        fill(helper, Fluids.WATER, 1_000);

        helper.runAfterDelay(2L * CYCLE + CYCLE / 2, () -> {
            assertBalance(helper, team, 0);
            assertTank(helper, seller(helper).tank(), Fluids.WATER, 1_000);
            MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        });
        helper.runAfterDelay(4L * CYCLE + CYCLE / 2, () -> {
            assertBalance(helper, team, 8);
            assertTank(helper, seller(helper).tank(), Fluids.WATER, 500);
            helper.succeed();
        });
    }

    /** The Fluid Seller is placed by no player, as with a command. */
    private static void unownedSellerSellsNothing(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FLUID_SELLER.get());
        fill(helper, Fluids.LAVA, 2_000);

        helper.runAfterDelay(3L * CYCLE, () -> {
            assertTank(helper, seller(helper).tank(), Fluids.LAVA, 2_000);
            helper.succeed();
        });
    }

    /** The saved data key {@code lockedRecipe} must match the name that admins' block entity data uses. */
    private static void sellerSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_seller_saver");
        place(helper, owner, ModBlocks.FLUID_SELLER.get());
        FluidSellerEntity seller = seller(helper);
        seller.lockTo(FluidTradeTests.SELL_LAVA);
        seller.tank().set(0, FluidResource.of(Fluids.LAVA), 2_500);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = seller.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(seller.getBlockPos(), seller.getBlockState(), saved, registries);

        helper.assertValueEqual(saved.getStringOr("lockedRecipe", ""), FluidTradeTests.SELL_LAVA.identifier().toString(),
                "the saved lockedRecipe");
        if (!(loaded instanceof FluidSellerEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Fluid Seller loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.lockedOffer(), FluidTradeTests.SELL_LAVA, "the reloaded lock");
        assertTank(helper, reloaded.tank(), Fluids.LAVA, 2_500);
    }

    /**
     * Tests that the menus carry the tank to the screen in data slots, which travel as signed 16-bit numbers, even
     * for a full tank's 64000 mB. The test decodes what the client reads after the network cuts each data slot to
     * a short.
     */
    private static void menusShowTheTank(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("fluid_menu_viewer");
        place(helper, owner, ModBlocks.FLUID_BUYER.get());
        buyer(helper).tank().set(0, FluidResource.of(Fluids.WATER), CAPACITY - 1);
        use(helper, owner);
        var buyerMenu = (FluidBuyerMenu) owner.containerMenu;
        helper.assertValueEqual(buyerMenu.tank().getFluid(), Fluids.WATER, "the Fluid Buyer menu's fluid");
        helper.assertValueEqual(buyerMenu.tank().getAmount(), CAPACITY - 1, "the Fluid Buyer menu's amount");
        helper.assertTrue(buyerMenu.hasOwner(), "the Fluid Buyer menu shows no owner");
        helper.assertTrue(buyerMenu.target().isEmpty(), "the Fluid Buyer menu shows a fluid without an Offer");

        BlockPos sellerPos = MACHINE.east(2);
        helper.setBlock(sellerPos, ModBlocks.FLUID_SELLER.get());
        helper.getBlockEntity(sellerPos, FluidSellerEntity.class).setTeamId(team(helper, owner));
        helper.getBlockEntity(sellerPos, FluidSellerEntity.class).tank().set(0, FluidResource.of(Fluids.LAVA), 40_000);
        BlockPos absolute = helper.absolutePos(sellerPos);
        helper.getBlockState(sellerPos).useWithoutItem(helper.getLevel(), owner,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        var sellerMenu = (FluidSellerMenu) owner.containerMenu;
        helper.assertValueEqual(sellerMenu.tank().getFluid(), Fluids.LAVA, "the Fluid Seller menu's fluid");
        helper.assertValueEqual(sellerMenu.tank().getAmount(), 40_000, "the Fluid Seller menu's amount");

        FluidStack full = new FluidStack(Fluids.LAVA, CAPACITY);
        var received = new SimpleContainerData(TankData.SLOTS);
        for (int slot = 0; slot < TankData.SLOTS; slot++) {
            received.set(slot, (short) TankData.encode(full, slot));
        }
        FluidStack decoded = TankData.decode(received, 0);
        helper.assertValueEqual(decoded.getFluid(), Fluids.LAVA, "the fluid a client receives");
        helper.assertValueEqual(decoded.getAmount(), CAPACITY, "the amount a client receives");
    }

    /**
     * A player with {@code balance} places a Fluid Buyer, sets it to {@code offer} from its screen and logs out.
     * Returns their team.
     */
    private static UUID setUpBuyer(GameTestHelper helper, String name, long balance, ResourceKey<Recipe<?>> offer) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            setBalance(helper, owner, balance);
            place(helper, owner, ModBlocks.FLUID_BUYER.get());
            use(helper, owner);
            helper.assertTrue(FluidBuyerMenu.select(owner, offer), "setting the Fluid Buyer's Offer failed");
            return team(helper, owner);
        });
    }

    /** A player with {@code balance} places a Fluid Seller and logs out. Returns their team. */
    private static UUID setUpSeller(GameTestHelper helper, String name, long balance) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            setBalance(helper, owner, balance);
            place(helper, owner, ModBlocks.FLUID_SELLER.get());
            return team(helper, owner);
        });
    }

    private static void place(GameTestHelper helper, ServerPlayer player, Block block) {
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(MACHINE.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertTrue(result.consumesAction(), "placing the machine failed: " + result);
        helper.assertBlockPresent(block, MACHINE);
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(MACHINE);
        helper.getBlockState(MACHINE).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    /** Pipes {@code amount} of {@code fluid} into the machine. Returns how much went in. */
    private static int fill(GameTestHelper helper, Fluid fluid, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = handler(helper).insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static ResourceHandler<FluidResource> handler(GameTestHelper helper) {
        @Nullable ResourceHandler<FluidResource> handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(MACHINE), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the machine exposes no fluid handler"));
        }
        return handler;
    }

    private static void assertTank(GameTestHelper helper, ResourceHandler<FluidResource> tank, Fluid fluid, int amount) {
        FluidStack held = tank.getResource(0).toStack(tank.getAmountAsInt(0));
        helper.assertValueEqual(held.getFluid(), fluid, "the fluid in the tank");
        helper.assertValueEqual(held.getAmount(), amount, "the amount in the tank");
    }

    private static FluidBuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, FluidBuyerEntity.class);
    }

    private static FluidSellerEntity seller(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, FluidSellerEntity.class);
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
}
