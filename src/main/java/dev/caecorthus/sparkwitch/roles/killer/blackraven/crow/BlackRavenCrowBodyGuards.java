package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowNetworking;
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
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative lock of the frozen body during Raven Form (Seeker precedent {@code SeekerInteractionGuards}):
 * the five Fabric player callbacks return FAIL for a player in crow form, in a dedicated phase ordered before the
 * default phase, so the body uses no item, block or entity and attacks nothing. The same listeners also run on the
 * owner's client, where FAIL means no packet is sent. Players not in crow form always get PASS, so the relative order
 * of every other listener is unchanged. Drops, hand swaps and inventory clicks are refused by
 * {@code mixin/blackraven/BlackRavenCrowBodyInventoryGuardMixin}, item and skill payloads by
 * {@code mixin/blackraven/BlackRavenCrowBodyPayloadGuardMixin} with {@link #blocksPayloadInCrowForm}.
 * 化鸦期间冻结本体的服务端权威锁（搜寻者先例 {@code SeekerInteractionGuards}）：在排在默认阶段之前的专用阶段中，五个 Fabric 玩家回调对化鸦中的
 * 玩家返回 FAIL，因此本体不使用任何物品、方块或实体，也不攻击任何东西。同样的监听器也在主人客户端运行，FAIL 时不发送数据包。未化鸦的玩家
 * 总是得到 PASS，其他监听器的相对顺序不变。丢弃、换手与物品栏点击由 {@code BlackRavenCrowBodyInventoryGuardMixin} 拒绝，物品与技能数据包由
 * {@code BlackRavenCrowBodyPayloadGuardMixin} 按 {@link #blocksPayloadInCrowForm} 拒绝。
 */
public final class BlackRavenCrowBodyGuards {
    public static final Identifier BODY_PHASE = SparkWitch.id("black_raven_crow_body");
    /**
     * Perception (the Raven's primary skill, dispatched through {@code sparkwitch:use_skill}) stays usable in crow form
     * (plan §2.10). / 感知（黑羽鸦主技能，经 {@code sparkwitch:use_skill} 分发）在化鸦期间仍可使用（计划 §2.10）。
     */
    public static final Identifier PERCEPTION_PAYLOAD_ID = SparkWitch.id("use_skill");

    private static boolean registered;

    private BlackRavenCrowBodyGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(BODY_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(BODY_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return locked(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(BODY_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(BODY_PHASE, (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.addPhaseOrdering(BODY_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(BODY_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.addPhaseOrdering(BODY_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(BODY_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(BODY_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(BODY_PHASE, (player, world, hand, pos, direction) -> verdict(player));
    }

    /**
     * Stable payload rule for the frozen body: everything on the Control Expert stun deny-list (Wathe's knife, gun and
     * shop, and every known role-skill payload) is dropped, except Raven Form's own four C2S ids and Perception.
     * 冻结本体的稳定数据包规则：丢弃控场专家眩晕拦截名单上的一切（Wathe 刀、枪与商店，以及所有已知职业技能包），化鸦自身的四个 C2S id
     * 与感知除外。
     */
    public static boolean blocksPayloadInCrowForm(@Nullable Identifier payloadId) {
        return payloadId != null
                && ControlExpertStunRules.isBlockedPayload(payloadId)
                && !BlackRavenCrowNetworking.C2S_IDS.contains(payloadId)
                && !PERCEPTION_PAYLOAD_ID.equals(payloadId);
    }

    private static boolean locked(PlayerEntity player) {
        return BlackRavenCrowForm.isInCrowForm(player);
    }

    private static ActionResult verdict(PlayerEntity player) {
        return locked(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
