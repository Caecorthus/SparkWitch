package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownKind;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gun-hit cooldown aura (C4). Queued at the hit and applied at {@code END_SERVER_TICK}, after the gun handler wrote the
 * shooter's own post-shot cooldown, so that write cannot undercut the floor. Every other playing, alive, non-spectator
 * participant within {@link FiendRules#AURA_RADIUS} of the Fiend (the shooter included) gets every distinct carried item
 * (main, offhand, armor) raised to at least {@link FiendRules#AURA_COOLDOWN_TICKS} through SparkFactionAPI
 * {@code ForcedCooldowns.raiseAll} with {@code CooldownKind.ITEM}: exact past cooldown modifiers, never shortened.
 * The registry's item exemptions apply: NoellesRoles' timed bomb (C15; its cooldown is the Bomber pass gate, and a
 * 20 s floor would outlast the 15 s beep and always kill the holder) and the Seeker car ({@code SeekerCooldowns} stays
 * its sole writer; owner decision, 2026-10-02). Role-skill counters are untouched. Server only.
 * 枪击冷却光环（C4）。在受击时入队，于 {@code END_SERVER_TICK} 结算，此时枪械处理器已写入开枪者自己的射击冷却，不会压低
 * 下限。魔人 {@link FiendRules#AURA_RADIUS} 格内其他在局、存活、非旁观的参与者（含开枪者）身上每种物品（主背包、副手、
 * 盔甲）的冷却经 SparkFactionAPI {@code ForcedCooldowns.raiseAll}（{@code CooldownKind.ITEM}）提升至至少
 * {@link FiendRules#AURA_COOLDOWN_TICKS}：越过冷却倍率精确写入，绝不缩短。注册表的物品豁免生效：NoellesRoles 定时炸弹
 * （C15：其冷却是炸弹客的转手门槛，20 秒下限会超过 15 秒的蜂鸣期，必然炸死持有者）与搜寻者小车（{@code SeekerCooldowns}
 * 仍是其唯一写入方；所有者决定，2026-10-02）。职业技能计数不受影响。仅服务端。
 */
public final class FiendCooldownAura {
    private static final Set<UUID> PENDING = new LinkedHashSet<>();
    private static boolean registered;

    private FiendCooldownAura() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FiendCooldownAura::flush);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> PENDING.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    static void enqueue(ServerPlayerEntity fiend) {
        PENDING.add(fiend.getUuid());
    }

    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UUID> fiends = List.copyOf(PENDING);
        PENDING.clear();
        for (UUID id : fiends) {
            ServerPlayerEntity fiend = server.getPlayerManager().getPlayer(id);
            if (fiend != null) {
                pulse(fiend);
            }
        }
    }

    private static void pulse(ServerPlayerEntity fiend) {
        for (ServerPlayerEntity player : List.copyOf(fiend.getServerWorld().getPlayers())) {
            if (isTarget(player == fiend, GameFunctions.isPlayerPlayingAndAlive(player), player.isSpectator(),
                    player.squaredDistanceTo(fiend))) {
                ForcedCooldowns.raiseAll(player, CooldownKind.ITEM, FiendRules.AURA_COOLDOWN_TICKS);
            }
        }
    }

    /** Pure target filter; swallowed players are living spectators and are skipped. / 纯目标过滤；被吞者是存活旁观者，被跳过。 */
    static boolean isTarget(boolean isFiend, boolean playingAndAlive, boolean spectator, double squaredDistance) {
        return !isFiend && playingAndAlive && !spectator
                && squaredDistance <= FiendRules.AURA_RADIUS * FiendRules.AURA_RADIUS;
    }
}
