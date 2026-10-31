package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

/**
 * Tests every AdminShop block, so a block added later is covered too. A broken block drops itself, and a pickaxe
 * mines every block except the unbreakable ones, such as the Creative machines.
 */
final class BlockTests {

    private BlockTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("every_block_drops_itself", 20, BlockTests::dropsItself);
        tests.test("every_breakable_block_is_mineable_with_a_pickaxe", 20, BlockTests::mineableWithAPickaxe);
    }

    /** The blocks stand two apart, so each drop is found next to its own block only. */
    private static void dropsItself(GameTestHelper helper) {
        var blocks = ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.get()).toList();
        helper.assertTrue(blocks.size() <= 16, "more blocks than the platform's spots for this test");
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            BlockPos pos = new BlockPos(1 + (i % 4) * 2, 1, 1 + (i / 4) * 2);
            helper.setBlock(pos, block);

            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

            helper.assertItemEntityPresent(block.asItem(), pos, 1);
        }
        helper.succeed();
    }

    private static void mineableWithAPickaxe(GameTestHelper helper) {
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            Block block = holder.get();
            if (block.defaultDestroyTime() < 0) {
                continue;
            }
            helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                    holder.getId() + " is not mineable with a pickaxe");
        }
        helper.succeed();
    }
}
