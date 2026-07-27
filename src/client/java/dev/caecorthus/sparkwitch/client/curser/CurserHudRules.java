package dev.caecorthus.sparkwitch.client.curser;

/** Pure visibility gate for Curser's role-owned HUD, independent of Wathe's alive state. / 诅咒师角色自有 HUD 的纯显示门禁，不依赖 wathe 存活状态。 */
public final class CurserHudRules {
    private CurserHudRules() {
    }

    public static boolean shouldRender(
            boolean confirmedServer,
            boolean exactCurserRole,
            boolean activeWraith,
            boolean promotedWraith
    ) {
        return confirmedServer && exactCurserRole && activeWraith && promotedWraith;
    }
}
