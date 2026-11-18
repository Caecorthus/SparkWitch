package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Sniper contract: a Team Jiahao member that shares the Corrupt Cop, Insider and Sphinx Hao co-win
 * ({@code JiahaoTeamWinRules}), blocks killers and passengers while alive, starts with no money and earns the same task
 * pay as a civilian. Never drawn at random (the owner will rework role rolling later); it is handed out by admins, and
 * a forced Sniper takes the lineup's partner seat beside the Corrupt Cop ({@code JiahaoThirdFactionLineup}).
 * 狙击手契约：嘉豪阵营成员，与黑警、内应、狮身豪共同获胜（{@code JiahaoTeamWinRules}），存活时阻止杀手和乘客获胜；开局没有
 * 钱，任务收入与平民相同。不参与随机抽取（所有者之后会重做刷新规则），当前只由管理员发放；强制指定的狙击手占用阵容中黑警旁的
 * 同伴席位（{@code JiahaoThirdFactionLineup}）。
 */
public final class SniperRules {
    public static final Identifier ROLE_ID = SparkWitch.id("sniper");

    /** Pale US Army olive drab, picked to sit apart from the Corrupt Cop's navy and the Insider's mint.
     *  偏淡的二战美军橄榄绿，与黑警的深蓝、内应的薄荷青区分开。 */
    public static final int COLOR = 0xA5B382;

    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;

    public static final int AMMO_PRICE = 25;
    public static final int CROWBAR_PRICE = 25;
    /** Crowbar purchases are capped at one per round; ammunition is not capped. / 撬棍每局限购一件，子弹不限量。 */
    public static final int CROWBAR_STOCK = 1;
    public static final String AMMO_ENTRY_ID = "sparkwitch_sniper_ammo";
    public static final String CROWBAR_ENTRY_ID = "sparkwitch_sniper_crowbar";

    private SniperRules() {
    }

    public static boolean isSniperRole(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
