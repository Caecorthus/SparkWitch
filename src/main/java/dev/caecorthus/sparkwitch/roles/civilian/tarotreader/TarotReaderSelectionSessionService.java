package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns server-only, one-shot Tarot Reader selection authorization.
 * 管理仅存在于服务端、只能提交一次的塔罗牌师占卜选择授权。
 */
public final class TarotReaderSelectionSessionService {
    private static final Map<UUID, Session> PENDING = new HashMap<>();

    private TarotReaderSelectionSessionService() {
    }

    public static synchronized void open(UUID playerUuid, int mode) {
        PENDING.put(playerUuid, new Session(mode));
    }

    public static synchronized Optional<Session> consume(UUID playerUuid) {
        Session session = PENDING.remove(playerUuid);
        return Optional.ofNullable(session);
    }

    public static synchronized void clear(UUID playerUuid) {
        PENDING.remove(playerUuid);
    }

    public static synchronized void clearAll() {
        PENDING.clear();
    }

    public record Session(int mode) {
    }
}
