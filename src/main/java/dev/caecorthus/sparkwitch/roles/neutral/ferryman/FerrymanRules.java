package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import dev.doctor4t.wathe.api.Role;

/**
 * Stable Ferryman contract: identifiers, tuning values and pure gates (identifiers and numbers in, values out, no
 * world access). The passive, the pardon, the ferry and the shop all read these values and never redefine them.
 * 摆渡人的稳定契约：标识符、调优数值与纯规则判断（输入标识符与数值、输出结果，不访问世界）。
 * 被动、宽恕、摆渡与商店都只读取这些数值，不另行定义。
 */
public final class FerrymanRules {
    public static final Identifier ROLE_ID = SparkWitch.id("charon");
    public static final int COLOR = 0x4C6B7A;

    /**
     * The Ferryman is never drawn below this population; it carries its own win condition, so small rounds keep it out.
     * 低于该人数时不抽取摆渡人：它自带胜利条件，小对局不放入。
     */
    public static final int MIN_PLAYERS = 8;

    /** The ability key is armed at round start and re-armed 10 s after a pardon. / 技能键开局 60 秒冷却，宽恕后 10 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int PARDON_COOLDOWN_TICKS = 10 * 20;

    /** Press window after a lethal hit (0.5 s). / 致命一击后的按键窗口（0.5 秒）。 */
    public static final int PARDON_WINDOW_TICKS = 10;

    /** A body stays "fresh" (highlighted, ferriable) for 60 s after death. / 尸体在死亡后 60 秒内保持新鲜（高亮、可摆渡）。 */
    public static final int FRESH_BODY_TICKS = 60 * 20;

    /** Ferry reach and pay. / 摆渡距离与报酬。 */
    public static final double FERRY_RANGE = 4.0;
    public static final double FERRY_RANGE_SQUARED = FERRY_RANGE * FERRY_RANGE;
    public static final int FERRY_GOLD = 50;
    public static final int MERCY_BREAK_GOLD = 50;

    /** Second hit on a marked attacker: 10 s of feign death, invisible to instinct for 5 s after it ends. */
    /** 被标记者再次攻击摆渡人：10 秒假死，结束后 5 秒内本能透视不到。 */
    public static final int FEIGN_TICKS = 10 * 20;
    public static final int FEIGN_INSTINCT_BLOCK_TICKS = 5 * 20;

    /** Shop. / 商店。 */
    public static final int MOMENT_PRICE = 250;
    public static final int CROWBAR_PRICE = 25;
    public static final String MOMENT_SHOP_ENTRY_ID = "sparkwitch_ferryman_moment";
    public static final String CROWBAR_SHOP_ENTRY_ID = "sparkwitch_ferryman_crowbar";

    /** Lost Soul revival: one body per 6 players in the round, at least one. / 亡魂时刻复活数量：每 6 名玩家 1 具，至少 1 具。 */
    public static final int SOUL_PLAYERS_PER_BODY = 6;

    private FerrymanRules() {
    }

    /** Exact-role gate; never inferred from faction or namespace. / 仅按精确职业判断，不从阵营或命名空间推断。 */
    public static boolean isFerryman(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean canPardon(int cooldownTicks) {
        return cooldownTicks <= 0;
    }

    public static boolean isFreshBody(long now, long deathGameTime) {
        return now - deathGameTime <= FRESH_BODY_TICKS;
    }

    public static boolean inFerryRange(double squaredDistance) {
        return squaredDistance <= FERRY_RANGE_SQUARED;
    }

    /** Number of Lost Souls one purchase may revive. / 一次购买可复活的迷失灵魂数量。 */
    public static int soulCount(int totalPlayers) {
        return Math.max(1, totalPlayers / SOUL_PLAYERS_PER_BODY);
    }
}
