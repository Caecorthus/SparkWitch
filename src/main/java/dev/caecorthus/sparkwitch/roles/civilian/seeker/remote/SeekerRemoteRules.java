package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure remote-view rules. {@link #BLOCKED_WHILE_VIEWING} is the Seeker's own literal deny-list (decoupled from the
 * Control Expert list; only a test couples them): lethal Wathe actions, the shop, UI actions that would leak through the
 * frozen body, and every known role-skill payload. It is a deny-list, so voice handshakes still pass.
 * TODO(WP-09): open/exit rule helpers. WP-09 may extend but never shrink the list. / 待 WP-09 实现；名单只增不减。
 * 纯遥控视角规则。{@link #BLOCKED_WHILE_VIEWING} 是搜寻者自有的字面量拦截名单（与控场专家名单解耦，仅由测试关联）：
 * Wathe 致命行为、商店、会经冻结本体泄漏的界面操作，以及所有已知职业技能包。它是黑名单，语音握手等照常通过。
 */
public final class SeekerRemoteRules {
    public static final Set<Identifier> BLOCKED_WHILE_VIEWING = Set.copyOf(List.of(
            Identifier.of("wathe", "knifestab"),
            Identifier.of("wathe", "gunshoot"),
            Identifier.of("wathe", "storebuy"),
            Identifier.of("wathe", "note"),
            Identifier.of("wathe", "walkie_talkie_channel"),

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
            Identifier.of("sparkwitch", "use_curser_ability"),
            Identifier.of("sparkwitch", "use_orthopedist_skill"),
            Identifier.of("sparkwitch", "use_saboteur_skill"),
            Identifier.of("sparkwitch", "throw_kidnapper_body"),
            Identifier.of("sparkwitch", "guardian"),
            Identifier.of("sparkwitch", "vendetta_knife_stab"),
            Identifier.of("sparkwitch", "recruit_accomplice"),
            Identifier.of("sparkwitch", "open_judge_selection"),
            Identifier.of("sparkwitch", "confirm_judge_selection"),
            Identifier.of("sparkwitch", "submit_tarot_divination_selection"),
            Identifier.of("sparkwitch", "seeker_car_swallow"),
            Identifier.of("sparkwitch", "seeker_car_recall"),

            Identifier.of("sparkstrength", "noisemaker_glow"),
            Identifier.of("sparkstrength", "phantom_backpack_invisibility"),
            Identifier.of("sparkstrength", "coroner_morph"),
            Identifier.of("sparkstrength", "professor_remote_feed"),
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            Identifier.of("sparkstrength", "call_tablet_meeting"),
            Identifier.of("sparkstrength", "cast_tablet_vote"),
            Identifier.of("sparkstrength", "confirm_tablet_vote"),
            Identifier.of("sparkstrength", "approve_suspect_removal"),
            Identifier.of("sparkstrength", "select_criminologist_target")));

    private SeekerRemoteRules() {
    }

    public static boolean isBlockedWhileViewing(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_WHILE_VIEWING.contains(payloadId);
    }
}
