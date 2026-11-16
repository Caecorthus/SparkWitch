package dev.caecorthus.sparkwitch.roles.neutral.insider;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Team Jiahao lineup for SparkFactionAPI's round-start third-faction draw (owner, 2026-11-16): one NoellesRoles
 * Corrupt Cop, plus one Insider when a second slot is free. Team Jiahao never has more than {@link #MAX_MEMBERS}
 * members from the draw: forced members count toward it and are always kept. The old "only with 4+ killers" pairing
 * rule is gone, and the Corrupt Cop is no longer drawn by Wathe's neutral pass. Without a Corrupt Cop (NoellesRoles
 * absent or the role disabled) and none forced, the faction is not drawable.
 * SparkFactionAPI 开局“第三阵营”抽取中嘉豪阵营的纯阵容规则（所有者 2026-11-16）：一名 NoellesRoles 黑警，有第二个空位时
 * 再加一名内应。经由抽取的嘉豪阵营成员从不超过 {@link #MAX_MEMBERS} 名：强制指定的成员计入其中且始终保留。旧的“至少 4 名
 * 杀手才配对”规则已取消，黑警也不再由 Wathe 中立分配抽取。没有可用黑警（未安装 NoellesRoles 或该职业被禁用）且没有强制
 * 指定时，本阵营不可抽取。
 */
public final class JiahaoThirdFactionLineupRules {
    /** One Corrupt Cop and one Insider. / 一名黑警与一名内应。 */
    public static final int MAX_MEMBERS = 2;

    private JiahaoThirdFactionLineupRules() {
    }

    /**
     * Seats to deal, in deal order (Corrupt Cop first). Never a second Corrupt Cop or a second Insider.
     * 按发放顺序返回要发放的席位（黑警在前）。从不发放第二名黑警或第二名内应。
     */
    public static List<Seat> seats(
            int corruptCopsPresent,
            int insidersPresent,
            boolean corruptCopAvailable,
            boolean insiderEnabled,
            int remainingSlots
    ) {
        int room = Math.min(remainingSlots, MAX_MEMBERS - corruptCopsPresent - insidersPresent);
        if (room <= 0) {
            return List.of();
        }
        List<Seat> seats = new ArrayList<>(room);
        if (corruptCopsPresent == 0) {
            if (!corruptCopAvailable) {
                return List.of();
            }
            seats.add(Seat.CORRUPT_COP);
        }
        if (insidersPresent == 0 && insiderEnabled && seats.size() < room) {
            seats.add(Seat.INSIDER);
        }
        return List.copyOf(seats);
    }

    public enum Seat {
        CORRUPT_COP,
        INSIDER
    }
}
