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
 * The Ferryman's first skill on the shared ability key: a lethal hit opens a 1.5 s window instead of killing, and a
 * press inside it cancels the death, restarts a 5 s cooldown and lands 瞥视 on the attacker 1.5 s later. A press with
 * nothing to refuse is a whiff and costs 15 s. An expired window replays the exact kill it held (same killer, same
 * death reason, same {@code spawnBody}, not forced), so nothing about the original hit is rewritten. Server only.
 * 摆渡人在共享技能键上的第一技能：致命一击改为开启 1.5 秒窗口，窗口内按键即取消死亡、给攻击者挂上「瞥视」并进入
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
    private static final String WHIFF_KEY = "message.sparkwitch.ferryman.pardon.whiff";
    private static final String COOLING_KEY = "message.sparkwitch.ferryman.pardon.cooling";
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
            if (component.hasPendingMark()) {
                resolvePendingMark(player, component);
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
            // A press while the cooldown still runs only reports the remaining time: it must never extend a cooldown
            // the player already paid, or mashing the key would lock the skill at the long whiff timer.
            // 冷却期间按键只回报剩余时间：绝不能延长已经付过的冷却，否则连按会把技能锁在空放的长冷却里。
            int cooldown = component.cooldownTicks();
            if (cooldown > 0) {
                player.sendMessage(Text.translatable(COOLING_KEY, (cooldown + 19) / 20), true);
                return false;
            }
            // Nothing lethal to refuse: the press is a whiff and costs the long cooldown, so the key cannot be used as
            // a free "is anything about to kill me" probe and has to be held for a real hit.
            // 没有致命伤可拒绝：这次按键就是空放，代价是更长的冷却，因此技能键不能当作"是不是有人要杀我"的免费探针，必须留到真正的
            // 一击上。
            component.setCooldown(FerrymanRules.PARDON_WHIFF_COOLDOWN_TICKS);
            player.sendMessage(Text.translatable(WHIFF_KEY), true);
            return false;
        }
        UUID killerId = component.pendingKiller();
        component.closePardon();
        component.setCooldown(FerrymanRules.PARDON_COOLDOWN_TICKS);
        if (killerId != null) {
            // The glance lands 1.5 s later, not now: the attacker keeps a short window to answer the Charon first.
            // 「瞥视」1.5 秒后才落下：攻击者因此先有一小段时间回应摆渡人。
            component.scheduleGazeMark(killerId, player.getServerWorld().getTime() + FerrymanRules.GAZE_MARK_DELAY_TICKS);
        }
        player.sendMessage(Text.translatable(SAVED_KEY), true);
        return true;
    }

    /**
     * Applies a scheduled glance once its delay has run out, then tells the attacker. Runs for every player, so a mark
     * still lands when the Charon has meanwhile lost the role.
     * 延迟到期后落下一层已排定的「瞥视」，随后通知攻击者。对每名玩家都检查，因此即使摆渡人中途失去职业，标记也照样落下。
     */
    private static void resolvePendingMark(ServerPlayerEntity player, FerrymanPlayerComponent component) {
        UUID target = component.duePendingMark(player.getServerWorld().getTime());
        if (target == null) {
            return;
        }
        component.clearPendingMark();
        component.addGaze(target);
        if (player.getServer() == null) {
            return;
        }
        ServerPlayerEntity killer = player.getServer().getPlayerManager().getPlayer(target);
        if (killer != null) {
            killer.sendMessage(Text.translatable(MARKED_KEY), true);
        }
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
