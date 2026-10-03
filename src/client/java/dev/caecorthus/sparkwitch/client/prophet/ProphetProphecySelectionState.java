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
    public static final int REQUEST_TIMEOUT_TICKS = 100;
    public static final int EXPIRY_MARGIN_TICKS = 20;

    private int requestTicks;
    private int sessionTicks;
    private UUID sessionId;
    private Set<UUID> victims = Set.of();

    public boolean beginRequest() {
        if (requestTicks > 0 || sessionId != null) {
            return false;
        }
        requestTicks = REQUEST_TIMEOUT_TICKS;
        return true;
    }

    public boolean accept(UUID sessionId, Collection<UUID> victims, int lifetimeTicks) {
        if (requestTicks <= 0 || this.sessionId != null) {
            return false;
        }
        this.sessionId = Objects.requireNonNull(sessionId);
        this.victims = Set.copyOf(victims);
        this.sessionTicks = Math.max(1, lifetimeTicks - EXPIRY_MARGIN_TICKS);
        requestTicks = 0;
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
        if (sessionId != null && --sessionTicks <= 0) {
            clear();
            return true;
        }
        return false;
    }

    public void clear() {
        requestTicks = 0;
        sessionTicks = 0;
        sessionId = null;
        victims = Set.of();
    }
}
