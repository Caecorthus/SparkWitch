package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bomb-pass credit (C6): who handed the NoellesRoles timed bomb DIRECTLY to its current holder, recorded only when the
 * passer is a Fiend. Any later hand-off, a fresh placement and every explosion drop the holder's entry, so only the
 * Fiend's own direct pass can pay. Server-only map, never synced or persisted; cleared on round start, player reset,
 * finalize and server stop. Fed only by {@code BomberPlayerComponentFiendLedgerMixin}.
 * 炸弹转手奖励（C6）：记录谁把 NoellesRoles 定时炸弹直接交给当前持有者，仅在转手者为魔人时记录。之后的任何转手、新的放置
 * 与每次爆炸都会移除该持有者的条目，因此只有魔人自己的直接转手才能获得奖励。仅服务端的映射，从不同步或持久化；在开局、
 * 玩家重置、结算与服务器停止时清空。只由 {@code BomberPlayerComponentFiendLedgerMixin} 写入。
 */
public final class FiendBombLedger {
    private static final FiendBombLedger SERVER = new FiendBombLedger();
    private static boolean registered;

    private final Map<UUID, UUID> passerByHolder = new HashMap<>();

    FiendBombLedger() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_GAME_START.register(gameMode -> SERVER.clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> SERVER.clear());
        ResetPlayer.EVENT.register(player -> SERVER.forget(player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER.clear());
    }

    // ---- Pure ledger / 纯账本 ----

    /**
     * A successful hand-off: the passer no longer holds a bomb, and the recipient's holder entry is the passer only when
     * the passer is a Fiend. / 一次成功转手：转手者不再持有炸弹；仅当转手者为魔人时，接手者的条目才记为该转手者。
     */
    void recordTransfer(UUID passer, UUID recipient, boolean passerIsFiend) {
        passerByHolder.remove(Objects.requireNonNull(passer));
        if (passerIsFiend) {
            passerByHolder.put(Objects.requireNonNull(recipient), passer);
        } else {
            passerByHolder.remove(Objects.requireNonNull(recipient));
        }
    }

    @Nullable UUID pop(UUID holder) {
        return passerByHolder.remove(holder);
    }

    void drop(UUID holder) {
        passerByHolder.remove(holder);
    }

    /** Drops the player both as a holder and as a passer. / 同时移除该玩家作为持有者与转手者的条目。 */
    void forget(UUID player) {
        passerByHolder.remove(player);
        passerByHolder.values().removeIf(player::equals);
    }

    void clear() {
        passerByHolder.clear();
    }

    /**
     * Pays only when the bomb killed its own holder (not the Taotie that swallowed it), the death is final, and the
     * recorded passer is still a dormant Fiend: reactions stop once the moment is bought or the Fiend is spent
     * (C3, C12, C15).
     * 仅当炸弹炸死其持有者本人（而非吞下持有者的饕餮）、死亡为最终死亡，且记录的转手者仍是休眠魔人时发放：购买时刻后或
     * 魔人耗尽后不再有反应（C3、C12、C15）。
     */
    static boolean shouldPay(boolean hasPasser, boolean victimIsHolder, boolean victimFinallyDead,
                             boolean passerIsDormantFiend) {
        return hasPasser && victimIsHolder && victimFinallyDead && passerIsDormantFiend;
    }

    // ---- Mixin entry points (server only) / Mixin 入口（仅服务端） ----

    public static void onTransfer(PlayerEntity passer, PlayerEntity recipient) {
        if (passer instanceof ServerPlayerEntity && recipient instanceof ServerPlayerEntity) {
            SERVER.recordTransfer(passer.getUuid(), recipient.getUuid(), FiendParticipation.isFiend(passer));
        }
    }

    /** A new placement is a fresh bomb without a passer. / 新放置的炸弹没有转手者。 */
    public static void onPlaced(PlayerEntity holder) {
        if (holder instanceof ServerPlayerEntity) {
            SERVER.drop(holder.getUuid());
        }
    }

    /**
     * Wraps each {@code explode()} kill: pops the holder's entry, runs the kill exactly once, then pays if due.
     * 包裹 {@code explode()} 的每次击杀：弹出持有者条目，恰好执行一次击杀，然后按需发放奖励。
     */
    public static void onExplosionKill(PlayerEntity holder, ServerPlayerEntity victim, Runnable kill) {
        UUID passer = holder instanceof ServerPlayerEntity ? SERVER.pop(holder.getUuid()) : null;
        kill.run();
        if (passer == null) {
            return;
        }
        boolean finallyDead = GameWorldComponent.KEY.get(victim.getWorld()).isPlayerDead(victim.getUuid())
                && !WitchFactorTraitsBridge.isDeathIntercepted(victim);
        ServerPlayerEntity fiend = victim.getServer().getPlayerManager().getPlayer(passer);
        boolean dormantFiend = fiend != null && FiendParticipation.isDormantFiend(fiend);
        if (shouldPay(true, victim == holder, finallyDead, dormantFiend)) {
            FiendReactionService.rewardBombPass(fiend);
        }
    }

    /** No entry survives an explosion, whether or not a kill ran. / 无论是否执行击杀，爆炸后都不保留条目。 */
    public static void onExplosionEnd(PlayerEntity holder) {
        if (holder instanceof ServerPlayerEntity) {
            SERVER.drop(holder.getUuid());
        }
    }
}
