package dev.caecorthus.sparkwitch.client.fisher;

/**
 * Angler client presentation: Glimmerfish outlines and HUD, held-item hiding, Swordfish crosshair. WP5 stub.
 * 钓鱼佬客户端表现：灵光鱼描边与 HUD、隐藏手持物、剑鱼准星。WP5 占位。
 */
public final class FisherClient {
    private static boolean registered;

    private FisherClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
    }
}
