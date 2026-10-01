package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseBootstrap;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Registers Black Raven lifecycle hooks while keeping the global event owner declarative. */
public final class BlackRavenFeatureService {
    /**
     * Wathe's ON_FINISH_INITIALIZE phase for the Black Raven, ordered after the default phase so Wathe has started
     * the replay match (the binding id) and every default listener (e.g. SparkTraits compensation) has settled final
     * roles, whatever the mod initializer order.
     * 黑羽鸦在 Wathe ON_FINISH_INITIALIZE 上使用的阶段，排在默认阶段之后：无论模组初始化顺序如何，此时 Wathe
     * 已开始回放对局（绑定 id），所有默认监听器（如 SparkTraits 补偿）也已确定最终身份。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("black_raven_finish_initialize");

    private static boolean registered;

    private BlackRavenFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FeatherBladeMeleeService.register();
        BlackRavenShopService.register();
        BlackRavenDisguiseBootstrap.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            BlackRavenPerceptionPlayerComponent perception = BlackRavenPerceptionPlayerComponent.KEY.get(serverPlayer);
            if (!BlackRavenRules.isBlackRaven(role)) {
                // Role loss ends any disguise first: stashes vanish, the live set stays with the new role.
                // 失去职业时先结束伪装：存档消失，当前物品留给新职业。
                BlackRavenDisguiseService.endForRoleLoss(serverPlayer);
                if (perception.hasRoundState()) {
                    BlackRavenPerceptionService.clearForRoleLossOrDeath(serverPlayer);
                    BlackRavenLoadoutService.removeOwnedItems(serverPlayer);
                } else {
                    BlackRavenLoadoutService.removeLedger(serverPlayer);
                }
                return;
            }
            // A re-assigned, still disguised Raven reverts first so the kit re-grant cannot duplicate stashed items.
            // 被重新指定且仍在伪装的黑羽鸦先恢复原身份，避免重新发放套装时与存档物品重复。
            BlackRavenDisguiseService.revertForRoleReassign(serverPlayer);
            BlackRavenLoadoutService.assignForRole(serverPlayer, role);
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> clearDeadPlayer(victim));
        ResetPlayer.EVENT.register(player -> {
            BlackRavenDisguiseService.clearForReset(player);
            clearRoundItems(player);
        });
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlackRavenMarkPlayerComponent.KEY.get(player).clear();
                if (BlackRavenRules.isBlackRaven(game.getRole(player))) {
                    // Binds the disguise state and snapshots Tab B before the kit check. / 在检查装备前绑定伪装状态并快照名单。
                    BlackRavenDisguiseService.beginRound(player, serverWorld, game);
                    BlackRavenPerceptionService.bindCurrentMatch(player);
                    BlackRavenLoadoutService.restoreLedgerIfNeeded(player);
                } else {
                    BlackRavenDisguiseService.clearForReset(player);
                    BlackRavenPerceptionPlayerComponent.KEY.get(player).clear();
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlackRavenDisguiseService.clearForReset(player);
                if (BlackRavenPerceptionPlayerComponent.KEY.get(player).hasRoundState()) {
                    BlackRavenPerceptionService.clearForRoleLossOrDeath(player);
                }
                BlackRavenMarkPlayerComponent.KEY.get(player).clear();
                BlackRavenLoadoutService.removeOwnedItems(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (BlackRavenPerceptionPlayerComponent.KEY.get(player).isActive()) {
                BlackRavenPerceptionService.cancelForDisconnect(player);
            }
            BlackRavenDisguiseService.onDisconnect(player);
        });
    }

    /**
     * Death cleanup. The disguise ends first (every stash vanishes; the live set already went through Wathe's
     * drop loop), then the Perception cleanup and owned-item removal, which also removes the Raven Mask.
     * 死亡清理：先结束伪装（所有存档消失；当前物品已经过 Wathe 掉落流程），再执行感知清理与自有物品移除（含鸦羽假面）。
     */
    private static void clearDeadPlayer(ServerPlayerEntity player) {
        BlackRavenDisguiseService.endForDeath(player);
        clearRoundItems(player);
    }

    private static void clearRoundItems(ServerPlayerEntity player) {
        if (BlackRavenPerceptionPlayerComponent.KEY.get(player).hasRoundState()) {
            BlackRavenPerceptionService.clearForRoleLossOrDeath(player);
        }
        BlackRavenMarkPlayerComponent.KEY.get(player).clear();
        if (BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            BlackRavenLoadoutService.removeOwnedItems(player);
        } else {
            BlackRavenLoadoutService.removeLedger(player);
        }
    }
}
