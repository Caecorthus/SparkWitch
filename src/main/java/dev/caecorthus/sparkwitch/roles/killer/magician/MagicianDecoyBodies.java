package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only registry of decoy bodies: the fake corpses a forced puppet end leaves (owner decision 2026-10-07 D2).
 * Keyed by the body entity's UUID, which survives chunk unload/reload; cleared at round end. Never synced, so a client
 * cannot tell a decoy from a real body. Roles that must ignore decoys (Prophet, Vulture, Kidnapper) ask
 * {@link #isDecoy}; a decoy's cover role is stamped separately on the body itself.
 * 仅服务端的诱饵尸体登记表：皮套被强制结束时留下的假尸体（所有者 2026-10-07 决定 D2）。以尸体实体 UUID 为键
 * （区块卸载/重载后不变），回合结束时清空。从不同步，客户端无法分辨诱饵与真尸体。必须忽略诱饵的职业（先知、秃鹫、绑匪）
 * 通过 {@link #isDecoy} 查询；诱饵的掩护身份另外写在尸体自身上。
 */
public final class MagicianDecoyBodies {
    private static final Set<UUID> DECOYS = ConcurrentHashMap.newKeySet();

    private MagicianDecoyBodies() {
    }

    static void mark(Entity body) {
        if (body != null) {
            DECOYS.add(body.getUuid());
        }
    }

    /**
     * True for a decoy body on the server; always false on the client and for null.
     * 服务端上的诱饵尸体返回 true；客户端与 null 一律返回 false。
     */
    public static boolean isDecoy(@Nullable Entity body) {
        return body != null && !body.getWorld().isClient() && DECOYS.contains(body.getUuid());
    }

    /** Round end. / 回合结束。 */
    public static void clear() {
        DECOYS.clear();
    }
}
