package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import dev.caecorthus.sparkwitch.roles.civilian.blind.perception.BlindPerceptionTargets;
import dev.caecorthus.sparkwitch.roles.civilian.blind.perception.BlindVoiceInbox;

/**
 * External seam (Simple Voice Chat, server): the Blind's voice perception (C9, C16). Registered at
 * {@code Integer.MIN_VALUE}, after every muting listener; SVC stops dispatching at the first cancel, so muted speakers
 * (Wraith, kidnapped, silenced, Spirit Walker, Depression, morph reagent) never arrive. Runs on SVC's packet thread:
 * it reads only SVC objects and the volatile active flag and hands (speaker UUID, whisper) to {@link BlindVoiceInbox};
 * the server tick does every world and component read. Only proximity speech counts (no group, or an OPEN group);
 * relayed voice (walkie, morph replay, Taotie) is never a microphone frame of a body here, so it never pulses.
 * 外部接缝（Simple Voice Chat，服务端）：盲人的语音感知（C9、C16）。以 {@code Integer.MIN_VALUE} 注册，排在所有静音监听之后；
 * SVC 在首个取消处停止分发，因此被静音的说话者（怨灵、被绑架、沉默、灵行者、抑郁、变形药剂）永远不会到达这里。运行在 SVC
 * 的数据包线程：只读取 SVC 对象与 volatile 激活标记，并把（说话者 UUID，悄悄话）交给 {@link BlindVoiceInbox}；所有世界与组件
 * 读取都在服务端刻中完成。只计近距离语音（不在群组，或在 OPEN 群组）；转发的语音（对讲机、变形重放、饕餮）不会在此成为
 * 某个身体的麦克风帧，因此不会产生脉冲。
 */
final class BlindVoicePerceptionListener {
    private BlindVoicePerceptionListener() {
    }

    static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!BlindPerceptionTargets.anyActive() || event == null || event.isCancelled()) {
            return;
        }
        MicrophonePacket packet = event.getPacket();
        if (packet == null) {
            return;
        }
        byte[] opus = packet.getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        ServerPlayer player = sender == null ? null : sender.getPlayer();
        if (player == null) {
            return;
        }
        Group group = sender.getGroup();
        if (!BlindVoiceInbox.isProximityVoice(group != null, group != null && group.getType() == Group.Type.OPEN)) {
            return;
        }
        BlindVoiceInbox.get().record(player.getUuid(), packet.isWhispering());
    }
}
