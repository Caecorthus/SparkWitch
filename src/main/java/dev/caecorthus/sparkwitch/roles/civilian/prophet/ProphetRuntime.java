package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.NecrologyEntry;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative passive Death Sense: role-assignment arming, match binding, the periodic world-wide corpse
 * snapshot, and round cleanup for {@link ProphetPlayerComponent}.
 * 服务端权威的被动死亡感知：分配职业时启用、绑定对局、周期性的全世界尸体快照，以及 {@link ProphetPlayerComponent} 的回合清理。
 */
public final class ProphetRuntime {
    static final String SENSE_FOUND_KEY = "message.sparkwitch.prophet.sense.found";
    static final String SENSE_NONE_KEY = "message.sparkwitch.prophet.sense.none";
    private static boolean registered;

    private ProphetRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ResetPlayer.EVENT.register(player -> ProphetPlayerComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    ProphetPlayerComponent.KEY.get(player).clear();
                }
            }
        });
    }

    /**
     * Every Prophet assignment starts a fresh record with the first pulse one interval later; any other role drops
     * all Prophet state. The match id binds later in {@link #tick}, since Wathe starts the match record after
     * RoleAssigned at round start.
     * 每次分配先知都会重新开始记录，首次感知在一个周期之后；其他职业清空全部先知状态。对局 id 稍后在 {@link #tick} 中绑定，
     * 因为开局时 Wathe 在 RoleAssigned 之后才开始对局记录。
     */
    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        ProphetPlayerComponent component = ProphetPlayerComponent.KEY.get(player);
        if (ProphetRules.isProphet(role)) {
            component.arm();
        } else {
            component.clear();
        }
    }

    /**
     * Death Sense runs only for a living, playing, non-spectating Prophet and ignores Fear (owner decision Q7).
     * 死亡感知仅对存活、参与对局且非旁观的先知推进，不受恐惧影响（所有者决定 Q7）。
     */
    public static void tick(ServerPlayerEntity player, ProphetPlayerComponent component) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!ProphetRules.isProphet(game.getRole(player))) {
            component.clear();
            return;
        }
        UUID currentMatch = currentMatchId();
        if (ProphetPlayerState.isStale(component.matchId(), currentMatch)) {
            component.clear();
        }
        if (!component.isSenseArmed()) {
            // Self-heal for an assignment path that skipped RoleAssigned. / 为未触发 RoleAssigned 的分配路径兜底。
            component.arm();
        }
        if (currentMatch != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            component.bindMatch(currentMatch);
        }
        boolean eligible = GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player);
        if (component.tickSense(eligible) == ProphetPlayerState.TickOutcome.PULSE) {
            pulse(player, world, component);
        }
    }

    /**
     * Snapshots every visible body in the Prophet's world (no distance limit; Scavenger-hidden bodies excluded), then
     * sends a private sound and action-bar count to the Prophet alone.
     * 快照先知所在世界里所有可见尸体（不限距离，排除拾荒者隐藏的尸体），然后只向先知本人播放私有音效并发送动作栏计数。
     */
    private static void pulse(ServerPlayerEntity prophet, ServerWorld world, ProphetPlayerComponent component) {
        List<PlayerBodyEntity> bodies = new ArrayList<>(world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> !body.isRemoved()
                        && body.getPlayerUuid() != null
                        && !NoellesHiddenBodiesBridge.isHidden(world, body.getPlayerUuid())
        ));
        bodies.sort(Comparator.comparingInt(PlayerBodyEntity::getDeathGameTime).thenComparing(Entity::getUuid));

        Map<UUID, String> knownNames = new LinkedHashMap<>();
        component.necrology().forEach(entry -> knownNames.put(entry.player(), entry.name()));
        List<UUID> bodyUuids = new ArrayList<>(bodies.size());
        Map<UUID, NecrologyEntry> owners = new LinkedHashMap<>();
        for (PlayerBodyEntity body : bodies) {
            bodyUuids.add(body.getUuid());
            UUID owner = body.getPlayerUuid();
            owners.computeIfAbsent(owner, uuid -> new NecrologyEntry(
                    uuid,
                    resolveName(world.getServer(), uuid, knownNames.get(uuid))
            ));
        }
        component.recordPulse(bodyUuids, List.copyOf(owners.values()));

        prophet.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 0.6F);
        prophet.sendMessage(
                bodies.isEmpty()
                        ? Text.translatable(SENSE_NONE_KEY)
                        : Text.translatable(SENSE_FOUND_KEY, bodies.size()),
                true
        );
    }

    private static String resolveName(@Nullable MinecraftServer server, UUID uuid, @Nullable String knownName) {
        if (server != null) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            if (online != null) {
                return online.getGameProfile().getName();
            }
            UserCache cache = server.getUserCache();
            if (cache != null) {
                String cached = cache.getByUuid(uuid).map(GameProfile::getName).orElse(null);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            }
        }
        if (knownName != null && !knownName.isBlank()) {
            return knownName;
        }
        return uuid.toString().substring(0, 8);
    }

    private static @Nullable UUID currentMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
