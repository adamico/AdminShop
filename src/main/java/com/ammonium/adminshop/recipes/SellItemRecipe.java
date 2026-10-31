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

/**
 * A Sell Offer for items: the team gives up {@code item} (its count is how many one trade takes) and
 * receives {@code price}. An item carrying components only matches when the Offer names the same ones;
 * an Offer naming none matches the item whatever components it carries, as on 1.21.1.
 */
public record SellItemRecipe(long price, ItemStackTemplate item, String permit) implements ShopRecipe {

    public static final MapCodec<SellItemRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            OfferCodecs.PRICE.forGetter(SellItemRecipe::price),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(SellItemRecipe::item),
            OfferCodecs.PERMIT.forGetter(SellItemRecipe::permit)
    ).apply(instance, SellItemRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SellItemRecipe::price,
            ItemStackTemplate.STREAM_CODEC, SellItemRecipe::item,
            ByteBufCodecs.STRING_UTF8, SellItemRecipe::permit,
            SellItemRecipe::new);

    public static final RecipeSerializer<SellItemRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** How many items one trade takes. */
    public int getCount() {
        return item.count();
    }

    /** Whether {@code stack} is the item this Offer takes, whatever its count. */
    public boolean isMatchingItemNoCount(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(item.item())) {
            return false;
        }
        return item.components().isEmpty() || item.components().equals(stack.getComponentsPatch());
    }

    /** Whether {@code stack} holds enough of this Offer's item for one trade. */
    public boolean isMatchingItemStack(ItemStack stack) {
        return isMatchingItemNoCount(stack) && stack.getCount() >= getCount();
    }

    @Override
    public boolean isBuy() {
        return false;
    }

    @Override
    public ShopGoods goods() {
        return new ShopGoods.Items(List.of(item));
    }

    @Override
    public Component name() {
        return item.create().getHoverName();
    }

    @Override
    public RecipeSerializer<SellItemRecipe> getSerializer() {
        return ModRecipeSerializers.SHOP_SELL_ITEM.get();
    }

    @Override
    public RecipeType<SellItemRecipe> getType() {
        return ModRecipeTypes.SHOP_SELL_ITEM.get();
    }
}
