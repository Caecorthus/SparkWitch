package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class HealingAbility {
    public static final Identifier ID = SparkWitch.id("healing");
    public static final int COLOR = 0x6DF2A2;
    public static final int MANA_COST = 40;
    public static final int DURATION_TICKS = GameConstants.getInTicks(0, 20);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(2, 0);
    public static final double RANGE_BLOCKS = 8.0;
    public static final float MOOD_PER_SECOND = 0.03f;

    private HealingAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.healing.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginHealingAura(DURATION_TICKS)
        );
    }

    public static void applyPulse(ServerPlayerEntity caster) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(caster.getServerWorld());
        double rangeSquared = RANGE_BLOCKS * RANGE_BLOCKS;
        for (ServerPlayerEntity target : caster.getServerWorld().getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(target)
                    || caster.squaredDistanceTo(target) > rangeSquared
                    || !gameComponent.isInnocent(target)) {
                continue;
            }
            PlayerMoodComponent moodComponent = PlayerMoodComponent.KEY.get(target);
            moodComponent.setMood(Math.min(1.0f, moodComponent.getMood() + MOOD_PER_SECOND));
        }
    }
}
