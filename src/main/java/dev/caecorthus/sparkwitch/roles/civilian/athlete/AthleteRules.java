package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 运动员 / Athlete: a passenger with unlimited stamina who can dash five blocks forward on the secondary key.
 * 运动员：取消体力限制的乘客，按副技能键向前冲刺 5 格。
 */
public final class AthleteRules {
    public static final Identifier ROLE_ID = SparkWitch.id("athlete");
    public static final int COLOR = 0x6FD08C;

    /** Dash length in blocks. 冲刺格数。 */
    public static final int DASH_BLOCKS = 5;
    /** Shared cooldown between dashes. 冲刺间隔。 */
    public static final int COOLDOWN_TICKS = 300;
    /** Step used when searching for the furthest standable tile. 寻找最远可站立位置时的步长。 */
    public static final double DASH_STEP = 0.5D;

    private AthleteRules() {
    }
}
