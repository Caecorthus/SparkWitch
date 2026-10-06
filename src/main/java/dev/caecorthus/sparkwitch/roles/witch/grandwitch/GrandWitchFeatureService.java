package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoundComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Round lifecycle for the Grand Witch runtime, the Witch Factor and the special-accomplice ledger.
 * 大魔女运行态、魔女因子与特殊共犯账本的回合生命周期。 */
public final class GrandWitchFeatureService {
    private static boolean registered;

    private GrandWitchFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(WitchFactorService::afterKill);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WitchFactorService.onRoleChanged(serverPlayer);
            }
        });
        ResetPlayer.EVENT.register(player -> GrandWitchRuntimeComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                // Roles are assigned before ON_FINISH_INITIALIZE, so a forced variant stays used even after its
                // role-map entry is later replaced (death into Wraith, then Curser).
                // 身份在 ON_FINISH_INITIALIZE 之前已分配；被强制指定的特殊共犯即使之后身份表条目被替换（死亡转亡灵再转诅咒者）
                // 仍视为已使用。
                AccompliceVariantRoundComponent.KEY.get(serverWorld).beginRound(AccompliceVariants.variants().stream()
                        .filter(role -> !game.getAllWithRole(role).isEmpty())
                        .toList());
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    GrandWitchRuntimeComponent.KEY.get(player).clear();
                }
                WitchFactorService.beginRound(serverWorld, game.getAllPlayers().size());
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                WitchFactorService.clearRound(serverWorld);
                AccompliceVariantRoundComponent.KEY.get(serverWorld).clearRound();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    GrandWitchRuntimeComponent.KEY.get(player).clear();
                }
            }
        });
    }
}
