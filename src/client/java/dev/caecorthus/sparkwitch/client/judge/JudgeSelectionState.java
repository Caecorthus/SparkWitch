package dev.caecorthus.sparkwitch.client.judge;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** UI-only one-shot state; payment and nonce validation remain server-owned. / 仅界面单次状态，扣费及随机标识校验由服务端负责。 */
public final class JudgeSelectionState {
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private int requestTicks;
    private UUID sessionId;
    private Set<UUID> targets = Set.of();

    public boolean beginRequest() {
        if (requestTicks > 0 || sessionId != null) {
            return false;
        }
        requestTicks = REQUEST_TIMEOUT_TICKS;
        return true;
    }

    public boolean accept(UUID sessionId, List<UUID> targets) {
        if (requestTicks <= 0 || this.sessionId != null) {
            return false;
        }
        this.sessionId = Objects.requireNonNull(sessionId);
        this.targets = Set.copyOf(targets);
        requestTicks = 0;
        return true;
    }

    public boolean isPending(UUID sessionId) {
        return this.sessionId != null && this.sessionId.equals(sessionId);
    }

    public boolean consume(UUID sessionId, UUID targetId) {
        if (!isPending(sessionId) || !targets.contains(targetId)) {
            return false;
        }
        clear();
        return true;
    }

    public void tick() {
        if (requestTicks > 0) {
            requestTicks--;
        }
    }

    public void clear() {
        requestTicks = 0;
        sessionId = null;
        targets = Set.of();
    }
}
