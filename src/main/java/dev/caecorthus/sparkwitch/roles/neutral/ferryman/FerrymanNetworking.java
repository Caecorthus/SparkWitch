package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanFerryC2SPayload;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanPardonC2SPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The Ferryman's two payloads: the shared-key pardon request and the secondary-key ferry request. Registered from
 * {@link FerrymanFeatureService} in the common initializer, so both sides know the types; only the server acts.
 * 摆渡人的两个数据包：共享技能键的宽恕请求与第二技能键的摆渡请求。经 {@link FerrymanFeatureService} 在通用初始化器中
 * 注册，两端都认识类型；只有服务端执行。
 */
public final class FerrymanNetworking {
    private static boolean registered;

    private FerrymanNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(FerrymanPardonC2SPayload.ID, FerrymanPardonC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FerrymanFerryC2SPayload.ID, FerrymanFerryC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FerrymanPardonC2SPayload.ID,
                (payload, context) -> FerrymanPardonService.onPress(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(FerrymanFerryC2SPayload.ID,
                (payload, context) -> FerrymanFerryService.ferry(context.player(), payload.bodyEntityId()));
    }
}
