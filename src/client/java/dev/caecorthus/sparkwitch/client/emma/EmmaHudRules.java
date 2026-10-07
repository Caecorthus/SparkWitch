package dev.caecorthus.sparkwitch.client.emma;

/** HUD priority depends only on owner resources and public geometry. / HUD 优先级仅取决于自身资源与公共几何。 */
public final class EmmaHudRules {
    private EmmaHudRules() {
    }

    public static State state(int cooldownTicks, int mana, int manaCost, boolean hasPublicTarget) {
        if (cooldownTicks > 0) {
            return State.COOLDOWN;
        }
        if (mana < manaCost) {
            return State.NEED_MANA;
        }
        return hasPublicTarget ? State.TRANSFER : State.NO_TARGET;
    }

    public enum State {
        COOLDOWN, NEED_MANA, NO_TARGET, TRANSFER
    }
}
