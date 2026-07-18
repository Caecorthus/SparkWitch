package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns Witch Maiden's final Voodoo cancellation and Tofana retaliation.
 * 负责巫女的巫毒最终死亡取消与托法娜反杀。
 */
public final class WitchMaidenFeatureService {
    private static boolean registered;

    private WitchMaidenFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.BEFORE.register(WitchMaidenFeatureService::beforeKill);
        KillPlayer.AFTER.register(WitchMaidenFeatureService::afterKill);
    }

    private static @Nullable KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        if (!WitchMaidenRules.blocksVoodooDeath(gameComponent.getRole(victim), deathReason)) {
            return null;
        }
        return KillPlayer.KillResult.cancel();
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        boolean distinctPlayerKiller = killer != null && !victim.getUuid().equals(killer.getUuid());
        boolean killerPlayingAndAlive = killer != null && GameFunctions.isPlayerPlayingAndAlive(killer);
        boolean carriesElixir = hasTofana(victim);
        if (!WitchMaidenRules.shouldTriggerTofana(
                WitchMaidenRules.isWitchMaiden(gameComponent.getRole(victim)),
                distinctPlayerKiller,
                killerPlayingAndAlive,
                carriesElixir
        )) {
            return;
        }

        // Consumption precedes the nested non-forced kill, so protection still spends the single-use elixir.
        // 先消耗再发起非强制嵌套击杀，因此反杀被保护拦下时仙液仍会消耗。
        consumeTofana(victim);
        GameFunctions.killPlayer(
                killer,
                true,
                victim,
                WitchMaidenRules.TOFANA_DEATH_REASON_ID
        );
    }

    private static boolean hasTofana(ServerPlayerEntity victim) {
        for (ItemStack stack : victim.getInventory().main) {
            if (stack.isOf(SparkWitchItems.tofanaElixir())) {
                return true;
            }
        }
        return false;
    }

    private static void consumeTofana(ServerPlayerEntity victim) {
        for (ItemStack stack : victim.getInventory().main) {
            if (stack.isOf(SparkWitchItems.tofanaElixir())) {
                stack.decrement(1);
                victim.getInventory().markDirty();
                return;
            }
        }
    }
}
