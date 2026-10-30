package com.ammonium.adminshop.network;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.ClientCache;
import com.ammonium.adminshop.money.MoneyHelper;
import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.UUID;

/** Tells a client the Account of the team its player is in: its team, Balance and Permits. */
public record PacketSyncMoneyToClient(UUID teamId, long balance, ImmutableSet<String> permits) implements CustomPacketPayload {

    public static final Type<PacketSyncMoneyToClient> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AdminShop.MODID, "sync_money_to_client"));
    public static final StreamCodec<ByteBuf, PacketSyncMoneyToClient> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PacketSyncMoneyToClient::teamId,
            ByteBufCodecs.LONG, PacketSyncMoneyToClient::balance,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.collection(LinkedHashSet::new)).map(ImmutableSet::copyOf, LinkedHashSet::new),
            PacketSyncMoneyToClient::permits,
            PacketSyncMoneyToClient::new);

    public PacketSyncMoneyToClient(MoneyHelper.MoneyAccount account) {
        this(account.teamId(), account.balance(), account.permits());
    }

    /** Runs on the client's main thread. */
    public static void handle(PacketSyncMoneyToClient packet, IPayloadContext context) {
        AdminShop.LOGGER.debug("Syncing money to client: {} {} {}", packet.teamId, packet.balance, packet.permits);
        ClientCache.setAccount(packet.teamId, packet.balance, packet.permits);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
