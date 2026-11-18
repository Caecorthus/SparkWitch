package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * Registration hub of the 「“手”枪」 (GUN work package), called once from {@code SparkWitchEvents}: the world-target
 * veto, the death cleanup and the replay line. The guard mixins in {@code mixin/sphinxhao/} need no registration.
 * <ul>
 *   <li>{@code UseEntityCallback} on both sides: item frames, armor stands and allays refuse the kit (FAIL), so the
 *   client stops before sending and the server refuses a forged packet.</li>
 *   <li>{@code KillPlayer.AFTER} for every victim: a death SparkTraits intercepted (Last Stand) keeps everything;
 *   otherwise every kit copy the victim holds is deleted (any holder). Wathe's death-drop loop already skipped them
 *   ({@code GameFunctionsSphinxHaoGunDropMixin}).</li>
 *   <li>A disconnect from a live round strips before the save, since Wathe's ESCAPED kill only runs afterwards.</li>
 * </ul>
 * 「“手”枪」的注册中心（GUN 工作包），由 {@code SparkWitchEvents} 调用一次：世界目标否决、死亡清理与回放文本。
 * {@code mixin/sphinxhao/} 中的防护 mixin 无需注册。
 * 双端 {@code UseEntityCallback}：物品展示框、盔甲架与悦灵拒收装备（FAIL），客户端在发包前拦截，服务端拒绝伪造的数据包。
 * 对每名受害者执行 {@code KillPlayer.AFTER}：被 SparkTraits 拦截的死亡（背水一战）保留一切；否则删除受害者身上所有装备副本
 * （任何持有者）。Wathe 死亡掉落流程已跳过它们（{@code GameFunctionsSphinxHaoGunDropMixin}）。
 * 在进行中的对局里断线会在存档前清除，因为 Wathe 的 ESCAPED 击杀在之后才执行。
 */
public final class SphinxHaoGunFeatureService {
    private static boolean registered;

    private SphinxHaoGunFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                SphinxHaoGunInventory.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim != null) {
                afterKill(victim);
            }
        });
        ServerDisconnectCleanup.register((player, server) -> {
            if (GameFunctions.isPlayerPlayingAndAlive(player)) {
                SphinxHaoGunInventory.strip(player);
            }
        });
        SphinxHaoGunReplay.register();
    }

    static void afterKill(ServerPlayerEntity victim) {
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        SphinxHaoGunInventory.strip(victim);
    }
}
