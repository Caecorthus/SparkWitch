package dev.caecorthus.sparkwitch.roles.civilian.seeker;

/**
 * The two Seeker devices; the lowercase name is written to replay NBT.
 * 搜寻者的两种设备；小写名称写入回放 NBT。
 */
public enum SeekerDeviceKind {
    CAR("car"),
    CAMERA("camera");

    private final String id;

    SeekerDeviceKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
