package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * "不破盾" (owner 2026-11-11): the Rope's kill passes every shield and armour layer WITHOUT spending it. Stable seam,
 * the only death reason is {@code sparkwitch:strangled}; each shield asks {@link #passesShields} before it blocks or
 * consumes anything and steps aside (a {@code KillPlayer.BEFORE} listener returns null, never {@code allow()}, so the
 * later listeners still run):
 * <ul>
 *   <li>Guardian Angel shield, Pig God chase freeze, Ninja parry: an early return in their {@code beforeKill};</li>
 *   <li>NoellesRoles Iron Man buff and whiskey shield: {@code mixin/rope/NoellesRopeShieldPassMixin};</li>
 *   <li>Wathe psycho armour and, behind the same answer, the Specter's Otherworld shield: a
 *       {@code ShouldPiercePsychoArmour} listener registered by {@link RopeStrangleService};</li>
 *   <li>SparkStrength's Democracy Shield and vest only act on their own reason table, which does not list this
 *       reason; the Rys-T ricochet only acts on ranged reasons.</li>
 * </ul>
 * Trait-type protections and role immunities are not shields and keep stopping the kill (the Ceremonial Sword's line):
 * SparkTraits Last Stand, Last Escape, Stands Firm and Depression, the Fiend's immunity, the Charon's pardon, the Saint
 * and SparkFactionAPI affect vetoes, Tofana, Schrödinger's Cat, the Jester's stasis. Unlike
 * {@code CeremonialSwordProtectionPolicy}, nothing is consumed first.
 * 「不破盾」（所有者 2026-11-11）：绳索的击杀穿过所有护盾与护甲层且不消耗它们。稳定接缝，唯一的死因是
 * {@code sparkwitch:strangled}；每个护盾在拦截或消耗之前都先询问 {@link #passesShields} 并让开（{@code KillPlayer.BEFORE}
 * 监听返回 null 而非 {@code allow()}，后续监听仍会执行）：守护天使护盾、皮革噶的追杀定身、忍者格挡在各自 {@code beforeKill} 开头
 * 直接返回；NoellesRoles 铁人与威士忌护盾由 {@code mixin/rope/NoellesRopeShieldPassMixin} 处理；Wathe 疯魔护甲以及依赖同一答案的
 * 幽魂里世界护盾由 {@link RopeStrangleService} 注册的 {@code ShouldPiercePsychoArmour} 监听处理；SparkStrength 的民主盾牌与防弹衣
 * 只按自己的死因表生效（表中没有该死因），Rys-T 跳弹只作用于远程死因。词条类保护与职业免疫不是护盾，仍会挡下击杀（与仪礼剑同一条线）：
 * SparkTraits 背水一战、脱险、站得住与抑郁，魔人免疫，卡戎宽恕，圣徒与 SparkFactionAPI 的影响否决，托法娜，薛定谔之猫，小丑禁锢。
 * 与 {@code CeremonialSwordProtectionPolicy} 不同，这里不会先消耗任何东西。
 */
public final class RopeShieldPolicy {
    private RopeShieldPolicy() {
    }

    public static boolean passesShields(@Nullable Identifier deathReason) {
        return SparkWitchDeathReasons.STRANGLED.equals(deathReason);
    }
}
