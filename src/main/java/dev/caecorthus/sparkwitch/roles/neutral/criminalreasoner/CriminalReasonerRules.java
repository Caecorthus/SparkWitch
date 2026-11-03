package dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 犯罪推理学家 Criminal Reasoner — neutral role that wins by naming every victim's killer.
 * <p>Ported from NoellesRoles' Parox build (owner request, 2026-11-01). Owner-facing rules:
 * neutral faction with a fake sanity bar and limited sprint stamina, cannot pick up the revolver, carries the
 * neutral master key, reasons about the un-solved dead through a menu, and each new success grants one 20 s
 * invisibility charge that no instinct highlight can see through.
 * 犯罪推理学家——靠指认每名死者的凶手获胜的中立身份。
 * <p>按所有者要求（2026-11-01）从 NoellesRoles 的 Parox 版本移植。对外规则：
 * 中立阵营、假理智条、疾跑体力有限、不能捡左轮、携带中立万能钥匙；
 * 通过菜单推理尚未解出的死者，每获得一次新的成功就得到一次 20 秒隐身，且任何本能透视都看不穿。
 */
public final class CriminalReasonerRules {

    public static final Identifier ROLE_ID = SparkWitch.id("criminal_reasoner");

    /** Identity colour from the source build: (112, 75, 75). 沿用原实现的身份色 (112, 75, 75)。 */
    public static final int COLOR = 0x704B4B;

    /** Round-start cooldown for this role's skill, in ticks (45 s, owner 2026-11-02).
     * 该身份技能的回合初始冷却（45 秒，所有者 2026-11-02）。 */
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;

    /** Cooldown after a correct reasoning, in ticks (5 s). 推理成功后的冷却（5 秒）。 */
    public static final int SUCCESS_COOLDOWN_TICKS = 5 * 20;

    /** Cooldown after a wrong reasoning, in ticks (60 s). 推理失败后的冷却（60 秒）。 */
    public static final int FAIL_COOLDOWN_TICKS = 60 * 20;

    /** How long one invisibility charge lasts, in ticks (20 s). 一次隐身持续的时长（20 秒）。 */
    public static final int INVISIBILITY_TICKS = 20 * 20;

    /** Delay before the success notice reaches the other factions, in ticks (5 s). 成功通知发给他方阵营的延迟（5 秒）。 */
    public static final int NOTICE_DELAY_TICKS = 5 * 20;

    /** One charge is granted per new solved victim. 每解出一名新死者给一次充能。 */
    public static final int CHARGES_PER_SUCCESS = 1;

    /** Victim count divisor for the win threshold: floor(players / 3). 胜利阈值：⌊开局人数 / 3⌋。 */
    public static final int WIN_THRESHOLD_DIVISOR = 3;

    private CriminalReasonerRules() {
    }
}
