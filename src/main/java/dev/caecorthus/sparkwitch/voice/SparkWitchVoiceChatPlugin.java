package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithCommunicationPolicy;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Simple Voice Chat bridge for Wraith lifecycle voice rules, plus the Blind's lowest-priority voice perception.
 * Simple Voice Chat 桥接：负责冤魂生命周期各阶段的语音规则，并以最低优先级提供盲人的语音感知。
 * On the physical client it also wires the Holy Flash incoming-voice muffle via {@link HolyFlashVoiceClientBridge}.
 * 在物理客户端上还会通过 {@link HolyFlashVoiceClientBridge} 接入圣光弹的传入语音压低。
 */
public final class SparkWitchVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SparkWitch.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(
                MicrophonePacketEvent.class,
                this::blockWraithSpeaker,
                Integer.MAX_VALUE
        );
        /*
         * 必须在声音包阶段过滤，而不能在这里直接取消已晋升身份的麦克风事件：
         * Wind Spirit、Vendetta、Curser 和 Saboteur 的对讲机功能依赖 Wathe
         * TrainVoicePlugin 继续收到 MicrophonePacketEvent。
         *
         * 三类声音包都要过滤，因为原生近距离语音、实体语音和插件转发的对讲机
         * 可能走不同的包类型。
         */
        registration.registerEvent(EntitySoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        registration.registerEvent(StaticSoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        // Lowest priority: the Blind only perceives speakers that no listener muted. / 最低优先级：盲人只感知未被静音的说话者。
        registration.registerEvent(
                MicrophonePacketEvent.class,
                BlindVoicePerceptionListener::onMicrophonePacket,
                Integer.MIN_VALUE
        );
        // Physical client only: Holy Flash muffles incoming voice; a no-op on dedicated servers.
        // 仅物理客户端：圣光弹压低传入语音；专用服务器上不做任何事。
        HolyFlashVoiceClientBridge.register(registration);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockRestrictedRecipient(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        if (SaboteurVoiceRules.shouldBlockPacket(event) || shouldBlockWraithRecipient(event)) {
            event.cancel();
        }
    }

    private boolean shouldBlockWraithRecipient(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        ServerPlayerEntity recipient = player(event.getReceiverConnection());
        if (recipient == null) {
            return false;
        }

        Role role = GameWorldComponent.KEY.get(recipient.getServerWorld()).getRole(recipient);
        boolean activeWraith = WraithStateService.isActive(recipient);
        boolean promotedWraith = WraithStateService.isPromoted(recipient);
        boolean guardianAngel = GuardianAngelRules.isGuardianAngel(role);

        /*
         * 未晋升冤魂继续保持完全静音；已晋升身份不再被普通 Wraith 接收限制
         * 拦截。Saboteur 的阵营语音限制已经由 SaboteurVoiceRules 双向处理。
         */
        if (WraithCommunicationPolicy.shouldBlockCommunication(
                activeWraith,
                promotedWraith,
                guardianAngel,
                recipient.isCreative()
        )) {
            return true;
        }

        ServerPlayerEntity speaker = soundPacketSpeaker(event, recipient);
        if (speaker == null) {
            return false;
        }

        Role speakerRole = GameWorldComponent.KEY.get(speaker.getServerWorld()).getRole(speaker);
        boolean speakerActiveWraith = WraithStateService.isActive(speaker);
        boolean speakerPromotedWraith = WraithStateService.isPromoted(speaker);
        boolean speakerGuardianAngel = GuardianAngelRules.isGuardianAngel(speakerRole);
        boolean speakerSaboteur = SaboteurRules.isActivePromotedSaboteur(speaker);
        boolean walkieTalkiePacket = isWalkieTalkiePacket(event);
        boolean recipientPlayingAndAlive = GameFunctions.isPlayerPlayingAndAlive(recipient);
        boolean livingWitchFactionRecipient = WitchFactionRules.isGrandWitch(role)
                || WitchFactionRules.isAccompliceLike(role);

        /*
         * 诅咒者是转身后需要继续和存活魔女阵营协作的特殊身份。
         * 这里只给大魔女 / 共犯补一条直通例外，其他存活玩家仍然交给下面的
         * 通用冤魂语音规则处理。
         */
        if (WraithCommunicationPolicy.shouldAllowPromotedCurserVoiceToLivingWitchFaction(
                speakerActiveWraith,
                speakerPromotedWraith,
                CurserFeatureService.isActivePromotedCurser(speaker),
                speaker.isCreative(),
                recipientPlayingAndAlive,
                livingWitchFactionRecipient
        )) {
            return false;
        }

        /*
         * 守护天使保留 Wathe 隐藏死者组。它可以向死者组说话，也可以从正常
         * 存活玩家处接收近距离语音，但任何发往正常存活玩家的声音包都必须取消。
         * 这里包含对讲机包，解决 Wathe 手动复制语音绕过分组的问题。
         */
        if (WraithCommunicationPolicy.shouldBlockGuardianAngelVoiceToLiving(
                speakerActiveWraith,
                speakerPromotedWraith,
                speakerGuardianAngel,
                recipientPlayingAndAlive
        )) {
            return true;
        }

        /*
         * 风精灵、仇杀客和诅咒者可以继续使用对讲机，但普通近距离语音不能
         * 被正常存活玩家听到。不能取消麦克风事件，否则 Wathe 的对讲机转发
         * 也会一起失效，所以只在最终声音包阶段屏蔽。
         */
        return WraithCommunicationPolicy.shouldBlockPromotedCivilianVoiceToLiving(
                speakerActiveWraith,
                speakerPromotedWraith,
                speakerGuardianAngel,
                speakerSaboteur,
                speaker.isCreative(),
                walkieTalkiePacket,
                recipientPlayingAndAlive
        );
    }

    private void blockWraithSpeaker(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null
                || event.getSenderConnection().getPlayer() == null
                || event.getSenderConnection().getPlayer().getPlayer() == null) {
            return;
        }
        ServerPlayerEntity speaker = (ServerPlayerEntity) event.getSenderConnection().getPlayer().getPlayer();
        if (KidnapperControlComponent.KEY.get(speaker).isControlled()
                && GameFunctions.isPlayerAliveAndSurvival(speaker)) {
            // 迷药控制期间目标黑屏且无法主动行动；语音也必须在同一入口静音，避免报点破坏劫持效果。
            event.cancel();
            return;
        }
        if (RiftSessionService.isInside(speaker)) {
            // Rift Gate occupants are muted but still hear (plan §6.6): no voice may leak from a gate, and the NR
            // Paranoid would otherwise hear them as non-swallowed spectators.
            // 裂隙门内的玩家被静音但仍能听见（plan §6.6）：门口不得传出人声，否则 NR 偏执杀手会把他们当作未被吞的旁观者听到。
            event.cancel();
            return;
        }
        if (RecruitmentHold.isHeld(speaker)) {
            // A held recruit is invisible inside the Grand Witch, and the stun lock leaves voice keys usable, so the
            // hold mutes them here; they still hear. The Blind voice listener skips cancelled packets.
            // 被定身的新共犯隐身站在大魔女体内，而眩晕锁保留语音按键，因此在此静音；仍能听见。盲人语音监听会跳过已取消的数据包。
            event.cancel();
            return;
        }
        Role role = GameWorldComponent.KEY.get(speaker.getServerWorld()).getRole(speaker);
        if (WraithCommunicationPolicy.shouldBlockCommunication(
                WraithStateService.isActive(speaker),
                WraithStateService.isPromoted(speaker),
                GuardianAngelRules.isGuardianAngel(role),
                speaker.isCreative()
        )) {
            // 只有未晋升的普通冤魂在麦克风入口静音；已晋升身份必须放行，
            // 否则 Wathe 无法收到它们的对讲机语音。
            event.cancel();
        }
    }

    private boolean isWalkieTalkiePacket(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        return event instanceof SoundPacketEvent<?> soundPacketEvent
                && TrainVoicePlugin.WALKIE_TALKIE_CATEGORY.equals(
                soundPacket(soundPacketEvent).getCategory()
        );
    }

    /**
     * 优先使用事件提供的发送者连接；Wathe 通过 VoicechatServerApi 手动发送
     * 对讲机包时，部分版本的事件可能没有 senderConnection，因此再从声音包
     * 自带的 sender UUID 解析说话者。
     */
    private ServerPlayerEntity soundPacketSpeaker(
            de.maxhenkel.voicechat.api.events.PacketEvent<?> event,
            ServerPlayerEntity recipient
    ) {
        ServerPlayerEntity speaker = player(event.getSenderConnection());
        if (speaker != null || !(event instanceof SoundPacketEvent<?> soundPacketEvent)
                || recipient.getServer() == null
                || soundPacket(soundPacketEvent).getSender() == null) {
            return speaker;
        }
        return recipient.getServer().getPlayerManager().getPlayer(
                soundPacket(soundPacketEvent).getSender()
        );
    }

    private SoundPacket soundPacket(SoundPacketEvent<?> event) {
        /*
         * voicechat-api 的 SoundPacketEvent 泛型上界是基础 Packet；
         * 三类服务端声音事件的运行时包统一实现 SoundPacket。
         */
        return (SoundPacket) event.getPacket();
    }

    private ServerPlayerEntity player(de.maxhenkel.voicechat.api.VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null
                || !(connection.getPlayer().getPlayer() instanceof ServerPlayerEntity player)) {
            return null;
        }
        return player;
    }
}
