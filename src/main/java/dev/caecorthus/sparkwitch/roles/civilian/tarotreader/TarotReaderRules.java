package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class TarotReaderRules {
    public static final int COLOR = 0xAEE1CF;
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    public static final int REGULAR_PRICE = 200;
    public static final int IDENTITY_PRICE = 50;
    public static final int SURVIVAL_PRICE = 50;

    private TarotReaderRules() {
    }

    public static boolean isTarotReader(@Nullable Role role) {
        return role == SparkWitchRoles.tarotReader();
    }

    /**
     * Static registry bucket of a role, for grouping the identity selector. It reads only the role's registered base
     * faction, never any player's live alignment, so it reveals nothing about the round. Null means no HUD bucket.
     * 职业的静态注册阵营桶，用于身份占卜界面分组。只读取职业注册的基础阵营，从不读取任何玩家的实时阵营，
     * 因此不会泄露本局信息。返回 null 表示不属于 HUD 的任一阵营桶。
     */
    public static @Nullable FactionBucket staticBucket(Role role) {
        return classifyFaction(SparkFactionApi.resolveBaseFaction(role), role.getFaction());
    }

    /**
     * Bucket for an SFA faction id, falling back to the role's Wathe faction only for ids outside the five buckets.
     * SFA appends native constants (WITCH, JIAHAO) to Wathe's {@code Faction} enum at runtime, so the fallback compares
     * the built-in constants by identity and never switches over the enum: any other constant reads as no bucket.
     * 按 SFA 阵营 ID 归桶；仅当 ID 不属于五个桶时才回退到职业的 Wathe 阵营。SFA 会在运行时向 Wathe 的
     * {@code Faction} 枚举追加原生常量（WITCH、JIAHAO），因此回退只按引用比较内置常量、从不对该枚举做 switch：
     * 其他常量一律视为不属于任何桶。
     */
    static @Nullable FactionBucket classifyFaction(
            @Nullable Identifier effectiveFaction,
            @Nullable Faction nativeFaction
    ) {
        if (effectiveFaction == null || FactionIds.NONE.equals(effectiveFaction)) {
            return null;
        }
        if (SparkWitchFactions.WITCH.equals(effectiveFaction)) {
            return FactionBucket.WITCH;
        }
        if (SparkWitchFactions.JIAHAO.equals(effectiveFaction)) {
            return FactionBucket.JIAHAO;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return FactionBucket.CIVILIAN;
        }
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return FactionBucket.KILLER;
        }
        if (FactionIds.NEUTRAL.equals(effectiveFaction)) {
            return FactionBucket.NEUTRAL;
        }
        if (nativeFaction == Faction.CIVILIAN) {
            return FactionBucket.CIVILIAN;
        }
        if (nativeFaction == Faction.KILLER) {
            return FactionBucket.KILLER;
        }
        if (nativeFaction == Faction.NEUTRAL) {
            return FactionBucket.NEUTRAL;
        }
        return null;
    }

    static boolean shouldCountActivePlayer(boolean assigned, boolean dead, boolean creative) {
        return assigned && !dead && !creative;
    }

    static boolean identityWasAssigned(boolean recordedInHistory, int currentAssignedCount) {
        return recordedInHistory || currentAssignedCount > 0;
    }

    static boolean isTargetAlive(boolean assigned, boolean dead) {
        return assigned && !dead;
    }

    /**
     * HUD and selector order. JIAHAO is Team Jiahao (Insider and Corrupt Cop, owner decision D5 2026-11-04).
     * HUD 与选择界面的顺序。JIAHAO 为嘉豪阵营（内应与黑警，所有者决定 D5，2026-11-04）。
     */
    public enum FactionBucket {
        CIVILIAN,
        KILLER,
        NEUTRAL,
        WITCH,
        JIAHAO
    }
}
