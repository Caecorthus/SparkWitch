package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the top-left inventory panel exclusive to the Witch-skill roles and their own skills: the Grand Witch,
 * Apprentice Witch and Murderous Witch, plus accomplices (owner decision D13: plain and special, i.e.
 * {@link WitchFactionRules#isAccompliceLike}), whose own skills are only the ids their registered
 * {@code AccompliceVariantHooks#ownSkillIds()} list. The plain Accomplice owns none, so it never shows the panel.
 * Registration in {@code WitchSkillRegistry}, the {@code sparkwitch} namespace, or shared dispatch and cooldowns
 * grant no access; every other role stays excluded. Runs every client frame, so it reads only lock-free state.
 * 背包左上角技能面板仅属于魔女技能职业且只展示其自有技能：大魔女、预备魔女、杀意魔女，以及共犯（所有者决定 D13：
 * 普通与特殊共犯，即 {@link WitchFactionRules#isAccompliceLike}），共犯的自有技能仅限其注册的
 * {@code AccompliceVariantHooks#ownSkillIds()} 所列 ID；普通共犯没有自有技能，因此从不显示面板。技能注册、
 * {@code sparkwitch} 命名空间或共享的分发与冷却均不授予资格，其他职业一律排除。客户端每帧运行，因此只读取无锁状态。
 */
public final class WitchSkillPresentationRules {
    private WitchSkillPresentationRules() {
    }

    public static boolean shouldShowInventorySkillPanel(@Nullable Role role, @Nullable Identifier skillId) {
        if (role == null || skillId == null) {
            return false;
        }
        if (role == SparkWitchRoles.grandWitch()) {
            return WitchFactorService.SKILL_ID.equals(skillId);
        }
        if (role == SparkWitchRoles.apprenticeWitch()) {
            return ApprenticeAbilityCatalog.ABILITY_IDS.contains(skillId);
        }
        if (role == SparkWitchRoles.murderousWitch()) {
            return MurderousWitchDeathRayRules.isDeathRaySkill(skillId);
        }
        // D13: an accomplice shows only the skill ids its variant hooks own (plain Accomplice: NONE, so never).
        // D13：共犯只展示其特殊共犯回调声明的自有技能（普通共犯为 NONE，因此从不显示）。
        return WitchFactionRules.isAccompliceLike(role)
                && AccompliceVariants.hooks(role).ownSkillIds().contains(skillId);
    }
}
