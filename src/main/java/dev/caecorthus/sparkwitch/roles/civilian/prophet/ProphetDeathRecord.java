package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One server-only death fact; {@code responsible == null} means no killer. Names are captured at death time.
 * 一条仅服务端的死亡事实；{@code responsible == null} 表示无人行凶。名字在死亡时刻抓取。
 */
public record ProphetDeathRecord(
        UUID victim,
        String victimName,
        Identifier deathReason,
        ProphetDeathCauseGroup group,
        @Nullable UUID responsible,
        @Nullable String responsibleName
) {
}
