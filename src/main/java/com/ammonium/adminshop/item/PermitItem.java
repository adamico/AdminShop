package com.ammonium.adminshop.item;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.MoneyHelper;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The Trade Permit item. Shift-clicking it in the shop screen uses it up and unlocks its Permit for the
 * player's Account. The Permit is the {@code key} of its {@code minecraft:custom_data}, so
 * {@code /give @p adminshop:permit[custom_data={key:"gems"}]} makes one.
 */
public class PermitItem extends LoreItem {
    private static final String KEY = "key";

    public PermitItem(Properties properties) {
        super(properties, "Shift-click inside a shop to unlock new trades");
    }

    /** Creates a Trade Permit for {@code permit}. */
    public static ItemStack of(String permit) {
        ItemStack stack = new ItemStack(ModItems.PERMIT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, permit);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** Returns the Permit that {@code stack} unlocks, if it is a Trade Permit that names one. */
    public static Optional<String> permit(ItemStack stack) {
        if (!stack.is(ModItems.PERMIT.get())) {
            return Optional.empty();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(KEY)
                .filter(permit -> !permit.isEmpty());
    }

    /**
     * Uses up one Trade Permit from the player's main inventory {@code slot} and unlocks its Permit for their
     * Account. Returns false if the slot holds no Trade Permit. Only the redeeming player hears the sound.
     */
    public static boolean redeem(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
            return false;
        }
        Optional<String> permit = permit(player.getInventory().getItem(slot));
        if (permit.isEmpty()) {
            return false;
        }
        MoneyHelper accounts = MoneyHelper.get(player.level());
        MoneyHelper.MoneyAccount account = accounts.getPlayerAccount(player);
        AdminShop.LOGGER.info("Adding permit {} to {}", permit.get(), account.teamId());
        accounts.addPermit(account.teamId(), permit.get());
        player.getInventory().removeItem(slot, 1);
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE),
                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
        player.sendSystemMessage(Component.translatable("message.adminshop.add_permit", permit.get(), account.name()));
        return true;
    }
}
