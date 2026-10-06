package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.List;
import net.minecraft.util.Identifier;

public final class ApprenticeAbilityCatalog {
    public static final List<Identifier> ABILITY_IDS = List.of(
            MightyForceAbility.ID,
            SwiftStepAbility.ID,
            MurderSenseAbility.ID,
            HealingAbility.ID,
            ClairvoyanceAbility.ID
    );

    // Opening cooldown 60 s -> 30 s (owner 2026-10-06 D10). / 开局冷却 60 秒 -> 30 秒（所有者 2026-10-06 D10）。
    public static final int INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(0, 30);

    private ApprenticeAbilityCatalog() {
    }
}
