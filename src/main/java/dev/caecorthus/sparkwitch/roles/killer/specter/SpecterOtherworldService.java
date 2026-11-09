package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Otherworld debuff: while the world Otherworld window is open, every living non-killer-faction player gets Slowness I.
 * The effect is refreshed every half second with the remaining window length plus a small margin, so it expires on its
 * own just after the window closes; a longer enemy-applied Slowness instance is never overwritten or removed.
 * The window is world-level, so it keeps running after the Specter dies.
 * 里世界减益：世界里世界窗口开启期间，所有存活的非杀手阵营玩家获得缓慢 I。每半秒按剩余窗口时长加少量余量刷新，
 * 窗口结束后自然过期；其他来源更长/更强的缓慢不会被覆盖或移除。窗口是世界级的，幽魂死亡后仍继续。
 */
public final class SpecterOtherworldService {
    /** Slowness refresh interval; half a second. / 缓慢刷新间隔：半秒。 */
    private static final int REFRESH_INTERVAL_TICKS = 10;
    /** Extra ticks so the effect survives between sweeps and expires just after the window. / 余量：撑过两次刷新并略晚于窗口结束。 */
    private static final int SLOWNESS_MARGIN_TICKS = 5;
    private static boolean registered;

    private SpecterOtherworldService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SpecterOtherworldService::tick);
    }

    /** Immediate pass when the window opens; the periodic sweep handles the rest. / 窗口开启时立即刷一次，其余靠周期检查。 */
    public static void refresh(ServerWorld world) {
        applySlowness(world, SpecterOtherworldComponent.KEY.get(world).getActiveTicks());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            applySlowness(world, SpecterOtherworldComponent.KEY.get(world).getActiveTicks());
        }
    }

    /**
     * Otherworld targeting: a kill credited to a Specter whose window is open may only land on a non-killer-team player
     * whose sanity is at or below 25, or who is cowardly. Everyone else takes no damage and spends no shields.
     * 里世界索敌：幽魂窗口开启时，只能击杀非杀手队、且理智不高于 25 或处于怯懦状态的玩家；
     * 其他目标不受伤、不消耗护盾。
     */
    public static boolean blocksKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (killer == null || killer == victim) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getServerWorld());
        boolean otherworld = SpecterOtherworldComponent.KEY.get(killer.getServerWorld()).isActive()
                || SpecterSecondSkillComponent.KEY.get(killer).isActive();
        if (!SpecterRules.isSpecter(game.getRole(killer)) || !otherworld) {
            return false;
        }
        // Spectators/creative players and role-less test targets are not part of the targeting rule.
        // 旁观/创造模式与没有职业的测试目标不参与索敌限制。
        if (victim.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(victim)) {
            return false;
        }
        Role role = game.getRole(victim);
        if (role == null) {
            return false;
        }
        if (FactionIds.KILLER.equals(SparkFactionApi.resolveEffectiveFaction(victim, game))) {
            return true;
        }
        if (SpecterCowardiceComponent.KEY.get(victim).isCoward()) {
            return false;
        }
        return role.getMoodType() != Role.MoodType.REAL
                || PlayerMoodComponent.KEY.get(victim).getMood() * 100.0F > SpecterRules.LOW_SANITY_THRESHOLD;
    }

    private static void applySlowness(ServerWorld world, int remainingTicks) {
        if (remainingTicks <= 0) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isNonKillerParticipant(player, game)) {
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    remainingTicks + SLOWNESS_MARGIN_TICKS,
                    0,
                    true,
                    false,
                    true
            ));
        }
    }

    /** Alive, not creative/spectating, has a role, and is not on the killer team. / 存活、非创造/旁观、有职业且不在杀手阵营。 */
    private static boolean isNonKillerParticipant(ServerPlayerEntity player, GameWorldComponent game) {
        if (player.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        Role role = game.getRole(player);
        return role != null && !role.canUseKiller();
    }
}
