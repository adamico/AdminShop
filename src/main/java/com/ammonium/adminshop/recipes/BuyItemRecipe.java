package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.shop.ShopGoods;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** A Buy Offer for items. The team pays {@code price} and receives {@code result}, an item stack with components. */
public record BuyItemRecipe(long price, ItemStackTemplate result, String permit) implements ShopRecipe {

    public static final MapCodec<BuyItemRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            OfferCodecs.PRICE.forGetter(BuyItemRecipe::price),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(BuyItemRecipe::result),
            OfferCodecs.PERMIT.forGetter(BuyItemRecipe::permit)
    ).apply(instance, BuyItemRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyItemRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BuyItemRecipe::price,
            ItemStackTemplate.STREAM_CODEC, BuyItemRecipe::result,
            ByteBufCodecs.STRING_UTF8, BuyItemRecipe::permit,
            BuyItemRecipe::new);

    public static final RecipeSerializer<BuyItemRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** A new copy of the items one trade hands over. */
    public ItemStack getItem() {
        return result.create();
    }

    /** How many items one trade hands over. */
    public int getCount() {
        return result.count();
    }

    @Override
    public boolean isBuy() {
        return true;
    }

    @Override
    public ShopGoods goods() {
        return new ShopGoods.Items(List.of(result));
    }

    @Override
    public Component name() {
        return getItem().getHoverName();
    }

    @Override
    public RecipeSerializer<BuyItemRecipe> getSerializer() {
        return ModRecipeSerializers.SHOP_BUY_ITEM.get();
    }

    @Override
    public RecipeType<BuyItemRecipe> getType() {
        return ModRecipeTypes.SHOP_BUY_ITEM.get();
    }
}
