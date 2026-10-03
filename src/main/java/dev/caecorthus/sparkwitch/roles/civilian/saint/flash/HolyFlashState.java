package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import net.minecraft.network.PacketByteBuf;

/**
 * Data-only owner-private flash state: total and remaining ticks plus the burst position and whether the player
 * faced it. Never persisted, so a relog or restart can never strand a player behind a black screen.
 * 仅保存拥有者私有的闪光状态：总刻数与剩余刻数，以及爆点位置和玩家是否正对爆点。
 * 从不持久化，因此重新登录或重启永远不会让玩家卡在黑屏里。
 */
public final class HolyFlashState {
    private static final int SYNC_INTERVAL_TICKS = 20;

    private int totalTicks;
    private int remainingTicks;
    private double burstX;
    private double burstY;
    private double burstZ;
    private boolean faced;

    public int totalTicks() {
        return totalTicks;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public boolean isActive() {
        return remainingTicks > 0 && totalTicks > 0;
    }

    public double burstX() {
        return burstX;
    }

    public double burstY() {
        return burstY;
    }

    public double burstZ() {
        return burstZ;
    }

    public boolean faced() {
        return faced;
    }

    /** Elapsed ticks since the current flash started. / 当前闪光开始以来经过的刻数。 */
    public int elapsedTicks() {
        return isActive() ? totalTicks - remainingTicks : 0;
    }

    /**
     * Max semantics: a new flash replaces the current one only when it lasts longer than what remains, and then
     * restarts the timeline. Returns whether the state changed.
     * 取最大值：新闪光只有比剩余时间更长时才替换当前闪光，并重新开始时间线。返回状态是否改变。
     */
    public boolean flash(int ticks, double x, double y, double z, boolean faced) {
        if (ticks <= 0 || ticks <= remainingTicks) {
            return false;
        }
        this.totalTicks = ticks;
        this.remainingTicks = ticks;
        this.burstX = x;
        this.burstY = y;
        this.burstZ = z;
        this.faced = faced;
        return true;
    }

    /** Returns true when the owner should be synced (whole second or zero). / 到达整秒或归零时返回 true 表示需要同步。 */
    public boolean serverTick() {
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        if (remainingTicks == 0) {
            totalTicks = 0;
            return true;
        }
        return remainingTicks % SYNC_INTERVAL_TICKS == 0;
    }

    /** Client prediction stops at one tick; only the server's zero sync ends a flash. / 客户端预测停在 1 刻，只有服务端同步的零值结束闪光。 */
    public void clientTick() {
        if (remainingTicks > 1) {
            remainingTicks--;
        }
    }

    public boolean clear() {
        if (remainingTicks == 0 && totalTicks == 0) {
            return false;
        }
        remainingTicks = 0;
        totalTicks = 0;
        return true;
    }

    /** Packet layout: total varint, remaining varint, burst xyz doubles, faced boolean. / 数据包布局：总刻数、剩余刻数、爆点坐标、是否正对。 */
    public void writeSync(PacketByteBuf buf) {
        buf.writeVarInt(totalTicks);
        buf.writeVarInt(remainingTicks);
        buf.writeDouble(burstX);
        buf.writeDouble(burstY);
        buf.writeDouble(burstZ);
        buf.writeBoolean(faced);
    }

    public void readSync(PacketByteBuf buf) {
        int total = buf.readVarInt();
        int remaining = buf.readVarInt();
        burstX = buf.readDouble();
        burstY = buf.readDouble();
        burstZ = buf.readDouble();
        faced = buf.readBoolean();
        totalTicks = Math.max(0, total);
        remainingTicks = Math.max(0, Math.min(remaining, totalTicks));
    }
}
