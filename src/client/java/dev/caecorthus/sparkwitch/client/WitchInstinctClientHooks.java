package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.impl.GrandWitchRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Grants only the visual instinct lightmap bridge for Grand Witch faction members.
 * 只给大魔女阵营成员接入本能亮度过渡，不授予 wathe 原生杀手能力。
 */
public final class WitchInstinctClientHooks {
    private WitchInstinctClientHooks() {
    }

    public static boolean usesKillerStyleInstinctLight() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !WatheClient.isInstinctEnabled() || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return GrandWitchRules.usesKillerStyleInstinctLight(role);
    }
}
