package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

/**
 * Pure classification of who may use a Rift Gate and on which terms (D5, D5b, D14, C1). Built from the RAW role and
 * the SparkFactionAPI effective faction by {@link RiftGateUsers#classify}; never from the Black Raven acting role.
 * 纯分类：谁可以使用裂隙门、按什么条件（D5、D5b、D14、C1）。由 {@link RiftGateUsers#classify} 依据原始职业与
 * SparkFactionAPI 有效阵营构建，从不使用黑羽鸦伪装职业。
 */
public enum RiftGateUser {
    RIFTWALKER(RiftwalkerRules.STAY_RIFTWALKER, RiftwalkerRules.REENTRY_COOLDOWN_RIFTWALKER, 0),
    /** Effective faction {@code sparkwitch:witch}: Grand Witch, Accomplice, every special accomplice. / 魔女阵营。 */
    WITCH_FACTION(RiftwalkerRules.STAY_FACTION, RiftwalkerRules.REENTRY_COOLDOWN_OTHERS, 0),
    MURDEROUS_WITCH(RiftwalkerRules.STAY_MURDEROUS_WITCH, RiftwalkerRules.REENTRY_COOLDOWN_OTHERS,
            RiftwalkerRules.OTHER_WITCH_ENTRY_FEE),
    APPRENTICE_WITCH(RiftwalkerRules.STAY_APPRENTICE_WITCH, RiftwalkerRules.REENTRY_COOLDOWN_OTHERS,
            RiftwalkerRules.OTHER_WITCH_ENTRY_FEE),
    /** May not use gates at all. / 不能使用裂隙门。 */
    NONE(0, 0, 0);

    private final int stayTicks;
    private final int reentryCooldownTicks;
    private final int entryFee;

    RiftGateUser(int stayTicks, int reentryCooldownTicks, int entryFee) {
        this.stayTicks = stayTicks;
        this.reentryCooldownTicks = reentryCooldownTicks;
        this.entryFee = entryFee;
    }

    /** Maximum stay inside, in ticks (0 for {@link #NONE}). / 门内最长停留 tick（NONE 为 0）。 */
    public int stayTicks() {
        return stayTicks;
    }

    /** Re-entry cooldown after any exit except round end, in ticks. / 除对局结束外任意出门后的再次进门冷却 tick。 */
    public int reentryCooldownTicks() {
        return reentryCooldownTicks;
    }

    /** Mana charged per entry (C1: hops inside are free). / 每次进门扣除的魔力（C1：门内跳转免费）。 */
    public int entryFee() {
        return entryFee;
    }

    public boolean paysMana() {
        return entryFee > 0;
    }

    public boolean mayUse() {
        return this != NONE;
    }

    /**
     * Pure classifier; the Riftwalker check wins over the faction check (it is also witch faction), and the two
     * "other witches" are never witch faction in practice. Order: Riftwalker, witch faction, Murderous, Apprentice.
     * 纯分类器；隙行者判定优先于阵营判定（隙行者同属魔女阵营）；两类「其他魔女」实际上从不属于魔女阵营。
     * 判定顺序：隙行者、魔女阵营、杀意魔女、预备魔女。
     */
    public static RiftGateUser classify(boolean isRiftwalker, boolean isWitchFaction, boolean isMurderousWitch,
                                        boolean isApprenticeWitch) {
        if (isRiftwalker) {
            return RIFTWALKER;
        }
        if (isWitchFaction) {
            return WITCH_FACTION;
        }
        if (isMurderousWitch) {
            return MURDEROUS_WITCH;
        }
        if (isApprenticeWitch) {
            return APPRENTICE_WITCH;
        }
        return NONE;
    }
}
