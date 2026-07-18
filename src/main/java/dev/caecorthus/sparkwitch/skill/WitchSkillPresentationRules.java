package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the top-left inventory panel exclusive to the three Witch-skill roles and their own skills.
 * 背包左上角技能面板仅属于三个魔女技能职业，且只展示它们自己的技能。
 */
public final class WitchSkillPresentationRules {
    private WitchSkillPresentationRules() {
    }

    public static boolean shouldShowInventorySkillPanel(@Nullable Role role, @Nullable Identifier skillId) {
        if (role == null || skillId == null) {
            return false;
        }
        if (role == SparkWitchRoles.grandWitch()) {
            return GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skillId);
        }
        if (role == SparkWitchRoles.apprenticeWitch()) {
            return ApprenticeAbilityCatalog.ABILITY_IDS.contains(skillId);
        }
        return role == SparkWitchRoles.murderousWitch()
                && MurderousWitchDeathRayRules.isDeathRaySkill(skillId);
    }
}
