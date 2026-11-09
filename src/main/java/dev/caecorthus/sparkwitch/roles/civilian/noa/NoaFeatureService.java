package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.roles.civilian.noa.net.UseNoaPaintC2SPayload;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers 城崎诺亚 outside the witch skill dispatcher: the {@code sparkwitch:use_noa_paint} payload (common
 * initializer, so both sides know the type; only the server receiver acts — Fabric already runs it on the server
 * thread), the replay formatter, coins and shop ({@link NoaEconomyService}), the kill rules ({@link NoaKillWatch}) and
 * the per-round state in {@link NoaComponent}: role assignment clears it and starts the 50 s opening cooldown for a
 * drawn Noa; Wathe's {@code ResetPlayer} (every round end) clears it. Her sanity lock is the
 * {@code mixin/noa} PlayerMoodComponent mixin, not code here.
 * 在魔女技能分发路径之外注册城崎诺亚：{@code sparkwitch:use_noa_paint} 数据包（在通用初始化器中注册，两端都知道该类型；
 * 只有服务端接收器会执行——Fabric 已在服务端线程上运行它）、回放格式化器、金币与商店（{@link NoaEconomyService}）、击杀
 * 规则（{@link NoaKillWatch}），以及 {@link NoaComponent} 中的每局状态：身份分配时清空并为抽到的诺亚开始 50 秒开局冷却；
 * Wathe 的 {@code ResetPlayer}（每局结束）会清空它。理智锁定由 {@code mixin/noa} 的 PlayerMoodComponent mixin 实现，
 * 不在此处。
 */
public final class NoaFeatureService {
    private static boolean registered;

    private NoaFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseNoaPaintC2SPayload.ID, UseNoaPaintC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseNoaPaintC2SPayload.ID,
                (payload, context) -> NoaPaintService.tryUse(context.player()));
        NoaReplay.register();
        NoaEconomyService.register();
        KillPlayer.AFTER.register(NoaKillWatch::afterKill);
        RoleAssigned.EVENT.register((player, role) -> {
            NoaComponent state = NoaComponent.KEY.get(player);
            state.clear();
            if (NoaRules.isNoa(role)) {
                state.setPaintReadyTick(player.getWorld().getTime() + NoaRules.INITIAL_COOLDOWN_TICKS);
            }
        });
        ResetPlayer.EVENT.register(player -> NoaComponent.KEY.get(player).clear());
    }
}
