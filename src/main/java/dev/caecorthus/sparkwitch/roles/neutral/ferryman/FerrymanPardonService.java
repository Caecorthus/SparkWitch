package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Ferryman's first skill on the shared ability key: a lethal hit opens a 0.5 s window instead of killing, and a
 * press inside it cancels the death, marks the attacker with 瞥视 and restarts a 10 s cooldown. An expired window
 * replays the exact kill it held (same killer, same death reason, same {@code spawnBody}, not forced), so nothing about
 * the original hit is rewritten. Server only.
 * 摆渡人在共享技能键上的第一技能：致命一击改为开启 0.5 秒窗口，窗口内按键即取消死亡、给攻击者挂上「瞥视」并进入
 * 10 秒冷却；窗口过期则原样重放它所保存的那次击杀（同凶手、同死亡原因、同 {@code spawnBody}、非强制），原命中的任何
 * 信息都不被改写。仅服务端。
 *
 * <p>The decision is made by {@link #interceptKill} from {@code mixin/ferryman/GameFunctionsFerrymanPardonMixin}, a
 * HEAD guard on Wathe's 5-arg {@code killPlayer} that sees {@code force}; it is deliberately not a
 * {@code KillPlayer.BEFORE} listener: Wathe's BEFORE invoker stops at the first non-null result, so a listener here
 * could switch off every protection registered after it, and it could not tell a forced kill (which ignores a cancel)
 * from an ordinary one.
 * 该判定由 {@code mixin/ferryman/GameFunctionsFerrymanPardonMixin}（Wathe 五参 {@code killPlayer} 的 HEAD 守卫，可见
 * {@code force}）调用 {@link #interceptKill} 完成；刻意不做成 {@code KillPlayer.BEFORE} 监听器：Wathe 的 BEFORE 调用器
 * 在第一个非 null 结果处停止，此处的监听器可能关掉其后注册的所有保护，而且分不清强制击杀（无视取消）与普通击杀。</p>
 */
public final class FerrymanPardonService {
    private static final String PROMPT_KEY = "message.sparkwitch.ferryman.pardon.prompt";
    private static final String SAVED_KEY = "message.sparkwitch.ferryman.pardon.saved";
    private static final String UNAVAILABLE_KEY = "message.sparkwitch.ferryman.pardon.unavailable";
    private static final String FEIGN_KEY = "message.sparkwitch.ferryman.gaze.feign";
    private static final String MARKED_KEY = "message.sparkwitch.ferryman.gaze.marked";
    /**
     * Re-entrancy guard: the replay must reach Wathe's own kill path without our HEAD guard holding it again.
     * Server thread only.
     * 重入保护：重放必须能进入 Wathe 自己的击杀流程，而不能被本 HEAD 守卫再次扣住。仅服务端线程。
     */
    private static boolean replayingDeath;
    private static boolean registered;

    private FerrymanPardonService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(FerrymanPardonService::afterKill);
        ServerTickEvents.END_SERVER_TICK.register(FerrymanPardonService::tick);
        ResetPlayer.EVENT.register(FerrymanPardonService::onReset);
        // A restart drops every window; the death it held is not owed after a server stop.
        // 重启丢弃所有窗口：服务器停止后不再追讨它扣住的那次死亡。
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> replayingDeath = false);
    }

    /**
     * HEAD of Wathe's 5-arg {@code killPlayer}: returns true when the kill must be cancelled. Every victim that is not
     * the Charon returns false at once, so no other role, weapon or reason reaches the pardon path. A forced or
     * SparkTraits-terminal kill, a kill without an attacker, an unheld Wathe reason (train fall, disconnect…) and the
     * window's own replay pass untouched: no window, no feign, no gaze spent.
     * Wathe 五参 {@code killPlayer} 的 HEAD：返回 true 表示必须取消这次击杀。凡不是摆渡人的受害者立即返回 false，
     * 因此其他职业、武器与死因都不会进入宽恕路径。强制或 SparkTraits 终结击杀、没有攻击者的击杀、不扣住的 Wathe 死因
     * （坠车、断线……）以及窗口自身的重放一律放行：不开窗口、不假死、不消耗瞥视。
     */
    public static boolean interceptKill(ServerPlayerEntity victim, boolean spawnBody,
                                        @Nullable ServerPlayerEntity killer, @Nullable Identifier deathReason,
                                        boolean force) {
        if (victim == null || replayingDeath || force || killer == null
                || !FerrymanParticipation.isFerryman(victim)) {
            return false;
        }
        boolean terminal = SparkTraitsKillerBridge.isTerminalDeathReason(deathReason);
        if (!FerrymanRules.mayHoldKill(replayingDeath, force, terminal, true, killer == victim, deathReason)) {
            return false;
        }
        // A Charon already out of the round, or held in a Taotie's stomach, has no pardon to give.
        // 已离开对局或被饕餮吞在胃里的摆渡人无从宽恕。
        if (!FerrymanParticipation.isActiveFerryman(victim) || NoellesTaotieSeekerBridge.isSwallowed(victim)) {
            return false;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(victim);
        // A marked attacker's second strike is answered with 10 s of feign death instead of another pardon, and it
        // consumes the mark: the glance is spent the moment it pays off, never left hanging on the killer. An attacker
        // that cannot feign now keeps its mark and the hit falls through to the plain pardon below.
        // 被标记者再次攻击时，回答他的是 10 秒假死，而不是再一次宽恕；这层「瞥视」当场被消耗，不会一直挂在他头上。此刻无法
        // 假死的攻击者保留标记，这一击落回下面的普通宽恕。
        if (component.hasGaze(killer.getUuid()) && startFeign(killer)) {
            component.removeGaze(killer.getUuid());
            killer.sendMessage(Text.translatable(FEIGN_KEY), true);
            return true;
        }
        // A second lethal hit inside an open window kills normally (owner decision kept).
        // 窗口开启期间的第二次致命一击照常击杀（保留所有者决定）。
        if (component.isPardoning() || !FerrymanRules.canPardon(component.cooldownTicks())) {
            return false;
        }
        long now = victim.getServerWorld().getTime();
        component.openPardon(killer.getUuid(), deathReason, spawnBody, now + FerrymanRules.PARDON_WINDOW_TICKS);
        victim.sendMessage(Text.translatable(PROMPT_KEY), true);
        return true;
    }

    /**
     * Whether a held kill is replaying right now, on this thread's synchronous replay call (and its same-victim
     * re-entries). The BEAR Rys-T ricochet reads this: the held kill already passed its earlier hook (priority 1050)
     * un-deflected, so the replay is that same attack, not a new roll. Server thread only.
     * 此刻是否正在重放被扣住的击杀，即本线程同步执行的那次重放调用（及其对同一受害者的重入）。BEAR Rys-T 跳弹读取此值：被扣住的
     * 击杀先前已经未被弹开地通过了它更早的钩子（优先级 1050），因此重放是同一次攻击，而不是新的掷骰。仅限服务端线程。
     */
    public static boolean isReplayingHeldKill() {
        return replayingDeath;
    }

    /** A failing feign never cancels the kill silently: it falls back to the pardon. / 假死出错绝不会静默取消击杀：改走宽恕。 */
    private static boolean startFeign(ServerPlayerEntity killer) {
        try {
            return FerrymanFeignService.start(killer);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.error("Ferryman gaze feign failed for {}; falling back to the pardon.",
                    killer.getUuid(), exception);
            return false;
        }
    }

    private static void tick(MinecraftServer server) {
        // A replayed kill never adds or removes a player, so the live list is safe to walk.
        // 重放的击杀不会增删玩家，因此可以直接遍历实时列表。
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
            if (FerrymanParticipation.isFerryman(player)) {
                component.tickCooldown();
                component.tickFerryCooldown();
            }
            // Checked for every player, so a window never outlives a re-role mid-window.
            // 对每名玩家检查，窗口因此不会因窗口期间换职业而残留。
            if (component.isPardoning() && player.getServerWorld().getTime() >= component.pardonDeadlineTick()) {
                resolveExpiredWindow(player, component);
            }
        }
    }

    /**
     * The window ran out without a press: the held kill is owed and replays exactly, but only while the Charon is still
     * playing and alive; a Charon that already died another way just loses the window.
     * 窗口到期且未按键：被扣住的击杀仍需偿还并原样重放，但仅在摆渡人仍在局内存活时；已经以其他方式死亡的摆渡人只是失去窗口。
     */
    private static void resolveExpiredWindow(ServerPlayerEntity victim, FerrymanPlayerComponent component) {
        UUID killerId = component.pendingKiller();
        Identifier reason = component.pendingDeathReason();
        boolean spawnBody = component.pendingSpawnBody();
        component.closePardon();
        if (!GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return;
        }
        MinecraftServer server = victim.getServer();
        ServerPlayerEntity killer = killerId == null || server == null
                ? null
                : server.getPlayerManager().getPlayer(killerId);
        replayingDeath = true;
        try {
            // The original kill was never forced (forced kills are not held), so neither is its replay.
            // 原击杀从不是强制的（强制击杀不会被扣住），重放因此也不强制。
            GameFunctions.killPlayer(victim, spawnBody, killer,
                    reason == null ? SparkWitchDeathReasons.PARDON_EXPIRED : reason, false);
        } finally {
            replayingDeath = false;
        }
    }

    /**
     * Any committed death of a Charon (a second attacker inside the window, a kill without an attacker, a forced kill,
     * a disconnect) closes its pending window, so the window can never replay a second death.
     * 摆渡人的任何已提交死亡（窗口内的第二名攻击者、无攻击者的击杀、强制击杀、断线）都会关闭其待决窗口，窗口因此绝不会重放
     * 第二次死亡。
     */
    private static void afterKill(@Nullable ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  @Nullable Identifier deathReason) {
        if (victim == null) {
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(victim);
        if (component.isPardoning()) {
            component.closePardon();
        }
    }

    /** Shared ability key, server side. Returns false when no window is open. / 共享技能键的服务端处理；无窗口时返回 false。 */
    public static boolean onPress(ServerPlayerEntity player) {
        if (player == null || !FerrymanParticipation.isActiveFerryman(player)) {
            return false;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
        if (!component.isPardoning()) {
            player.sendMessage(Text.translatable(UNAVAILABLE_KEY), true);
            return false;
        }
        UUID killerId = component.pendingKiller();
        component.closePardon();
        component.setCooldown(FerrymanRules.PARDON_COOLDOWN_TICKS);
        if (killerId != null) {
            component.addGaze(killerId);
            // The marked attacker is the one who must hear the warning, not the Ferryman who already survived.
            // 该听到警告的是被标记的攻击者，而不是已经活下来的摆渡人。
            ServerPlayerEntity killer = player.getServer() == null
                    ? null
                    : player.getServer().getPlayerManager().getPlayer(killerId);
            if (killer != null) {
                killer.sendMessage(Text.translatable(MARKED_KEY), true);
            }
        }
        player.sendMessage(Text.translatable(SAVED_KEY), true);
        return true;
    }

    private static void onReset(ServerPlayerEntity player) {
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
        if (!FerrymanParticipation.isFerryman(player)) {
            // A window can only be open on a Charon, but a reset must never carry one into the next round.
            // 窗口只会在摆渡人身上开启，但换局绝不能把它带进下一局。
            if (component.isPardoning()) {
                component.closePardon();
            }
            return;
        }
        component.clearRoundState();
    }
}
