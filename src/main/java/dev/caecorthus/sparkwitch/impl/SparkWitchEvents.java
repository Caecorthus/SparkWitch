package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.economy.WitchEconomyService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordCombatService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerCombatService;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerFallAttributionService;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceCombatService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.caecorthus.sparkwitch.mana.WitchManaService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchFeature.MurderousWitchFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodFeatureService;
import dev.caecorthus.sparkwitch.skill.WitchSkillAssignmentService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
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

        CeremonialSwordCombatService.register();
        CeremonialSwordDashService.register();
        MightyForceCombatService.register();
        FirePokerCombatService.register();
        WitchFactionFeatureService.register();
        MurderousWitchFeatureService.register();
        PigGodFeatureService.register();
        PigGodEconomyService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WitchSkillAssignmentService.assignForRole(serverPlayer, role);
                WitchManaService.assignForRole(serverPlayer, role);
                WitchFactionFeatureService.assignForRole(serverPlayer, role);
                MurderousWitchFeatureService.assignForRole(serverPlayer, role);
                PigGodEconomyService.assignForRole(serverPlayer, role);
            }
        });
        TaskComplete.EVENT.register(WitchManaService::onTaskComplete);
        TaskComplete.EVENT.register((player, taskType) -> GrandWitchActiveSkillService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> PigGodEconomyService.onTaskComplete(player));
        KillPlayer.AFTER.register(WitchManaService::afterKill);
        KillPlayer.AFTER.register(WitchEconomyService::afterKill);
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            WitchFactionFeatureService.clearPlayerRuntime(victim);
            FirePokerFallAttributionService.clearPlayer(victim);
            WitchPlayerComponent.KEY.get(victim).clearPigChaseState();
        });
        ResetPlayer.EVENT.register(player -> {
            WitchFactionFeatureService.clearPlayerRuntime(player);
            FirePokerFallAttributionService.clearPlayer(player);
            WitchPlayerComponent.KEY.get(player).clear();
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                // Round end clears only SparkWitch runtime state; role maps and other mods remain owned by wathe.
                // 回合结束只清理 SparkWitch 运行态，身份表和其他模组状态仍由 wathe 自己管理。
                WitchWorldComponent.KEY.get(serverWorld).clearRoundState();
                FirePokerFallAttributionService.clearAll();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    WitchFactionFeatureService.clearPlayerRuntime(player);
                    WitchPlayerComponent.KEY.get(player).clear();
                }
            }
        });
    }
}
