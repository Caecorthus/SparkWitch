package dev.caecorthus.sparkwitch.roles.neutral.insider;

/**
 * Pure bound-spawn rule: the Insider follows a drawn Corrupt Cop (D1, C1, C2). Counts are taken after Wathe assigned
 * killers, vigilantes and neutrals and before civilians; the Insider takes a would-be civilian seat, never a neutral slot.
 * 纯绑定生成规则：内应跟随已抽到的黑警（D1、C1、C2）。计数取自 Wathe 分配完杀手、义警与中立之后、分配平民之前；
 * 内应占用原本属于平民的位置，从不占用中立名额。
 */
public final class InsiderPairingRules {
    private InsiderPairingRules() {
    }

    /**
     * ASSIGN only when the role is enabled, a Corrupt Cop was drawn, no Insider exists yet (a forced Insider is kept
     * as is, even without a Corrupt Cop), enough killer-team roles were actually assigned and a free player remains.
     * 仅当内应已启用、已抽到黑警、尚无内应（强制指定的内应即使没有黑警也原样保留）、实际分配的杀手阵营人数足够，
     * 且仍有空闲玩家时才 ASSIGN。
     */
    public static PairingAction pairingAction(
            boolean insiderEnabled,
            int corruptCopCount,
            int insiderCount,
            int killerCount,
            int freePlayers
    ) {
        return insiderEnabled
                && corruptCopCount >= 1
                && insiderCount == 0
                && killerCount >= InsiderRules.MIN_KILLERS
                && freePlayers >= 1
                ? PairingAction.ASSIGN
                : PairingAction.NONE;
    }

    public enum PairingAction {
        NONE,
        ASSIGN
    }
}
