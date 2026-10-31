package com.ammonium.adminshop.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Sends a fluid to a fluid machine's screen in {@link #SLOTS} data slots, one for the fluid and one for its amount.
 * A data slot travels as a signed 16-bit number, so each is read back as unsigned. Amounts are capped at
 * {@link #MAX_AMOUNT}, which is more than any fluid machine's tank holds. Components are not sent, because the screen
 * only draws the fluid.
 */
public final class TankData {
    public static final int SLOTS = 2;
    public static final int MAX_AMOUNT = 0xFFFF;

    private static final int FLUID = 0;
    private static final int AMOUNT = 1;

    private TankData() {
    }

    /**
     * The value of data slot {@code slot}, from 0 to {@link #SLOTS} - 1, for {@code fluid}. The fluid slot holds the
     * registry id plus one, so 0 means no fluid.
     */
    public static int encode(FluidStack fluid, int slot) {
        if (fluid.isEmpty()) {
            return 0;
        }
        return switch (slot) {
            case FLUID -> BuiltInRegistries.FLUID.getId(fluid.getFluid()) + 1;
            case AMOUNT -> Math.min(fluid.getAmount(), MAX_AMOUNT);
            default -> 0;
        };
    }

    /** Reads the fluid from the data slots of {@code data}, starting at {@code first}. */
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
