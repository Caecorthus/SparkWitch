package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.SparkWitch;
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
 * Server-authoritative interaction lock (also runs on the client, where FAIL means no packet is sent). The listeners
 * sit in a dedicated phase ordered before {@link Event#DEFAULT_PHASE}, so they decide before every existing listener,
 * including callbacks that kill inside the callback (sword, poker, Mighty Force). Unstunned players always get PASS,
 * so the relative order of all other listeners is unchanged.
 * 服务端权威的交互锁（客户端同样运行，FAIL 时不会发送数据包）。监听器位于排在 {@link Event#DEFAULT_PHASE}
 * 之前的专用阶段，因此先于所有现有监听器做出决定，包括在回调内直接击杀的监听器（仪式剑、火钳、蛮力）。
 * 未被眩晕的玩家始终得到 PASS，其他监听器之间的相对顺序保持不变。
 */
public final class ControlExpertStunGuards {
    public static final Identifier STUN_PHASE = SparkWitch.id("control_expert_stun");
    private static boolean registered;

    private ControlExpertStunGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(STUN_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return ControlExpertStun.isStunned(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(STUN_PHASE, (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(STUN_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(STUN_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(STUN_PHASE, (player, world, hand, pos, direction) -> verdict(player));
    }

    private static ActionResult verdict(PlayerEntity player) {
        return ControlExpertStun.isStunned(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
