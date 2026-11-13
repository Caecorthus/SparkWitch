package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.payloadpolicy.GeneratedPayloadPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeFearExemption;
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

    // Generated FEAR column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // Rule of the former literal list: every role skill of SparkWitch, NoellesRoles and SparkStrength is
    // blocked (owner 2026-10-07), weapons are not: wathe:gunshoot, the Savior pistol and the Bandit revolver
    // stay allowed, while the Serial Killer and Demon Hunter pistols are blocked (owner 2026-10-07). Tablet
    // meetings, votes and chat, breaker repair, detective notes, drone moves and exit, the Timekeeper's watch-mode
    // pick and the Seeker's close and car moves stay allowed, so a feared Seeker can always leave the view. Many
    // handlers re-check Fear on use as well. Since 2026-11-13 a role-owned weapon (the Vendetta knife) is blocked
    // like those two pistols, while weapons any holder may fire (the USEC rifle, M32A1, potion launcher) stay allowed.
    // 生成的 FEAR 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    // 原字面量名单的规则：拦截 SparkWitch、NoellesRoles 与 SparkStrength 的所有职业技能（所有者 2026-10-07）；不拦截武器：
    // wathe:gunshoot、救世主手枪与强盗左轮放行，连环杀手与猎魔人手枪被拦截（所有者 2026-10-07）。平板会议、投票与聊天、
    // 修电闸、侦探笔记、无人机移动与退出、计时员怀表模式切换以及搜寻者的关闭与小车移动放行，使被恐惧的搜寻者总能退出视角。
    // 许多处理器在使用时也会再次检查恐惧。自 2026-11-13 起，职业专属武器（仇杀客的刀）与这两把手枪一样被拦截，而任何
    // 持有者都能开火的武器（USEC 步枪、M32A1、药炮发射器）仍然放行。
    private static final Set<Identifier> BLOCKED_ROLE_SKILL_PAYLOADS = GeneratedPayloadPolicy.FEAR;

    /**
     * Fear-blocked ids that are dropped without the "skill blocked" message: per-tick streams whose in-flight packets
     * would otherwise repeat it (Raven Form crow moves; Fear already ends the session on the next server tick). They
     * stay on {@link #BLOCKED_ROLE_SKILL_PAYLOADS}.
     * 被恐惧拦截但不提示“技能被封锁”的 id：逐刻发送的数据流，否则在途数据包会反复刷出提示（化鸦乌鸦移动；恐惧会在下一服务端刻结束会话）。
     * 它们仍保留在 {@link #BLOCKED_ROLE_SKILL_PAYLOADS} 中。
     */
    // Generated FEAR_SILENT column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 FEAR_SILENT 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    private static final Set<Identifier> SILENTLY_BLOCKED_PAYLOADS = GeneratedPayloadPolicy.FEAR_SILENT;

    private GrandWitchFearService() {
    }

    /** Whether a Fear-blocked payload is dropped without a message. / 被恐惧拦截的数据包是否静默丢弃。 */
    public static boolean isSilentlyBlockedPayload(Identifier payloadId) {
        return payloadId != null && SILENTLY_BLOCKED_PAYLOADS.contains(payloadId);
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
