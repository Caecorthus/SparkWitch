package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCallbackPhases;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

/**
 * Server-authoritative session lock: the five Fabric player callbacks in phase {@code seeker_session_lock} return FAIL
 * while locked, PASS otherwise. The phase runs before {@code seeker_device} and the default phase, so a locked Seeker
 * cannot open doors, sit, use vents, hit or use anything through the body even though the client crosshair starts at
 * the car (the server measures reach from the body). The same listeners also run on the owner's client, where FAIL
 * means no packet is sent. Unlocked players always get PASS, so every other listener keeps its relative order.
 * 服务端权威的会话锁：在 {@code seeker_session_lock} 阶段的五个 Fabric 玩家回调，锁定时返回 FAIL，否则 PASS。
 * 该阶段先于 {@code seeker_device} 与默认阶段，因此即使客户端准星从小车发出（服务端按本体计算可达距离），
 * 被锁定的搜寻者也无法经由本体开门、就座、钻通风口、攻击或使用任何东西。拥有者客户端同样运行这些监听器，FAIL 时不会发包。
 * 未锁定的玩家始终得到 PASS，其他监听器的相对顺序保持不变。
 */
public final class SeekerInteractionGuards {
    private static boolean registered;

    private SeekerInteractionGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SeekerCallbackPhases.ensureOrdered();
        UseItemCallback.EVENT.register(SeekerCallbackPhases.SESSION_LOCK, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return SeekerRemoteSessionService.isLocked(player)
                    ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.register(SeekerCallbackPhases.SESSION_LOCK,
                (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.register(SeekerCallbackPhases.SESSION_LOCK,
                (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.register(SeekerCallbackPhases.SESSION_LOCK,
                (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.register(SeekerCallbackPhases.SESSION_LOCK,
                (player, world, hand, pos, direction) -> verdict(player));
    }

    static ActionResult verdict(PlayerEntity player) {
        return SeekerRemoteSessionService.isLocked(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
