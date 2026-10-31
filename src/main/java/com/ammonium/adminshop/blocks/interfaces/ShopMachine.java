package com.ammonium.adminshop.blocks.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A machine that trades for one team's Account: every Buyer, Seller and Detector. */
public interface ShopMachine {

    /** The id of the team whose Account the machine trades for, or null while nobody has claimed it. */
    @Nullable UUID getTeamId();

    void setTeamId(UUID teamId);

    /** Whether the machine trades Offers without the Permit they need: only the Creative machines do. */
    default boolean bypassesPermits() {
        return false;
    }
}
