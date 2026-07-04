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

    public static void reset() {
        confirmedServer = false;
    }

    public static boolean isConfirmedServer() {
        return confirmedServer;
    }
}
