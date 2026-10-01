package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * Frozen Insider contract: stable gameplay values shared by every work package, which read and never redefine them.
 * The Insider is a native neutral that spawns only beside a Corrupt Cop and wins with it as Team Jiahao.
 * 冻结的内应契约：各工作包共用的稳定玩法数值，只读取、不重新定义。内应是 Wathe 原生中立，只随黑警生成，
 * 并与黑警组成嘉豪阵营共同获胜。
 */
public final class InsiderRules {
    public static final Identifier ROLE_ID = SparkWitch.id("insider");
    /** Mint, chosen to contrast with the Corrupt Cop's navy (D8). / 薄荷青，与黑警的深蓝形成反差（D8）。 */
    public static final int COLOR = 0x00FFD0;
    /** Paired only when at least this many killer-team roles were assigned (D1, C1). / 杀手阵营人数至少为此值时才配对（D1、C1）。 */
    public static final int MIN_KILLERS = 4;

    /**
     * NoellesRoles Corrupt Cop. A literal id on purpose: reading NoellesRoles' static fields would initialize its
     * entrypoint class.
     * NoellesRoles 黑警。刻意使用字面量：读取 NoellesRoles 的静态字段会触发其入口类初始化。
     */
    public static final Identifier CORRUPT_COP_ID = Identifier.of("noellesroles", "corrupt_cop");
    /** RGB of NoellesRoles' {@code new Color(25, 50, 100)}, without its alpha byte. / NoellesRoles 黑警颜色的 RGB，不含 alpha。 */
    public static final int CORRUPT_COP_COLOR = 0x193264;
    /**
     * What killers see on the Insider: SparkTraits' Impostor instinct blue, mirrored because SparkWitch may not read
     * SparkTraits internals (D3).
     * 杀手看到的内应颜色：SparkTraits 的内鬼本能蓝。SparkWitch 不能读取 SparkTraits 内部代码，所以在此镜像（D3）。
     */
    public static final int KILLER_VIEW_COLOR = 0x0013FF;

    public static final int TASK_MONEY_REWARD = 50;
    public static final int INITIAL_MONEY = 0;

    public static final int REVOLVER_PRICE = 150;
    public static final int CROWBAR_PRICE = 50;
    public static final int TABLET_PRICE = 100;
    public static final String REVOLVER_ENTRY_ID = "sparkwitch_insider_revolver";
    public static final String CROWBAR_ENTRY_ID = "sparkwitch_insider_crowbar";

    /** Neutral master key cooldown after a door opens, as for the Corrupt Cop (D6). / 万能钥匙开门后的冷却，与黑警相同（D6）。 */
    public static final int MASTER_KEY_COOLDOWN_TICKS = 200;

    private InsiderRules() {
    }
}
