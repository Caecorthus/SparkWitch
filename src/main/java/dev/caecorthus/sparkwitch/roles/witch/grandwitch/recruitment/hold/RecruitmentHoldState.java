package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold;

import net.minecraft.network.PacketByteBuf;

/**
 * Data-only hold counter (remaining ticks, never absolute server ticks) and the server-only anchor. Only the counter
 * is synced; nothing is ever saved.
 * 仅保存定身计时（剩余刻数，从不保存服务端绝对时刻）与仅服务端使用的锚点。只同步计时，从不存盘。
 */
public final class RecruitmentHoldState {
    private int remainingTicks;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;

    public int remainingTicks() {
        return remainingTicks;
    }

    public boolean isActive() {
        return remainingTicks > 0;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public float anchorYaw() {
        return anchorYaw;
    }

    public float anchorPitch() {
        return anchorPitch;
    }

    /**
     * Server: always re-anchors; the counter takes the max. Returns whether the counter changed (a sync point).
     * 服务端：总是重新取锚点；计时取最大值。返回计时是否改变（需同步）。
     */
    public boolean start(int ticks, double x, double y, double z, float yaw, float pitch) {
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchorYaw = yaw;
        anchorPitch = pitch;
        int next = Math.max(remainingTicks, ticks);
        if (next == remainingTicks) {
            return false;
        }
        remainingTicks = next;
        return true;
    }

    /**
     * Server: counts down; returns true exactly when the counter reaches zero, the only sync point after the start.
     * 服务端：倒计时；仅在计时归零时返回 true，这是开始之后唯一的同步点。
     */
    public boolean serverTick() {
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        return remainingTicks == 0;
    }

    /**
     * Client prediction stops at one tick: only the server's zero sync ends the hold.
     * 客户端预测停在 1 刻：只有服务端同步的零值才会结束定身。
     */
    public void clientTick() {
        if (remainingTicks > 1) {
            remainingTicks--;
        }
    }

    /** Returns whether anything was active (a sync point). / 返回此前是否处于定身（需同步）。 */
    public boolean clear() {
        if (remainingTicks == 0) {
            return false;
        }
        remainingTicks = 0;
        return true;
    }

    /** Whether a position left the anchor (see {@link RecruitmentHoldRules#drifted}). / 位置是否偏离锚点。 */
    public boolean drifted(double x, double y, double z) {
        return isActive() && RecruitmentHoldRules.drifted(x - anchorX, y - anchorY, z - anchorZ);
    }

    /** Packet layout: remaining ticks as one varint. / 数据包布局：剩余刻数，单个 varint。 */
    public void writeSync(PacketByteBuf buf) {
        buf.writeVarInt(remainingTicks);
    }

    public void readSync(PacketByteBuf buf) {
        remainingTicks = Math.max(0, buf.readVarInt());
    }
}
