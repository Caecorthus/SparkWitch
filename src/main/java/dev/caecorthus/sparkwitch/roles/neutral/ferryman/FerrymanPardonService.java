package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
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
 * replays the exact kill it held (same killer, same death reason, body kept), so nothing about the original hit is
 * rewritten. Server only.
 * 摆渡人在共享技能键上的第一技能：致命一击改为开启 0.5 秒窗口，窗口内按键即取消死亡、给攻击者挂上「瞥视」并进入
 * 10 秒冷却；窗口过期则原样重放它所保存的那次击杀（同凶手、同死亡原因、保留尸体），原命中的任何信息都不被改写。
 * 仅服务端。
 */
public final class FerrymanPardonService {
    private static final String PROMPT_KEY = "message.sparkwitch.ferryman.pardon.prompt";
    private static final String SAVED_KEY = "message.sparkwitch.ferryman.pardon.saved";
    private static final String UNAVAILABLE_KEY = "message.sparkwitch.ferryman.pardon.unavailable";
    private static final String FEIGN_KEY = "message.sparkwitch.ferryman.gaze.feign";
    /**
     * Re-entrancy guard: the replay must reach Wathe's own kill path without our BEFORE listener holding it again.
     * 重入保护：重放必须能进入 Wathe 自己的击杀流程，而不能被本监听器再次扣住。
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
        KillPlayer.BEFORE.register(FerrymanPardonService::beforeKill);
        ServerTickEvents.END_SERVER_TICK.register(FerrymanPardonService::tick);
        ResetPlayer.EVENT.register(FerrymanPardonService::onReset);
        // A restart drops every window; the death it held is not owed after a server stop.
        // 重启丢弃所有窗口：服务器停止后不再追讨它扣住的那次死亡。
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> replayingDeath = false);
    }

    private static @Nullable KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            @Nullable Identifier deathReason
    ) {
        if (replayingDeath || killer == null || killer == victim) {
            return KillPlayer.KillResult.allow();
        }
        if (!FerrymanParticipation.isFerryman(victim)) {
            return KillPlayer.KillResult.allow();
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(victim);
        // A marked attacker's second strike is answered with 10 s of feign death instead of another pardon.
        // 被标记者再次攻击时，回答他的是 10 秒假死，而不是再一次宽恕。
        if (component.hasGaze(killer.getUuid()) && FerrymanFeignService.start(killer)) {
            killer.sendMessage(Text.translatable(FEIGN_KEY), true);
            return KillPlayer.KillResult.cancel();
        }
        if (component.isPardoning() || !FerrymanRules.canPardon(component.cooldownTicks())) {
            return KillPlayer.KillResult.allow();
        }
        long now = victim.getServerWorld().getTime();
        component.openPardon(killer.getUuid(), deathReason, now + FerrymanRules.PARDON_WINDOW_TICKS);
        // Wathe already applied the damage; keep the body standing until the window resolves.
        // Wathe 已经结算了伤害；窗口决出前让身体站住。
        victim.setHealth(1.0F);
        victim.sendMessage(Text.translatable(PROMPT_KEY), true);
        return KillPlayer.KillResult.cancel();
    }

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!FerrymanParticipation.isFerryman(player)) {
                continue;
            }
            FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
            component.tickCooldown();
            if (component.isPardoning() && now >= component.pardonDeadlineTick()) {
                replayHeldDeath(player, component);
            }
        }
    }

    private static void replayHeldDeath(ServerPlayerEntity victim, FerrymanPlayerComponent component) {
        UUID killerId = component.pendingKiller();
        Identifier reason = component.pendingDeathReason();
        component.closePardon();
        MinecraftServer server = victim.getServer();
        ServerPlayerEntity killer = killerId == null || server == null
                ? null
                : server.getPlayerManager().getPlayer(killerId);
        replayingDeath = true;
        try {
            GameFunctions.killPlayer(
                    victim,
                    true,
                    killer,
                    reason == null ? SparkWitchDeathReasons.PARDON_EXPIRED : reason
            );
        } finally {
            replayingDeath = false;
        }
    }

    /** Shared ability key, server side. Returns false when no window is open. / 共享技能键的服务端处理；无窗口时返回 false。 */
    public static boolean onPress(ServerPlayerEntity player) {
        if (!FerrymanParticipation.isFerryman(player)) {
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
        }
        player.sendMessage(Text.translatable(SAVED_KEY), true);
        return true;
    }

    private static void onReset(ServerPlayerEntity player) {
        if (!FerrymanParticipation.isFerryman(player)) {
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
        component.clearRoundState();
        component.setCooldown(FerrymanRules.INITIAL_COOLDOWN_TICKS);
    }
}
