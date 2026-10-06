package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.GrandWitchRecruitmentService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Lifecycle and requests for the two Grand Witch abilities. / 大魔女双技能的生命周期与请求入口。 */
public final class GrandWitchFeatureService {
    private static boolean registered;

    private GrandWitchFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GrandWitchRecruitmentService.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                GrandWitchRecruitmentService.syncRuntime(handler.player));
        KillPlayer.AFTER.register(WitchFactorService::afterKill);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WitchFactorService.onRoleChanged(serverPlayer);
            }
        });
        ResetPlayer.EVENT.register(player -> GrandWitchRuntimeComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                int participants = game.getAllPlayers().size();
                GrandWitchRecruitmentService.beginRound(serverWorld, participants);
                WitchFactorService.beginRound(serverWorld, participants);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                WitchFactorService.clearRound(serverWorld);
                GrandWitchRecruitmentService.clearRound(serverWorld);
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    GrandWitchRuntimeComponent.KEY.get(player).clear();
                }
            }
        });
    }

    public static void recruit(ServerPlayerEntity player, @Nullable UUID targetId) {
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(player, targetId);
        WitchSkillUseResult result = GrandWitchRecruitmentService.use(player, targetId);
        if (result.accepted() && target != null) {
            WitchFactorService.onRecruited(target);
            WitchFactorService.onRoleChanged(target);
        }
        if (result.messageKey() != null) {
            player.sendMessage(Text.translatable(result.messageKey()), true);
        } else if (!result.accepted()) {
            player.sendMessage(Text.translatable("message.sparkwitch.skill.unavailable"), true);
        }
    }
}
