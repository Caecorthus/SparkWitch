package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.api.Role;

/**
 * Bewitched (魔化使) role definition: a witch-faction member with the plain Accomplice profile (FAKE mood, unlimited
 * sprint, round clock, C1). SparkWitch deals it at round start in a Grand Witch round; it is never drawn naturally and
 * is not a Wathe special role, so {@code /wathe:forceRole} can still force it.
 * 魔化使职业定义：魔女阵营成员，参数与普通共犯一致（伪装情绪、无限疾跑、可见回合时间，C1）。由 SparkWitch 在有大魔女的
 * 对局开局时发放；从不自然抽取，也不是 Wathe 特殊职业，因此仍可用 {@code /wathe:forceRole} 强制指定。
 */
public final class BewitchedRole {
    public static final FactionRoleDefinition DEFINITION =
            FactionRoleDefinition.builder(BewitchedRules.ROLE_ID, SparkWitchFactions.WITCH)
                    .color(BewitchedRules.COLOR)
                    .moodType(Role.MoodType.FAKE)
                    .maxSprintTime(-1)
                    .canSeeTime(true)
                    // Exclude natural selection (Wathe's neutral bucket); WitchRoleAssignmentService deals it.
                    // 排除自然抽选（Wathe 中立抽取）；由 WitchRoleAssignmentService 发放。
                    .appearanceCondition(context -> false)
                    .build();

    private BewitchedRole() {
    }
}
