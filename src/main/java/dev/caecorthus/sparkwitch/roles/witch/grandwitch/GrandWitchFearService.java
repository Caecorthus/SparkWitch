package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
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
            SparkWitch.id("fire_death_ray"),
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
            Identifier.of("noellesroles", "demon_hunter_shoot")
    );

    private GrandWitchFearService() {
    }

    public static boolean isFearActive(World world) {
        return world != null && WitchWorldComponent.KEY.get(world).getFearTicks() > 0;
    }

    public static boolean isPlayerFeared(PlayerEntity player) {
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
