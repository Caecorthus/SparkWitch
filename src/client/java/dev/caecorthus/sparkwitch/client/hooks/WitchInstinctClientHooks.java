package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.client.curser.CurserInstinctClientRules;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules.MurderousWitchRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
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
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }

        boolean instinctEnabled = WatheClient.isInstinctEnabled();
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (CurserInstinctClientRules.shouldUseWitchInstinctLight(
                role == null ? null : role.identifier(),
                SparkWitchServerConnection.isConfirmedServer(),
                WraithClientState.isActive(player),
                WraithClientState.isPromoted(player),
                GameFunctions.isPlayerSpectatingOrCreative(player),
                instinctEnabled
        )) {
            return true;
        }
        if (!instinctEnabled || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }

        return WitchFactionRules.usesKillerStyleInstinctLight(role)
                || MurderousWitchRules.usesKillerStyleInstinctLight(role);
    }
}
