package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.caecorthus.sparkwitch.roles.civilian.athlete.net.UseAthleteDashC2SPayload;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the Athlete's dash outside the witch skill dispatcher: the {@code sparkwitch:use_athlete_dash} payload
 * (common initializer, so both sides know the type; only the server receiver acts), the replay formatter, and the
 * cooldown lifecycle — role assignment clears it and starts the 15 s opening cooldown for a drawn Athlete, Wathe's
 * {@code ResetPlayer} clears it. Unlimited stamina is the registry's {@code maxSprintTime(-1)}, not code here.
 * 在魔女技能分发路径之外注册运动员冲刺：{@code sparkwitch:use_athlete_dash} 数据包（在通用初始化器中注册，两端都知道该
 * 类型；只有服务端接收器会执行）、回放格式化器以及冷却生命周期——身份分配时清空冷却，并为抽到的运动员开始 15 秒开局
 * 冷却；Wathe 的 {@code ResetPlayer} 会清空冷却。无限体力来自注册时的 {@code maxSprintTime(-1)}，不在此处实现。
 */
public final class AthleteFeatureService {
    private static boolean registered;

    private AthleteFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseAthleteDashC2SPayload.ID, UseAthleteDashC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseAthleteDashC2SPayload.ID,
                (payload, context) -> AthleteDashService.tryUse(context.player()));
        AthleteReplay.register();
        RoleAssigned.EVENT.register((player, role) -> {
            AthleteComponent state = AthleteComponent.KEY.get(player);
            state.clear();
            if (AthleteRules.isAthlete(role)) {
                state.setDashReadyTick(player.getWorld().getTime() + AthleteRules.COOLDOWN_TICKS);
            }
        });
        ResetPlayer.EVENT.register(player -> AthleteComponent.KEY.get(player).clear());
    }
}
