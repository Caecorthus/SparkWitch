package dev.caecorthus.sparkwitch.item.sparekey;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 备用钥匙 Spare Key: tuning and pure decisions (owner decisions 2026-11-03). The NoellesRoles Attendant starts each
 * round with one; anyone holding it may use it. Seven points; a closed train door costs 3, a closed key-locked small
 * door costs 1; too few points refuse the door; zero points remove the key; a 5 s cooldown follows every opening.
 * 备用钥匙：数值与纯规则（所有者 2026-11-03 决定）。NoellesRoles 乘务员每局开局获得一把；任何持有者都能使用。
 * 共 7 点：关着的列车门消耗 3 点，关着的上锁小门消耗 1 点；点数不够则拒绝开门；点数归零钥匙消失；每次开门后冷却 5 秒。
 */
public final class SpareKeyRules {
    public static final Identifier ITEM_ID = SparkWitch.id("spare_key");
    public static final int DURABILITY = 7;
    /** Wathe {@code DoorType.TRAIN_DOOR}. / Wathe 列车门。 */
    public static final int TRAIN_DOOR_COST = 3;
    /** Wathe {@code DoorType.SMALL_DOOR} with a key name. / 设有钥匙名的 Wathe 小门。 */
    public static final int ROOM_DOOR_COST = 1;
    /** Written after each real opening; the SparkFactionAPI nominal. / 每次真正开门后写入；也是 SparkFactionAPI 标准冷却。 */
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 5);

    private SpareKeyRules() {
    }

    /** Which hand's key a door use spends. / 一次开门消耗哪只手上的钥匙。 */
    public enum KeyHand {
        NONE,
        MAIN,
        OFF
    }

    /**
     * Picks the key a door use may spend.
     * <p>The main-hand key is read from the event's hand item, Wathe's copy of the real main-hand stack, so remote
     * dispatches that pass an empty hand never reach it. The off-hand key acts only with both the event's hand and the
     * real main hand empty (a main-hand door tool always wins), only while the body is not remote-locked, and only
     * when the body could reach the door itself.
     * 选出一次开门可以消耗的钥匙。
     * <p>主手钥匙读取事件的手持物品（Wathe 对真实主手物品栈的副本），因此传入空手的远程分发永远碰不到它。副手钥匙只在
     * 事件手持与真实主手都为空时生效（主手开门工具永远优先），且本体未处于远程锁定、本体自己够得着这扇门。
     */
    public static KeyHand keyHand(boolean eventHandIsKey, boolean mainHandIsKey, boolean eventHandEmpty,
                                  boolean mainHandEmpty, boolean offHandIsKey, boolean bodyRemoteLocked,
                                  boolean bodyInReach) {
        if (eventHandIsKey && mainHandIsKey) {
            return KeyHand.MAIN;
        }
        if (eventHandEmpty && mainHandEmpty && offHandIsKey && !bodyRemoteLocked && bodyInReach) {
            return KeyHand.OFF;
        }
        return KeyHand.NONE;
    }

    /**
     * Same door gate as the Key Fish, plus a running round for small doors too: a closed, unblasted door with no
     * jammed leaf that is a train door or key-locked.
     * 与钥匙鱼相同的门判定，另外小门也要求对局进行中：关闭、未被炸开、两扇门扇都没被卡住，且为列车门或上锁的门。
     */
    public static boolean canUnlock(boolean open, boolean blasted, boolean jammed, boolean requiresKey,
                                    boolean train, boolean running) {
        return running && !open && !blasted && !jammed && (train || requiresKey);
    }

    public static int doorCost(boolean train) {
        return train ? TRAIN_DOOR_COST : ROOM_DOOR_COST;
    }

    public static boolean canPay(int remaining, int cost) {
        return remaining >= cost;
    }

    /** Remaining points from vanilla damage values. / 由原版耐久值得出的剩余点数。 */
    public static int remaining(int maxDamage, int damage) {
        return Math.max(0, maxDamage - damage);
    }

    /** One key per living final Attendant that holds none yet. / 每名尚未持有的存活最终乘务员发一把。 */
    public static boolean receivesRoundStartKey(boolean hasRole, boolean dead, @Nullable Role finalRole,
                                                boolean alreadyHoldsKey) {
        return hasRole && !dead && NoellesRoleIds.isAttendant(finalRole) && !alreadyHoldsKey;
    }
}
