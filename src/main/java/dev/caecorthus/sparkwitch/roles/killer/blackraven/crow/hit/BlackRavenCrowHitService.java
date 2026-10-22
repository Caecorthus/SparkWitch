package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

/**
 * Registration of the crow hit seams that need event wiring: the lag-compensation history, the per-tick expiry of
 * owner substitutions and the bat left-click handler. Every other seam is a direct call from its weapon or a
 * {@code mixin/blackraven/BlackRavenCrow*} mixin. Common initializer; only the server acts.
 * 需要事件接线的乌鸦命中接缝的注册：延迟补偿历史、主人替身的逐刻失效与球棒左键处理器。其余接缝都由各武器或
 * {@code mixin/blackraven/BlackRavenCrow*} mixin 直接调用。在通用初始化器中运行；只有服务端执行。
 */
public final class BlackRavenCrowHitService {
    private BlackRavenCrowHitService() {
    }

    public static void register() {
        BlackRavenCrowHitboxHistory.register();
        BlackRavenCrowTargets.register();
        BlackRavenCrowAttackHandlers.register();
    }
}
