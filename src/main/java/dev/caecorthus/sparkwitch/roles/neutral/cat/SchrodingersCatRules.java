package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 薛定谔之猫 / Schrödinger's cat: a neutral that survives any non-train-ejection kill by becoming the killer's faction.
 * 薛定谔之猫：中立阵营，被非"推出列车外"的伤害击杀时免疫死亡并转变为击杀者的阵营。
 */
public final class SchrodingersCatRules {
    public static final Identifier ROLE_ID = SparkWitch.id("schrodingers_cat");
    public static final int COLOR = 0xB07CD8;

    /** Money for each finished task. 每完成一个任务获得的金币。 */
    public static final int TASK_MONEY_REWARD = 25;

    private SchrodingersCatRules() {
    }
}
