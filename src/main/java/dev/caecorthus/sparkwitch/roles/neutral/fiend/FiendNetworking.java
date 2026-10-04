package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.net.UseFiendDashC2SPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the Fiend's Dash request ({@code sparkwitch:use_fiend_dash}) without entering the witch skill dispatcher.
 * Runs from {@link FiendFeatureService} in the common initializer, so the payload type exists on both sides (the
 * client must know it to send it); only the server-side receiver acts.
 * 注册魔人的疾驰请求（{@code sparkwitch:use_fiend_dash}），不进入魔女技能分发路径。经 {@link FiendFeatureService} 在通用
 * 初始化器中运行，因此数据包类型在两端都存在（客户端须知道该类型才能发送）；只有服务端接收器会执行。
 */
public final class FiendNetworking {
    private static boolean registered;

    private FiendNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseFiendDashC2SPayload.ID, UseFiendDashC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseFiendDashC2SPayload.ID,
                (payload, context) -> FiendDashService.tryUse(context.player()));
    }
}
