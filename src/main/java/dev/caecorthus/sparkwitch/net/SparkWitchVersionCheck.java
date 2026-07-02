package dev.caecorthus.sparkwitch.net;

public final class SparkWitchVersionCheck {
    private static final String MOD_NAME = "SparkWitch";

    private SparkWitchVersionCheck() {
    }

    public static boolean isCompatible(String serverVersion, String clientVersion) {
        return !isBlank(serverVersion) && !isBlank(clientVersion) && serverVersion.equals(clientVersion);
    }

    public static String missingClientMessage(String serverVersion) {
        return MOD_NAME + " is required on the client with version " + serverVersion + ".";
    }

    public static String mismatchMessage(String serverVersion, String clientVersion) {
        return MOD_NAME + " version mismatch: server=" + serverVersion
                + ", client=" + clientVersion
                + ". Please install the same " + MOD_NAME + " version as the server.";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
