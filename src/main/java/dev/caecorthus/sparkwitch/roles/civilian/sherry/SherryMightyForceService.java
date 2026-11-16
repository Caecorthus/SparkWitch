package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 橘雪莉's own 巨力魔法 on the Role Skill 2 key: the Apprentice's numbers (mana cost, window, cooldown after the window)
 * and the Apprentice's strike, misfire rule, death reason and replay line, but with her own state in
 * {@link SherryComponent}; it is never a Witch-skill-panel skill and never touches {@code WitchPlayerComponent}'s
 * active skill or cooldown. The strike itself stays in {@code MightyForceCombatService}, which asks this class whether
 * she holds a live window. Owner rule 2026-11-11: no exclusivity with an Apprentice who also carries Mighty Force.
 * 橘雪莉自己的巨力魔法，位于职业技能 2 键：数值（魔力消耗、窗口、窗口结束后的冷却）与出拳、误杀规则、死因、回放文案都沿用
 * 预备魔女，但状态保存在她自己的 {@link SherryComponent} 中；它不是魔女技能面板技能，也从不触碰 {@code WitchPlayerComponent}
 * 的当前技能与冷却。出拳本身仍在 {@code MightyForceCombatService} 中，由它来询问本类她是否持有生效的窗口。
 * 所有者规则 2026-11-11：不与同样携带巨力的预备魔女互斥。
 */
public final class SherryMightyForceService {
    private static boolean registered;

    private SherryMightyForceService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
    }

    /** The cooldown that starts when a window ends. / 窗口结束时开始的冷却。 */
    static int cooldownTicks() {
        return MightyForceAbility.COOLDOWN_TICKS;
    }

    /**
     * Server entry of the Role Skill 2 key. Every refusal happens before the mana is spent.
     * 职业技能 2 键的服务端入口。所有拒绝都发生在扣魔力之前。
     */
    public static void use(ServerPlayerEntity player) {
        if (player == null || !SherryDeductionService.mayUse(player)) {
            return;
        }
        SherryComponent component = SherryComponent.KEY.get(player);
        if (component.isMightyForfeited()) {
            tell(player, Text.translatable("message.sparkwitch.skill.mighty_force.forfeited"), Formatting.RED);
            return;
        }
        if (component.hasMightyWindow()) {
            return;
        }
        int cooldown = component.getMightyCooldownTicks();
        if (cooldown > 0) {
            tell(player, Text.translatable("message.sparkwitch.sherry.mighty.cooldown", (cooldown + 19) / 20),
                    Formatting.GRAY);
            return;
        }
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        if (mana.getMana() < MightyForceAbility.MANA_COST) {
            tell(player, Text.translatable("message.sparkwitch.skill.not_enough_mana"), Formatting.RED);
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player) || !mana.spendMana(MightyForceAbility.MANA_COST)) {
            return;
        }
        component.beginMightyWindow(MightyForceAbility.WINDOW_TICKS);
        tell(player, Text.translatable("message.sparkwitch.skill.mighty_force.activated"), Formatting.AQUA);
    }

    /**
     * Combat seam for {@code MightyForceCombatService}: the current Sherry inside her own window. The caller adds the
     * alive / spectator / swallowed gates it shares with the Apprentice.
     * 供 {@code MightyForceCombatService} 使用的战斗接缝：处于自己窗口内的现任雪莉。存活、旁观者、被吞等与预备魔女共用的门槛
     * 由调用方负责。
     */
    public static boolean hasLiveWindow(ServerPlayerEntity attacker) {
        return SherryRules.isSherry(GameWorldComponent.KEY.get(attacker.getWorld()).getRole(attacker))
                && SherryComponent.KEY.get(attacker).hasMightyWindow();
    }

    /** A strike landed: the window ends and the cooldown starts. / 命中：窗口结束并开始冷却。 */
    public static void onStrike(ServerPlayerEntity attacker) {
        SherryComponent.KEY.get(attacker).endMightyWindow(cooldownTicks());
    }

    /** Misfire under PREVENT_GUN_PICKUP: Mighty Force is gone for the round. / 禁止拾枪设置下误杀：巨力本局失效。 */
    public static void forfeit(ServerPlayerEntity attacker) {
        SherryComponent.KEY.get(attacker).forfeitMighty();
    }

    private static void tell(ServerPlayerEntity player, Text text, Formatting formatting) {
        player.sendMessage(text.copy().formatted(formatting), true);
    }
}
