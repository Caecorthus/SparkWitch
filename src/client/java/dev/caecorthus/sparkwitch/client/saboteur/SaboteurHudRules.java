package dev.caecorthus.sparkwitch.client.saboteur;

/** Pure eligibility and display rules for Saboteur's role-owned HUD. / 破坏者角色自有 HUD 的纯资格与显示规则。 */
public final class SaboteurHudRules {
    private SaboteurHudRules() {
    }

    public static boolean shouldRender(
            boolean confirmedServer,
            boolean exactSaboteurRole,
            boolean promotedWraith
    ) {
        return confirmedServer && exactSaboteurRole && promotedWraith;
    }

    public static int cooldownSeconds(int cooldownTicks) {
        return (int) Math.ceil(Math.max(0, cooldownTicks) / 20.0D);
    }
}
