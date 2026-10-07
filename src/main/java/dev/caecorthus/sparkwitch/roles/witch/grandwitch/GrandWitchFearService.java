package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.net.SelectBlackRavenDisguiseC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeFearExemption;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.UseSaboteurSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Centralizes Grand Witch Fear restrictions and lightweight sanity pulses.
 * 集中处理大魔女“恐惧”的禁用判断和低频理智扣除。
 */
public final class GrandWitchFearService {
    public static final String SKILL_BLOCKED_KEY = "message.sparkwitch.fear.skill_blocked";
    public static final String INSTINCT_BLOCKED_KEY = "message.sparkwitch.fear.instinct_blocked";
    public static final String SHOP_BLOCKED_KEY = "shop.error.sparkwitch.fear";
    public static final float TOTAL_MOOD_LOSS = 0.5f;
    public static final int PULSE_INTERVAL_TICKS = 20;

    private static final Set<Identifier> BLOCKED_ROLE_SKILL_PAYLOADS = Set.of(
            SparkWitch.id("use_skill"),
            SparkWitch.id("emma_factor"),
            SparkWitch.id("fire_death_ray"),
            SparkWitch.id("use_orthopedist_skill"),
            UseSaboteurSkillC2SPacket.PAYLOAD_ID,
            SparkWitch.id("throw_kidnapper_body"),
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
            // Seeker actions; close and car moves stay allowed so a feared Seeker can always leave the view.
            // 搜寻者行为；关闭与小车移动放行，使被恐惧的搜寻者总能退出视角。
            Identifier.of("sparkwitch", "seeker_remote_open"),
            Identifier.of("sparkwitch", "seeker_car_swallow"),
            Identifier.of("sparkwitch", "seeker_car_recall"),
            Identifier.of("sparkwitch", "seeker_car_use"),
            // Black Raven transform selection; the server also re-checks fear on select.
            // 黑羽鸦变身选择；服务端在选择时也会再次检查恐惧。
            SelectBlackRavenDisguiseC2SPacket.PAYLOAD_ID,
            // Blind Attune. / 盲人凝神。
            Identifier.of("sparkwitch", "use_blind_attune"),
            // Fiend Dash; the server also re-checks fear on use. / 魔人疾驰；服务端在使用时也会再次检查恐惧。
            Identifier.of("sparkwitch", "use_fiend_dash"),
            // Magician record/playback stages; the server also re-checks fear on use.
            // 魔术师录制/播放阶段；服务端在使用时也会再次检查恐惧。
            Identifier.of("sparkwitch", "magician_ability"),
            // SparkStrength role skills, like the NoellesRoles skills above (owner 2026-10-07). Tablet meetings, votes
            // and chat, detective notes, drone moves and exit, and the Timekeeper's watch-mode pick stay allowed; so
            // does the Serial Killer pistol shot (serial_pistol_shoot), a weapon use left off like wathe:gunshoot.
            // SparkStrength 职业技能，与上方 NoellesRoles 技能一致（所有者 2026-10-07）。平板会议、投票与聊天、侦探笔记、
            // 无人机移动与退出以及计时员怀表模式切换放行；连环杀手手枪射击（serial_pistol_shoot）属于武器使用，与
            // wathe:gunshoot 一样不在列。
            Identifier.of("sparkstrength", "noisemaker_glow"),
            Identifier.of("sparkstrength", "phantom_backpack_invisibility"),
            Identifier.of("sparkstrength", "coroner_morph"),
            Identifier.of("sparkstrength", "professor_remote_feed"),
            Identifier.of("sparkstrength", "reporter_communication"),
            Identifier.of("sparkstrength", "drone_pilot_start"),
            Identifier.of("sparkstrength", "drone_pilot_action"),
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            // Vulture Super Curse (key 2) and Taotie head launch. / 秃鹫超级骂（技能键 2）与饕餮发射头颅。
            Identifier.of("sparkstrength", "vulture_super_curse"),
            Identifier.of("sparkstrength", "taotie_head_fire"),
            // Legacy Criminologist skill (older SparkStrength builds); an unregistered id never matches.
            // 旧版犯罪学家技能（较旧的 SparkStrength 构建）；未注册时该 id 永远不会命中。
            Identifier.of("sparkstrength", "select_criminologist_target")
    );

    private GrandWitchFearService() {
    }

    public static boolean isFearActive(World world) {
        return world != null && WitchWorldComponent.KEY.get(world).getFearTicks() > 0;
    }

    /**
     * Fear's skill and instinct block. The Apprentice Witch and players warded by her Healing aura are exempt (owner
     * decisions 2026-10-06 D4/D8); the shop keeps the raw {@link #isPlayerUnderFear} gate.
     * 恐惧对技能与本能的封锁。预备魔女及受其疗愈光环庇护的玩家豁免（所有者 2026-10-06 决定 D4/D8）；商店仍使用原始的
     * {@link #isPlayerUnderFear} 判断。
     */
    public static boolean isPlayerFeared(PlayerEntity player) {
        return isPlayerUnderFear(player) && !ApprenticeFearExemption.isExempt(player);
    }

    /** Raw Fear state of an affected player, without the Apprentice exemptions. / 受影响玩家的原始恐惧状态，不含预备魔女豁免。 */
    public static boolean isPlayerUnderFear(PlayerEntity player) {
        if (player == null || !isFearActive(player.getWorld()) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getWorld());
        return isAffectedRole(gameComponent.getRole(player));
    }

    public static boolean isAffectedRole(Role role) {
        return WitchFactionRules.isAffectedByFear(role);
    }

    public static boolean shouldPulseFear(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks % PULSE_INTERVAL_TICKS == 0;
    }

    public static int pulseCount(int durationTicks) {
        return Math.max(1, (int) Math.ceil(durationTicks / (double) PULSE_INTERVAL_TICKS));
    }

    public static float moodLossPerPulse(int durationTicks) {
        return TOTAL_MOOD_LOSS / pulseCount(durationTicks);
    }

    public static void applyMoodPulse(ServerPlayerEntity player, int durationTicks) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(mood.getMood() - moodLossPerPulse(durationTicks));
    }

    public static boolean isBlockedRoleSkillPayload(Identifier payloadId) {
        return payloadId != null && BLOCKED_ROLE_SKILL_PAYLOADS.contains(payloadId);
    }

    public static boolean shouldBlockRoleSkillPayload(PlayerEntity player, Identifier payloadId) {
        return isBlockedRoleSkillPayload(payloadId) && isPlayerFeared(player);
    }

    public static boolean denyRoleSkillIfFeared(ServerPlayerEntity player) {
        if (!isPlayerFeared(player)) {
            return false;
        }
        sendSkillBlocked(player);
        return true;
    }

    public static void sendSkillBlocked(ServerPlayerEntity player) {
        send(player, SKILL_BLOCKED_KEY);
    }

    private static void send(ServerPlayerEntity player, String translationKey) {
        player.sendMessage(Text.translatable(translationKey), true);
    }
}
