package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Stable packet contract: registers every USEC payload TYPE (the only place that does), from
 * {@code SparkWitchPackets.register()}, which runs before {@code SparkWitchEvents}. Receivers are not registered here:
 * each owning service registers its own in its {@code register()} (fire: {@code UsecRifleFireService}, attachment:
 * {@code UsecAttachmentService}, scope: {@code UsecScopeService}; the impacts receiver is client-side). Payload ids
 * are classified for the stun, Seeker session and Rift session deny-lists by later work packages.
 * 稳定数据包契约：注册所有 USEC 数据包类型（唯一注册处），由先于 {@code SparkWitchEvents} 运行的
 * {@code SparkWitchPackets.register()} 调用。这里不注册接收器：各自的服务在其 {@code register()} 中注册
 * （开火：{@code UsecRifleFireService}，配件：{@code UsecAttachmentService}，开镜：{@code UsecScopeService}；
 * 裂痕接收器在客户端）。数据包 id 由后续工作包归入眩晕、搜寻者会话与隙行者会话拒绝列表。
 */
public final class UsecNetworking {
    /** Every USEC C2S payload id, in registration order. / 所有 USEC C2S 数据包 id，按注册顺序。 */
    public static final List<Identifier> C2S_IDS = List.of(
            FireUsecRifleC2SPacket.PAYLOAD_ID,
            UsecAttachmentC2SPacket.PAYLOAD_ID,
            UsecScopeC2SPacket.PAYLOAD_ID);
    /** Every USEC S2C payload id. / 所有 USEC S2C 数据包 id。 */
    public static final List<Identifier> S2C_IDS = List.of(UsecBulletImpactsS2CPacket.PAYLOAD_ID);

    private static boolean registered;

    private UsecNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(FireUsecRifleC2SPacket.ID, FireUsecRifleC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UsecAttachmentC2SPacket.ID, UsecAttachmentC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UsecScopeC2SPacket.ID, UsecScopeC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(UsecBulletImpactsS2CPacket.ID, UsecBulletImpactsS2CPacket.CODEC);
    }
}
