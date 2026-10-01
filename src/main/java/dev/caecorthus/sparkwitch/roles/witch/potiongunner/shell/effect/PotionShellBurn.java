package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

/**
 * Harmless TR burning: visible fire with every fire-damage event vetoed while the owned burn lasts, so no hurt
 * flash, sound, or damage hook fires.
 * 无伤的 TR 燃烧：显示火焰，并在本效果持续期间否决所有火焰伤害事件，因此不会出现受伤闪红、受伤音效或伤害钩子。
 */
public final class PotionShellBurn {
    private static boolean registered;

    private PotionShellBurn() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // WP3 implements. / 由 WP3 实现。
    }
}
