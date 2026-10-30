package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Stable contract (owner decision 2026-10-30): while a Knight duel runs, BOTH duelists lose every primary role skill,
 * meaning every payload sent from the shared NoellesRoles ability key (G), and every weapon fired by C2S payload
 * rather than by an item use (spec: all weapons but the knife and the derringer are sealed). The server enforces both
 * lists in {@code mixin/knight/KnightDuelSkillPayloadGuardMixin}; the client's shared dispatch only skips a duelist's
 * press. {@link #BLOCKED_WHILE_DUELING} (G skills) and {@link #SEALED_WEAPON_PAYLOADS} (payload weapons) are this
 * lock's own literal lists (decoupled from the stun, Seeker, Rift and Fear lists), kept as plain ids so no optional mod
 * class is loaded. Deliberately left out of both: the Wathe knife and gun and the Knight's own cross-sword stab (see
 * {@link #SEALED_WEAPON_PAYLOADS}), skill-key-2 (N) payloads, inventory-panel skills (Morphling, Swapper and Voodoo
 * morphs, SparkStrength player widgets), item-opened screens, the shop, and every exit or close payload. A new G-key
 * or weapon payload must be classified here deliberately.
 * 稳定契约（所有者 2026-10-30 决定）：骑士决斗进行时，双方决斗者失去全部主职业技能，即共享的 NoellesRoles 技能键（G）
 * 发送的所有数据包，以及所有经 C2S 数据包而非物品使用发射的武器（规格：除刀与德林加外的武器均被封印）。两份名单都由服务端的
 * {@code mixin/knight/KnightDuelSkillPayloadGuardMixin} 强制执行；客户端共享分发只会跳过决斗者的按键。
 * {@link #BLOCKED_WHILE_DUELING}（G 键技能）与 {@link #SEALED_WEAPON_PAYLOADS}（数据包武器）是该锁自有的字面量名单
 * （与眩晕、搜寻者、裂隙与恐惧名单解耦），以纯 id 保存，不加载任何可选模组的类。两份名单均有意不列入：Wathe 刀与枪、
 * 骑士自己的十字剑刺击（见 {@link #SEALED_WEAPON_PAYLOADS}）、技能键 2（N）数据包、背包面板技能（变形者、换位者与巫毒
 * 变形，SparkStrength 玩家小部件）、由物品打开的界面、商店以及所有退出或关闭数据包。新增 G 键或武器数据包必须在此有意识地归类。
 */
public final class KnightDuelSkillLock {
    public static final Set<Identifier> BLOCKED_WHILE_DUELING = Set.copyOf(List.of(
            // NoellesRoles: every packet its own ability-key handler sends, plus the guess from the Assassin screen G
            // opens. / NoellesRoles：其技能键处理器发送的全部数据包，以及 G 打开的刺客界面发出的猜测包。
            Identifier.of("noellesroles", "ability"),
            Identifier.of("noellesroles", "assassin_guess_role"),
            Identifier.of("noellesroles", "detective_investigate"),
            Identifier.of("noellesroles", "morph_corpse_toggle"),
            Identifier.of("noellesroles", "party_animal_buzz"),
            Identifier.of("noellesroles", "reporter_mark"),
            Identifier.of("noellesroles", "silencer_silence"),
            Identifier.of("noellesroles", "spirit_project"),
            Identifier.of("noellesroles", "taotie_swallow"),
            Identifier.of("noellesroles", "vulture"),
            Identifier.of("noellesroles", "shadow_ally_request"),

            // SparkWitch: the branches of the shared dispatch in SparkWitchClient and their priced confirmations.
            // SparkWitch：SparkWitchClient 共享分发的各分支及其付费确认包。
            Identifier.of("sparkwitch", "use_skill"),
            Identifier.of("sparkwitch", "emma_factor"),
            Identifier.of("sparkwitch", "use_curser_ability"),
            Identifier.of("sparkwitch", "use_orthopedist_skill"),
            Identifier.of("sparkwitch", "use_saboteur_skill"),
            Identifier.of("sparkwitch", "guardian"),
            Identifier.of("sparkwitch", "magician_ability"),
            Identifier.of("sparkwitch", "open_judge_selection"),
            Identifier.of("sparkwitch", "confirm_judge_selection"),
            Identifier.of("sparkwitch", "request_prophecy"),
            Identifier.of("sparkwitch", "confirm_prophecy"),
            // Tarot Reader pick in the selector its use_skill opened. / 塔罗师在 use_skill 打开的选择界面中的选择。
            Identifier.of("sparkwitch", "submit_tarot_divination_selection"),
            Identifier.of("sparkwitch", "use_blind_attune"),
            Identifier.of("sparkwitch", "use_fiend_dash"),
            // The Knight's own pick; a non-duelist is never locked, so it always passes for them.
            // 骑士自己的选择包；非决斗者从不被锁，因此对他们始终放行。
            Identifier.of("sparkwitch", "knight_duel_target"),
            // A Taotie's G on a Seeker car. / 饕餮对搜寻者小车按 G。
            Identifier.of("sparkwitch", "seeker_car_swallow"),

            // SparkStrength listeners on the same key: the Demon Hunter Sniff and a Taotie swallowing a Partygoer.
            // SparkStrength 在同一按键上的监听：猎魔人嗅探与饕餮吞下派对客。
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            Identifier.of("sparkstrength", "partygoer_swallow")));

    /**
     * Weapons fired by C2S payload, which the item seal ({@code KnightDuelSeal}) cannot reach when the handler skips
     * the item cooldown or no item is involved. Never listed: {@code wathe:gunshoot} (the derringer must fire; the
     * revolver is already sealed because Wathe's receiver checks the held gun's cooldown), {@code wathe:knifestab} and
     * {@code sparkwitch:cross_sword_stab} (the duel's own weapons). Classified as not weapons and left out:
     * {@code sparkwitch:double_barrel_aim} (aim claims for a shotgun shot that is an item use the seal blocks),
     * {@code sparkwitch:throw_kidnapper_body} (moves a corpse, hurts no one), {@code sparkwitch:usec_attachment} and
     * {@code usec_scope} (loading and presentation), {@code sparkstrength:taotie_head_fire} (a skill-key-2 volley that
     * dazes and never kills), {@code sparkstrength:drone_pilot_start}, {@code drone_pilot_move} and
     * {@code drone_pilot_exit} (flying a drone; exit must always pass) and {@code sparkstrength:m67_cancel} (cancels a
     * throw).
     * 经 C2S 数据包发射的武器：当处理器跳过物品冷却或根本不涉及物品时，物品封印（{@code KnightDuelSeal}）够不到它们。
     * 永不列入：{@code wathe:gunshoot}（德林加必须能开火；左轮已被封印，因为 Wathe 的接收器会检查手中枪械的冷却）、
     * {@code wathe:knifestab} 与 {@code sparkwitch:cross_sword_stab}（决斗自身的武器）。判定为非武器而不列入：
     * {@code sparkwitch:double_barrel_aim}（猎枪射击的瞄准声明，射击本身是被封印拦截的物品使用）、
     * {@code sparkwitch:throw_kidnapper_body}（只移动尸体，不伤人）、{@code sparkwitch:usec_attachment} 与
     * {@code usec_scope}（装填与表现）、{@code sparkstrength:taotie_head_fire}（技能键 2 的齐射，只造成眩晕、从不致死）、
     * {@code sparkstrength:drone_pilot_start}、{@code drone_pilot_move} 与 {@code drone_pilot_exit}（驾驶无人机；退出必须
     * 始终放行）以及 {@code sparkstrength:m67_cancel}（取消投掷）。
     */
    public static final Set<Identifier> SEALED_WEAPON_PAYLOADS = Set.copyOf(List.of(
            // NoellesRoles Demon Hunter pistol (its receiver checks the pistol cooldown; listed so the seal does not
            // rest on each handler). / NoellesRoles 猎魔人手枪（接收器会检查手枪冷却；列入名单使封印不依赖各处理器）。
            Identifier.of("noellesroles", "demon_hunter_shoot"),
            // Murderous Witch Death Ray: no item at all, so the seal never reaches it (review finding).
            // 杀意魔女死光：完全不涉及物品，封印无从覆盖（审核发现）。
            Identifier.of("sparkwitch", "fire_death_ray"),
            // Potion Gunner anti-tank launcher, USEC rifle and Witch Maiden crimson skull (cooldown-gated handlers).
            // 药炮手反坦克发射器、USEC 步枪与巫女赤首（处理器受冷却限制）。
            Identifier.of("sparkwitch", "fire_potion_launcher"),
            Identifier.of("sparkwitch", "fire_usec_rifle"),
            Identifier.of("sparkwitch", "fire_crimson_skull"),
            // Vendetta knife (the Vendetta is an active Wraith, who cannot duel; listed for completeness) and the
            // Fisher's swordfish stab. / 仇杀刀（仇杀者是活跃冤魂，无法决斗；为完整起见列入）与钓鱼佬的剑鱼刺击。
            Identifier.of("sparkwitch", "vendetta_knife_stab"),
            Identifier.of("sparkwitch", "swordfish_stab"),
            // SparkStrength Serial Killer psycho pistols and the Bomber drone's FIRE (M67 drop or detonation).
            // SparkStrength 连环杀手疯魔双枪与炸弹客无人机开火（投下 M67 或引爆）。
            Identifier.of("sparkstrength", "serial_pistol_shoot"),
            Identifier.of("sparkstrength", "drone_pilot_action")));

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
