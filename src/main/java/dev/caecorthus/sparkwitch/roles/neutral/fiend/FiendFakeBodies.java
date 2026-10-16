package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only registry of feign-death fake corpses, keyed by the body entity's UUID (the owner UUID belongs to a living
 * Fiend and would also match a later real body). Never synced, so no client can tell the corpse is fake. Body readers
 * that must not treat it as a death (Prophet, Kidnapper, Wraith conversion) ask {@link #isFake}; the Vulture may eat it,
 * which {@link FiendFeignService#onBodyDevoured} turns into the Fiend's real death. Cleared at round boundaries.
 * 仅服务端的假死假尸体登记表，以尸体实体 UUID 为键（尸体主人 UUID 属于仍存活的魔人，也会匹配之后的真尸体）。从不同步，
 * 客户端无法看出尸体是假的。不得把它当作死亡的尸体读取方（先知、绑匪、冤魂转化）通过 {@link #isFake} 查询；秃鹫可以吃掉它，
 * {@link FiendFeignService#onBodyDevoured} 会把这次进食变成魔人的真正死亡。在回合边界清空。
 */
public final class FiendFakeBodies {
    private static final Set<UUID> FAKES = ConcurrentHashMap.newKeySet();

    private FiendFakeBodies() {
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
     * True for a feign-death fake corpse on the server; always false on the client and for null.
     * 服务端上的假死假尸体返回 true；客户端与 null 一律返回 false。
     */
    public static boolean isFake(@Nullable Entity body) {
        return body != null && !body.getWorld().isClient() && FAKES.contains(body.getUuid());
    }

    /** Round boundary or server stop. / 回合边界或服务器停止。 */
    static void clear() {
        FAKES.clear();
    }
}
