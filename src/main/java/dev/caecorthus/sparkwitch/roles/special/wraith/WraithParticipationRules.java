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

    public static boolean mayDropConsumable(boolean restrictedWraith, boolean consumable) {
        return !restrictedWraith || !consumable;
    }

    /**
     * A promoted Wraith's right-click on a door, train window, door button or vent hatch. Only the Wathe lockpick
     * keeps its own uses there: a sneak jam, or unlocking a closed locked door (owner decision 2026-10-04).
     * 晋升冤魂右键门、车窗、门边按钮或通风口盖时，只有开锁器保留自身用途：潜行卡门，或撬开关着的上锁门（所有者 2026-10-04 决定）。
     */
    public static boolean mayUsePassageBlock(
            boolean lockpickOnWatheDoor,
            boolean sneaking,
            boolean doorOpen,
            boolean doorLocked
    ) {
        return lockpickOnWatheDoor && (sneaking || (!doorOpen && doorLocked));
    }

    /**
     * Sneaking with an item in either hand skips every block's own use on both sides, so only the held item's
     * {@code useOnBlock} runs. On a passage block only the Wathe crowbar's blast toggles anything there.
     * 手持物品潜行时双端都会跳过方块自身的使用，只运行手中物品的 useOnBlock；在门窗类方块上只有 Wathe 撬棍的爆破会改变状态。
     */
    public static boolean mayUseItemOnPassage(boolean blockUseSkipped, boolean crowbar) {
        return blockUseSkipped && !crowbar;
    }

    /**
     * Wind charges thrown by an active Wraith trigger no blocks, through neither their explosion nor their impact
     * (owner decision 2026-10-04). Knockback is unchanged.
     * 激活冤魂扔出的风弹无论爆炸还是命中都不触发任何方块（所有者 2026-10-04 决定），击退不变。
     */
    public static boolean mayTriggerBlocks(boolean causedByActiveWraith) {
        return !causedByActiveWraith;
    }
}
