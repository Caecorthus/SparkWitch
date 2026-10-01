package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One-shot selection sessions opened by a mask use (60 s TTL). A session authorizes exactly one select
 * request and is cleared on any identity change, death, disconnect, or finalize. Server thread only.
 * 由面具使用开启的一次性选择会话（60 秒有效）。一个会话只授权一次选择请求，并在任何身份变化、死亡、
 * 断线或结算时清除。仅在服务端线程使用。
 */
public final class BlackRavenDisguiseSessions {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private BlackRavenDisguiseSessions() {
    }

    private record Session(int id, long openedAt) {
        boolean isLive(long now) {
            return now >= openedAt && now - openedAt < BlackRavenDisguiseRules.SESSION_TTL_TICKS;
        }
    }

    /** Opens (replacing any previous) session; returns its positive id. / 开启（替换旧的）会话并返回正数 id。 */
    public static int open(UUID owner, long now) {
        Session previous = SESSIONS.get(owner);
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        } while (previous != null && previous.id() == id);
        SESSIONS.put(owner, new Session(id, now));
        return id;
    }

    /** True once if the session matches and has not expired; always consumes it. / 会话匹配且未过期时返回 true；总会消费会话。 */
    public static boolean consume(UUID owner, int session, long now) {
        if (owner == null) {
            return false;
        }
        Session current = SESSIONS.remove(owner);
        return current != null && session > 0 && current.id() == session && current.isLive(now);
    }

    public static boolean isOpen(UUID owner, long now) {
        if (owner == null) {
            return false;
        }
        Session current = SESSIONS.get(owner);
        if (current == null) {
            return false;
        }
        if (!current.isLive(now)) {
            SESSIONS.remove(owner, current);
            return false;
        }
        return true;
    }

    public static void clear(UUID owner) {
        if (owner != null) {
            SESSIONS.remove(owner);
        }
    }

    public static void clearAll() {
        SESSIONS.clear();
    }
}
