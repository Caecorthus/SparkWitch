package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify.PurifyAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.net.UseApprenticePurifyC2SPayload;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Event wiring for the Apprentice Witch buff (owner 2026-10-06): graduation after two tasks (D2), the Purify request
 * (D3), its replay line, and round cleanup of {@link ApprenticePlayerComponent}. Runs from the common initializer, so
 * the Purify payload type exists on both sides; only the server receiver acts.
 * 预备魔女增强（所有者 2026-10-06）的事件接线：两个任务后出师（D2）、净化请求（D3）及其回放文本，以及
 * {@link ApprenticePlayerComponent} 的回合清理。在通用初始化器中运行，因此净化数据包类型在两端都存在；只有服务端接收器会执行。
 */
public final class ApprenticeFeatureService {
    private static boolean registered;

    private ApprenticeFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseApprenticePurifyC2SPayload.ID, UseApprenticePurifyC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseApprenticePurifyC2SPayload.ID,
                (payload, context) -> PurifyAbility.use(context.player()));
        PurifyAbility.registerReplayFormatter();
        TaskComplete.EVENT.register((player, taskType) -> onTaskComplete(player));
        RoleAssigned.EVENT.register((player, role) -> ApprenticePlayerComponent.KEY.get(player).clear());
        ResetPlayer.EVENT.register(player -> ApprenticePlayerComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    ApprenticePlayerComponent.KEY.get(player).clear();
                }
            }
        });
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player) != SparkWitchRoles.apprenticeWitch()) {
            return;
        }
        if (ApprenticePlayerComponent.KEY.get(player).recordCompletedTask()) {
            player.sendMessage(Text.translatable("message.sparkwitch.apprentice.graduated"), false);
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }
}
