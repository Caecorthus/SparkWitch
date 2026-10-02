package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;

/**
 * Witches' Sabbath (魔女集会, plan §10): the Riftwalker's 150-mana, instant, cooldown-free skill on the shared witch-skill
 * path ({@code SparkWitchBuiltInSkills}, exact-role selector). The handler validates the caster, finds free living
 * witch-faction teammates (C6; never those inside a gate, D6), spends the mana only when at least one can be pulled, and
 * places each on its own safe spot around the caster. Server only. Owned by P5.
 * 魔女集会（plan §10）：隙行者在共享魔女技能路径上的 150 魔力、瞬发、无冷却技能（{@code SparkWitchBuiltInSkills}，
 * 精确职业选择器）。处理器校验施法者，找出可召集的存活魔女阵营队友（C6；门内的除外，D6），至少能拉到一人时才扣魔力，
 * 并把每人放到施法者周围各自的安全落点。仅服务端。归属 P5。
 */
public final class WitchesSabbathService {
    static final String NOT_READY_MESSAGE_KEY = "message.sparkwitch.riftwalker.not_ready";
    private static boolean registered;

    private WitchesSabbathService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P5): replay formatter registration, if any. / TODO(P5)：回放格式化器注册（如需要）。
    }

    /**
     * Frozen skill handler ({@code WitchSkillDefinition.useHandler}). On success return
     * {@code WitchSkillUseResult.success(RiftwalkerRules.SABBATH_COOLDOWN_TICKS)} (0). G0 stub: placeholder failure,
     * nothing spent.
     * 冻结的技能处理器（{@code WitchSkillDefinition.useHandler}）。成功时返回
     * {@code WitchSkillUseResult.success(RiftwalkerRules.SABBATH_COOLDOWN_TICKS)}（0）。G0 存根：占位失败，不扣任何东西。
     */
    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        // TODO(P5)
        return WitchSkillUseResult.fail(NOT_READY_MESSAGE_KEY);
    }
}
