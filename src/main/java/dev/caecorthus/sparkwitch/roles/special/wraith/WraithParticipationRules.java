package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Pure common policy for restrictions shared by base and promoted active Wraiths. */
public final class WraithParticipationRules {
    private WraithParticipationRules() {
    }

    public static boolean mayUseTextChat(
            boolean activeWraith,
            boolean guardianAngel,
            boolean creative
    ) {
        return activeWraith && WraithCommunicationPolicy.mayCommunicate(true, guardianAngel, creative);
    }

    public static boolean mayJump(boolean activeWraith, boolean mapAllowsJump) {
        // 冤魂是死亡后的特殊参与形态，跳跃需要完全绕过 Wathe 地图禁跳配置；
        // 非冤魂玩家不由 SparkWitch 的冤魂规则限制，仍交给 Wathe 自己处理。
        return true;
    }

    public static boolean mayGenerateGroundParticles(boolean activeWraith) {
        return !activeWraith;
    }

    public static boolean mayPickUpGroundItems(boolean activeWraith) {
        return !activeWraith;
    }
}
