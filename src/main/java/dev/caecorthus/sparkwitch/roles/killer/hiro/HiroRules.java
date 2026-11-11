package dev.caecorthus.sparkwitch.roles.killer.hiro;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 二阶堂希罗 Nikaido Hiro (killer): every number of the role and its raw-role predicates. Feature state lives in the
 * sub-packages: {@code returning} (死亡回归 Return by Death, ability key G), {@code witchification} (魔女化, passive) and
 * {@code shop}; the Rope is {@code item/rope} with its own {@code RopeRules}.
 * <p>Mana (owner 2026-11-11): Hiro uses exactly the Witch Maiden's mana system (0 at round start, +1 per 20 ticks,
 * natural cap 250, no kill or task rewards), wired in {@code mana/WitchManaRules}; it pays only for 死亡回归. She is never
 * a Witch-skill role and never appears in the {@code gui.sparkwitch.skills} panel.
 * 二阶堂希罗（杀手）：本职业的全部数值与真实身份判定。功能状态位于子包：{@code returning}（死亡回归，技能键 G）、
 * {@code witchification}（魔女化，被动）与 {@code shop}；绳索位于 {@code item/rope}，数值在其 {@code RopeRules} 中。
 * <p>魔力（所有者 2026-11-11）：希罗沿用与巫女完全相同的魔力系统（开局 0、每 20 tick +1、自然上限 250、无击杀或任务奖励），
 * 在 {@code mana/WitchManaRules} 中接入；魔力只用于死亡回归。她不是魔女技能职业，也绝不出现在 {@code gui.sparkwitch.skills} 面板中。
 */
public final class HiroRules {
    public static final Identifier ROLE_ID = SparkWitch.id("nikaido_hiro");
    // Placeholder crimson until the owner picks the final colour. / 占位用绯红色，待所有者确定最终颜色。
    public static final int COLOR = 0xC8324B;

    /**
     * Stable id of 死亡回归: the SparkFactionAPI role-skill cooldown store id, and the id the feature should use for its
     * replay skill line and {@code canAffectPlayer} action.
     * 死亡回归的稳定 id：SparkFactionAPI 职业技能冷却存储的 id；功能实现的回放技能条目与 {@code canAffectPlayer} 行为也应使用它。
     */
    public static final Identifier RETURN_SKILL_ID = SparkWitch.id("nikaido_hiro_return");

    // 死亡回归 (owner 2026-11-11): 15 s window; victims killed inside it feign death for 15 s each.
    // 死亡回归（所有者 2026-11-11）：窗口 15 秒；窗口内被她击杀者各假死 15 秒。
    public static final int RETURN_WINDOW_TICKS = 300;
    public static final int VICTIM_FEIGN_TICKS = 300;
    // First use 90 s after role assignment; 45 s cooldown counted from the moment the skill ends.
    // 分配职业后 90 秒才能首次使用；冷却 45 秒，从技能结束时开始计算。
    public static final int OPENING_COOLDOWN_TICKS = 1800;
    public static final int RETURN_COOLDOWN_TICKS = 900;
    // Mana spent when the skill is cast (WitchPlayerComponent#spendMana). / 施放时消耗的魔力（WitchPlayerComponent#spendMana）。
    public static final int RETURN_MANA_COST = 100;
    // A cancelled attack: +75 coins, 5 s Glowing, 10 s all-item cooldown. / 攻击被取消：+75 金币、5 秒发光、全物品 10 秒冷却。
    public static final int RETURN_REWARD_COINS = 75;
    public static final int RETURN_GLOW_TICKS = 100;
    public static final int RETURN_ITEM_LOCK_TICKS = 200;

    private HiroRules() {
    }

    /** 魔女化 kill threshold: one per six round players, plus one. / 魔女化所需击杀数：每 6 名本局玩家 1 次，再加 1。 */
    public static int requiredKills(int players) {
        return Math.max(0, players) / 6 + 1;
    }

    public static boolean isHiro(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Raw role of {@code player} in {@code game}; {@code getRole} is never widened by the Black Raven acting overlay.
     * {@code game} 中 {@code player} 的真实身份；{@code getRole} 不受黑羽鸦扮演覆盖层影响。
     */
    public static boolean isHiro(@Nullable GameWorldComponent game, @Nullable PlayerEntity player) {
        return game != null && player != null && isHiro(game.getRole(player));
    }

    /** Raw role, either side. / 真实身份，两端通用。 */
    public static boolean isHiro(@Nullable PlayerEntity player) {
        return player != null && isHiro(GameWorldComponent.KEY.get(player.getWorld()), player);
    }

    /**
     * Raw-role Hiro who is alive, playing and not spectating; side-neutral (the client reads synced state).
     * 真实身份为希罗、存活、参与对局且非旁观；两端通用（客户端读取同步状态）。
     */
    public static boolean isLivingHiro(@Nullable PlayerEntity player) {
        return isHiro(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }
}
