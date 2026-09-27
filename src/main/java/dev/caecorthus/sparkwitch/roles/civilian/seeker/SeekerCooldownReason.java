package dev.caecorthus.sparkwitch.roles.civilian.seeker;

/**
 * Stable wire contract: why the {@code seeker_car} item cooldown was written, synced as its byte id for the HUD label
 * only. The item cooldown itself is authoritative; a reason is shown only while our write is the active one.
 * 稳定线格式契约：{@code seeker_car} 物品冷却的写入原因，以字节 id 同步，仅用作 HUD 标签。
 * 物品冷却本身才是权威；只有当我们写入的冷却正在生效时才显示原因。
 */
public enum SeekerCooldownReason {
    NONE(0, 0),
    INITIAL(1, SeekerRules.INITIAL_COOLDOWN_TICKS),
    RECALL(2, SeekerRules.RECALL_COOLDOWN_TICKS),
    BROKEN(3, SeekerRules.BROKEN_COOLDOWN_TICKS),
    DEPLETED(4, SeekerRules.DEPLETED_COOLDOWN_TICKS),
    RETURNED(5, SeekerRules.RETURNED_COOLDOWN_TICKS);

    private final int id;
    private final int ticks;

    SeekerCooldownReason(int id, int ticks) {
        this.id = id;
        this.ticks = ticks;
    }

    public int id() {
        return id;
    }

    public int ticks() {
        return ticks;
    }

    /** HUD/console label key, e.g. {@code hud.sparkwitch.seeker.reason.broken}. / HUD 与控制台标签键。 */
    public String translationKey() {
        return "hud.sparkwitch.seeker.reason." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SeekerCooldownReason fromId(int id) {
        for (SeekerCooldownReason reason : values()) {
            if (reason.id == id) {
                return reason;
            }
        }
        return NONE;
    }
}
