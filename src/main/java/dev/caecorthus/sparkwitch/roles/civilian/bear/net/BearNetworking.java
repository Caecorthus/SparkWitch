package dev.caecorthus.sparkwitch.roles.civilian.bear.net;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RysTRicochetS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * BEAR payload-type registration hub (the smoke payloads stay in {@code bear.smoke.net.SmokeNetworking}): the single
 * line in {@code SparkWitchPackets}, placed right before USEC's (USEC stays last). Work packages register their
 * payload TYPES here, in append order; their receivers belong to the owning services. WP1: the M32A1 fire intent
 * (receiver in {@code launcher.M32FireService}).
 * BEAR 数据包类型注册中心（烟雾数据包仍在 {@code bear.smoke.net.SmokeNetworking}）：{@code SparkWitchPackets} 中唯一的一行，
 * 紧挨 USEC 之前（USEC 保持最后）。各工作包按追加顺序在此注册数据包类型；接收器属于各自的服务。WP1：M32A1 开火意图
 * （接收器在 {@code launcher.M32FireService}）。
 */
public final class BearNetworking {
    /** WP1 C2S payload ids, in registration order. / WP1 的 C2S 数据包 id，按注册顺序。 */
    public static final List<Identifier> LAUNCHER_C2S_IDS = List.of(FireM32A1C2SPayload.PAYLOAD_ID);

    private static boolean registered;

    private BearNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // WP3: the wearer's ricochet daze (S2C, presentation only). / WP3：佩戴者的跳弹眩晕（S2C，纯表现）。
        PayloadTypeRegistry.playS2C().register(RysTRicochetS2CPayload.ID, RysTRicochetS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FireM32A1C2SPayload.ID, FireM32A1C2SPayload.CODEC);
    }
}
