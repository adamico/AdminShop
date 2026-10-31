package com.ammonium.adminshop.blocks.entity;

import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A fluid machine's tank, exposed through the fluid capability. It holds up to {@code capacity} millibuckets of one
 * fluid, accepts only what {@code accepts} allows and runs {@code changed} on every change.
 */
public class MachineTank extends FluidStacksResourceHandler {
    private final Predicate<FluidResource> accepts;
    private final Runnable changed;

    public MachineTank(int capacity, Predicate<FluidResource> accepts, Runnable changed) {
        super(1, capacity);
        this.accepts = accepts;
        this.changed = changed;
    }

    /** A copy of the tank's contents. */
    public FluidStack fluid() {
        return getResource(0).toStack(getAmountAsInt(0));
    }

    public int capacity() {
        return capacity;
    }

    /** A view of the tank for an output-only machine. Pipes can drain it but cannot fill it. */
    public ResourceHandler<FluidResource> extractOnly() {
        return new DelegatingResourceHandler<>(this) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return resource.isEmpty();
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int insert(FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return accepts.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        changed.run();
    }
}
