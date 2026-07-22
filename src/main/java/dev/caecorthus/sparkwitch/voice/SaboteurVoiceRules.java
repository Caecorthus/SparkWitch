package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.events.PacketEvent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative recipient policy for promoted Saboteur voice packets. */
final class SaboteurVoiceRules {
    private SaboteurVoiceRules() {
    }


    static boolean shouldBlockPacket(PacketEvent<?> event) {
        ServerPlayerEntity speaker = player(event.getSenderConnection());
        if (speaker == null || !SaboteurRules.isActivePromotedSaboteur(speaker)) {
            return false;
        }
        ServerPlayerEntity recipient = player(event.getReceiverConnection());
        return !isLivingKiller(recipient);
    }

    static boolean isLivingKiller(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game.isPlayerDead(player.getUuid())) {
            return false;
        }
        Role role = game.getRole(player);
        return role != null && role.getFaction() == Faction.KILLER;
    }

    private static ServerPlayerEntity player(de.maxhenkel.voicechat.api.VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null
                || !(connection.getPlayer().getPlayer() instanceof ServerPlayerEntity player)) {
            return null;
        }
        return player;
    }
}
