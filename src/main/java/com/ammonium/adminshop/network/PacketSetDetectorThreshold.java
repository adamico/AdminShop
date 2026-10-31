package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.DetectorMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks the server to set the threshold of the Detector whose screen the player has open; see {@link DetectorMenu#setThreshold}. */
public record PacketSetDetectorThreshold(long threshold) implements CustomPacketPayload {

    public static final Type<PacketSetDetectorThreshold> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdminShop.MODID, "set_detector_threshold"));
    public static final StreamCodec<ByteBuf, PacketSetDetectorThreshold> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(PacketSetDetectorThreshold::new, PacketSetDetectorThreshold::threshold);

    /** Runs on the server's main thread. */
    public static void handle(PacketSetDetectorThreshold packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DetectorMenu.setThreshold(player, packet.threshold);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
