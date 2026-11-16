package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.payloadpolicy.GeneratedPayloadPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Stable contract (owner decision 2026-10-30): while a Knight duel runs, BOTH duelists lose every primary role skill,
 * meaning every payload sent from the shared NoellesRoles ability key (G), and every weapon fired by C2S payload
 * rather than by an item use (spec: all weapons but the knife and the derringer are sealed). The server enforces both
 * lists in {@code mixin/knight/KnightDuelSkillPayloadGuardMixin}; the client's shared dispatch only skips a duelist's
 * press. {@link #BLOCKED_WHILE_DUELING} (G skills) and {@link #SEALED_WEAPON_PAYLOADS} (payload weapons) are this
 * lock's own lists (decoupled from the stun, Seeker, Rift and Fear lists), kept as plain ids so no optional mod
 * class is loaded. Deliberately left out of both: the Wathe knife and gun and the Knight's own cross-sword stab (see
 * {@link #SEALED_WEAPON_PAYLOADS}), skill-key-2 (N) payloads, inventory-panel skills (Morphling, Swapper and Voodoo
 * morphs, SparkStrength player widgets), item-opened screens, the shop, and every exit or close payload. A new G-key
 * or weapon payload must be classified deliberately, as a complete row of the canonical table.
 * 稳定契约（所有者 2026-10-30 决定）：骑士决斗进行时，双方决斗者失去全部主职业技能，即共享的 NoellesRoles 技能键（G）
 * 发送的所有数据包，以及所有经 C2S 数据包而非物品使用发射的武器（规格：除刀与德林加外的武器均被封印）。两份名单都由服务端的
 * {@code mixin/knight/KnightDuelSkillPayloadGuardMixin} 强制执行；客户端共享分发只会跳过决斗者的按键。
 * {@link #BLOCKED_WHILE_DUELING}（G 键技能）与 {@link #SEALED_WEAPON_PAYLOADS}（数据包武器）是该锁自有的名单
 * （与眩晕、搜寻者、裂隙与恐惧名单解耦），以纯 id 保存，不加载任何可选模组的类。两份名单均有意不列入：Wathe 刀与枪、
 * 骑士自己的十字剑刺击（见 {@link #SEALED_WEAPON_PAYLOADS}）、技能键 2（N）数据包、背包面板技能（变形者、换位者与巫毒
 * 变形，SparkStrength 玩家小部件）、由物品打开的界面、商店以及所有退出或关闭数据包。
 * 新增 G 键或武器数据包必须作为规范表中完整的一行有意识地归类。
 */
public final class KnightDuelSkillLock {
    // Generated DUEL_PRIMARY column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 DUEL_PRIMARY 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> BLOCKED_WHILE_DUELING = GeneratedPayloadPolicy.DUEL_PRIMARY;

    /**
     * Weapons fired by C2S payload, which the item seal ({@code KnightDuelSeal}) cannot reach when the handler skips
     * the item cooldown or no item is involved. Never listed: {@code wathe:gunshoot} (the derringer must fire; the
     * revolver is already sealed because Wathe's receiver checks the held gun's cooldown), {@code wathe:knifestab} and
     * {@code sparkwitch:cross_sword_stab} (the duel's own weapons). Classified as not weapons and left out:
     * {@code sparkwitch:double_barrel_aim} (aim claims for a shotgun shot that is an item use the seal blocks),
     * {@code sparkwitch:throw_kidnapper_body} (moves a corpse, hurts no one), {@code sparkwitch:accountant_withdraw}
     * (the passbook's left-click; moves coins, hurts no one), {@code sparkwitch:usec_attachment} and
     * {@code usec_scope} (loading and presentation), {@code sparkwitch:savior_lightsaber_swing} and
     * {@code savior_lightsaber_ignite} (sound intents; the lightsaber kills through the vanilla attack the seal's
     * Attack guard refuses), {@code sparkstrength:taotie_head_fire} (a skill-key-2 volley that
     * dazes and never kills), {@code sparkstrength:drone_pilot_start}, {@code drone_pilot_move} and
     * {@code drone_pilot_exit} (flying a drone; exit must always pass) and {@code sparkstrength:m67_cancel} (cancels a
     * throw).
     * 经 C2S 数据包发射的武器：当处理器跳过物品冷却或根本不涉及物品时，物品封印（{@code KnightDuelSeal}）够不到它们。
     * 永不列入：{@code wathe:gunshoot}（德林加必须能开火；左轮已被封印，因为 Wathe 的接收器会检查手中枪械的冷却）、
     * {@code wathe:knifestab} 与 {@code sparkwitch:cross_sword_stab}（决斗自身的武器）。判定为非武器而不列入：
     * {@code sparkwitch:double_barrel_aim}（猎枪射击的瞄准声明，射击本身是被封印拦截的物品使用）、
     * {@code sparkwitch:throw_kidnapper_body}（只移动尸体，不伤人）、{@code sparkwitch:accountant_withdraw}（存折左键，
     * 只移动金币，不伤人）、{@code sparkwitch:usec_attachment} 与
     * {@code usec_scope}（装填与表现）、{@code sparkwitch:savior_lightsaber_swing} 与 {@code savior_lightsaber_ignite}
     * （音效意图；光剑经原版攻击击杀，封印的攻击拦截会拒绝它）、
     * {@code sparkstrength:taotie_head_fire}（技能键 2 的齐射，只造成眩晕、从不致死）、
     * {@code sparkstrength:drone_pilot_start}、{@code drone_pilot_move} 与 {@code drone_pilot_exit}（驾驶无人机；退出必须
     * 始终放行）以及 {@code sparkstrength:m67_cancel}（取消投掷）。
     */
    // Generated DUEL_WEAPON column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 DUEL_WEAPON 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> SEALED_WEAPON_PAYLOADS = GeneratedPayloadPolicy.DUEL_WEAPON;

    private KnightDuelSkillLock() {
    }

    /**
     * Pure id predicate over both lists, safe on the Netty thread: what the guard drops for a duelist.
     * 覆盖两份名单的纯 id 判定，可在 Netty 线程上调用：拦截器为决斗者丢弃的内容。
     */
    public static boolean isBlockedWhileDueling(@Nullable Identifier payloadId) {
        return payloadId != null
                && (BLOCKED_WHILE_DUELING.contains(payloadId) || SEALED_WEAPON_PAYLOADS.contains(payloadId));
    }

    /**
     * Server thread only: whether the sender is either duelist of the running duel. Server authority: the world
     * component is the duel's only state, so a modified client cannot escape the lock.
     * 仅限服务端主线程：发送者是否为进行中决斗的任一方。服务端权威：世界组件是决斗的唯一状态，修改过的客户端无法逃脱该锁。
     */
    public static boolean isLocked(@Nullable ServerPlayerEntity player) {
        return player != null && KnightDuelWorldComponent.KEY.maybeGet(player.getWorld())
                .map(duel -> duel.isDuelist(player.getUuid()))
                .orElse(false);
    }
}
