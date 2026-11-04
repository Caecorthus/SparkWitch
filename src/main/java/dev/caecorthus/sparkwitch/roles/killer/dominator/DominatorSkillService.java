package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Dominator skill entry ({@code sparkwitch:use_dominator_skill}). G needs at least one remaining
 * use: the first press marks the aimed player for 30 s without consuming it, a press during the mark releases it,
 * consuming one use and running the forced-shot sequence on the marked player. A refused press changes nothing.
 * 支配者技能的服务端入口（{@code sparkwitch:use_dominator_skill}）。按 G 至少需要 1 次剩余次数：第一次按下标记准星所指的玩家
 * 30 秒且不消耗次数；标记期间再按为释放，消耗一次次数并在被标记者身上执行强制开枪序列。被拒绝的按键不改变任何东西。
 */
public final class DominatorSkillService {
    private DominatorSkillService() {
    }

    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        DominatorPlayerComponent.KEY.get(player).assignForRole(role);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        DominatorPlayerComponent.KEY.get(player).clear();
    }

    /**
     * Every kill the Dominator makes with their own hands restores one use, so running out is never permanent. Kills
     * caused by the forced shot do not count: they are attributed to the marked player, and the suppressed window
     * excludes them explicitly.
     * 支配者亲手每杀一人回复一次可用次数，因此次数耗尽并非永久禁用。技能杀不计：强制开枪的击杀者是被标记者，
     * 且压制窗口内会显式排除。
     */
    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null
                || killer == victim
                || DominatorForcedShotRewards.isSuppressing()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        if (!game.isRunning() || !game.isRole(killer, SparkWitchRoles.dominator())) {
            return;
        }
        DominatorPlayerComponent.KEY.get(killer).addUse();
    }

    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !mayCast(caster)) {
            return;
        }
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(caster);
        if (component.getRemainingUses() <= 0 || component.isOnCooldown()) {
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }
        // While a mark is active, G releases it: consume one use and force the marked player to fire with their own aim.
        // 标记期间按 G 为释放：消耗一次次数，并让被标记者按自己的准星开一枪。
        if (component.hasActiveMark()) {
            ServerPlayerEntity marked = markedPlayer(caster, component);
            if (marked == null) {
                // The target died, left, changed world or became a spectator before the release; revert without
                // charging a use. / 目标在释放前死亡、离线、换了世界或变为旁观者；退回常态且不扣次数。
                component.clearMark();
                return;
            }
            if (DominatorReleaseGate.isForcedShotBlocked(marked)) {
                // Owner decision: a player who could not fire on their own cannot be forced to; the mark, the use and
                // the cooldown stay as they are, so the Dominator can retry while the mark lasts.
                // 所有者决定：自己无法开枪的玩家也不能被强制开枪；标记、次数与冷却均保持不变，标记期间可再次尝试。
                caster.sendMessage(Text.translatable(DominatorRules.TARGET_UNFORCEABLE_KEY), true);
                return;
            }
            if (!DominatorForcedShotService.begin(marked)) {
                component.clearMark();
                return;
            }
            component.releaseMark();
            // Recorded before the shot so the replay reads release, then the forced shooter's gun use and any kill.
            // 在开枪前记录，回放顺序为释放、被强制者的开枪记录、以及可能的击杀。
            GameRecordManager.recordSkillUse(caster, DominatorRules.RELEASE_SKILL_ID, marked, null);
            DominatorForcedShotService.fireNow(marked, caster);
            return;
        }
        PlayerEntity target = DominatorRules.findAimedPlayer(caster);
        if (!(target instanceof ServerPlayerEntity serverTarget)) {
            return;
        }
        component.mark(serverTarget.getUuid());
        GameRecordManager.recordSkillUse(caster, DominatorRules.MARK_SKILL_ID, serverTarget, null);
    }

    /**
     * Caster gate, refusing silently (Fear, which shows its own message, is checked by the caller): exactly ACTIVE
     * (not the post-win STOPPING fade, which Wathe still calls running); the Dominator; alive, not a spectator and not
     * swallowed ({@link DominatorRules#isActiveCaster}); not held by another mechanic ({@link NinjaGrappleService#isHeld}:
     * Taotie, Last Stand, Last Escape, Kidnapper control, Control Expert stun, Seeker session, borrowed camera, Hunter
     * root); not silenced by NoellesRoles (checked directly so it holds without SparkTraits, as for the Magician and
     * Time Stealer) and not role-skill blocked by SparkTraits. The payload guards drop the request in most of these
     * states too; this keeps the handler correct on its own.
     * 施放者门槛，静默拒绝（恐惧会显示自己的提示，由调用方检查）：对局恰为 ACTIVE（不含胜负判定后的 STOPPING 淡出，Wathe 仍视其为
     * 进行中）；是支配者；存活、非旁观、未被吞下（{@link DominatorRules#isActiveCaster}）；未被其他机制持有
     * （{@link NinjaGrappleService#isHeld}：饕餮、背水一战、脱险、绑架者控制、控场专家眩晕、搜寻者会话、镜头被占用、猎人定身）；
     * 未被 NoellesRoles 沉默（直接检查，使其在没有 SparkTraits 时同样生效，与魔术师、窃时者一致），且未被 SparkTraits 封锁职业技能。
     * 这些状态下数据包拦截大多也会丢弃请求；此处保证处理器自身正确。
     */
    static boolean mayCast(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && game.isRole(caster, SparkWitchRoles.dominator())
                && DominatorRules.isActiveCaster(caster)
                && !NinjaGrappleService.isHeld(caster)
                && !NoellesSilenceBridge.isSilenced(caster)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(caster);
    }

    /**
     * The marked player if they can still be released on: online, in the Dominator's own world (a forced shot across
     * worlds is never meaningful), not a spectator and alive in Wathe. Otherwise null, which ends the mark.
     * 若被标记者仍可被释放则返回之：在线、与支配者处于同一世界（跨世界的强制开枪毫无意义）、非旁观且在 Wathe 中存活。否则返回
     * null，标记随之结束。
     */
    private static @Nullable ServerPlayerEntity markedPlayer(
            ServerPlayerEntity owner,
            DominatorPlayerComponent component
    ) {
        UUID uuid = component.getMarkedTarget();
        if (uuid == null || owner.getServer() == null) {
            return null;
        }
        ServerPlayerEntity target = owner.getServer().getPlayerManager().getPlayer(uuid);
        return target != null
                && target.getServerWorld() == owner.getServerWorld()
                && !target.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                ? target
                : null;
    }

}
