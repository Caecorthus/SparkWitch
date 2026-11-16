package dev.caecorthus.sparkwitch.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure witch-faction lineup for SparkFactionAPI's round-start third-faction draw (owner, 2026-11-16). SparkFactionAPI
 * owns the 50/50 pick, the player minimum and the member cap ({@code floor(players / dividend)}); this decides only
 * which seats fill the remaining slots: one Grand Witch, then Bewitched on every slot left. Bewitched are dealt only
 * in a round that has a Grand Witch (D1), so a disabled Grand Witch with none forced makes the faction undrawable.
 * Replaces the old 18+ / {@code (players - 18) / 6} quota.
 * SparkFactionAPI 开局“第三阵营”抽取中魔女阵营的纯阵容规则（所有者 2026-11-16）。SparkFactionAPI 负责 50/50 抽取、最低人数
 * 与成员上限（{@code floor(人数 / 除数)}）；这里只决定剩余名额如何填充：一名大魔女，其余名额全部为魔化使。魔化使只在有
 * 大魔女的对局发放（D1），因此大魔女被禁用且没有强制指定时，本阵营不可抽取。取代旧的 18 人以上 / {@code (人数 - 18) / 6}
 * 名额。
 */
public final class WitchThirdFactionLineupRules {
    private WitchThirdFactionLineupRules() {
    }

    /**
     * Seats to deal, in deal order. Forced members are already counted in {@code remainingSlots}; a forced Grand Witch
     * is kept and only Bewitched are added.
     * 按发放顺序返回要发放的席位。强制指定的成员已计入 {@code remainingSlots}；强制指定的大魔女原样保留，只追加魔化使。
     */
    public static List<Seat> seats(
            boolean grandWitchPresent,
            boolean grandWitchEnabled,
            boolean bewitchedEnabled,
            int remainingSlots
    ) {
        if (remainingSlots <= 0) {
            return List.of();
        }
        List<Seat> seats = new ArrayList<>(remainingSlots);
        if (!grandWitchPresent) {
            if (!grandWitchEnabled) {
                return List.of();
            }
            seats.add(Seat.GRAND_WITCH);
        }
        if (bewitchedEnabled) {
            while (seats.size() < remainingSlots) {
                seats.add(Seat.BEWITCHED);
            }
        }
        return List.copyOf(seats);
    }

    public enum Seat {
        GRAND_WITCH,
        BEWITCHED
    }
}
