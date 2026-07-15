package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;

/**
 * Owns the Prophet's private Death Omen window and insertion-ordered corpse history.
 * 持有先知私有的死亡预兆窗口，以及按发现顺序保存的尸体记录。
 */
public final class ProphetPlayerState {
    private int remainingTicks;
    private final Set<UUID> bodyUuids = new LinkedHashSet<>();

    public enum TickOutcome {
        NONE,
        SYNC,
        FINISHED
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public boolean isActive() {
        return remainingTicks > 0;
    }

    public boolean containsBody(UUID bodyUuid) {
        return bodyUuid != null && bodyUuids.contains(bodyUuid);
    }

    public void begin(int durationTicks) {
        remainingTicks = Math.max(0, durationTicks);
        bodyUuids.clear();
    }

    public boolean recordBody(UUID bodyUuid) {
        return isActive() && bodyUuid != null && bodyUuids.add(bodyUuid);
    }

    /**
     * Separates normal completion from periodic syncs so the component alone starts shared cooldowns.
     * 区分正常结束与周期同步，让组件独自负责启动共享冷却。
     */
    public TickOutcome tick() {
        if (remainingTicks <= 0) {
            return TickOutcome.NONE;
        }
        remainingTicks--;
        if (remainingTicks == 0) {
            bodyUuids.clear();
            return TickOutcome.FINISHED;
        }
        return remainingTicks % 20 == 0 ? TickOutcome.SYNC : TickOutcome.NONE;
    }

    public boolean isEmpty() {
        return remainingTicks == 0 && bodyUuids.isEmpty();
    }

    public boolean cancel() {
        if (isEmpty()) {
            return false;
        }
        clear();
        return true;
    }

    public void clear() {
        remainingTicks = 0;
        bodyUuids.clear();
    }

    /**
     * Keeps the established Death Omen NBT keys stable behind the role-owned state boundary.
     * 在角色自有状态边界内保持既有死亡预兆 NBT 键不变。
     */
    public void writeNbt(NbtCompound tag) {
        if (!isActive()) {
            return;
        }
        tag.putInt("DeathOmenTicks", remainingTicks);
        NbtList bodies = new NbtList();
        bodyUuids.stream().map(UUID::toString).map(NbtString::of).forEach(bodies::add);
        tag.put("DeathOmenBodyUuids", bodies);
    }

    public void readNbt(NbtCompound tag) {
        remainingTicks = tag.contains("DeathOmenTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("DeathOmenTicks"))
                : 0;
        bodyUuids.clear();
        if (!isActive()) {
            return;
        }
        NbtList bodies = tag.getList("DeathOmenBodyUuids", NbtElement.STRING_TYPE);
        for (int index = 0; index < bodies.size(); index++) {
            try {
                bodyUuids.add(UUID.fromString(bodies.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved UUIDs so stale data cannot expose an invalid body.
                // 忽略损坏的存档 UUID，避免旧数据暴露无效尸体。
            }
        }
    }

    /**
     * Writes one owner-only packet segment: ticks, count, then insertion-ordered UUIDs.
     * 写入一个仅所有者可见的包区段：tick、数量、再按记录顺序写 UUID。
     */
    public void writeSync(RegistryByteBuf buf, boolean ownerVisible) {
        boolean visible = ownerVisible && isActive();
        buf.writeVarInt(visible ? remainingTicks : 0);
        buf.writeVarInt(visible ? bodyUuids.size() : 0);
        if (visible) {
            bodyUuids.forEach(buf::writeUuid);
        }
    }

    public void readSync(RegistryByteBuf buf) {
        remainingTicks = Math.max(0, buf.readVarInt());
        bodyUuids.clear();
        int bodyCount = Math.max(0, buf.readVarInt());
        for (int index = 0; index < bodyCount; index++) {
            UUID bodyUuid = buf.readUuid();
            if (isActive()) {
                bodyUuids.add(bodyUuid);
            }
        }
    }
}
