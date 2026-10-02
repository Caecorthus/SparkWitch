package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.api.Role;

/**
 * Riftwalker (隙行者) role definition: a witch-faction special accomplice whose profile mirrors the plain Accomplice
 * (FAKE mood, unlimited sprint, round clock). It is only ever assigned by the special-accomplice pool during Grand Witch
 * recruitment (or forced by an admin), never drawn naturally.
 * 隙行者职业定义：魔女阵营特殊共犯，参数与普通共犯一致（伪装情绪、无限疾跑、可见回合时间）。
 * 只会在大魔女招募时由特殊共犯池分配（或由管理员强制指定），从不自然抽取。
 */
public final class RiftwalkerRole {
    public static final FactionRoleDefinition DEFINITION =
            FactionRoleDefinition.builder(RiftwalkerRules.ROLE_ID, SparkWitchFactions.WITCH)
                    .color(RiftwalkerRules.COLOR)
                    .moodType(Role.MoodType.FAKE)
                    .maxSprintTime(-1)
                    .canSeeTime(true)
                    // Exclude natural selection; the special-accomplice pool assigns this registered role on recruit.
                    // 排除自然抽选；招募时由特殊共犯池直接赋予这个已注册职业。
                    .appearanceCondition(context -> false)
                    .build();

    private RiftwalkerRole() {
    }
}
