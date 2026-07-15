package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Saint tuning and compatibility predicates.
 * 圣徒的纯数值与兼容判断集中在这里，运行时接线由外层模块负责。
 */
public final class SaintRules {
    public static final int COLOR = 0xEEBC78;
    public static final int HELLFIRE_INITIAL_COOLDOWN_TICKS = 1200;
    public static final int HELLFIRE_ACTIVE_TICKS = 300;
    public static final int HELLFIRE_POST_COOLDOWN_TICKS = 1200;
    public static final int NORMAL_KARMA = 100;
    public static final int BOMBER_KARMA = 400;

    public static final Identifier SAINT_ROLE_ID = Identifier.of("sparkwitch", "saint");
    private static final Identifier GRAND_WITCH_ROLE_ID = Identifier.of("sparkwitch", "grand_witch");
    // These ID-only seams keep Saint rules independent from NoellesRoles implementation classes.
    // 这些仅依赖 ID 的接缝让圣徒规则不依赖 NoellesRoles 的实现类。
    public static final Identifier BOMBER_ROLE_ID = Identifier.of("noellesroles", "bomber");
    public static final Identifier TIMED_BOMB_ITEM_ID = Identifier.of("noellesroles", "timed_bomb");
    public static final Identifier POISON_NEEDLE_ITEM_ID = Identifier.of("noellesroles", "poison_needle");
    public static final Identifier VOODOO_DEATH_REASON_ID = Identifier.of("noellesroles", "voodoo");

    private SaintRules() {
    }

    public static boolean isSaint(@Nullable Role role) {
        return hasRoleId(role, SAINT_ROLE_ID);
    }

    public static boolean isBomber(@Nullable Role role) {
        return hasRoleId(role, BOMBER_ROLE_ID);
    }

    static boolean isKarmaImmune(@Nullable Role role) {
        return hasRoleId(role, GRAND_WITCH_ROLE_ID);
    }

    public static int karmaFor(@Nullable Role role) {
        return isBomber(role) ? BOMBER_KARMA : NORMAL_KARMA;
    }

    public static boolean isKarmaRecordTrigger(@Nullable Identifier itemId, @Nullable String action) {
        return POISON_NEEDLE_ITEM_ID.equals(itemId)
                || TIMED_BOMB_ITEM_ID.equals(itemId) && "transfer".equals(action);
    }

    public static int mergeCooldownTicks(int currentTicks, int requestedTicks) {
        return Math.max(currentTicks, requestedTicks);
    }

    public static boolean isBlockedDeathReason(@Nullable Identifier deathReason) {
        return VOODOO_DEATH_REASON_ID.equals(deathReason);
    }

    public static boolean blocksKill(
            boolean saintVictim,
            @Nullable Identifier effectiveKillerFaction,
            @Nullable Identifier deathReason
    ) {
        return saintVictim && (isBlockedDeathReason(deathReason)
                || FactionIds.CIVILIAN.equals(effectiveKillerFaction));
    }

    private static boolean hasRoleId(@Nullable Role role, Identifier id) {
        return role != null && id.equals(role.identifier());
    }
}
