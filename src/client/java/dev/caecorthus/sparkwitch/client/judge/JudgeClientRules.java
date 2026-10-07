package dev.caecorthus.sparkwitch.client.judge;

/** Pure fallback and transport gates, with no gameplay authority. / 无游戏裁决权的纯后备高亮与发送门禁。 */
public final class JudgeClientRules {
    private JudgeClientRules() {
    }

    public static boolean maySend(boolean confirmedServer, boolean channelAvailable, boolean activeJudge) {
        return confirmedServer && channelAvailable && activeJudge;
    }

    /** HUD hint only; the server re-checks the balance on confirm. / 仅用于 HUD 提示；确认时服务端会重新检查余额。 */
    public static boolean canAffordJudgment(int balance, int cost) {
        return balance >= cost;
    }

    public static int resolveOutline(int originalColor, boolean activePair, boolean hardHidden,
                                     boolean eventSkipped, boolean sentenced, int sentenceColor) {
        return originalColor == -1 && activePair && !hardHidden && !eventSkipped && sentenced
                ? sentenceColor : originalColor;
    }
}
