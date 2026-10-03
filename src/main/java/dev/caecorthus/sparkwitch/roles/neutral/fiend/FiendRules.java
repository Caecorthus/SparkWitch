package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen Fiend contract: stable gameplay values and pure decisions (identifiers in, values out, no world access).
 * Work packages read these values and never redefine them.
 * 冻结的魔人契约：稳定玩法数值与纯规则判断（输入标识符、输出数值，不访问世界）。各工作包只读取、不重新定义这些数值。
 */
public final class FiendRules {
    public static final Identifier ROLE_ID = SparkWitch.id("fiend");
    public static final int COLOR = 0x8E1B3A;
    public static final int MIN_PLAYERS = 18;

    public static final int GUN_GOLD = 50;
    public static final int KNIFE_GOLD = 50;
    public static final int BAT_GOLD = 100;
    public static final int SWORD_GOLD = 100;
    public static final int BOMB_PASS_GOLD = 50;
    public static final int KNIFE_NOTES = 4;
    /** Speed III for 5 s after a gun hit (D7). / 被枪击后获得 5 秒速度 III（D7）。 */
    public static final int GUN_SPEED_TICKS = 100;
    public static final int GUN_SPEED_AMPLIFIER = 2;
    public static final double AURA_RADIUS = 8.0;
    public static final int AURA_COOLDOWN_TICKS = 400;

    public static final int MOMENT_PRICE = 200;
    public static final int MOMENT_DURATION_TICKS = 2400;
    public static final int MOMENT_SPEED_AMPLIFIER = 3;
    public static final int MOMENT_SHIELD_LAYERS = 1;
    public static final int MOMENT_CROWBAR_COOLDOWN_TICKS = 100;
    public static final String MOMENT_SHOP_ENTRY_ID = "sparkwitch_fiend_moment";

    /**
     * NoellesRoles Demon Hunter pistol kill. A literal id on purpose: NoellesRoles exposes it only as a mutable
     * static on its entrypoint class, and reading it would initialize that class.
     * NoellesRoles 猎魔枪击杀原因。刻意使用字面量：NoellesRoles 只在入口类上以可变静态字段暴露它，读取会触发该类初始化。
     */
    public static final Identifier DEMON_HUNTER_SHOT_ID = Identifier.of("noellesroles", "demon_hunter_shot");

    /** Attack kinds that pay the dormant Fiend. / 能让休眠魔人获得收益的攻击类型。 */
    public enum Hit {
        GUN,
        KNIFE,
        BAT,
        SWORD,
        NONE
    }

    private FiendRules() {
    }

    /**
     * Owner decision D3: only falling or being pushed off the train kills the dormant Fiend; forced and piercing
     * kills are blocked too. Disconnect ({@code wathe:escaped}) and {@code /kill} ({@code wathe:vanilla_death}) still
     * apply. An unknown ({@code null}) reason is treated as immune.
     * 所有者决定 D3：只有坠落或被推下列车能杀死休眠魔人，强制与穿透击杀同样被拦截。断线（{@code wathe:escaped}）与
     * {@code /kill}（{@code wathe:vanilla_death}）仍然生效。未知（{@code null}）原因视为免疫。
     */
    public static boolean mayDie(@Nullable Identifier deathReason) {
        return GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                || GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                || GameConstants.DeathReasons.VANILLA_DEATH.equals(deathReason);
    }

    /**
     * Owner decision D5: every gun counts as GUN (revolver, derringer and the Hunter shotgun share
     * {@code wathe:gun_shot}; plus the Demon Hunter pistol). A {@code wathe:knife_stab} counts only when the attacker
     * holds a knife in hand. Bat and Ceremonial Sword throat-cut map to BAT and SWORD; everything else is NONE.
     * 所有者决定 D5：所有枪械都算 GUN（左轮、短管手枪与猎人霰弹枪共用 {@code wathe:gun_shot}，另加猎魔枪）。
     * {@code wathe:knife_stab} 仅在攻击者手持刀时算 KNIFE。球棒与仪礼剑封喉分别为 BAT 与 SWORD，其余均为 NONE。
     */
    public static Hit classify(@Nullable Identifier deathReason, boolean killerHoldsKnife) {
        if (deathReason == null) {
            return Hit.NONE;
        }
        if (GameConstants.DeathReasons.GUN.equals(deathReason) || DEMON_HUNTER_SHOT_ID.equals(deathReason)) {
            return Hit.GUN;
        }
        if (GameConstants.DeathReasons.KNIFE.equals(deathReason)) {
            return killerHoldsKnife ? Hit.KNIFE : Hit.NONE;
        }
        if (GameConstants.DeathReasons.BAT.equals(deathReason)) {
            return Hit.BAT;
        }
        if (SparkWitchDeathReasons.CEREMONIAL_BLADE.equals(deathReason)) {
            return Hit.SWORD;
        }
        return Hit.NONE;
    }

    public static int goldFor(@Nullable Hit hit) {
        if (hit == null) {
            return 0;
        }
        return switch (hit) {
            case GUN -> GUN_GOLD;
            case KNIFE -> KNIFE_GOLD;
            case BAT -> BAT_GOLD;
            case SWORD -> SWORD_GOLD;
            case NONE -> 0;
        };
    }
}
