package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownSlot;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * GW-AC: for every forcible cooldown the target has (role skills and carried items, through the SparkFactionAPI
 * forced-cooldown registry), add 20% of that cooldown's nominal length, scaled by falloff, on top of what remains; a
 * ready skill or item starts a fresh cooldown of that length. Slots without a known nominal are skipped. The registry
 * writes exactly (Fast Hands cannot shrink it) and never shortens; the affect veto already ran in the blast resolver.
 * GW-AC：对目标每一项可被强制的冷却（经 SparkFactionAPI 强制冷却注册表的职业技能与携带物品），在剩余冷却之上追加该项
 * 标准冷却的 20%（按衰减缩放）；已就绪的技能或物品则从头进入这段冷却。未知标准冷却的项被跳过。注册表精确写入（快手无法缩短）
 * 且绝不缩短；影响否决已在爆炸判定中执行。
 */
public final class AcShellEffect {
    static final String JAMMED_MESSAGE = "message.sparkwitch.potion_gunner.ac_jammed";

    private AcShellEffect() {
    }

    public static void apply(PotionBlastContext context) {
        for (PotionBlastHit hit : context.hits()) {
            ServerPlayerEntity target = hit.target();
            // The resolver already dropped allies and the gunner; both flags are re-checked fail-closed.
            // 判定器已剔除队友与药炮手本人；此处再按失败即关闭的原则复查两项标记。
            if (hit.ally() || PotionShellTargets.isSelf(context, hit) || !PotionShellTargets.isLiving(target)) {
                continue;
            }
            boolean jammed = false;
            for (CooldownSlot slot : ForcedCooldowns.slots(target)) {
                if (slot.nominalTicks().isEmpty()) {
                    continue;
                }
                int ticks = AcShellEffectRules.penaltyTicks(slot.nominalTicks().getAsInt(), hit.factor());
                if (ticks > 0 && ForcedCooldowns.extend(target, slot, ticks)) {
                    jammed = true;
                }
            }
            if (jammed) {
                target.sendMessage(Text.translatable(JAMMED_MESSAGE), true);
            }
        }
    }
}
