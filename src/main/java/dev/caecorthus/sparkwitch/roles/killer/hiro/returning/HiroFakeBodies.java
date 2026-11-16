package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only registry of the fake corpses of 二阶堂希罗's fake-dead victims, keyed by the body entity's UUID (the owner
 * UUID belongs to a living player and would also match the later real body). Never synced, so no client can tell the
 * corpse is fake. Stable contract: every corpse consumer (Vulture, body bag, Kidnapper, Ragpicker, Charon ferry,
 * Prophet, Wraith conversion) asks {@link #isFake} and leaves the corpse alone, silently. Cleared at round boundaries.
 * 仅服务端的二阶堂希罗假死受害者假尸体登记表，以尸体实体 UUID 为键（尸体主人 UUID 属于仍存活的玩家，也会匹配之后的真尸体）。
 * 从不同步，客户端无法看出尸体是假的。稳定契约：所有尸体使用方（秃鹫、裹尸袋、绑匪、拾荒者、卡戎摆渡、先知、冤魂转化）都
 * 通过 {@link #isFake} 查询并静默地不碰这具尸体。在回合边界清空。
 */
public final class HiroFakeBodies {
    private static final Set<UUID> FAKES = ConcurrentHashMap.newKeySet();

    private HiroFakeBodies() {
    }

    static void mark(Entity body) {
        if (body != null) {
            FAKES.add(body.getUuid());
        }
    }

    static void forget(@Nullable UUID body) {
        if (body != null) {
            FAKES.remove(body);
        }
    }

    /**
     * True for a fake corpse of a fake-dead victim on the server; always false on the client and for null.
     * 服务端上假死受害者的假尸体返回 true；客户端与 null 一律返回 false。
     */
    public static boolean isFake(@Nullable Entity body) {
        return body != null && !body.getWorld().isClient() && FAKES.contains(body.getUuid());
    }

    /**
     * Drops fake corpses from a body lookup, silently (no message: a refusal must not name the corpse as fake).
     * Returns {@code found} itself when it holds none.
     * 从尸体查找结果中静默移除假尸体（不发任何提示：拒绝不能点明尸体是假的）。没有假尸体时原样返回 {@code found}。
     */
    public static <T extends Entity> List<T> withoutFakes(List<T> found) {
        if (found == null || found.isEmpty() || FAKES.isEmpty()) {
            return found;
        }
        List<T> kept = new ArrayList<>(found.size());
        for (T candidate : found) {
            if (!isFake(candidate)) {
                kept.add(candidate);
            }
        }
        return kept.size() == found.size() ? found : kept;
    }

    /** Round boundary or server stop. / 回合边界或服务器停止。 */
    static void clear() {
        FAKES.clear();
    }
}
