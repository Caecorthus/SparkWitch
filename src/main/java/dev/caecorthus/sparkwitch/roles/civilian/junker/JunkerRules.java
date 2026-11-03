package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 拾荒者 Ragpicker — civilian who sees dropped items through walls and loots corpses.
 * <p>Ported from NoellesRoles' Parox build, where the role is {@code noellesroles:junker}, a civilian
 * ({@code isInnocent = true, canUseKiller = false}, real sanity, passenger stamina). The SparkWitch copy keeps its own
 * id so the two can coexist.
 * 拾荒者——能隔着墙看见掉落物、并能从尸体上搜刮物品的平民。
 * <p>移植自 NoellesRoles 的 Parox 版本（原 id 为 {@code noellesroles:junker}），在原版里是平民
 * （{@code isInnocent = true, canUseKiller = false}、真实理智、乘客级体力）。SparkWitch 这份用自己的 id，两者可共存。
 */
public final class JunkerRules {

    public static final Identifier ROLE_ID = SparkWitch.id("ragpicker");

    /** Identity colour from the source build: (145, 135, 110). 沿用原实现的身份色 (145, 135, 110)。 */
    public static final int COLOR = 0x91876E;

    /** Round-start cooldown for this role's skill, in ticks (45 s, owner 2026-11-02).
     * 该身份技能的回合初始冷却（45 秒，所有者 2026-11-02）。 */
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;

    /** How long the player must keep the corpse in sight, in ticks (5 s). 需要持续注视尸体的时长（5 秒）。 */
    public static final int SEARCH_TICKS_REQUIRED = 5 * 20;

    /** Maximum distance between the player and the corpse. 玩家与尸体的最大距离。 */
    public static final double SEARCH_RANGE = 3.0D;

    /** Cooldown after a successful loot, in ticks (60 s). 成功搜刮后的冷却（60 秒）。 */
    public static final int SEARCH_COOLDOWN_TICKS = 60 * 20;

    /** Dropped items glow within this many blocks, through walls. 掉落物在这个半径内透墙高亮。 */
    public static final double LOOT_VISION_RANGE = 8.0D;

    /** Custom-data key marking a stack as junker loot. 标记“搜刮所得”的自定义数据键。 */
    public static final String LOOT_MARKER = "sparkwitch_ragpicker_loot";

    private JunkerRules() {
    }
}
