package dev.caecorthus.sparkwitch.roles.civilian.seeker;

/**
 * Stable wire contract: the owner's car state, synced as its byte id. "Broken" is READY plus an item cooldown.
 * 稳定线格式契约：拥有者的小车状态，以字节 id 同步。“已损坏”表示为 READY 加物品冷却。
 */
public enum SeekerCarState {
    NONE(0),
    READY(1),
    DEPLOYED(2),
    SWALLOWED(3);

    private final int id;

    SeekerCarState(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Unknown ids decode to NONE (fail closed). / 未知 id 解码为 NONE（失败即关闭）。 */
    public static SeekerCarState fromId(int id) {
        for (SeekerCarState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        return NONE;
    }
}
