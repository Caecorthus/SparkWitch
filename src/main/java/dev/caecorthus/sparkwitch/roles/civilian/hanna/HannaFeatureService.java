package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.net.UseHannaFloatC2SPayload;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers 远野汉娜 outside the witch skill dispatcher: the {@code sparkwitch:use_hanna_float} payload (common
 * initializer, so both sides know the type; only the server receiver acts — Fabric already runs it on the server
 * thread), the replay formatter, coins and shop ({@link HannaEconomyService}), the float's interruption hooks and
 * world tick ({@link HannaFloatService}) and the per-round state in {@link HannaComponent}: role assignment clears it
 * and starts the 40 s opening cooldown for a drawn Hanna; Wathe's {@code ResetPlayer} (every round end) clears it.
 * <p>Mana is not granted here. She joins the shared mana system through {@code WitchManaRules} (40 at deal time, +20
 * per task, no regeneration), which {@code WitchManaService.assignForRole} applies in {@code SparkWitchEvents}'
 * shared {@code RoleAssigned} listener. She never gets a witch skill, so the witch skill panel stays empty for her.
 * 在魔女技能分发路径之外注册远野汉娜：{@code sparkwitch:use_hanna_float} 数据包（在通用初始化器中注册，两端都知道该类型；
 * 只有服务端接收器会执行——Fabric 已在服务端线程上运行它）、回放格式化器、金币与商店（{@link HannaEconomyService}）、浮游的
 * 打断钩子与世界刻（{@link HannaFloatService}），以及 {@link HannaComponent} 中的每局状态：身份分配时清空并为抽到的汉娜开始
 * 40 秒开局冷却；Wathe 的 {@code ResetPlayer}（每局结束）会清空它。
 * <p>魔力不在此处发放。她通过 {@code WitchManaRules} 加入共享魔力系统（开局 40、每个任务 +20、无自然回复），由
 * {@code SparkWitchEvents} 的共享 {@code RoleAssigned} 监听器中的 {@code WitchManaService.assignForRole} 执行。她从不获得
 * 魔女技能，因此魔女技能面板对她始终为空。
 */
public final class HannaFeatureService {
    private static boolean registered;

    private HannaFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseHannaFloatC2SPayload.ID, UseHannaFloatC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseHannaFloatC2SPayload.ID,
                (payload, context) -> HannaFloatService.tryUse(context.player()));
        HannaReplay.register();
        HannaEconomyService.register();
        HannaFloatService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            HannaFloatService.clear(serverPlayer);
            if (HannaRules.isHanna(role)) {
                HannaComponent.KEY.get(serverPlayer).setFloatReadyTick(
                        serverPlayer.getWorld().getTime() + HannaRules.INITIAL_COOLDOWN_TICKS);
            }
        });
        ResetPlayer.EVENT.register(HannaFloatService::clear);
    }
}
