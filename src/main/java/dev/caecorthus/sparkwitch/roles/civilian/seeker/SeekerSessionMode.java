package dev.caecorthus.sparkwitch.roles.civilian.seeker;

/**
 * Stable wire contract: the remote-view session mode, synced and sent in {@code seeker_remote_open} as its byte id.
 * 稳定线格式契约：遥控视角会话模式，以字节 id 同步并在 {@code seeker_remote_open} 中发送。
 */
public enum SeekerSessionMode {
    NONE(0),
    CAR(1),
    CAMERA(2);

    private final int id;

    SeekerSessionMode(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Unknown ids decode to NONE (fail closed). / 未知 id 解码为 NONE（失败即关闭）。 */
    public static SeekerSessionMode fromId(int id) {
        for (SeekerSessionMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return NONE;
    }
}
