package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;

/**
 * Pure fan-out of one attributed sound or voice frame to every listening Blind in the same world whose own range
 * ({@link BlindPerceiver#perceptionRange}, halved for whispers) reaches the source. Sounds are throttled per (emitter,
 * Blind) unless one-shot; voice is already limited per speaker by {@link BlindVoiceInbox} and skips a Blind who cannot
 * hear voice ({@link BlindPerceiver#hearsVoice}). A Blind that is itself the
 * emitter gets an environment-only SELF pulse; an unattributed source is an OBJECT pulse with no emitter (D3). Every
 * pulse lights {@link BlindRules#SOUND_REVEAL_RADIUS} blocks for {@link BlindRules#SOUND_PULSE_TICKS} (C3, C4), except
 * that a Blind's own footstep lights {@link BlindRules#selfStepRevealRadius} for that Blind (12 with ComTac, D14).
 * 将一次已归属的声音或语音帧纯扇出给同一世界中、自身感知距离（{@link BlindPerceiver#perceptionRange}，悄悄话减半）能覆盖
 * 声源的每个聆听盲人。声音按（发声者，盲人）节流，一次性声音除外；语音已由 {@link BlindVoiceInbox} 按说话者限流，
 * 并跳过听不到语音的盲人（{@link BlindPerceiver#hearsVoice}）。盲人
 * 自己就是发声者时收到只照亮环境的 SELF 脉冲；未归属的声源为不带发声者的 OBJECT 脉冲（D3）。每个脉冲照亮
 * {@link BlindRules#SOUND_REVEAL_RADIUS} 格，持续 {@link BlindRules#SOUND_PULSE_TICKS} 刻（C3、C4）；但盲人自己的脚步对其本人
 * 照亮 {@link BlindRules#selfStepRevealRadius} 格（戴 ComTac 时为 12，D14）。
 */
public final class BlindPulseFanout {
    public static final int NO_ENTITY = BlindPulseS2CPayload.NO_EMITTER;
    private static final double MAX_RANGE_SQUARED =
            (double) BlindSoundRules.MAX_PERCEPTION_RANGE * BlindSoundRules.MAX_PERCEPTION_RANGE;

    /**
     * One perceived source after attribution. {@code emitterEntityId} is the attributed player or {@link #NO_ENTITY}
     * (object). The throttle key is {@code keyEntityId} when set (the emitter, else the moving source entity), otherwise
     * {@code keyBlock} (a packed block position). {@code footstep} marks a step sound ({@link BlindSoundRules#isFootstep}).
     * 归属后的一个被感知声源。{@code emitterEntityId} 为归属的玩家，或 {@link #NO_ENTITY}（物体）。节流键在设置了
     * {@code keyEntityId} 时取它（发声者，否则为移动的声源实体），否则取 {@code keyBlock}（打包的方块坐标）。
     * {@code footstep} 标记脚步声（{@link BlindSoundRules#isFootstep}）。
     */
    public record Source(double x, double y, double z, int emitterEntityId, boolean voice, boolean whispering,
                         int keyEntityId, long keyBlock, boolean oneShot, boolean footstep) {
    }

    private BlindPulseFanout() {
    }

    /** Cheap pre-check before attribution. / 归属前的廉价预检。 */
    public static boolean anyInRange(BlindPerceiver[] perceivers, Object world, double x, double y, double z,
                                     boolean whispering) {
        for (BlindPerceiver perceiver : perceivers) {
            if (inRange(perceiver, world, x, y, z, whispering)) {
                return true;
            }
        }
        return false;
    }

    /** Sends the pulses and returns how many were sent. / 发送脉冲并返回发送数量。 */
    public static int fanOut(BlindPerceiver[] perceivers, Object world, Source source) {
        int sent = 0;
        for (BlindPerceiver perceiver : perceivers) {
            if (!inRange(perceiver, world, source.x(), source.y(), source.z(), source.whispering())
                    || !perceiver.isActive()
                    || (source.voice() ? !perceiver.hearsVoice() : !acquire(perceiver, source))) {
                continue;
            }
            perceiver.send(payload(source, perceiver));
            sent++;
        }
        return sent;
    }

    static BlindPulseS2CPayload payload(Source source, BlindPerceiver perceiver) {
        byte kind = kind(source, perceiver.entityId());
        int emitter = kind == BlindPulseS2CPayload.SOUND || kind == BlindPulseS2CPayload.VOICE
                ? source.emitterEntityId()
                : NO_ENTITY;
        int radius = kind == BlindPulseS2CPayload.SELF && source.footstep()
                ? BlindRules.selfStepRevealRadius(perceiver.wearsComTac())
                : BlindRules.SOUND_REVEAL_RADIUS;
        return new BlindPulseS2CPayload((float) source.x(), (float) source.y(), (float) source.z(),
                radius, (short) BlindRules.SOUND_PULSE_TICKS, kind, emitter, null);
    }

    static byte kind(Source source, int blindEntityId) {
        if (source.emitterEntityId() == NO_ENTITY) {
            return BlindPulseS2CPayload.OBJECT;
        }
        if (source.emitterEntityId() == blindEntityId) {
            return BlindPulseS2CPayload.SELF;
        }
        return source.voice() ? BlindPulseS2CPayload.VOICE : BlindPulseS2CPayload.SOUND;
    }

    private static boolean inRange(BlindPerceiver perceiver, Object world, double x, double y, double z,
                                   boolean whispering) {
        if (perceiver == null || perceiver.world() != world) {
            return false;
        }
        double squaredDistance = perceiver.squaredDistanceTo(x, y, z);
        return squaredDistance <= MAX_RANGE_SQUARED && BlindSoundRules.withinRange(squaredDistance,
                BlindSoundRules.effectiveRange(perceiver.perceptionRange(), whispering));
    }

    private static boolean acquire(BlindPerceiver perceiver, Source source) {
        long now = perceiver.time();
        return source.keyEntityId() != NO_ENTITY
                ? perceiver.throttle().tryEntity(source.keyEntityId(), now, source.oneShot())
                : perceiver.throttle().tryBlock(source.keyBlock(), now, source.oneShot());
    }
}
