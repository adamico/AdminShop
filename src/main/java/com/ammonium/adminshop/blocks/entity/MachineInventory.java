package com.ammonium.adminshop.blocks.entity;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's item slots, as pipes see them through the item capability and players through its menu: they take
 * only what {@code accepts} allows, and tell the machine when they change.
 */
public class MachineInventory extends ItemStacksResourceHandler {
    private final Predicate<ItemResource> accepts;
    private final Runnable changed;

    public MachineInventory(int size, Predicate<ItemResource> accepts, Runnable changed) {
        super(size);
        this.accepts = accepts;
        this.changed = changed;
    }

    /** A copy of the stack in slot {@code index}. */
    public ItemStack stack(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
    }

    /** These slots as pipes see a machine that only outputs: they can take from them, but put nothing in. */
    public ResourceHandler<ItemResource> extractOnly() {
        return new DelegatingResourceHandler<>(this) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return resource.isEmpty();
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /** Drops what the slots hold at {@code pos}, as a machine does when broken. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = stack(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return accepts.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        changed.run();
    }
}
