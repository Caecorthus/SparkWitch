package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

/**
 * Occupant interaction lock (research 03 §3.3, vectors 1 and 6). Fabric fires these five player callbacks on the
 * server BEFORE vanilla's spectator branches, so FAIL here closes spectator container viewing
 * ({@code ServerPlayerInteractionManager#interactBlock} opens a block's screen for a spectator), entity screens
 * ({@code PlayerEntity#interact}) and left-click camera possession ({@code ServerPlayerEntity#attack} calls
 * {@code setCameraEntity(target)} in SPECTATOR). They run in the {@code sparkwitch:rift_session_lock} phase, ahead of
 * the default phase; non-occupants always get PASS, so every other listener keeps its order. The same listeners run on
 * the owner's client (own synced state), where FAIL means no packet is sent. The shop is denied in an early
 * {@code ShopPurchase.BEFORE} phase so an earlier "allow" (e.g. the Grand Witch's) can never let an occupant buy; the
 * payload guard drops {@code wathe:storebuy} first anyway.
 * 门内玩家的交互锁（调研 03 §3.3，第 1 与第 6 条）。Fabric 在服务端会先于原版的旁观者分支触发这五个玩家回调，因此在此返回
 * FAIL 即可封住旁观者查看容器（旁观者右键方块时 {@code interactBlock} 会打开方块界面）、实体界面（{@code PlayerEntity#interact}）
 * 与左键附身视角（旁观模式下 {@code ServerPlayerEntity#attack} 会调用 {@code setCameraEntity(target)}）。它们运行在先于默认阶段的
 * {@code sparkwitch:rift_session_lock} 阶段；非门内玩家始终得到 PASS，其他监听器的顺序不变。拥有者客户端同样运行这些监听器
 * （读取自己同步来的状态），FAIL 时不会发包。商店在更早的 {@code ShopPurchase.BEFORE} 阶段拒绝，因此更早的「允许」（如大魔女的）
 * 永远无法让门内玩家购物；而数据包拦截本就会先丢弃 {@code wathe:storebuy}。
 */
final class RiftSessionGuards {
    static final Identifier LOCK_PHASE = SparkWitch.id("rift_session_lock");
    private static boolean registered;

    private RiftSessionGuards() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        order(UseItemCallback.EVENT);
        order(UseBlockCallback.EVENT);
        order(UseEntityCallback.EVENT);
        order(AttackEntityCallback.EVENT);
        order(AttackBlockCallback.EVENT);
        order(ShopPurchase.BEFORE);

        UseItemCallback.EVENT.register(LOCK_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return RiftSessionService.isInside(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.register(LOCK_PHASE, (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.register(LOCK_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.register(LOCK_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.register(LOCK_PHASE, (player, world, hand, pos, direction) -> verdict(player));

        ShopPurchase.BEFORE.register(LOCK_PHASE, (player, entry, index) -> RiftSessionService.isInside(player)
                ? ShopPurchase.PurchaseResult.deny(RiftSessionRules.SHOP_BLOCKED_KEY) : null);
    }

    static ActionResult verdict(PlayerEntity player) {
        return RiftSessionService.isInside(player) ? ActionResult.FAIL : ActionResult.PASS;
    }

    private static void order(Event<?> event) {
        event.addPhaseOrdering(LOCK_PHASE, Event.DEFAULT_PHASE);
    }
}
