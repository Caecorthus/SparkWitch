package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Dominator skill entry. Marking is free and lasts 30 s; releasing the mark consumes one use
 * and runs the forced-shot sequence on the marked player.
 * 支配者技能的服务端入口。标记不消耗次数、持续 30 秒；释放标记消耗一次次数，并在被标记者身上执行强制开枪序列。
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
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(caster);
        if (!game.isRunning()
                || !game.isRole(caster, SparkWitchRoles.dominator())
                || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || component.getRemainingUses() <= 0
                || component.isOnCooldown()) {
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
                // The target died, left or became a spectator before the release; revert without charging a use.
                // 目标在释放前死亡、离线或变为旁观者；退回常态且不扣次数。
                component.clearMark();
                return;
            }
            if (!DominatorForcedShotService.begin(marked)) {
                component.clearMark();
                return;
            }
            component.releaseMark();
            DominatorForcedShotService.fireNow(marked, caster);
            return;
        }
        PlayerEntity target = DominatorRules.findAimedPlayer(caster);
        if (target == null) {
            return;
        }
        component.mark(target.getUuid());
    }

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
                && !target.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                ? target
                : null;
    }

}
