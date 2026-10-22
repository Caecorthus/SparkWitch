package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.net.OpenJudgeSelectionS2CPacket;
import dev.caecorthus.sparkwitch.record.AchievementRecords;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.ladysnake.cca.api.v3.component.ComponentProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JudgeRuntime {
    private static boolean registered;

    private JudgeRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                JudgeWorldComponent component = JudgeWorldComponent.KEY.get(world);
                component.sessions().clear();
                component.state().begin(UUID.randomUUID(), game.getAllPlayers());
                JudgeTrainFallAttribution.clearWorld(serverWorld);
                component.sync();
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearRound(serverWorld);
            }
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                assignForRole(serverPlayer, role);
            }
        });
        TaskComplete.EVENT.register((player, task) -> JudgeEconomyService.onTaskComplete(player));
        CanSeeMoney.EVENT.register(player -> canJudge(player) ? CanSeeMoney.Result.ALLOW : null);
        // JOIN fires inside PlayerManager#onPlayerConnect before the joiner is added to its world, so a world-wide
        // sync reached only the players already there, whose views a join cannot change; CCA then syncs every world
        // component to the joiner in sendWorldInfo. Only the joiner is synced here.
        // JOIN 在 PlayerManager#onPlayerConnect 中、加入者被加入世界之前触发，因此全世界同步只会发给已在世界中的玩家，
        // 而加入不会改变他们的视图；随后 CCA 在 sendWorldInfo 中把所有世界组件同步给加入者。这里只同步加入者。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                JudgeWorldComponent.KEY.syncWith(handler.player, (ComponentProvider) handler.player.getServerWorld()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (ServerWorld world : server.getWorlds()) {
                JudgeWorldComponent.KEY.get(world).sessions().invalidate(handler.player.getUuid());
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> clearRound(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                clearRound(world);
            }
        });
    }

    private static void clearRound(ServerWorld world) {
        JudgeWorldComponent.KEY.get(world).clearRound();
        JudgeTrainFallAttribution.clearWorld(world);
    }

    private static void assignForRole(ServerPlayerEntity player, Role role) {
        JudgeWorldComponent component = JudgeWorldComponent.KEY.get(player.getServerWorld());
        component.sessions().invalidate(player.getUuid());
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (JudgeRules.isJudge(role) && game.getGameStatus() == GameWorldComponent.GameStatus.STARTING
                && component.initializeJudge(player.getUuid())) {
            player.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            JudgeEconomyService.initialize(player);
        }
    }

    static void tick(ServerWorld world, JudgeWorldComponent component) {
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            if (component.state().active()) {
                clearRound(world);
            }
            return;
        }
        long now = world.getTime();
        if (component.state().expire(now)) {
            component.sync();
        }
        component.sessions().expire(now);
        for (UUID actor : component.sessions().actors()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(actor);
            if (player == null || player.getServerWorld() != world || !canJudge(player)) {
                component.sessions().invalidate(actor);
            }
        }
    }

    public static boolean canJudge(PlayerEntity player) {
        if (player == null || GameWorldComponent.KEY.get(player.getWorld()).getGameStatus()
                != GameWorldComponent.GameStatus.ACTIVE) {
            return false;
        }
        return JudgeRules.isJudge(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && activeParticipant(player) && !SparkWitchApi.isWraithRestricted(player);
    }

    private static boolean activeParticipant(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator() && !player.isCreative();
    }

    public static void openSelection(ServerPlayerEntity player) {
        if (!canJudge(player)) {
            message(player, "unavailable");
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, OpenJudgeSelectionS2CPacket.ID)) {
            message(player, "unavailable");
            return;
        }
        ServerWorld world = player.getServerWorld();
        JudgeWorldComponent component = JudgeWorldComponent.KEY.get(world);
        if (!component.state().active()) {
            message(player, "unavailable");
            return;
        }
        List<ServerPlayerEntity> targets = world.getPlayers().stream()
                .filter(target -> target != player && activeParticipant(target))
                .sorted(Comparator.comparing((ServerPlayerEntity target) -> target.getName().getString(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(target -> target.getUuid().toString()))
                .toList();
        if (targets.isEmpty() || targets.size() > JudgeRules.MAX_SELECTION_TARGETS) {
            message(player, "unavailable");
            return;
        }
        List<UUID> ids = targets.stream().map(ServerPlayerEntity::getUuid).toList();
        Optional<JudgeSelectionSessions.Session> session = component.sessions().open(
                player.getUuid(), component.state().match(), ids, world.getTime());
        if (session.isEmpty()) {
            return;
        }
        try {
            ServerPlayNetworking.send(player, new OpenJudgeSelectionS2CPacket(
                    session.get().nonce(), ids, targets.stream().map(target -> target.getName().getString()).toList()));
        } catch (RuntimeException error) {
            component.sessions().invalidate(player.getUuid());
            throw error;
        }
    }

    /** A valid attempt is charged once whether or not the target meets the threshold.
     * 合法尝试只扣款一次，无论目标是否达到判决门槛。 */
    public static void confirmSelection(ServerPlayerEntity player, UUID nonce, UUID targetUuid) {
        ServerWorld world = player.getServerWorld();
        JudgeWorldComponent component = JudgeWorldComponent.KEY.get(world);
        Optional<JudgeSelectionSessions.Session> session = component.sessions().consume(
                player.getUuid(), nonce, component.state().match(), world.getTime());
        if (session.isEmpty()) {
            message(player, "no_pending");
            return;
        }
        if (!canJudge(player) || !session.get().targets().contains(targetUuid)
                || player.getUuid().equals(targetUuid)) {
            message(player, "invalid_selection");
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || target.getServerWorld() != world || !activeParticipant(target)) {
            message(player, "invalid_selection");
            return;
        }
        component.state().admit(targetUuid);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        JudgeRoundState.Verdict result = component.state().judge(targetUuid, world.getTime(), shop.getBalance());
        if (result.cost() != 0) {
            shop.addToBalance(-result.cost());
        }
        switch (result) {
            case INVALID -> message(player, "invalid_selection");
            case ALREADY_SENTENCED -> message(player, "already_sentenced");
            case INSUFFICIENT_MONEY -> message(player, "insufficient_money", JudgeRules.JUDGMENT_COST);
            case BELOW_THRESHOLD -> message(player, "not_guilty");
            case SENTENCED -> {
                component.sync();
                message(player, "sentenced", target.getName());
                Text announcement = Text.translatable("message.sparkwitch.judge.announcement", target.getName());
                for (ServerPlayerEntity recipient : world.getPlayers()) {
                    recipient.sendMessage(announcement, false);
                }
            }
        }
        if (result.cost() != 0) {
            // Achievement record W13: every charged judgment, sentenced or not, after the fee and the verdict.
            // 成就记录 W13：每次已扣费的审判（无论是否判刑），在扣费与判决之后写入。
            AchievementRecords.judgeVerdict(player, target, result == JudgeRoundState.Verdict.SENTENCED,
                    component.state().kills(targetUuid), component.state().requiredKills(), JudgeRules.SENTENCE_TICKS);
        }
    }

    /** Offline or dead perpetrators remain bound by their UUID's unexpired sentence.
     * 责任玩家即使离线或死亡，其 UUID 上未到期的判决仍有效。 */
    public static boolean blocksKill(ServerWorld world, UUID responsiblePlayer, UUID victim) {
        if (world == null || responsiblePlayer == null || victim == null || responsiblePlayer.equals(victim)
                || GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return false;
        }
        return JudgeWorldComponent.KEY.get(world).state().isSentenced(responsiblePlayer, world.getTime());
    }

    public static void recordKill(ServerWorld world, UUID responsiblePlayer, UUID victim) {
        if (world == null || responsiblePlayer == null || victim == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        JudgeRoundState state = JudgeWorldComponent.KEY.get(world).state();
        if (game.hasAnyRole(responsiblePlayer)) {
            state.admit(responsiblePlayer);
        }
        if (game.hasAnyRole(victim)) {
            state.admit(victim);
        }
        state.recordKill(responsiblePlayer, victim);
    }

    private static void message(ServerPlayerEntity player, String key, Object... arguments) {
        player.sendMessage(Text.translatable("message.sparkwitch.judge." + key, arguments), true);
    }
}
