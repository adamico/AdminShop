package com.ammonium.adminshop.gametest;

import java.util.Set;

import com.ammonium.adminshop.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

/** Tests every AdminShop block, so a block added later is covered too. */
final class BlockTests {

    private BlockTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("every_block_but_the_detectors_drops_itself", 20, BlockTests::dropsItself);
        tests.test("no_block_is_mineable_with_a_pickaxe", 20, BlockTests::notMineableWithAPickaxe);
    }

    /**
     * Known bug (adamico/AdminShop#20): the Basic and Advanced Detectors have no loot table, so they drop nothing.
     * Every AdminShop block should drop itself when broken. The blocks stand two apart, so each drop is found next to
     * its own block only.
     */
    private static void dropsItself(GameTestHelper helper) {
        var blocks = ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.get()).toList();
        Set<Block> detectors = Set.of(ModBlocks.DETECTOR.get(), ModBlocks.ADVANCED_DETECTOR.get());
        helper.assertTrue(blocks.size() <= 16, "more blocks than the platform's spots for this test");
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            BlockPos pos = new BlockPos(1 + (i % 4) * 2, 1, 1 + (i / 4) * 2);
            helper.setBlock(pos, block);

            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

            if (detectors.contains(block)) {
                helper.assertItemEntityNotPresent(block.asItem(), pos, 1);
            } else {
                helper.assertItemEntityPresent(block.asItem(), pos, 1);
            }
        }
        helper.succeed();
    }

    /**
     * Known bug (adamico/AdminShop#20): the mineable tags are empty, so a pickaxe mines no AdminShop block faster.
     * Every breakable block, which leaves out the Creative machines, should be mineable with a pickaxe.
     */
    private static void notMineableWithAPickaxe(GameTestHelper helper) {
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            Block block = holder.get();
            helper.assertFalse(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                    holder.getId() + " is mineable with a pickaxe");
        }
        helper.succeed();
    }
}
