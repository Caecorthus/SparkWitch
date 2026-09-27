package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Round-start loadout at ON_FINISH_INITIALIZE: revolver + Search Car, exact 60 s car cooldown, starting balance.
 * TODO(WP-07): implement. / 待 WP-07 实现。
 * ON_FINISH_INITIALIZE 时的开局装备：左轮 + 搜寻小车、精确 60 秒小车冷却、开局余额。
 */
public final class SeekerLoadoutService {
    private SeekerLoadoutService() {
    }

    /** Registers no round-start listener: the grant is driven by SeekerLifecycleService. / 不注册开局监听：发放由生命周期服务驱动。 */
    public static void register() {
        // TODO(WP-07) / 待 WP-07 实现
    }

    /**
     * Called exactly once per final Seeker by {@code SeekerLifecycleService} at ON_FINISH_INITIALIZE, after
     * {@code bindMatch}: revolver + car item, {@code component.apply(state.grant())} (INITIAL cooldown written by
     * {@code apply}) and the starting balance. Never called for a mid-round Seeker.
     * 由 {@code SeekerLifecycleService} 在 ON_FINISH_INITIALIZE、{@code bindMatch} 之后对每名最终搜寻者恰好调用一次：
     * 发放左轮与小车物品、{@code component.apply(state.grant())}（INITIAL 冷却由 {@code apply} 写入）以及开局余额。对局中途成为搜寻者时不调用。
     */
    public static void grant(ServerPlayerEntity player) {
    }
}
