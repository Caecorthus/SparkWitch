package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

/** Pure state selection for the Guardian Angel's private skill HUD. / 守护天使私有技能 HUD 的纯状态选择。 */
public final class GuardianAngelHudRules {
    public enum State {
        COOLDOWN,
        AIM_AT_PLAYER,
        READY
    }

    private GuardianAngelHudRules() {
    }

    public static State state(int cooldownTicks, boolean hasAimedPlayer) {
        if (cooldownTicks > 0) {
            return State.COOLDOWN;
        }
        return hasAimedPlayer ? State.READY : State.AIM_AT_PLAYER;
    }

    public static int cooldownSeconds(int cooldownTicks) {
        return Math.max(1, (int) Math.ceil(cooldownTicks / 20.0D));
    }
}
