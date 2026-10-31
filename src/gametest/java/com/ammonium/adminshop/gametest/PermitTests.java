package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.BuyerEntity;
import com.ammonium.adminshop.blocks.entity.MachineInventory;
import com.ammonium.adminshop.blocks.interfaces.ShopMachine;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.BuyerMenu;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Offers gated by a Permit: a team that doesn't hold it can't use them, by hand, through a Seller or through a
 * Buyer, and can once it does; a machine that bypasses Permits (the Creative machines) trades without them.
 * Offers from the game tests' datapack, both gated by the Permit {@code gametest_gate}:
 * {@code gametest/buy_gated_emeralds} gives 1 emerald for 10, {@code gametest/sell_gated_flint} takes 4 flint for 5.
 */
final class PermitTests {

    static final String GATE = "gametest_gate";
    private static final ResourceKey<Recipe<?>> BUY_EMERALDS = offer("gametest/buy_gated_emeralds");
    private static final ResourceKey<Recipe<?>> SELL_FLINT = offer("gametest/sell_gated_flint");
    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** Long enough for a Seller or T1 Buyer to trade twice. */
    private static final int TWO_CYCLES = 90;

    private PermitTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("buying_a_gated_offer_needs_its_permit", 20, TestPlayers.withPlayers(PermitTests::buyingNeedsThePermit));
        tests.test("selling_to_a_gated_offer_needs_its_permit", 20, TestPlayers.withPlayers(PermitTests::sellingNeedsThePermit));
        tests.test("a_seller_sells_to_a_gated_offer_only_with_its_permit", 2 * TWO_CYCLES + 20, PermitTests::sellerNeedsThePermit);
        tests.test("a_buyer_buys_a_gated_offer_only_with_its_permit", 2 * TWO_CYCLES + 20, PermitTests::buyerNeedsThePermit);
        tests.test("a_machine_that_bypasses_permits_trades_without_them", 20, TestPlayers.withPlayers(PermitTests::bypassing));
    }

    private static void buyingNeedsThePermit(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("permit_buy");
        UUID team = team(helper, player);
        setBalance(helper, team, 100);

        var without = ShopTrades.buy(player, BUY_EMERALDS, 2);
        helper.assertValueEqual(without, ShopTrades.Result.NO_PERMIT, "buying without the Permit");
        assertBalance(helper, team, 100);
        helper.assertValueEqual(count(player, Items.EMERALD), 0, "emeralds bought without the Permit");

        MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        var with = ShopTrades.buy(player, BUY_EMERALDS, 2);
        helper.assertValueEqual(with, ShopTrades.Result.DONE, "buying with the Permit");
        assertBalance(helper, team, 80);
        helper.assertValueEqual(count(player, Items.EMERALD), 2, "emeralds bought with the Permit");
    }

    private static void sellingNeedsThePermit(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("permit_sell");
        UUID team = team(helper, player);
        setBalance(helper, team, 0);
        player.getInventory().setItem(3, new ItemStack(Items.FLINT, 12));

        helper.assertValueEqual(ShopTrades.sell(player, SELL_FLINT, ShopTrades.ANY_SLOT, 1), ShopTrades.Result.NO_PERMIT,
                "selling without the Permit");
        // As a shift-click on the slot sends it.
        helper.assertValueEqual(ShopTrades.sell(player, SELL_FLINT, 3, ShopTrades.ALL), ShopTrades.Result.NO_PERMIT,
                "selling a slot without the Permit");
        assertBalance(helper, team, 0);
        helper.assertValueEqual(count(player, Items.FLINT), 12, "flint left without the Permit");

        MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        helper.assertValueEqual(ShopTrades.sell(player, SELL_FLINT, ShopTrades.ANY_SLOT, 1), ShopTrades.Result.DONE,
                "selling with the Permit");
        assertBalance(helper, team, 5);
        helper.assertValueEqual(count(player, Items.FLINT), 8, "flint left with the Permit");
    }

    private static void sellerNeedsThePermit(GameTestHelper helper) {
        UUID team = placedBy(helper, "permit_seller", ModBlocks.SELLER.get());
        setBalance(helper, team, 0);
        int inserted = insert(helper, new ItemStack(Items.FLINT, 8));
        helper.assertValueEqual(inserted, 8, "flint piped in");

        helper.runAfterDelay(TWO_CYCLES, () -> {
            assertBalance(helper, team, 0);
            helper.assertValueEqual(handler(helper).getAmountAsInt(0), 8, "flint left in the Seller without the Permit");
            MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        });
        helper.runAfterDelay(2L * TWO_CYCLES, () -> {
            assertBalance(helper, team, 10);
            helper.assertValueEqual(handler(helper).getAmountAsInt(0), 0, "flint left in the Seller with the Permit");
            helper.succeed();
        });
    }

    private static void buyerNeedsThePermit(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join("permit_buyer");
            place(helper, owner, ModBlocks.BUYER_1.get());
            use(helper, owner);
            // The server lets the Buyer be set to a gated Offer, as on 1.21.1; it just doesn't buy it.
            helper.assertTrue(BuyerMenu.select(owner, BUY_EMERALDS), "setting the Buyer's Offer failed");
            return team(helper, owner);
        });
        setBalance(helper, team, 100);

        helper.runAfterDelay(TWO_CYCLES, () -> {
            assertBalance(helper, team, 100);
            helper.assertValueEqual(emeralds(helper.getBlockEntity(MACHINE, BuyerEntity.class).inventory()), 0,
                    "emeralds bought without the Permit");
            MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        });
        helper.runAfterDelay(2L * TWO_CYCLES, () -> {
            assertBalance(helper, team, 80);
            helper.assertValueEqual(emeralds(helper.getBlockEntity(MACHINE, BuyerEntity.class).inventory()), 2,
                    "emeralds bought with the Permit");
            helper.succeed();
        });
    }

    private static void bypassing(GameTestHelper helper, TestPlayers players) {
        UUID team = team(helper, players.join("permit_bypass"));
        setBalance(helper, team, 100);
        var output = new MachineInventory(1, resource -> true, () -> {});
        var goods = new MachineInventory(1, resource -> true, () -> {});
        goods.set(0, ItemResource.of(Items.FLINT), 4);

        var bought = ShopTrades.buyInto(helper.getLevel(), machine(team, true), output, BUY_EMERALDS);
        var sold = ShopTrades.sellFrom(helper.getLevel(), machine(team, true), goods, null);
        var refused = ShopTrades.buyInto(helper.getLevel(), machine(team, false), output, BUY_EMERALDS);

        helper.assertValueEqual(bought, ShopTrades.Result.DONE, "buying through a machine that bypasses Permits");
        helper.assertValueEqual(sold, ShopTrades.Result.DONE, "selling through a machine that bypasses Permits");
        helper.assertValueEqual(refused, ShopTrades.Result.NO_PERMIT, "buying through a machine that doesn't");
        assertBalance(helper, team, 100 - 10 + 5);
        helper.assertValueEqual(emeralds(output), 1, "emeralds bought");
        helper.assertValueEqual(goods.getAmountAsInt(0), 0, "flint left");
    }

    private static ShopMachine machine(UUID team, boolean bypasses) {
        return new ShopMachine() {
            @Override
            public @Nullable UUID getTeamId() {
                return team;
            }

            @Override
            public void setTeamId(UUID teamId) {
            }

            @Override
            public boolean bypassesPermits() {
                return bypasses;
            }
        };
    }

    /** A player places {@code block} on the platform and logs out; returns their team. */
    private static UUID placedBy(GameTestHelper helper, String name, Block block) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            place(helper, owner, block);
            return team(helper, owner);
        });
    }

    /** {@code player} places {@code block} on the platform, as from their hand. */
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

    private static int insert(GameTestHelper helper, ItemStack stack) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(handler(helper), ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static ResourceHandler<ItemResource> handler(GameTestHelper helper) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE), null);
        if (handler == null) {
            throw helper.assertionException(Component.literal("the machine exposes no item handler"));
        }
        return handler;
    }

    private static int emeralds(ResourceHandler<ItemResource> inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getResource(slot).is(Items.EMERALD)) {
                total += inventory.getAmountAsInt(slot);
            }
        }
        return total;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static UUID team(GameTestHelper helper, ServerPlayer player) {
        return MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).teamId();
    }

    private static void setBalance(GameTestHelper helper, UUID team, long balance) {
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        var account = accounts.getAccountById(team);
        helper.assertTrue(account != null, "the team has no Account");
        accounts.removeMoney(team, account.balance());
        accounts.addMoney(team, balance);
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
