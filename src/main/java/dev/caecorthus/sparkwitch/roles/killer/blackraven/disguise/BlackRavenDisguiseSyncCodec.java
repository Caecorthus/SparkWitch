package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-only sync layout of {@code sparkwitch:black_raven_disguise}, in this fixed order:
 * bound, acting ("" = none), unlockRemaining, cooldownRemaining, poolCount (max 64) then per row
 * (id, flag byte, visited), ravenBalance. Never carries stashes or absolute server ticks.
 * sparkwitch:black_raven_disguise 仅拥有者的同步布局，顺序固定：bound、acting（"" 为无）、unlockRemaining、
 * cooldownRemaining、poolCount（最多 64）及每行 (id, flag 字节, visited)、ravenBalance。从不携带存档或服务端绝对 tick。
 */
public final class BlackRavenDisguiseSyncCodec {
    private BlackRavenDisguiseSyncCodec() {
    }

    /** One Tab B row as the owner's client sees it. / 拥有者客户端看到的一行 Tab B。 */
    public record PoolRow(Identifier id, BlackRavenDisguiseRules.PoolFlag flag, boolean visited) {
        public PoolRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(flag, "flag");
        }
    }

    /** Decoded owner view; remaining values are ticks at write time. / 解码后的拥有者视图；剩余值为写入时的 tick。 */
    public record View(
            boolean bound,
            @Nullable Identifier acting,
            int unlockRemaining,
            int cooldownRemaining,
            List<PoolRow> pool,
            int ravenBalance
    ) {
        public static final View EMPTY = new View(false, null, 0, 0, List.of(), 0);

        public View {
            pool = List.copyOf(pool);
        }

        public boolean disguised() {
            return acting != null;
        }
    }

    public static void write(PacketByteBuf buf, View view) {
        buf.writeBoolean(view.bound());
        String acting = view.acting() == null ? "" : view.acting().toString();
        buf.writeString(acting.length() > BlackRavenDisguiseRules.MAX_ROLE_ID_LENGTH ? "" : acting,
                BlackRavenDisguiseRules.MAX_ROLE_ID_LENGTH);
        buf.writeVarInt(Math.max(0, view.unlockRemaining()));
        buf.writeVarInt(Math.max(0, view.cooldownRemaining()));
        int count = Math.min(view.pool().size(), BlackRavenDisguiseRules.MAX_POOL_ENTRIES);
        buf.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            PoolRow row = view.pool().get(index);
            buf.writeIdentifier(row.id());
            buf.writeByte(row.flag().code());
            buf.writeBoolean(row.visited());
        }
        buf.writeVarInt(Math.max(0, view.ravenBalance()));
    }

    /** Rejects poolCount above 64; unknown flag codes decode as UNKNOWN. / 拒绝超过 64 的行数；未知标记解码为 UNKNOWN。 */
    public static View read(PacketByteBuf buf) {
        boolean bound = buf.readBoolean();
        String acting = buf.readString(BlackRavenDisguiseRules.MAX_ROLE_ID_LENGTH);
        int unlockRemaining = Math.max(0, buf.readVarInt());
        int cooldownRemaining = Math.max(0, buf.readVarInt());
        int count = buf.readVarInt();
        if (count < 0 || count > BlackRavenDisguiseRules.MAX_POOL_ENTRIES) {
            throw new IllegalArgumentException("Black Raven disguise pool too large: " + count);
        }
        List<PoolRow> pool = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Identifier id = buf.readIdentifier();
            BlackRavenDisguiseRules.PoolFlag flag = BlackRavenDisguiseRules.PoolFlag.fromCode(buf.readByte());
            pool.add(new PoolRow(id, flag, buf.readBoolean()));
        }
        int ravenBalance = Math.max(0, buf.readVarInt());
        return new View(bound, acting.isEmpty() ? null : Identifier.tryParse(acting), unlockRemaining,
                cooldownRemaining, pool, ravenBalance);
    }
}
