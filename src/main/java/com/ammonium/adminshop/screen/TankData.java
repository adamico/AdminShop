package com.ammonium.adminshop.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Carries a fluid to a fluid machine's screen in {@link #SLOTS} data slots: the fluid and its amount. A data slot
 * travels as a signed 16-bit number, so each is read back as an unsigned one, up to {@link #MAX_AMOUNT}; the fluid
 * machines' tanks hold less. Components don't travel: the screen only draws the fluid.
 */
public final class TankData {
    public static final int SLOTS = 2;
    public static final int MAX_AMOUNT = 0xFFFF;

    private static final int FLUID = 0;
    private static final int AMOUNT = 1;

    private TankData() {
    }

    /** Data slot {@code slot} (0 to {@link #SLOTS} - 1) of {@code fluid}. */
    public static int encode(FluidStack fluid, int slot) {
        if (fluid.isEmpty()) {
            return 0;
        }
        return switch (slot) {
            // 0 is no fluid.
            case FLUID -> BuiltInRegistries.FLUID.getId(fluid.getFluid()) + 1;
            case AMOUNT -> Math.min(fluid.getAmount(), MAX_AMOUNT);
            default -> 0;
        };
    }

    /** The fluid in {@code data}'s slots from {@code first} on, as the client received them. */
    public static FluidStack decode(ContainerData data, int first) {
        int id = data.get(first + FLUID) & 0xFFFF;
        int amount = data.get(first + AMOUNT) & 0xFFFF;
        if (id == 0 || amount == 0) {
            return FluidStack.EMPTY;
        }
        return BuiltInRegistries.FLUID.get(id - 1)
                .map(fluid -> new FluidStack(fluid, amount))
                .orElse(FluidStack.EMPTY);
    }
}
