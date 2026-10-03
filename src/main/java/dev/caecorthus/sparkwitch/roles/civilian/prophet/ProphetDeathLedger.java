package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only, in-memory death ledger for the Prophecy. It is never synced or persisted: only a correct guess may
 * reveal one responsible name. Bound to Wathe's current match id and cleared on round start/finalize, unload and stop.
 * 预言用的仅服务端内存死亡账本，永不同步、不持久化：只有猜中才会泄露一个责任人名字。
 * 绑定 Wathe 当前对局 id，并在开局、结算、卸载与停服时清空。
 */
public final class ProphetDeathLedger {
    private static final ProphetDeathBook<ServerWorld> BOOK = new ProphetDeathBook<>();
    private static boolean registered;

    private ProphetDeathLedger() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(ProphetDeathLedger::afterKill);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                BOOK.clearWorld(serverWorld);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                BOOK.clearWorld(serverWorld);
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> BOOK.clearWorld(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BOOK.clearAll());
    }

    public static Optional<ProphetDeathRecord> get(ServerWorld world, UUID victim) {
        return BOOK.get(world, currentMatch(world), victim);
    }

    /** Participants still dead in Wathe that have a record, sorted by name. / 仍处于死亡状态且有记录的参与者，按名字排序。 */
    public static List<ProphetDeathRecord> deadCandidates(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Set<UUID> participants = new HashSet<>(game.getAllPlayers());
        return BOOK.deadCandidates(world, currentMatch(world),
                victim -> participants.contains(victim) && game.isPlayerDead(victim));
    }

    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        ServerWorld world = victim.getServerWorld();
        String match = currentMatch(world);
        if (match == null) {
            return;
        }
        // AFTER runs inside Judge's committed attempt, whose cause scope is masked; read the attempt instead.
        // AFTER 在法官已提交的尝试内执行，此时归因作用域已屏蔽，故读取该尝试的责任人。
        UUID responsible = ProphetDeathBook.resolveResponsible(victim.getUuid(),
                JudgeKillAttribution.committedResponsible(victim), killer == null ? null : killer.getUuid());
        ProphetDeathRecord stored = BOOK.record(world, match, new ProphetDeathRecord(
                victim.getUuid(),
                victim.getGameProfile().getName(),
                reason,
                ProphetDeathCauseGroup.classify(reason),
                responsible,
                responsible == null ? null : nameOf(world.getServer(), responsible)));
        // A revived victim died again: Prophets' records for the earlier death no longer apply (the request path
        // re-checks this too). Only the new serial is compared; no ledger data reaches the client.
        // 被复活的受害者再次死亡：先知针对上一次死亡的记录不再适用（请求流程也会再次检查）。只比较新序号，账本数据不会发往客户端。
        Map<UUID, Long> serial = Map.of(stored.victim(), stored.serial());
        for (ServerPlayerEntity player : world.getPlayers()) {
            ProphetPlayerComponent component = ProphetPlayerComponent.KEY.get(player);
            if (component.prophecy(stored.victim()).isPresent()) {
                component.forgetStaleProphecies(serial);
            }
        }
    }

    /** Captured now so an offline poisoner or bomber still has a name later. / 立即抓取，离线的下毒者或炸弹客之后仍有名字。 */
    private static @Nullable String nameOf(@Nullable MinecraftServer server, UUID player) {
        if (server == null) {
            return null;
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        UserCache cache = server.getUserCache();
        return cache == null ? null : cache.getByUuid(player).map(GameProfile::getName).orElse(null);
    }

    private static @Nullable String currentMatch(ServerWorld world) {
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameRecordManager.hasActiveMatch()) {
            return null;
        }
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return match == null || match.getMatchId() == null ? null : match.getMatchId().toString();
    }
}
