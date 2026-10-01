package dev.caecorthus.sparkwitch.roles.witch.abysslistener.shriek;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;

/**
 * Warden's Shriek (监守之啸) use handler on the shared Witch-skill path (registered in SparkWitchBuiltInSkills with an
 * exact-role selector). Server-authoritative.
 * Frozen stub (L0): refuses every use until L1 implements it, so no cooldown is spent.
 * 共享魔女技能路径上的监守之啸处理器（在 SparkWitchBuiltInSkills 中以精确职业选择器注册）。由服务端权威决定。
 * 冻结桩（L0）：在 L1 实现之前拒绝所有使用，因此不会消耗冷却。
 */
public final class WardensShriekService {
    private WardensShriekService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        // L1 implements: spend SHRIEK_MANA_COST (the definition only displays it), then shriek every eligible non-ally
        // within 8 blocks; always enters cooldown, even with no target.
        // L1 实现：扣除 SHRIEK_MANA_COST（技能定义只用于显示），再对 8 格内所有符合条件的非队友尖啸；即使没有目标也照样进入冷却。
        return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
    }
}
