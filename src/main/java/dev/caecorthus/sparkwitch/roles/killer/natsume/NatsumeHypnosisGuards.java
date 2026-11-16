package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.payloadpolicy.GeneratedPayloadPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Hypnosis lock (owner decision, final): every hypnotised player, whatever their faction, uses no item and no role
 * skill; walking and opening doors with an empty hand stay allowed. The lock is {@link NatsumeComponent#isLocked()},
 * read on both sides for the acting player. The five Fabric player callbacks sit in a dedicated phase ordered before
 * {@link Event#DEFAULT_PHASE} (Control Expert stun precedent) and run on both sides: on the client FAIL means nothing
 * is predicted and no packet is sent, on the server it is the authority. Unlocked players always get PASS, so the
 * relative order of every other listener is unchanged. Item and skill payloads are refused by
 * {@code mixin/natsume/NatsumeHypnosisPayloadGuardMixin} with {@link #blocksPayloadWhileLocked}.
 * 催眠锁（所有者最终决定）：任何被催眠的玩家，无论阵营，都不能使用道具与职业技能；行走与空手开门保持允许。锁状态为
 * {@link NatsumeComponent#isLocked()}，两端都对行动玩家读取。五个 Fabric 玩家回调位于排在 {@link Event#DEFAULT_PHASE} 之前的专用阶段
 * （控场专家眩晕先例），并在两端运行：客户端 FAIL 表示不做预测、不发送数据包，服务端则是权威判定。未被锁定的玩家始终得到 PASS，
 * 其他监听器的相对顺序不变。道具与技能数据包由 {@code mixin/natsume/NatsumeHypnosisPayloadGuardMixin} 按
 * {@link #blocksPayloadWhileLocked} 拒绝。
 */
public final class NatsumeHypnosisGuards {
    public static final Identifier HYPNOSIS_PHASE = SparkWitch.id("natsume_hypnosis");
    /** Lang key owned by the Natsume server module. / 由夏目安安服务端模块维护的语言键。 */
    public static final String BLOCKED_MESSAGE_KEY = "message.sparkwitch.natsume.blocked";
    /** At most one blocked notice per second per player, so a held button never spams. / 每名玩家每秒至多一条提示。 */
    public static final int NOTICE_INTERVAL_TICKS = 20;

    /**
     * Stable payload contract: payloads refused for a locked sender on top of the Control Expert stun deny-list
     * ({@link ControlExpertStunRules#BLOCKED_PAYLOADS}: Wathe knife, gun and shop, and every classified role skill).
     * The cross sword and Swordfish stabs are item releases whose handlers reject only stunned senders themselves, and
     * Natsume's own menu request and cast are her role skill. Plain ids, so no other module's class is loaded. Since
     * 2026-11-13 both stabs are on the stun list too (classified like {@code wathe:knifestab}); their entries here are
     * redundant and kept on purpose.
     * 稳定数据包契约：在控场专家眩晕拦截名单（{@link ControlExpertStunRules#BLOCKED_PAYLOADS}：Wathe 刀、枪与商店，以及所有已归类的职业
     * 技能）之外，对被锁定发送者额外拒绝的数据包。十字剑与剑鱼刺击是道具松手动作，其处理器只自行拒绝被眩晕者；夏目安安自己的菜单请求与
     * 施放是她的职业技能。以纯 id 保存，不加载其他模块的类。自 2026-11-13 起两种刺击也在眩晕名单中（与 {@code wathe:knifestab}
     * 同样归类）；此处的条目因此重复，有意保留。
     */
    // Generated HYPNOSIS_EXTRA column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 HYPNOSIS_EXTRA 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> EXTRA_BLOCKED_PAYLOADS = GeneratedPayloadPolicy.HYPNOSIS_EXTRA;

    /**
     * Refused without a notice: Raven Form's per-tick pose stream (Grand Witch Fear precedent).
     * 静默拒绝：化鸦每 tick 发送的位姿数据流（与大魔女恐惧相同）。
     */
    // Generated HYPNOSIS_SILENT column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 HYPNOSIS_SILENT 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> SILENT_PAYLOADS = GeneratedPayloadPolicy.HYPNOSIS_SILENT;

    private static boolean registered;

    /** A player action the lock judges. / 锁所判定的玩家行为。 */
    public enum Interaction {
        USE_ITEM,
        USE_BLOCK,
        USE_ENTITY,
        ATTACK_ENTITY,
        ATTACK_BLOCK
    }

    private NatsumeHypnosisGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(HYPNOSIS_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(HYPNOSIS_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return verdict(player, hand, Interaction.USE_ITEM) == ActionResult.FAIL
                    ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(HYPNOSIS_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(HYPNOSIS_PHASE,
                (player, world, hand, hit) -> verdict(player, hand, Interaction.USE_BLOCK));
        UseEntityCallback.EVENT.addPhaseOrdering(HYPNOSIS_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(HYPNOSIS_PHASE,
                (player, world, hand, entity, hit) -> verdict(player, hand, Interaction.USE_ENTITY));
        AttackEntityCallback.EVENT.addPhaseOrdering(HYPNOSIS_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(HYPNOSIS_PHASE,
                (player, world, hand, entity, hit) -> verdict(player, hand, Interaction.ATTACK_ENTITY));
        AttackBlockCallback.EVENT.addPhaseOrdering(HYPNOSIS_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(HYPNOSIS_PHASE,
                (player, world, hand, pos, direction) -> verdict(player, hand, Interaction.ATTACK_BLOCK));
    }

    /**
     * Side-neutral lock read for the acting player; a player without the component is never locked.
     * 对行动玩家的两端通用锁读取；没有该组件的玩家从不被锁定。
     */
    public static boolean isLocked(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        NatsumeComponent component = NatsumeComponent.KEY.getNullable(player);
        return component != null && component.isLocked();
    }

    /**
     * The hand rule: item use always fails, and so does an entity attack (a bare-hand strike can be a role skill, e.g.
     * the Apprentice's Mighty Force); block use, entity use and block attack fail only with something in the acting
     * hand, so an empty hand still opens doors.
     * 手部规则：物品使用总是失败，攻击实体同样失败（空手一击也可能是职业技能，例如预备魔女的巨力）；使用方块、使用实体与攻击方块
     * 只在行动手持有物品时失败，因此空手仍可开门。
     */
    public static boolean blocks(boolean locked, Interaction interaction, boolean handEmpty) {
        if (!locked) {
            return false;
        }
        return switch (interaction) {
            case USE_ITEM, ATTACK_ENTITY -> true;
            case USE_BLOCK, USE_ENTITY, ATTACK_BLOCK -> !handEmpty;
        };
    }

    /**
     * Netty-safe id predicate: only immutable id sets, never the player (read inside the scheduled Runnable).
     * 可在 Netty 线程调用的 id 判定：只读不可变 id 集合，从不读取玩家（玩家在被调度的 Runnable 内读取）。
     */
    public static boolean blocksPayloadWhileLocked(@Nullable Identifier payloadId) {
        return ControlExpertStunRules.isBlockedPayload(payloadId)
                || payloadId != null && EXTRA_BLOCKED_PAYLOADS.contains(payloadId);
    }

    /**
     * Server thread, inside the scheduled Runnable: true drops the payload (with a throttled notice unless silent).
     * 服务端主线程、被调度的 Runnable 内调用：返回 true 时丢弃该数据包（非静默时附带节流提示）。
     */
    public static boolean refusesPayload(@Nullable ServerPlayerEntity sender, @Nullable Identifier payloadId) {
        if (!isLocked(sender)) {
            return false;
        }
        if (payloadId == null || !SILENT_PAYLOADS.contains(payloadId)) {
            notifyBlocked(sender);
        }
        return true;
    }

    /** Throttle; a negative {@code last} means never and a clock that went back never blocks. / 节流判定。 */
    static boolean noticeDue(long now, long last) {
        return last < 0 || now < last || now - last >= NOTICE_INTERVAL_TICKS;
    }

    private static ActionResult verdict(PlayerEntity player, Hand hand, Interaction interaction) {
        if (!blocks(isLocked(player), interaction, player.getStackInHand(hand).isEmpty())) {
            return ActionResult.PASS;
        }
        notifyBlocked(player);
        return ActionResult.FAIL;
    }

    /**
     * Action-bar notice on the side that refused: the server sends it, and the owner's client shows it locally when its
     * own FAIL kept the packet from ever reaching the server. Presentation only.
     * 在拒绝的一端显示动作栏提示：服务端发送；当客户端自身的 FAIL 使数据包根本没有发出时，由主人客户端本地显示。仅为表现。
     */
    private static void notifyBlocked(PlayerEntity player) {
        // The throttle lives on this side's own component instance (no static per-player state).
        // 节流状态存放在本端自己的组件实例上（不使用静态的逐玩家状态）。
        NatsumeComponent component = NatsumeComponent.KEY.getNullable(player);
        if (component == null) {
            return;
        }
        long now = player.getWorld().getTime();
        if (!noticeDue(now, component.lastBlockedNoticeTick())) {
            return;
        }
        component.markBlockedNotice(now);
        player.sendMessage(Text.translatable(BLOCKED_MESSAGE_KEY).formatted(Formatting.DARK_PURPLE), true);
    }
}
