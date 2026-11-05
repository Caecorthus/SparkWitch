package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 会计 Accountant (PR #195): a plain task civilian with no active skill. Ids, numbers and the pure gates; the runtime
 * lives in the services next to this class.
 * <ul>
 *   <li>Income: +{@link #TASK_REWARD} per task (never for a SparkTraits Impostor) and +{@link #PASSIVE_INCOME} every
 *   {@link #PASSIVE_INCOME_INTERVAL_TICKS} ticks, both through {@code addToBalance} and only while {@link #canEarn}.</li>
 *   <li>Shop: one 存折 passbook entry, {@link #PASSBOOK_PRICE} coins, unlimited stock,
 *   {@link #PASSBOOK_PURCHASE_COOLDOWN_TICKS} ticks between purchases.</li>
 *   <li>Passbook (owner 2026-11-04): only a living Accountant may deposit; anyone holding it may withdraw everything
 *   (the passbook is used up); sneak + right-click drops it; it drops on the death of whoever carries it.</li>
 * </ul>
 * 会计（PR #195）：没有主动技能的普通任务平民。此处只放 id、数值与纯判定；运行逻辑在同包的各服务中。
 * 收入：每个任务 +{@link #TASK_REWARD}（SparkTraits 内鬼不发），另每 {@link #PASSIVE_INCOME_INTERVAL_TICKS} 刻
 * +{@link #PASSIVE_INCOME}，都通过 {@code addToBalance} 发放，且只在 {@link #canEarn} 成立时发放。
 * 商店：一个存折条目，{@link #PASSBOOK_PRICE} 金币，不限购，两次购买间隔 {@link #PASSBOOK_PURCHASE_COOLDOWN_TICKS} 刻。
 * 存折（所有者 2026-11-04）：只有存活的会计能存钱；任何持有者都能全部取出（存折随之用掉）；潜行 + 右键丢出；
 * 携带者死亡时掉落。
 */
public final class AccountantRules {
    public static final Identifier ROLE_ID = SparkWitch.id("accountant");
    public static final Identifier PASSBOOK_ID = SparkWitch.id("accountant_passbook");

    /** Identity colour FFFFC5. / 身份色 FFFFC5。 */
    public static final int COLOR = 0xFFFFC5;

    // ---- Income / 收入 ----

    public static final int TASK_REWARD = 50;
    public static final int PASSIVE_INCOME = 5;
    public static final int PASSIVE_INCOME_INTERVAL_TICKS = 10 * 20;

    // ---- Shop / 商店 ----

    /** Bare shop-entry id, like the other SparkWitch shops. / 不带命名空间的商店条目 id，与其他 SparkWitch 商店一致。 */
    public static final String PASSBOOK_ENTRY_ID = "accountant_passbook";
    public static final int PASSBOOK_PRICE = 75;
    public static final int PASSBOOK_PURCHASE_COOLDOWN_TICKS = 30 * 20;

    // ---- Passbook / 存折 ----

    /**
     * Stored coins live in the stack's {@code minecraft:custom_data} under this key, so they travel with the item
     * (drop, pickup, death drop). The item is registered with {@code maxCount(1)}: a stack would share one value.
     * 存入的金币保存在物品的 {@code minecraft:custom_data} 的这个键下，随物品一起移动（丢出、拾取、死亡掉落）。物品以
     * {@code maxCount(1)} 注册：一叠物品会共享同一个数值。
     */
    public static final String STORED_COINS_KEY = "sparkwitch:stored_coins";

    // ---- Replay / 回放 ----

    public static final Identifier DEPOSIT_SKILL_ID = SparkWitch.id("accountant_deposit");
    public static final Identifier WITHDRAW_SKILL_ID = SparkWitch.id("accountant_withdraw");
    public static final String REPLAY_AMOUNT_KEY = "amount";

    private AccountantRules() {
    }

    public static boolean isAccountant(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Gate for every passbook action and for the income, the USEC/Discoverer {@code canEarn} shape without the role
     * test: an ACTIVE round, playing and alive in Wathe, survival (so a Taotie-swallowed player or a Rift occupant, both
     * alive spectators, is refused) and not a restricted Wraith.
     * 所有存折操作与收入共用的门槛，沿用 USEC 与探索者的 {@code canEarn}（不含职业判断）：对局 ACTIVE、在 Wathe 中参与且
     * 存活、生存模式（被饕餮吞下的玩家与裂隙门内的玩家这类存活旁观者都被拒绝）、不是受限冤魂。
     */
    public static boolean canAct(boolean active, boolean playingAndAlive, boolean spectator, boolean creative,
                                 boolean wraithRestricted) {
        return active && playingAndAlive && !spectator && !creative && !wraithRestricted;
    }

    /** {@link #canAct} plus exactly the Accountant. / {@link #canAct} 且身份恰为会计。 */
    public static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                                  boolean creative, boolean wraithRestricted) {
        return isAccountant(role) && canAct(active, playingAndAlive, spectator, creative, wraithRestricted);
    }

    /**
     * Pays only on a confirmed non-Impostor: SparkTraits pays an Impostor's task income itself, so the two never stack.
     * An absent SparkTraits reads as FALSE (pay); an unknown answer (null) never pays.
     * 只在确认不是内鬼时发放：SparkTraits 自行支付内鬼的任务收入，两者绝不叠加。未安装 SparkTraits 视为 FALSE（发放）；
     * 未知答案（null）从不发放。
     */
    public static boolean receivesTaskMoney(@Nullable Boolean impostor) {
        return Boolean.FALSE.equals(impostor);
    }

    /**
     * Passive coins due on this world tick (aligned like Wathe's {@code PASSIVE_MONEY_TICKER} and USEC), else 0.
     * 本世界刻应发的被动金币（与 Wathe {@code PASSIVE_MONEY_TICKER} 及 USEC 一样按世界时间对齐），否则为 0。
     */
    public static int passiveIncomeAt(long worldTime) {
        return worldTime > 0L && worldTime % PASSIVE_INCOME_INTERVAL_TICKS == 0L ? PASSIVE_INCOME : 0;
    }

    public enum DepositVerdict {
        /** Move the whole balance into the passbook. / 把全部余额存入存折。 */
        DEPOSIT,
        /** A living holder who is not the Accountant. / 存活但不是会计的持有者。 */
        NOT_ACCOUNTANT,
        /** The Accountant carries no coins. / 会计身上没有金币。 */
        NO_COINS,
        /** Not in a state to act (round not running, dead, spectator, Wraith); silent. / 不可操作（静默）。 */
        REFUSED
    }

    public static DepositVerdict depositVerdict(boolean canAct, boolean accountant, int balance) {
        if (!canAct) {
            return DepositVerdict.REFUSED;
        }
        if (!accountant) {
            return DepositVerdict.NOT_ACCOUNTANT;
        }
        return balance > 0 ? DepositVerdict.DEPOSIT : DepositVerdict.NO_COINS;
    }

    public enum WithdrawVerdict {
        /** Pay out everything stored and use the passbook up. / 取出全部存款并用掉存折。 */
        WITHDRAW,
        /** The passbook holds nothing; it is kept. / 存折里没有钱；存折保留。 */
        EMPTY,
        /** Not in a state to act, or no passbook in the main hand; silent. / 不可操作或主手没有存折（静默）。 */
        REFUSED
    }

    public static WithdrawVerdict withdrawVerdict(boolean canAct, boolean holdingPassbook, int stored) {
        if (!canAct || !holdingPassbook) {
            return WithdrawVerdict.REFUSED;
        }
        return stored > 0 ? WithdrawVerdict.WITHDRAW : WithdrawVerdict.EMPTY;
    }

    /**
     * World targets that would keep a passbook through Wathe's round reset (vanilla 1.21.1 item frames incl. glow,
     * armor stands and allays take the held stack; a decorated pot inserts it). Pure class check, testable without a
     * bootstrapped registry; asked by the passbook's world-use veto.
     * 会让存折躲过 Wathe 回合重置的世界目标（原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品；饰纹陶罐会放入
     * 物品）。纯类判断，无需引导注册表即可测试；由存折的世界交互否决调用。
     */
    public static boolean takesHeldStack(@Nullable Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /** Overflow-safe sum for stored coins and balances. / 存款与余额的防溢出加法。 */
    public static int saturatingAdd(int a, int b) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) Math.max(0, a) + Math.max(0, b)));
    }
}
