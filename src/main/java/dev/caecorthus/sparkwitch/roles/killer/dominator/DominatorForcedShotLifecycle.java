package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/**
 * Forced-shot listeners that are not mixins. Server and both-sides callbacks only; registered once from
 * {@code SparkWitchEvents}.
 * 非 mixin 的强制开枪监听器。仅服务端与双端回调；由 {@code SparkWitchEvents} 注册一次。
 */
public final class DominatorForcedShotLifecycle {
    /**
     * Runs before Wathe's default-phase disconnect listener, which kills a leaving participant ({@code escaped}) and
     * drops its revolvers: the marked gun must be gone and the stash back in its slot by then (SchrödingersCat
     * precedent). / 先于 Wathe 默认阶段的断线监听器执行（后者以 {@code escaped} 杀死离开的参与者并掉落其左轮）：在此之前
     * 标记左轮必须已收走、原物品必须已放回其槽位（薛定谔之猫先例）。
     */
    private static final Identifier DISCONNECT_PHASE = SparkWitch.id("dominator_forced_shot_release");
    private static boolean registered;

    private DominatorForcedShotLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // The marked gun cannot be handed to item frames, armor stands or allays. Both sides: the client stops before
        // sending, the server refuses a forged packet. Decorated pots: DecoratedPotBlockForcedShotGunMixin.
        // 标记左轮无法交给物品展示框、盔甲架或悦灵。双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。
        // 饰纹陶罐由 DecoratedPotBlockForcedShotGunMixin 处理。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                DominatorForcedShotGuns.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(DISCONNECT_PHASE, Event.DEFAULT_PHASE);
        // Fabric may fire DISCONNECT on the network thread; execute() runs at once on the server thread and otherwise
        // queues ahead of Wathe's own queued kill. / Fabric 可能在网络线程触发 DISCONNECT；execute() 在服务端线程上立即
        // 执行，否则排在 Wathe 自身排队的击杀之前。
        ServerPlayConnectionEvents.DISCONNECT.register(DISCONNECT_PHASE, (handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> DominatorForcedShotService.release(player));
        });
    }
}
