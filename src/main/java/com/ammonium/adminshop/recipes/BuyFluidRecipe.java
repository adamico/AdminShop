package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.shop.ShopGoods;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A Buy Offer for a fluid. The team pays {@code price} and receives {@code result}, a NeoForge fluid stack with
 * {@code id}, {@code amount} in millibuckets and optional {@code components}. The fluid goes into a fluid container
 * by hand, or into a Fluid Buyer's tank.
 */
public record BuyFluidRecipe(long price, FluidStackTemplate result, String permit) implements ShopRecipe {

    public static final MapCodec<BuyFluidRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            OfferCodecs.PRICE.forGetter(BuyFluidRecipe::price),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(BuyFluidRecipe::result),
            OfferCodecs.PERMIT.forGetter(BuyFluidRecipe::permit)
    ).apply(instance, BuyFluidRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyFluidRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BuyFluidRecipe::price,
            FluidStackTemplate.STREAM_CODEC, BuyFluidRecipe::result,
            ByteBufCodecs.STRING_UTF8, BuyFluidRecipe::permit,
            BuyFluidRecipe::new);

    public static final RecipeSerializer<BuyFluidRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** The fluid one trade hands over, whatever its amount. */
    public FluidResource fluid() {
        return FluidResource.of(result);
    }

    /** How many millibuckets one trade hands over. */
    public int amount() {
        return result.amount();
    }

    @Override
    public boolean isBuy() {
        return true;
    }

    @Override
    public ShopGoods goods() {
        return new ShopGoods.Fluid(result);
    }

    @Override
    public Component name() {
        return result.create().getHoverName();
    }

    @Override
    public RecipeSerializer<BuyFluidRecipe> getSerializer() {
        return ModRecipeSerializers.SHOP_BUY_FLUID.get();
    }

    @Override
    public RecipeType<BuyFluidRecipe> getType() {
        return ModRecipeTypes.SHOP_BUY_FLUID.get();
    }
}
