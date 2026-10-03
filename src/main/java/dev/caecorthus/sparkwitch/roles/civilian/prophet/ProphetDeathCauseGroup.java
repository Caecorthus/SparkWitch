package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.util.Locale;
import java.util.Optional;

/**
 * Placeholder for the S1-owned Prophecy cause groups (declaration order is the UI order); S1 adds {@code classify}.
 * S1 负责的预言死因分组占位（声明顺序即 UI 顺序）；{@code classify} 由 S1 补充。
 */
public enum ProphetDeathCauseGroup {
    BLADE,
    GUNSHOT,
    THROWN,
    BLUNT,
    EXPLOSION,
    POISON,
    ACCIDENT,
    MENTAL,
    SUPERNATURAL,
    PENALTY,
    OTHER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.sparkwitch.prophecy.cause." + id();
    }

    public static Optional<ProphetDeathCauseGroup> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ProphetDeathCauseGroup group : values()) {
            if (group.id().equals(id)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }
}
