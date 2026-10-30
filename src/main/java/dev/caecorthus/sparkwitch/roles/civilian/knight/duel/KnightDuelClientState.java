package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.net.KnightDuelStateS2CPayload;

import java.util.UUID;

/**
 * Client mirror of the duel state from the dedicated packet, with a wall-clock expiry. Used by the client-side
 * collision/door checks so pass-through lasts the whole duel even if component sync is missed.
 * 客户端独立的决斗状态（带真实时间过期）。客户端碰撞/穿门检查使用它，确保穿透持续整场决斗。
 */
public final class KnightDuelClientState {
    private static boolean active;
    private static UUID knight;
    private static UUID target;
    private static long expiresAtMillis;

    private KnightDuelClientState() {
    }

    public static void apply(KnightDuelStateS2CPayload payload) {
        active = payload.active();
        knight = payload.knight();
        target = payload.target();
        expiresAtMillis = active ? System.currentTimeMillis() + (payload.remainingTicks() + 20L) * 50L : 0L;
    }

    public static void clear() {
        active = false;
        knight = null;
        target = null;
        expiresAtMillis = 0L;
    }

    /** Re-asserts the state from the synced component every client tick while a duel is active. / 决斗期间每客户端 tick 用组件状态续期。 */
    public static void forceFrom(KnightDuelWorldComponent duel) {
        if (duel.active() && duel.knight() != null && duel.target() != null) {
            active = true;
            knight = duel.knight();
            target = duel.target();
            expiresAtMillis = System.currentTimeMillis() + 120_000L;
        }
    }

    public static boolean isDuelist(UUID uuid) {
        return active && uuid != null && System.currentTimeMillis() < expiresAtMillis
                && (uuid.equals(knight) || uuid.equals(target));
    }

    public static UUID knight() {
        return active ? knight : null;
    }

    public static UUID target() {
        return active ? target : null;
    }

    public static boolean isKnight(UUID uuid) {
        return active && uuid != null && uuid.equals(knight);
    }
}
