package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.AdminShop;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * A Buyer's tier. It sets the number of output slots, the ticks between purchases and the screen layout.
 */
public enum BuyerTier implements StringRepresentable {
    ONE("1", 1, 40, 80, "buyer"),
    TWO("2", 3, 20, 62, "buyer_2"),
    THREE("3", 5, 10, 44, "buyer_3");

    public static final Codec<BuyerTier> CODEC = StringRepresentable.fromEnum(BuyerTier::values);

    private final String name;
    private final int slots;
    private final int cooldown;
    private final int firstSlotX;
    private final Identifier texture;

    BuyerTier(String name, int slots, int cooldown, int firstSlotX, String texture) {
        this.name = name;
        this.slots = slots;
        this.cooldown = cooldown;
        this.firstSlotX = firstSlotX;
        this.texture = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/" + texture + ".png");
    }

    /** The number of output slots. */
    public int slots() {
        return slots;
    }

    /** The number of ticks between purchases. */
    public int cooldown() {
        return cooldown;
    }

    /** The screen x position of the first output slot. The other slots follow to its right. */
    public int firstSlotX() {
        return firstSlotX;
    }

    /** The screen background texture. */
    public Identifier texture() {
        return texture;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
