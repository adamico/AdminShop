package com.ammonium.adminshop.item;

import com.ammonium.adminshop.screen.ShopMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The Shop Tablet: using it opens the shop screen anywhere. */
public class ShopTablet extends LoreItem {

    public ShopTablet(Properties properties) {
        super(properties, "A very sophisticated PDA. Right-click to open the Shop");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ShopMenu.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
