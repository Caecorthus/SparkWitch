package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

public final class MightyForceAbility {
    public static final Identifier ID = SparkWitch.id("mighty_force");
    public static final int COLOR = 0x75EDFA;
    public static final int MANA_COST = 60;
    public static final int WINDOW_TICKS = GameConstants.getInTicks(0, 10);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 45);
    public static final double KNOCKBACK_STRENGTH = 10.0;

    private MightyForceAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        // A misfire under Wathe's PREVENT_GUN_PICKUP setting takes Mighty Force away for the round (owner D5).
        // 在 Wathe 的禁止拾枪设置下误杀好人，会让巨力本局失效（所有者 D5）。
        if (ApprenticePlayerComponent.KEY.get(context.player()).isMightyForceForfeited()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.mighty_force.forfeited");
        }
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.mighty_force.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginMightyForceWindow(WINDOW_TICKS)
        );
    }
}
