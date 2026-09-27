package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;

/**
 * Data-only owner-private disrupt and stun counters (remaining ticks, never absolute server ticks).
 * 仅保存拥有者私有的干扰与眩晕计时（剩余刻数，从不保存服务端绝对时刻）。
 */
public final class ControlExpertStatusState {
    /** Only the disrupt counter is ever persisted. / 只有干扰计时会被持久化。 */
    public static final String DISRUPT_NBT_KEY = "DisruptTicks";
    private static final int SYNC_INTERVAL_TICKS = 20;

    private int disruptTicks;
    private int stunTicks;

    public int disruptTicks() {
        return disruptTicks;
    }

    public int stunTicks() {
        return stunTicks;
    }

    public boolean isDisrupted() {
        return disruptTicks > 0;
    }

    public boolean isStunned() {
        return stunTicks > 0;
    }

    public boolean blocksInstinct() {
        return isDisrupted() || isStunned();
    }

    public boolean isActive() {
        return blocksInstinct();
    }

    /** Max semantics; returns whether the counter changed. / 取最大值；返回计时是否改变。 */
    public boolean disrupt(int ticks) {
        int next = Math.max(disruptTicks, ticks);
        if (next == disruptTicks) {
            return false;
        }
        disruptTicks = next;
        return true;
    }

    /** Max semantics and no immunity window; returns whether the counter changed. / 取最大值且无免疫窗口；返回计时是否改变。 */
    public boolean stun(int ticks) {
        int next = Math.max(stunTicks, ticks);
        if (next == stunTicks) {
            return false;
        }
        stunTicks = next;
        return true;
    }

    /** Decrements both counters; returns true when the owner should be synced (whole second or zero).
     * 同时递减两个计时；到达整秒或归零时返回 true 表示需要同步给拥有者。 */
    public boolean serverTick() {
        boolean sync = false;
        if (disruptTicks > 0) {
            disruptTicks--;
            sync = disruptTicks % SYNC_INTERVAL_TICKS == 0;
        }
        if (stunTicks > 0) {
            stunTicks--;
            sync |= stunTicks % SYNC_INTERVAL_TICKS == 0;
        }
        return sync;
    }

    /**
     * Client prediction stops at one tick: only the server's zero sync ends a lock.
     * 客户端预测停在 1 刻：只有服务端同步的零值才会结束锁定。
     */
    public void clientTick() {
        if (disruptTicks > 1) {
            disruptTicks--;
        }
        if (stunTicks > 1) {
            stunTicks--;
        }
    }

    public boolean clear() {
        if (disruptTicks == 0 && stunTicks == 0) {
            return false;
        }
        disruptTicks = 0;
        stunTicks = 0;
        return true;
    }

    public void restore(int disruptTicks, int stunTicks) {
        this.disruptTicks = Math.max(0, disruptTicks);
        this.stunTicks = Math.max(0, stunTicks);
    }

    /** Packet layout: disrupt varint, then stun varint. / 数据包布局：先干扰 varint，再眩晕 varint。 */
    public void writeSync(PacketByteBuf buf) {
        buf.writeVarInt(disruptTicks);
        buf.writeVarInt(stunTicks);
    }

    public void readSync(PacketByteBuf buf) {
        restore(buf.readVarInt(), buf.readVarInt());
    }

    /**
     * A stun is never saved or loaded, so a relog or restart can never strand a player in the input lock.
     * 眩晕从不保存或读取，因此重新登录或重启永远不会让玩家卡在输入锁定中。
     */
    public void writeNbt(NbtCompound tag) {
        if (disruptTicks > 0) {
            tag.putInt(DISRUPT_NBT_KEY, disruptTicks);
        }
    }

    public void readNbt(NbtCompound tag) {
        restore(tag.contains(DISRUPT_NBT_KEY, NbtElement.NUMBER_TYPE) ? tag.getInt(DISRUPT_NBT_KEY) : 0, 0);
    }
}
