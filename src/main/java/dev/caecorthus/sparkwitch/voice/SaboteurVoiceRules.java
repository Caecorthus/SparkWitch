package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.events.PacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative recipient policy for promoted Saboteur voice packets. */
final class SaboteurVoiceRules {
    private SaboteurVoiceRules() {
    }


    static boolean shouldBlockPacket(PacketEvent<?> event) {
        ServerPlayerEntity speaker = player(event.getSenderConnection());
        ServerPlayerEntity recipient = player(event.getReceiverConnection());
        if (!(event instanceof SoundPacketEvent<?> soundPacketEvent)) {
            return false;
        }

        if (speaker == null) {
            speaker = playerByPacketSender(soundPacketEvent, recipient);
        }
        if (speaker == null || recipient == null) {
            return false;
        }

        boolean speakerIsSaboteur = SaboteurRules.isActivePromotedSaboteur(speaker);
        boolean recipientIsSaboteur = SaboteurRules.isActivePromotedSaboteur(recipient);
        if (!speakerIsSaboteur && !recipientIsSaboteur) {
            return false;
        }

        /*
         * 破坏者的语音边界是双向的：
         * 1. 破坏者说话时，只允许 Wathe 判定为正常存活的杀手阵营接收；
         * 2. 破坏者接收语音时，只允许正常存活的杀手阵营说话。
         *
         * 已晋升破坏者仍然保留 Wraith 的死亡记录，因此不能把它自身当作
         * isPlayerPlayingAndAlive 的“存活杀手”端点；这样可以保持“只和存活杀手
         * 沟通”的原有规则。
         */
        if (speakerIsSaboteur && !isLivingKiller(recipient)) {
            return true;
        }
        return recipientIsSaboteur && !isLivingKiller(speaker);
    }

    static boolean isLivingKiller(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        Role role = game.getRole(player);
        return role != null && role.getFaction() == Faction.KILLER;
    }

    private static ServerPlayerEntity playerByPacketSender(
            SoundPacketEvent<?> event,
            ServerPlayerEntity recipient
    ) {
        SoundPacket packet = soundPacket(event);
        if (recipient == null || recipient.getServer() == null || packet.getSender() == null) {
            return null;
        }
        return recipient.getServer().getPlayerManager().getPlayer(packet.getSender());
    }

    private static SoundPacket soundPacket(SoundPacketEvent<?> event) {
        /*
         * voicechat-api 的 SoundPacketEvent 泛型上界仍是基础 Packet，
         * 但 Entity/Locational/StaticSoundPacketEvent 的实际包都实现了 SoundPacket。
         */
        return (SoundPacket) event.getPacket();
    }

    private static ServerPlayerEntity player(de.maxhenkel.voicechat.api.VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null
                || !(connection.getPlayer().getPlayer() instanceof ServerPlayerEntity player)) {
            return null;
        }
        return player;
    }
}
