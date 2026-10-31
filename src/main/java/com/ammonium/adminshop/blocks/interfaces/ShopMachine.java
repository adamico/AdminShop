package com.ammonium.adminshop.blocks.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A machine that works for one team's Account. Every Buyer, Seller and Detector is one. */
public interface ShopMachine {

    /** The id of the owning team, or null while nobody has claimed the machine. */
    @Nullable UUID getTeamId();

    void setTeamId(UUID teamId);

    /** Whether the machine trades Offers without holding their Permits. Only the Creative machines do. */
    default boolean bypassesPermits() {
        return false;
    }
}
