package com.ammonium.adminshop.gametest;

import java.util.UUID;

import com.ammonium.adminshop.blocks.BasicDetector;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.AdvancedDetectorEntity;
import com.ammonium.adminshop.blocks.interfaces.Detector;
import com.ammonium.adminshop.network.PacketSetDetectorThreshold;
import com.ammonium.adminshop.screen.DetectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Tests the Basic and Advanced Detectors through the redstone signal next to them. Once a cycle a Detector compares
 * its Account's Balance with its threshold. The Basic Detector gives full power while the Balance is above the
 * threshold, and none at or below it. The Advanced Detector gives power in proportion to the Balance, from 0 at none
 * to 15 at the threshold and above. A Detector belongs to the team that placed it, or to the first team to use it,
 * and keeps its owner and threshold when saved.
 */
final class DetectorTests {

    private static final BlockPos DETECTOR = new BlockPos(4, 1, 4);
    /** Next to the Detector, where a lamp or dust reads its signal. */
    private static final BlockPos NEIGHBOUR = DETECTOR.east();
    /** Long enough for a Detector to check its Balance again, every 21 ticks, and for a lamp to go out (4 ticks). */
    private static final int CYCLE = 30;

    private DetectorTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("a_basic_detector_powers_only_above_its_threshold", 200, DetectorTests::basicThreshold);
        tests.test("a_placed_basic_detector_powers_fully_until_its_first_check", 100, DetectorTests::basicFirstSecond);
        tests.test("an_advanced_detector_powers_in_proportion_up_to_its_threshold", 250, DetectorTests::advancedThreshold);
        tests.test("an_advanced_detector_with_no_threshold_powers_fully_with_any_balance", 100, DetectorTests::advancedNoThreshold);
        tests.test("a_detector_belongs_to_the_team_that_placed_it", 20, TestPlayers.withPlayers(DetectorTests::belongsToPlacer));
        tests.test("a_team_member_sets_a_detectors_threshold_from_its_screen", 20,
                TestPlayers.withPlayers(DetectorTests::settingThreshold));
        tests.test("a_detectors_owner_and_threshold_survive_a_save_and_reload", 20,
                TestPlayers.withPlayers(DetectorTests::survivesSaveAndReload));
    }

    private static void basicThreshold(GameTestHelper helper) {
        UUID team = TestShop.placedBy(helper, "basic_detector_owner", 99, ModBlocks.DETECTOR.get(), DETECTOR);
        detector(helper).setThreshold(100);
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_LAMP);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "below the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 100))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "at the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 101))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, true, "above the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 50))
                .thenExecuteAfter(CYCLE, () -> assertBasic(helper, false, "after the Balance fell below the threshold"))
                .thenSucceed();
    }

    /**
     * Known bug (adamico/AdminShop#20): a Basic Detector gives full power for its first second after being placed,
     * whatever the Balance. Its Account holds nothing, which its threshold of 0 does not exceed, yet the Detector
     * powers the lamp next to it until its first check. It should give no power from the start.
     */
    private static void basicFirstSecond(GameTestHelper helper) {
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_LAMP);
        TestShop.placedBy(helper, "basic_detector_first_second", 0, ModBlocks.DETECTOR.get(), DETECTOR);

        assertBasic(helper, true, "right after it was placed");
        helper.runAfterDelay(CYCLE, () -> {
            assertBasic(helper, false, "after its first check");
            helper.succeed();
        });
    }

    private static void advancedThreshold(GameTestHelper helper) {
        UUID team = TestShop.placedBy(helper, "advanced_detector_owner", 0, ModBlocks.ADVANCED_DETECTOR.get(), DETECTOR);
        detector(helper).setThreshold(150);
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_WIRE);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 0, "with no Balance"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 50))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 5, "at a third of the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 149))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 14, "just below the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 150))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "at the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 1000))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "above the threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 10))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 1, "after the Balance fell"))
                .thenSucceed();
    }

    private static void advancedNoThreshold(GameTestHelper helper) {
        UUID team = TestShop.placedBy(helper, "advanced_detector_zero_owner", 0, ModBlocks.ADVANCED_DETECTOR.get(), DETECTOR);
        helper.setBlock(NEIGHBOUR, Blocks.REDSTONE_WIRE);

        helper.startSequence()
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 0, "with no Balance and no threshold"))
                .thenExecute(() -> TestShop.setBalance(helper, team, 1))
                .thenExecuteAfter(CYCLE, () -> assertAdvanced(helper, 15, "with a Balance and no threshold"))
                .thenSucceed();
    }

    private static void belongsToPlacer(GameTestHelper helper, TestPlayers players) throws Exception {
        ServerPlayer owner = players.join("detector_party_owner");
        ServerPlayer member = players.join("detector_party_member");
        ServerPlayer stranger = players.join("detector_stranger");
        UUID party = AccountTests.joinParty(owner, member).getId();
        TestShop.place(helper, owner, ModBlocks.DETECTOR.get(), DETECTOR);

        helper.assertValueEqual(detector(helper).getTeamId(), party, "the Detector's owner");
        TestShop.use(helper, stranger, DETECTOR);
        helper.assertFalse(stranger.containerMenu instanceof DetectorMenu, "a player of another team opened the Detector");
        setThreshold(helper, stranger, 5);
        helper.assertValueEqual(detector(helper).getThreshold(), 0L, "the threshold after another team's request");
        helper.assertValueEqual(detector(helper).getTeamId(), party, "the Detector's owner after another team used it");
        TestShop.use(helper, member, DETECTOR);
        helper.assertTrue(member.containerMenu instanceof DetectorMenu, "a member of the owning team could not open the Detector");
    }

    /**
     * Known bug (adamico/AdminShop#20): a team member can set a negative threshold, and the Detector's screen is
     * never sent the threshold, because the Detector's update for the client leaves it out. A negative threshold
     * should be refused, and the screen should show the Detector's threshold.
     */
    private static void settingThreshold(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("detector_setter");
        TestShop.place(helper, owner, ModBlocks.ADVANCED_DETECTOR.get(), DETECTOR);
        TestShop.use(helper, owner, DETECTOR);
        helper.assertTrue(owner.containerMenu instanceof DetectorMenu, "the owner could not open the Detector");

        setThreshold(helper, owner, 123_456_789_012L);
        helper.assertValueEqual(detector(helper).getThreshold(), 123_456_789_012L, "the threshold");
        CompoundTag update = ((BlockEntity) detector(helper)).getUpdateTag(helper.getLevel().registryAccess());
        helper.assertFalse(update.contains("threshold"), "the update for the client holds the threshold");

        setThreshold(helper, owner, -1);
        helper.assertValueEqual(detector(helper).getThreshold(), -1L, "the threshold after a negative one");
    }

    private static void survivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer owner = players.join("detector_saver");
        TestShop.place(helper, owner, ModBlocks.ADVANCED_DETECTOR.get(), DETECTOR);
        AdvancedDetectorEntity detector = helper.getBlockEntity(DETECTOR, AdvancedDetectorEntity.class);
        detector.setThreshold(4242);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = detector.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(detector.getBlockPos(), detector.getBlockState(), saved, registries);

        if (!(loaded instanceof AdvancedDetectorEntity reloaded)) {
            throw helper.assertionException(Component.literal("the saved Detector loaded back as " + loaded));
        }
        helper.assertValueEqual(reloaded.getTeamId(), TestShop.team(helper, owner), "the reloaded owner");
        helper.assertValueEqual(reloaded.getThreshold(), 4242L, "the reloaded threshold");
    }

    /** Sends the request the Detector's screen sends when the player confirms {@code threshold}. */
    private static void setThreshold(GameTestHelper helper, ServerPlayer player, long threshold) {
        TestShop.send(player, new PacketSetDetectorThreshold(helper.absolutePos(DETECTOR), threshold));
    }

    private static void assertBasic(GameTestHelper helper, boolean powered, String when) {
        helper.assertBlockProperty(DETECTOR, BasicDetector.LIT, powered);
        helper.assertValueEqual(signal(helper), powered ? 15 : 0, "the Basic Detector's signal " + when);
        helper.assertBlockProperty(NEIGHBOUR, RedstoneLampBlock.LIT, powered);
    }

    private static void assertAdvanced(GameTestHelper helper, int expected, String when) {
        helper.assertBlockProperty(DETECTOR, BasicDetector.LIT, expected > 0);
        helper.assertValueEqual(signal(helper), expected, "the Advanced Detector's signal " + when);
        helper.assertValueEqual(helper.getBlockState(NEIGHBOUR).getValue(RedStoneWireBlock.POWER), expected,
                "the power of the dust next to the Advanced Detector " + when);
    }

    /** The signal the Detector gives the block next to it. */
    private static int signal(GameTestHelper helper) {
        return helper.getLevel().getSignal(helper.absolutePos(DETECTOR), Direction.WEST);
    }

    private static Detector detector(GameTestHelper helper) {
        BlockEntity entity = helper.getBlockEntity(DETECTOR, BlockEntity.class);
        if (!(entity instanceof Detector detector)) {
            throw helper.assertionException(Component.literal("no Detector at " + DETECTOR));
        }
        return detector;
    }
}
