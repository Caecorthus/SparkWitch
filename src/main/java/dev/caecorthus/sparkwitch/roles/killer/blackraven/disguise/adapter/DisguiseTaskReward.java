package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

/**
 * Which real payer a disguise's task income mirrors; the amount rule lives in BlackRavenDisguiseEconomy.
 * 伪装任务收入所镜像的真实付款方；金额规则位于 BlackRavenDisguiseEconomy。
 */
public enum DisguiseTaskReward {
    /** NoellesRoles +50, skipped when SparkTraits Conscience/Impostor already pays or the facade fails. / 诺艾尔职业 +50。 */
    NOELLES_NATIVE,
    /** SparkStrength good-role +50, only when SparkStrength is loaded and not Impostor. / SparkStrength 好人职业 +50。 */
    SPARKSTRENGTH_GOOD_ROLE,
    /** SparkWitch civilian economy +50, unconditional. / SparkWitch 平民经济 +50，无条件。 */
    SPARKWITCH,
    NONE
}
