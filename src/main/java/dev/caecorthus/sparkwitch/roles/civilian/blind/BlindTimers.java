package dev.caecorthus.sparkwitch.roles.civilian.blind;

import net.minecraft.network.PacketByteBuf;

/**
 * Data-only cane and Attune windows as absolute world ticks. "Ready" ticks include any active window, because the
 * cooldown starts when the window ends (C2). The wire form is remaining ticks relative to the sender's world time, in
 * the fixed order cane cooldown, cane active, Attune cooldown, Attune active; the receiver rebuilds absolute ticks from
 * its own world time, so a client simply counts down between syncs and never learns a server tick.
 * 仅数据：盲杖与凝神窗口，以绝对世界刻保存。“就绪”刻包含持续窗口，因为冷却在窗口结束后才开始（C2）。
 * 线上格式为相对发送方世界时间的剩余刻数，固定顺序为盲杖冷却、盲杖持续、凝神冷却、凝神持续；接收方按自己的世界时间
 * 还原绝对刻，因此客户端在两次同步之间自然倒计时，永远不会得知服务端刻。
 */
public final class BlindTimers {
    private long caneReadyTick;
    private long caneActiveUntilTick;
    private long attuneReadyTick;
    private long attuneActiveUntilTick;

    public long caneReadyTick() {
        return caneReadyTick;
    }

    public long caneActiveUntilTick() {
        return caneActiveUntilTick;
    }

    public long attuneReadyTick() {
        return attuneReadyTick;
    }

    public long attuneActiveUntilTick() {
        return attuneActiveUntilTick;
    }

    public boolean isCaneActive(long now) {
        return now < caneActiveUntilTick;
    }

    public boolean isAttuneActive(long now) {
        return now < attuneActiveUntilTick;
    }

    public boolean caneReady(long now) {
        return now >= caneReadyTick;
    }

    public boolean attuneReady(long now) {
        return now >= attuneReadyTick;
    }

    /** Ticks until the cane may be used again (includes its active window). / 距盲杖可再次使用的刻数（含持续窗口）。 */
    public int caneCooldownRemaining(long now) {
        return remaining(caneReadyTick, now);
    }

    public int caneActiveRemaining(long now) {
        return remaining(caneActiveUntilTick, now);
    }

    /** Ticks until Attune may be used again (includes its active window). / 距凝神可再次使用的刻数（含持续窗口）。 */
    public int attuneCooldownRemaining(long now) {
        return remaining(attuneReadyTick, now);
    }

    public int attuneActiveRemaining(long now) {
        return remaining(attuneActiveUntilTick, now);
    }

    /** Returns whether anything changed. / 返回是否有变化。 */
    public boolean setCane(long activeUntilTick, long readyTick) {
        long active = Math.max(0L, activeUntilTick);
        long ready = Math.max(0L, readyTick);
        if (active == caneActiveUntilTick && ready == caneReadyTick) {
            return false;
        }
        caneActiveUntilTick = active;
        caneReadyTick = ready;
        return true;
    }

    /** Returns whether anything changed. / 返回是否有变化。 */
    public boolean setAttune(long activeUntilTick, long readyTick) {
        long active = Math.max(0L, activeUntilTick);
        long ready = Math.max(0L, readyTick);
        if (active == attuneActiveUntilTick && ready == attuneReadyTick) {
            return false;
        }
        attuneActiveUntilTick = active;
        attuneReadyTick = ready;
        return true;
    }

    public boolean isClear() {
        return caneReadyTick == 0L && caneActiveUntilTick == 0L && attuneReadyTick == 0L && attuneActiveUntilTick == 0L;
    }

    /** Returns whether anything changed. / 返回是否有变化。 */
    public boolean clear() {
        if (isClear()) {
            return false;
        }
        caneReadyTick = 0L;
        caneActiveUntilTick = 0L;
        attuneReadyTick = 0L;
        attuneActiveUntilTick = 0L;
        return true;
    }

    /** Wire order: cane cooldown, cane active, Attune cooldown, Attune active (VarInts). / 线上顺序（VarInt）。 */
    public void writeSync(PacketByteBuf buf, long now) {
        buf.writeVarInt(caneCooldownRemaining(now));
        buf.writeVarInt(caneActiveRemaining(now));
        buf.writeVarInt(attuneCooldownRemaining(now));
        buf.writeVarInt(attuneActiveRemaining(now));
    }

    /** Rebuilds absolute ticks from the receiver's own clock; zero stays "never set". / 按接收方时钟还原；零仍表示未设置。 */
    public void readSync(PacketByteBuf buf, long now) {
        caneReadyTick = absolute(buf.readVarInt(), now);
        caneActiveUntilTick = absolute(buf.readVarInt(), now);
        attuneReadyTick = absolute(buf.readVarInt(), now);
        attuneActiveUntilTick = absolute(buf.readVarInt(), now);
    }

    private static int remaining(long until, long now) {
        long left = until - now;
        if (left <= 0L) {
            return 0;
        }
        return left >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) left;
    }

    private static long absolute(int remaining, long now) {
        return remaining <= 0 ? 0L : now + remaining;
    }
}
