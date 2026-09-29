package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Round-start rod and Angler cleanup. Glimmer state belongs to WP4. / 开局鱼竿与钓鱼佬清理；灵光状态归 WP4。 */
public final class FisherLifecycleService {
    private static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("fisher_finish_initialize");
    private static boolean registered;
    private static @Nullable MinecraftServer server;

    private FisherLifecycleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(FisherPufferfishService::sweepAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            FisherPufferfishService.sweepAll(stopped);
            server = null;
        });
        GameEvents.ON_GAME_START.register(mode -> {
            if (server != null) {
                FisherPufferfishService.sweepAll(server);
            }
        });
        // SparkTraits' DEFAULT-phase Conscience compensation settles final roles before rods are granted.
        // SparkTraits 的 DEFAULT 阶段良知补偿先确定最终身份，然后才发鱼竿。
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartRods(serverWorld, game);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity && !FisherRules.isFisher(role)) {
                FisherPufferfishService.clearPlayer(player.getUuid());
            }
        });
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (victim != null && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                FisherPufferfishService.clearPlayer(victim.getUuid());
            }
        });
        ResetPlayer.EVENT.register(player -> FisherPufferfishService.clearPlayer(player.getUuid()));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                FisherPufferfishService.sweepAll(serverWorld.getServer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, disconnected) ->
                FisherPufferfishService.clearPlayer(handler.player.getUuid()));
    }

    private static void grantRoundStartRods(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!receivesRod(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))) {
                continue;
            }
            var inventory = player.getInventory();
            boolean hasRod = false;
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (inventory.getStack(slot).isOf(SparkWitchItems.fishingRod())) {
                    hasRod = true;
                    break;
                }
            }
            if (!hasRod) {
                inventory.insertStack(new ItemStack(SparkWitchItems.fishingRod()));
            }
            FisherInventory.sync(player);
        }
    }

    /** STARTING is intentional; only this final-role phase grants a rod, never a mid-round assignment.
     * 此时仍是 STARTING；仅此最终身份阶段发放鱼竿，中途换职业不发放。 */
    static boolean receivesRod(boolean hasRole, boolean dead, @Nullable Role role) {
        return hasRole && !dead && FisherRules.isFisher(role);
    }
}
