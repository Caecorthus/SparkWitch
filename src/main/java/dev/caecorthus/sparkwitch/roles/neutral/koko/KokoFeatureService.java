package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.roles.neutral.koko.net.UseKokoStreamC2SPayload;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers 泽渡可可 outside the witch skill dispatcher: the {@code sparkwitch:use_koko_stream} payload (common
 * initializer, so both sides know the type; only the server receiver acts — Fabric already runs it on the server
 * thread), the replay formatter, her shop ({@link KokoShopService}), her master key and its doors
 * ({@link KokoKeyService}), her win ({@link KokoWinService}), the corpse-stream interaction and world tick
 * ({@link KokoStreamService}) and the per-round state in {@link KokoComponent}: role assignment clears it and starts
 * the 60 s opening cooldown for a drawn Koko; Wathe's {@code ResetPlayer} (every round end) clears it.
 * <p>Mana is not granted here. She joins the shared mana system through {@code WitchManaRules} on the Murderous
 * Witch's regeneration (owner 2026-11-13), which {@code WitchManaService.assignForRole} applies in
 * {@code SparkWitchEvents}' shared {@code RoleAssigned} listener. She never gets a witch skill, so the witch skill
 * panel stays empty for her.
 * 在魔女技能分发路径之外注册泽渡可可：{@code sparkwitch:use_koko_stream} 数据包（在通用初始化器中注册，两端都知道该类型；
 * 只有服务端接收器会执行——Fabric 已在服务端线程上运行它）、回放格式化器、商店（{@link KokoShopService}）、万能钥匙及其可开
 * 的门（{@link KokoKeyService}）、胜利条件（{@link KokoWinService}）、尸体直播交互与世界刻（{@link KokoStreamService}），以及
 * {@link KokoComponent} 中的每局状态：身份分配时清空并为抽到的可可开始 60 秒开局冷却；Wathe 的 {@code ResetPlayer}（每局
 * 结束）会清空它。
 * <p>魔力不在此处发放。她通过 {@code WitchManaRules} 加入共享魔力系统，恢复方式与杀意魔女相同（所有者 2026-11-13），由
 * {@code SparkWitchEvents} 的共享 {@code RoleAssigned} 监听器中的 {@code WitchManaService.assignForRole} 执行。她从不获得
 * 魔女技能，因此魔女技能面板对她始终为空。
 */
public final class KokoFeatureService {
    private static boolean registered;

    private KokoFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseKokoStreamC2SPayload.ID, UseKokoStreamC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseKokoStreamC2SPayload.ID,
                (payload, context) -> KokoStreamService.tryGoLive(context.player()));
        KokoReplay.register();
        KokoShopService.register();
        KokoKeyService.register();
        KokoWinService.register();
        KokoStreamService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            KokoStreamService.clear(serverPlayer);
            if (KokoRules.isKoko(role)) {
                KokoComponent.KEY.get(serverPlayer).setReadyTick(
                        serverPlayer.getWorld().getTime() + KokoRules.INITIAL_COOLDOWN_TICKS);
            }
        });
        ResetPlayer.EVENT.register(KokoStreamService::clear);
    }
}
