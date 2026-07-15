package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Ninja tuning and kill predicates.
 * 忍者的纯数值与击杀判断集中在这里，运行时接线由外层服务负责。
 */
public final class NinjaRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "ninja");
    public static final Identifier PARRY_SKILL_ID = Identifier.of("sparkwitch", "ninja_parry");
    public static final int COLOR = 0x2C2C2C;
    public static final int PARRY_INITIAL_COOLDOWN_TICKS = 1200;
    public static final int PARRY_WINDOW_TICKS = 50;
    public static final int PARRY_COOLDOWN_TICKS = 3600;
    public static final int DARK_KILL_BOUNTY = 100;
    public static final int DARKNESS_MAX_RAW_BRIGHTNESS = 5;
    public static final int NINJA_KNIFE_PRICE = 130;
    public static final int NINJA_SHURIKEN_PRICE = 275;
    public static final int LOCKPICK_PRICE = 75;
    private static final Set<Identifier> UNPARRYABLE_WATHE_DEATH_REASONS = Set.of(
            GameConstants.DeathReasons.GUN_BACKFIRE,
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.ESCAPED,
            GameConstants.DeathReasons.SHOT_INNOCENT,
            GameConstants.DeathReasons.MENTAL_BREAKDOWN,
            GameConstants.DeathReasons.VANILLA_DEATH,
            GameConstants.DeathReasons.DROWNED
    );

    private NinjaRules() {
    }

    public static boolean isNinja(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean shouldParryPlayerKill(
            boolean ninjaVictim,
            boolean parryActive,
            boolean selfKill,
            @Nullable Identifier deathReason
    ) {
        return ninjaVictim
                && parryActive
                && !selfKill
                && deathReason != null
                && !UNPARRYABLE_WATHE_DEATH_REASONS.contains(deathReason);
    }

    public static boolean isDarkKillLocation(int rawBrightness, boolean blackoutActive) {
        return blackoutActive || rawBrightness <= DARKNESS_MAX_RAW_BRIGHTNESS;
    }
}
