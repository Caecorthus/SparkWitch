package dev.caecorthus.sparkwitch.client.prophet;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * UI-only one-shot Prophecy state: one outstanding request, then one pending session that a single confirmation
 * consumes. It mirrors the server session lifetime (minus a small margin) so an expired session closes the screen
 * instead of spending a click on a refusal. Payment, nonce and guess validation remain server-owned.
 * 仅界面使用的一次性预言状态：至多一个未完成的请求，之后一个待确认会话由单次确认消费。它镜像服务端会话时长
 * （留少量余量），会话过期时直接关闭界面，而不是让一次点击换来拒绝提示。扣费、随机标识与猜测校验仍由服务端负责。
 */
public final class ProphetProphecySelectionState {
    /**
     * Short on purpose: refusals (cooldown, no dead, the silent server open throttle) send no session, so this is how
     * long the key stays locked after one. It must stay above {@code ProphetProphecySessions.OPEN_INTERVAL_TICKS}.
     * 有意设短：拒绝（冷却、无死者、服务端静默的打开节流）不会回传会话，按键会被锁住这么久。必须大于
     * {@code ProphetProphecySessions.OPEN_INTERVAL_TICKS}。
     */
    public static final int REQUEST_TIMEOUT_TICKS = 15;
    /**
     * How long an answer to the last request is still accepted, independent of the short key lock, so a high-latency
     * reply still opens the screen instead of being dropped silently.
     * 上一次请求的回复仍被接受的时长，与较短的按键锁无关，确保高延迟时的回复仍能打开界面而不是被静默丢弃。
     */
    public static final int ACCEPT_WINDOW_TICKS = 100;
    public static final int EXPIRY_MARGIN_TICKS = 20;

    private int requestTicks;
    private int acceptTicks;
    private int sessionTicks;
    private UUID sessionId;
    private Set<UUID> victims = Set.of();

    public boolean beginRequest() {
        if (requestTicks > 0 || sessionId != null) {
            return false;
        }
        requestTicks = REQUEST_TIMEOUT_TICKS;
        acceptTicks = ACCEPT_WINDOW_TICKS;
        return true;
    }

    public boolean accept(UUID sessionId, Collection<UUID> victims, int lifetimeTicks) {
        if (acceptTicks <= 0 || this.sessionId != null) {
            return false;
        }
        this.sessionId = Objects.requireNonNull(sessionId);
        this.victims = Set.copyOf(victims);
        this.sessionTicks = Math.max(1, lifetimeTicks - EXPIRY_MARGIN_TICKS);
        requestTicks = 0;
        acceptTicks = 0;
        return true;
    }

    public boolean isPending(UUID sessionId) {
        return this.sessionId != null && this.sessionId.equals(sessionId);
    }

    public int remainingTicks() {
        return sessionId == null ? 0 : sessionTicks;
    }

    public boolean consume(UUID sessionId, UUID victim) {
        if (!isPending(sessionId) || !victims.contains(victim)) {
            return false;
        }
        clear();
        return true;
    }

    /** Returns true exactly when a pending session just ran out. / 恰在待确认会话刚刚过期时返回 true。 */
    public boolean tick() {
        if (requestTicks > 0) {
            requestTicks--;
        }
        if (acceptTicks > 0) {
            acceptTicks--;
        }
        if (sessionId != null && --sessionTicks <= 0) {
            clear();
            return true;
        }
        return false;
    }

    public void clear() {
        requestTicks = 0;
        acceptTicks = 0;
        sessionTicks = 0;
        sessionId = null;
        victims = Set.of();
    }
}
