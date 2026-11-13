package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for the Knockout Drug's attack / use lock. Until now the lock existed only on the victim's client
 * ({@code KidnapperControlledKeyMixin} answers false for the attack and use keys), so a modified client could keep
 * using items, attacking and shooting for the whole control. The lock is exactly the client's own predicate, read for
 * the acting player: {@link KidnapperControlComponent#isControlled()} and
 * {@link GameFunctions#isPlayerAliveAndSurvival}. While it holds, the five Fabric player callbacks fail (no empty-hand
 * exception: the client lock leaves no use or attack key at all), and
 * {@code mixin/kidnapper/KidnapperControlPayloadGuardMixin} drops the direct weapon payloads through
 * {@link #refusesPayload}. The listeners sit in a dedicated phase ordered before {@link Event#DEFAULT_PHASE} (Control
 * Expert stun and hypnosis precedent) and run on both sides; a player who is not controlled always gets PASS, so no
 * other role, faction or trait reaches a different result and the relative order of every other listener is
 * unchanged. Silent: the victim's screen is black and no new message is added.
 * Deliberately not covered, pending an owner decision (see the Kidnapper notes in CONTEXT.md): role-skill and shop
 * payloads, which the client lock does not stop either, and hotbar selection.
 * 迷药“禁用攻击/使用”锁的服务端权威。此前该锁只存在于受害者客户端（{@code KidnapperControlledKeyMixin} 让攻击键与使用键
 * 始终返回 false），修改过的客户端在整个控制期间仍可使用道具、攻击与开枪。锁的判定与客户端完全一致，并对行动玩家读取：
 * {@link KidnapperControlComponent#isControlled()} 且 {@link GameFunctions#isPlayerAliveAndSurvival}。锁生效时五个
 * Fabric 玩家回调一律失败（没有空手例外：客户端锁根本不保留使用键与攻击键），直接的武器数据包则由
 * {@code mixin/kidnapper/KidnapperControlPayloadGuardMixin} 经 {@link #refusesPayload} 丢弃。监听器位于排在
 * {@link Event#DEFAULT_PHASE} 之前的专用阶段（控场专家眩晕与催眠先例），两端都运行；未被控制的玩家始终得到 PASS，因此其他
 * 职业、阵营与天赋的结果不变，其他监听器的相对顺序也不变。静默拒绝：受害者本就黑屏，不新增任何提示。
 * 有意不覆盖，留待所有者决定（见 CONTEXT.md 的绑架者条目）：职业技能与商店数据包（客户端锁同样不拦截）以及快捷栏选择。
 */
public final class KidnapperControlGuards {
    public static final Identifier CONTROL_PHASE = SparkWitch.id("kidnapper_control");
    private static boolean registered;

    private KidnapperControlGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(CONTROL_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(CONTROL_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return isLocked(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(CONTROL_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(CONTROL_PHASE, (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.addPhaseOrdering(CONTROL_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(CONTROL_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.addPhaseOrdering(CONTROL_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(CONTROL_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(CONTROL_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(CONTROL_PHASE, (player, world, hand, pos, direction) -> verdict(player));
    }

    /**
     * Side-neutral lock read for the acting player; a player without the component (or not controlled) is never
     * locked. The O(1) control flag goes first, as in the client key mixin, so every other player pays one component
     * lookup per interaction.
     * 对行动玩家的两端通用锁读取；没有该组件（或未被控制）的玩家从不被锁定。与客户端按键 mixin 一样先做 O(1) 的受控判断，
     * 因此其他玩家每次交互只多一次组件查找。
     */
    public static boolean isLocked(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        KidnapperControlComponent control = KidnapperControlComponent.KEY.getNullable(player);
        return control != null && control.isControlled() && GameFunctions.isPlayerAliveAndSurvival(player);
    }

    /**
     * Server thread, inside the scheduled Runnable: true drops the payload, silently.
     * 服务端主线程、被调度的 Runnable 内调用：返回 true 时静默丢弃该数据包。
     */
    public static boolean refusesPayload(@Nullable ServerPlayerEntity sender) {
        return isLocked(sender);
    }

    private static ActionResult verdict(PlayerEntity player) {
        return isLocked(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
