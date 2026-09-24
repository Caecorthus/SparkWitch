package dev.caecorthus.sparkwitch.client.judge;

/** Pure fallback and transport gates, with no gameplay authority. / 无游戏裁决权的纯后备高亮与发送门禁。 */
public final class JudgeClientRules {
    private JudgeClientRules() {
    }

    public static boolean maySend(boolean confirmedServer, boolean channelAvailable, boolean activeJudge) {
        return confirmedServer && channelAvailable && activeJudge;
    }

    public static int resolveOutline(int originalColor, boolean activePair, boolean hardHidden,
                                     boolean eventSkipped, boolean sentenced, int sentenceColor) {
        return originalColor == -1 && activePair && !hardHidden && !eventSkipped && sentenced
                ? sentenceColor : originalColor;
    }
}
