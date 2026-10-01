package dev.caecorthus.sparkwitch.client.potiongunner;

/**
 * One attack press = at most one fire request. The latch closes when a press is consumed (sent or swallowed) and
 * reopens only once the attack key is seen released, so a held key, the same press reaching several input hooks in
 * one tick, or vanilla's held-attack block breaking never fires twice.
 * 一次攻击按键最多产生一次发射请求。按键被消耗（发送或吞掉）时闭锁，只有观察到攻击键松开后才重新打开；因此按住不放、
 * 同一次按键在同一刻经过多个输入钩子，或原版按住攻击的挖掘逻辑，都不会重复发射。
 */
public final class PotionFireLatch {
    private boolean closed;

    /** True once per press: the caller may fire now; the latch is then closed. / 每次按键只返回一次 true：调用方可以发射，随后闭锁。 */
    public boolean tryConsume() {
        if (closed) {
            return false;
        }
        closed = true;
        return true;
    }

    /** Closes the latch without firing (a swallowed press). / 不发射而闭锁（被吞掉的按键）。 */
    public void close() {
        closed = true;
    }

    /** Called every client tick with the raw held state of the attack key. / 每个客户端刻以攻击键的原始按住状态调用。 */
    public void onTick(boolean attackHeld) {
        if (!attackHeld) {
            closed = false;
        }
    }

    public void reset() {
        closed = false;
    }

    public boolean isClosed() {
        return closed;
    }
}
