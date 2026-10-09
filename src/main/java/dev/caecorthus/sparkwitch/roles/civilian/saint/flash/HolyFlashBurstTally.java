package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pure count of one burst's flashed players for the {@code sparkwitch:holy_flash_burst} record: everyone flashed, and
 * everyone flashed except the thrower (matched by the owner UUID, so it holds while the thrower is offline).
 * 一次爆开所致盲玩家的纯计数，供 {@code sparkwitch:holy_flash_burst} 记录使用：所有被致盲者，以及除投掷者以外的被致盲者
 * （按所有者 UUID 匹配，投掷者离线时同样成立）。
 */
final class HolyFlashBurstTally {
    private final @Nullable UUID thrower;
    private int affected;
    private int affectedOthers;

    HolyFlashBurstTally(@Nullable UUID thrower) {
        this.thrower = thrower;
    }

    void flashed(UUID player) {
        affected++;
        if (!player.equals(thrower)) {
            affectedOthers++;
        }
    }

    int affected() {
        return affected;
    }

    int affectedOthers() {
        return affectedOthers;
    }
}
