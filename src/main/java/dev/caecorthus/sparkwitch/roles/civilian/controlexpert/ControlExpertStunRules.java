package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Side-neutral input-lock rules for the Control Expert stun.
 * 控场专家眩晕输入锁的两端通用规则。
 */
public final class ControlExpertStunRules {
    /**
     * Simple Voice Chat's key category. Push-to-talk reads raw input anyway; this keeps its toggle keys usable.
     * Simple Voice Chat 的按键分类。按键通话本就读取原始输入；此项让其开关类按键仍可使用。
     */
    public static final String EXEMPT_KEY_CATEGORY = "key.categories.voicechat";

    /**
     * Owner switch: false lets the camera rotate during the stun; true also freezes mouse look.
     * 所有者开关：false 时眩晕期间视角仍可转动；true 时同时冻结鼠标视角。
     */
    public static final boolean LOCK_CAMERA = false;

    /**
     * Stable contract: C2S payloads dropped while the sender is stunned — lethal Wathe actions, the shop, and every
     * known role-skill payload. Kept as plain ids so no optional mod class is loaded. UI-only payloads (notes, map
     * vote, walkie channel, grenade cancel, tablet chat/snapshot/channel, detective case notes) stay allowed.
     * A new skill payload must be classified here deliberately.
     * 稳定契约：发送者处于眩晕时丢弃的 C2S 数据包——Wathe 致命行为、商店以及所有已知职业技能包。
     * 以纯 id 保存，不加载任何可选模组的类。仅界面用途的数据包（笔记、地图投票、对讲频道、手雷取消、
     * 平板聊天/快照/频道、侦探案件笔记）仍然放行。新增技能包必须在此有意识地归类。
     */
    public static final Set<Identifier> BLOCKED_PAYLOADS = Set.copyOf(List.of(
            Identifier.of("wathe", "knifestab"),
            Identifier.of("wathe", "gunshoot"),
            Identifier.of("wathe", "storebuy"),

            Identifier.of("noellesroles", "ability"),
            Identifier.of("noellesroles", "assassin_guess_role"),
            Identifier.of("noellesroles", "detective_investigate"),
            Identifier.of("noellesroles", "morph"),
            Identifier.of("noellesroles", "morph_corpse_toggle"),
            Identifier.of("noellesroles", "party_animal_buzz"),
            Identifier.of("noellesroles", "reporter_mark"),
            Identifier.of("noellesroles", "silencer_silence"),
            Identifier.of("noellesroles", "spirit_project"),
            Identifier.of("noellesroles", "swapper"),
            Identifier.of("noellesroles", "taotie_swallow"),
            Identifier.of("noellesroles", "vulture"),
            Identifier.of("noellesroles", "demon_hunter_shoot"),
            Identifier.of("noellesroles", "shadow_ally_request"),

            Identifier.of("sparkwitch", "use_skill"),
            Identifier.of("sparkwitch", "emma_factor"),
            Identifier.of("sparkwitch", "fire_death_ray"),
            Identifier.of("sparkwitch", "fire_potion_launcher"),
            Identifier.of("sparkwitch", "use_curser_ability"),
            Identifier.of("sparkwitch", "use_orthopedist_skill"),
            Identifier.of("sparkwitch", "use_saboteur_skill"),
            Identifier.of("sparkwitch", "throw_kidnapper_body"),
            Identifier.of("sparkwitch", "guardian"),
            Identifier.of("sparkwitch", "vendetta_knife_stab"),
            Identifier.of("sparkwitch", "open_judge_selection"),
            Identifier.of("sparkwitch", "confirm_judge_selection"),
            // Prophet Prophecy request and priced confirmation, classified like the Judge's pair.
            // 先知预言的请求与付费确认，与法官的两个包同样归类。
            Identifier.of("sparkwitch", "request_prophecy"),
            Identifier.of("sparkwitch", "confirm_prophecy"),
            Identifier.of("sparkwitch", "submit_tarot_divination_selection"),
            // Seeker actions (open, swallow, remote recall); close and car moves stay allowed because a stun ends the
            // session anyway. / 搜寻者行为（打开、吞车、远程回收）；关闭与小车移动放行，因为眩晕本身就会结束会话。
            Identifier.of("sparkwitch", "seeker_remote_open"),
            Identifier.of("sparkwitch", "seeker_car_swallow"),
            Identifier.of("sparkwitch", "seeker_car_recall"),
            Identifier.of("sparkwitch", "seeker_car_use"),
            // Black Raven transform selection (SelectBlackRavenDisguiseC2SPacket). / 黑羽鸦变身选择。
            Identifier.of("sparkwitch", "select_black_raven_disguise"),
            // Blind Attune (UseBlindAttuneC2SPayload). / 盲人凝神。
            Identifier.of("sparkwitch", "use_blind_attune"),
            // Riftwalker gate hop and console close; the occupant's own exit and the console snapshot request stay
            // allowed (leaving must always pass; the request is UI-only). / 隙行者跳门与控制台关门；门内玩家自己的
            // 出门与控制台快照请求放行（出门必须始终放行；快照请求仅为界面用途）。
            Identifier.of("sparkwitch", "rift_hop"),
            Identifier.of("sparkwitch", "rift_gate_close"),
            // Fiend Dash (UseFiendDashC2SPayload), moment-only. / 魔人疾驰，仅限时刻中。
            Identifier.of("sparkwitch", "use_fiend_dash"),

            Identifier.of("sparkstrength", "noisemaker_glow"),
            Identifier.of("sparkstrength", "phantom_backpack_invisibility"),
            Identifier.of("sparkstrength", "coroner_morph"),
            Identifier.of("sparkstrength", "professor_remote_feed"),
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            Identifier.of("sparkstrength", "call_tablet_meeting"),
            Identifier.of("sparkstrength", "cast_tablet_vote"),
            Identifier.of("sparkstrength", "confirm_tablet_vote"),
            Identifier.of("sparkstrength", "approve_suspect_removal"),
            // Legacy Criminologist skill: still registered by SparkStrength main-line and pre-b17ab75 1.0.7 builds,
            // which "suggests: *" allows to load; an unregistered id never matches.
            // 旧版犯罪学家技能：SparkStrength main 线及 b17ab75 之前的 1.0.7 仍注册此包，"suggests: *" 允许其同时加载；
            // 未注册时该 id 永远不会命中。
            Identifier.of("sparkstrength", "select_criminologist_target")));

    private ControlExpertStunRules() {
    }

    public static boolean isBlockedPayload(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_PAYLOADS.contains(payloadId);
    }

    public static boolean isExemptKeyCategory(@Nullable String category) {
        return EXEMPT_KEY_CATEGORY.equals(category);
    }
}
