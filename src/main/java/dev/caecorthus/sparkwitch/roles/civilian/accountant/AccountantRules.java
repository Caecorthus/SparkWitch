package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 会计 accountant — passenger who audits the wallet leaderboard.
 * <p>Owner spec 2026-11-03: identity colour {@code FFFFC5}; the ability key hands her a slip listing the top five
 * players by carried coins (90 s initial, 75 s afterwards). The shop sells a passbook for 50 coins (4 purchases,
 * 30 s apart) that stores and releases her own coins.
 * 会计——乘客阵营，负责查账。
 * <p>所有者规格 2026-11-03：身份色 {@code FFFFC5}；按技能键获得一张纸条，列出携带金币前 5 名玩家
 * （开局 90 秒，之后 75 秒）。商店出售 50 金币的存折（最多 4 次，每次间隔 30 秒），用来存取自己的金币。
 */
public final class AccountantRules {

    public static final Identifier ROLE_ID = SparkWitch.id("accountant");
    public static final Identifier NOTE_ID = SparkWitch.id("accountant_note");
    public static final Identifier PASSBOOK_ID = SparkWitch.id("accountant_passbook");

    /** Identity colour FFFFC5. 身份色 FFFFC5。 */
    public static final int COLOR = 0xFFFFC5;

    /** Cooldowns: 90 s at round start, 75 s afterwards. 冷却：开局 90 秒，之后 75 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 90 * 20;
    public static final int COOLDOWN_TICKS = 75 * 20;

    /** Income: tasks pay 50, and 5 every 10 s passively. 收入：任务 50 金币，另每 10 秒 +5。 */
    public static final int TASK_COINS = 50;
    public static final int PASSIVE_COINS = 5;
    public static final int PASSIVE_INTERVAL_TICKS = 10 * 20;

    /** Slip lists the top five wallets. 纸条列出金币前 5 名。 */
    public static final int LEADERBOARD_SIZE = 5;

    /** Passbook: 75 coins, unlimited stock, 30 s between purchases. 存折：75 金币，不限量，每次购买间隔 30 秒。 */
    public static final int PASSBOOK_PRICE = 75;
    public static final int PASSBOOK_STOCK = 4;
    public static final int PASSBOOK_PURCHASE_COOLDOWN_TICKS = 30 * 20;

    private AccountantRules() {
    }
}
