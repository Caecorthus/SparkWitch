package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Thread hand-off for voice perception (C9, C16). Simple Voice Chat's packet thread {@link #record records} only the
 * speaker's UUID and whisper flag (one reusable entry per speaker, no world or component access); the server tick
 * {@link #drain drains} it into at most one VOICE pulse per speaker per {@link #VOICE_INTERVAL_TICKS}. Holds no Simple
 * Voice Chat type, so the Blind works when the mod is absent (nothing is ever recorded then).
 * 语音感知的线程交接（C9、C16）。Simple Voice Chat 的数据包线程只 {@link #record 记录} 说话者 UUID 与悄悄话标记（每名说话者
 * 一个可复用条目，不访问世界或组件）；服务端刻将其 {@link #drain 取出}，每名说话者每 {@link #VOICE_INTERVAL_TICKS} 刻至多
 * 一个 VOICE 脉冲。不持有任何 Simple Voice Chat 类型，因此该模组缺席时盲人照常可用（此时从不记录）。
 */
public final class BlindVoiceInbox {
    public static final int VOICE_INTERVAL_TICKS = BlindRules.EMITTER_THROTTLE_TICKS;
    private static final BlindVoiceInbox SHARED = new BlindVoiceInbox();
    private static final Function<UUID, Speaker> NEW_SPEAKER = ignored -> new Speaker();

    private final ConcurrentHashMap<UUID, Speaker> speakers = new ConcurrentHashMap<>();

    /** Receives one due speaker on the server thread. / 在服务端线程接收一名到期的说话者。 */
    @FunctionalInterface
    public interface Sink {
        void pulse(UUID speaker, boolean whispering);
    }

    private static final class Speaker {
        private volatile boolean pending;
        private volatile boolean whispering;
        private long nextPulseTick = Long.MIN_VALUE;
    }

    public static BlindVoiceInbox get() {
        return SHARED;
    }

    /**
     * Only proximity speech can be heard where the speaker stands: no group, or an OPEN group (SVC sends proximity audio
     * only then). Pure.
     * 只有近距离语音能在说话者所在处被听见：不在群组中，或在 OPEN 群组中（只有此时 SVC 才发送近距离音频）。纯函数。
     */
    public static boolean isProximityVoice(boolean inGroup, boolean openGroup) {
        return !inGroup || openGroup;
    }

    /** Any thread (SVC packet thread): one microphone frame. / 任意线程（SVC 数据包线程）：一帧麦克风数据。 */
    public void record(@Nullable UUID speaker, boolean whispering) {
        if (speaker == null) {
            return;
        }
        Speaker entry = speakers.computeIfAbsent(speaker, NEW_SPEAKER);
        entry.whispering = whispering;
        entry.pending = true;
    }

    /** Server thread: hands every due speaker to {@code sink}; returns how many. / 服务端线程：把到期说话者交给 {@code sink}。 */
    public int drain(long now, Sink sink) {
        if (speakers.isEmpty()) {
            return 0;
        }
        int drained = 0;
        for (Map.Entry<UUID, Speaker> entry : speakers.entrySet()) {
            Speaker speaker = entry.getValue();
            if (!speaker.pending || now < speaker.nextPulseTick) {
                continue;
            }
            speaker.pending = false;
            speaker.nextPulseTick = now + VOICE_INTERVAL_TICKS;
            sink.pulse(entry.getKey(), speaker.whispering);
            drained++;
        }
        return drained;
    }

    /** Server thread: forgets speakers idle past their window. / 服务端线程：移除窗口已过且空闲的说话者。 */
    public void prune(long now) {
        for (Iterator<Speaker> iterator = speakers.values().iterator(); iterator.hasNext(); ) {
            Speaker speaker = iterator.next();
            if (!speaker.pending && now >= speaker.nextPulseTick) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        speakers.clear();
    }

    public int size() {
        return speakers.size();
    }
}
