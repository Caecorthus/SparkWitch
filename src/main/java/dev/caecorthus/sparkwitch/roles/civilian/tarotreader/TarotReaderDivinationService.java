package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.net.SubmitTarotDivinationSelectionC2SPacket;
import dev.caecorthus.sparkwitch.net.TarotDivinationSnapshotS2CPacket;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves Tarot divinations on the server and returns only the purchased result.
 * 所有塔罗占卜均由服务端判定，并且只把购买得到的结果发给购买者。
 */
public final class TarotReaderDivinationService {
    private TarotReaderDivinationService() {
    }

    public static boolean purchaseRegular(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(serverPlayer.getServerWorld());
        if (!canDivine(serverPlayer, gameComponent)
                || !ServerPlayNetworking.canSend(serverPlayer, TarotDivinationSnapshotS2CPacket.ID)) {
            return false;
        }

        FactionCounts counts = countActiveFactions(serverPlayer.getServerWorld(), gameComponent);
        try {
            ServerPlayNetworking.send(serverPlayer, new TarotDivinationSnapshotS2CPacket(
                    counts.civilian(),
                    counts.killer(),
                    counts.neutral(),
                    counts.witch()
            ));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean purchaseIdentity(PlayerEntity player) {
        return openSelector(player, OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY);
    }

    public static boolean purchaseSurvival(PlayerEntity player) {
        return openSelector(player, OpenTarotDivinationSelectorS2CPacket.MODE_SURVIVAL);
    }

    public static void submit(ServerPlayerEntity player, SubmitTarotDivinationSelectionC2SPacket payload) {
        Optional<TarotReaderSelectionSessionService.Session> pending =
                TarotReaderSelectionSessionService.consume(player.getUuid());
        if (pending.isEmpty()) {
            sendActionbar(player, "message.sparkwitch.tarot.no_pending");
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        if (!canDivine(player, gameComponent) || pending.get().mode() != payload.mode()) {
            sendActionbar(player, "message.sparkwitch.tarot.invalid_selection");
            return;
        }

        if (payload.mode() == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY) {
            resolveIdentity(player, gameComponent, payload.target());
        } else if (payload.mode() == OpenTarotDivinationSelectorS2CPacket.MODE_SURVIVAL) {
            resolveSurvival(player, gameComponent, payload.target());
        } else {
            sendActionbar(player, "message.sparkwitch.tarot.invalid_selection");
        }
    }

    static FactionCounts countActiveFactions(ServerWorld world, GameWorldComponent gameComponent) {
        int civilian = 0;
        int killer = 0;
        int neutral = 0;
        int witch = 0;
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            UUID uuid = candidate.getUuid();
            boolean assigned = gameComponent.hasAnyRole(uuid);
            boolean dead = gameComponent.isPlayerDead(uuid);
            // Swallowed spectators stay alive until digestion, so only creative players are excluded here.
            // 被吞下的旁观玩家在消化前仍算存活，因此这里仅额外排除创造模式玩家。
            if (!TarotReaderRules.shouldCountActivePlayer(assigned, dead, candidate.isCreative())) {
                continue;
            }

            Role role = gameComponent.getRole(candidate);
            TarotReaderRules.FactionBucket bucket = TarotReaderRules.classifyFaction(
                    SparkFactionApi.resolveEffectiveFaction(candidate, gameComponent),
                    role.getFaction()
            );
            if (bucket == null) {
                continue;
            }
            switch (bucket) {
                case CIVILIAN -> civilian++;
                case KILLER -> killer++;
                case NEUTRAL -> neutral++;
                case WITCH -> witch++;
            }
        }
        return new FactionCounts(civilian, killer, neutral, witch);
    }

    private static boolean openSelector(PlayerEntity player, int mode) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(serverPlayer.getServerWorld());
        if (!canDivine(serverPlayer, gameComponent)
                || !ServerPlayNetworking.canSend(serverPlayer, OpenTarotDivinationSelectorS2CPacket.ID)) {
            return false;
        }

        List<UUID> playerIds = List.of();
        List<String> playerNames = List.of();
        if (mode == OpenTarotDivinationSelectorS2CPacket.MODE_SURVIVAL) {
            List<PlayerTarget> targets = playerTargets(serverPlayer.getServerWorld(), gameComponent);
            playerIds = targets.stream().map(PlayerTarget::uuid).toList();
            playerNames = targets.stream().map(PlayerTarget::name).toList();
        } else if (mode != OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY) {
            return false;
        }

        TarotReaderSelectionSessionService.open(
                serverPlayer.getUuid(),
                mode
        );
        try {
            ServerPlayNetworking.send(serverPlayer, new OpenTarotDivinationSelectorS2CPacket(
                    mode,
                    playerIds,
                    playerNames
            ));
            return true;
        } catch (RuntimeException ignored) {
            TarotReaderSelectionSessionService.clear(serverPlayer.getUuid());
            return false;
        }
    }

    private static void resolveIdentity(
            ServerPlayerEntity player,
            GameWorldComponent gameComponent,
            String target
    ) {
        Identifier roleId = Identifier.tryParse(target);
        Role role = roleId == null ? null : WatheRoles.getRole(roleId);
        if (role == null
                || role == WatheRoles.NO_ROLE
                || role == WatheRoles.DISCOVERY_CIVILIAN
                || !WatheRoles.ROLES.contains(role)) {
            sendActionbar(player, "message.sparkwitch.tarot.invalid_selection");
            return;
        }

        Text roleName = Text.translatable(RoleDisplayTextRules.roleTranslationKey(role));
        boolean assigned = TarotReaderRules.identityWasAssigned(
                TarotReaderRoundRoleHistory.wasAssigned(role),
                gameComponent.getAllWithRole(role).size()
        );
        sendActionbar(
                player,
                assigned
                        ? "message.sparkwitch.tarot.identity.present"
                        : "message.sparkwitch.tarot.identity.absent",
                roleName
        );
    }

    private static void resolveSurvival(
            ServerPlayerEntity player,
            GameWorldComponent gameComponent,
            String target
    ) {
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            sendActionbar(player, "message.sparkwitch.tarot.invalid_selection");
            return;
        }
        if (!gameComponent.hasAnyRole(targetUuid)) {
            sendActionbar(player, "message.sparkwitch.tarot.invalid_selection");
            return;
        }

        boolean alive = TarotReaderRules.isTargetAlive(true, gameComponent.isPlayerDead(targetUuid));
        Text playerName = Text.literal(playerName(player.getServerWorld(), gameComponent, targetUuid));
        sendActionbar(
                player,
                alive
                        ? "message.sparkwitch.tarot.survival.alive"
                        : "message.sparkwitch.tarot.survival.dead",
                playerName
        );
    }

    private static List<PlayerTarget> playerTargets(ServerWorld world, GameWorldComponent gameComponent) {
        List<PlayerTarget> targets = new ArrayList<>();
        for (UUID uuid : gameComponent.getAllPlayers()) {
            targets.add(new PlayerTarget(uuid, playerName(world, gameComponent, uuid)));
        }
        targets.sort(Comparator.comparing(PlayerTarget::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(target -> target.uuid().toString()));
        return List.copyOf(targets);
    }

    private static String playerName(ServerWorld world, GameWorldComponent gameComponent, UUID uuid) {
        ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        GameProfile profile = gameComponent.getGameProfiles().get(uuid);
        return profile == null ? uuid.toString() : profile.getName();
    }

    private static boolean canDivine(ServerPlayerEntity player, GameWorldComponent gameComponent) {
        UUID uuid = player.getUuid();
        return gameComponent.isRunning()
                && gameComponent.hasAnyRole(uuid)
                && !gameComponent.isPlayerDead(uuid)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && TarotReaderRules.isTarotReader(gameComponent.getRole(player));
    }

    private static void sendActionbar(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }

    record FactionCounts(int civilian, int killer, int neutral, int witch) {
    }

    private record PlayerTarget(UUID uuid, String name) {
    }
}
