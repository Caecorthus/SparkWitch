package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
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
     * Otherworld targeting rule: any non-forced kill credited to a Specter while the world Otherworld window is open
     * only lands on a player who is not effective-killer faction ({@link SpecterRules#isEffectiveKiller}) and is either
     * cowardly or at effective real sanity 25 or below ({@link SpecterRules#isLowSanity}) — the same players the
     * Specter sees framed. Any other victim takes no damage and spends no shield or protection, whatever weapon
     * (knife, gun, bat, grenade, poison, a thrown item from another mod) delivered the blow. Falls, drowning, escapes
     * and mental breakdowns carry no attack and stay untouched, as does a forced kill. Spectating, creative and
     * role-less victims are not part of the rule.
     * 里世界索敌规则：世界里世界窗口开启时，记在幽魂名下的任何非强制击杀都只能落在不属于有效杀手阵营
     * （{@link SpecterRules#isEffectiveKiller}），且处于怯懦或有效真实理智不高于 25（{@link SpecterRules#isLowSanity}）
     * 的玩家身上——也就是幽魂看到有边框的那批玩家。其他受害者不受伤，也不消耗任何护盾或保护，无论致命一击来自小刀、枪、
     * 球棒、手雷、毒，还是其他模组投出的武器。坠落、溺水、逃离列车与精神崩溃不含攻击，不受影响；强制击杀同样不受影响。
     * 旁观、创造与没有职业的受害者不参与该规则。
     */
    public static boolean blocksKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                     @Nullable Identifier deathReason, boolean force) {
        if (force
                || killer == null
                || killer == victim
                || isNonAttackReason(deathReason)
                || !SpecterOtherworldComponent.KEY.get(killer.getServerWorld()).isActive()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getServerWorld());
        if (!SpecterRules.isSpecter(game.getRole(killer))) {
            return false;
        }
        // Spectators/creative players and role-less test targets are not part of the targeting rule.
        // 旁观/创造模式与没有职业的测试目标不参与索敌限制。
        if (victim.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(victim)
                || game.getRole(victim) == null) {
            return false;
        }
        return !isOtherworldTarget(victim);
    }

    /**
     * Server knife receiver seam ({@code SpecterOtherworldKnifeMixin}, HEAD 900 ahead of every knife guard): true only
     * when a living Specter holding the Wathe knife stabs, inside an open Otherworld, a living non-spectator player the
     * targeting rule rejects. Such a stab is dropped with no side effects (no record, sound, Veteran use or cooldown);
     * every other stab, including every non-player target, goes through Wathe's receiver and all its guards.
     * 服务端刀刺接收器接缝（{@code SpecterOtherworldKnifeMixin}，HEAD 900，先于所有刀守卫）：仅当里世界开启时，手持 Wathe
     * 小刀的存活幽魂刺向一名被索敌规则拒绝的存活非旁观玩家时为 true。这样的刺击被无副作用地丢弃（不记录、无音效、不消耗老兵
     * 次数、不写冷却）；其他所有刺击（包括所有非玩家目标）照常走 Wathe 接收器及其全部守卫。
     */
    public static boolean rejectsKnifeStab(@Nullable ServerPlayerEntity attacker, int targetId) {
        if (attacker == null
                || !SpecterOtherworldComponent.KEY.get(attacker.getServerWorld()).isActive()
                || attacker.isSpectator()
                || !GameFunctions.isPlayerPlayingAndAlive(attacker)
                || !(attacker.getMainHandStack().isOf(WatheItems.KNIFE)
                || attacker.getOffHandStack().isOf(WatheItems.KNIFE))) {
            return false;
        }
        if (!(attacker.getServerWorld().getEntityById(targetId) instanceof ServerPlayerEntity victim)
                || victim.isSpectator()
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return false;
        }
        return blocksKill(victim, attacker, GameConstants.DeathReasons.KNIFE, false);
    }

    /** Deaths that carry no attack, so the rule has no target to pick. / 不含攻击的死因，规则不介入。 */
    private static boolean isNonAttackReason(@Nullable Identifier deathReason) {
        return GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                || GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                || GameConstants.DeathReasons.DROWNED.equals(deathReason)
                || GameConstants.DeathReasons.MENTAL_BREAKDOWN.equals(deathReason);
    }

    /** Not effective killer, and cowardly or at low effective real sanity. / 非有效杀手，且怯懦或有效真实理智偏低。 */
    private static boolean isOtherworldTarget(ServerPlayerEntity victim) {
        if (SpecterRules.isEffectiveKiller(victim)) {
            return false;
        }
        return SpecterCowardiceComponent.KEY.get(victim).isCoward() || SpecterRules.isLowSanity(victim);
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

    /**
     * Alive, not creative/spectating, has a role that is not the Specter itself, and not SparkFactionAPI effective
     * killer faction (a Conscience killer is slowed, an Impostor is not; the caster never slows itself).
     * 存活、非创造/旁观、有职业且不是幽魂本身，并且不属于 SFA 有效杀手阵营（善良杀手会被减速，内鬼不会；施放者不会减速自己）。
     */
    private static boolean isNonKillerParticipant(ServerPlayerEntity player, GameWorldComponent game) {
        if (player.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        var role = game.getRole(player);
        return role != null && !SpecterRules.isSpecter(role) && !SpecterRules.isEffectiveKiller(player);
    }
}
