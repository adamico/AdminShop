package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.DetectorBlock;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.DetectorEntity;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.screen.DetectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Basic and Advanced Detectors, as redstone next to them sees them. Every {@link DetectorEntity#TICK_COOLDOWN}
 * ticks a Detector compares its Account's Balance with its threshold: the Basic Detector gives full power while the
 * Balance is above the threshold, and none at or below it; the Advanced Detector gives power in proportion to the
 * Balance, from 0 at none to 15 at the threshold and above. A Detector nobody owns gives none. It belongs to the team
 * that placed it, or to the first team to use it, and keeps its owner and threshold when saved.
 */
final class DetectorTests {

    private static final BlockPos DETECTOR = new BlockPos(4, 1, 4);
    /** Next to the Detector, where a lamp or dust reads its signal. */
    private static final BlockPos NEIGHBOUR = DETECTOR.east();
    /** Long enough for a Detector to have looked at its Balance again, and for a lamp to go out (4 ticks). */
    private static final int CYCLE = DetectorEntity.TICK_COOLDOWN + 5;

    private DetectorTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_basic_detector_powers_only_above_its_threshold", 200, DetectorTests::basicThreshold);
        tests.test("an_advanced_detector_powers_in_proportion_up_to_its_threshold", 200, DetectorTests::advancedThreshold);
        tests.test("an_advanced_detector_with_no_threshold_powers_fully_with_any_balance", 100, DetectorTests::advancedNoThreshold);
        tests.test("an_unowned_detector_gives_no_signal_until_claimed", 100, DetectorTests::unownedUntilClaimed);
        tests.test("a_detector_belongs_to_the_team_that_placed_it", 20, TestPlayers.withPlayers(DetectorTests::belongsToPlacer));
        tests.test("a_team_member_sets_a_detectors_threshold_from_its_screen", 20,
                TestPlayers.withPlayers(DetectorTests::settingThreshold));
        tests.test("a_detectors_threshold_reaches_its_screen_whole", 20, DetectorTests::thresholdDataSlots);
        tests.test("a_detectors_owner_and_threshold_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(DetectorTests::survivesSaveAndReload));
    }

    private static void basicThreshold(GameTestHelper helper) {
        UUID team = placedBy(helper, "basic_detector_owner", ModBlocks.DETECTOR.get());
        detector(helper).setThreshold(100);
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_LAMP);
        setBalance(helper, team, 99);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "below the threshold"))
                .thenExecute(() -> setBalance(helper, team, 100))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "at the threshold"))
                .thenExecute(() -> setBalance(helper, team, 101))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, true, "above the threshold"))
                .thenExecute(() -> setBalance(helper, team, 50))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "after the Balance fell below the threshold"))
                .thenSucceed();
    }

    private static void advancedThreshold(GameTestHelper helper) {
        UUID team = placedBy(helper, "advanced_detector_owner", ModBlocks.ADVANCED_DETECTOR.get());
        detector(helper).setThreshold(150);
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_WIRE);
        setBalance(helper, team, 0);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 0, "with no Balance"))
                .thenExecute(() -> setBalance(helper, team, 50))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 5, "at a third of the threshold"))
                .thenExecute(() -> setBalance(helper, team, 149))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 14, "just below the threshold"))
                .thenExecute(() -> setBalance(helper, team, 150))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "at the threshold"))
                .thenExecute(() -> setBalance(helper, team, 1000))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "above the threshold"))
                .thenExecute(() -> setBalance(helper, team, 10))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 1, "after the Balance fell"))
                .thenSucceed();
    }

    private static void advancedNoThreshold(GameTestHelper helper) {
        UUID team = placedBy(helper, "advanced_detector_zero_owner", ModBlocks.ADVANCED_DETECTOR.get());
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_WIRE);
        setBalance(helper, team, 0);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 0, "with no Balance and no threshold"))
                .thenExecute(() -> setBalance(helper, team, 1))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "with a Balance and no threshold"))
                .thenSucceed();
    }

    private static void unownedUntilClaimed(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer claimer = players.join("detector_claimer");
            UUID id = team(helper, claimer);
            setBalance(helper, id, 10);
            return id;
        });
        // Placed by no player, as by a command; with no threshold, any Balance would give full power.
        helper.setBlock(DETECTOR, ModBlocks.ADVANCED_DETECTOR.get());
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_WIRE);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> {
                    helper.assertTrue(detector(helper).getTeamId() == null, "a Detector placed by no player has an owner");
                    assertAdvanced(helper, 0, "while nobody owns it");
                    boolean opened = TestPlayers.session(helper, players -> {
                        ServerPlayer claimer = players.join("detector_claimer");
                        use(helper, claimer);
                        return claimer.containerMenu instanceof DetectorMenu;
                    });
                    helper.assertTrue(opened, "claiming the Detector did not open it");
                    helper.assertValueEqual(detector(helper).getTeamId(), team, "the owner after the first use");
                })
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "once claimed"))
                .thenSucceed();
    }

    private static void belongsToPlacer(GameTestHelper helper, TestPlayers players) throws Exception {
        ServerPlayer owner = players.join("detector_party_owner");
        ServerPlayer member = players.join("detector_party_member");
        ServerPlayer stranger = players.join("detector_stranger");
        UUID party = AccountTests.joinParty(owner, member).getId();
        place(helper, owner, ModBlocks.DETECTOR.get());

        helper.assertValueEqual(detector(helper).getTeamId(), party, "the Detector's owner");
        use(helper, stranger);
        helper.assertFalse(stranger.containerMenu instanceof DetectorMenu, "a player of another team opened the Detector");
        helper.assertFalse(DetectorMenu.setThreshold(stranger, 5), "a player of another team set the threshold");
        helper.assertValueEqual(detector(helper).getTeamId(), party, "the Detector's owner after another team used it");
        use(helper, member);
        helper.assertTrue(member.containerMenu instanceof DetectorMenu, "a member of the owning team could not open the Detector");
    }

    private static void settingThreshold(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("detector_setter");
        place(helper, owner, ModBlocks.ADVANCED_DETECTOR.get());
        use(helper, owner);
        DetectorMenu menu = (DetectorMenu) owner.containerMenu;

        helper.assertTrue(DetectorMenu.setThreshold(owner, 123_456_789_012L), "the owner could not set the threshold");
        helper.assertValueEqual(detector(helper).getThreshold(), 123_456_789_012L, "the threshold");
        helper.assertValueEqual(menu.threshold(), 123_456_789_012L, "the threshold in the menu");
        helper.assertFalse(DetectorMenu.setThreshold(owner, -1), "a negative threshold was set");
        helper.assertValueEqual(detector(helper).getThreshold(), 123_456_789_012L, "the threshold after a negative one");
    }

    /** A data slot travels as a signed 16-bit number, so the threshold must survive that on the way to the client. */
    private static void thresholdDataSlots(GameTestHelper helper) {
        for (long threshold : new long[] {0, 1, 0xFFFF, 0x1_0000, 123_456_789_012L, Long.MAX_VALUE}) {
            var sent = DetectorMenu.serverData(threshold, true);
            var received = new SimpleContainerData(sent.getCount());
            for (int i = 0; i < sent.getCount(); i++) {
                received.set(i, (short) sent.get(i));
            }
            helper.assertValueEqual(DetectorMenu.threshold(received), threshold, "the threshold the client received");
        }
        helper.succeed();
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("detector_saver");
        place(helper, owner, ModBlocks.ADVANCED_DETECTOR.get());
        DetectorEntity detector = detector(helper);
        detector.setThreshold(4242);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = detector.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(detector.getBlockPos(), detector.getBlockState(), saved, registries);

        if (!(loaded instanceof DetectorEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Detector loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.getThreshold(), 4242L, "the reloaded threshold");
    }

    private static void assertBasic(GameTestHelper helper, boolean powered, String when) {
        helper.assertBlockProperty(DETECTOR, DetectorBlock.LIT, powered);
        helper.assertValueEqual(signal(helper), powered ? 15 : 0, "the Basic Detector's signal " + when);
        helper.assertBlockProperty(NEIGHBOUR, RedstoneLampBlock.LIT, powered);
    }

    private static void assertAdvanced(GameTestHelper helper, int expected, String when) {
        helper.assertBlockProperty(DETECTOR, DetectorBlock.LIT, expected > 0);
        helper.assertValueEqual(signal(helper), expected, "the Advanced Detector's signal " + when);
        helper.assertValueEqual(helper.getBlockState(NEIGHBOUR).getValue(RedStoneWireBlock.POWER), expected,
                "the power of the dust next to the Advanced Detector " + when);
    }

    /** The signal the Detector gives the block next to it. */
    private static int signal(GameTestHelper helper) {
        return helper.getLevel().getSignal(helper.absolutePos(DETECTOR), Direction.WEST);
    }

    /** A player places a Detector on the platform and logs out; returns their team. */
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
        BlockPos floor = helper.absolutePos(DETECTOR.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        helper.assertTrue(result.consumesAction(), "placing the Detector failed: " + result);
        helper.assertBlockPresent(block, DETECTOR);
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(DETECTOR);
        helper.getBlockState(DETECTOR).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static DetectorEntity detector(GameTestHelper helper) {
        return helper.getBlockEntity(DETECTOR, DetectorEntity.class);
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
}
