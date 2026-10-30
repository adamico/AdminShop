package com.ammonium.adminshop.setup;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.network.PacketSyncMoneyToClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The client's handlers for the payloads {@link Messages} registers as clientbound. */
@EventBusSubscriber(modid = AdminShop.MODID, value = Dist.CLIENT)
public final class ClientMessages {

    private ClientMessages() {
    }

    @SubscribeEvent
    static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(PacketSyncMoneyToClient.TYPE, PacketSyncMoneyToClient::handle);
    }
}
