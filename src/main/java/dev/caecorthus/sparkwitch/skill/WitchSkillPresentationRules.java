package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure client presentation rules for SparkWitch skill UI surfaces.
 * SparkWitch 技能 UI 展示用的纯规则，避免把技能本身和展示位置耦合在一起。
 */
public final class WitchSkillPresentationRules {
    private WitchSkillPresentationRules() {
    }

    public static boolean shouldShowInventorySkillPanel(@Nullable Role role, @Nullable Identifier skillId) {
        return skillId != null
                && SparkWitchRoles.isSparkWitchRole(role)
                && !PigGodRules.isPigGod(role);
    }
}
