package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunFeatureService;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net.SphinxHaoPayloads;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net.UseSphinxHaoInfectC2SPayload;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net.UseSphinxHaoRoarC2SPayload;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers 狮身豪 Sphinx Hao's server side outside the witch skill dispatcher (泽渡可可 hub model), in a fixed order:
 * both C2S payload types through the idempotent {@link SphinxHaoPayloads#registerTypes()} (common initializer, so both
 * sides know them) and their server receivers (Fabric already runs them on the server thread), the kit and its doors
 * ({@link SphinxHaoKitService}), the hao shop and the hidden coin counter ({@link SphinxHaoShopService}), the two
 * skills with their world tick ({@link SphinxHaoSkillService}), the "手"枪's hub ({@link SphinxHaoGunFeatureService}), and the per-player state in
 * {@link SphinxHaoPlayerComponent}: role assignment deals 0 hao with the 60 s / 90 s opening cooldowns to a Sphinx Hao
 * and clears everyone else; Wathe's {@code ResetPlayer} clears it and drops the player from the world state; a real
 * death removes the victim's infection. Never a Witch-skill role: nothing here touches the witch skill panel.
 * 在魔女技能分发路径之外按固定顺序注册狮身豪的服务端（泽渡可可注册中心模式）：经幂等的 {@link SphinxHaoPayloads#registerTypes()}
 * 注册两个 C2S 数据包类型（通用初始化器，两端都知道）及其服务端接收器（Fabric 已在服务端线程上运行它们）、开局物品与开门
 * （{@link SphinxHaoKitService}）、豪度值商店与隐藏的金币计数（{@link SphinxHaoShopService}）、两个技能及其世界刻
 * （{@link SphinxHaoSkillService}），以及 {@link SphinxHaoPlayerComponent} 中的个人状态：身份分配时为狮身豪发放 0 豪度值与
 * 60 秒 / 90 秒开局冷却，并清空其他人的状态；Wathe 的 {@code ResetPlayer} 清空它并把该玩家移出世界状态；真实死亡移除被害者
 * 的感染。不是魔女技能职业：这里不触及魔女技能面板。
 */
public final class SphinxHaoFeatureService {
    private static boolean registered;

    private SphinxHaoFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SphinxHaoPayloads.registerTypes();
        ServerPlayNetworking.registerGlobalReceiver(UseSphinxHaoInfectC2SPayload.ID,
                (payload, context) -> SphinxHaoSkillService.infect(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UseSphinxHaoRoarC2SPayload.ID,
                (payload, context) -> SphinxHaoSkillService.roar(context.player()));
        SphinxHaoKitService.register();
        SphinxHaoShopService.register();
        SphinxHaoSkillService.register();
        // The "手"枪's world-target veto, death cleanup and replay line. / “手”枪的世界目标否决、死亡清理与回放文本。
        SphinxHaoGunFeatureService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            SphinxHaoPlayerComponent state = SphinxHaoPlayerComponent.KEY.get(serverPlayer);
            if (SphinxHaoRules.isSphinxHao(role)) {
                state.assignForRole(serverPlayer.getWorld().getTime());
            } else {
                state.clear();
            }
            // A new Sphinx Hao (or one that stops being one) must receive (or lose) the infected set in its sync view;
            // with no infection that view is unchanged, so the round-start deal sends nothing.
            // 新成为（或不再是）狮身豪的玩家须在其同步视图中收到（或失去）感染集合；没有感染时该视图不变，因此开局发牌不发送任何东西。
            SphinxHaoWorldComponent world = SphinxHaoWorldComponent.KEY.get(serverPlayer.getServerWorld());
            if (!world.infections().isEmpty()) {
                world.sync();
            }
        });
        ResetPlayer.EVENT.register(player -> {
            SphinxHaoSkillService.onReset(player);
            SphinxHaoPlayerComponent.KEY.get(player).clear();
        });
        KillPlayer.AFTER.register(SphinxHaoSkillService::afterKill);
    }
}
