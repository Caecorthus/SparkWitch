package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class SparkWitchEvents {
    private static boolean registered;

    private SparkWitchEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        RitualSwordCombatService.register();
        RitualSwordDashService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WitchSkillAssignmentService.assignForRole(serverPlayer, role);
            }
        });
        ResetPlayer.EVENT.register(player -> WitchPlayerComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                // Round end clears only SparkWitch runtime state; role maps and other mods remain owned by wathe.
                // 回合结束只清理 SparkWitch 运行态，身份表和其他模组状态仍由 wathe 自己管理。
                WitchWorldComponent.KEY.get(serverWorld).clearRoundState();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    WitchPlayerComponent.KEY.get(player).clear();
                }
            }
        });
    }
}
