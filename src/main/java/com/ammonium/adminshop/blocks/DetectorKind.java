package com.ammonium.adminshop.blocks;

import com.ammonium.adminshop.AdminShop;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The kind of a Detector, Basic or Advanced. It sets how the signal follows the Account's Balance, which sides
 * are powered, the shape and the screen texture.
 */
public enum DetectorKind implements StringRepresentable {
    /** Full power while the Balance is above the threshold, and none otherwise. Shaped as a post with no collision. */
    BASIC("basic", Shapes.box(0.375, 0, 0.375, 0.625, 0.625, 0.625), "detector"),
    /** Power proportional to the Balance, from 0 at no Balance to 15 at the threshold and above. Shaped as a floor plate. */
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

    /**
     * The redstone signal, 0 to 15, for {@code balance} against {@code threshold}. The Advanced ratio is computed
     * in doubles, so with a threshold of 0 any positive Balance gives full power.
     */
    public int signal(long balance, long threshold) {
        return switch (this) {
            case BASIC -> balance > threshold ? 15 : 0;
            case ADVANCED -> balance <= 0 ? 0 : (int) Math.min(Math.floor(balance / (double) threshold * 15), 15);
        };
    }

    /**
     * Whether a Detector of this kind strongly powers the neighbour in {@code direction}. As in
     * {@code getDirectSignal}, {@code direction} points from that neighbour towards the Detector. Neither kind gives
     * a weak signal in the {@code UP} direction.
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

    /** This kind's screen background. The confirm button always comes from the Basic Detector's texture. */
    public Identifier texture() {
        return texture;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
