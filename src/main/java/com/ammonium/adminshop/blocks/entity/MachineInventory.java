package com.ammonium.adminshop.blocks.entity;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

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

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return accepts.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        changed.run();
    }
}
