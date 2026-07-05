package dev.caecorthus.sparkwitch.net;

/**
 * Tracks whether the current client connection was confirmed by a SparkWitch login or play query.
 * 记录当前客户端连接是否已经通过 SparkWitch 登录阶段或 play 阶段确认。
 */
public final class SparkWitchServerConnection {
    private static volatile boolean confirmedServer;

    private SparkWitchServerConnection() {
    }

    public static void confirmServer() {
        confirmedServer = true;
    }

    /**
     * Confirms only version-compatible SparkWitch servers and clears stale state otherwise.
     * 只确认版本兼容的 SparkWitch 服务端；否则清理残留确认状态。
     */
    public static boolean confirmCompatible(String serverVersion, String clientVersion) {
        if (!SparkWitchVersionCheck.isCompatible(serverVersion, clientVersion)) {
            reset();
            return false;
        }
        confirmServer();
        return true;
    }

    public static void reset() {
        confirmedServer = false;
    }

    public static boolean isConfirmedServer() {
        return confirmedServer;
    }
}
