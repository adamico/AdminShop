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
 * A Sell Offer for a fluid. The team gives up {@code result} and receives {@code price}. {@code result} is a NeoForge
 * fluid stack with {@code id}, {@code amount} in millibuckets and optional {@code components}. The fluid comes from a
 * fluid container by hand, or from a Fluid Seller's tank.
 */
public record SellFluidRecipe(long price, FluidStackTemplate result, String permit) implements ShopRecipe {

    public static final MapCodec<SellFluidRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            OfferCodecs.PRICE.forGetter(SellFluidRecipe::price),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(SellFluidRecipe::result),
            OfferCodecs.PERMIT.forGetter(SellFluidRecipe::permit)
    ).apply(instance, SellFluidRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SellFluidRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SellFluidRecipe::price,
            FluidStackTemplate.STREAM_CODEC, SellFluidRecipe::result,
            ByteBufCodecs.STRING_UTF8, SellFluidRecipe::permit,
            SellFluidRecipe::new);

    public static final RecipeSerializer<SellFluidRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** The fluid one trade takes, whatever its amount. */
    public FluidResource fluid() {
        return FluidResource.of(result);
    }

    /** How many millibuckets one trade takes. */
    public int amount() {
        return result.amount();
    }

    /** Whether this Offer takes {@code fluid}. It must be the same fluid with the same components. */
    public boolean takes(FluidResource fluid) {
        return !fluid.isEmpty() && fluid.equals(fluid());
    }

    @Override
    public boolean isBuy() {
        return false;
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
    public RecipeSerializer<SellFluidRecipe> getSerializer() {
        return ModRecipeSerializers.SHOP_SELL_FLUID.get();
    }

    @Override
    public RecipeType<SellFluidRecipe> getType() {
        return ModRecipeTypes.SHOP_SELL_FLUID.get();
    }
}
