package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.AdminShop;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Whether a Detector is Basic or Advanced: how its signal follows its Account's Balance, which sides it powers,
 * its shape and how its screen looks.
 */
public enum DetectorKind implements StringRepresentable {
    /** Full power while the Balance is above the threshold, none at or below it. A post, walked through. */
    BASIC("basic", Shapes.box(0.375, 0, 0.375, 0.625, 0.625, 0.625), "detector"),
    /** Power in proportion to the Balance, from none at 0 to 15 at the threshold and above. A plate on the floor. */
    ADVANCED("advanced", Shapes.box(0, 0, 0, 1, 0.125, 1), "adv_detector");

    public static final Codec<DetectorKind> CODEC = StringRepresentable.fromEnum(DetectorKind::values);

    private final String name;
    private final VoxelShape shape;
    private final Identifier texture;

    DetectorKind(String name, VoxelShape shape, String texture) {
        this.name = name;
        this.shape = shape;
        this.texture = Identifier.fromNamespaceAndPath(AdminShop.MODID, "textures/gui/" + texture + ".png");
    }

    /** The redstone signal, 0 to 15, of a Detector of this kind with {@code threshold} over {@code balance}. */
    public int signal(long balance, long threshold) {
        return switch (this) {
            case BASIC -> balance > threshold ? 15 : 0;
            // As on 1.21.1, in doubles: with no threshold, any Balance gives full power and none gives none.
            case ADVANCED -> balance <= 0 ? 0 : (int) Math.min(Math.floor(balance / (double) threshold * 15), 15);
        };
    }

    /**
     * Whether a Detector of this kind strongly powers the block on its {@code direction} side, as
     * {@code getDirectSignal} names it (towards the Detector from that block). Neither gives a weak signal upwards.
     */
    public boolean powersStrongly(Direction direction) {
        return switch (this) {
            case BASIC -> direction != Direction.DOWN;
            case ADVANCED -> direction != Direction.UP;
        };
    }

    public VoxelShape shape() {
        return shape;
    }

    /** The background of this kind's screen; its confirm button is always drawn from the Basic Detector's. */
    public Identifier texture() {
        return texture;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
