package dev.caecorthus.sparkwitch.roles.civilian.bear.net;

/**
 * BEAR payload-type registration hub (the smoke payloads stay in {@code bear.smoke.net.SmokeNetworking}): the single
 * line in {@code SparkWitchPackets}, placed right before USEC's (USEC stays last). Work packages register their
 * payload TYPES here, in append order; their receivers belong to the owning services. Empty in WP0.
 * BEAR 数据包类型注册中心（烟雾数据包仍在 {@code bear.smoke.net.SmokeNetworking}）：{@code SparkWitchPackets} 中唯一的一行，
 * 紧挨 USEC 之前（USEC 保持最后）。各工作包按追加顺序在此注册数据包类型；接收器属于各自的服务。WP0 中为空。
 */
public final class BearNetworking {
    private static boolean registered;

    private BearNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
    }
}
