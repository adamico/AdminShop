package com.ammonium.adminshop.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/** The JSON fields every Offer shares, with their 1.21.1 names. */
final class OfferCodecs {

    private OfferCodecs() {
    }

    static final MapCodec<Long> PRICE = Codec.LONG
            .validate(price -> price >= 0 ? DataResult.success(price)
                    : DataResult.error(() -> "An Offer's price can't be negative: " + price))
            .fieldOf("price");

    static final MapCodec<String> PERMIT = Codec.STRING.optionalFieldOf("permit", "");
}
