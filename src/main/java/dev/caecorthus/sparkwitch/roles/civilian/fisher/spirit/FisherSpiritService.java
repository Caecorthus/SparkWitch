package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherParticipants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Glimmerfish window for any living participant, regardless of role. / 所有存活参与者的灵光鱼窗口，与职业无关。 */
public final class FisherSpiritService {
    private static boolean registered;

    private FisherSpiritService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // The pinned SFA evaluates this predicate locally on BOTH entities, on both logical sides.
        // 固定版本的 SFA 在双端对双方实体分别本地求值；依靠 CCA 公共同步标记，不依靠他人收不到的状态效果。
        SparkFactionApi.registerEntityCollisionExemption(entity -> entity instanceof PlayerEntity player
                && FisherSpiritComponent.KEY.get(player).isActive());
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (victim != null && FisherSpiritComponent.KEY.get(victim).isActive()
                    && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                end(victim, false);
            }
        });
        ResetPlayer.EVENT.register(player -> end(player, false));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : List.copyOf(serverWorld.getServer().getPlayerManager().getPlayerList())) {
                    end(player, false);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
        // No role-change listener: transferred fish remain valid across recruitment/other role changes.
        // 不监听职业变化：转交的鱼在招募及其他职业变化后仍然有效。
    }

    /** Server only. Starts (or restarts) the 156-tick window; returns whether it started. / 仅服务端。 */
    public static boolean start(ServerPlayerEntity player) {
        String matchId = currentMatchId();
        if (!canStart(player, matchId)) {
            return false;
        }
        FisherSpiritComponent component = FisherSpiritComponent.KEY.get(player);
        if (component.isActive() && (component.startedWorld != player.getServerWorld()
                || !Objects.equals(component.matchId, matchId))) {
            end(player, false);
        }
        FisherInvisibility.release(player, component);
        component.matchId = matchId;
        component.startedWorld = player.getServerWorld();
        component.window.start(player.getWorld().getTime());
        FisherSpiritExit.rememberSafePosition(player, component);
        FisherInvisibility.ensure(player, component);
        component.sync();
        player.sendMessage(Text.translatable("message.sparkwitch.fisher.glimmer_start"), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    static void tick(ServerPlayerEntity player, FisherSpiritComponent component) {
        if (!FisherParticipants.isLivingParticipant(player) || component.startedWorld != player.getServerWorld()
                || !Objects.equals(component.matchId, currentMatchId())) {
            end(player, false);
            return;
        }
        if (component.window.tickServer(player.getWorld().getTime())) {
            end(player, true);
            return;
        }
        FisherSpiritExit.rememberSafePosition(player, component);
        FisherInvisibility.ensure(player, component);
    }

    private static void end(ServerPlayerEntity player, boolean announce) {
        FisherSpiritComponent component = FisherSpiritComponent.KEY.get(player);
        if (!component.isActive()) {
            return;
        }
        if (player.isAlive() && !player.isSpectator()
                && !GameWorldComponent.KEY.get(player.getWorld()).isPlayerDead(player.getUuid())) {
            FisherSpiritExit.moveOutOfDoor(player, component);
        }
        FisherInvisibility.release(player, component);
        component.clear();
        if (announce) {
            player.sendMessage(Text.translatable("message.sparkwitch.fisher.glimmer_end"), true);
        }
    }

    /** Side-effect-free preflight; a transferred fish never requires the Angler role. / 无副作用预检，不限制职业。 */
    public static boolean canStart(ServerPlayerEntity player) {
        return canStart(player, currentMatchId());
    }

    private static boolean canStart(ServerPlayerEntity player, @Nullable String matchId) {
        return FisherParticipants.isLivingParticipant(player) && matchId != null;
    }

    private static @Nullable String currentMatchId() {
        if (!GameRecordManager.hasActiveMatch()) {
            return null;
        }
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return match == null || match.getMatchId() == null ? null : match.getMatchId().toString();
    }
}
