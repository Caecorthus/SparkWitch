package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;

/** Server deadline and a separately latched client capability. / 服务端截止刻与独立锁存的客户端能力标记。 */
final class FisherSpiritWindow {
    private boolean active;
    private int remainingTicks;
    private long deadline;

    void start(long now) {
        active = true;
        remainingTicks = FisherRules.GLIMMER_WINDOW_TICKS;
        deadline = now + FisherRules.GLIMMER_WINDOW_TICKS;
    }

    boolean isActive() {
        return active;
    }

    int remainingTicks() {
        return remainingTicks;
    }

    boolean tickServer(long now) {
        remainingTicks = active ? Math.clamp(deadline - now, 0, FisherRules.GLIMMER_WINDOW_TICKS) : 0;
        return active && remainingTicks == 0;
    }

    void tickClient() {
        remainingTicks = Math.max(0, remainingTicks - 1);
    }

    void clear() {
        active = false;
        remainingTicks = 0;
        deadline = 0;
    }

    /**
     * One VarInt: 0 = inactive, 1 = active observer, owner = remaining + 1. No match id or position is sent.
     * 单个 VarInt：0 = 未激活，1 = 观察者看到的激活态，拥有者 = 剩余刻 + 1。不发送对局 id 或位置。
     */
    int encode(boolean owner) {
        return !active ? 0 : owner ? remainingTicks + 1 : 1;
    }

    void applySync(int value) {
        active = value > 0;
        remainingTicks = Math.clamp((long) value - 1, 0, FisherRules.GLIMMER_WINDOW_TICKS);
    }
}
