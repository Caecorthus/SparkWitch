package dev.caecorthus.sparkwitch.roles.killer.consultant;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 顾问 / Consultant: a killer-side information role that pays to learn a player's identity and records it.
 * 顾问：杀手阵营的情报型职业，付费得知一名玩家的身份并记录在案。
 */
public final class ConsultantRules {
    public static final Identifier ROLE_ID = SparkWitch.id("consultant");
    public static final int COLOR = 0x4A6FA5;

    /** Cost of one inspection. 一次查看的花费。 */
    public static final int INSPECT_COST = 25;
    /** Extra money a consultant gains whenever a teammate kills. 同伙击杀时顾问额外获得的金币。 */
    public static final int TEAMMATE_KILL_BONUS = 5;
    /** Maximum distance a target may be inspected from. 可查看的最大距离。 */
    public static final double REACH = 5.0D;
    /** How long an inspection stays on cooldown. 查看冷却。 */
    public static final int COOLDOWN_TICKS = 200;

    private ConsultantRules() {
    }
}
