package dev.caecorthus.sparkwitch.roles.neutral.insider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pure Team Jiahao lineup for SparkFactionAPI's round-start third-faction draw (owner, 2026-11-16 and Sphinx Hao Q1
 * 2026-11-17): one NoellesRoles Corrupt Cop, plus one partner when a second slot is free. The partner is the Insider or
 * 狮身豪 Sphinx Hao, 50/50 from a coin the caller flips; when only one of them is enabled, that one. Team Jiahao never
 * has more than {@link #MAX_MEMBERS} members from the draw: forced members (a forced Sphinx Hao counts like an Insider)
 * count toward it and are always kept, and the draw never deals a partner beside a present one, so never both an
 * Insider and a Sphinx Hao. The old "only with 4+ killers" pairing rule is gone, and the Corrupt Cop is no longer drawn
 * by Wathe's neutral pass. Without a Corrupt Cop (NoellesRoles absent or the role disabled) and none forced, the
 * faction is not drawable.
 * SparkFactionAPI 开局“第三阵营”抽取中嘉豪阵营的纯阵容规则（所有者 2026-11-16 及狮身豪 Q1 2026-11-17）：一名 NoellesRoles
 * 黑警，有第二个空位时再加一名同伴。同伴为内应或狮身豪，各 50%，由调用方抛的硬币决定；只有其中一个启用时就是它。经由抽取
 * 的嘉豪阵营成员从不超过 {@link #MAX_MEMBERS} 名：强制指定的成员（强制的狮身豪与内应同样计入）计入其中且始终保留；已有同伴
 * 时抽取不再发放同伴，因此从不同时出现内应与狮身豪。旧的“至少 4 名杀手才配对”规则已取消，黑警也不再由 Wathe 中立分配
 * 抽取。没有可用黑警（未安装 NoellesRoles 或该职业被禁用）且没有强制指定时，本阵营不可抽取。
 */
public final class JiahaoThirdFactionLineupRules {
    /** One Corrupt Cop and one partner (Insider or Sphinx Hao). / 一名黑警与一名同伴（内应或狮身豪）。 */
    public static final int MAX_MEMBERS = 2;

    private JiahaoThirdFactionLineupRules() {
    }

    /**
     * Seats to deal, in deal order (Corrupt Cop first). Never a second Corrupt Cop and never a partner beside a present
     * partner. {@code sphinxHaoCoin} is the caller's 50/50 flip (true picks Sphinx Hao, false the Insider), asked at
     * most once and only when a partner seat is dealt with both partners enabled.
     * 按发放顺序返回要发放的席位（黑警在前）。从不发放第二名黑警，已有同伴时也不再发放同伴。{@code sphinxHaoCoin} 是调用方
     * 的 50/50 硬币（true 选狮身豪，false 选内应），最多询问一次，且只在两者都启用并确实发放同伴席位时询问。
     */
    public static List<Seat> seats(
            int corruptCopsPresent,
            int partnersPresent,
            boolean corruptCopAvailable,
            boolean insiderEnabled,
            boolean sphinxHaoEnabled,
            BooleanSupplier sphinxHaoCoin,
            int remainingSlots
    ) {
        int room = Math.min(remainingSlots, MAX_MEMBERS - corruptCopsPresent - partnersPresent);
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
        if (partnersPresent == 0 && seats.size() < room) {
            Seat partner = partnerSeat(insiderEnabled, sphinxHaoEnabled, sphinxHaoCoin);
            if (partner != null) {
                seats.add(partner);
            }
        }
        return List.copyOf(seats);
    }

    /**
     * The second seat: the coin when both are enabled, else the enabled one, else none.
     * 第二个席位：两者都启用时看硬币，否则取启用的那个，都未启用则没有。
     */
    static Seat partnerSeat(boolean insiderEnabled, boolean sphinxHaoEnabled, BooleanSupplier sphinxHaoCoin) {
        if (insiderEnabled && sphinxHaoEnabled) {
            return sphinxHaoCoin.getAsBoolean() ? Seat.SPHINX_HAO : Seat.INSIDER;
        }
        if (insiderEnabled) {
            return Seat.INSIDER;
        }
        return sphinxHaoEnabled ? Seat.SPHINX_HAO : null;
    }

    public enum Seat {
        CORRUPT_COP,
        INSIDER,
        SPHINX_HAO
    }
}
