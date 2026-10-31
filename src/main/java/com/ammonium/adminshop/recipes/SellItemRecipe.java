package com.ammonium.adminshop.recipes;

import com.ammonium.adminshop.shop.ShopGoods;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A Sell Offer for items. The team gives up what {@code sold} names and receives {@code price}. In JSON, {@code item}
 * names one item, and its count is how many one trade takes. {@code tag} names an item tag instead, with
 * {@code count} items per trade (1 by default). An Offer sets one of the two, never both.
 */
public record SellItemRecipe(long price, Sold sold, String permit) implements ShopRecipe {

    /** A Sell Offer for one item. */
    public SellItemRecipe(long price, ItemStackTemplate item, String permit) {
        this(price, new OneItem(item), permit);
    }

    /** A Sell Offer for any item of {@code tag}, {@code count} of them per trade. */
    public SellItemRecipe(long price, TagKey<Item> tag, int count, String permit) {
        this(price, new AnyOfTag(tag, count), permit);
    }

    /** What a Sell Offer takes, either one item or any item of a tag. */
    public sealed interface Sold {

        /** How many items one trade takes. */
        int count();

        /** Whether {@code stack} is an item this Offer takes, whatever its count. */
        boolean takes(ItemStack stack);
    }

    /**
     * One item. If the Offer names components, a stack matches only with exactly those components. If it names
     * none, a stack matches whatever components it carries.
     */
    public record OneItem(ItemStackTemplate item) implements Sold {

        @Override
        public int count() {
            return item.count();
        }

        @Override
        public boolean takes(ItemStack stack) {
            if (stack.isEmpty() || !stack.is(item.item())) {
                return false;
            }
            return item.components().isEmpty() || item.components().equals(stack.getComponentsPatch());
        }
    }

    /** Any item of an item tag, whatever components it carries. */
    public record AnyOfTag(TagKey<Item> tag, int count) implements Sold {

        @Override
        public boolean takes(ItemStack stack) {
            return !stack.isEmpty() && stack.is(tag);
        }
    }

    private static final MapCodec<Sold> SOLD_CODEC = RecordCodecBuilder.<SoldFields>mapCodec(instance -> instance.group(
            ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(SoldFields::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(SoldFields::tag),
            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(SoldFields::count)
    ).apply(instance, SoldFields::new)).flatXmap(SoldFields::sold, SoldFields::of);

    /** The raw JSON fields that say what a Sell Offer takes, before the check that exactly one is set. */
    private record SoldFields(Optional<ItemStackTemplate> item, Optional<TagKey<Item>> tag, int count) {

        DataResult<Sold> sold() {
            if (item.isPresent() && tag.isPresent()) {
                return DataResult.error(() -> "An item Sell Offer takes an item or a tag, not both");
            }
            if (item.isPresent()) {
                return DataResult.success(new OneItem(item.get()));
            }
            if (tag.isPresent()) {
                return DataResult.success(new AnyOfTag(tag.get(), count));
            }
            return DataResult.error(() -> "An item Sell Offer needs an item or a tag");
        }

        static DataResult<SoldFields> of(Sold sold) {
            return DataResult.success(switch (sold) {
                case OneItem one -> new SoldFields(Optional.of(one.item()), Optional.empty(), 1);
                case AnyOfTag anyOf -> new SoldFields(Optional.empty(), Optional.of(anyOf.tag()), anyOf.count());
            });
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Sold> SOLD_STREAM_CODEC = ByteBufCodecs.either(
            ItemStackTemplate.STREAM_CODEC,
            StreamCodec.composite(
                    TagKey.streamCodec(Registries.ITEM), AnyOfTag::tag,
                    ByteBufCodecs.VAR_INT, AnyOfTag::count,
                    AnyOfTag::new))
            .map(either -> either.map(OneItem::new, anyOf -> anyOf), sold -> switch (sold) {
                case OneItem one -> Either.left(one.item());
                case AnyOfTag anyOf -> Either.right(anyOf);
            });

    public static final MapCodec<SellItemRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            OfferCodecs.PRICE.forGetter(SellItemRecipe::price),
            SOLD_CODEC.forGetter(SellItemRecipe::sold),
            OfferCodecs.PERMIT.forGetter(SellItemRecipe::permit)
    ).apply(instance, SellItemRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SellItemRecipe::price,
            SOLD_STREAM_CODEC, SellItemRecipe::sold,
            ByteBufCodecs.STRING_UTF8, SellItemRecipe::permit,
            SellItemRecipe::new);

    public static final RecipeSerializer<SellItemRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    /** How many items one trade takes. */
    public int getCount() {
        return sold.count();
    }

    /** Whether {@code stack} is an item this Offer takes, whatever its count. */
    public boolean isMatchingItemNoCount(ItemStack stack) {
        return sold.takes(stack);
    }

    /** Whether {@code stack} holds enough of an item this Offer takes for one trade. */
    public boolean isMatchingItemStack(ItemStack stack) {
        return isMatchingItemNoCount(stack) && stack.getCount() >= getCount();
    }

    @Override
    public boolean isBuy() {
        return false;
    }

    /**
     * For a tag Offer, every item in the tag whose max stack size reaches the Offer's count. It reads the tags loaded
     * on the current side, so on the client these are the tags the server sent.
     */
    @Override
    public ShopGoods goods() {
        return switch (sold) {
            case OneItem one -> new ShopGoods.Items(List.of(one.item()));
            case AnyOfTag anyOf -> new ShopGoods.Items(StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(anyOf.tag()).spliterator(), false)
                    .filter(item -> item.value().getDefaultMaxStackSize() >= anyOf.count())
                    .map(item -> new ItemStackTemplate(item, anyOf.count()))
                    .toList());
        };
    }

    @Override
    public Component name() {
        return switch (sold) {
            case OneItem one -> one.item().create().getHoverName();
            case AnyOfTag anyOf -> Component.translatable("sellitem.recipe.tag", anyOf.tag().location().toString());
        };
    }

    /** For a tag Offer, the tag's id. */
    @Override
    public String searchTerm() {
        return switch (sold) {
            case OneItem one -> ShopRecipe.super.searchTerm();
            case AnyOfTag anyOf -> anyOf.tag().location().toString().toLowerCase(Locale.ROOT).strip();
        };
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
